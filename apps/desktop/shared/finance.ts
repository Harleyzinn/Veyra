import {
  Item,
  Finance,
  Invoice,
  Point,
  amount,
  safe,
  addDays,
  addMonths,
  daysBetween,
  decimal,
  MAX_MINOR,
  today,
  money,
} from "./model";
export const currency = (i: Item) => i.fields.currency || "BRL";
export function status(i: Item) {
  const s = (i.fields.status || "").toLowerCase();
  if (
    [
      "received",
      "recebido",
      "recebida",
      "paid",
      "pago",
      "paga",
      "realizado",
      "realizada",
    ].includes(s)
  )
    return i.type === "income" ? "received" : "paid";
  if (["cancelled", "canceled", "cancelado", "cancelada"].includes(s))
    return "cancelled";
  if (["pending", "pendente"].includes(s)) return "pending";
  if (["expected", "previsto", "prevista"].includes(s)) return "expected";
  if (["overdue", "atrasado", "atrasada"].includes(s)) return "overdue";
  return s ||
    (i.type === "bill" && !i.done) ||
    (i.fields.planned === "Sim" && !i.done)
    ? i.type === "income"
      ? "expected"
      : "pending"
    : i.type === "income"
      ? "received"
      : "paid";
}
export const active = (i: Item) => !i.deletedAt && status(i) !== "cancelled";
export const settled = (i: Item) => ["paid", "received"].includes(status(i));
export const booked = (i: Item) => i.fields.settledDate || i.date;
export const due = (i: Item) => i.fields.dueDate || i.date;
export const legacyCard = (i: Item) =>
  i.fields.legacyCardCash === "yes" ||
  (i.type === "expense" &&
    !!i.fields.card &&
    i.fields.financialVersion !== "3" &&
    settled(i));
