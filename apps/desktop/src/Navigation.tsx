import React, { useState } from "react";
import {
  LayoutDashboard,
  Sun,
  Inbox,
  CheckSquare,
  CalendarDays,
  Target,
  FileText,
  Flame,
  Timer,
  Wallet,
  Grid2X2,
  Settings2,
  RefreshCw,
  ShieldCheck,
  Download,
  Bell,
  Star,
  History,
  ChevronDown,
  PanelLeftClose,
  PanelLeftOpen,
  ArrowUp,
  ArrowDown,
} from "lucide-react";
import {
  destinations,
  navigationGroups,
  NavigationPreferences,
  NavigationHistory,
  orderedDestinations,
  frequent,
  defaultNavigation,
} from "../shared/navigation";
import { Button, Field, Panel, Empty } from "./ui";
const icons: Record<string, typeof Grid2X2> = {
  home: LayoutDashboard,
  day: Sun,
  inbox: Inbox,
  tasks: CheckSquare,
  calendar: CalendarDays,
  planner: CalendarDays,
  projects: Target,
  goals: Target,
  notes: FileText,
  habits: Flame,
  focus: Timer,
  finance: Wallet,
  settings: Settings2,
  sync: RefreshCw,
  privacy: ShieldCheck,
  backup: Download,
  notifications: Bell,
  favorites: Star,
  recent: History,
};
export function DestinationIcon({ id }: { id: string }) {
  const Icon = icons[id] || Grid2X2;
  return <Icon size={18} />;
}
export function ContextNavigation({
  page,
  prefs,
  go,
  save,
}: {
  page: string;
  prefs: NavigationPreferences;
  go: (id: string) => void;
  save: (p: NavigationPreferences) => void;
}) {
  const current = destinations.find((d) => d.id === page);
  if (!current) return null;
  const related = destinations
    .filter(
      (d) =>
        d.group === current.group &&
        d.id !== page &&
        !d.id.startsWith("modules:"),
    )
    .slice(0, 3);
  const favorite = prefs.favorites.includes(page);
  return (
    <nav className="context-navigation" aria-label="Atalhos desta área">
      <button
        title={favorite ? "Desafixar página" : "Fixar página"}
        aria-label={favorite ? "Desafixar página" : "Fixar página"}
        aria-pressed={favorite}
        onClick={() =>
          save({
            ...prefs,
            favorites: favorite
              ? prefs.favorites.filter((id) => id !== page)
              : [...prefs.favorites, page],
          })
        }
      >
        <Star size={15} fill={favorite ? "currentColor" : "none"} />
        <span>{favorite ? "Página fixada" : "Fixar página"}</span>
      </button>
      {related.map((d) => (
        <button key={d.id} onClick={() => go(d.id)}>
          {d.label}
        </button>
      ))}
    </nav>
  );
}
export function SidebarNavigation({
  page,
  prefs,
  history,
  go,
  save,
}: {
  page: string;
  prefs: NavigationPreferences;
  history: NavigationHistory;
  go: (id: string) => void;
  save: (p: NavigationPreferences) => void;
}) {
  const button = (id: string) => {
    const d = destinations.find((x) => x.id === id);
    return d ? (
      <button
        key={id}
        title={d.label}
        aria-label={d.label}
        aria-current={page === id ? "page" : undefined}
        className={page === id ? "active" : ""}
        onClick={() => go(id)}
      >
        <DestinationIcon id={d.icon} />
        <span className="nav-text">{d.label}</span>
      </button>
    ) : null;
  };
  const ordered = orderedDestinations(prefs).filter(
    (d) => !prefs.hidden.includes(d.id),
  );
  const activeGroup = destinations.find((d) => d.id === page)?.group;
  return (
    <>
      <button
        className="nav-mode"
        aria-label={prefs.compact ? "Expandir menu" : "Recolher menu"}
        title={prefs.compact ? "Expandir menu" : "Recolher menu"}
        onClick={() => save({ ...prefs, compact: !prefs.compact })}
      >
        {prefs.compact ? (
          <PanelLeftOpen size={18} />
        ) : (
          <PanelLeftClose size={18} />
        )}
        <span className="nav-text">
          Menu {prefs.compact ? "compacto" : "expandido"}
        </span>
      </button>
      <nav aria-label="Navegação principal">
        {prefs.favorites.length > 0 && (
          <section>
            <div className="nav-group-label">Favoritos de páginas</div>
            {prefs.favorites.map(button)}
          </section>
        )}
        {!prefs.compact && history.recent.length > 0 && (
          <details className="nav-usage">
            <summary>Recentes e mais utilizados</summary>
            <div className="nav-group-label">Recentes</div>
            {history.recent.slice(0, 4).map(button)}
            <div className="nav-group-label">Mais utilizados</div>
            {frequent(history).map(button)}
          </details>
        )}
        {navigationGroups.map((group) => {
          const children = ordered.filter((d) => d.group === group);
          const expanded = !prefs.collapsed.includes(group);
          return (
            <section className="nav-section" key={group}>
              <button
                className="nav-group-toggle"
                aria-expanded={expanded}
                aria-label={group}
                title={group}
                onClick={() =>
                  save({
                    ...prefs,
                    collapsed: expanded
                      ? [...prefs.collapsed, group]
                      : prefs.collapsed.filter((x) => x !== group),
                  })
                }
              >
                <span className="nav-text">{group}</span>
                <ChevronDown size={14} className={expanded ? "expanded" : ""} />
              </button>
              <div
                className="nav-children"
                hidden={!expanded && !prefs.compact}
              >
                {children
                  .filter((d) => !d.id.startsWith("modules:"))
                  .map((d) => button(d.id))}
                {children.some((d) => d.id.startsWith("modules:")) && (
                  <details
                    className="nav-usage"
                    open={page.startsWith("modules:") && activeGroup === group}
                  >
                    <summary title={"Módulos de " + group}>
                      Módulos da área
                    </summary>
                    {children
                      .filter((d) => d.id.startsWith("modules:"))
                      .map((d) => button(d.id))}
                  </details>
                )}
              </div>
              {!expanded &&
                !prefs.compact &&
                activeGroup === group &&
                button(page)}
            </section>
          );
        })}
      </nav>
    </>
  );
}
export function NavigationPreferencesPanel({
  prefs,
  save,
}: {
  prefs: NavigationPreferences;
  save: (p: NavigationPreferences) => void;
}) {
  const [query, setQuery] = useState("");
  const ordered = orderedDestinations(prefs);
  const move = (id: string, delta: number) => {
    const group = destinations.find((d) => d.id === id)?.group;
    const siblings = ordered.filter((d) => d.group === group).map((d) => d.id);
    const n = siblings.indexOf(id),
      to = n + delta;
    if (to < 0 || to >= siblings.length) return;
    const order = ordered.map((d) => d.id),
      a = order.indexOf(id),
      b = order.indexOf(siblings[to]);
    [order[a], order[b]] = [order[b], order[a]];
    save({ ...prefs, order });
  };
  const toggle = (key: "favorites" | "hidden", id: string) =>
    save({
      ...prefs,
      [key]: prefs[key].includes(id)
        ? prefs[key].filter((x) => x !== id)
        : [...prefs[key], id],
    });
  const visible = ordered.filter((d) =>
    (d.label + " " + d.group)
      .toLocaleLowerCase()
      .includes(query.toLocaleLowerCase()),
  );
  return (
    <>
      <div className="page-title">
        <div>
          <div className="eyebrow">SEU ESPAÇO, SUA ORDEM</div>
          <h1>Personalizar menus</h1>
          <p>
            Ocultar um atalho mantém a página disponível na pesquisa e em
            Explorar.
          </p>
        </div>
      </div>
      <Panel title="Organização">
        <div className="actions">
          <Button onClick={() => save({ ...prefs, compact: !prefs.compact })}>
            {prefs.compact ? "Usar menu expandido" : "Usar menu compacto"}
          </Button>
          <Button onClick={() => save({ ...defaultNavigation })}>
            Restaurar organização padrão
          </Button>
        </div>
        <Field
          label="Página inicial"
          value={prefs.home}
          onChange={(home) => save({ ...prefs, home })}
          options={destinations.map((d) => ({
            value: d.id,
            label: d.group + " / " + d.label,
          }))}
        />
        <p className="muted">
          Organização dos menus sincronizada por conta e plataforma quando
          conectado. Recentes e contagens ficam neste dispositivo. Ctrl/Cmd+K
          encontra páginas e conteúdo.
        </p>
      </Panel>
      <Panel title="Atalhos e favoritos">
        <Field
          label="Encontrar página"
          value={query}
          onChange={setQuery}
          placeholder="Nome ou área"
        />
        <div className="navigation-editor">
          {visible.map((d) => (
            <div className="navigation-row" key={d.id}>
              <DestinationIcon id={d.icon} />
              <span>
                <strong>{d.label}</strong>
                <small>{d.group}</small>
              </span>
              <button
                aria-label={"Favoritar " + d.label}
                aria-pressed={prefs.favorites.includes(d.id)}
                onClick={() => toggle("favorites", d.id)}
              >
                <Star
                  size={18}
                  fill={
                    prefs.favorites.includes(d.id) ? "currentColor" : "none"
                  }
                />
              </button>
              <label>
                <input
                  type="checkbox"
                  disabled={["modules", "navigation", "settings"].includes(
                    d.id,
                  )}
                  checked={!prefs.hidden.includes(d.id)}
                  onChange={() => toggle("hidden", d.id)}
                />
                Atalho
              </label>
              <button
                aria-label={"Subir " + d.label}
                disabled={ordered.find((x) => x.group === d.group)?.id === d.id}
                onClick={() => move(d.id, -1)}
              >
                <ArrowUp size={16} />
              </button>
              <button
                aria-label={"Descer " + d.label}
                disabled={
                  ordered.filter((x) => x.group === d.group).at(-1)?.id === d.id
                }
                onClick={() => move(d.id, 1)}
              >
                <ArrowDown size={16} />
              </button>
            </div>
          ))}
        </div>
        {!visible.length && (
          <Empty
            title="Nenhuma página encontrada"
            detail="Tente outro nome ou área."
          />
        )}
      </Panel>
    </>
  );
}
