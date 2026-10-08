import { Boundary } from "./Boundary";
import React, {
  useCallback,
  useEffect,
  useRef,
  useState,
  lazy,
  Suspense,
} from "react";
import { createRoot } from "react-dom/client";
import {
  LayoutDashboard,
  Wallet,
  CheckSquare,
  FileText,
  CalendarDays,
  Flame,
  Target,
  Timer,
  Grid2X2,
  Settings2,
  Search,
  Plus,
  Cloud,
  CloudOff,
  RefreshCw,
  Check,
  ArrowUpRight,
  ArrowRight,
  ChevronDown,
  Sun,
  CloudSun,
  GripVertical,
  X,
  Monitor,
  Paperclip,
  Pause,
  Play,
  Square,
  Loader2,
  ShieldCheck,
} from "lucide-react";
import {
  Snapshot,
  Item,
  createItem,
  today,
  addDays,
  money,
  amount,
} from "../shared/model";
import { catalog } from "../shared/catalog";
import {
  api,
  Button,
  IconButton,
  Panel,
  Empty,
  Editor,
  RecordRow,
  Modal,
  Chart,
  SearchBox,
  labels,
  Field,
  dateLabel,
} from "./ui";
import { habitStats } from "../shared/productivity";
import {
  widgetCatalog,
  defaultDashboard,
  dailySummary,
} from "../shared/platform";
import { reportRows } from "../shared/reporting";
import { modules } from "../shared/commands";
import { QuickComposer, CommandPalette } from "./CommandCenter";
const FinanceView = lazy(() =>
  import("./FinanceView").then((m) => ({ default: m.FinanceView })),
);
const Tasks = lazy(() =>
  import("./Productivity").then((m) => ({ default: m.Tasks })),
);
const Notes = lazy(() => import("./Notes"));
const Calendar = lazy(() =>
  import("./Productivity").then((m) => ({ default: m.Calendar })),
);
const Habits = lazy(() =>
  import("./Productivity").then((m) => ({ default: m.Habits })),
);
const Goals = lazy(() =>
  import("./Productivity").then((m) => ({ default: m.Goals })),
);
const Focus = lazy(() =>
  import("./Productivity").then((m) => ({ default: m.Focus })),
);
const Settings = lazy(() =>
  import("./Settings").then((m) => ({ default: m.Settings })),
);
const Workspaces = lazy(() => import("./Workspaces"));
const CommandHome = lazy(() =>
  import("./Workspaces").then((m) => ({ default: m.CommandHome })),
);
const Brief = lazy(() =>
  import("./Workspaces").then((m) => ({ default: m.Brief })),
);
const ExtraWidget = lazy(() =>
  import("./Workspaces").then((m) => ({ default: m.ExtraWidget })),
);
import { VeyraGlassPanel } from "./Glass";
import { isPanelRole } from "../shared/glass";
import "./styles.css";
import "./glass.css";
import "./platform.css";
if (isPanelRole(location.hash.slice(1)))
  document.documentElement.dataset.floating = "true";
