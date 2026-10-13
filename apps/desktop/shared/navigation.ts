import { catalog } from "./catalog";
export const navigationGroups = [
  "Principal",
  "Produtividade",
  "Finanças",
  "Organização",
  "Inteligência",
  "Vida pessoal",
  "Sistema",
] as const;
export type Destination = {
  id: string;
  label: string;
  group: string;
  icon: string;
};
const page = (
  id: string,
  label: string,
  group: string,
  icon = id,
): Destination => ({ id, label, group, icon });
export const destinations: Destination[] = [
  page("home", "Visão geral", "Principal"),
  page("day", "Meu dia", "Principal"),
  page("inbox", "Caixa de entrada", "Principal"),
  page("modules", "Explorar", "Principal"),
  page("tasks", "Tarefas", "Produtividade"),
  page("calendar", "Calendário", "Produtividade"),
  page("planner", "Planejar meu dia", "Produtividade"),
  page("projects", "Projetos", "Produtividade"),
  page("goals", "Metas", "Produtividade"),
  page("notes", "Notas", "Produtividade"),
  page("habits", "Hábitos", "Produtividade"),
  page("focus", "Foco", "Produtividade"),
  page("finance", "Centro financeiro", "Finanças"),
  page("favorites", "Favoritos", "Organização"),
  page("recent", "Recentes", "Organização"),
  page("templates", "Modelos", "Organização"),
  page("review", "Revisão da semana", "Inteligência"),
  page("automations", "Automações", "Inteligência"),
  page("notifications", "Notificações", "Sistema"),
  page("settings", "Configurações", "Sistema"),
  page("sync", "Sincronização", "Sistema"),
  page("diagnostics", "Diagnóstico", "Sistema"),
  page("backup", "Backup e dados", "Sistema"),
  page("privacy", "Privacidade", "Sistema"),
  page("navigation", "Personalizar menus", "Sistema"),
  ...catalog.map((s) =>
    page(
      "modules:" + s.type,
      s.label+(["Notas","Tarefas","Calendário","Projetos","Metas","Hábitos","Foco","Automações","Modelos"].includes(s.label)?" · registros":""),
      s.group === "Finanças"
        ? "Finanças"
        : s.group === "Sistema"
          ? "Inteligência"
          : s.group === "Produtividade" || s.group === "Notas e diário"
            ? "Produtividade"
            : ["Documentos", "Organização"].includes(s.group)
              ? "Organização"
              : "Vida pessoal",
      "module",
    ),
  ),
];
export interface NavigationPreferences {
  compact: boolean;
  home: string;
  favorites: string[];
  hidden: string[];
  order: string[];
  collapsed: string[];
}
export const defaultNavigation: NavigationPreferences = {
  compact: false,
  home: "home",
  favorites: [],
  hidden: [],
  order: [],
  collapsed: ["Vida pessoal"],
};
const ids = new Set(destinations.map((d) => d.id));
export function validRoute(id: string) {
  return ids.has(id);
}
export function navigationPreferences(value: unknown): NavigationPreferences {
  const x = (
    value && typeof value === "object" ? value : {}
  ) as Partial<NavigationPreferences>;
  const list = (v: unknown, allowed: Set<string>): string[] =>
    Array.isArray(v)
      ? [
          ...new Set(
            v.filter(
              (id): id is string => typeof id === "string" && allowed.has(id),
            ),
          ),
        ]
      : [];
  return {
    compact: x.compact === true,
    home: typeof x.home === "string" && validRoute(x.home) ? x.home : "home",
    favorites: list(x.favorites, ids),
    hidden: list(x.hidden, ids).filter(
      (id) => !["modules", "navigation", "settings"].includes(id),
    ),
    order: list(x.order, ids),
    collapsed: list(x.collapsed, new Set(navigationGroups)),
  };
}
export function orderedDestinations(p: NavigationPreferences) {
  return [...destinations].sort((a, b) => {
    const rank = (id: string) => {
      const n = p.order.indexOf(id);
      return n < 0 ? 10000 + destinations.findIndex((d) => d.id === id) : n;
    };
    return rank(a.id) - rank(b.id);
  });
}
export type NavigationHistory = {
  recent: string[];
  counts: Record<string, number>;
};
export function visit(
  history: NavigationHistory,
  id: string,
): NavigationHistory {
  if (!validRoute(id)) return history;
  return {
    recent: [
      id,
      ...history.recent.filter((x) => x !== id && validRoute(x)),
    ].slice(0, 8),
    counts: {
      ...history.counts,
      [id]: Math.min(100000, (history.counts[id] || 0) + 1),
    },
  };
}
export function frequent(history: NavigationHistory) {
  return Object.keys(history.counts)
    .filter(validRoute)
    .sort((a, b) => history.counts[b] - history.counts[a])
    .slice(0, 4);
}
export const financeNavigation = {
  "Dia a dia": ["Visão geral", "Movimentações", "Calendário financeiro"],
  Contas: ["Contas e cartões", "Recorrências", "Assinaturas"],
  Planejamento: ["Planejamento", "Simulador"],
  Análise: ["Conferência", "Relatórios"],
};
