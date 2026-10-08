import { test } from "node:test";
import assert from "node:assert/strict";
import { createItem } from "../shared/model";
import {
  interpretQuick,
  parseSearch,
  matchesSearch,
  linkedItems,
  backlinks,
  taskBlocked,
  validateTaskDependencies,
  planAutomations,
  dailySummary,
  paymentAgenda,
} from "../shared/platform";
import { desktopPatch } from "../shared/settings";
import { reportCSV, reportRows, readCSV } from "../shared/reporting";
test("Portuguese quick capture preserves exact cents and always proposes a review", () => {
  const p = interpretQuick("Gastei 42,35 no almoço", "2026-10-08")!;
  assert.equal(p.item.type, "expense");
  assert.equal(p.item.fields.amountMinor, "4235");
  assert.equal(p.item.fields.category, "Alimentação");
  assert.equal(p.confidence, "review");
  const t = interpretQuick("Academia amanhã 18h", "2026-10-08")!;
  assert.equal(t.item.title, "Academia");
  assert.equal(t.item.date, "2026-10-09");
  assert.equal(t.item.fields.time, "18:00");
  assert.equal(interpretQuick("Ideia para projeto")?.item.type, "note");
});

test("payment agenda includes overdue bills and remaining invoices once, with currency and deletion isolation", () => {
  const bill = createItem("expense", {
      id: "bill",
      date: "2026-10-07",
      title: "Internet",
      fields: { amountMinor: "12000", currency: "BRL", status: "pending" },
    }),
    card = createItem("card", { id: "card", title: "Meu cartão" });
  const invoices = [
    {
      id: "invoice:card:2026-10-09",
      cardId: "card",
      due: "2026-10-09",
      total: 90000,
      paid: 20000,
      remaining: 70000,
      currency: "BRL",
    },
  ];
  const result = paymentAgenda([bill], invoices, [bill, card], "2026-10-08");
  assert.equal(result.length, 2);
  assert(result[0].overdue);
  assert.equal(result[1].value, 70000);
  assert.equal(result[1].itemId, "card");
  assert.equal(
    paymentAgenda(
      [bill],
      [{ ...invoices[0], remaining: 0 }],
      [bill, card],
      "2026-10-08",
    ).length,
    1,
  );
  assert.equal(
    paymentAgenda(
      [],
      [{ ...invoices[0], currency: "USD" }],
      [card],
      "2026-10-08",
    ).length,
    0,
  );
  assert.equal(
    paymentAgenda([], invoices, [{ ...card, deletedAt: 1 }], "2026-10-08")
      .length,
    0,
  );
});
test("cash reports use actual payment dates and exclude card purchases, transfers, pending and future payments", () => {
  const income = createItem("income", {
    date: "2026-01-28",
    fields: {
      amountMinor: "100000",
      currency: "BRL",
      status: "received",
      settledDate: "2026-02-02",
    },
  });
  const purchase = createItem("expense", {
    date: "2026-02-03",
    fields: {
      amountMinor: "30000",
      currency: "BRL",
      card: "c",
      financialVersion: "3",
      status: "paid",
    },
  });
  const payment = createItem("expense", {
    date: "2026-01-30",
    fields: {
      amountMinor: "20000",
      currency: "BRL",
      card: "c",
      financialVersion: "3",
      paymentType: "card_payment",
      invoiceId: "invoice:c:2026-02-10",
      settledDate: "2026-02-10",
      status: "paid",
    },
  });
  const pending = createItem("expense", {
    date: "2026-02-05",
    fields: { amountMinor: "1000", currency: "BRL", status: "pending" },
  });
  const future = {
      ...income,
      id: "future",
      fields: { ...income.fields, settledDate: "2099-02-02" },
    },
    transfer = createItem("transfer", {
      date: "2026-02-03",
      fields: { amountMinor: "90000", status: "paid" },
    });
  const items = [income, purchase, payment, pending, future, transfer],
    filter = {
      from: "2026-02-01",
      to: "2026-02-28",
      currency: "BRL",
      basis: "cash" as const,
    };
  const result = reportRows(items, filter);
  assert.equal(result.income, 100000);
  assert.equal(result.expense, 20000);
  assert.equal(result.rows.length, 2);
  assert.equal(
    reportRows(items, { ...filter, basis: "recognized" }).expense,
    30000,
  );
  assert.equal(reportRows(items, { ...filter, card: "c" }).expense, 20000);
  const csv = readCSV(reportCSV(items, filter));
  assert.equal(csv[0][9], "paymentType");
  assert.equal(
    csv.find((row) => row[9] === "card_payment")?.[11],
    "2026-02-10",
  );
  assert.throws(() => reportRows(items, { ...filter, basis: "wrong" as any }));
});
test("monthly natural captures preserve requested day and reject impossible dates and amounts", () => {
  const p = interpretQuick("Internet 120 todo dia 31", "2026-02-15")!;
  assert.equal(p.item.type, "recurring_rule");
  assert.equal(p.item.fields.dayOfMonth, "31");
  assert.equal(p.item.date, "2026-02-28");
  assert.throws(() => interpretQuick("Internet 120 todo dia 40"));
  assert.throws(() => interpretQuick("Academia 31/02/2026"));
  assert.throws(() => interpretQuick("Gastei 42,351 almoço"));
});
test("universal filters combine type, tag, civil date, quoted category and exact amount", () => {
  const i = createItem("expense", {
    title: "Almoço",
    date: "2026-10-08",
    tags: "viagem, casa",
    fields: { amountMinor: "4235", currency: "BRL", category: "Alimentação" },
  });
  const syntax = parseSearch(
    'type:expense date:hoje tag:viagem category:"Alimentação" amount:>40',
    "BRL",
    "2026-10-08",
  );
  assert.equal(syntax.error, undefined);
  assert(matchesSearch(i, syntax));
  assert(!matchesSearch(i, parseSearch("type:income")));
  assert(!matchesSearch(i, parseSearch("amount:>50")));
  assert(parseSearch("date:2026-02-31").error);
});
test("project descendants and backlinks are local, cycle-safe and exclude deleted records", () => {
  const p = createItem("project"),
    task = createItem("task", { parentId: p.id }),
    sub = createItem("task", { parentId: task.id }),
    note = createItem("note", { title: "Planejamento" }),
    reference = createItem("note", {
      notes: "Veja [[Planejamento]]",
      parentId: p.id,
    });
  p.parentId = sub.id;
  assert.equal(linkedItems([p, task, sub, note, reference], p.id).length, 3);
  assert.equal(backlinks([note, reference], note).length, 1);
  assert.equal(
    backlinks([note, { ...reference, deletedAt: 1 }], note).length,
    0,
  );
});
test("task dependencies reject cycles and block only unfinished active requirements", () => {
  const a = createItem("task"),
    b = createItem("task", { fields: { dependencies: a.id } });
  assert.equal(taskBlocked(b, [a, b]).length, 1);
  assert.equal(taskBlocked(b, [{ ...a, done: true }, b]).length, 0);
  assert.throws(
    () =>
      validateTaskDependencies({ ...a, fields: { dependencies: b.id } }, [
        { ...a, fields: { dependencies: b.id } },
        b,
      ]),
    /ciclo/,
  );
  assert.throws(
    () =>
      validateTaskDependencies({ ...a, fields: { dependencies: "missing" } }, [
        a,
      ]),
    /existentes/,
  );
});
test("automation is explicit, currency-aware, deduplicated and cannot execute financial writes", () => {
  const rule = createItem("automation", {
      title: "Alerta",
      fields: {
        desktopEnabled: "yes",
        trigger: "expense",
        minimum: "100",
        currency: "BRL",
        category: "Alimentação",
        action: "alert",
        message: "Revisar gasto",
      },
    }),
    expense = createItem("expense", {
      title: "Mercado",
      fields: {
        amountMinor: "12000",
        currency: "BRL",
        category: "Alimentação",
      },
    });
  const plan = planAutomations([rule], expense, "2026-10-08");
  assert.equal(plan.alerts.length, 1);
  assert.equal(plan.records.length, 0);
  assert.equal(
    planAutomations([rule], expense, "2026-10-08", new Set(plan.marks)).alerts
      .length,
    0,
  );
  assert.equal(
    planAutomations([rule], {
      ...expense,
      fields: { ...expense.fields, currency: "USD" },
    }).alerts.length,
    0,
  );
  assert.equal(
    planAutomations(
      [{ ...rule, fields: { ...rule.fields, action: "payment" } }],
      expense,
    ).marks.length,
    0,
  );
});
test("weekday automation creates a deterministic task and never duplicates a daily run", () => {
  const rule = createItem("automation", {
    title: "Planejar",
    fields: {
      desktopEnabled: "yes",
      trigger: "weekday",
      weekday: "1",
      action: "task",
      message: "Planejar semana",
    },
  });
  const p = planAutomations([rule], undefined, "2026-10-12");
  assert.equal(p.records.length, 1);
  assert.equal(
    p.records[0].id,
    planAutomations([rule], undefined, "2026-10-12").records[0].id,
  );
  assert.equal(
    planAutomations([rule], undefined, "2026-10-13").records.length,
    0,
  );
});
test("dashboard action summary respects scheduled habits, pending bills and card separation", () => {
  const task = createItem("task", { date: "2026-10-07" }),
    bill = createItem("expense", {
      date: "2026-10-09",
      fields: { amountMinor: "10000", status: "pending", currency: "BRL" },
    }),
    card = {
      ...bill,
      id: "card-purchase",
      fields: { ...bill.fields, card: "c" },
    };
  const s = dailySummary([task, bill, card], "2026-10-08");
  assert.equal(s.overdue.length, 1);
  assert.equal(s.bills.length, 1);
});
test("desktop settings reject malformed layout, unknown capabilities and hostile import values", () => {
  assert.throws(() => desktopPatch({ layout: "oops" }));
  assert.throws(() =>
    desktopPatch({
      layout: [
        { id: "finance", size: "wide" },
        { id: "finance", size: "normal" },
      ],
    }),
  );
  assert.throws(() => desktopPatch({ scale: Infinity }));
  assert.throws(() => desktopPatch({ notifications: "true" }));
  assert.throws(() => desktopPatch({ panels: {} }));
  assert.equal(
    desktopPatch({ performanceMode: "economy" }).performanceMode,
    "economy",
  );
});
test("reports exclude internal transfers and invoice payments, preserve exact values and neutralize CSV formulas", () => {
  const date = "2026-01-10",
    expense = createItem("expense", {
      title: '=HYPERLINK("x")',
      date,
      fields: { amountMinor: "10001", status: "paid", currency: "BRL" },
    }),
    income = createItem("income", {
      title: "Salário",
      date,
      fields: { amountMinor: "30000", status: "received", currency: "BRL" },
    }),
    transfer = createItem("transfer", {
      title: "Transferir",
      date,
      fields: { amountMinor: "99900" },
    }),
    payment = createItem("expense", {
      title: "Fatura",
      date,
      fields: { ...expense.fields, paymentType: "card_payment" },
    });
  const filter = { from: "2026-01-01", to: "2026-01-31", currency: "BRL" };
  const report = reportRows([expense, income, transfer, payment], filter);
  assert.equal(report.expense, 10001);
  assert.equal(report.net, 19999);
  const csv = reportCSV([expense, income], filter);
  assert(csv.includes("'=HYPERLINK"));
  const rows = readCSV(csv);
  assert.equal(rows.length, 3);
  assert.equal(rows.find((r) => r[0] === "expense")?.[3], "100.01");
  assert.throws(() => readCSV('a;b\n"unfinished'));
});

test("large dependency chains validate without recursive stack overflow or repeated branches", () => {
  const records = Array.from({ length: 5000 }, (_, n) =>
    createItem("task", {
      id: "chain:" + n,
      fields: n ? { dependencies: "chain:" + (n - 1) } : {},
    }),
  );
  validateTaskDependencies(records[4999], records);
  assert.throws(
    () =>
      validateTaskDependencies(
        { ...records[0], fields: { dependencies: records[4999].id } },
        [
          { ...records[0], fields: { dependencies: records[4999].id } },
          ...records.slice(1),
        ],
      ),
    /ciclo/,
  );
});
