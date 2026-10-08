import { test } from "node:test";
import assert from "node:assert/strict";
import {
  createItem,
  parseMinor,
  amount,
  decimal,
  validDate,
  MAX_MINOR,
} from "../shared/model";
import {
  normalize,
  currentBalance,
  cardDue,
  invoices,
  finance,
  projection,
  ruleDates,
  expanded,
  simulate,
  installmentItems,
} from "../shared/finance";
const account = () =>
  normalize(
    createItem("account", {
      id: "bank",
      title: "Banco",
      fields: { openingMinor: "100000", currency: "BRL" },
    }),
  );
const transaction = (
  type: string,
  value: number,
  date = "2026-10-07",
  fields: Record<string, string> = {},
) =>
  normalize(
    createItem(type, {
      title: type,
      date,
      fields: {
        amountMinor: String(value),
        currency: "BRL",
        account: "bank",
        status: type === "income" ? "received" : "paid",
        ...fields,
      },
    }),
  );
test("money preserves cents, locale, grouping and currency precision", () => {
  assert.equal(parseMinor("1.234,56"), 123456);
  assert.equal(parseMinor("1,234.56", "USD"), 123456);
  assert.equal(parseMinor("R$ 1.234"), 123400);
  assert.equal(parseMinor("12.001", "KWD"), 12001);
  assert.equal(parseMinor("120", "JPY"), 120);
  assert.equal(decimal(12001, "KWD"), "12.001");
  assert.throws(() => parseMinor("1.001", "BRL"));
  assert.throws(() => parseMinor("1,23.45"));
  assert.throws(() => parseMinor(String(MAX_MINOR + 1) + "00"));
});
test("civil dates reject impossible days and accept leap years", () => {
  assert.equal(validDate("2026-02-29"), false);
  assert.equal(validDate("2024-02-29"), true);
  assert.equal(validDate("2026-04-31"), false);
});
test("current cash excludes future, pending and card purchases", () => {
  const card = normalize(
    createItem("card", {
      id: "card",
      title: "Cartão",
      fields: { closing: "10", due: "20", currency: "BRL" },
    }),
  );
  const expense = transaction("expense", 10001, "2026-10-07", {
    card: "card",
    status: "pending",
  });
  const items = [
    account(),
    card,
    transaction("income", 1000),
    expense,
    transaction("expense", 500, "2026-10-07", { status: "pending" }),
    transaction("income", 900, "2026-12-01"),
  ];
  assert.equal(currentBalance(items, "2026-10-07"), 101000);
  assert.equal(finance(items, "2026-10").expense, 10001);
});
test("legacy card cash survives an edit and normalization", () => {
  const legacy = createItem("expense", {
    title: "Legado",
    date: "2026-10-07",
    fields: { amount: "100.00", card: "card", account: "bank", status: "paid" },
  });
  assert.equal(normalize(legacy).fields.legacyCardCash, "yes");
  assert.equal(
    currentBalance([account(), normalize(legacy)], "2026-10-07"),
    90000,
  );
});
test("transfers preserve total and move only account balances", () => {
  const second = normalize(
    createItem("account", {
      id: "other",
      title: "Carteira",
      fields: { openingMinor: "0" },
    }),
  );
  const transfer = transaction("transfer", 10000, "2026-10-07", {
    destination: "other",
  });
  assert.equal(
    currentBalance([account(), second, transfer], "2026-10-07"),
    100000,
  );
  assert.equal(
    currentBalance([account(), second, transfer], "2026-10-07", "BRL", "bank"),
    90000,
  );
  assert.equal(
    currentBalance([account(), second, transfer], "2026-10-07", "BRL", "other"),
    10000,
  );
});
test("card closing-day purchases move to next invoice", () => {
  const card = createItem("card", {
    title: "Cartão",
    fields: { closing: "10", due: "20" },
  });
  assert.equal(cardDue("2026-10-09", card), "2026-10-20");
  assert.equal(cardDue("2026-10-10", card), "2026-11-20");
  const end = createItem("card", {
    title: "Fim",
    fields: { closing: "31", due: "5" },
  });
  assert.equal(cardDue("2026-02-28", end), "2026-04-05");
});
test("partial invoice payment subtracts cash once and keeps exact remainder", () => {
  const card = createItem("card", {
    id: "card",
    title: "Cartão",
    fields: { closing: "10", due: "20", currency: "BRL" },
  });
  const purchase = transaction("expense", 10001, "2026-10-07", {
    card: "card",
    status: "pending",
  });
  const id = "invoice:card:2026-10-20";
  const payment = transaction("expense", 3333, "2026-10-07", {
    card: "card",
    paymentType: "card_payment",
    invoiceId: id,
    settledDate: "2026-10-07",
  });
  const items = [account(), card, purchase, payment];
  const invoice = invoices(items, card)[0];
  assert.equal(invoice.remaining, 6668);
  assert.equal(invoice.paid, 3333);
  assert.equal(currentBalance(items, "2026-10-07"), 96667);
  assert.equal(finance(items, "2026-10").expense, 10001);
});
test("monthly recurrence stays anchored on day 31 without drift", () => {
  const rule = normalize(
    createItem("recurring_rule", {
      id: "r",
      title: "Aluguel",
      date: "2026-01-31",
      fields: {
        amountMinor: "10000",
        frequency: "monthly",
        startDate: "2026-01-31",
        transactionType: "expense",
      },
    }),
  );
  assert.deepEqual(ruleDates(rule, "2026-01-01", "2026-04-30"), [
    "2026-01-31",
    "2026-02-28",
    "2026-03-31",
    "2026-04-30",
  ]);
});
test("recurrence overrides and tombstones do not duplicate predictions", () => {
  const rule = normalize(
    createItem("recurring_rule", {
      id: "r",
      title: "Salário",
      date: "2026-10-07",
      fields: {
        amountMinor: "10000",
        frequency: "monthly",
        transactionType: "income",
      },
    }),
  );
  const override = transaction("income", 11000, "2026-10-07", {
    source: "r",
    recurrenceRuleId: "r",
  });
  override.id = "recurring:r:2026-10-07";
  assert.equal(
    expanded([rule, override], "2026-10-01", "2026-10-31").length,
    1,
  );
  assert.equal(
    expanded([rule, { ...override, deletedAt: 1 }], "2026-10-01", "2026-10-31")
      .length,
    0,
  );
});
test("paused recurring rules preserve past commitments without creating later ones", () => {
  const rule = normalize(
    createItem("recurring_rule", {
      title: "Internet",
      date: "2026-01-10",
      done: true,
      fields: {
        amountMinor: "1000",
        frequency: "monthly",
        paused: "yes",
        pausedAt: "2026-03-01",
      },
    }),
  );
  assert.deepEqual(ruleDates(rule, "2026-01-01", "2026-05-01"), [
    "2026-01-10",
    "2026-02-10",
  ]);
});
test("forecast uses future incomes and outstanding invoice without double charges", () => {
  const card = createItem("card", {
    id: "card",
    title: "Cartão",
    fields: { closing: "10", due: "20" },
  });
  const items = [
    account(),
    card,
    transaction("income", 280000, "2026-10-15", { status: "expected" }),
    transaction("expense", 195000, "2026-10-07", {
      card: "card",
      status: "pending",
    }),
  ];
  const projected = projection(items, "2026-10-07", "2026-11-07");
  assert.equal(projected.income, 280000);
  assert.equal(projected.expense, 195000);
  assert.equal(projected.balance, 185000);
});
test("installments distribute odd cents exactly and preserve calendar anchor", () => {
  const result = installmentItems(
    transaction("expense", 10001, "2026-01-31"),
    3,
    [],
  );
  assert.deepEqual(
    result.slice(1).map((i) => amount(i)),
    [3334, 3334, 3333],
  );
  assert.deepEqual(
    result.slice(1).map((i) => i.date),
    ["2026-01-31", "2026-02-28", "2026-03-31"],
  );
  assert.equal(result[0].type, "installment_plan");
});
test("simulator never borrows from future deposit and never changes the base", () => {
  const base = {
    income: 100000,
    expense: 0,
    balance: 100900,
    points: [
      { date: "2026-10-07", balance: 900, income: 0, expense: 0, labels: [] },
      {
        date: "2026-10-16",
        balance: 100900,
        income: 100000,
        expense: 0,
        labels: [],
      },
      {
        date: "2026-10-26",
        balance: 100900,
        income: 0,
        expense: 0,
        labels: [],
      },
    ],
  };
  const before = JSON.stringify(base);
  assert.equal(simulate(base, "2026-10-07", 0, 0, 0).daily, 100);
  assert.equal(JSON.stringify(base), before);
});
test("reserve and one-off expense use exact daily floor", () => {
  const base = {
    income: 0,
    expense: 0,
    balance: 10000,
    points: [
      { date: "2026-10-07", balance: 10000, income: 0, expense: 0, labels: [] },
      { date: "2026-10-16", balance: 10000, income: 0, expense: 0, labels: [] },
    ],
  };
  const result = simulate(base, "2026-10-10", 0, 1000, 4000);
  assert.equal(result.balance, 9000);
  assert.equal(result.daily, 500);
  assert.equal(result.points[0].balance, 10000);
  assert.throws(() => simulate(base, "2026-10-06", 0, 0, 0));
});
