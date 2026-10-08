export interface Item {
  id: string;
  type: string;
  title: string;
  notes: string;
  date: string;
  done: boolean;
  favorite: boolean;
  tags: string;
  parentId: string;
  fields: Record<string, string>;
  deletedAt: number;
  createdAt: number;
}
export interface Remote {
  item: Item;
  revision: number;
  operationId: string;
  updatedAt: string;
}
export interface Pending {
  item: Item;
  operationId: string;
  baseRevision: number;
  localRevision: number;
}
export interface Conflict {
  id: string;
  local: Item;
  remote: Remote;
  createdAt: number;
}
export interface PublicUser {
  uid: string;
  name: string;
  email: string;
  verified: boolean;
}
export interface Point {
  date: string;
  balance: number;
  income: number;
  expense: number;
  labels: string[];
}
export interface Invoice {
  id: string;
  cardId: string;
  due: string;
  total: number;
  paid: number;
  remaining: number;
  currency: string;
}
export interface Finance {
  error?: string;
  balance: number;
  income: number;
  expense: number;
  savings: number;
  previousExpense: number;
  currency: string;
  month: string;
  forecast: {
    income: number;
    expense: number;
    balance: number;
    points: Point[];
  };
  categories: { name: string; value: number }[];
  accounts: { item: Item; balance: number }[];
  invoices: Invoice[];
  budgets: { item: Item; used: number; limit: number }[];
  insights: string[];
}
export interface Snapshot {
  uid: string | null;
  user: PublicUser | null;
  items: Item[];
  preferences: Record<string, string>;
  desktop: Record<string, any>;
  sync: {
    status: string;
    pending: number;
    conflicts: number;
    lastSync: number;
    error: string;
  };
  finance: Finance;
  focus: {
    active: boolean;
    paused: boolean;
    remaining: number;
    title: string;
    taskId: string;
  };
  version: string;
  configured: boolean;
  deviceId: string;
}
export interface DesktopAPI {
  call: (method: string, value?: any) => Promise<any>;
  onChange: (listener: () => void) => () => void;
  onCommand: (listener: (command: string) => void) => () => void;
  filePath: (file: File) => string;
}
declare global {
  interface Window {
    veyra: DesktopAPI;
  }
}
export const buckets = [
  "transactions",
  "accounts",
  "creditCards",
  "recurringTransactions",
  "budgets",
  "goals",
  "subscriptions",
  "debts",
  "automationRules",
  "categories",
  "templates",
  "assets",
  "notes",
  "tasks",
  "settings",
  "workspace",
];
const routes: Record<string, string> = {
  income: "transactions",
  expense: "transactions",
  transfer: "transactions",
  invoice_payment: "transactions",
  bill: "transactions",
  receivable: "transactions",
  account: "accounts",
  card: "creditCards",
  recurrence: "recurringTransactions",
  finance_recurrence: "recurringTransactions",
  recurring_rule: "recurringTransactions",
  recurrence_exception: "recurringTransactions",
  installment_plan: "recurringTransactions",
  budget: "budgets",
  savings_goal: "goals",
  finance_goal: "goals",
  emergency_reserve: "goals",
  subscription: "subscriptions",
  debt: "debts",
  loan: "debts",
  finance_rule: "automationRules",
  financial_rule: "automationRules",
  automation: "automationRules",
  finance_category: "categories",
  financial_category: "categories",
  finance_template: "templates",
  financial_template: "templates",
  investment: "assets",
  asset: "assets",
  financial_asset: "assets",
  networth_snapshot: "assets",
  month_close: "assets",
  cash_carry: "assets",
  card_carry: "assets",
  note: "notes",
  task: "tasks",
  cloud_settings: "settings",
};
export const bucketFor = (type: string) => routes[type] ?? "workspace";
export const syncPreferences = new Set([
  "name",
  "theme",
  "profile",
  "home",
  "hidden",
  "favoriteModules",
  "focusMinutes",
  "breakMinutes",
  "weatherMode",
  "weatherCity",
  "weatherTemperature",
  "weatherCondition",
  "clockZones",
  "financeCurrency",
  "financeHideValues",
  "financeHidden",
  "financeFirstDay",
  "financialDay",
  "financeNotifications",
  "financeBudgetAlerts",
  "financeRecentIncomeCategory",
  "financeRecentExpenseCategory",
  "financeRecentAccount",
  "recentIncomeCategory",
  "recentExpenseCategory",
  "recentFinanceAccount",
]);
export const today = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
};
export function validDate(value: string) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || Number(value.slice(0, 4)) < 1)
    return false;
  const d = new Date(value + "T12:00:00Z");
  return !Number.isNaN(d.valueOf()) && d.toISOString().slice(0, 10) === value;
}
export function addDays(day: string, count: number) {
  if (!validDate(day) || !Number.isInteger(count))
    throw Error("Data inválida.");
  const d = new Date(day + "T12:00:00Z");
  d.setUTCDate(d.getUTCDate() + count);
  return d.toISOString().slice(0, 10);
}
export function addMonths(
  day: string,
  count: number,
  anchor = Number(day.slice(8)),
) {
  const d = new Date(day.slice(0, 7) + "-01T12:00:00Z");
  d.setUTCMonth(d.getUTCMonth() + count);
  const last = new Date(
    Date.UTC(d.getUTCFullYear(), d.getUTCMonth() + 1, 0),
  ).getUTCDate();
  d.setUTCDate(Math.min(anchor, last));
  return d.toISOString().slice(0, 10);
}
export const daysBetween = (a: string, b: string) =>
  Math.round(
    (Date.parse(b + "T12:00:00Z") - Date.parse(a + "T12:00:00Z")) / 86400000,
  );
