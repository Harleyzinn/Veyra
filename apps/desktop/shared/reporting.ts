import { Item, amount, safe, decimal, validDate, today } from "./model";
import {
  active,
  settled,
  booked,
  currency,
  expanded,
  cashDelta,
} from "./finance";
export interface ReportFilter {
  from: string;
  to: string;
  currency: string;
  account?: string;
  card?: string;
  category?: string;
  basis?: "recognized" | "cash";
}
export function reportRows(items: Item[], filter: ReportFilter) {
  if (
    !validDate(filter.from) ||
    !validDate(filter.to) ||
    filter.from > filter.to
  )
    throw Error("Confira o período do relatório.");
  if (filter.basis && !["recognized", "cash"].includes(filter.basis))
    throw Error("Escolha gasto registrado ou fluxo de caixa.");
  const cash = filter.basis === "cash";
  const rows = expanded(items, filter.from, filter.to).filter(
    (i) =>
      (cash ? booked(i) : i.date) >= filter.from &&
      (cash ? booked(i) : i.date) <= filter.to &&
      currency(i) === filter.currency &&
      active(i) &&
      ["income", "expense"].includes(i.type) &&
      (cash
        ? i.fields.virtual !== "yes" &&
          booked(i) <= today() &&
          cashDelta(i) !== 0
        : i.fields.paymentType !== "card_payment") &&
      (!filter.account || i.fields.account === filter.account) &&
      (!filter.card || i.fields.card === filter.card) &&
      (!filter.category || i.fields.category === filter.category),
  );
  const realized = rows.filter(
    (i) =>
      i.fields.virtual !== "yes" &&
      (cash ||
        (settled(i) && booked(i) <= filter.to && booked(i) <= today()) ||
        (i.type === "expense" &&
          !!i.fields.card &&
          i.date <= filter.to &&
          i.date <= today())),
  );
  const income = realized
      .filter((i) => i.type === "income")
      .reduce((n, i) => safe(n + amount(i)), 0),
    expense = realized
      .filter((i) => i.type === "expense")
      .reduce((n, i) => safe(n + amount(i)), 0);
  const categories = new Map<string, number>();
  for (const i of realized.filter((i) => i.type === "expense")) {
    const c = i.fields.category || "Sem categoria";
    categories.set(c, safe((categories.get(c) || 0) + amount(i)));
  }
  return {
    rows,
    income,
    expense,
    net: safe(income - expense),
    categories: [...categories]
      .map(([name, value]) => ({ name, value }))
      .sort((a, b) => b.value - a.value),
  };
}
export function csvCell(value: string) {
  return (
    '"' +
    (/^[\s]*[=+@-]/.test(value) ? "'" + value : value).replaceAll('"', '""') +
    '"'
  );
}
export function reportCSV(items: Item[], filter: ReportFilter) {
  const report = reportRows(items, filter),
    names = new Map(items.map((i) => [i.id, i.title]));
  return (
    "\ufeff" +
    [
      [
        "type",
        "date",
        "title",
        "amount",
        "currency",
        "category",
        "account",
        "card",
        "status",
        "paymentType",
        "settledDate",
        "invoiceDue",
        "legacyCardCash",
        "financialVersion",
      ],
      ...report.rows.map((i) => [
        i.type,
        i.date,
        i.title,
        decimal(amount(i), filter.currency),
        filter.currency,
        i.fields.category || "",
        names.get(i.fields.account) || "",
        names.get(i.fields.card) || "",
        i.fields.virtual === "yes" ? "expected" : i.fields.status || "",
        i.fields.paymentType || "",
        i.fields.settledDate || "",
        i.fields.paymentType === "card_payment"
          ? i.fields.invoiceId?.slice(-10) || ""
          : "",
        i.fields.legacyCardCash || "",
        i.fields.financialVersion || "",
      ]),
    ]
      .map((row) => row.map(csvCell).join(";"))
      .join("\r\n")
  );
}
export function readCSV(text: string) {
  if (text.length > 10000000) throw Error("CSV excede o limite de 10 MB.");
  const separator = text.split(/\r?\n/, 1)[0].includes(";") ? ";" : ",";
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
    } else if (c === separator && !quoted) {
      row.push(cell);
      cell = "";
    } else if ((c === "\n" || c === "\r") && !quoted) {
      if (c === "\r" && text[n + 1] === "\n") n++;
      row.push(cell);
      if (row.some(Boolean)) rows.push(row);
      row = [];
      cell = "";
    } else cell += c;
    if (rows.length > 10001)
      throw Error("Importe até 10 mil linhas por arquivo.");
  }
  if (quoted) throw Error("CSV contém aspas sem fechamento.");
  row.push(cell);
  if (row.some(Boolean)) rows.push(row);
  if (rows[0]) rows[0][0] = rows[0][0].replace(/^\ufeff/, "");
  return rows;
}