const navigation = [
  ["home", "Central", LayoutDashboard],
  ["day", "Meu dia", Sun],
  ["finance", "Finanças", Wallet],
  ["tasks", "Tarefas", CheckSquare],
  ["notes", "Notas", FileText],
  ["calendar", "Calendário", CalendarDays],
  ["habits", "Hábitos", Flame],
  ["goals", "Metas e projetos", Target],
  ["focus", "Foco", Timer],
  ["modules", "Explorar", Grid2X2],
] as const;
const widgets = widgetCatalog;
const defaultLayout = defaultDashboard;
const syncLabels: Record<string, string> = {
  local: "Neste PC",
  ready: "Sincronizado",
  syncing: "Sincronizando",
  offline: "Offline",
  pending: "Alterações pendentes",
  conflict: "Revisar conflitos",
  verification: "Verifique seu e-mail",
  error: "Conexão pendente",
};
function App() {
  const role = location.hash.slice(1) || "main";
  const [data, setData] = useState<Snapshot | null>(null);
  const [page, setPage] = useState(role === "notes" ? "notes" : "home");
  const [month, setMonth] = useState(today().slice(0, 7));
  const [days, setDays] = useState(30);
  const [editor, setEditorState] = useState<{
    item?: Item;
    type: string;
    uid?: string | null;
  } | null>(null);
  const setEditor = (value: { item?: Item; type: string } | null) =>
    setEditorState(value ? { ...value, uid: data?.uid } : null);
  const [palette, setPalette] = useState(false);
  const [quickAdd, setQuickAdd] = useState(false);
  const [onboarding, setOnboarding] = useState(false);
  const [toast, setToast] = useState("");
  const [undo, setUndo] = useState<{ id: string; until: number } | null>(null);
  useEffect(() => {
    const action = data?.desktop.lastUndo;
    if (!action || action.until <= Date.now()) {
      setUndo(null);
      return;
    }
    setUndo(action);
    const timeout = setTimeout(() => setUndo(null), action.until - Date.now());
    return () => clearTimeout(timeout);
  }, [data?.desktop.lastUndo?.id, data?.desktop.lastUndo?.until, data?.uid]);
  const [quickClosing, setQuickClosing] = useState(false);
  const [clipboard, setClipboard] = useState("");
  const [dropPath, setDropPath] = useState("");
  const [dragging, setDragging] = useState(false);
  const refresh = useCallback(async () => {
    try {
      const snapshot: Snapshot = await api("snapshot", { month, days });
      setData((previous) => ({
        ...snapshot,
        items:
          previous?.uid === snapshot.uid &&
          previous.desktop.dataVersion === snapshot.desktop.dataVersion
            ? previous.items
            : snapshot.items,
      }));
    } catch (e) {
      setToast(cleanError(e));
    }
  }, [month, days]);
  useEffect(() => {
    void refresh();
    let timer: ReturnType<typeof setTimeout>;
    const unsubscribe = window.veyra.onChange(() => {
      clearTimeout(timer);
      timer = setTimeout(() => void refresh(), 60);
    });
    return () => {
      clearTimeout(timer);
      unsubscribe();
    };
  }, [refresh]);
  const previousUid = useRef<string | null | undefined>(undefined);
  useEffect(() => {
    if (!data) return;
    if (previousUid.current !== undefined) {
      setEditor(null);
      setPalette(false);
      setQuickAdd(false);
      setClipboard("");
      setDropPath("");
    }
    previousUid.current = data.uid;
    if (role === "main" && !data.uid && !data.desktop.welcomeSeen)
      setOnboarding(true);
    if (data && data.desktop.openDay && role === "main") setPage("day");
  }, [data?.uid]);
  const create = (type: string) => {
    if (isPanelRole(role) && role !== "quick") void api("capture", { type });
    else setEditor({ type });
  };
  const open = async (item: Item) => {
    if (isPanelRole(role) && role !== "quick") {
      await api("capture", { id: item.id });
      return;
    }
    const identity = data?.uid;
    const full: Item = await api("item", item.id);
    if (currentData.current?.uid !== identity) return;
    setEditor({ type: item.type, item: full || item });
  };
  const navigate = (next: string) => {
    if (["main", "notes"].includes(role)) setPage(next);
    else void api("navigate", next);
  };
  const currentData = useRef(data);
  currentData.current = data;
  const choose = (item: Item) => {
    setQuickAdd(false);
    setPalette(false);
    setEditorState({ type: item.type, item, uid: currentData.current?.uid });
  };
  const runCommand = useCallback((command: string) => {
    const identity = currentData.current?.uid;
    if (command.startsWith("edit:"))
      void api("item", command.slice(5)).then((item) => {
        if (item && currentData.current?.uid === identity)
          setEditorState({
            type: item.type,
            item,
            uid: currentData.current?.uid,
          });
      });
    else if (command.startsWith("new:"))
      setEditorState({ type: command.slice(4), uid: currentData.current?.uid });
    else if (command === "capture") {
      setEditor(null);
      setPalette(false);
    } else if (command === "spotlight") {
      setEditor(null);
      setPalette(true);
    } else if (command === "palette") setPalette(true);
    else setPage(command);
  }, []);
  useEffect(() => window.veyra.onCommand(runCommand), [runCommand]);
  useEffect(() => {
    const listener = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        setPalette((v) => !v);
      } else if (
        (e.ctrlKey || e.metaKey) &&
        !e.altKey &&
        !e.shiftKey &&
        e.key.toLowerCase() === "n"
      ) {
        e.preventDefault();
        setQuickAdd(true);
      } else if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "p") {
        e.preventDefault();
        setPage("favorites");
      } else if (
        (e.ctrlKey || e.metaKey) &&
        e.shiftKey &&
        ["t", "g", "n"].includes(e.key.toLowerCase())
      ) {
        e.preventDefault();
        setEditorState({
          type: (
            { t: "task", g: "expense", n: "note" } as Record<string, string>
          )[e.key.toLowerCase()],
          uid: currentData.current?.uid,
        });
      }
    };
    const error = (event: PromiseRejectionEvent) => {
      event.preventDefault();
      setToast(cleanError(event.reason));
    };
    window.addEventListener("keydown", listener);
    window.addEventListener("unhandledrejection", error);
    return () => {
      window.removeEventListener("keydown", listener);
      window.removeEventListener("unhandledrejection", error);
    };
  }, []);
  useEffect(() => {
    if (!toast) return;
    const timeout = setTimeout(() => setToast(""), 6500);
    return () => clearTimeout(timeout);
  }, [toast]);
  useEffect(() => {
    if (!data) return;
    const raw = data.desktop.theme || data.preferences.theme || "system";
    const media = matchMedia("(prefers-color-scheme: dark)");
    const applyTheme = () => {
      const theme =
        raw === "Claro" || raw === "light"
          ? "light"
          : raw === "Escuro" || raw === "dark"
            ? "dark"
            : media.matches
              ? "dark"
              : "light";
      document.documentElement.dataset.theme = theme;
    };
    applyTheme();
    media.addEventListener("change", applyTheme);
    document.documentElement.dataset.performance =
      data.desktop.performanceMode || "auto";
    document.documentElement.dataset.animations =
      data.desktop.animations === false ? "off" : "on";
    document.documentElement.dataset.accent = data.desktop.accent || "violet";
    document.documentElement.dataset.density =
      data.desktop.density || "comfortable";
    document.documentElement.style.fontSize =
      (Number(data.desktop.scale || 100) / 100) * 14 + "px";
    return () => media.removeEventListener("change", applyTheme);
  }, [
    data?.desktop.theme,
    data?.desktop.performanceMode,
    data?.desktop.animations,
    data?.desktop.accent,
    data?.desktop.scale,
    data?.desktop.density,
    data?.preferences.theme,
  ]);
  if (!data)
    return (
      <div className="loading">
        <div className="brand-mark">v</div>
        <h2>Abrindo seu espaço.</h2>
        <Loader2 className="spin" />
        <p>Armazenamento protegido no computador.</p>
      </div>
    );
  const hidden =
    data.preferences.financeHidden === "yes" ||
    data.preferences.financeHideValues === "yes";
  const cash = (n: number) =>
    data.finance.error
      ? "—"
      : hidden
        ? "••••"
        : money(n, data.finance.currency);
  const shortName =
    data.user?.name.split(" ")[0] ||
    data.preferences.name?.split(" ")[0] ||
    "você";
  const complete = (i: Item) =>
    void api("save", {
      base: i,
      item: {
        ...i,
        done: !i.done,
        fields: { ...i.fields, status: i.done ? "A fazer" : "Concluído" },
      },
    });
  const attachDropped = async (type: string) => {
    const attachment = await api("attach", { path: dropPath });
    setDropPath("");
    if (attachment)
      setEditor({ type, item: createItem(type, { fields: attachment }) });
  };
  const editorNode = editor && (
    <Editor
      key={String(editor.uid) + editor.item?.id + editor.type}
      expectedUid={editor.uid === undefined ? data.uid : editor.uid}
      item={editor.item}
      initialType={editor.type}
      all={data.items}
      onClose={() => {
        setEditor(null);
        if (role === "quick") {
          setQuickClosing(true);
          setTimeout(() => void api("closeWindow"), 130);
        }
      }}
      onSaved={() => {
        setToast("Registro salvo.");
        void refresh();
      }}
    />
  );
  if (isPanelRole(role)) {
    const mode = data.desktop.panels?.[role]?.mode || "normal";
    const widget = (id: string) => (
      <Widget
        key={id}
        id={id}
        data={data}
        open={open}
        create={create}
        go={navigate}
        cash={cash}
        complete={complete}
      />
    );
    const modules = data.desktop.miniWidgets || [
      "clock",
      "weather",
      "tasks",
      "calendar",
      "finance",
      "habits",
      "focus",
      "shortcuts",
    ];
    return (
      <VeyraGlassPanel role={role} data={data} exiting={quickClosing}>
        {role === "brief" ? (
          <Suspense fallback={null}>
            <Brief data={data} go={navigate} cash={cash} />
          </Suspense>
        ) : role === "quick" ? (
          palette ? (
            <CommandPalette
              data={data}
              inline
              open={open}
              choose={choose}
              go={navigate}
              close={() => setPalette(false)}
            />
          ) : !editor ? (
            <QuickComposer data={data} choose={choose} />
          ) : null
        ) : role === "widget-focus" || role === "widget-timer" ? (
          <CompactFocus data={data} stopwatch={role === "widget-timer"} />
        ) : role === "mini" || role === "dock" ? (
          <>
            <div className={role === "dock" ? "glass-dock" : "glass-modules"}>
              {modules
                .slice(0, mode === "compact" ? 3 : 10)
                .map((id: string) => (
                  <FloatingModule
                    key={id}
                    id={id}
                    data={data}
                    cash={cash}
                    go={navigate}
                    complete={complete}
                  />
                ))}
            </div>
            <details className="glass-module-picker">
              <summary>Escolher informações</summary>
              {widgets.map(([id, title]) => (
                <label className="toggle" key={id}>
                  <input
                    type="checkbox"
                    checked={modules.includes(id)}
                    onChange={(e) =>
                      void api("desktop", {
                        miniWidgets: e.target.checked
                          ? [...modules, id]
                          : modules.filter((v: string) => v !== id),
                      })
                    }
                  />
                  {title}
                </label>
              ))}
            </details>
          </>
        ) : mode === "micro" ? (
          <MicroWidget role={role} data={data} cash={cash} />
        ) : (
          widget(role.slice(7))
        )}
        {editorNode}
        {toast && (
          <div className="toast" role="status">
            {toast}
          </div>
        )}
      </VeyraGlassPanel>
    );
  }
  return (
    <div
      className="app"
      onDragOver={(e) => {
        if (e.dataTransfer.types.includes("Files")) {
          e.preventDefault();
          setDragging(true);
        }
      }}
      onDragLeave={(e) => {
        if (e.currentTarget === e.target) setDragging(false);
      }}
      onDrop={(e) => {
        if (e.dataTransfer.files.length) {
          e.preventDefault();
          setDragging(false);
          setDropPath(window.veyra.filePath(e.dataTransfer.files[0]));
        }
      }}
    >
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark">v</div>
          <div>
            <strong>
              veyra<span>life</span>
            </strong>
            <small>Seu espaço pessoal</small>
          </div>
        </div>
        <button
          className="workspace-switch"
          onClick={() => setOnboarding(true)}
        >
          <div className="avatar">{shortName.slice(0, 1).toUpperCase()}</div>
          <div>
            <strong>{data.user ? "Minha conta" : "Espaço visitante"}</strong>
            <small>
              {data.user ? "PC + celular" : "Somente neste computador"}
            </small>
          </div>
          <ChevronDown size={14} />
        </button>
        <div className="nav-caption">SEU ESPAÇO</div>
        <nav>
          {modules.map(({ id, label: title, group }, n) => {
            const Icon = navigation.find(([key]) => key === id)?.[2] || Grid2X2;
            return (
              <React.Fragment key={id}>
                {n === 0 || modules[n - 1].group !== group ? (
                  <div className="nav-group-label">{group}</div>
                ) : null}
                <button
                  key={id}
                  className={page === id ? "active" : ""}
                  onClick={() => setPage(id)}
                >
                  <Icon size={18} />
                  {title}
                  {id === "tasks" &&
                    data.items.some((i) => i.type === "task" && !i.done) && (
                      <span className="nav-count">
                        {
                          data.items.filter((i) => i.type === "task" && !i.done)
                            .length
                        }
                      </span>
                    )}
                </button>
              </React.Fragment>
            );
          })}
        </nav>
        <div className="sidebar-bottom">
          <button className="shortcut-card" onClick={() => setQuickAdd(true)}>
            <Plus size={18} />
            <div>
              <strong>Capture uma ideia</strong>
              <small>Ctrl + Shift + Space</small>
            </div>
          </button>
          <button
            className={"settings-nav " + (page === "settings" ? "active" : "")}
            onClick={() => setPage("settings")}
          >
            <Settings2 size={18} />
            Configurações
            {data.sync.conflicts > 0 && <span className="notification-dot" />}
          </button>
          <span className="sidebar-footnote">
            <ShieldCheck size={12} />
            Privado. Seu. Em todo lugar.
          </span>
        </div>
      </aside>
      <section className="workspace">
        <header className="topbar">
          <div className="breadcrumb">
            Meu espaço <span>/</span>{" "}
            {page === "settings"
              ? "Configurações"
              : modules.find((m) => m.id === page)?.label || "Central"}
          </div>
          <button className="global-search" onClick={() => setPalette(true)}>
            <Search size={16} />
            <span>Pesquisar ou executar um comando</span>
            <kbd>Ctrl K</kbd>
          </button>
          <div
            className={"sync-indicator " + data.sync.status}
            title={data.sync.error || "Sincronização automática"}
            onClick={() => setPage("sync")}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => e.key === "Enter" && setPage("sync")}
          >
            {data.sync.status === "syncing" ? (
              <RefreshCw size={13} className="spin" />
            ) : data.sync.status === "offline" ? (
              <CloudOff size={14} />
            ) : data.sync.status === "ready" ? (
              <Check size={14} />
            ) : (
              <Cloud size={14} />
            )}
            <span>{syncLabels[data.sync.status]}</span>
          </div>
          <IconButton
            label="Mini dashboard"
            onClick={() => void api("openWindow", "mini")}
          >
            <Monitor size={17} />
          </IconButton>
          <IconButton label="Captura rápida" onClick={() => setQuickAdd(true)}>
            <Plus size={20} />
          </IconButton>
        </header>
        <main
          key={data.uid || "guest"}
          className={
            "content " +
            (page === "focus" && data.focus.active ? "focus-mode" : "")
          }
        >
          <Boundary key={(data.uid || "guest") + page}>
            <Suspense
              fallback={
                <div className="module-skeleton" aria-label="Carregando módulo">
                  <i />
                  <i />
                  <i />
                </div>
              }
            >
              {["home", "day"].includes(page) ? (
                <Dashboard
                  data={data}
                  dayMode={page === "day"}
                  shortName={shortName}
                  open={open}
                  create={create}
                  go={setPage}
                  cash={cash}
                  complete={complete}
                />
              ) : page === "finance" ? (
                <FinanceView
                  data={data}
                  open={open}
                  create={create}
                  month={month}
                  setMonth={setMonth}
                  days={days}
                  setDays={setDays}
                />
              ) : page === "tasks" ? (
                <Tasks data={data} open={open} create={create} />
              ) : page === "notes" ? (
                <Notes data={data} create={create} />
              ) : page === "calendar" ? (
                <Calendar data={data} open={open} create={create} />
              ) : page === "habits" ? (
                <Habits data={data} open={open} create={create} />
              ) : page === "goals" ? (
                <Goals data={data} open={open} create={create} />
              ) : page === "focus" ? (
                <Focus data={data} />
              ) : [
                  "inbox",
                  "projects",
                  "planner",
                  "review",
                  "automations",
                  "templates",
                  "notifications",
                  "sync",
                  "diagnostics",
                  "recent",
                  "favorites",
                ].includes(page) ? (
                <Workspaces
                  page={page}
                  data={data}
                  open={open}
                  create={create}
                  choose={choose}
                  go={navigate}
                />
              ) : ["settings", "backup", "privacy"].includes(page) ? (
                <Settings
                  data={data}
                  initialTab={
                    page === "backup"
                      ? "Dados"
                      : page === "privacy"
                        ? "Privacidade"
                        : undefined
                  }
                  onLogin={() => setOnboarding(true)}
                />
              ) : (
                <Modules data={data} open={open} create={create} />
              )}
            </Suspense>
          </Boundary>
          <footer className="content-footer">
            <span>Um pouco de ordem. Muito mais possibilidades.</span>
            <span>Veyra Life · {data.version}</span>
          </footer>
        </main>
      </section>
      {editorNode}
      {undo && (
        <div className="undo-toast" role="status">
          <span>Registro movido para a lixeira</span>
          <Button
            onClick={() =>
              void api("restore", undo.id).then(() => setUndo(null))
            }
          >
            Desfazer
          </Button>
        </div>
      )}
      {palette && (
        <CommandPalette
          data={data}
          close={() => setPalette(false)}
          open={open}
          choose={choose}
          go={navigate}
        />
      )}
      {quickAdd && (
        <Modal title="Captura universal" onClose={() => setQuickAdd(false)}>
          <QuickComposer data={data} choose={choose} />
        </Modal>
      )}
      {onboarding && (
        <Onboarding data={data} close={() => setOnboarding(false)} />
      )}{" "}
      {toast && (
        <div className="toast" role="status">
          <Check size={16} />
          <span>{toast}</span>
          <button aria-label="Fechar aviso" onClick={() => setToast("")}>
            <X size={15} />
          </button>
        </div>
      )}
      {dragging && (
        <div className="drop-overlay">
          <Paperclip size={40} />
          <h2>Um arquivo, novas possibilidades.</h2>
          <p>
            Solte para escolher onde anexar. O arquivo ficará somente neste PC.
          </p>
        </div>
      )}
      {dropPath && (
        <Modal
          title="Onde guardar este arquivo?"
          onClose={() => setDropPath("")}
        >
          <div className="form-body">
            <p>
              Escolha um registro. Você vai revisar os detalhes antes de salvar.
            </p>
            <div className="capture-options">
              {[
                ["expense", "Uma transação", Wallet],
                ["note", "Uma nota", FileText],
                ["task", "Uma tarefa", CheckSquare],
              ].map(([type, title, Icon]: any) => (
                <Button key={type} onClick={() => void attachDropped(type)}>
                  <Icon size={20} />
                  {title}
                </Button>
              ))}
            </div>
            <p className="muted">Nenhum arquivo será enviado à nuvem.</p>
          </div>
        </Modal>
      )}
      {clipboard && (
        <Modal
          title="Texto copiado • somente neste PC"
          onClose={() => setClipboard("")}
        >
          <div className="form-body">
            <pre>{clipboard.slice(0, 2000)}</pre>
            <div className="actions">
              {/^\s*(?:R\$\s*)?\d+(?:[.,]\d{2})\s*$/.test(clipboard) && (
                <Button
                  onClick={() => {
                    setEditor({
                      type: "expense",
                      item: createItem("expense", {
                        title: "Gasto capturado",
                        fields: {
                          amount: clipboard.replace(/R\$/g, "").trim(),
                        },
                      }),
                    });
                    setClipboard("");
                  }}
                >
                  Registrar gasto
                </Button>
              )}
              {/^(https?:\/\/)/.test(clipboard.trim()) && (
                <Button
                  onClick={() => {
                    setEditor({
                      type: "link",
                      item: createItem("link", {
                        title: "Link capturado",
                        fields: { url: clipboard.trim() },
                      }),
                    });
                    setClipboard("");
                  }}
                >
                  Salvar link
                </Button>
              )}
              <Button
                onClick={() => {
                  setEditor({
                    type: "note",
                    item: createItem("note", {
                      title: "Texto capturado",
                      notes: clipboard,
                    }),
                  });
                  setClipboard("");
                }}
              >
                Criar nota
              </Button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  );
}
function cleanError(error: any) {
  return String(error?.message || error || "Não foi possível concluir.")
    .replace(/^Error invoking remote method '[^']+': Error: /, "")
    .slice(0, 500);
}
function Dashboard({
  data,
  dayMode,
  shortName,
  open,
  create,
  go,
  cash,
  complete,
}: {
  data: Snapshot;
  dayMode: boolean;
  shortName: string;
  open: (i: Item) => void;
  create: (s: string) => void;
  go: (s: string) => void;
  cash: (n: number) => string;
  complete: (i: Item) => void;
}) {
  const [customize, setCustomize] = useState(false);
  const layout: { id: string; size: string; height?: number }[] =
    data.desktop.layout || defaultLayout;
  const tasks = data.items.filter(
    (i) => i.type === "task" && !i.done && i.date === today(),
  );
  const events = data.items
    .filter(
      (i) =>
        ["event", "task", "bill", "inbox"].includes(i.type) &&
        i.date === today() &&
        !i.done,
    )
    .sort((a, b) =>
      (a.fields.time || a.fields.reminder || "23:59").localeCompare(
        b.fields.time || b.fields.reminder || "23:59",
      ),
    );
  function drop(e: React.DragEvent, id: string) {
    e.preventDefault();
    const dragged = e.dataTransfer.getData("application/veyra-widget");
    if (!dragged) return;
    const next = layout.filter((w) => w.id !== dragged);
    const old = layout.find((w) => w.id === dragged);
    if (!old) return;
    next.splice(
      Math.max(
        0,
        next.findIndex((w) => w.id === id),
      ),
      0,
      old,
    );
    void api("desktop", { layout: next });
  }
  return (
    <>
      <div className="dashboard-greeting">
        <div>
          <div className="eyebrow">
            {new Date()
              .toLocaleDateString("pt-BR", {
                weekday: "long",
                day: "numeric",
                month: "long",
              })
              .toUpperCase()}
          </div>
          <h1>
            {new Date().getHours() < 12
              ? "Bom dia"
              : new Date().getHours() < 18
                ? "Boa tarde"
                : "Boa noite"}
            , {shortName}.
          </h1>
          <p>
            {dayMode
              ? "Um dia de cada vez. Vamos dar espaço ao que importa."
              : "Tudo em seu lugar. Mais espaço para viver."}
          </p>
        </div>
        <div className="actions">
          <Button onClick={() => setCustomize(!customize)}>
            <Grid2X2 size={15} />
            {customize ? "Concluir edição" : "Personalizar"}
          </Button>
          <Button kind="primary" onClick={() => create("task")}>
            <Plus size={17} />
            Capturar
          </Button>
        </div>
      </div>
      <Suspense fallback={null}>
        <CommandHome data={data} open={open} go={go} cash={cash} />
      </Suspense>
      <div className="day-strip">
        <span>
          <i className="dot mint" />
          {tasks.length}{" "}
          {tasks.length === 1 ? "tarefa de hoje" : "tarefas de hoje"}
        </span>
        <span>
          <i className="dot accent" />
          {
            data.items.filter((i) => i.type === "event" && i.date === today())
              .length
          }{" "}
          eventos
        </span>
        <span>
          <i className="dot amber" />
          {data.items.filter((i) => i.type === "habit").length} hábitos
        </span>
        <span className="push">Seu dia, no seu ritmo.</span>
      </div>
      {customize && (
        <Panel
          title="Sua central, do seu jeito"
          subtitle="Galeria de widgets com prévia. Arraste para reorganizar ou use os controles de posição."
        >
          <div className="actions">
            <Button
              onClick={() => void api("desktop", { layout: defaultLayout })}
            >
              Restaurar layout padrão
            </Button>
          </div>
          <div className="widget-gallery">
            {[...new Set(widgets.map((w) => w[2]))].map((category) => (
              <section key={category}>
                <h3>{category}</h3>
                {widgets
                  .filter((w) => w[2] === category)
                  .map(([id, title]) => (
                    <button
                      className="gallery-card"
                      key={id}
                      disabled={layout.some((w) => w.id === id)}
                      onClick={() =>
                        void api("desktop", {
                          layout: [...layout, { id, size: "normal" }],
                        })
                      }
                    >
                      <div className="gallery-preview" aria-hidden="true">
                        <span>{title}</span>
                        <strong>
                          {id === "finance"
                            ? cash(data.finance.balance)
                            : id === "tasks"
                              ? data.items.filter(
                                  (i) => i.type === "task" && !i.done,
                                ).length + " em aberto"
                              : id === "clock"
                                ? new Date().toLocaleTimeString("pt-BR", {
                                    hour: "2-digit",
                                    minute: "2-digit",
                                  })
                                : id === "notes"
                                  ? data.items.filter((i) => i.type === "note")
                                      .length + " notas"
                                  : id === "habits"
                                    ? data.items.filter(
                                        (i) => i.type === "habit",
                                      ).length + " hábitos"
                                    : id === "weather"
                                      ? data.desktop.weather?.temperature
                                        ? data.desktop.weather.temperature +
                                          "°C"
                                        : "Escolher cidade"
                                      : id === "inbox"
                                        ? data.items.filter(
                                            (i) =>
                                              i.type === "inbox" && !i.done,
                                          ).length + " capturas"
                                        : id === "focus"
                                          ? data.focus.active
                                            ? data.focus.title
                                            : "25:00"
                                          : id === "spending"
                                            ? "Resumo de hoje"
                                            : id === "calendar"
                                              ? "Próximos compromissos"
                                              : id === "bills"
                                                ? "Vencimentos e previsão"
                                                : "Seus dados, seu contexto"}
                        </strong>
                        <i />
                      </div>
                      <strong>{title}</strong>
                      <small>
                        {layout.some((w) => w.id === id)
                          ? "Adicionado"
                          : "Adicionar widget"}
                      </small>
                    </button>
                  ))}
              </section>
            ))}
          </div>
        </Panel>
      )}
      {dayMode && (
        <Panel
          title="Timeline de hoje"
          subtitle="Compromissos, tarefas e lembretes em ordem"
        >
          {events.length ? (
            events.map((i) => (
              <div className="timeline-entry" key={i.id}>
                <span>{i.fields.time || i.fields.reminder || "Dia todo"}</span>
                <button onClick={() => open(i)}>
                  <strong>{i.title}</strong>
                  <small>{labels[i.type]}</small>
                </button>
                <span className="badge">
                  {i.fields.priority || "Planejado"}
                </span>
              </div>
            ))
          ) : (
            <Empty
              title="Seu dia tem espaço"
              detail="Escolha uma tarefa, evento ou hábito para começar."
              onAdd={() => create("event")}
            />
          )}
        </Panel>
      )}
      <div className="dashboard-grid">
        {layout.map((w, position) => (
          <div
            className={
              "dashboard-widget " + w.size + " " + (customize ? "editable" : "")
            }
            key={w.id}
            style={w.height ? { height: w.height } : undefined}
            onPointerUp={(e) => {
              if (!customize) return;
              const node = e.currentTarget;
              if (!node.style.height) return;
              const height = Math.round(node.getBoundingClientRect().height);
              if (Math.abs(height - (w.height || 180)) > 2)
                void api("desktop", {
                  layout: layout.map((i) =>
                    i.id === w.id ? { ...i, height } : i,
                  ),
                });
            }}
            draggable={customize}
            onDragStart={(e) =>
              e.dataTransfer.setData("application/veyra-widget", w.id)
            }
            onDragOver={(e) => {
              if (customize) e.preventDefault();
            }}
            onDrop={(e) => drop(e, w.id)}
          >
            {customize && (
              <div className="widget-controls">
                <GripVertical size={14} />
                <Button
                  disabled={position === 0}
                  onClick={() => {
                    const next = [...layout];
                    [next[position - 1], next[position]] = [
                      next[position],
                      next[position - 1],
                    ];
                    void api("desktop", { layout: next });
                  }}
                >
                  ←
                </Button>
                <Button
                  disabled={position === layout.length - 1}
                  onClick={() => {
                    const next = [...layout];
                    [next[position + 1], next[position]] = [
                      next[position],
                      next[position + 1],
                    ];
                    void api("desktop", { layout: next });
                  }}
                >
                  →
                </Button>
                <select
                  value={w.size}
                  aria-label={"Tamanho de " + w.id}
                  onChange={(e) =>
                    void api("desktop", {
                      layout: layout.map((i) =>
                        i.id === w.id ? { ...i, size: e.target.value } : i,
                      ),
                    })
                  }
                >
                  <option value="normal">Normal</option>
                  <option value="wide">Largo</option>
                  <option value="full">Linha inteira</option>
                </select>
                <IconButton
                  label={"Remover widget " + w.id}
                  onClick={() =>
                    void api("desktop", {
                      layout: layout.filter((i) => i.id !== w.id),
                    })
                  }
                >
                  <X size={13} />
                </IconButton>
              </div>
            )}
            <Widget
              id={w.id}
              data={data}
              open={open}
              create={create}
              go={go}
              cash={cash}
              complete={complete}
            />
          </div>
        ))}
      </div>
    </>
  );
}
function Widget({
  id,
  data,
  open,
  create,
  go,
  cash,
  complete,
}: {
  id: string;
  data: Snapshot;
  open: (i: Item) => void;
  create: (s: string) => void;
  go: (s: string) => void;
  cash: (n: number) => string;
  complete: (i: Item) => void;
}) {
  const items = data.items;
  const f = data.finance;
  const action = (page: string) => (
    <IconButton label="Ver tudo" onClick={() => go(page)}>
      <ArrowUpRight size={17} />
    </IconButton>
  );
  if (["bills", "inbox", "recent-spending", "progress"].includes(id))
    return (
      <Suspense fallback={null}>
        <ExtraWidget id={id} data={data} open={open} go={go} cash={cash} />
      </Suspense>
    );
  if (id === "finance")
    return (
      <Panel
        title="Seu dinheiro"
        subtitle="Uma visão tranquila do mês"
        action={action("finance")}
      >
        <div className="balance-heading">
          <div>
            <small>SALDO DISPONÍVEL</small>
            <strong>{cash(f.balance)}</strong>
          </div>
          <span className="badge">{f.currency}</span>
        </div>
        <div className="money-mini">
          <div>
            <span className="mint">
              <ArrowUpRight size={14} />
              Entradas
            </span>
            <strong>{cash(f.income)}</strong>
          </div>
          <div>
            <span className="coral">
              <ArrowUpRight size={14} />
              Saídas
            </span>
            <strong>{cash(f.expense)}</strong>
          </div>
          <div>
            <span>Economia</span>
            <strong>{cash(f.savings)}</strong>
          </div>
        </div>
        <Chart
          points={f.forecast.points}
          cur={f.currency}
          hidden={data.preferences.financeHidden === "yes"}
        />
        <div className="panel-bottom">
          <span>
            Projeção em 30 dias <strong>{cash(f.forecast.balance)}</strong>
          </span>
          <button onClick={() => create("expense")}>
            <Plus size={14} />
            Registrar gasto
          </button>
        </div>
      </Panel>
    );
  if (id === "tasks") {
    const tasks = items
      .filter((i) => i.type === "task" && !i.done)
      .sort((a, b) => a.date.localeCompare(b.date));
    return (
      <Panel
        title="Próximas tarefas"
        subtitle="Um passo de cada vez"
        action={action("tasks")}
      >
        {tasks.slice(0, 5).map((i) => (
          <RecordRow key={i.id} item={i} open={open} complete={complete} />
        ))}
        {!tasks.length && (
          <Empty
            title="Tire uma ideia do papel"
            detail="Sua próxima tarefa pode começar aqui."
            onAdd={() => create("task")}
          />
        )}
        <button className="panel-add" onClick={() => create("task")}>
          <Plus size={15} />
          Adicionar tarefa
        </button>
      </Panel>
    );
  }
  if (id === "weather") return <Weather data={data} />;
  if (id === "calendar") {
    const events = items
      .filter((i) => i.type === "event" && i.date >= today())
      .sort((a, b) =>
        (a.date + (a.fields.time || "")).localeCompare(
          b.date + (b.fields.time || ""),
        ),
      );
    return (
      <Panel
        title="Sua agenda"
        subtitle="O que vem pela frente"
        action={action("calendar")}
      >
        {events.slice(0, 4).map((i) => (
          <div className="agenda-row" key={i.id}>
            <div className="date-tile">
              <strong>{Number(i.date.slice(8))}</strong>
              <small>
                {new Date(i.date + "T12:00:00")
                  .toLocaleDateString("pt-BR", { month: "short" })
                  .replace(".", "")}
              </small>
            </div>
            <button onClick={() => open(i)}>
              <strong>{i.title}</strong>
              <small>
                {i.fields.time || "Dia todo"}
                {i.fields.place ? " · " + i.fields.place : ""}
              </small>
            </button>
          </div>
        ))}
        {!events.length && (
          <Empty
            title="Seu tempo em perspectiva"
            detail="Planeje um compromisso para vê-lo aqui."
            onAdd={() => create("event")}
          />
        )}
      </Panel>
    );
  }
  if (id === "habits") {
    const habits = items.filter((i) => i.type === "habit");
    return (
      <Panel
        title="Pequenos hábitos"
        subtitle="Constância vale mais que pressa"
        action={action("habits")}
      >
        {habits.slice(0, 4).map((h) => {
          const stats = habitStats(items, h.id);
          return (
            <div className="habit-mini" key={h.id}>
              <button onClick={() => go("habits")}>
                <strong>{h.title}</strong>
                <small>{stats.current} dias de sequência</small>
              </button>
              <div>
                {Array.from({ length: 7 }, (_, n) =>
                  addDays(today(), n - 6),
                ).map((d) => (
                  <span
                    key={d}
                    className={stats.dates.includes(d) ? "filled" : ""}
                    title={dateLabel(d)}
                  />
                ))}
              </div>
            </div>
          );
        })}
        {!habits.length && (
          <Empty
            title="Comece com algo pequeno"
            onAdd={() => create("habit")}
          />
        )}
      </Panel>
    );
  }
  if (id === "notes") {
    const notes = items
      .filter((i) => i.type === "note")
      .sort(
        (a, b) =>
          Number(b.favorite) - Number(a.favorite) || b.createdAt - a.createdAt,
      );
    return (
      <Panel
        title="Ideias por perto"
        subtitle="Suas notas recentes"
        action={action("notes")}
      >
        <div className="note-cards">
          {notes.slice(0, 3).map((n) => (
            <button
              className="note-card"
              key={n.id}
              onClick={() => go("notes")}
            >
              <FileText size={19} />
              <h3>{n.title}</h3>
              <p>
                {n.notes.replace(/[#*_`]/g, "").slice(0, 110) ||
                  "Uma página para suas ideias."}
              </p>
              <small>
                {dateLabel(n.date)}
                {n.fields.folder ? " · " + n.fields.folder : ""}
              </small>
            </button>
          ))}
        </div>
        {!notes.length && (
          <Empty
            title="Guarde uma ideia"
            detail="Um pensamento, uma lista ou seu próximo plano."
            onAdd={() => create("note")}
          />
        )}
      </Panel>
    );
  }
  if (id === "focus") return <CompactFocus data={data} />;
  if (id === "spending")
    return (
      <Panel title="Gastos de hoje">
        <strong className="daily-spending">
          {cash(
            items
              .filter(
                (i) =>
                  i.type === "expense" &&
                  i.date === today() &&
                  ["paid", "Pago", ""].includes(i.fields.status || ""),
              )
              .reduce((total, i) => total + amount(i), 0),
          )}
        </strong>
      </Panel>
    );
  if (id === "shortcuts")
    return (
      <Panel title="Por perto">
        <div className="capture-options">
          <Button onClick={() => void api("openWindow", "quick")}>
            Capturar
          </Button>
          <Button onClick={() => void api("openWindow", "widget-focus")}>
            Foco
          </Button>
          <Button onClick={() => go("day")}>Meu dia</Button>
        </div>
      </Panel>
    );
  if (id === "clock") return <Clock />;
  if (id === "insights")
    return (
      <Panel title="Perspectivas do seu mês">
        {f.insights.map((s, index) => (
          <p className="insight" key={index}>
            <span>{index + 1}</span>
            {data.preferences.financeHidden === "yes"
              ? "Exiba os valores para ler o insight."
              : s}
          </p>
        ))}
        {!f.insights.length && (
          <Empty title="Seu histórico conta uma história" />
        )}
      </Panel>
    );
  if (id === "goals")
    return (
      <Panel title="Seus próximos objetivos" action={action("goals")}>
        {items
          .filter((i) => ["goal", "savings_goal", "project"].includes(i.type))
          .slice(0, 4)
          .map((i) => (
            <RecordRow key={i.id} item={i} open={open} />
          ))}
        {!items.some((i) => i.type === "goal") && (
          <button className="panel-add" onClick={() => create("goal")}>
            <Plus size={15} />
            Adicionar meta
          </button>
        )}
      </Panel>
    );
  return (
    <Panel title="Capture em segundos">
      <div className="capture-options">
        {[
          ["task", "Tarefa", CheckSquare],
          ["expense", "Gasto", Wallet],
          ["income", "Entrada", ArrowUpRight],
          ["note", "Nota", FileText],
          ["event", "Evento", CalendarDays],
        ].map(([type, title, Icon]: any) => (
          <Button key={type} onClick={() => create(type)}>
            <Icon size={17} />
            {title}
          </Button>
        ))}
      </div>
    </Panel>
  );
}
function Clock() {
  const [now, setNow] = useState(new Date());
  useEffect(() => {
    const timer = setInterval(() => {
      if (!document.hidden) setNow(new Date());
    }, 30000);
    return () => clearInterval(timer);
  }, []);
  return (
    <Panel title="Seu tempo">
      <div className="clock-display">
        {now.toLocaleTimeString("pt-BR", {
          hour: "2-digit",
          minute: "2-digit",
        })}
      </div>
      <p className="muted">
        {now.toLocaleDateString("pt-BR", {
          weekday: "long",
          day: "numeric",
          month: "long",
        })}
      </p>
    </Panel>
  );
}
function FloatingModule({
  id,
  data,
  cash,
  go,
  complete,
}: {
  id: string;
  data: Snapshot;
  cash: (n: number) => string;
  go: (page: string) => void;
  complete: (i: Item) => void;
}) {
  const [now, setNow] = useState(new Date());
  useEffect(() => {
    if (id !== "clock") return;
    const timer = setInterval(() => {
      if (!document.hidden) setNow(new Date());
    }, 30000);
    return () => clearInterval(timer);
  }, [id]);
  const heading = (label: string, page?: string) => (
    <header>
      <span>{label}</span>
      {page && (
        <button aria-label={"Abrir " + label} onClick={() => go(page)}>
          <ArrowUpRight size={14} />
        </button>
      )}
    </header>
  );
  if (id === "focus")
    return (
      <section className="floating-tile tile-focus">
        <CompactFocus data={data} />
      </section>
    );
  if (id === "clock")
    return (
      <section className="floating-tile tile-clock glass-drag">
        <strong>
          {now.toLocaleTimeString("pt-BR", {
            hour: "2-digit",
            minute: "2-digit",
          })}
        </strong>
        <span>
          {now.toLocaleDateString("pt-BR", {
            weekday: "long",
            day: "numeric",
            month: "long",
          })}
        </span>
      </section>
    );
  if (id === "finance" || id === "spending")
    return (
      <section className="floating-tile">
        {heading(
          id === "finance" ? "Saldo disponível" : "Gastos de hoje",
          "finance",
        )}
        <strong className="tile-value">
          {cash(
            id === "finance"
              ? data.finance.balance
              : reportRows(data.items, {
                  from: today(),
                  to: today(),
                  currency: data.finance.currency,
                }).expense,
          )}
        </strong>
        <small>
          {data.finance.currency} ·{" "}
          {id === "finance"
            ? "Suas contas, em equilíbrio"
            : "Gastos reconhecidos hoje"}
        </small>
      </section>
    );
  if (["bills", "inbox", "progress", "recent-spending"].includes(id)) {
    const summary = dailySummary(data.items, today(), data.finance.currency);
    const list =
      id === "bills"
        ? summary.bills
        : id === "inbox"
          ? summary.inbox
          : id === "recent-spending"
            ? data.items
                .filter(
                  (i) =>
                    i.type === "expense" &&
                    i.fields.paymentType !== "card_payment",
                )
                .sort((a, b) => b.createdAt - a.createdAt)
            : data.items.filter(
                (i) =>
                  i.type === "task" && i.date.startsWith(today().slice(0, 7)),
              );
    const label =
      id === "bills"
        ? "Contas próximas"
        : id === "inbox"
          ? "Caixa de entrada"
          : id === "progress"
            ? "Progresso do mês"
            : "Gastos recentes";
    return (
      <section className="floating-tile">
        {heading(
          label,
          id === "inbox" ? "inbox" : id === "progress" ? "review" : "finance",
        )}
        {id === "progress" ? (
          <strong className="tile-value">
            {list.filter((i) => i.done).length}/{list.length}
          </strong>
        ) : (
          list.slice(0, 3).map((i) => (
            <p className="tile-text" key={i.id}>
              {i.title}
            </p>
          ))
        )}
        {!list.length && <p>Nenhuma pendência por aqui.</p>}
        <small>{list.length} registros</small>
      </section>
    );
  }
  if (id === "tasks") {
    const list = data.items
      .filter((i) => i.type === "task" && !i.done)
      .sort((a, b) => a.date.localeCompare(b.date));
    return (
      <section className="floating-tile">
        {heading("Próximas tarefas", "tasks")}
        {list.slice(0, 3).map((i) => (
          <div className="tile-task" key={i.id}>
            <button
              aria-label={"Concluir " + i.title}
              onClick={() => complete(i)}
            >
              <Check size={13} />
            </button>
            <span>{i.title}</span>
          </div>
        ))}
        {!list.length && <p>Seu próximo passo cabe aqui.</p>}
        <small>{list.length} tarefas abertas</small>
      </section>
    );
  }
  if (id === "calendar") {
    const event = data.items
      .filter((i) => i.type === "event" && i.date >= today())
      .sort((a, b) =>
        (a.date + (a.fields.time || "")).localeCompare(
          b.date + (b.fields.time || ""),
        ),
      )[0];
    return (
      <section className="floating-tile">
        {heading("Próximo compromisso", "calendar")}
        <strong className="tile-text">
          {event?.title || "Seu tempo está livre"}
        </strong>
        {event && (
          <small>
            {dateLabel(event.date)} · {event.fields.time || "Dia todo"}
          </small>
        )}
      </section>
    );
  }
  if (id === "weather") {
    const city = data.items.find(
      (i) => i.type === "city" && i.id === data.preferences.weatherCity,
    );
    let cache: any = {};
    try {
      cache = JSON.parse(city?.fields.cache || "{}");
    } catch {}
    const manual = data.preferences.weatherMode === "Manual";
    const value = manual
      ? (data.preferences.weatherTemperature || "24") + "°"
      : cache.current
        ? Math.round(cache.current.temperature_2m) + "°"
        : "—";
    return (
      <section className="floating-tile tile-weather">
        {heading(city?.title || "Seu horizonte")}
        <div>
          <Sun size={30} />
          <strong className="tile-value">{value}</strong>
          <span>
            {manual
              ? data.preferences.weatherCondition || "Meu clima"
              : cache.current
                ? "Previsão salva"
                : "Escolha uma cidade"}
          </span>
          <button
            aria-label="Abrir painel de clima"
            onClick={() => void api("openWindow", "widget-weather")}
          >
            <ArrowUpRight size={14} />
          </button>
        </div>
      </section>
    );
  }
  if (id === "habits") {
    const habits = data.items.filter((i) => i.type === "habit");
    const done = habits.filter((h) =>
      habitStats(data.items, h.id).dates.includes(today()),
    ).length;
    return (
      <section className="floating-tile">
        {heading("Pequenos hábitos", "habits")}
        <strong className="tile-value">
          {done}
          <span> / {habits.length}</span>
        </strong>
        <small>Concluídos hoje · um dia de cada vez</small>
      </section>
    );
  }
  if (id === "notes") {
    const note = data.items
      .filter((i) => i.type === "note")
      .sort((a, b) => b.createdAt - a.createdAt)[0];
    return (
      <section className="floating-tile">
        {heading("Ideias por perto", "notes")}
        <strong className="tile-text">
          {note?.title || "Guarde sua próxima ideia"}
        </strong>
      </section>
    );
  }
  if (id === "goals")
    return (
      <section className="floating-tile">
        {heading("Seus próximos objetivos", "goals")}
        <strong className="tile-text">
          {data.items.find(
            (i) => ["goal", "savings_goal"].includes(i.type) && !i.done,
          )?.title || "Dê nome ao seu próximo passo"}
        </strong>
      </section>
    );
  if (id === "insights")
    return (
      <section className="floating-tile">
        {heading("Uma perspectiva", "finance")}
        <p>
          {data.preferences.financeHidden === "yes" ||
          data.preferences.financeHideValues === "yes"
            ? "Valores financeiros ocultos."
            : data.finance.insights[0] || "Seu histórico conta uma história."}
        </p>
      </section>
    );
  return (
    <section className="floating-tile tile-shortcuts">
      {heading("Por perto")}
      <div>
        <Button onClick={() => void api("openWindow", "quick")}>
          <Plus size={14} />
          Capturar
        </Button>
        <Button onClick={() => go("day")}>Meu dia</Button>
      </div>
    </section>
  );
}
function MicroWidget({
  role,
  data,
  cash,
}: {
  role: string;
  data: Snapshot;
  cash: (n: number) => string;
}) {
  const [now, setNow] = useState(new Date());
  useEffect(() => {
    if (role !== "widget-clock") return;
    const t = setInterval(() => {
      if (!document.hidden) setNow(new Date());
    }, 30000);
    return () => clearInterval(t);
  }, [role]);
  let value = "";
  let label = "";
  if (role === "widget-finance") {
    value = cash(data.finance.balance);
    label = "Disponível";
  }
  if (role === "widget-clock") {
    value = now.toLocaleTimeString("pt-BR", {
      hour: "2-digit",
      minute: "2-digit",
    });
    label = now.toLocaleDateString("pt-BR", { day: "numeric", month: "short" });
  }
  if (role === "widget-weather") {
    const city = data.items.find(
      (i) => i.type === "city" && i.id === data.preferences.weatherCity,
    );
    let cache: any = {};
    try {
      cache = JSON.parse(city?.fields.cache || "{}");
    } catch {}
    value =
      data.preferences.weatherMode === "Manual"
        ? (data.preferences.weatherTemperature || "24") + "°"
        : cache.current
          ? Math.round(cache.current.temperature_2m) + "°"
          : "—";
    label = city?.title || "Clima";
  }
  return (
    <div className="glass-micro-summary glass-drag">
      <strong>{value}</strong>
      <span>{label}</span>
    </div>
  );
}
function Weather({ data }: { data: Snapshot }) {
  const p = data.preferences;
  const manual = p.weatherMode === "Manual";
  const city = data.items.find(
    (i) => i.type === "city" && i.id === p.weatherCity,
  );
  let cache: any;
  try {
    cache = JSON.parse(city?.fields.cache || "{}");
  } catch {
    cache = {};
  }
  const current = cache.current;
  const condition = (code: number) =>
    code === 0
      ? "Céu limpo"
      : code <= 3
        ? "Parcialmente nublado"
        : code <= 48
          ? "Névoa"
          : code <= 67
            ? "Chuva"
            : code <= 77
              ? "Neve"
              : "Pancadas de chuva";
  const [edit, setEdit] = useState(false);
  const [query, setQuery] = useState("");
  const [cities, setCities] = useState<any[]>([]);
  return (
    <Panel
      title={manual ? "Meu clima" : city?.title || "Seu horizonte"}
      subtitle={manual ? "Clima personalizado" : "Previsão real • Open-Meteo"}
      action={
        <IconButton label="Configurar clima" onClick={() => setEdit(true)}>
          <Settings2 size={17} />
        </IconButton>
      }
    >
      <div className="weather-hero">
        <Sun className="sun-icon" size={64} />
        <div>
          <strong>
            {manual
              ? (p.weatherTemperature || "24") + "°"
              : current
                ? Math.round(current.temperature_2m) + "°"
                : "—"}
          </strong>
          <p>
            {manual
              ? p.weatherCondition || "Céu limpo"
              : current
                ? condition(current.weather_code)
                : "Escolha uma cidade"}
          </p>
        </div>
      </div>
      {cache.daily && !manual && (
        <div className="weather-days">
          {cache.daily.time.slice(0, 4).map((d: string, n: number) => (
            <div key={d}>
              <small>
                {n === 0
                  ? "Hoje"
                  : new Date(d + "T12:00:00").toLocaleDateString("pt-BR", {
                      weekday: "short",
                    })}
              </small>
              <CloudSun size={18} />
              <strong>{Math.round(cache.daily.temperature_2m_max[n])}°</strong>
              <span>{Math.round(cache.daily.temperature_2m_min[n])}°</span>
            </div>
          ))}
        </div>
      )}
      <div className="panel-bottom">
        <span>
          {manual
            ? "Você escolhe a atmosfera."
            : city?.fields.cacheAt
              ? "Previsão salva · " +
                new Date(Number(city.fields.cacheAt)).toLocaleTimeString(
                  "pt-BR",
                  { hour: "2-digit", minute: "2-digit" },
                )
              : "Sem acesso à sua localização"}
        </span>
        <button onClick={() => setEdit(true)}>
          Ajustar
          <ArrowRight size={13} />
        </button>
      </div>
      {edit && (
        <Modal title="Clima do seu jeito" onClose={() => setEdit(false)}>
          <div className="form-body">
            <Field
              label="Modo"
              value={manual ? "Manual" : "Real"}
              onChange={(v) =>
                void api("preference", { key: "weatherMode", value: v })
              }
              options={[
                { value: "Real", label: "Previsão real" },
                { value: "Manual", label: "Personalizado" },
              ]}
            />
            {manual ? (
              <>
                <Field
                  label="Temperatura • °C"
                  type="number"
                  value={p.weatherTemperature || "24"}
                  onChange={(v) =>
                    void api("preference", {
                      key: "weatherTemperature",
                      value: v,
                    })
                  }
                />
                <Field
                  label="Condição"
                  value={p.weatherCondition || "Céu limpo"}
                  onChange={(v) =>
                    void api("preference", {
                      key: "weatherCondition",
                      value: v,
                    })
                  }
                  options={[
                    "Céu limpo",
                    "Parcialmente nublado",
                    "Chuva leve",
                    "Tempestade",
                    "Neve",
                    "Névoa",
                  ].map((v) => ({ label: v, value: v }))}
                />
              </>
            ) : (
              <>
                <Field
                  label="Buscar cidade"
                  value={query}
                  onChange={setQuery}
                />
                <Button
                  onClick={() =>
                    void api("weatherSearch", query).then(setCities)
                  }
                >
                  Buscar
                </Button>
                {cities.map((c) => (
                  <button
                    className="city-result"
                    key={c.id}
                    onClick={() =>
                      void api("weatherSelect", c).then(() => setEdit(false))
                    }
                  >
                    {c.name} · {c.admin1} · {c.country}
                    <ArrowRight size={14} />
                  </button>
                ))}
                {city && (
                  <Button onClick={() => void api("weatherRefresh")}>
                    Atualizar previsão
                  </Button>
                )}
              </>
            )}
            <p className="muted">
              A consulta envia somente a cidade ou suas coordenadas manuais ao
              Open-Meteo. Não usa GPS.
            </p>
          </div>
        </Modal>
      )}
    </Panel>
  );
}
function Onboarding({ data, close }: { data: Snapshot; close: () => void }) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [profile, setProfile] = useState(
    data.desktop.onboardingProfile || "all",
  );
  const configureProfile = async () => {
    const fresh: Snapshot = await api("snapshot");
    if (fresh.desktop.welcomeSeen) return;
    const layouts: Record<string, string[]> = {
      finance: ["finance", "bills", "spending", "insights"],
      productivity: ["tasks", "calendar", "focus", "notes"],
      studies: ["tasks", "focus", "notes", "goals"],
      organization: ["calendar", "habits", "inbox", "notes"],
    };
    await api("desktop", {
      onboardingProfile: profile,
      welcomeSeen: true,
      layout:
        profile === "all"
          ? defaultLayout
          : layouts[profile].map((id) => ({
              id,
              size: id === "finance" ? "wide" : "normal",
            })),
    });
  };
  const [emailMode, setEmailMode] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function login(method: string, value?: any) {
    setBusy(true);
    setError("");
    try {
      await api(method, value);
      await configureProfile();
      close();
    } catch (e) {
      setError(cleanError(e));
    } finally {
      setBusy(false);
      setPassword("");
    }
  }
  return (
    <Modal title="Bem-vindo ao Veyra Life" onClose={close}>
      <div className="onboarding">
        <div className="brand-mark giant">v</div>
        <div className="eyebrow">UM ESPAÇO. TODOS OS SEUS DIAS.</div>
        <h2>Seu Veyra está aqui.</h2>
        <p>
          Entre com a mesma conta do celular. Seu financeiro, suas tarefas e
          suas ideias acompanham você.
        </p>
        <Field
          label="Como você pretende usar o Veyra?"
          value={profile}
          onChange={setProfile}
          options={[
            { value: "all", label: "Tudo" },
            { value: "finance", label: "Finanças" },
            { value: "productivity", label: "Produtividade" },
            { value: "studies", label: "Estudos" },
            { value: "organization", label: "Organização" },
          ]}
        />
        {data.user ? (
          <>
            <div className="account-profile">
              <strong>{data.user.name}</strong>
              <span>{data.user.email}</span>
            </div>
            <Button onClick={() => void api("logout").then(close)}>
              Sair desta conta
            </Button>
          </>
        ) : (
          <>
            <Button
              kind="primary google"
              onClick={() => void login("google")}
              disabled={busy}
            >
              <span className="google-letter">G</span>
              {busy ? "Aguardando login no navegador…" : "Continuar com Google"}
            </Button>
            <button
              className="text-button"
              onClick={() => setEmailMode(!emailMode)}
            >
              Entrar com e-mail e senha
            </button>
            {emailMode && (
              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  void login("login", { email, password });
                }}
              >
                <Field
                  label="E-mail"
                  value={email}
                  onChange={setEmail}
                  type="email"
                />
                <Field
                  label="Senha"
                  value={password}
                  onChange={setPassword}
                  type="password"
                />
                <Button type="submit" kind="primary" disabled={busy}>
                  Entrar
                </Button>
              </form>
            )}
            <Button onClick={() => void configureProfile().then(close)}>
              Continuar no espaço visitante
            </Button>
          </>
        )}
        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}
        <div className="onboarding-foot">
          <ShieldCheck size={15} />
          <span>
            Dados privados por conta. Offline quando precisar.
            <br />O login Google acontece no navegador do Windows.
          </span>
        </div>
      </div>
    </Modal>
  );
}
function Modules({
  data,
  open,
  create,
}: {
  data: Snapshot;
  open: (i: Item) => void;
  create: (s: string) => void;
}) {
  const [type, setType] = useState("");
  const [query, setQuery] = useState("");
  const [records, setRecords] = useState<{ total: number; items: Item[] }>({
    total: 0,
    items: [],
  });
  const [page, setPage] = useState(0);
  const groups = [...new Set(catalog.map((s) => s.group))];
  useEffect(() => {
    if (!type) return;
    let live = true;
    void api("search", {
      query,
      types: [type],
      offset: page * 80,
      limit: 80,
    }).then((r) => {
      if (live) setRecords(r);
    });
    return () => {
      live = false;
    };
  }, [type, query, page, data.items]);
  return (
    <>
      <div className="page-title">
        <div>
          <div className="eyebrow">MAIS DO QUE UMA ROTINA</div>
          <h1>{type ? labels[type] || type : "Seu ecossistema"}</h1>
          <p>
            {type
              ? "Os mesmos registros do celular, com espaço para organizar."
              : "Todos os módulos existentes continuam com você."}
          </p>
        </div>
        {type && (
          <div className="actions">
            <Button onClick={() => setType("")}>Todos os módulos</Button>
            <Button kind="primary" onClick={() => create(type)}>
              <Plus size={16} />
              Adicionar
            </Button>
          </div>
        )}
      </div>
      {type ? (
        <Panel>
          <SearchBox
            value={query}
            onChange={(v) => {
              setQuery(v);
              setPage(0);
            }}
          />
          {records.items.map((i) => (
            <RecordRow key={i.id} item={i} open={open} />
          ))}
          {!records.items.length && (
            <Empty
              title="Um novo espaço para organizar"
              onAdd={() => create(type)}
            />
          )}
          <div className="pagination">
            <Button disabled={!page} onClick={() => setPage((v) => v - 1)}>
              Anterior
            </Button>
            <span>{records.total} registros</span>
            <Button
              disabled={(page + 1) * 80 >= records.total}
              onClick={() => setPage((v) => v + 1)}
            >
              Próxima
            </Button>
          </div>
        </Panel>
      ) : (
        groups.map((group) => (
          <section className="module-group" key={group}>
            <h2>{group}</h2>
            <div className="module-grid">
              {catalog
                .filter((s) => s.group === group)
                .map((s) => (
                  <button
                    key={s.type}
                    onClick={() => {
                      setType(s.type);
                      setPage(0);
                    }}
                  >
                    <span className="module-icon">
                      <Grid2X2 size={20} />
                    </span>
                    <strong>{s.label}</strong>
                    <small>
                      {data.items.filter((i) => i.type === s.type).length}{" "}
                      registros
                    </small>
                    <ArrowUpRight size={16} />
                  </button>
                ))}
            </div>
          </section>
        ))
      )}
    </>
  );
}
function CompactFocus({
  data,
  stopwatch = false,
}: {
  data: Snapshot;
  stopwatch?: boolean;
}) {
  const [tick, setTick] = useState(0);
  useEffect(() => {
    if (!data.desktop.focus?.active || data.desktop.focus?.paused) return;
    const timer = setInterval(() => {
      if (!document.hidden) setTick((t) => t + 1);
    }, 1000);
    return () => clearInterval(timer);
  }, [data.desktop.focus?.active, data.desktop.focus?.paused]);
  const f = data.desktop.focus;
  const remaining = f?.active
    ? f.paused
      ? f.remaining
      : Math.max(0, Math.ceil((f.deadline - Date.now()) / 1000))
    : 25 * 60;
  const left =
    f?.active && f.mode === "Cronômetro" ? f.seconds - remaining : remaining;
  return (
    <div className="compact-focus" data-tick={tick}>
      <span className="eyebrow">{stopwatch ? "CRONÔMETRO" : "FOCO"}</span>
      <div className="focus-clock">
        {String(Math.floor(left / 60)).padStart(2, "0")}:
        {String(left % 60).padStart(2, "0")}
      </div>
      <p>{f?.active ? f.title : "Um tempo para o que importa."}</p>
      {f?.active ? (
        <div className="actions center">
          <Button
            aria-label={f.paused ? "Retomar foco" : "Pausar foco"}
            onClick={() => void api("focusPause")}
          >
            {f.paused ? <Play size={16} /> : <Pause size={16} />}
          </Button>
          <Button aria-label="Parar foco" onClick={() => void api("focusStop")}>
            <Square size={15} />
          </Button>
          <Button
            aria-label="Concluir foco"
            onClick={() => void api("focusComplete")}
          >
            <Check size={15} />
          </Button>
        </div>
      ) : (
        <Button
          kind="primary"
          onClick={() =>
            void api("focusStart", {
              minutes: 25,
              title: stopwatch ? "Meu cronômetro" : "Tempo de foco",
              mode: stopwatch ? "Cronômetro" : "Pomodoro",
            })
          }
        >
          <Play size={15} />
          Começar
        </Button>
      )}
    </div>
  );
}
createRoot(document.getElementById("root")!).render(
  <Boundary>
    <App />
  </Boundary>,
);