export function currencyScale(currency = "BRL") {
  if (!/^[A-Z]{3}$/.test(currency)) throw Error("Moeda inválida.");
  const scale = new Intl.NumberFormat("en", {
    style: "currency",
    currency,
  }).resolvedOptions().maximumFractionDigits;
  if (scale === undefined || scale > 3) throw Error("Moeda não suportada.");
  return scale;
}
export const MAX_MINOR = 900_000_000_000_000;
export function safe(value: number) {
  if (!Number.isSafeInteger(value))
    throw Error("O total excedeu o limite exato de cálculo.");
  return value;
}
export function parseMinor(input: string, currency = "BRL"): number {
  let value = input.trim().replace(/R\$|\s/g, "");
  if (
    value.includes(",") &&
    value.includes(".") &&
    value.lastIndexOf(".") > value.lastIndexOf(",")
  ) {
    if (!/^[+-]?\d{1,3}(,\d{3})+\.\d+$/.test(value))
      throw Error("Confira os separadores do valor.");
    value = value.replaceAll(",", "");
  } else if (value.includes(",")) {
    if (!/^[+-]?(\d+|\d{1,3}(\.\d{3})+),\d+$/.test(value))
      throw Error("Confira os separadores do valor.");
    value = value.replaceAll(".", "").replace(",", ".");
  } else if (
    currency === "BRL" &&
    input.includes("R$") &&
    /^[+-]?\d{1,3}(\.\d{3})+$/.test(value)
  )
    value = value.replaceAll(".", "");
  if (!/^[+-]?\d+(\.\d+)?$/.test(value))
    throw Error("Informe um valor válido.");
  const scale = currencyScale(currency);
  const sign = value.startsWith("-") ? -1n : 1n;
  const [whole, part = ""] = value.replace(/^[+-]/, "").split(".");
  if (part.slice(scale).replaceAll("0", ""))
    throw Error("Use a precisão da moeda, sem arredondamento.");
  const result =
    sign *
    (BigInt(whole) * 10n ** BigInt(scale) +
      BigInt(part.slice(0, scale).padEnd(scale, "0") || "0"));
  if (result > BigInt(MAX_MINOR) || result < -BigInt(MAX_MINOR))
    throw Error("Valor acima do limite seguro.");
  return Number(result);
}
export function amount(item: Item, key = "amount") {
  const exact = item.fields[key === "amount" ? "amountMinor" : key + "Minor"];
  if (exact) {
    if (!/^-?\d+$/.test(exact)) throw Error("Valor armazenado inválido.");
    const n = Number(exact);
    if (!Number.isSafeInteger(n) || Math.abs(n) > MAX_MINOR)
      throw Error("Valor acima do limite.");
    return n;
  }
  return item.fields[key]
    ? parseMinor(item.fields[key], item.fields.currency || "BRL")
    : 0;
}
export const decimal = (value: number, currency = "BRL") => {
  const scale = currencyScale(currency);
  const negative = value < 0 ? "-" : "";
  const n = BigInt(Math.abs(safe(value)));
  return (
    negative +
    (n / 10n ** BigInt(scale)).toString() +
    (scale
      ? "." + (n % 10n ** BigInt(scale)).toString().padStart(scale, "0")
      : "")
  );
};
export const money = (value: number, currency = "BRL") =>
  new Intl.NumberFormat("pt-BR", { style: "currency", currency }).format(
    value / 10 ** currencyScale(currency),
  );
