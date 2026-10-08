import {
  Item,
  addDays,
  createItem,
  decimal,
  parseMinor,
  today,
  validDate,
  amount,
  safe,
} from "./model";
import { active, currency, expanded, due, settled } from "./finance";
import { habitStats } from "./productivity";

export const fold = (s: string) =>
  s.toLocaleLowerCase("pt-BR").normalize("NFD").replace(/\p{M}/gu, "");
export function sameContent(a: Item | null, b: Item | null) {
  const stable = (i: Item | null) =>
    i &&
    JSON.stringify({
      ...i,
      fields: Object.fromEntries(
        Object.entries(i.fields)
          .filter(([k]) => k !== "attachment")
          .sort(([a], [b]) => a.localeCompare(b)),
      ),
    });
  return stable(a) === stable(b);
}
export interface QuickProposal {
  item: Item;
  explanation: string[];
  confidence: "review" | "clear";
  recurring: boolean;
}
export function interpretQuick(
  text: string,
  reference = today(),
  cur = "BRL",
): QuickProposal | null {
  const source = text.trim().slice(0, 200),
    input = fold(source);
  if (!source) return null;
  const expense = /^(gastei|paguei|gasto|despesa|saida)\b/.test(input);
  const income = /^(recebi|entrada|receita|ganhei)\b/.test(input);
  const recurringDay = input.match(/\btodo dia\s+(\d{1,2})\b/);
  const value = source.match(/(?:R\$\s*)?([0-9]+(?:[.,][0-9]{1,3})?)/);
  const financial = expense || income || !!recurringDay;
  let type = financial
    ? income
      ? "income"
      : "expense"
    : /^(nota|ideia)\b/.test(input)
      ? "note"
      : /^(evento|reuniao)\b/.test(input)
        ? "event"
        : "task";
  let title = source,
    date = "",
    fields: Record<string, string> = {};
  const explanation: string[] = [];
  if (financial) {
    if (!value) return null;
    const minor = parseMinor(value[1], cur);
    if (minor <= 0) throw Error("O valor deve ser maior que zero.");
    fields = {
      amount: decimal(minor, cur),
      amountMinor: String(minor),
      currency: cur,
      status: income ? "received" : "paid",
    };
    title = source
      .replace(value[0], "")
      .replace(
        /^(gastei|paguei|gasto|despesa|saída|recebi|entrada|receita|ganhei)\s*/i,
        "",
      )
      .replace(/^(no|na|com|de)\s+/i, "")
      .trim();
    if (/almoco|jantar|mercado|comida|delivery|cafe/.test(input))
      fields.category = "Alimentação";
    else if (/uber|onibus|gasolina|transporte/.test(input))
      fields.category = "Transporte";
    date = reference;
    explanation.push(
      "Valor interpretado na moeda selecionada; confira conta, categoria e situação.",
    );
    if (recurringDay) {
      const day = Number(recurringDay[1]);
      if (day < 1 || day > 31)
        throw Error("O dia da recorrência deve ficar entre 1 e 31.");
      title = title.replace(/todo dia\s+\d{1,2}/i, "").trim();
      const month = reference.slice(0, 7),
        last = new Date(
          Number(month.slice(0, 4)),
          Number(month.slice(5)),
          0,
        ).getDate();
      date = month + "-" + String(Math.min(day, last)).padStart(2, "0");
      if (date < reference) {
        const d = new Date(reference + "T12:00:00");
        d.setDate(1);
        d.setMonth(d.getMonth() + 1);
        const end = new Date(d.getFullYear(), d.getMonth() + 1, 0).getDate();
        date = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(Math.min(day, end)).padStart(2, "0")}`;
      }
      type = "recurring_rule";
      fields = {
        ...fields,
        transactionType: income ? "income" : "expense",
        startDate: date,
        frequency: "monthly",
        dayOfMonth: String(day),
        status: "expected",
      };
      explanation.push(
        "Recorrência mensal sugerida; nenhuma cobrança ou pagamento será executado.",
      );
    }
  } else {
    if (/\bamanha\b/.test(input)) date = addDays(reference, 1);
    else if (/\bhoje\b/.test(input)) date = reference;
    const explicit = input.match(/\b(\d{2})\/(\d{2})(?:\/(\d{4}))?\b/);
    if (explicit) {
      date = `${explicit[3] || reference.slice(0, 4)}-${explicit[2]}-${explicit[1]}`;
      if (!validDate(date)) throw Error("Confira a data interpretada.");
    }
    const time = input.match(
      /\b(?:as\s+)?([01]?\d|2[0-3])(?:h(?:(\d{2}))?|:([0-5]\d))\b/,
    );
    if (time) {
      const minute = time[2] || time[3] || "00";
      if (Number(minute) > 59) throw Error("Confira o horário.");
      fields.time = String(Number(time[1])).padStart(2, "0") + ":" + minute;
    }
    title = source
      .replace(/^(tarefa|nota|ideia|evento|reunião)\s+/i, "")
      .replace(/(?:^|\s)(amanhã|amanha|hoje)(?=\s|$)/gi, " ")
      .replace(/\b\d{2}\/\d{2}(?:\/\d{4})?\b/g, "")
      .replace(/\b(?:às\s+)?([01]?\d|2[0-3])(?:h(?:\d{2})?|:[0-5]\d)\b/gi, "")
      .replace(/\s+/g, " ")
      .trim();
    if (date)
      explanation.push(
        "Data sugerida: " + date + (fields.time ? " às " + fields.time : ""),
      );
    else
      explanation.push(
        "Sem data inferida; escolha uma data ou deixe na Inbox.",
      );
  }
  return {
    item: createItem(type, { title: title || source, date, fields }),
    explanation,
    confidence: "review",
    recurring: !!recurringDay,
  };
}

export interface SearchSyntax {
  text: string;
  types: string[];
  date?: string;
  tag?: string;
  category?: string;
  minAmount?: number;
  maxAmount?: number;
  favorite?: boolean;
  error?: string;
}
export function parseSearch(
  query: string,
  cur = "BRL",
  reference = today(),
): SearchSyntax {
  const result: SearchSyntax = { text: "", types: [] };
  const terms: string[] = [];
  const tokens = query.match(/(?:\w+:)?"[^"]*"|\S+/g) || [];
  for (const token of tokens) {
    const match = token.match(
      /^(type|date|tag|category|amount|favorite):(.+)$/i,
    );
    if (!match) {
      terms.push(token.replace(/^"|"$/g, ""));
      continue;
    }
    const key = match[1].toLowerCase(),
      value = match[2].replace(/^"|"$/g, "");
    if (key === "type") {
      const alias: Record<string, string> = {
        gasto: "expense",
        entrada: "income",
        tarefa: "task",
        nota: "note",
        evento: "event",
        projeto: "project",
        meta: "goal",
        habito: "habit",
      };
      result.types = value
        .split(",")
        .map((v) => alias[fold(v)] || v)
        .filter((v) => /^[a-z_]{1,40}$/.test(v));
    }
    if (key === "date") {
      const date =
        fold(value) === "hoje"
          ? reference
          : fold(value) === "amanha"
            ? addDays(reference, 1)
            : value;
      if (validDate(date) || /^\d{4}-(0[1-9]|1[0-2])$/.test(date))
        result.date = date;
      else result.error = "Use date:AAAA-MM-DD, date:AAAA-MM, hoje ou amanhã.";
    }
    if (key === "category") result.category = value;
    if (key === "tag") result.tag = value;
    if (key === "favorite")
      result.favorite = ["yes", "true", "sim"].includes(fold(value));
    if (key === "amount") {
      try {
        const m = value.match(/^(>=|<=|>|<|=)?(.+)$/)!;
        const n = parseMinor(m[2], cur);
        if (m[1] === ">" || m[1] === ">=")
          result.minAmount = n + (m[1] === ">" ? 1 : 0);
        else if (m[1] === "<" || m[1] === "<=")
          result.maxAmount = n - (m[1] === "<" ? 1 : 0);
        else result.minAmount = result.maxAmount = n;
      } catch {
        result.error = "Confira amount:50, amount:>50 ou amount:<100.";
      }
    }
  }
  result.text = terms.join(" ");
  return result;
}
export function matchesSearch(item: Item, syntax: SearchSyntax) {
  if (syntax.types.length && !syntax.types.includes(item.type)) return false;
  if (syntax.date && !item.date.startsWith(syntax.date)) return false;
  if (
    syntax.category &&
    fold(item.fields.category || "") !== fold(syntax.category)
  )
    return false;
  if (
    syntax.tag &&
    !item.tags.split(/[,;]/).some((t) => fold(t.trim()) === fold(syntax.tag!))
  )
    return false;
  if (syntax.favorite !== undefined && item.favorite !== syntax.favorite)
    return false;
  if (syntax.minAmount !== undefined || syntax.maxAmount !== undefined) {
    if (
      !["income", "expense", "bill", "receivable", "transfer"].includes(
        item.type,
      )
    )
      return false;
    try {
      const n = amount(item);
      if (
        n < (syntax.minAmount ?? -Infinity) ||
        n > (syntax.maxAmount ?? Infinity)
      )
        return false;
    } catch {
      return false;
    }
  }
  if (!syntax.text.trim()) return true;
  const hay = fold(
    [
      item.title,
      item.notes,
      item.tags,
      ...Object.values(item.fields).filter((v) => v.length < 1000),
    ].join(" "),
  );
  return syntax.text
    .split(/\s+/)
    .filter(Boolean)
    .every((t) => hay.includes(fold(t)));
}

export function dailySummary(items: Item[], reference = today(), cur = "BRL") {
  const alive = items.filter((i) => !i.deletedAt),
    tasks = alive.filter((i) => i.type === "task" && !i.done),
    habits = alive.filter((i) => i.type === "habit");
  const bills = expanded(alive, reference, addDays(reference, 7))
    .filter(
      (i) =>
        ["expense", "bill"].includes(i.type) &&
        !i.fields.card &&
        active(i) &&
        !settled(i) &&
        currency(i) === cur &&
        !!due(i) &&
        due(i) <= addDays(reference, 7),
    )
    .sort((a, b) => due(a).localeCompare(due(b)));
  return {
    tasks: tasks.filter((i) => i.date === reference),
    overdue: tasks.filter((i) => !!i.date && i.date < reference),
    events: alive
      .filter((i) => i.type === "event" && i.date >= reference && !i.done)
      .sort((a, b) =>
        (a.date + (a.fields.time || "23:59")).localeCompare(
          b.date + (b.fields.time || "23:59"),
        ),
      ),
    bills,
    habits: habits.filter((i) => {
      const h = habitStats(alive, i.id, reference);
      return h.scheduledToday && !h.dates.includes(reference);
    }),
    inbox: alive.filter((i) => i.type === "inbox" && !i.done),
    focusMinutes: safe(
      Math.round(
        alive
          .filter(
            (i) =>
              i.type === "focus" &&
              i.date === reference &&
              i.fields.mode !== "Pausa",
          )
          .reduce((n, i) => n + Number(i.fields.minutes || 0), 0),
      ),
    ),
  };
}
export function linkedItems(items: Item[], id: string) {
  const index = new Map<string, Item[]>();
  for (const i of items.filter((i) => !i.deletedAt))
    for (const target of new Set(
      [
        i.parentId,
        ...["project", "taskId", "goalId", "eventId"].map((k) => i.fields[k]),
      ].filter(Boolean),
    ))
      index.set(target, [...(index.get(target) || []), i]);
  const seen = new Set([id]),
    queue = [id],
    related: Item[] = [];
  for (let n = 0; n < queue.length; n++)
    for (const i of index.get(queue[n]) || [])
      if (!seen.has(i.id)) {
        seen.add(i.id);
        related.push(i);
        queue.push(i.id);
      }
  return related;
}
export function backlinks(items: Item[], note: Item) {
  return items.filter(
    (i) =>
      !i.deletedAt &&
      i.id !== note.id &&
      i.type === "note" &&
      (i.notes.includes("[[" + note.title + "]]") ||
        i.notes.includes("[[" + note.id + "]]")),
  );
}
export function taskBlocked(task: Item, items: Item[]) {
  return (task.fields.dependencies || "")
    .split(",")
    .filter(Boolean)
    .map((id) => items.find((i) => i.id === id))
    .filter((i): i is Item => !!i && !i.deletedAt && !i.done);
}
export function validateTaskDependencies(task: Item, items: Item[]) {
  if (task.type !== "task" || !task.fields.dependencies) return;
  const index = new Map(
    items
      .filter((i) => i.type === "task" && !i.deletedAt)
      .map((i) => [i.id, i]),
  );
  const dependencies = (i: Item) =>
    (i.fields.dependencies || "").split(",").filter(Boolean);
  if (
    dependencies(task).length > 20 ||
    dependencies(task).some((id) => !index.has(id))
  )
    throw Error("Escolha até vinte tarefas existentes como dependências.");
  const visiting = new Set<string>(),
    visited = new Set<string>();
  const stack = [{ id: task.id, exit: false }];
  while (stack.length) {
    const { id, exit } = stack.pop()!;
    if (exit) {
      visiting.delete(id);
      visited.add(id);
      continue;
    }
    if (visiting.has(id))
      throw Error("As dependências não podem formar um ciclo.");
    if (visited.has(id)) continue;
    const item = index.get(id);
    if (!item) continue;
    visiting.add(id);
    stack.push({ id, exit: true });
    for (const target of dependencies(item))
      stack.push({ id: target, exit: false });
  }
}
export const widgetCatalog = [
  ["finance", "Seu dinheiro", "Finanças"],
  ["spending", "Gastos do dia", "Finanças"],
  ["bills", "Contas próximas", "Finanças"],
  ["recent-spending", "Últimos gastos", "Finanças"],
  ["tasks", "Próximas tarefas", "Produtividade"],
  ["notes", "Notas recentes", "Produtividade"],
  ["inbox", "Caixa de entrada", "Produtividade"],
  ["calendar", "Sua agenda", "Planejamento"],
  ["goals", "Metas e projetos", "Planejamento"],
  ["progress", "Progresso do mês", "Planejamento"],
  ["weather", "Seu horizonte", "Informação"],
  ["clock", "Hora e data", "Informação"],
  ["insights", "Insights", "Informação"],
  ["focus", "Foco", "Foco"],
  ["habits", "Hábitos", "Produtividade"],
  ["quick", "Captura rápida", "Utilidades"],
  ["shortcuts", "Atalhos", "Utilidades"],
] as const;
export const defaultDashboard = [
  { id: "finance", size: "wide" },
  { id: "tasks", size: "normal" },
  { id: "calendar", size: "normal" },
  { id: "bills", size: "normal" },
  { id: "habits", size: "normal" },
  { id: "notes", size: "wide" },
];

export interface AutomationPlan {
  records: Item[];
  alerts: { id: string; title: string; itemId: string }[];
  marks: string[];
}
export function planAutomations(
  items: Item[],
  input: Item | undefined,
  reference = today(),
  marks = new Set<string>(),
): AutomationPlan {
  const plan: AutomationPlan = { records: [], alerts: [], marks: [] };
  for (const rule of items
    .filter(
      (i) =>
        !i.deletedAt &&
        i.type === "automation" &&
        i.fields.desktopEnabled === "yes",
    )
    .slice(0, 100)) {
    const f = rule.fields;
    if (f.trigger === "expense" && input?.type !== "expense") continue;
    if (
      f.trigger === "weekday" &&
      (input ||
        Number(f.weekday) !== new Date(reference + "T12:00:00").getDay())
    )
      continue;
    if (!["expense", "weekday"].includes(f.trigger)) continue;
    if (f.category && fold(input?.fields.category || "") !== fold(f.category))
      continue;
    if (f.trigger === "expense") {
      if (currency(input!) !== (f.currency || "BRL")) continue;
      try {
        if (amount(input!) < parseMinor(f.minimum || "0", f.currency || "BRL"))
          continue;
      } catch {
        continue;
      }
    }
    const key = rule.id + ":" + (input?.id || reference);
    if (marks.has(key)) continue;
    const title = (f.message || rule.title).slice(0, 200);
    if (!title.trim()) continue;
    if (f.action === "task" && f.trigger === "weekday")
      plan.records.push(
        createItem("task", {
          id: "auto:" + rule.id + ":" + reference,
          title,
          date: reference,
          fields: { automationId: rule.id },
        }),
      );
    else if (f.action === "alert")
      plan.alerts.push({ id: key, title, itemId: input?.id || rule.id });
    else continue;
    plan.marks.push(key);
  }
  return plan;
}