export function cashDelta(i: Item) {
  if (!active(i) || !settled(i)) return 0;
  return i.type === "income"
    ? amount(i)
    : i.type === "expense" &&
        (!i.fields.card ||
          legacyCard(i) ||
          i.fields.paymentType === "card_payment")
      ? -amount(i)
      : 0;
}
export function normalize(i: Item): Item {
  if (
    ["workspace", "notes", "tasks", "settings"].includes(
      i.type === "note"
        ? "notes"
        : i.type === "task"
          ? "tasks"
          : ["cloud_settings"].includes(i.type)
            ? "settings"
            : financialTypes.has(i.type)
              ? "financial"
              : "workspace",
    )
  )
    return i;
  const fields: Record<string, string> = {
    ...i.fields,
    currency: currency(i),
    financialVersion: "3",
  };
  if (legacyCard(i)) fields.legacyCardCash = "yes";
  for (const key of [
    "amount",
    "opening",
    "limit",
    "current",
    "saved",
    "paid",
    "payment",
    "monthlyContribution",
    "installmentAmount",
  ]) {
    const minor = key === "amount" ? "amountMinor" : key + "Minor";
    if (fields[key] || fields[minor]) {
      const n = amount({ ...i, fields }, key);
      if (
        n < 0 &&
        key !== "opening" &&
        !["cash_carry", "networth_snapshot", "month_close"].includes(i.type)
      )
        throw Error("Use valores positivos.");
      fields[minor] = String(n);
      fields[key] = decimal(n, currency(i));
    }
  }
  if (["income", "expense", "transfer", "bill"].includes(i.type)) {
    fields.status = status(i);
    fields.planned = settled(i) ? "Não" : "Sim";
  }
  return { ...i, fields };
}
export const financialTypes = new Set([
  "income",
  "expense",
  "transfer",
  "account",
  "card",
  "budget",
  "subscription",
  "recurring_rule",
  "recurrence_exception",
  "installment_plan",
  "investment",
  "debt",
  "savings_goal",
  "bill",
  "receivable",
  "financial_category",
  "financial_rule",
  "financial_template",
  "cash_carry",
  "card_carry",
  "networth_snapshot",
  "month_close",
  "financial_asset",
]);
export function validateFinance(item: Item, all: Item[]) {
  if (!financialTypes.has(item.type)) return;
  for (const key of ["account", "destination", "card"]) {
    const id = item.fields[key];
    if (id && active(item)) {
      const target = all.find((i) => i.id === id);
      if (
        !target ||
        target.deletedAt ||
        target.type !== (key === "card" ? "card" : "account") ||
        currency(target) !== currency(item)
      )
        throw Error("Use uma conta ou cartão ativo na mesma moeda.");
    }
  }
  if (
    item.type === "transfer" &&
    (!item.fields.account ||
      !item.fields.destination ||
      item.fields.account === item.fields.destination)
  )
    throw Error("Escolha duas contas diferentes.");
  if (item.type === "card")
    for (const k of ["closing", "due"]) {
      const d = Number(item.fields[k]);
      if (!Number.isInteger(d) || d < 1 || d > 31)
        throw Error("Fechamento e vencimento devem ficar entre 1 e 31.");
    }
  if (item.type === "savings_goal" && amount(item, "saved") > amount(item))
    throw Error("O valor reservado supera a meta.");
  if (item.type === "debt" && amount(item, "paid") > amount(item))
    throw Error("O valor pago supera a dívida.");
  if (isRule(item))
    ruleDates(
      item,
      item.fields.startDate || item.date,
      addDays(item.fields.startDate || item.date, 1),
    );
}
export function currentBalance(
  items: Item[],
  day = today(),
  cur = "BRL",
  accountId = "",
) {
  const all = items.filter((i) => currency(i) === cur);
  const carries = all.filter((i) => i.type === "cash_carry");
  const cutoff =
    carries
      .map((i) => i.date)
      .sort()
      .at(-1) || "";
  let value = all
    .filter(
      (i) =>
        i.type === "account" &&
        !i.deletedAt &&
        (!accountId || i.id === accountId),
    )
    .reduce((n, i) => safe(n + amount(i, "opening")), 0);
  value = carries
    .filter((i) => !accountId || i.fields.account === accountId)
    .reduce((n, i) => safe(n + amount(i)), value);
  for (const i of all) {
    if (
      !["income", "expense", "transfer"].includes(i.type) ||
      !i.date ||
      booked(i) > day ||
      (cutoff && booked(i) <= cutoff)
    )
      continue;
    if (accountId) {
      if (i.type === "transfer" && active(i) && settled(i)) {
        if (i.fields.account === accountId) value = safe(value - amount(i));
        if (i.fields.destination === accountId) value = safe(value + amount(i));
      } else if (i.fields.account === accountId)
        value = safe(value + cashDelta(i));
    } else value = safe(value + cashDelta(i));
  }
  return value;
}
export function cardDue(date: string, card: Item) {
  const close = Number(card.fields.closing || 1);
  const day = Number(card.fields.due || 10);
  let month = date.slice(0, 7) + "-01";
  const cl = addMonths(month, 0, close);
  if (date >= cl) month = addMonths(month, 1, 1);
  if (day <= close) month = addMonths(month, 1, 1);
  return addMonths(month, 0, day);
}
export function invoices(items: Item[], card: Item): Invoice[] {
  const groups = new Map<string, { total: number; carryPaid: number }>();
  for (const i of items) {
    if (currency(i) !== currency(card) || i.fields.card !== card.id) continue;
    if (
      (i.type === "expense" &&
        active(i) &&
        !legacyCard(i) &&
        i.fields.paymentType !== "card_payment") ||
      i.type === "card_carry"
    ) {
      const d = i.fields.dueDate || cardDue(i.date, card);
      const g = groups.get(d) || { total: 0, carryPaid: 0 };
      g.total = safe(g.total + amount(i));
      if (i.type === "card_carry")
        g.carryPaid = safe(g.carryPaid + amount(i, "paid"));
      groups.set(d, g);
    }
  }
  return [...groups]
    .map(([d, g]) => {
      const id = `invoice:${card.id}:${d}`;
      const paid = items
        .filter(
          (i) =>
            active(i) &&
            settled(i) &&
            i.fields.paymentType === "card_payment" &&
            i.fields.invoiceId === id,
        )
        .reduce((n, i) => safe(n + amount(i)), g.carryPaid);
      return {
        id,
        cardId: card.id,
        due: d,
        total: g.total,
        paid,
        remaining: Math.max(0, safe(g.total - paid)),
        currency: currency(card),
      };
    })
    .sort((a, b) => a.due.localeCompare(b.due));
}
export const isRule = (i: Item) =>
  ["recurring_rule", "subscription"].includes(i.type);