export function createItem(type: string, partial: Partial<Item> = {}): Item {
  return {
    id: crypto.randomUUID(),
    type,
    title: "",
    notes: "",
    date: today(),
    done: false,
    favorite: false,
    tags: "",
    parentId: "",
    fields: {},
    deletedAt: 0,
    createdAt: Date.now(),
    ...partial,
  };
}
export function validateItem(item: Item) {
  if (
    !item ||
    typeof item !== "object" ||
    !item.id ||
    item.id.length > 512 ||
    !/^[a-z_]{1,40}$/.test(item.type) ||
    !item.title.trim() ||
    item.title.length > 200 ||
    item.notes.length > 100000 ||
    item.tags.length > 10000
  )
    throw Error("Confira o título e os limites do registro.");
  if (item.date && !validDate(item.date)) throw Error("Data inválida.");
  if (
    typeof item.done !== "boolean" ||
    typeof item.favorite !== "boolean" ||
    !Number.isSafeInteger(item.createdAt) ||
    item.createdAt < 0 ||
    !Number.isSafeInteger(item.deletedAt) ||
    item.deletedAt < 0 ||
    typeof item.parentId !== "string"
  )
    throw Error("Registro inválido.");
  if (
    !item.fields ||
    Object.keys(item.fields).length > 100 ||
    Object.entries(item.fields).some(
      ([k, v]) =>
        !k ||
        k.length > 100 ||
        typeof v !== "string" ||
        v.length > (k === "attachment" ? 14000000 : 100000),
    )
  )
    throw Error("Um campo excede o limite do registro.");
  for (const k of [
    "dueDate",
    "settledDate",
    "startDate",
    "endDate",
    "deadline",
    "targetDate",
  ])
    if (item.fields[k] && !validDate(item.fields[k]))
      throw Error("Confira as datas.");
  if (item.fields.time && !/^([01]\d|2[0-3]):[0-5]\d$/.test(item.fields.time))
    throw Error("Use um horário válido.");
  if (
    item.fields.reminder &&
    !/^([01]\d|2[0-3]):[0-5]\d$/.test(item.fields.reminder)
  )
    throw Error("Confira o horário do lembrete.");
}
export function withoutBinary(item: Item): Item {
  const { attachment: _attachment, ...fields } = item.fields;
  return { ...item, fields };
}
export function cloudItem(item: Item): Item {
  if (item.fields.cloudAttachmentPath)
    throw Error("Exporte o anexo da nuvem antes de usar o modo gratuito.");
  if (!item.fields.attachment && item.fields.hasAttachment !== "yes")
    return item;
  const { attachment: _a, attachmentHash: _h, ...fields } = item.fields;
  return {
    ...item,
    fields: { ...fields, hasAttachment: "no", attachmentLocalOnly: "yes" },
  };
}
