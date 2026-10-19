import {
  Item,
  createItem,
  amount,
  today,
  addDays,
  addMonths,
  daysBetween,
  safe,
  validateItem,
  validDate,
  decimal,
} from "../desktop/shared/model";
import {
  active,
  settled,
  due,
  booked,
  currency,
  currentBalance,
  expanded,
  invoices,
  normalize,
  validateFinance,
  installmentItems,
  cardDue,
} from "../desktop/shared/finance";
import { CalendarRules, scheduleDates } from "./calendar";
export {
  amount,
  createItem,
  today,
  addDays,
  addMonths,
  daysBetween,
  decimal,
  validDate,
  active,
  settled,
  due,
  booked,
  normalize,
  installmentItems,
  cardDue,
};
export const categories = [
  "Moradia",
  "Alimentação",
  "Transporte",
  "Saúde",
  "Educação",
  "Lazer",
  "Compras",
  "Assinaturas",
  "Salário",
  "Outros",
];
export function categoryNames(items: Item[]) {
  const custom = items.filter(
    (i) => active(i) && i.type === "financial_category",
  );
  const hidden = new Set(
    custom.flatMap((i) =>
      [i.fields.builtinName, ...(i.done ? [i.title] : [])].filter(Boolean),
    ),
  );
  return [
    ...new Set([
      ...categories.filter((c) => !hidden.has(c)),
      ...custom.filter((i) => !i.done).map((i) => i.title),
    ]),
  ];
}
export interface Data {
  version: 1;
  revision: number;
  items: Item[];
  preferences: Record<string, string>;
}
export const emptyData = (): Data => ({
  version: 1,
  revision: 0,
  items: [],
  preferences: { mode: "simple", theme: "emerald" },
});
export function validatePreferences(p: Record<string, string>) {
  for (const [k, v] of Object.entries(p))
    if (typeof v !== "string" || k.length > 100 || v.length > 100_000)
      throw Error("Preferência inválida.");
  if (
    p.marginMinor &&
    (!Number.isSafeInteger(Number(p.marginMinor)) ||
      Number(p.marginMinor) < 0 ||
      Number(p.marginMinor) > 900_000_000_000_000)
  )
    throw Error("Margem de segurança inválida.");
  if (
    p.savingPercent &&
    (!Number.isFinite(Number(p.savingPercent)) ||
      Number(p.savingPercent) < 0 ||
      Number(p.savingPercent) > 100)
  )
    throw Error("Percentual de economia inválido.");
  if (
    p.holidays &&
    p.holidays
      .split(/[\s,;]+/)
      .filter(Boolean)
      .some((d) => !validDate(d))
  )
    throw Error("Confira as datas dos feriados.");
  if (
    p.emergencyMonths &&
    ![1, 3, 6, 9, 12].includes(Number(p.emergencyMonths))
  )
    throw Error("Meses de reserva inválidos.");
  if (p.textSize && ![14, 16, 18].includes(Number(p.textSize)))
    throw Error("Tamanho de texto inválido.");
}
export function calendar(prefs: Record<string, string>): CalendarRules {
  return {
    saturday: prefs.saturday === "yes",
    national: prefs.national !== "no",
    holidays: (prefs.holidays || "").split(/[\s,;]+/).filter(Boolean),
    state: prefs.state,
    city: prefs.city,
  };
}
export function transactions(data: Data, from: string, end: string): Item[] {
  const base = expanded(data.items, from, end);
  for (const rule of data.items.filter(
    (i) => i.type === "salary_schedule" && active(i),
  )) {
    for (const date of scheduleDates(
      rule,
      from,
      end,
      calendar(data.preferences),
    )) {
      const id = `salary:${rule.fields.rootScheduleId || rule.id}:${date}`;
      if (data.items.some((i) => i.id === id)) continue;
      base.push({
        ...rule,
        id,
        type: rule.fields.transactionType || "income",
        date,
        done: false,
        fields: {
          ...rule.fields,
          status: "expected",
          planned: "Sim",
          scheduleId: rule.id,
          occurrenceDate: date,
          virtual: "yes",
        },
      });
    }
  }
  return base.filter(active);
}
export function allInvoices(data: Data, from: string, end: string) {
  const tx = transactions(data, from, end);
  return data.items
    .filter((i) => active(i) && i.type === "card" && currency(i) === "BRL")
    .flatMap((c) =>
      invoices(
        [...tx, ...data.items.filter((i) => i.type === "card_carry")],
        c,
      ),
    );
}
export function safeSpending(data: Data, day = today()) {
  const monthEnd = addDays(addMonths(day.slice(0, 7) + "-01", 1, 1), -1);
  const future = transactions(data, day, addMonths(day, 2));
  const next = future
    .filter((i) => i.type === "income" && !settled(i) && due(i) > day)
    .sort((a, b) => due(a).localeCompare(due(b)))[0];
  const end =
    data.preferences.horizon === "salary" && next
      ? addDays(due(next), -1)
      : monthEnd;
  const excluded = new Set(
    data.items
      .filter((i) => i.type === "account" && i.fields.includeAvailable === "no")
      .map((i) => i.id),
  );
  let balance = currentBalance(data.items, day, "BRL");
  for (const id of excluded)
    balance = safe(balance - currentBalance(data.items, day, "BRL", id));
  const rows = future.filter(
    (i) =>
      currency(i) === "BRL" &&
      i.type === "expense" &&
      !settled(i) &&
      !i.fields.card &&
      due(i) <= end,
  );
  const bills = allInvoices(
    data,
    addMonths(day, -120),
    addMonths(end, 1),
  ).filter((i) => i.due <= end && i.remaining > 0);
  const pending =
    rows.reduce((n, i) => safe(n + amount(i)), 0) +
    bills.reduce((n, i) => safe(n + i.remaining), 0);
  // A goal explicitly linked to an excluded reserve account is already excluded from available cash.
  const reserved = data.items
    .filter(
      (i) =>
        active(i) &&
        i.type === "savings_goal" &&
        !excluded.has(i.fields.account),
    )
    .reduce((n, i) => safe(n + amount(i, "saved")), 0);
  const goalPlan = data.items
    .filter((i) => active(i) && i.type === "savings_goal")
    .reduce((n, i) => safe(n + amount(i, "monthlyContribution")), 0);
  const savingPlan = Math.floor(
    (monthly(data, day.slice(0, 7)).income *
      Number(data.preferences.savingPercent || 0)) /
      100,
  );
  const planned = Math.max(goalPlan, Math.max(0, savingPlan - reserved));
  const margin = Number(data.preferences.marginMinor || 0);
  const expected = future
    .filter(
      (i) =>
        currency(i) === "BRL" &&
        i.type === "income" &&
        !settled(i) &&
        due(i) <= end,
    )
    .reduce((n, i) => safe(n + amount(i)), 0);
  const total = safe(
    balance -
      pending -
      reserved -
      planned -
      margin +
      (data.preferences.includeFuture === "yes" ? expected : 0),
  );
  return {
    balance,
    pending,
    reserved,
    planned,
    margin,
    total,
    expected,
    end,
    next,
    rows,
    bills,
    daily: Math.floor(total / Math.max(1, daysBetween(day, end) + 1)),
  };
}
export function monthly(data: Data, month: string, account = "") {
  const end = addDays(addMonths(month + "-01", 1, 1), -1);
  const tx = transactions(data, month + "-01", end).filter(
    (i) =>
      currency(i) === "BRL" &&
      (!account || i.fields.account === account) &&
      booked(i).startsWith(month),
  );
  const incomes = tx.filter((i) => i.type === "income" && settled(i));
  const expenses = tx.filter(
    (i) =>
      i.type === "expense" &&
      i.fields.paymentType !== "card_payment" &&
      (settled(i) || !!i.fields.card),
  );
  const sum = (rows: Item[]) => rows.reduce((n, i) => safe(n + amount(i)), 0);
  const cats = new Map<string, number>();
  expenses.forEach((i) =>
    cats.set(
      i.fields.category || "Outros",
      safe((cats.get(i.fields.category || "Outros") || 0) + amount(i)),
    ),
  );
  return {
    income: sum(incomes),
    expense: sum(expenses),
    saved: sum(incomes) - sum(expenses),
    categories: [...cats]
      .map(([name, value]) => ({ name, value }))
      .sort((a, b) => b.value - a.value),
    tx,
  };
}
export function saveChanges(data: Data, changes: Item[]): Data {
  validatePreferences(data.preferences);
  if (changes.length > 20000)
    throw Error("Importe até 20 mil registros por vez.");
  const ids = new Set(changes.map((i) => i.id));
  if (ids.size !== changes.length)
    throw Error("Há identificadores duplicados.");
  const normalized = changes.map((i) => {
    if (i.type === "savings_goal") {
      const previous = data.items.find((x) => x.id === i.id),
        old = previous ? amount(previous, "saved") : 0,
        now = amount(i, "saved");
      if (i.fields.goalHistory) {
        const h = JSON.parse(i.fields.goalHistory);
        if (
          !Array.isArray(h) ||
          h.some(
            (r) =>
              !validDate(r.date) ||
              !Number.isSafeInteger(r.savedMinor) ||
              !Number.isSafeInteger(r.deltaMinor),
          )
        )
          throw Error("Histórico da meta inválido.");
      }
      if (now !== old && (previous || !i.fields.goalHistory)) {
        let history: {
          date: string;
          savedMinor: number;
          deltaMinor: number;
        }[] = [];
        try {
          history = JSON.parse(previous?.fields.goalHistory || "[]");
        } catch {}
        i = {
          ...i,
          fields: {
            ...i.fields,
            goalHistory: JSON.stringify(
              [
                ...history,
                { date: today(), savedMinor: now, deltaMinor: now - old },
              ].slice(-500),
            ),
          },
        };
      }
    }
    return normalize(i);
  });
  const merged = [...data.items.filter((i) => !ids.has(i.id)), ...normalized];
  for (const i of normalized) {
    validateItem(i);
    validateFinance(i, merged);
    if (i.type === "salary_schedule") {
      if (amount(i) <= 0) throw Error("A receita precisa ter valor positivo.");
      scheduleDates(
        i,
        i.date,
        addMonths(i.date, 13),
        calendar(data.preferences),
      );
      if (
        i.fields.account &&
        !merged.some(
          (a) =>
            a.id === i.fields.account &&
            a.type === "account" &&
            active(a) &&
            currency(a) === currency(i),
        )
      )
        throw Error("Escolha uma conta ativa na mesma moeda.");
    }
    const old = data.items.find((o) => o.id === i.id);
    if (
      old?.fields.currency &&
      old.fields.currency !== i.fields.currency &&
      data.items.some(
        (o) =>
          active(o) &&
          [o.fields.account, o.fields.card, o.fields.destination].includes(
            i.id,
          ),
      )
    )
      throw Error("Crie outra conta para usar uma moeda diferente.");
    if (
      i.deletedAt &&
      ["account", "card"].includes(i.type) &&
      merged.some(
        (o) =>
          active(o) &&
          [o.fields.account, o.fields.destination, o.fields.card].includes(
            i.id,
          ),
      )
    )
      throw Error(
        "Esta conta ou cartão tem registros vinculados. Preserve o histórico.",
      );
  }
  return { ...data, revision: data.revision + 1, items: merged };
}
export function confirmTransaction(
  data: Data,
  row: Item,
  actual = amount(row),
  day = today(),
) {
  if (day > today() || !validDate(day))
    throw Error("Informe a data de um recebimento ou pagamento realizado.");
  if (!Number.isSafeInteger(actual) || actual <= 0)
    throw Error("Informe um valor positivo.");
  const existing = data.items.find((i) => i.id === row.id);
  if (existing && settled(existing)) return data;
  const original = existing || row;
  const result = {
    ...original,
    done: true,
    fields: {
      ...original.fields,
      virtual: "no",
      amountMinor: String(actual),
      amount: decimal(actual),
      status: row.type === "income" ? "received" : "paid",
      settledDate: day,
      planned: "Não",
    },
  };
  return saveChanges(data, [result]);
}
export function automaticChanges(data: Data, day = today()) {
  const rules = data.items.filter(
    (i) =>
      active(i) &&
      i.type === "salary_schedule" &&
      i.fields.mode === "automatic",
  );
  let next = data;
  for (const rule of rules)
    for (const date of scheduleDates(
      rule,
      rule.fields.startDate || rule.date,
      day,
      calendar(data.preferences),
    )) {
      const id = `salary:${rule.fields.rootScheduleId || rule.id}:${date}`;
      if (next.items.some((i) => i.id === id)) continue;
      next = confirmTransaction(
        next,
        {
          ...rule,
          id,
          type: "income",
          date,
          fields: {
            ...rule.fields,
            scheduleId: rule.id,
            status: "expected",
            occurrenceDate: date,
          },
        },
        amount(rule),
        date,
      );
    }
  return next;
}
export function payInvoice(
  data: Data,
  invoiceId: string,
  accountId: string,
  day = today(),
) {
  const invoice = allInvoices(
    data,
    addMonths(day, -120),
    addMonths(day, 120),
  ).find((i) => i.id === invoiceId);
  if (!invoice || invoice.remaining <= 0) return data;
  const account = data.items.find(
    (i) => i.id === accountId && i.type === "account" && active(i),
  );
  if (!account || currency(account) !== "BRL")
    throw Error("Escolha uma conta ativa em reais.");
  if (day > today()) throw Error("O pagamento não pode estar no futuro.");
  return saveChanges(data, [
    createItem("expense", {
      id: `payment:${invoiceId}:${invoice.paid}`,
      title: "Pagamento de fatura",
      date: day,
      fields: {
        amountMinor: String(invoice.remaining),
        currency: "BRL",
        account: accountId,
        card: invoice.cardId,
        invoiceId,
        paymentType: "card_payment",
        status: "paid",
        settledDate: day,
        financialVersion: "3",
      },
    }),
  ]);
}
export function editSchedule(
  data: Data,
  rule: Item,
  at: string,
  replacement: Item,
  scope: "one" | "future",
) {
  const root = rule.fields.rootScheduleId || rule.id;
  const old = data.items.find((i) => i.id === `salary:${root}:${at}`);
  if (old && settled(old))
    throw Error(
      "O histórico confirmado não pode ser alterado pela recorrência.",
    );
  if (scope === "one")
    return saveChanges(data, [
      {
        ...replacement,
        id: `salary:${root}:${at}`,
        type: "income",
        date: at,
        fields: {
          ...replacement.fields,
          scheduleId: rule.id,
          status: "expected",
          planned: "Sim",
        },
      },
    ]);
  const child = {
    ...replacement,
    id: crypto.randomUUID(),
    date: at,
    fields: { ...replacement.fields, startDate: at, rootScheduleId: root },
  };
  const pending = data.items
    .filter(
      (i) =>
        i.fields.scheduleId === rule.id &&
        i.date >= at &&
        !settled(i) &&
        active(i),
    )
    .map((i) => ({
      ...i,
      title: replacement.title,
      fields: {
        ...i.fields,
        ...replacement.fields,
        scheduleId: child.id,
        status: "expected",
      },
    }));
  return saveChanges(data, [
    { ...rule, fields: { ...rule.fields, endDate: addDays(at, -1) } },
    child,
    ...pending,
  ]);
}
export function editExpenseRule(
  data: Data,
  rule: Item,
  at: string,
  replacement: Item,
  scope: "one" | "future",
) {
  if (at < today())
    throw Error("Use hoje ou uma data futura para alterar a série.");
  const occurrence = transactions(data, at, at).find(
    (i) => i.fields.recurrenceRuleId === rule.id && i.date === at,
  );
  if (scope === "one") {
    if (!occurrence) throw Error("Escolha a data de uma ocorrência existente.");
    if (settled(data.items.find((i) => i.id === occurrence.id) || occurrence))
      throw Error("O histórico confirmado será preservado.");
    return saveChanges(data, [
      {
        ...occurrence,
        title: replacement.title,
        notes: replacement.notes,
        fields: {
          ...occurrence.fields,
          ...replacement.fields,
          status: "pending",
          virtual: "no",
        },
      },
    ]);
  }
  const child = {
    ...replacement,
    id: crypto.randomUUID(),
    date: at,
    fields: { ...replacement.fields, startDate: at },
  };
  const previous = data.items.filter(
    (i) => i.fields.recurrenceRuleId === rule.id && i.date >= at && active(i),
  );
  const changes = previous.map((i) =>
    settled(i)
      ? createItem("recurrence_exception", {
          title: "Preservar ocorrência já confirmada",
          date: i.date,
          fields: {
            recurrenceRuleId: child.id,
            occurrenceDate: i.date,
            action: "skip",
          },
        })
      : { ...i, deletedAt: Date.now() },
  );
  return saveChanges(data, [
    { ...rule, fields: { ...rule.fields, endDate: addDays(at, -1) } },
    child,
    ...changes,
  ]);
}
export function togglePause(item: Item, day = today()): Item {
  if (item.fields.pausedAt) {
    const end = addDays(day, -1),
      start = item.fields.pausedAt;
    const windows =
      start <= end
        ? [item.fields.pauseWindows, `${start}/${end}`]
            .filter(Boolean)
            .join(";")
        : item.fields.pauseWindows || "";
    return {
      ...item,
      fields: {
        ...item.fields,
        paused: "no",
        pausedAt: "",
        pauseWindows: windows,
      },
    };
  }
  return { ...item, fields: { ...item.fields, paused: "yes", pausedAt: day } };
}
export function importPreview(text: string, data: Data) {
  const input = JSON.parse(text);
  if (input.preferences) validatePreferences(input.preferences);
  const records: Array<Item> = input.items || input;
  if (!Array.isArray(records) || records.length > 20000)
    throw Error("Backup inválido ou muito grande.");
  records.forEach(validateItem);
  const seen = new Set<string>();
  const duplicate = records.filter((i) => {
    const exists = data.items.some((x) => x.id === i.id) || seen.has(i.id);
    seen.add(i.id);
    return exists;
  });
  const ids = new Set(data.items.map((i) => i.id));
  const unique = records.filter((i) => !ids.has(i.id) && (ids.add(i.id), true));
  saveChanges(data, unique);
  return {
    items: unique,
    duplicates: duplicate.length,
    total: records.length,
    preferences:
      input.preferences && typeof input.preferences === "object"
        ? (Object.fromEntries(
            Object.entries(input.preferences).filter(
              ([k, v]) => typeof v === "string" && k.length < 100,
            ),
          ) as Record<string, string>)
        : undefined,
  };
}
export async function csvPreview(text: string, data: Data) {
  if (text.length > 5_000_000) throw Error("O CSV excede 5 MB.");
  const rows: string[][] = [];
  let row: string[] = [],
    cell = "",
    quoted = false;
  for (let n = 0; n < text.length; n++) {
    const c = text[n];
    if (c === '"') {
      if (quoted && text[n + 1] === '"') {
        cell += '"';
        n++;
      } else quoted = !quoted;
    } else if (c === ";" && !quoted) {
      row.push(cell);
      cell = "";
    } else if ((c === "\n" || c === "\r") && !quoted) {
      if (c === "\r" && text[n + 1] === "\n") n++;
      row.push(cell);
      if (row.some(Boolean)) rows.push(row);
      row = [];
      cell = "";
    } else cell += c;
  }
  if (quoted) throw Error("O CSV tem aspas não fechadas.");
  row.push(cell);
  if (row.some(Boolean)) rows.push(row);
  const header = rows.shift()?.map((v) => v.replace(/^\uFEFF/, "").trim());
  if (
    !header ||
    header.join("|") !== "Data|Tipo|Descrição|Categoria|Valor (R$)|Situação"
  )
    throw Error(
      "Use o CSV exportado pelo Veyra (Data;Tipo;Descrição;Categoria;Valor (R$);Situação).",
    );
  const { parseMinor } = await import("../desktop/shared/model");
  const items: Item[] = [];
  for (const cells of rows) {
    if (cells.length !== 6 || !["income", "expense"].includes(cells[1]))
      throw Error(
        "O CSV aceita receitas e despesas; transferências precisam de contas de origem e destino no backup JSON.",
      );
    if (!validDate(cells[0])) throw Error("Data inválida no CSV.");
    const encoded = new TextEncoder().encode(cells.join("\u0000"));
    const digest = await crypto.subtle.digest("SHA-256", encoded);
    const id =
      "csv:" +
      Array.from(new Uint8Array(digest))
        .map((v) => v.toString(16).padStart(2, "0"))
        .join("");
    const value = parseMinor(cells[4]);
    if (value <= 0) throw Error("O CSV precisa de valores positivos.");
    const imported = createItem(cells[1], {
      id,
      title:
        cells[2].replace(/^'(?=[=+@\-\t\r])/, "") || cells[3] || "Importado",
      date: cells[0],
      fields: {
        currency: "BRL",
        amountMinor: String(value),
        category: cells[3],
        status:
          cells[5] === "Confirmado"
            ? cells[1] === "income"
              ? "received"
              : "paid"
            : "pending",
        financialVersion: "3",
      },
    });
    // A CSV has no original record ID. Identical financial content is treated conservatively as duplicate.
    const original = data.items.find(
      (i) =>
        i.type === imported.type &&
        i.date === imported.date &&
        i.title === imported.title &&
        amount(i) === value &&
        i.fields.category === imported.fields.category,
    );
    items.push(original || imported);
  }
  return importPreview(JSON.stringify({ items }), data);
}
export function csv(rows: Item[]) {
  const quote = (s: string) =>
    '"' + (/^[=+@\-\t\r]/.test(s) ? "'" : "") + s.replaceAll('"', '""') + '"';
  return (
    "\uFEFF" +
    [
      ["Data", "Tipo", "Descrição", "Categoria", "Valor (R$)", "Situação"],
      ...rows.map((i) => [
        i.date,
        i.type,
        i.title,
        i.fields.category || "",
        decimal(amount(i)).replace(".", ","),
        settled(i) ? "Confirmado" : "Previsto",
      ]),
    ]
      .map((r) => r.map(quote).join(";"))
      .join("\r\n")
  );
}