export function ruleDates(rule: Item, from: string, through: string) {
  const start = rule.fields.startDate || rule.date;
  const interval = Number(rule.fields.interval || 1);
  const count = Number(rule.fields.occurrenceCount || 10000);
  if (
    interval < 1 ||
    interval > 365 ||
    !Number.isInteger(interval) ||
    count < 1 ||
    count > 10000 ||
    !Number.isInteger(count) ||
    amount(rule) <= 0
  )
    throw Error("Confira o valor, intervalo e limite da recorrência.");
  const frequency = (rule.fields.frequency || "monthly").toLowerCase();
  const mapping: Record<string, [string, number]> = {
    daily: ["days", 1],
    diária: ["days", 1],
    weekly: ["days", 7],
    semanal: ["days", 7],
    fortnightly: ["days", 14],
    quinzenal: ["days", 14],
    monthly: ["months", 1],
    mensal: ["months", 1],
    bimonthly: ["months", 2],
    bimestral: ["months", 2],
    quarterly: ["months", 3],
    trimestral: ["months", 3],
    semiannual: ["months", 6],
    semestral: ["months", 6],
    yearly: ["months", 12],
    anual: ["months", 12],
    custom: [rule.fields.customUnit || "days", 1],
    personalizada: [rule.fields.customUnit || "days", 1],
  };
  let [unit, multiple] = mapping[frequency] || [];
  if (!unit) throw Error("Frequência de recorrência inválida.");
  if (unit === "weeks") {
    unit = "days";
    multiple *= 7;
  }
  if (unit === "years") {
    unit = "months";
    multiple *= 12;
  }
  const step = interval * multiple;
  const paused =
    rule.done || ["yes", "true", "Sim"].includes(rule.fields.paused);
  if (rule.deletedAt || (paused && !rule.fields.pausedAt)) return [];
  const end = [
    through,
    rule.fields.endDate || through,
    paused ? addDays(rule.fields.pausedAt, -1) : through,
  ].sort()[0];
  const windows = (rule.fields.pauseWindows || "")
    .split(";")
    .filter(Boolean)
    .map((v) => v.split("/"));
  const weekdays = (rule.fields.weekdays || "")
    .split(",")
    .filter(Boolean)
    .map(Number);
  const out: string[] = [];
  let accepted = 0;
  let inspected = 0;
  let d = start;
  const anchor = Number(rule.fields.dayOfMonth || start.slice(8));
  for (let index = 0; d <= end && accepted < count; index++) {
    if (inspected++ > 100000)
      throw Error("A recorrência ultrapassa a janela suportada.");
    if (
      weekdays.length &&
      ["daily", "diária", "weekly", "semanal"].includes(frequency)
    ) {
      d = addDays(start, index);
      if (d > end) break;
      const weekday = ((new Date(d + "T12:00:00Z").getUTCDay() + 6) % 7) + 1;
      const weekOffset = daysBetween(
        addDays(start, -((new Date(start + "T12:00:00Z").getUTCDay() + 6) % 7)),
        d,
      );
      if (
        !weekdays.includes(weekday) ||
        (step === 7 * interval
          ? Math.floor(weekOffset / 7) % interval !== 0
          : index % step !== 0)
      )
        continue;
    } else
      d =
        unit === "months"
          ? addMonths(start, index * step, anchor)
          : addDays(start, index * step);
    if (d > end) break;
    if (
      rule.fields.lastBusinessDay === "yes" ||
      rule.fields.lastBusinessDay === "Sim" ||
      rule.fields.lastBusinessDay === "true"
    ) {
      if (unit === "months") {
        d = addDays(addMonths(d.slice(0, 7) + "-01", 1, 1), -1);
        while ([0, 6].includes(new Date(d + "T12:00:00Z").getUTCDay()))
          d = addDays(d, -1);
      }
    }
    if (d < start || windows.some(([a, b]) => d >= a && d <= b)) continue;
    accepted++;
    if (d >= from) out.push(d);
  }
  return [...new Set(out)];
}
export function expanded(items: Item[], from: string, through: string): Item[] {
  const original = items.filter((i) =>
    ["income", "expense", "transfer"].includes(i.type),
  );
  const result = [...original];
  for (const rule of items.filter(isRule))
    for (const date of ruleDates(rule, from, through)) {
      const id = `recurring:${rule.id}:${date}`;
      if (items.some((i) => i.id === id)) continue;
      const exception = items.find(
        (i) =>
          i.type === "recurrence_exception" &&
          i.fields.recurrenceRuleId === rule.id &&
          (i.fields.occurrenceDate || i.date) === date &&
          active(i),
      );
      if (exception?.fields.action === "skip") continue;
      const fields: Record<string, string> = {
        ...rule.fields,
        ...exception?.fields,
        status: rule.fields.transactionType === "income" ? "expected" : "pending",
        planned: "Sim",
        source: rule.id,
        recurrenceRuleId: rule.id,
        occurrenceDate: date,
        virtual: "yes",
      };
      const i = {
        ...rule,
        id,
        type: rule.fields.transactionType || "expense",
        date,
        fields,
        done: false,
      };
      if (i.fields.card && i.type === "expense") {
        const card = items.find(
          (c) => c.id === i.fields.card && c.type === "card",
        );
        if (card) i.fields.dueDate = cardDue(date, card);
      }
      result.push(i);
    }
  for (const bill of items.filter(
    (i) =>
      i.type === "bill" &&
      active(i) &&
      !i.done &&
      i.date &&
      !items.some((p) => p.id === `bill-payment:${i.id}` && active(p)),
  ))
    result.push({
      ...bill,
      id: `bill-payment:${bill.id}`,
      type: "expense",
      fields: {
        ...bill.fields,
        status: "pending",
        planned: "Sim",
        source: bill.id,
        virtual: "yes",
      },
    });
  return result.filter(active);
}
export function projection(
  items: Item[],
  day: string,
  through: string,
  cur = "BRL",
) {
  const starts = items
    .filter((i) => isRule(i) && !i.deletedAt)
    .map((i) => i.fields.startDate || i.date)
    .filter(Boolean);
  const cutoff = items
    .filter((i) => i.type === "cash_carry")
    .map((i) => addDays(i.date, 1))
    .sort()[0];
  const start = cutoff || [day, ...starts].sort()[0];
  if (daysBetween(start, through) > 3660)
    throw Error(
      "Carregue uma janela de até dez anos para projetar recorrências.",
    );
  const transactions = expanded(items, start, through).filter(
    (i) => currency(i) === cur,
  );
  const flow: {
    date: string;
    amount: number;
    input: boolean;
    title: string;
  }[] = [];
  for (const i of transactions) {
    if (
      i.type === "transfer" ||
      (i.type === "expense" &&
        i.fields.card &&
        !legacyCard(i) &&
        i.fields.paymentType !== "card_payment")
    )
      continue;
    const date = settled(i) ? booked(i) : due(i);
    if ((!settled(i) || date > day) && date <= through)
      flow.push({
        date: date < day ? day : date,
        amount: amount(i),
        input: i.type === "income",
        title: i.title,
      });
  }
  const inv = items
    .filter((i) => i.type === "card" && !i.deletedAt && currency(i) === cur)
    .flatMap((card) =>
      invoices(
        [...transactions, ...items.filter((i) => i.type === "card_carry")],
        card,
      ),
    );
  for (const i of inv)
    if (i.remaining > 0 && i.due <= through)
      flow.push({
        date: i.due < day ? day : i.due,
        amount: i.remaining,
        input: false,
        title: "Fatura de cartão",
      });
  let balance = currentBalance(items, day, cur);
  let income = 0,
    expense = 0;
  const points: Point[] = [
    { date: day, balance, income: 0, expense: 0, labels: ["Saldo atual"] },
  ];
  for (const date of [...new Set(flow.map((i) => i.date))].sort()) {
    const daily = flow.filter((i) => i.date === date);
    const input = daily
      .filter((i) => i.input)
      .reduce((a, i) => safe(a + i.amount), 0);
    const output = daily
      .filter((i) => !i.input)
      .reduce((a, i) => safe(a + i.amount), 0);
    income = safe(income + input);
    expense = safe(expense + output);
    balance = safe(balance + input - output);
    points.push({
      date,
      balance,
      income: input,
      expense: output,
      labels: daily.map((i) => i.title),
    });
  }
  if (points.at(-1)?.date !== through)
    points.push({ date: through, balance, income: 0, expense: 0, labels: [] });
  return { income, expense, balance, points };
}
export function finance(
  items: Item[],
  month = today().slice(0, 7),
  cur = "BRL",
  days = 30,
  financialDay = 1,
): Finance {
  const day = today();
  const from = addMonths(month + "-01", 0, financialDay);
  const through = addDays(addMonths(month + "-01", 1, financialDay), -1);
  const data = expanded(items, from, through).filter(
    (i) => currency(i) === cur && i.date >= from && i.date <= through,
  );
  const previousFrom = addMonths(month + "-01", -1, financialDay);
  const prior = expanded(items, previousFrom, addDays(from, -1)).filter(
    (i) => currency(i) === cur && i.date >= previousFrom && i.date < from,
  );
  const recognized = (i: Item) =>
    active(i) &&
    i.type === "expense" &&
    i.fields.paymentType !== "card_payment" &&
    ((settled(i) && booked(i) <= day) || (!!i.fields.card && i.date <= day));
  const income = data
    .filter((i) => i.type === "income" && settled(i) && booked(i) <= day)
    .reduce((n, i) => safe(n + amount(i)), 0);
  const expense = data
    .filter(recognized)
    .reduce((n, i) => safe(n + amount(i)), 0);
  const previousExpense = prior
    .filter(recognized)
    .reduce((n, i) => safe(n + amount(i)), 0);
  const categories = new Map<string, number>();
  data.filter(recognized).forEach((i) => {
    const c = i.fields.category || "Sem categoria";
    categories.set(c, safe((categories.get(c) || 0) + amount(i)));
  });
  const budgets = items
    .filter(
      (i) =>
        i.type === "budget" &&
        !i.deletedAt &&
        currency(i) === cur &&
        (!i.fields.month || i.fields.month === month),
    )
    .map((item) => ({
      item,
      limit: amount(item),
      used: data
        .filter(
          (i) =>
            recognized(i) &&
            (i.fields.category || "").toLowerCase() ===
              (item.fields.category || "").toLowerCase() &&
            (!item.fields.subcategory ||
              item.fields.subcategory === i.fields.subcategory),
        )
        .reduce((n, i) => safe(n + amount(i)), 0),
    }));
  const categoryRows = [...categories]
    .map(([name, value]) => ({ name, value }))
    .sort((a, b) => b.value - a.value);
  const insights: string[] = [];
  if (previousExpense > 0)
    insights.push(
      `Seus gastos ${expense >= previousExpense ? "aumentaram" : "diminuíram"} ${Math.abs((expense / previousExpense - 1) * 100).toFixed(1)}% em relação ao período anterior.`,
    );
  if (categoryRows.length)
    insights.push(
      `Sua maior categoria de gastos é ${categoryRows[0].name}: ${money(categoryRows[0].value, cur)}.`,
    );
  for (const b of budgets)
    if (b.limit > 0 && (b.used * 100) / b.limit >= 75)
      insights.push(
        `${b.item.title}: ${Math.round((b.used * 100) / b.limit)}% do orçamento utilizado.`,
      );
  return {
    balance: currentBalance(items, day, cur),
    income,
    expense,
    savings: safe(income - expense),
    previousExpense,
    currency: cur,
    month,
    forecast: projection(items, day, addDays(day, days), cur),
    categories: categoryRows,
    accounts: items
      .filter(
        (i) => i.type === "account" && !i.deletedAt && currency(i) === cur,
      )
      .map((item) => ({
        item,
        balance: currentBalance(items, day, cur, item.id),
      })),
    invoices: items
      .filter((i) => i.type === "card" && !i.deletedAt && currency(i) === cur)
      .flatMap((card) => invoices(items, card)),
    budgets,
    insights,
  };
}
export function simulate(
  base: Finance["forecast"],
  date: string,
  input: number,
  output: number,
  reserve: number,
) {
  if (
    [input, output, reserve].some(
      (n) => !Number.isSafeInteger(n) || n < 0 || n > MAX_MINOR,
    )
  )
    throw Error("Use valores positivos dentro do limite.");
  const start = base.points[0].date,
    end = base.points.at(-1)!.date;
  if (date < start || date > end)
    throw Error("A data deve estar dentro do horizonte.");
  const grouped = new Map<string, Point>();
  for (const p of base.points) {
    const before = grouped.get(p.date);
    grouped.set(p.date, {
      ...p,
      income: safe((before?.income || 0) + p.income),
      expense: safe((before?.expense || 0) + p.expense),
      labels: [...(before?.labels || []), ...p.labels],
    });
  }
  if (!grouped.has(date)) {
    const previous = [...grouped.values()]
      .filter((p) => p.date < date)
      .sort((a, b) => a.date.localeCompare(b.date))
      .at(-1)!;
    grouped.set(date, {
      date,
      balance: previous.balance,
      income: 0,
      expense: 0,
      labels: [],
    });
  }
  const points = [...grouped.values()]
    .sort((a, b) => a.date.localeCompare(b.date))
    .map((p) => ({
      ...p,
      balance: p.date >= date ? safe(p.balance + input - output) : p.balance,
      income: safe(p.income + (p.date === date ? input : 0)),
      expense: safe(p.expense + (p.date === date ? output : 0)),
    }));
  const checkpoints = [...points];
  points.slice(1).forEach((p, index) => {
    const before = points[index];
    if (daysBetween(before.date, p.date) > 1)
      checkpoints.push({ ...before, date: addDays(p.date, -1) });
  });
  return {
    points,
    balance: safe(base.balance + input - output),
    lowest: Math.min(...points.map((p) => p.balance)),
    daily: Math.min(
      ...checkpoints.map((p) =>
        Math.floor(
          Math.max(0, safe(p.balance - reserve)) /
            (daysBetween(start, p.date) + 1),
        ),
      ),
    ),
    below: points.find((p) => p.balance < reserve)?.date || "",
  };
}
export function installmentItems(item: Item, count: number, all: Item[]) {
  if (
    !Number.isInteger(count) ||
    count < 1 ||
    count > 120 ||
    item.type !== "expense" ||
    amount(item) <= 0
  )
    throw Error("Use de 1 a 120 parcelas para uma despesa.");
  const card = all.find((i) => i.id === item.fields.card);
  const total = amount(item);
  const children = Array.from({ length: count }, (_, index) => {
    const value = Math.floor(total / count) + (index < total % count ? 1 : 0);
    const date = addMonths(item.date, index);
    return normalize({
      ...item,
      id: `installment:${item.id}:${index}`,
      title: `${item.title.slice(0, 170)} • ${index + 1}/${count}`,
      date,
      fields: {
        ...item.fields,
        amountMinor: String(value),
        amount: decimal(value, currency(item)),
        installmentPlanId: item.id,
        installmentIndex: String(index + 1),
        installmentCount: String(count),
        purchaseDate: item.date,
        dueDate: card ? cardDue(date, card) : date,
        status: card || date > today() ? "pending" : "paid",
      },
    });
  });
  return [
    normalize({
      ...item,
      type: "installment_plan",
      fields: {
        ...item.fields,
        count: String(count),
        installments: String(count),
        firstInstallment: item.date,
      },
    }),
    ...children,
  ];
}
