import { test } from "node:test";
import assert from "node:assert/strict";
import { businessDay, scheduleDates, CalendarRules } from "../calendar";
import {
  emptyData,
  createItem,
  saveChanges,
  transactions,
  confirmTransaction,
  payInvoice,
  allInvoices,
  safeSpending,
  monthly,
  installmentItems,
  amount,
  editSchedule,
  importPreview,
  csv,
  automaticChanges,
  csvPreview,
  togglePause,
  validatePreferences,
} from "../domain";
import { currentBalance as balance } from "../../desktop/shared/finance";
import { themes } from "../themes";
const rules: CalendarRules = { saturday: false, national: true, holidays: [] };
const account = (id = "bank", opening = 100000) =>
  createItem("account", {
    id,
    title: id,
    fields: { openingMinor: String(opening), currency: "BRL" },
  });
const row = (
  type: string,
  id: string,
  value: number,
  date = "2026-10-10",
  fields: Record<string, string> = {},
) =>
  createItem(type, {
    id,
    title: id,
    date,
    fields: {
      amountMinor: String(value),
      currency: "BRL",
      account: "bank",
      status: type === "income" ? "received" : "paid",
      financialVersion: "3",
      ...fields,
    },
  });
test("5º dia útil: sábado, feriado e virada de ano", () => {
  assert.equal(businessDay("2026-01", 5, rules), "2026-01-08");
  assert.equal(
    businessDay("2026-01", 5, { ...rules, saturday: true }),
    "2026-01-07",
  );
  assert.equal(
    businessDay("2026-01", 5, { ...rules, holidays: ["2026-01-02"] }),
    "2026-01-09",
  );
  assert.equal(businessDay("2027-01", 5, rules), "2027-01-08");
});
test("dia fixo em fevereiro bissexto e ajuste antes/depois", () => {
  const rule = row("salary_schedule", "salary", 500000, "2024-01-31", {
    frequency: "monthly",
    day1: "31",
  });
  assert.deepEqual(scheduleDates(rule, "2024-02-01", "2024-02-29", rules), [
    "2024-02-29",
  ]);
  const jan = row("salary_schedule", "s", 1, "2026-01-01", {
    frequency: "monthly",
    day1: "10",
    adjust: "before",
  });
  assert.equal(
    scheduleDates(jan, "2026-01-01", "2026-01-31", rules)[0],
    "2026-01-09",
  );
  assert.equal(
    scheduleDates(
      { ...jan, fields: { ...jan.fields, adjust: "after" } },
      "2026-01-01",
      "2026-01-31",
      rules,
    )[0],
    "2026-01-12",
  );
});
test("semanal, duas semanas, duas datas e avulsa sem duplicação", () => {
  const s = row("salary_schedule", "s", 100, "2026-01-01", {
    frequency: "weekly",
  });
  assert.equal(scheduleDates(s, "2026-01-01", "2026-01-31", rules).length, 5);
  assert.equal(
    scheduleDates(
      { ...s, fields: { ...s.fields, frequency: "fortnightly" } },
      s.date,
      "2026-01-31",
      rules,
    ).length,
    3,
  );
  assert.equal(
    scheduleDates(
      {
        ...s,
        fields: { ...s.fields, frequency: "twice", day1: "31", day2: "30" },
      },
      "2026-02-01",
      "2026-02-28",
      rules,
    ).length,
    1,
  );
  assert.deepEqual(
    scheduleDates(
      { ...s, fields: { ...s.fields, frequency: "once" } },
      s.date,
      "2026-12-31",
      rules,
    ),
    [s.date],
  );
  assert.throws(() =>
    scheduleDates(
      { ...s, fields: { ...s.fields, day1: "0" } },
      s.date,
      "2026-12-31",
      rules,
    ),
  );
});
test("previsto não é saldo e confirmar recebimento é idempotente", () => {
  let data = saveChanges(emptyData(), [
    account(),
    row("salary_schedule", "salary", 500000, "2026-10-01", {
      frequency: "business",
      businessNth: "5",
      transactionType: "income",
    }),
  ]);
  const occurrence = transactions(data, "2026-10-01", "2026-10-31").find(
    (i) => i.type === "income",
  )!;
  assert.equal(balance(data.items, "2026-10-19"), 100000);
  data = confirmTransaction(data, occurrence, 490000, "2026-10-08");
  assert.equal(balance(data.items, "2026-10-19"), 590000);
  assert.deepEqual(
    confirmTransaction(data, occurrence, 490000, "2026-10-08"),
    data,
  );
  assert.equal(
    transactions(data, "2026-10-01", "2026-10-31").filter(
      (i) => i.type === "income",
    ).length,
    1,
  );
});
test("pagar conta atualiza saldo uma única vez", () => {
  let d = saveChanges(emptyData(), [
    account(),
    row("expense", "bill", 15000, "2026-10-10", { status: "pending" }),
  ]);
  d = confirmTransaction(d, d.items[1], 15000, "2026-10-11");
  assert.equal(balance(d.items, "2026-10-19"), 85000);
  assert.deepEqual(confirmTransaction(d, d.items[1], 15000, "2026-10-11"), d);
});
test("transferência preserva saldo total, receita e despesa", () => {
  const d = saveChanges(emptyData(), [
    account(),
    account("cash", 0),
    row("transfer", "t", 10000, "2026-10-10", { destination: "cash" }),
  ]);
  assert.equal(balance(d.items, "2026-10-19", "BRL", "bank"), 90000);
  assert.equal(balance(d.items, "2026-10-19", "BRL", "cash"), 10000);
  assert.equal(balance(d.items, "2026-10-19"), 100000);
  assert.equal(monthly(d, "2026-10").income, 0);
  assert.equal(monthly(d, "2026-10").expense, 0);
});
test("parcelas somam exatamente o total e fechamento determina fatura", () => {
  const c = createItem("card", {
    id: "card",
    title: "Cartão",
    fields: { limitMinor: "300000", closing: "5", due: "12", currency: "BRL" },
  });
  const original = row("expense", "purchase", 10000, "2026-10-06", {
    card: "card",
  });
  const children = installmentItems(original, 3, [c]);
  assert.equal(
    children
      .filter((i) => i.type === "expense")
      .reduce((n, i) => n + amount(i), 0),
    10000,
  );
  assert.equal(
    children.find((i) => i.type === "expense")!.fields.dueDate,
    "2026-11-12",
  );
});
test("pagar fatura não duplica gasto e é idempotente", () => {
  const c = createItem("card", {
    id: "card",
    title: "Cartão",
    fields: { limitMinor: "300000", closing: "5", due: "12", currency: "BRL" },
  });
  let d = saveChanges(emptyData(), [
    account(),
    c,
    row("expense", "p", 10000, "2026-10-01", {
      card: "card",
      status: "pending",
    }),
  ]);
  const inv = allInvoices(d, "2026-01-01", "2027-12-31")[0];
  assert.equal(inv.due, "2026-10-12");
  assert.equal(balance(d.items, "2026-10-19"), 100000);
  d = payInvoice(d, inv.id, "bank", "2026-10-12");
  assert.equal(balance(d.items, "2026-10-19"), 90000);
  assert.equal(monthly(d, "2026-10").expense, 10000);
  assert.deepEqual(payInvoice(d, inv.id, "bank", "2026-10-12"), d);
});
test("gastar considera atrasos, faturas, reservas e margem sem renda futura", () => {
  const d = saveChanges(
    { ...emptyData(), preferences: { marginMinor: "5000" } },
    [
      account(),
      row("expense", "bill", 10000, "2026-09-10", { status: "pending" }),
      row("income", "future", 500000, "2026-10-25", { status: "expected" }),
      createItem("savings_goal", {
        title: "Meta",
        fields: {
          amountMinor: "50000",
          savedMinor: "20000",
          monthlyContributionMinor: "1000",
        },
      }),
    ],
  );
  const s = safeSpending(d, "2026-10-19");
  assert.equal(s.total, 64000);
  assert.equal(s.expected, 500000);
});
test("reserva em conta excluída não é descontada duas vezes", () => {
  const reserve = {
    ...account("reserve", 20000),
    fields: { ...account("reserve", 20000).fields, includeAvailable: "no" },
  };
  const d = saveChanges(emptyData(), [
    account(),
    reserve,
    createItem("savings_goal", {
      title: "Meta",
      fields: { account: "reserve", amountMinor: "50000", savedMinor: "20000" },
    }),
  ]);
  assert.equal(safeSpending(d, "2026-10-19").balance, 100000);
  assert.equal(safeSpending(d, "2026-10-19").reserved, 0);
});
test("editar esta ocorrência e próximas preserva histórico confirmado", () => {
  const s = row("salary_schedule", "s", 100000, "2026-09-01", {
    frequency: "monthly",
    day1: "5",
  });
  let d = saveChanges(emptyData(), [account(), s]);
  const occurrence = transactions(d, "2026-09-01", "2026-09-30")[0];
  d = confirmTransaction(d, occurrence, 100000, "2026-09-05");
  const changed = { ...s, fields: { ...s.fields, amountMinor: "150000" } };
  d = editSchedule(d, s, "2026-10-05", changed, "future");
  assert.equal(amount(d.items.find((i) => i.id === occurrence.id)!), 100000);
  assert.equal(
    transactions(d, "2026-11-01", "2026-11-30").filter(
      (i) => i.type === "income" && i.date.startsWith("2026-11"),
    ).length,
    1,
  );
  assert.equal(
    amount(
      transactions(d, "2026-11-01", "2026-11-30").find(
        (i) => i.type === "income" && i.date.startsWith("2026-11"),
      )!,
    ),
    150000,
  );
  assert.throws(() => editSchedule(d, s, "2026-09-05", changed, "one"));
});
test("backup restaura em novo espaço e ignora IDs duplicados", () => {
  const d = saveChanges(emptyData(), [account(), row("expense", "e", 100)]);
  const p = importPreview(JSON.stringify(d), emptyData());
  assert.equal(p.items.length, 2);
  const restored = saveChanges(emptyData(), p.items);
  assert.equal(balance(restored.items, "2026-10-19"), 99900);
  assert.equal(importPreview(JSON.stringify(d), restored).items.length, 0);
  assert.throws(() => importPreview('{"items":[{"id":"invalid"}]}', d));
});
test("modo simples/completo mantém os mesmos registros", () => {
  const d = saveChanges(emptyData(), [account(), row("expense", "e", 100)]);
  const changed = { ...d, preferences: { ...d.preferences, mode: "complete" } };
  assert.deepEqual(changed.items, d.items);
  assert.deepEqual(monthly(changed, "2026-10"), monthly(d, "2026-10"));
});
test("registro automático é explícito e não repete ocorrências", () => {
  const s = row("salary_schedule", "s", 100000, "2026-10-01", {
    frequency: "monthly",
    day1: "5",
    mode: "automatic",
  });
  const d = automaticChanges(
    saveChanges(emptyData(), [account(), s]),
    "2026-10-19",
  );
  assert.equal(d.items.filter((i) => i.type === "income").length, 1);
  assert.deepEqual(automaticChanges(d, "2026-10-19"), d);
});
test("CSV neutraliza fórmulas e mantém centavos", () => {
  const text = csv([row("expense", '=HYPERLINK("malicious")', 123)]);
  assert.match(text, /1,23/);
  assert.match(text, /'=HYPERLINK/);
});
const luminance = (hex: string) => {
  const c = hex
    .match(/\w\w/g)!
    .map((v) => parseInt(v, 16) / 255)
    .map((n) => (n <= 0.04045 ? n / 12.92 : ((n + 0.055) / 1.055) ** 2.4));
  return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
};
const contrast = (a: string, b: string) => {
  const x = luminance(a),
    y = luminance(b);
  return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
};
test("24 temas completos têm texto legível em fundo e superfície", () => {
  assert.equal(themes.length, 24);
  assert.equal(new Set(themes.map((t) => t.id)).size, 24);
  for (const t of themes) {
    assert.ok(contrast(t.text, t.surface) >= 4.5, t.name + " text");
    assert.ok(contrast(t.muted, t.surface) >= 4.5, t.name + " muted");
    assert.ok(contrast(t.onAccent, t.accent) >= 4.5, t.name + " button");
  }
});
test("pausar e retomar não regenera ocorrências do intervalo pausado", () => {
  const s = row("salary_schedule", "s", 100, "2026-01-01", {
    frequency: "monthly",
    day1: "5",
  });
  const paused = togglePause(s, "2026-02-01");
  const resumed = togglePause(paused, "2026-04-01");
  assert.deepEqual(scheduleDates(resumed, "2026-01-01", "2026-04-30", rules), [
    "2026-01-05",
    "2026-04-05",
  ]);
});
test("CSV com aspas e quebra de linha restaura centavos e deduplica", async () => {
  const original = row("expense", "e", 1234, "2026-10-10", {
    category: "Lazer",
  });
  original.title = 'Cinema; "sessão"';
  original.notes = "local";
  const exported = csv([original]);
  const p = await csvPreview(exported, emptyData());
  assert.equal(p.items.length, 1);
  assert.equal(amount(p.items[0]), 1234);
  assert.equal(p.items[0].title, original.title);
  const restored = saveChanges(emptyData(), p.items);
  assert.equal((await csvPreview(exported, restored)).items.length, 0);
  assert.equal(
    (
      await csvPreview(
        exported,
        saveChanges(emptyData(), [account(), original]),
      )
    ).items.length,
    0,
  );
});
test("preferências inválidas de backup são recusadas antes de gravar", () => {
  assert.throws(() => validatePreferences({ marginMinor: "NaN" }));
  assert.throws(() => validatePreferences({ holidays: "2026-02-30" }));
  assert.throws(() =>
    importPreview(
      '{"items":[],"preferences":{"savingPercent":"999"}}',
      emptyData(),
    ),
  );
});
test("histórico de meta é preservado no backup e nas próximas alterações", () => {
  const goal = createItem("savings_goal", {
    id: "goal",
    title: "Reserva",
    fields: { amountMinor: "100000", savedMinor: "1000" },
  });
  let d = saveChanges(emptyData(), [goal]);
  assert.equal(JSON.parse(d.items[0].fields.goalHistory).length, 1);
  d = saveChanges(d, [
    { ...d.items[0], fields: { ...d.items[0].fields, savedMinor: "2000" } },
  ]);
  const p = importPreview(JSON.stringify(d), emptyData()),
    restored = saveChanges(emptyData(), p.items);
  assert.deepEqual(
    JSON.parse(restored.items[0].fields.goalHistory),
    JSON.parse(d.items[0].fields.goalHistory),
  );
});
test("déficit permanece negativo no indicador e na estimativa diária", () => {
  const d = saveChanges(emptyData(), [
    account("bank", 1000),
    row("expense", "pending", 2000, "2026-10-20", { status: "pending" }),
  ]);
  const s = safeSpending(d, "2026-10-19");
  assert.equal(s.total, -1000);
  assert.ok(s.daily < 0);
});
test("fatura em outra moeda não é misturada ao disponível em reais", () => {
  const c = createItem("card", {
    id: "usd-card",
    title: "USD",
    fields: { currency: "USD", limitMinor: "100000", closing: "5", due: "12" },
  });
  const purchase = row("expense", "usd-expense", 10000, "2026-10-01", {
    currency: "USD",
    card: c.id,
    account: "",
    status: "pending",
  });
  const d = saveChanges(emptyData(), [account(), c, purchase]);
  assert.equal(allInvoices(d, "2026-01-01", "2026-12-31").length, 0);
  assert.equal(safeSpending(d, "2026-10-19").total, 100000);
});
