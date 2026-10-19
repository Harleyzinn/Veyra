import React, { useState, useEffect, useRef, useMemo } from "react";
import { createRoot } from "react-dom/client";
import {
  Wallet,
  House,
  ArrowDownLeft,
  ArrowUpRight,
  ArrowLeftRight,
  Plus,
  Search,
  CalendarDays,
  CreditCard,
  Target,
  ChartNoAxesCombined,
  Settings2,
  MoreHorizontal,
  X,
  ChevronRight,
  ChevronLeft,
  Check,
  Trash2,
  Download,
  Upload,
  Palette,
  Repeat,
  ShieldCheck,
  WifiOff,
  Info,
  Star,
  Eye,
  EyeOff,
  Menu,
  Sun,
  Moon,
  ArrowRight,
  SlidersHorizontal,
  CheckCircle2,
  Landmark,
  Plane,
  Shield,
} from "lucide-react";
import { Item, money, parseMinor } from "../desktop/shared/model";
import { currentBalance, financialTypes } from "../desktop/shared/finance";
import {
  Data,
  emptyData,
  amount,
  createItem,
  today,
  addMonths,
  addDays,
  daysBetween,
  decimal,
  active,
  settled,
  due,
  booked,
  normalize,
  installmentItems,
  saveChanges,
  transactions,
  allInvoices,
  safeSpending,
  monthly,
  confirmTransaction,
  payInvoice,
  editSchedule,
  importPreview,
  csv,
  categories,
  categoryNames,
  automaticChanges,
  csvPreview,
  togglePause,
  validatePreferences,
  editExpenseRule,
} from "./domain";
import { themes, resolvedTheme, readableAccent } from "./themes";
import { load, persist, download, nativeCall } from "./storage";
import { scheduleDates } from "./calendar";
import { calendar } from "./domain";
import "./style.css";

const routes = [
  { id: "home", name: "Início", icon: House, group: "Seu dinheiro" },
  {
    id: "transactions",
    name: "Lançamentos",
    icon: ArrowLeftRight,
    group: "Seu dinheiro",
  },
  {
    id: "calendar",
    name: "Contas e calendário",
    icon: CalendarDays,
    group: "Seu dinheiro",
  },
  { id: "cards", name: "Cartões", icon: CreditCard, group: "Seu dinheiro" },
  {
    id: "accounts",
    name: "Contas e carteiras",
    icon: Wallet,
    group: "Seu dinheiro",
  },
  {
    id: "planning",
    name: "Planejamento e metas",
    icon: Target,
    group: "Seus planos",
  },
  {
    id: "reports",
    name: "Relatórios",
    icon: ChartNoAxesCombined,
    group: "Seus planos",
  },
  {
    id: "recurring",
    name: "Receitas e recorrências",
    icon: Repeat,
    group: "Seus planos",
  },
  {
    id: "subscriptions",
    name: "Assinaturas",
    icon: Repeat,
    group: "Seus planos",
  },
  { id: "appearance", name: "Aparência", icon: Palette, group: "Seu espaço" },
  {
    id: "settings",
    name: "Configurações",
    icon: Settings2,
    group: "Seu espaço",
  },
];
const dateLabel = (s: string) =>
  s
    ? new Date(s + "T12:00:00").toLocaleDateString("pt-BR", {
        day: "2-digit",
        month: "short",
      })
    : "Sem data";
const fullDate = (s: string) =>
  s ? new Date(s + "T12:00:00").toLocaleDateString("pt-BR") : "";
const typeLabels: Record<string, string> = {
  income: "Receita",
  expense: "Gasto",
  transfer: "Transferência",
  account: "Conta",
  card: "Cartão",
  salary_schedule: "Receita recorrente",
  recurring_rule: "Conta recorrente",
  subscription: "Assinatura",
  savings_goal: "Meta",
  budget: "Orçamento",
  financial_category: "Categoria",
};
const initials = (s: string) => s.slice(0, 2).toUpperCase();
function recordIcon(icon: string) {
  const Icon =
    (
      {
        bank: Landmark,
        plane: Plane,
        shield: Shield,
        target: Target,
        wallet: Wallet,
      } as Record<string, typeof Wallet>
    )[icon] || Wallet;
  return <Icon size={18} />;
}

function Dialog({
  title,
  close,
  children,
}: {
  title: string;
  close: () => void;
  children: React.ReactNode;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const old = document.activeElement as HTMLElement;
    ref.current?.showModal();
    return () => old?.focus();
  }, []);
  return (
    <dialog
      ref={ref}
      onCancel={(e) => {
        e.preventDefault();
        close();
      }}
      onClick={(e) => {
        if (e.target === ref.current) close();
      }}
    >
      <div className="dialog-head">
        <h2>{title}</h2>
        <button aria-label="Fechar" className="icon" onClick={close}>
          <X />
        </button>
      </div>
      {children}
    </dialog>
  );
}
function App() {
  const [data, setData] = useState<Data | null>(null),
    [fatal, setFatal] = useState(""),
    [route, setRoute] = useState(location.hash.slice(1) || "home");
  const [editor, setEditor] = useState<Item | null>(null),
    [more, setMore] = useState(false),
    [command, setCommand] = useState(false),
    [query, setQuery] = useState("");
  const [message, setMessage] = useState(""),
    [busy, setBusy] = useState(false),
    [month, setMonth] = useState(today().slice(0, 7)),
    [accountFilter, setAccountFilter] = useState("");
  const [categoryFilter, setCategoryFilter] = useState(""),
    [statusFilter, setStatusFilter] = useState(""),
    [min, setMin] = useState(""),
    [max, setMax] = useState("");
  const [calendarMode, setCalendarMode] = useState("list"),
    [chart, setChart] = useState("bars"),
    [preview, setPreview] = useState<ReturnType<typeof importPreview> | null>(
      null,
    );
  const [confirm, setConfirm] = useState<{
      text: string;
      run: () => Promise<void>;
    } | null>(null),
    [undo, setUndo] = useState<Item | null>(null),
    [onboarding, setOnboarding] = useState(false);
  const [themePreview, setThemePreview] = useState(""),
    [themeFilter, setThemeFilter] = useState("all"),
    [systemDark, setSystemDark] = useState(
      matchMedia("(prefers-color-scheme: dark)").matches,
    );
  const [hide, setHide] = useState(false),
    [offline, setOffline] = useState(!navigator.onLine),
    [paletteQuery, setPaletteQuery] = useState("");
  const fileInput = useRef<HTMLInputElement>(null),
    saveLock = useRef(false),
    dataRef = useRef<Data | null>(null);
  useEffect(() => {
    let alive = true;
    load()
      .then((d) => {
        if (alive) {
          setHide(d.preferences.hideValues === "yes");
          setData(d);
          dataRef.current = d;
          setOnboarding(!d.preferences.onboarded && d.items.length === 0);
        }
      })
      .catch((e) => setFatal(e.message));
    return () => {
      alive = false;
    };
  }, []);
  useEffect(() => {
    const onHash = () => {
      setRoute(location.hash.slice(1) || "home");
      setMore(false);
    };
    addEventListener("hashchange", onHash);
    const key = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key === "k") {
        e.preventDefault();
        setCommand((v) => !v);
      }
    };
    addEventListener("keydown", key);
    const net = () => setOffline(!navigator.onLine);
    addEventListener("online", net);
    addEventListener("offline", net);
    const m = matchMedia("(prefers-color-scheme: dark)"),
      change = () => setSystemDark(m.matches);
    m.addEventListener("change", change);
    return () => {
      removeEventListener("hashchange", onHash);
      removeEventListener("keydown", key);
      removeEventListener("online", net);
      removeEventListener("offline", net);
      m.removeEventListener("change", change);
    };
  }, []);
  useEffect(() => {
    if (!message) return;
    const t = setTimeout(() => setMessage(""), 6000);
    return () => clearTimeout(t);
  }, [message]);
  useEffect(() => {
    if (!window.VeyraFinance && "serviceWorker" in navigator)
      navigator.serviceWorker
        .register("./sw.js")
        .catch(() =>
          setMessage(
            "O cache offline não pôde ser ativado. Seus registros locais continuam salvos.",
          ),
        );
  }, []);
  useEffect(() => {
    if (!data || saveLock.current) return;
    try {
      const next = automaticChanges(data);
      if (next !== data) commit(next).catch((e) => setMessage(e.message));
    } catch (e) {
      setMessage((e as Error).message);
    }
  }, [data]);
  useEffect(() => {
    window.veyraBack = () => {
      if (confirm) {
        setConfirm(null);
        return true;
      }
      if (editor) {
        setEditor(null);
        return true;
      }
      if (more) {
        setMore(false);
        return true;
      }
      if (command) {
        setCommand(false);
        return true;
      }
      if (preview) {
        setPreview(null);
        return true;
      }
      if (onboarding) {
        setOnboarding(false);
        return true;
      }
      if (route !== "home") {
        if (history.length > 1) history.back();
        else location.hash = "home";
        return true;
      }
      return false;
    };
    return () => {
      delete window.veyraBack;
    };
  }, [confirm, editor, more, command, preview, onboarding, route]);
  const prefs = data?.preferences || {};
  const theme = resolvedTheme(
    { ...prefs, theme: themePreview || prefs.theme },
    systemDark,
  );
  useEffect(() => {
    const root = document.documentElement;
    for (const [k, v] of Object.entries(theme))
      if (typeof v === "string" && !["id", "name"].includes(k))
        root.style.setProperty("--" + k, v);
    root.style.colorScheme = theme.dark ? "dark" : "light";
    root.dataset.density = prefs.density || "comfortable";
    root.dataset.textSize = prefs.textSize || "14";
    root.dataset.motion = prefs.motion || "normal";
    root.style.setProperty("--font-size", `${prefs.textSize || 14}px`);
    root.style.setProperty(
      "--app-font",
      prefs.font === "system"
        ? "system-ui, sans-serif"
        : "Manrope, system-ui, sans-serif",
    );
    if (prefs.accent)
      root.style.setProperty("--accent", readableAccent(theme, prefs.accent));
    document
      .querySelector('meta[name="theme-color"]')
      ?.setAttribute("content", theme.bg);
  }, [
    theme,
    prefs.density,
    prefs.motion,
    prefs.textSize,
    prefs.font,
    prefs.accent,
  ]);
  async function commit(next: Data) {
    validatePreferences(next.preferences);
    if (saveLock.current) throw Error("Aguarde o salvamento anterior.");
    saveLock.current = true;
    setBusy(true);
    try {
      const saved = await persist(dataRef.current!, next);
      dataRef.current = saved;
      setData(saved);
    } finally {
      saveLock.current = false;
      setBusy(false);
    }
  }
  async function act(fn: () => Promise<void>) {
    try {
      await fn();
    } catch (e) {
      setMessage((e as Error).message);
    }
  }
  async function pref(key: string, value: string) {
    await commit({
      ...dataRef.current!,
      preferences: { ...dataRef.current!.preferences, [key]: value },
    });
  }
  const navigate = (id: string) => {
    location.hash = id;
    setMore(false);
    setCommand(false);
    setQuery("");
  };
  const newItem = (type: string) =>
    setEditor(
      createItem(type, {
        fields: {
          currency: "BRL",
          category: type === "income" ? "Salário" : "Outros",
          account:
            data?.items.find(
              (i) =>
                i.type === "account" &&
                active(i) &&
                i.fields.currency !== "USD",
            )?.id || "",
          status: type === "income" ? "received" : "paid",
          financialVersion: "3",
        },
      }),
    );
  if (fatal)
    return (
      <main className="fatal">
        <ShieldCheck size={40} />
        <h1>Seus dados merecem cuidado</h1>
        <p>{fatal}</p>
        <button onClick={() => location.reload()}>Tentar novamente</button>
      </main>
    );
  if (!data)
    return (
      <main className="fatal">
        <span className="brand-mark">V</span>
        <h1>Abrindo seu espaço</h1>
        <p>Preparando suas finanças…</p>
      </main>
    );
  const records = data.items.filter(active),
    accounts = records.filter(
      (i) => i.type === "account" && (i.fields.currency || "BRL") === "BRL",
    ),
    cards = records.filter(
      (i) => i.type === "card" && (i.fields.currency || "BRL") === "BRL",
    );
  const goals = records.filter(
      (i) =>
        i.type === "savings_goal" && (i.fields.currency || "BRL") === "BRL",
    ),
    budgets = records.filter(
      (i) => i.type === "budget" && (i.fields.currency || "BRL") === "BRL",
    ),
    simple = prefs.mode !== "complete";
  const safe = safeSpending(data),
    stats = monthly(data, month, accountFilter),
    previous = monthly(
      data,
      addMonths(month + "-01", -1, 1).slice(0, 7),
      accountFilter,
    );
  const end = addDays(addMonths(month + "-01", 1, 1), -1),
    allTx = transactions(
      data,
      addMonths(month + "-01", -120),
      addMonths(end, 12),
    ).filter((i) => (i.fields.currency || "BRL") === "BRL");
  const tx = allTx
    .filter(
      (i) =>
        i.date.startsWith(month) &&
        (!accountFilter || i.fields.account === accountFilter) &&
        (!categoryFilter || i.fields.category === categoryFilter) &&
        (!statusFilter ||
          (statusFilter === "paid"
            ? settled(i)
            : statusFilter === "overdue"
              ? !settled(i) && due(i) < today()
              : !settled(i))) &&
        (!query ||
          [i.title, i.notes, i.tags, i.fields.category]
            .join(" ")
            .toLocaleLowerCase()
            .includes(query.toLocaleLowerCase())) &&
        (!min || amount(i) >= parseMinor(min.replace(/[,.]$/, ""))) &&
        (!max || amount(i) <= parseMinor(max.replace(/[,.]$/, ""))),
    )
    .sort((a, b) => b.date.localeCompare(a.date));
  const coming = allTx
    .filter(
      (i) =>
        !settled(i) &&
        !i.fields.card &&
        i.type !== "transfer" &&
        due(i) <= addDays(today(), 30),
    )
    .sort((a, b) => due(a).localeCompare(due(b)));
  const amountText = (n: number) => (hide ? "R$ ••••" : money(n));
  const current = routes.find((r) => r.id === route) || routes[0];
  const goalAgenda = goals
    .flatMap((g) => [
      ...(g.fields.targetDate
        ? [
            {
              id: g.id + ":deadline",
              title: "Prazo · " + g.title,
              date: g.fields.targetDate,
              value: amount(g),
              item: g,
            },
          ]
        : []),
      ...(amount(g, "monthlyContribution") > 0
        ? [
            {
              id: g.id + ":contribution",
              title: "Aporte planejado · " + g.title,
              date: addMonths(
                month + "-01",
                0,
                Number(g.fields.contributionDay || g.date.slice(8) || 1),
              ),
              value: amount(g, "monthlyContribution"),
              item: g,
            },
          ]
        : []),
    ])
    .filter((e) => e.date.startsWith(month));
  const calendarInvoices = allInvoices(
    data,
    addMonths(today(), -120),
    addMonths(end, 1),
  ).filter((i) => i.remaining > 0 && i.due.startsWith(month));
  const categoriesAll = categoryNames(records);
  const remove = (item: Item) =>
    setConfirm({
      text: `Mover “${item.title}” para a lixeira? O registro poderá ser restaurado nas configurações.`,
      run: async () => {
        await commit(
          saveChanges(dataRef.current!, [{ ...item, deletedAt: Date.now() }]),
        );
        setUndo(item);
        setMessage("Registro movido para a lixeira.");
        setConfirm(null);
      },
    });
  const settle = (row: Item) => {
    setEditor({ ...row, fields: { ...row.fields, confirming: "yes" } });
  };
  function Row({ item }: { item: Item }) {
    return (
      <div className="record">
        <span
          className={
            "record-icon " + (item.type === "income" ? "positive" : "")
          }
        >
          {item.type === "income" ? (
            <ArrowDownLeft />
          ) : item.type === "transfer" ? (
            <ArrowLeftRight />
          ) : (
            <ArrowUpRight />
          )}
        </span>
        <button className="record-main" onClick={() => setEditor(item)}>
          <strong>{item.title}</strong>
          <small>
            {item.fields.category || typeLabels[item.type] || item.type} ·{" "}
            {fullDate(due(item))}
            {!settled(item) && due(item) < today()
              ? " · Atrasado"
              : !settled(item)
                ? " · Previsto"
                : " · Confirmado"}
            {item.fields.installmentCount
              ? ` · ${item.fields.installmentIndex}/${item.fields.installmentCount}`
              : ""}
          </small>
        </button>
        <strong className={item.type === "income" ? "positive" : ""}>
          {item.type === "income" ? "+" : ""}
          {amountText(amount(item))}
        </strong>
        {!settled(item) && !item.fields.card && (
          <button
            className="icon"
            title={
              item.type === "income" ? "Confirmar recebimento" : "Pagar conta"
            }
            aria-label={`Confirmar ${item.title}`}
            onClick={() => settle(item)}
          >
            <Check />
          </button>
        )}
      </div>
    );
  }
  function Empty({
    title = "Tudo pronto para começar",
    body = "Adicione seus primeiros registros e acompanhe seu dinheiro com clareza.",
    action = "Adicionar gasto",
    run = () => newItem("expense"),
  }: {
    title?: string;
    body?: string;
    action?: string;
    run?: () => void;
  }) {
    return (
      <div className="empty">
        <Wallet />
        <h3>{title}</h3>
        <p>{body}</p>
        <button className="secondary" onClick={run}>
          {action}
          <ArrowRight size={16} />
        </button>
      </div>
    );
  }
  function GoalCard({ item }: { item: Item }) {
    const target = amount(item),
      saved = amount(item, "saved"),
      remain = Math.max(0, target - saved),
      months = item.fields.targetDate
        ? Math.max(
            1,
            Math.ceil(daysBetween(today(), item.fields.targetDate) / 30),
          )
        : 0;
    return (
      <article className="panel goal">
        <div className="section-head">
          <span className="badge">
            {recordIcon(item.fields.icon || "target")}{" "}
            {item.fields.priority || "Sua meta"}
          </span>
          <button
            className="icon"
            aria-label={`Editar ${item.title}`}
            onClick={() => setEditor(item)}
          >
            <MoreHorizontal />
          </button>
        </div>
        <h3>{item.title}</h3>
        <p>
          <strong>{amountText(saved)}</strong>{" "}
          <span className="muted">de {amountText(target)}</span>
        </p>
        <progress max={target || 1} value={saved} />
        <small>
          {Math.min(100, Math.round((saved / (target || 1)) * 100))}%
          conquistado · Faltam {amountText(remain)}
        </small>
        {months > 0 && (
          <small>
            Para o prazo: {amountText(Math.ceil(remain / months))}/mês
          </small>
        )}
      </article>
    );
  }
  function MonthPicker() {
    return (
      <div className="month-picker">
        <button
          className="icon"
          aria-label="Mês anterior"
          onClick={() => setMonth(addMonths(month + "-01", -1, 1).slice(0, 7))}
        >
          <ChevronLeft size={18} />
        </button>
        <input
          aria-label="Período"
          type="month"
          value={month}
          onChange={(e) => {
            if (e.target.value) setMonth(e.target.value);
          }}
        />
        <button
          className="icon"
          aria-label="Próximo mês"
          onClick={() => setMonth(addMonths(month + "-01", 1, 1).slice(0, 7))}
        >
          <ChevronRight size={18} />
        </button>
      </div>
    );
  }
  const accountSelect = (
    <select
      aria-label="Filtrar por conta"
      value={accountFilter}
      onChange={(e) => setAccountFilter(e.target.value)}
    >
      <option value="">Todas as contas</option>
      {accounts.map((i) => (
        <option key={i.id} value={i.id}>
          {i.title}
        </option>
      ))}
    </select>
  );
  return (
    <div className="app">
      <aside
        className={"sidebar " + (prefs.compact === "yes" ? "compact" : "")}
      >
        <a className="brand" href="#home">
          <span className="brand-mark">V</span>
          <span>
            veyra<span className="brand-dot">.</span>
            <small>FINANÇAS PESSOAIS</small>
          </span>
        </a>
        <button className="search-trigger" onClick={() => setCommand(true)}>
          <Search size={18} />
          <span>Buscar no Veyra</span>
          <kbd>⌘ K</kbd>
        </button>
        <nav aria-label="Principal">
          {routes.map((r, index) => (
            <React.Fragment key={r.id}>
              {(index === 0 || routes[index - 1].group !== r.group) && (
                <small className="nav-label">{r.group}</small>
              )}
              <a
                href={"#" + r.id}
                aria-current={route === r.id ? "page" : undefined}
                title={r.name}
              >
                <r.icon size={20} />
                <span>{r.name}</span>
                {route === r.id && <i />}
              </a>
            </React.Fragment>
          ))}
        </nav>
        <div className="sidebar-foot">
          <ShieldCheck size={18} />
          <span>
            Seu espaço, seus dados
            <small>
              {window.VeyraFinance
                ? "Banco local do Android"
                : "Salvo neste navegador"}
            </small>
          </span>
        </div>
      </aside>
      <div className="workspace">
        <header className="topbar">
          <div className="breadcrumbs">
            Seu espaço <ChevronRight size={14} />{" "}
            <strong>{current.name}</strong>
          </div>
          <div className="top-actions">
            <span className="badge subtle">
              {simple ? "Modo simples" : "Modo completo"}
            </span>
            <button
              className="icon"
              aria-label={hide ? "Mostrar valores" : "Ocultar valores"}
              onClick={() => {
                setHide(!hide);
                act(() => pref("hideValues", hide ? "no" : "yes"));
              }}
            >
              {hide ? <EyeOff /> : <Eye />}
            </button>
            <button
              className="icon"
              aria-label="Pesquisar"
              onClick={() => setCommand(true)}
            >
              <Search />
            </button>
            <span className="avatar">EU</span>
          </div>
        </header>
        <main id="main">
          <div className="page-heading">
            <div>
              <span className="eyebrow">
                {route === "home"
                  ? "SEU DINHEIRO, COM CLAREZA"
                  : current.group.toUpperCase()}
              </span>
              <h1>
                {route === "home"
                  ? "Vamos cuidar dos seus planos."
                  : current.name}
              </h1>
              <p>
                {route === "home"
                  ? "Um olhar tranquilo para o que você tem e o que vem pela frente."
                  : route === "transactions"
                    ? "Cada entrada e saída, no lugar certo."
                    : route === "appearance"
                      ? "Um Veyra com a sua personalidade."
                      : "Organização para decidir com mais confiança."}
              </p>
            </div>
            {!["settings", "appearance"].includes(route) && (
              <button
                className="primary"
                onClick={() =>
                  newItem(
                    route === "cards"
                      ? "card"
                      : route === "accounts"
                        ? "account"
                        : route === "planning"
                          ? "savings_goal"
                          : route === "recurring"
                            ? "salary_schedule"
                            : route === "subscriptions"
                              ? "subscription"
                              : "expense",
                  )
                }
              >
                <Plus size={18} />{" "}
                {route === "cards"
                  ? "Novo cartão"
                  : route === "accounts"
                    ? "Nova conta"
                    : route === "planning"
                      ? "Nova meta"
                      : route === "recurring"
                        ? "Nova receita recorrente"
                        : route === "subscriptions"
                          ? "Nova assinatura"
                          : "Novo lançamento"}
              </button>
            )}
          </div>
          {offline && (
            <div className="notice">
              <WifiOff size={18} /> Você está offline. Seus registros continuam
              disponíveis neste dispositivo.
            </div>
          )}
          {route === "home" && (
            <>
              <div className="overview-grid">
                <section className="hero">
                  <div className="section-head">
                    <span>
                      <span className="live-dot" /> Seu dinheiro hoje
                    </span>
                    <Wallet size={22} />
                  </div>
                  <small>SALDO DISPONÍVEL</small>
                  <div className="hero-value">{amountText(safe.balance)}</div>
                  <p>Dinheiro confirmado nas contas selecionadas.</p>
                  <div className="hero-footer">
                    <span>
                      <ArrowDownLeft size={16} /> A receber até{" "}
                      {dateLabel(safe.end)}
                      <strong>{amountText(safe.expected)}</strong>
                    </span>
                    <span>
                      <ArrowUpRight size={16} /> Compromissos no período
                      <strong>{amountText(safe.pending)}</strong>
                    </span>
                  </div>
                </section>
                <section className="panel spend">
                  <span className="eyebrow">PARA GASTAR COM TRANQUILIDADE</span>
                  <h2>Você pode gastar</h2>
                  <div
                    className={
                      "spend-value " + (safe.total < 0 ? "negative" : "")
                    }
                  >
                    {amountText(safe.total)}
                  </div>
                  <p>
                    {safe.total < 0
                      ? "Déficit estimado. Revise seus compromissos."
                      : `${amountText(safe.daily)} por dia até ${fullDate(safe.end)}`}
                  </p>
                  <details>
                    <summary>
                      Como esse valor é calculado <Info size={14} />
                    </summary>
                    <dl>
                      {[
                        ["Saldo selecionado", safe.balance],
                        ["Contas e faturas pendentes", -safe.pending],
                        ["Valores efetivamente reservados", -safe.reserved],
                        ["Aportes planejados", -safe.planned],
                        ["Margem de segurança", -safe.margin],
                        ...(prefs.includeFuture === "yes"
                          ? [["Receitas previstas (projeção)", safe.expected]]
                          : []),
                      ].map(([k, v]) => (
                        <React.Fragment key={String(k)}>
                          <dt>{k}</dt>
                          <dd>{amountText(Number(v))}</dd>
                        </React.Fragment>
                      ))}
                    </dl>
                    <small>
                      Estimativa com base nos seus registros. Receitas previstas
                      só entram quando você ativa a projeção.
                    </small>
                  </details>
                </section>
              </div>
              <div className="quick-actions">
                <button onClick={() => newItem("expense")}>
                  <span>
                    <ArrowUpRight />
                  </span>
                  <div>
                    Adicionar gasto<small>Registre em segundos</small>
                  </div>
                  <Plus size={16} />
                </button>
                <button onClick={() => newItem("income")}>
                  <span>
                    <ArrowDownLeft />
                  </span>
                  <div>
                    Adicionar receita<small>Dinheiro que entrou</small>
                  </div>
                  <Plus size={16} />
                </button>
                <button onClick={() => navigate("calendar")}>
                  <span>
                    <CalendarDays />
                  </span>
                  <div>
                    Pagar conta<small>Veja os vencimentos</small>
                  </div>
                  <ChevronRight size={16} />
                </button>
                {!simple && (
                  <button onClick={() => newItem("transfer")}>
                    <span>
                      <ArrowLeftRight />
                    </span>
                    <div>
                      Transferir<small>Entre suas contas</small>
                    </div>
                  </button>
                )}
              </div>
              <FinanceSignals data={data} />
              <div className="stat-row">
                <section className="mini-stat">
                  <span>Gastos neste mês</span>
                  <strong>
                    {amountText(monthly(data, today().slice(0, 7)).expense)}
                  </strong>
                  <small>
                    {monthly(data, addMonths(today(), -1).slice(0, 7)).expense >
                    0
                      ? "Compare os períodos nos relatórios"
                      : "Seus gastos registrados, sem pagamentos de fatura em dobro"}
                  </small>
                </section>
                <section className="mini-stat">
                  <span>Próximo recebimento</span>
                  <strong>
                    {safe.next
                      ? amountText(amount(safe.next))
                      : "Ainda não cadastrado"}
                  </strong>
                  <small>
                    {safe.next
                      ? `${safe.next.title} · ${fullDate(due(safe.next))}`
                      : "Cadastre salário, freelance ou outra receita"}
                  </small>
                  {!safe.next && (
                    <button
                      className="text-button"
                      onClick={() => newItem("salary_schedule")}
                    >
                      Configurar receita <ArrowRight size={14} />
                    </button>
                  )}
                </section>
                <section className="mini-stat">
                  <span>Economia do mês</span>
                  <strong>
                    {amountText(monthly(data, today().slice(0, 7)).saved)}
                  </strong>
                  <small>Receitas recebidas menos despesas do mês</small>
                </section>
              </div>
              <div className="two-col">
                <section className="panel">
                  <div className="section-head">
                    <h2>O que vem pela frente</h2>
                    <button
                      className="text-button"
                      onClick={() => navigate("calendar")}
                    >
                      Ver calendário <ArrowRight size={16} />
                    </button>
                  </div>
                  {coming.length ? (
                    coming.slice(0, 5).map((i) => <Row key={i.id} item={i} />)
                  ) : (
                    <Empty
                      title="Nenhuma conta à vista"
                      body="Cadastre seus próximos vencimentos para planejar o mês."
                      action="Adicionar conta"
                      run={() =>
                        setEditor(
                          createItem("expense", {
                            fields: {
                              status: "pending",
                              planned: "Sim",
                              currency: "BRL",
                            },
                          }),
                        )
                      }
                    />
                  )}
                </section>
                <section>
                  <div className="section-head">
                    <h2>Seu próximo objetivo</h2>
                    <button
                      className="text-button"
                      onClick={() => navigate("planning")}
                    >
                      Ver metas <ArrowRight size={16} />
                    </button>
                  </div>
                  {goals[0] ? (
                    <GoalCard item={goals[0]} />
                  ) : (
                    <section className="panel">
                      <Empty
                        title="Qual é o seu próximo plano?"
                        body="Uma reserva, uma viagem ou um sonho. Comece no seu ritmo."
                        action="Criar uma meta"
                        run={() => newItem("savings_goal")}
                      />
                    </section>
                  )}
                </section>
              </div>
            </>
          )}
          {route === "transactions" && (
            <>
              <section className="panel filters">
                <MonthPicker />
                {accountSelect}
                <label className="search-input">
                  <Search size={18} />
                  <input
                    aria-label="Buscar lançamentos"
                    placeholder="Descrição, categoria ou tag"
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                  />
                </label>
                <select
                  aria-label="Categoria"
                  value={categoryFilter}
                  onChange={(e) => setCategoryFilter(e.target.value)}
                >
                  <option value="">Todas as categorias</option>
                  {categoriesAll.map((c) => (
                    <option key={c}>{c}</option>
                  ))}
                </select>
                <select
                  aria-label="Situação"
                  value={statusFilter}
                  onChange={(e) => setStatusFilter(e.target.value)}
                >
                  <option value="">Todas as situações</option>
                  <option value="paid">Confirmados</option>
                  <option value="pending">Previstos</option>
                  <option value="overdue">Atrasados</option>
                </select>
                <details className="advanced">
                  <summary>
                    <SlidersHorizontal size={16} /> Valor e ferramentas
                  </summary>
                  <div className="filters">
                    <input
                      aria-label="Valor mínimo"
                      placeholder="Mínimo R$"
                      inputMode="decimal"
                      value={min}
                      onChange={(e) => {
                        if (/^\d*([,.]\d{0,2})?$/.test(e.target.value))
                          setMin(e.target.value);
                      }}
                    />
                    <input
                      aria-label="Valor máximo"
                      placeholder="Máximo R$"
                      inputMode="decimal"
                      value={max}
                      onChange={(e) => {
                        if (/^\d*([,.]\d{0,2})?$/.test(e.target.value))
                          setMax(e.target.value);
                      }}
                    />
                    <button
                      onClick={() => {
                        setQuery("");
                        setCategoryFilter("");
                        setStatusFilter("");
                        setAccountFilter("");
                        setMin("");
                        setMax("");
                      }}
                    >
                      Limpar filtros
                    </button>
                    <button
                      onClick={() =>
                        act(async () =>
                          download(
                            "veyra-lancamentos.csv",
                            csv(tx),
                            "text/csv",
                          ),
                        )
                      }
                    >
                      <Download size={16} /> Exportar CSV
                    </button>
                  </div>
                </details>
              </section>
              <div className="section-head">
                <h2>
                  {tx.length} lançamento{tx.length !== 1 ? "s" : ""}
                </h2>
                <div className="inline">
                  <button
                    className="secondary"
                    onClick={() => newItem("income")}
                  >
                    <ArrowDownLeft size={16} /> Receita
                  </button>
                  <button
                    className="secondary"
                    onClick={() => newItem("transfer")}
                  >
                    <ArrowLeftRight size={16} /> Transferir
                  </button>
                </div>
              </div>
              <section className="panel">
                {tx.length ? (
                  tx.map((i) => <Row key={i.id} item={i} />)
                ) : (
                  <Empty
                    title="Nenhum lançamento neste período"
                    body="Adicione um registro ou ajuste os filtros para encontrar o que procura."
                  />
                )}
              </section>
            </>
          )}
          {route === "calendar" && (
            <>
              <div className="toolbar">
                <MonthPicker />
                <div className="segmented">
                  <button
                    aria-pressed={calendarMode === "list"}
                    onClick={() => setCalendarMode("list")}
                  >
                    Lista
                  </button>
                  <button
                    aria-pressed={calendarMode === "month"}
                    onClick={() => setCalendarMode("month")}
                  >
                    Calendário
                  </button>
                </div>
                <button
                  className="secondary"
                  onClick={() => newItem("recurring_rule")}
                >
                  <Repeat size={16} /> Conta fixa
                </button>
              </div>
              {calendarMode === "month" && (
                <section className="panel calendar-grid">
                  {["Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb"].map(
                    (d) => (
                      <strong key={d}>{d}</strong>
                    ),
                  )}
                  {Array.from(
                    { length: new Date(month + "-01T12:00:00").getDay() },
                    (_, n) => (
                      <div key={"blank" + n} />
                    ),
                  )}
                  {Array.from({ length: Number(end.slice(8)) }, (_, n) => {
                    const date = month + "-" + String(n + 1).padStart(2, "0"),
                      entries = [
                        ...allTx.filter((i) => due(i) === date),
                        ...calendarInvoices
                          .filter((i) => i.due === date)
                          .map((i) => ({
                            id: i.id,
                            type: "expense",
                            title:
                              "Fatura · " +
                              (cards.find((c) => c.id === i.cardId)?.title ||
                                "Cartão"),
                          })),
                        ...goalAgenda
                          .filter((i) => i.date === date)
                          .map((i) => ({
                            id: i.id,
                            type: "goal",
                            title: i.title,
                          })),
                      ];
                    return (
                      <button
                        className={
                          "calendar-day " + (date === today() ? "today" : "")
                        }
                        key={date}
                        onClick={() => {
                          setQuery("");
                          setEditor(
                            createItem("expense", {
                              date,
                              fields: { currency: "BRL", status: "pending" },
                            }),
                          );
                        }}
                      >
                        <strong>{n + 1}</strong>
                        {entries.slice(0, 2).map((i) => (
                          <small key={i.id}>
                            {i.type === "income" ? "↓" : "↑"} {i.title}
                          </small>
                        ))}
                        {entries.length > 2 && (
                          <small>+ {entries.length - 2} registro(s)</small>
                        )}
                      </button>
                    );
                  })}
                </section>
              )}
              <section className="panel">
                <div className="section-head">
                  <h2>Agenda financeira</h2>
                  <small>Inclui atrasos anteriores e faturas</small>
                </div>
                {goalAgenda.map((e) => (
                  <div className="record" key={e.id}>
                    <Target />
                    <button
                      className="record-main"
                      onClick={() => setEditor(e.item)}
                    >
                      <strong>{e.title}</strong>
                      <small>
                        {fullDate(e.date)} · Planejamento, sem movimentação
                        automática
                      </small>
                    </button>
                    <strong>{amountText(e.value)}</strong>
                  </div>
                ))}
                {allTx
                  .filter((i) => !settled(i) && !i.fields.card && due(i) <= end)
                  .sort((a, b) => due(a).localeCompare(due(b)))
                  .map((i) => (
                    <Row key={i.id} item={i} />
                  ))}
                {allInvoices(data, addMonths(today(), -120), addMonths(end, 1))
                  .filter((i) => i.remaining > 0 && i.due <= end)
                  .map((i) => (
                    <div className="record" key={i.id}>
                      <CreditCard />
                      <span className="record-main">
                        <strong>
                          Fatura · {cards.find((c) => c.id === i.cardId)?.title}
                        </strong>
                        <small>
                          {fullDate(i.due)}{" "}
                          {i.due < today() ? " · Atrasada" : ""}
                        </small>
                      </span>
                      <strong>{amountText(i.remaining)}</strong>
                      <button
                        className="secondary"
                        onClick={() => navigate("cards")}
                      >
                        Ver fatura
                      </button>
                    </div>
                  ))}
                {!allTx.some((i) => !settled(i) && due(i) <= end) && (
                  <Empty
                    title="Sem compromissos pendentes"
                    body="Inclua suas contas e receitas futuras para preencher sua agenda."
                    action="Adicionar conta fixa"
                    run={() => newItem("recurring_rule")}
                  />
                )}
              </section>
            </>
          )}
          {route === "accounts" && (
            <>
              <div className="card-grid">
                {accounts.map((i) => (
                  <article className="panel" key={i.id}>
                    <div className="section-head">
                      <span
                        className="record-icon"
                        style={{ color: i.fields.color || theme.accent }}
                      >
                        {recordIcon(i.fields.icon || "wallet")}
                      </span>
                      <button
                        className="icon"
                        aria-label={`Editar ${i.title}`}
                        onClick={() => setEditor(i)}
                      >
                        <MoreHorizontal />
                      </button>
                    </div>
                    <h2>{i.title}</h2>
                    <p>{i.fields.kind || "Conta corrente"}</p>
                    <div className="account-value">
                      {amountText(
                        currentBalance(data.items, today(), "BRL", i.id),
                      )}
                    </div>
                    <small>
                      {i.fields.includeAvailable === "no"
                        ? "Excluída do dinheiro disponível"
                        : "Incluída no dinheiro disponível"}
                    </small>
                  </article>
                ))}
              </div>
              {!accounts.length && (
                <section className="panel">
                  <Empty
                    title="Seu dinheiro começa aqui"
                    body="Cadastre banco, dinheiro em espécie ou carteira digital."
                    action="Criar conta"
                    run={() => newItem("account")}
                  />
                </section>
              )}
              <button className="secondary" onClick={() => newItem("transfer")}>
                <ArrowLeftRight size={16} /> Transferir entre contas
              </button>
              <p className="footnote">
                Transferências movimentam os saldos, sem aumentar receitas ou
                despesas. Outras moedas permanecem preservadas no backup e na
                experiência anterior.
              </p>
            </>
          )}
          {route === "cards" && (
            <>
              <div className="card-grid">
                {cards.map((c) => {
                  const inv = allInvoices(
                      data,
                      addMonths(today(), -120),
                      addMonths(today(), 120),
                    ).filter((i) => i.cardId === c.id),
                    used = inv.reduce((n, i) => n + i.remaining, 0);
                  return (
                    <article className="panel credit-card" key={c.id}>
                      <div className="section-head">
                        <span className="badge">
                          <CreditCard size={18} /> {c.title}
                        </span>
                        <button
                          className="icon"
                          aria-label={`Editar ${c.title}`}
                          onClick={() => setEditor(c)}
                        >
                          <MoreHorizontal />
                        </button>
                      </div>
                      <small>LIMITE DISPONÍVEL</small>
                      <h2>{amountText(amount(c, "limit") - used)}</h2>
                      <progress max={amount(c, "limit") || 1} value={used} />
                      <small>
                        Utilizado: {amountText(used)} de{" "}
                        {amountText(amount(c, "limit"))}
                      </small>
                      <p>
                        Fecha dia {c.fields.closing} · Vence dia {c.fields.due}
                      </p>
                      <button
                        className="secondary"
                        onClick={() =>
                          setEditor(
                            createItem("expense", {
                              fields: {
                                card: c.id,
                                currency: "BRL",
                                financialVersion: "3",
                                status: "pending",
                              },
                            }),
                          )
                        }
                      >
                        <Plus size={16} /> Compra no cartão
                      </button>
                      {inv.map((invoice) => (
                        <div className="invoice" key={invoice.id}>
                          <div>
                            <strong>Fatura de {fullDate(invoice.due)}</strong>
                            <small>
                              Pago {amountText(invoice.paid)} · Total{" "}
                              {amountText(invoice.total)}
                            </small>
                          </div>
                          <strong>{amountText(invoice.remaining)}</strong>
                          {invoice.remaining > 0 && (
                            <button
                              className="secondary"
                              onClick={() =>
                                setConfirm({
                                  text: `Pagar ${money(invoice.remaining)} da fatura de ${c.title}? Será usada ${accounts.find((a) => a.id === c.fields.account)?.title || accounts[0]?.title || "uma conta a cadastrar"}. A despesa da compra não será registrada novamente.`,
                                  run: async () => {
                                    await commit(
                                      payInvoice(
                                        dataRef.current!,
                                        invoice.id,
                                        c.fields.account ||
                                          accounts[0]?.id ||
                                          "",
                                      ),
                                    );
                                    setConfirm(null);
                                    setMessage(
                                      "Fatura paga. Saldo atualizado.",
                                    );
                                  },
                                })
                              }
                            >
                              Pagar fatura
                            </button>
                          )}
                        </div>
                      ))}
                    </article>
                  );
                })}
              </div>
              {!cards.length && (
                <section className="panel">
                  <Empty
                    title="Cartões sem surpresas"
                    body="Acompanhe limites, parcelas e próximas faturas."
                    action="Cadastrar cartão"
                    run={() => newItem("card")}
                  />
                </section>
              )}
              <section className="panel">
                <h2>Compras parceladas</h2>
                {records
                  .filter((i) => i.type === "installment_plan")
                  .map((plan) => {
                    const children = records.filter(
                        (i) => i.fields.installmentPlanId === plan.id,
                      ),
                      remaining = children.filter(
                        (i) => (i.fields.dueDate || i.date) > today(),
                      );
                    return (
                      <div className="record" key={plan.id}>
                        <span className="record-main">
                          <strong>{plan.title}</strong>
                          <small>
                            {children.length} parcelas · Total{" "}
                            {amountText(amount(plan))} · Futuro{" "}
                            {amountText(
                              remaining.reduce((n, i) => n + amount(i), 0),
                            )}
                          </small>
                        </span>
                        <button
                          className="secondary"
                          disabled={!remaining.length}
                          onClick={() =>
                            setConfirm({
                              text: "Antecipar a próxima parcela futura para o vencimento da fatura atual? O valor total será preservado.",
                              run: async () => {
                                const sorted = remaining.sort((a, b) =>
                                    due(a).localeCompare(due(b)),
                                  ),
                                  child = sorted[0],
                                  card = cards.find(
                                    (c) => c.id === child.fields.card,
                                  );
                                const target =
                                  allInvoices(
                                    data,
                                    addMonths(today(), -120),
                                    addMonths(today(), 12),
                                  )
                                    .filter(
                                      (i) =>
                                        i.cardId === card?.id &&
                                        i.due >= today(),
                                    )
                                    .sort((a, b) =>
                                      a.due.localeCompare(b.due),
                                    )[0]?.due || today();
                                await commit(
                                  saveChanges(dataRef.current!, [
                                    {
                                      ...child,
                                      fields: {
                                        ...child.fields,
                                        dueDate: target,
                                      },
                                    },
                                  ]),
                                );
                                setConfirm(null);
                              },
                            })
                          }
                        >
                          Antecipar próxima
                        </button>
                      </div>
                    );
                  })}
                <p className="footnote">
                  A compra entra nos relatórios. O pagamento da fatura reduz o
                  saldo, sem contar a mesma despesa duas vezes.
                </p>
              </section>
            </>
          )}
          {route === "planning" && (
            <>
              <div className="section-head">
                <h2>Metas que fazem sentido para você</h2>
                <button className="secondary" onClick={() => newItem("budget")}>
                  <Plus size={16} /> Orçamento
                </button>
              </div>
              <div className="card-grid">
                {goals.map((i) => (
                  <GoalCard key={i.id} item={i} />
                ))}
              </div>
              {!goals.length && (
                <section className="panel">
                  <Empty
                    title="Dê um nome ao seu próximo passo"
                    body="Separe o dinheiro reservado do que você ainda pretende guardar."
                    action="Criar meta"
                    run={() => newItem("savings_goal")}
                  />
                </section>
              )}
              <section className="panel">
                <div className="section-head">
                  <h2>Orçamentos do mês</h2>
                  <MonthPicker />
                </div>
                {budgets.map((b) => {
                  const used = b.fields.category
                      ? stats.categories.find(
                          (c) => c.name === b.fields.category,
                        )?.value || 0
                      : stats.expense,
                    limit = amount(b);
                  return (
                    <button
                      className="budget-row"
                      key={b.id}
                      onClick={() => setEditor(b)}
                    >
                      <span>
                        <strong>{b.title}</strong>
                        <small>
                          {used >= limit
                            ? "Limite ultrapassado"
                            : used >=
                                (limit * Number(b.fields.alertPercent || 80)) /
                                  100
                              ? "Atenção: próximo do limite"
                              : "Dentro do planejado"}
                        </small>
                      </span>
                      <progress max={limit || 1} value={used} />
                      <strong>
                        {amountText(used)} / {amountText(limit)}
                      </strong>
                      <small>Resta {amountText(limit - used)}</small>
                    </button>
                  );
                })}
                {!budgets.length && (
                  <Empty
                    title="Defina um limite confortável"
                    body="Crie um orçamento geral ou por categoria."
                    action="Criar orçamento"
                    run={() => newItem("budget")}
                  />
                )}
              </section>
              <section className="panel">
                <h2>Reserva de emergência</h2>
                <p>
                  Escolha as categorias essenciais e quantos meses deseja
                  cobrir. A base são as despesas registradas no período
                  selecionado.
                </p>
                <div className="filters">
                  <label>
                    Meses de cobertura
                    <select
                      value={prefs.emergencyMonths || "6"}
                      onChange={(e) =>
                        act(() => pref("emergencyMonths", e.target.value))
                      }
                    >
                      {[1, 3, 6, 9, 12].map((n) => (
                        <option key={n}>{n}</option>
                      ))}
                    </select>
                  </label>
                  <strong>
                    Objetivo estimado:{" "}
                    {amountText(
                      stats.categories
                        .filter(
                          (c) =>
                            !prefs.emergencyCategories ||
                            prefs.emergencyCategories
                              .split("|")
                              .includes(c.name),
                        )
                        .reduce((n, c) => n + c.value, 0) *
                        Number(prefs.emergencyMonths || 6),
                    )}
                  </strong>
                </div>
                <div className="chips">
                  {categories
                    .filter(
                      (c) =>
                        !records.some(
                          (i) =>
                            i.type === "financial_category" &&
                            (i.fields.builtinName === c || i.title === c),
                        ),
                    )
                    .map((c, n) => (
                      <button
                        key={c}
                        onClick={() =>
                          setEditor(
                            createItem("financial_category", {
                              id: "builtin-category:" + encodeURIComponent(c),
                              title: c,
                              fields: { builtinName: c },
                            }),
                          )
                        }
                      >
                        {c}
                      </button>
                    ))}
                  {categoriesAll.map((c) => (
                    <button
                      key={c}
                      aria-pressed={
                        !prefs.emergencyCategories ||
                        prefs.emergencyCategories.split("|").includes(c)
                      }
                      onClick={() =>
                        act(() => {
                          const chosen = new Set(
                            (
                              prefs.emergencyCategories ||
                              categoriesAll.join("|")
                            ).split("|"),
                          );
                          chosen.has(c) ? chosen.delete(c) : chosen.add(c);
                          return pref(
                            "emergencyCategories",
                            [...chosen].join("|") || "__none__",
                          );
                        })
                      }
                    >
                      {c}
                    </button>
                  ))}
                </div>
                <small>
                  Não é uma reserva movimentada automaticamente. Use uma meta e
                  uma conta de reserva para acompanhar dinheiro efetivamente
                  separado.
                </small>
              </section>
            </>
          )}
          {route === "reports" && (
            <>
              <GoalEvolution data={data} />
              <div className="toolbar">
                <MonthPicker />
                {accountSelect}
                <select
                  aria-label="Visualização do gráfico"
                  value={chart}
                  onChange={(e) => setChart(e.target.value)}
                >
                  <option value="bars">Barras horizontais</option>
                  <option value="columns">Colunas</option>
                  <option value="ring">Rosca</option>
                  <option value="table">Tabela</option>
                </select>
                <button
                  className="secondary"
                  onClick={() =>
                    act(() =>
                      download(`veyra-${month}.csv`, csv(stats.tx), "text/csv"),
                    )
                  }
                >
                  <Download size={16} /> CSV
                </button>
              </div>
              <div className="stat-row">
                {[
                  ["Receitas recebidas", stats.income],
                  ["Despesas do período", stats.expense],
                  ["Economia registrada", stats.saved],
                ].map(([k, v]) => (
                  <section className="mini-stat" key={String(k)}>
                    <span>{k}</span>
                    <strong>{amountText(Number(v))}</strong>
                  </section>
                ))}
              </div>
              <section className="panel">
                <div className="section-head">
                  <h2>Para onde seu dinheiro vai</h2>
                  <span className="badge">
                    {stats.categories.length} categorias
                  </span>
                </div>
                {stats.categories.length ? (
                  <>
                    <div
                      className={"report-chart " + chart}
                      role="img"
                      aria-label="Distribuição de despesas por categoria"
                    >
                      {chart === "ring" ? (
                        <div
                          className="ring-chart"
                          style={{
                            background: `conic-gradient(${stats.categories
                              .map((c, n) => {
                                const start =
                                  (stats.categories
                                    .slice(0, n)
                                    .reduce((s, x) => s + x.value, 0) /
                                    stats.expense) *
                                  100;
                                return `${["var(--accent)", "var(--positive)", "var(--warning)", "var(--negative)", "var(--muted)"][n % 5]} ${start}% ${start + (c.value / stats.expense) * 100}%`;
                              })
                              .join(",")})`,
                          }}
                        >
                          <div>
                            <small>Total</small>
                            <strong>{amountText(stats.expense)}</strong>
                          </div>
                        </div>
                      ) : (
                        chart !== "table" &&
                        stats.categories.map((c, n) => (
                          <div className="chart-row" key={c.name}>
                            <span>{c.name}</span>
                            <div className="chart-track">
                              <div
                                style={{
                                  [chart === "columns" ? "height" : "width"]:
                                    `${(c.value / Math.max(...stats.categories.map((x) => x.value))) * 100}%`,
                                  background: [
                                    "var(--accent)",
                                    "var(--positive)",
                                    "var(--warning)",
                                  ][n % 3],
                                }}
                              />
                            </div>
                            <strong>{amountText(c.value)}</strong>
                          </div>
                        ))
                      )}
                    </div>
                    <div className="table-scroll">
                      <table>
                        <caption>
                          Valores do gráfico · alternativa acessível
                        </caption>
                        <thead>
                          <tr>
                            <th>Categoria</th>
                            <th>Valor</th>
                            <th>Participação</th>
                          </tr>
                        </thead>
                        <tbody>
                          {stats.categories.map((c) => (
                            <tr key={c.name}>
                              <td>{c.name}</td>
                              <td>{amountText(c.value)}</td>
                              <td>
                                {Math.round((c.value / stats.expense) * 100)}%
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </>
                ) : (
                  <Empty
                    title="Seus relatórios começam com um registro"
                    body="Adicione uma receita ou gasto para visualizar o período."
                  />
                )}
              </section>
              <div className="two-col">
                <section className="panel">
                  <h2>Comparação com o mês anterior</h2>
                  <p>
                    Gastos atuais: <strong>{amountText(stats.expense)}</strong>
                  </p>
                  <p>
                    Mês anterior:{" "}
                    <strong>{amountText(previous.expense)}</strong>
                  </p>
                  <p>
                    {previous.expense > 0
                      ? `${stats.expense > previous.expense ? "Aumento" : "Redução"} de ${Math.abs(Math.round((stats.expense / previous.expense - 1) * 100))}% nas despesas registradas.`
                      : "Ainda não há gastos no mês anterior para comparar."}
                  </p>
                  {stats.categories.map((c) => {
                    const old = previous.categories.find(
                      (x) => x.name === c.name,
                    );
                    return old && c.value > old.value ? (
                      <p key={c.name}>
                        {c.name}: +{amountText(c.value - old.value)} frente ao
                        mês anterior.
                      </p>
                    ) : null;
                  })}
                </section>
                <section className="panel">
                  <h2>Compromissos e saldo previsto</h2>
                  <p>
                    Saldo confirmado:{" "}
                    <strong>{amountText(safe.balance)}</strong>
                  </p>
                  <p>
                    Entradas previstas:{" "}
                    <strong>{amountText(safe.expected)}</strong>
                  </p>
                  <p>
                    Saídas pendentes:{" "}
                    <strong>{amountText(safe.pending)}</strong>
                  </p>
                  <p>
                    Saldo projetado até {fullDate(safe.end)}:{" "}
                    <strong>
                      {amountText(safe.balance + safe.expected - safe.pending)}
                    </strong>
                  </p>
                  <small>
                    Projeção não significa dinheiro disponível. Transferências e
                    pagamento de fatura não aumentam os gastos dos relatórios.
                  </small>
                </section>
              </div>
              <section className="panel">
                <h2>Evolução do saldo e composição</h2>
                <div className="table-scroll">
                  <table>
                    <thead>
                      <tr>
                        <th>Fim de mês</th>
                        <th>Saldo registrado</th>
                        <th>Gastos fixos</th>
                        <th>Gastos variáveis</th>
                      </tr>
                    </thead>
                    <tbody>
                      {Array.from({ length: 6 }, (_, n) =>
                        addMonths(month + "-01", n - 5, 1),
                      ).map((d) => {
                        const m = d.slice(0, 7),
                          s = monthly(data, m, accountFilter),
                          fixed = s.tx
                            .filter(
                              (i) =>
                                i.type === "expense" &&
                                i.fields.paymentType !== "card_payment" &&
                                (settled(i) || i.fields.card) &&
                                (i.fields.recurrenceRuleId ||
                                  i.fields.scheduleId),
                            )
                            .reduce((sum, i) => sum + amount(i), 0);
                        return (
                          <tr key={m}>
                            <td>{fullDate(addDays(addMonths(d, 1, 1), -1))}</td>
                            <td>
                              {amountText(
                                currentBalance(
                                  data.items,
                                  addDays(addMonths(d, 1, 1), -1),
                                  "BRL",
                                  accountFilter,
                                ),
                              )}
                            </td>
                            <td>{amountText(fixed)}</td>
                            <td>{amountText(s.expense - fixed)}</td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </section>
            </>
          )}
          {["recurring", "subscriptions"].includes(route) && (
            <>
              <div className="toolbar">
                <button
                  className="secondary"
                  onClick={() =>
                    newItem(
                      route === "subscriptions"
                        ? "subscription"
                        : "salary_schedule",
                    )
                  }
                >
                  Receita ou assinatura
                </button>
                {route === "recurring" && (
                  <button
                    className="secondary"
                    onClick={() => newItem("recurring_rule")}
                  >
                    Nova conta fixa
                  </button>
                )}
              </div>
              <section className="panel">
                {records
                  .filter((i) =>
                    route === "subscriptions"
                      ? i.type === "subscription"
                      : ["salary_schedule", "recurring_rule"].includes(i.type),
                  )
                  .map((i) => {
                    let next = "";
                    try {
                      next =
                        i.type === "salary_schedule"
                          ? scheduleDates(
                              i,
                              today(),
                              addMonths(today(), 13),
                              calendar(prefs),
                            )[0] || ""
                          : "";
                    } catch {}
                    const monthlyCost = Math.round(
                      amount(i) *
                        ({
                          yearly: 1 / 12,
                          weekly: 52 / 12,
                          fortnightly: 26 / 12,
                          monthly: 1,
                        }[i.fields.frequency || "monthly"] || 1),
                    );
                    return (
                      <div className="record" key={i.id}>
                        <Repeat />
                        <button
                          className="record-main"
                          onClick={() => setEditor(i)}
                        >
                          <strong>{i.title}</strong>
                          <small>
                            {i.fields.paused === "yes" || i.fields.pausedAt
                              ? "Pausada/encerrada"
                              : i.fields.frequency === "business"
                                ? `${i.fields.businessNth || 5}º dia útil · sábado ${prefs.saturday === "yes" ? "conta" : "não conta"}`
                                : i.fields.frequency || "Mensal"}{" "}
                            {next ? " · Próxima: " + fullDate(next) : ""}
                            {i.type === "subscription"
                              ? ` · ${amountText(monthlyCost)}/mês · ${amountText(monthlyCost * 12)}/ano`
                              : ""}
                          </small>
                        </button>
                        <strong>{amountText(amount(i))}</strong>
                        <button
                          className="icon"
                          aria-label={`Pausar ${i.title}`}
                          onClick={() =>
                            act(async () => {
                              await commit(saveChanges(data, [togglePause(i)]));
                              setMessage(
                                i.type === "subscription"
                                  ? "Acompanhamento atualizado. Isso não cancela o serviço contratado."
                                  : "Recorrência atualizada. Histórico preservado.",
                              );
                            })
                          }
                        >
                          <MoreHorizontal />
                        </button>
                      </div>
                    );
                  })}
                {!records.some((i) =>
                  route === "subscriptions"
                    ? i.type === "subscription"
                    : ["salary_schedule", "recurring_rule"].includes(i.type),
                ) && (
                  <Empty
                    title={
                      route === "subscriptions"
                        ? "Pequenos valores também contam"
                        : "Seu próximo recebimento, no calendário"
                    }
                    body="Cadastre frequência, datas e valores. Previsão e recebimento ficam separados."
                    action="Cadastrar"
                    run={() =>
                      newItem(
                        route === "subscriptions"
                          ? "subscription"
                          : "salary_schedule",
                      )
                    }
                  />
                )}
              </section>
              {route === "recurring" && (
                <section className="notice">
                  <Info size={20} />
                  <span>
                    Calendário: feriados nacionais fixos incluídos. Feriados
                    estaduais, municipais e datas móveis precisam ser informados
                    nas configurações. Localização não gera feriados
                    automaticamente. Receitas previstas só alteram o saldo após
                    confirmação.
                  </span>
                </section>
              )}
            </>
          )}
          {route === "appearance" && (
            <>
              <section className="panel appearance-tools">
                <div className="filters">
                  <button
                    className="secondary"
                    onClick={() => act(() => pref("theme", "system"))}
                  >
                    <Sun size={16} />
                    <Moon size={16} /> Seguir sistema
                  </button>
                  <select
                    aria-label="Filtrar temas"
                    value={themeFilter}
                    onChange={(e) => setThemeFilter(e.target.value)}
                  >
                    <option value="all">Todos os 24 temas</option>
                    <option value="light">Claros</option>
                    <option value="dark">Escuros</option>
                    <option value="favorites">Favoritos</option>
                  </select>
                </div>
                <div className="theme-grid">
                  {themes
                    .filter(
                      (t) =>
                        themeFilter === "all" ||
                        (themeFilter === "light" && !t.dark) ||
                        (themeFilter === "dark" && t.dark) ||
                        (themeFilter === "favorites" &&
                          (prefs.themeFavorites || "")
                            .split("|")
                            .includes(t.id)),
                    )
                    .map((t) => (
                      <article
                        className={
                          "theme-choice " +
                          ((themePreview || prefs.theme) === t.id
                            ? "selected"
                            : "")
                        }
                        key={t.id}
                      >
                        <button
                          className="theme-preview"
                          style={{
                            background: t.bg,
                            color: t.text,
                            borderColor: t.line,
                          }}
                          onClick={() => setThemePreview(t.id)}
                          aria-label={`Testar tema ${t.name}`}
                        >
                          <span style={{ background: t.surface }}>
                            <i style={{ background: t.accent }} />
                            <i style={{ background: t.raised }} />
                            <i style={{ background: t.line }} />
                          </span>
                          <strong>Aa</strong>
                          <small style={{ color: t.muted }}>
                            Seu próximo plano
                          </small>
                        </button>
                        <div>
                          <button onClick={() => setThemePreview(t.id)}>
                            {t.name}
                          </button>
                          <button
                            className="icon"
                            aria-label={`Favoritar ${t.name}`}
                            aria-pressed={(prefs.themeFavorites || "")
                              .split("|")
                              .includes(t.id)}
                            onClick={() =>
                              act(() => {
                                const selected = new Set(
                                  (prefs.themeFavorites || "").split("|"),
                                );
                                selected.has(t.id)
                                  ? selected.delete(t.id)
                                  : selected.add(t.id);
                                return pref(
                                  "themeFavorites",
                                  [...selected].filter(Boolean).join("|"),
                                );
                              })
                            }
                          >
                            <Star size={16} />
                          </button>
                        </div>
                      </article>
                    ))}
                </div>
                {themePreview && (
                  <div className="preview-bar">
                    <span>
                      Prévia: {themes.find((t) => t.id === themePreview)?.name}
                    </span>
                    <button onClick={() => setThemePreview("")}>
                      Cancelar prévia
                    </button>
                    <button
                      className="primary"
                      onClick={() =>
                        act(async () => {
                          await pref("theme", themePreview);
                          setThemePreview("");
                          setMessage("Tema aplicado em todo o Veyra.");
                        })
                      }
                    >
                      Salvar tema
                    </button>
                  </div>
                )}
              </section>
              <section className="panel">
                <h2>Conforto em cada detalhe</h2>
                <div className="form-grid">
                  <label>
                    Tamanho do texto
                    <select
                      value={prefs.textSize || "14"}
                      onChange={(e) =>
                        act(() => pref("textSize", e.target.value))
                      }
                    >
                      {[14, 16, 18].map((n) => (
                        <option key={n} value={n}>
                          {n}px
                        </option>
                      ))}
                    </select>
                  </label>
                  <label>
                    Fonte
                    <select
                      value={prefs.font || "manrope"}
                      onChange={(e) => act(() => pref("font", e.target.value))}
                    >
                      <option value="manrope">Manrope</option>
                      <option value="system">Fonte do sistema</option>
                    </select>
                  </label>
                  <label>
                    Densidade
                    <select
                      value={prefs.density || "comfortable"}
                      onChange={(e) =>
                        act(() => pref("density", e.target.value))
                      }
                    >
                      <option value="comfortable">Confortável</option>
                      <option value="compact">Compacta</option>
                    </select>
                  </label>
                  <label>
                    Movimento
                    <select
                      value={prefs.motion || "normal"}
                      onChange={(e) =>
                        act(() => pref("motion", e.target.value))
                      }
                    >
                      <option value="normal">Discreto</option>
                      <option value="reduced">Reduzido</option>
                    </select>
                  </label>
                  <label>
                    Cor de destaque
                    <select
                      value={prefs.accent || ""}
                      onChange={(e) =>
                        act(() => pref("accent", e.target.value))
                      }
                    >
                      <option value="">Cor original do tema</option>
                      {(theme.dark
                        ? ["#b7f3ce", "#a8d3ff", "#e6b6ff", "#ffcc99"]
                        : ["#1d664b", "#235a8c", "#745090", "#99482e"]
                      ).map((c) => (
                        <option key={c}>{c}</option>
                      ))}
                    </select>
                  </label>
                </div>
                <button
                  className="secondary"
                  onClick={() =>
                    act(async () => {
                      const preferences = { ...prefs };
                      for (const k of [
                        "theme",
                        "textSize",
                        "font",
                        "density",
                        "motion",
                        "accent",
                      ])
                        delete preferences[k];
                      await commit({ ...data, preferences });
                      setThemePreview("");
                    })
                  }
                >
                  Restaurar aparência padrão
                </button>
              </section>
            </>
          )}
          {route === "settings" && (
            <>
              <section className="panel">
                <h2>Seu jeito de usar</h2>
                <div className="form-grid">
                  <label>
                    Experiência
                    <select
                      aria-label="Experiência"
                      value={prefs.mode || "simple"}
                      onChange={(e) => act(() => pref("mode", e.target.value))}
                    >
                      <option value="simple">
                        Simples · o essencial primeiro
                      </option>
                      <option value="complete">
                        Completo · mais controles
                      </option>
                    </select>
                  </label>
                  <label>
                    Período de planejamento
                    <select
                      value={prefs.horizon || "month"}
                      onChange={(e) =>
                        act(() => pref("horizon", e.target.value))
                      }
                    >
                      <option value="month">Até o fim do mês</option>
                      <option value="salary">Até o próximo recebimento</option>
                    </select>
                  </label>
                  <label>
                    Margem de segurança (R$)
                    <input
                      defaultValue={decimal(Number(prefs.marginMinor || 0))}
                      inputMode="decimal"
                      onBlur={(e) =>
                        act(async () => {
                          const n = parseMinor(e.target.value);
                          if (n < 0)
                            throw Error("A margem não pode ser negativa.");
                          await pref("marginMinor", String(n));
                        })
                      }
                    />
                  </label>
                  <label>
                    Renda para economia (%)
                    <input
                      type="number"
                      min="0"
                      max="100"
                      defaultValue={prefs.savingPercent || "0"}
                      onBlur={(e) =>
                        act(() =>
                          pref(
                            "savingPercent",
                            String(
                              Math.min(
                                100,
                                Math.max(0, Number(e.target.value)),
                              ),
                            ),
                          ),
                        )
                      }
                    />
                  </label>
                  <label>
                    Projeção do valor para gastar
                    <select
                      value={prefs.includeFuture || "no"}
                      onChange={(e) =>
                        act(() => pref("includeFuture", e.target.value))
                      }
                    >
                      <option value="no">Só dinheiro já disponível</option>
                      <option value="yes">
                        Incluir receitas previstas (projeção)
                      </option>
                    </select>
                  </label>
                  <label>
                    Alertas no painel
                    <select
                      value={prefs.alerts || "yes"}
                      onChange={(e) =>
                        act(() => pref("alerts", e.target.value))
                      }
                    >
                      <option value="yes">Ativados</option>
                      <option value="no">Desativados</option>
                    </select>
                  </label>
                  <label>
                    Sidebar
                    <select
                      value={prefs.compact || "no"}
                      onChange={(e) =>
                        act(() => pref("compact", e.target.value))
                      }
                    >
                      <option value="no">Expandida</option>
                      <option value="yes">Compacta</option>
                    </select>
                  </label>
                </div>
                <button
                  className="secondary"
                  onClick={() => setOnboarding(true)}
                >
                  Retomar configuração inicial
                </button>
              </section>
              <section className="panel">
                <h2>Calendário de recebimento</h2>
                <p>
                  O calendário nacional inclui somente datas fixas. Cadastre
                  também feriados móveis, estaduais, municipais e da sua
                  convenção.
                </p>
                <div className="form-grid">
                  <label>
                    Sábado conta como dia útil?
                    <select
                      value={prefs.saturday || "no"}
                      onChange={(e) =>
                        act(() => pref("saturday", e.target.value))
                      }
                    >
                      <option value="no">Não</option>
                      <option value="yes">Sim</option>
                    </select>
                  </label>
                  <label>
                    Feriados nacionais fixos
                    <select
                      value={prefs.national || "yes"}
                      onChange={(e) =>
                        act(() => pref("national", e.target.value))
                      }
                    >
                      <option value="yes">Considerar</option>
                      <option value="no">Não considerar</option>
                    </select>
                  </label>
                  <label>
                    Estado (referência)
                    <input
                      maxLength={60}
                      defaultValue={prefs.state || ""}
                      placeholder="Ex.: São Paulo"
                      onBlur={(e) => act(() => pref("state", e.target.value))}
                    />
                  </label>
                  <label>
                    Cidade (referência)
                    <input
                      maxLength={100}
                      defaultValue={prefs.city || ""}
                      placeholder="Ex.: Campinas"
                      onBlur={(e) => act(() => pref("city", e.target.value))}
                    />
                  </label>
                  <label className="wide">
                    Feriados manuais (AAAA-MM-DD, separados por espaço)
                    <textarea
                      defaultValue={prefs.holidays || ""}
                      onBlur={(e) =>
                        act(async () => {
                          const dates = e.target.value
                            .split(/[\s,;]+/)
                            .filter(Boolean);
                          if (
                            dates.some(
                              (d) =>
                                !/^\d{4}-\d{2}-\d{2}$/.test(d) ||
                                Number.isNaN(Date.parse(d + "T12:00:00Z")),
                            )
                          )
                            throw Error("Confira as datas dos feriados.");
                          await pref("holidays", dates.join(" "));
                        })
                      }
                    />
                  </label>
                </div>
              </section>
              <section className="panel">
                <h2>Dados, backup e continuidade</h2>
                <p>
                  {window.VeyraFinance
                    ? "Você está usando o banco original do Android. Login e sincronização existentes continuam na experiência anterior."
                    : "Seus dados ficam neste navegador. Esta versão web não sincroniza com a conta Firebase. Faça backups para transferir entre dispositivos."}
                </p>
                <div className="filters">
                  <button
                    className="secondary"
                    onClick={() =>
                      act(() =>
                        download(
                          `veyra-backup-${today()}.json`,
                          JSON.stringify(data, null, 2),
                          "application/json",
                        ),
                      )
                    }
                  >
                    <Download size={16} /> Fazer backup
                  </button>
                  <button
                    className="secondary"
                    onClick={() => fileInput.current?.click()}
                  >
                    <Upload size={16} /> Importar backup ou CSV
                  </button>
                  <button
                    className="secondary"
                    onClick={() =>
                      act(() =>
                        download(
                          "veyra-lancamentos.csv",
                          csv(
                            data.items.filter(
                              (i) =>
                                ["income", "expense", "transfer"].includes(
                                  i.type,
                                ) && active(i),
                            ),
                          ),
                          "text/csv",
                        ),
                      )
                    }
                  >
                    Exportar CSV
                  </button>
                  {window.VeyraFinance && (
                    <button
                      className="secondary"
                      onClick={() => act(() => nativeCall("legacy"))}
                    >
                      Abrir experiência anterior e conta
                    </button>
                  )}
                </div>
                <input
                  ref={fileInput}
                  type="file"
                  accept="application/json,.json,text/csv,.csv"
                  hidden
                  onChange={(e) =>
                    act(async () => {
                      const file = e.target.files?.[0];
                      if (!file) return;
                      if (file.size > 20_000_000)
                        throw Error("O backup excede 20 MB.");
                      setPreview(
                        file.name.toLowerCase().endsWith(".csv")
                          ? await csvPreview(await file.text(), data)
                          : importPreview(await file.text(), data),
                      );
                      e.target.value = "";
                    })
                  }
                />
                <p className="footnote">
                  Backups contêm seus dados financeiros sem criptografia. Guarde
                  em local privado. Nenhum dado financeiro é publicado no
                  GitHub.
                </p>
                <small>
                  Instalar no iPhone: abra o endereço no Safari, toque em
                  Compartilhar e Adicionar à Tela de Início. Android: use
                  Instalar aplicativo no navegador ou o APK.
                </small>
              </section>
              <section className="panel">
                <h2>Categorias e lixeira</h2>
                <button
                  className="secondary"
                  onClick={() => newItem("financial_category")}
                >
                  <Plus size={16} /> Nova categoria
                </button>
                <div className="chips">
                  {records
                    .filter((i) => i.type === "financial_category")
                    .map((i) => (
                      <button key={i.id} onClick={() => setEditor(i)}>
                        {i.title}
                        {i.done ? " · Arquivada" : ""}
                      </button>
                    ))}
                </div>
                {data.items
                  .filter((i) => i.deletedAt > 0)
                  .map((i) => (
                    <div className="record" key={i.id}>
                      <span className="record-main">
                        <strong>{i.title}</strong>
                        <small>
                          {typeLabels[i.type] || "Registro preservado"}
                        </small>
                      </span>
                      <button
                        className="secondary"
                        onClick={() =>
                          act(async () => {
                            await commit(
                              saveChanges(data, [{ ...i, deletedAt: 0 }]),
                            );
                            setMessage("Registro restaurado.");
                          })
                        }
                      >
                        Restaurar
                      </button>
                    </div>
                  ))}
                <small>
                  Registros de módulos antigos continuam preservados no banco e
                  nos backups. Nenhuma exclusão definitiva é feita nesta tela.
                </small>
              </section>
            </>
          )}
          {!routes.some((r) => r.id === route) && (
            <section className="panel">
              <Empty
                title="Este endereço não foi encontrado"
                body="Seus dados estão preservados. Volte ao início para continuar."
                action="Ir para início"
                run={() => navigate("home")}
              />
            </section>
          )}
          <footer className="footer">
            <span>
              <ShieldCheck size={14} />{" "}
              {busy ? "Salvando…" : "Seus registros ficam com você."}
            </span>
            <span>Veyra Finanças · 3.0</span>
          </footer>
        </main>
      </div>
      <nav className="bottom-nav" aria-label="Navegação do celular">
        {routes.slice(0, 4).map((r) => (
          <a
            key={r.id}
            href={"#" + r.id}
            aria-current={route === r.id ? "page" : undefined}
          >
            <r.icon size={22} />
            <small>{r.id === "calendar" ? "Contas" : r.name}</small>
          </a>
        ))}
        <button aria-expanded={more} onClick={() => setMore(true)}>
          <MoreHorizontal />
          <small>Mais</small>
        </button>
      </nav>
      {message && (
        <div className="toast" role="status">
          <Info size={18} />
          <span>{message}</span>
          {undo && (
            <button
              onClick={() =>
                act(async () => {
                  await commit(saveChanges(dataRef.current!, [undo]));
                  setUndo(null);
                  setMessage("Registro restaurado.");
                })
              }
            >
              Desfazer
            </button>
          )}
          <button
            className="icon"
            aria-label="Dispensar"
            onClick={() => {
              setMessage("");
              setUndo(null);
            }}
          >
            <X size={16} />
          </button>
        </div>
      )}
      {more && (
        <Dialog title="Explore seu Veyra" close={() => setMore(false)}>
          <div className="more-grid">
            {routes.slice(4).map((r) => (
              <button key={r.id} onClick={() => navigate(r.id)}>
                <r.icon />
                <span>{r.name}</span>
                <ChevronRight size={16} />
              </button>
            ))}
          </div>
        </Dialog>
      )}
      {command && (
        <Dialog title="Encontre no seu Veyra" close={() => setCommand(false)}>
          <label className="search-input command-input">
            <Search />
            <input
              autoFocus
              placeholder="Página, lançamento, conta ou meta…"
              value={paletteQuery}
              onChange={(e) => setPaletteQuery(e.target.value)}
            />
            <kbd>Esc</kbd>
          </label>
          <div className="command-results">
            {routes
              .filter((r) =>
                r.name.toLowerCase().includes(paletteQuery.toLowerCase()),
              )
              .map((r) => (
                <button key={r.id} onClick={() => navigate(r.id)}>
                  <r.icon size={18} />
                  {r.name}
                  <ArrowRight size={16} />
                </button>
              ))}
            {paletteQuery.length > 1 &&
              records
                .filter((i) => financialTypes.has(i.type))
                .filter((i) =>
                  [i.title, i.notes]
                    .join(" ")
                    .toLowerCase()
                    .includes(paletteQuery.toLowerCase()),
                )
                .slice(0, 20)
                .map((i) => (
                  <button
                    key={i.id}
                    onClick={() => {
                      setCommand(false);
                      setEditor(i);
                    }}
                  >
                    <Search size={18} />
                    {i.title}
                    <small>{typeLabels[i.type] || "Registro preservado"}</small>
                  </button>
                ))}
          </div>
        </Dialog>
      )}
      {editor && (
        <Editor
          key={editor.id + editor.fields.confirming}
          item={editor}
          data={data}
          simple={simple}
          busy={busy}
          close={() => setEditor(null)}
          remove={() => remove(editor)}
          saved={async (next) => {
            await commit(next);
            setEditor(null);
            setMessage("Salvo com cuidado.");
          }}
        />
      )}
      {confirm && (
        <Dialog title="Confirmar ação" close={() => setConfirm(null)}>
          <p>{confirm.text}</p>
          <div className="dialog-actions">
            <button onClick={() => setConfirm(null)}>Voltar</button>
            <button
              className="primary"
              disabled={busy}
              onClick={() => act(confirm.run)}
            >
              Confirmar
            </button>
          </div>
        </Dialog>
      )}
      {preview && (
        <Dialog
          title="Confira antes de importar"
          close={() => setPreview(null)}
        >
          <p>
            {preview.total} registros no arquivo. {preview.items.length} novos e{" "}
            {preview.duplicates} já existentes, que serão preservados sem
            sobrescrita.
          </p>
          <div className="import-list">
            {preview.items.slice(0, 15).map((i) => (
              <p key={i.id}>
                {typeLabels[i.type] || i.type} · {i.title}
              </p>
            ))}
          </div>
          <p>
            As preferências serão recuperadas apenas num espaço vazio. Dados
            atuais e histórico não serão apagados.
          </p>
          <div className="dialog-actions">
            <button onClick={() => setPreview(null)}>Cancelar</button>
            <button
              className="primary"
              disabled={busy}
              onClick={() =>
                act(async () => {
                  const next = saveChanges(data, preview.items);
                  if (!data.items.length && preview.preferences)
                    next.preferences = {
                      ...next.preferences,
                      ...preview.preferences,
                    };
                  await commit(next);
                  setPreview(null);
                  setMessage("Importação concluída sem duplicar registros.");
                })
              }
            >
              Importar {preview.items.length} registros
            </button>
          </div>
        </Dialog>
      )}
      {onboarding && (
        <Dialog
          title="Seu dinheiro, no seu ritmo"
          close={() => {
            setOnboarding(false);
            act(() => pref("onboarded", "yes"));
          }}
        >
          <p>
            Você pode fazer tudo agora ou voltar quando quiser. Sem conectar
            banco e sem preencher um monte de informação.
          </p>
          <div className="onboarding">
            {[
              [
                "Escolher experiência",
                () => act(() => pref("mode", simple ? "complete" : "simple")),
                simple ? "Simples" : "Completo",
              ],
              [
                "Cadastrar primeira conta",
                () => {
                  setOnboarding(false);
                  newItem("account");
                },
                accounts.length ? "Concluído" : "Começar",
              ],
              [
                "Configurar recebimento",
                () => {
                  setOnboarding(false);
                  newItem("salary_schedule");
                },
                records.some((i) => i.type === "salary_schedule")
                  ? "Concluído"
                  : "Opcional",
              ],
              [
                "Adicionar contas principais",
                () => {
                  setOnboarding(false);
                  newItem("recurring_rule");
                },
                "Opcional",
              ],
              [
                "Escolher uma meta",
                () => {
                  setOnboarding(false);
                  newItem("savings_goal");
                },
                goals.length ? "Concluído" : "Opcional",
              ],
              [
                "Encontrar seu tema",
                () => {
                  setOnboarding(false);
                  navigate("appearance");
                },
                "24 opções",
              ],
            ].map(([title, run, description], n) => (
              <button key={String(title)} onClick={run as () => void}>
                <span className="step-number">{n + 1}</span>
                <span>
                  <strong>{title as string}</strong>
                  <small>{description as string}</small>
                </span>
                <ChevronRight size={18} />
              </button>
            ))}
          </div>
          <div className="dialog-actions">
            <button
              className="primary"
              onClick={() =>
                act(async () => {
                  await pref("onboarded", "yes");
                  setOnboarding(false);
                })
              }
            >
              Começar a usar
            </button>
          </div>
        </Dialog>
      )}
    </div>
  );
}

function FinanceSignals({ data }: { data: Data }) {
  if (data.preferences.alerts === "no") return null;
  const day = today(),
    stats = monthly(data, day.slice(0, 7));
  const rows = transactions(data, addMonths(day, -120), addDays(day, 7)).filter(
    (i) =>
      !settled(i) &&
      !i.fields.card &&
      due(i) <= addDays(day, Number(i.fields.reminderDays || 1)),
  );
  const warnings = data.items
    .filter((i) => active(i) && i.type === "budget" && amount(i) > 0)
    .map((i) => {
      const used = i.fields.category
        ? stats.categories.find((c) => c.name === i.fields.category)?.value || 0
        : stats.expense;
      return { item: i, used, percent: Math.round((used / amount(i)) * 100) };
    })
    .filter((i) => i.percent >= Number(i.item.fields.alertPercent || 80));
  if (!rows.length && !warnings.length) return null;
  return (
    <section className="panel">
      <div className="section-head">
        <h2>Atenção ao seu dinheiro</h2>
        <Info size={18} />
      </div>
      {rows.slice(0, 5).map((i) => (
        <p key={i.id}>
          {due(i) < day ? "Atrasado" : due(i) === day ? "Hoje" : "Em breve"}:{" "}
          {i.title} · {money(amount(i))} · {fullDate(due(i))}
        </p>
      ))}
      {warnings.map((i) => (
        <p key={i.item.id}>
          {i.percent >= 100 ? "Limite ultrapassado" : "Próximo do limite"}:{" "}
          {i.item.title} · {i.percent}% utilizado.
        </p>
      ))}
      <small>
        Alertas calculados ao abrir o Veyra. Não são notificações em segundo
        plano.
      </small>
    </section>
  );
}
function GoalEvolution({ data }: { data: Data }) {
  const goals = data.items.filter(
    (i) => i.type === "savings_goal" && active(i),
  );
  if (!goals.length) return null;
  return (
    <section className="panel">
      <h2>Evolução das metas</h2>
      <div className="table-scroll">
        <table>
          <thead>
            <tr>
              <th>Meta</th>
              <th>Reservado</th>
              <th>Objetivo</th>
              <th>Aporte planejado</th>
            </tr>
          </thead>
          <tbody>
            {goals.map((g) => (
              <tr key={g.id}>
                <td>{g.title}</td>
                <td>{money(amount(g, "saved"))}</td>
                <td>{money(amount(g))}</td>
                <td>{money(amount(g, "monthlyContribution"))}/mês</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {goals.map((g) => {
        let history: {
          date: string;
          savedMinor: number;
          deltaMinor: number;
        }[] = [];
        try {
          history = JSON.parse(g.fields.goalHistory || "[]");
        } catch {}
        return (
          <details key={g.id}>
            <summary>{g.title} · histórico de reservas registradas</summary>
            {history.length ? (
              history.map((h, n) => (
                <p key={n}>
                  {fullDate(h.date)} · {h.deltaMinor >= 0 ? "+" : ""}
                  {money(h.deltaMinor)} · acumulado {money(h.savedMinor)}
                </p>
              ))
            ) : (
              <p>
                Ainda sem alterações de reserva registradas nesta versão. O
                valor atual permanece preservado.
              </p>
            )}
          </details>
        );
      })}
    </section>
  );
}
function MoneyField({
  item,
  name,
  label,
  field = "amount",
  optional = false,
}: {
  item: Item;
  name?: string;
  label: string;
  field?: string;
  optional?: boolean;
}) {
  return (
    <label>
      {label}
      <div className="money-input">
        <span>R$</span>
        <input
          name={name || field}
          aria-label={label}
          required={!optional}
          inputMode="decimal"
          defaultValue={
            amount(item, field)
              ? decimal(amount(item, field)).replace(".", ",")
              : ""
          }
          placeholder="0,00"
          maxLength={20}
        />
      </div>
    </label>
  );
}
function AccountField({
  item,
  accounts,
  name = "account",
  label = "Conta",
  required = false,
}: {
  item: Item;
  accounts: Item[];
  name?: string;
  label?: string;
  required?: boolean;
}) {
  return (
    <label>
      {label}
      <select
        name={name}
        aria-label={label}
        required={required}
        defaultValue={item.fields[name] || accounts[0]?.id || ""}
      >
        <option value="">Sem conta vinculada</option>
        {accounts.map((i) => (
          <option key={i.id} value={i.id}>
            {i.title}
          </option>
        ))}
      </select>
    </label>
  );
}
function Editor({
  item,
  data,
  simple,
  busy,
  close,
  remove,
  saved,
}: {
  item: Item;
  data: Data;
  simple: boolean;
  busy: boolean;
  close: () => void;
  remove: () => void;
  saved: (d: Data) => Promise<void>;
}) {
  const [error, setError] = useState(""),
    [type, setType] = useState(item.type),
    [advanced, setAdvanced] = useState(
      !simple || !["income", "expense"].includes(item.type),
    );
  const [frequency, setFrequency] = useState(
      item.fields.frequency || "monthly",
    ),
    [scope, setScope] = useState("one");
  const exists = data.items.some((i) => i.id === item.id),
    confirming = item.fields.confirming === "yes",
    tx = ["expense", "income", "transfer"].includes(type),
    schedule = type === "salary_schedule",
    rule = ["recurring_rule", "subscription"].includes(type),
    goal = type === "savings_goal",
    account = type === "account",
    card = type === "card",
    budget = type === "budget",
    category = type === "financial_category";
  const records = data.items.filter(active),
    accounts = records.filter(
      (i) => i.type === "account" && (i.fields.currency || "BRL") === "BRL",
    ),
    cards = records.filter(
      (i) => i.type === "card" && (i.fields.currency || "BRL") === "BRL",
    ),
    cats = [
      ...new Set([
        ...categoryNames(records),
        ...(item.fields.category ? [item.fields.category] : []),
      ]),
    ];
  const val = (key: string, fallback = "") => item.fields[key] || fallback;
  async function submit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError("");
    try {
      const form = new FormData(e.currentTarget),
        get = (k: string) => String(form.get(k) || ""),
        fields: Record<string, string> = {
          ...item.fields,
          currency: "BRL",
          financialVersion: "3",
        };
      delete fields.confirming;
      const moneyKeys = account
        ? ["opening"]
        : card
          ? ["limit"]
          : goal
            ? ["amount", "saved", "monthlyContribution"]
            : category
              ? []
              : ["amount"];
      for (const key of moneyKeys) {
        const text = get(key) || "0";
        const value = parseMinor(text);
        if (value < 0 && key !== "opening")
          throw Error("Use valores positivos.");
        fields[key + "Minor"] = String(value);
        fields[key] = decimal(value);
      }
      const fieldKeys = [
        "category",
        "subcategory",
        "account",
        "destination",
        "card",
        "status",
        "kind",
        "includeAvailable",
        "closing",
        "due",
        "targetDate",
        "priority",
        "frequency",
        "startDate",
        "endDate",
        "businessNth",
        "day1",
        "day2",
        "adjust",
        "transactionType",
        "mode",
        "color",
        "icon",
        "reminderDays",
        "variable",
        "alertPercent",
      ];
      for (const k of fieldKeys) if (form.has(k)) fields[k] = get(k);
      if (schedule) {
        fields.transactionType = "income";
        fields.frequency = frequency;
        fields.startDate = get("startDate") || item.date;
      }
      if (rule) {
        fields.transactionType = "expense";
        fields.frequency = frequency;
        fields.startDate = get("startDate") || item.date;
      }
      let title = get("title").trim() || fields.category || typeLabels[type];
      const next: Item = {
        ...item,
        type,
        title,
        notes: form.has("notes") ? get("notes") : item.notes,
        date: get("date") || fields.startDate || item.date,
        tags: form.has("tags") ? get("tags") : item.tags,
        fields,
      };
      if (tx && amount(next) <= 0)
        throw Error("O valor precisa ser maior que zero.");
      if (tx && next.date > today() && settled(next))
        throw Error("Uma data futura precisa ficar como prevista.");
      if (type === "expense" && fields.card) {
        fields.status = "pending";
        fields.planned = "Sim";
      }
      if (category) next.done = get("archived") === "yes";
      if (confirming) {
        await saved(
          confirmTransaction(
            data,
            { ...item, fields: { ...item.fields, account: fields.account } },
            amount(next),
            get("date") || today(),
          ),
        );
        return;
      }
      if (schedule && exists) {
        const at = get("effectiveDate") || today();
        if (at < today())
          throw Error("Use hoje ou uma data futura para editar a recorrência.");
        await saved(
          editSchedule(data, item, at, next, scope as "one" | "future"),
        );
        return;
      }
      if (rule && exists) {
        await saved(
          editExpenseRule(
            data,
            item,
            get("effectiveDate") || today(),
            next,
            scope as "one" | "future",
          ),
        );
        return;
      }
      const count = Number(get("installments") || 1);
      const changes =
        type === "expense" && count > 1 && !exists
          ? installmentItems(next, count, data.items)
          : [next];
      await saved(saveChanges(data, changes));
    } catch (e) {
      setError((e as Error).message);
    }
  }
  const details = (
    <>
      <div className="form-grid">
        {!category && (
          <label>
            Descrição
            <input
              name="title"
              defaultValue={item.title}
              maxLength={200}
              required={!tx}
              placeholder={
                schedule
                  ? "Ex.: Salário"
                  : goal
                    ? "Ex.: Reserva de emergência"
                    : "Dê um nome a este registro"
              }
            />
          </label>
        )}
        {tx && (
          <label>
            Data
            <input
              name="date"
              type="date"
              defaultValue={confirming ? today() : item.date}
              required
            />
          </label>
        )}
        {(tx || schedule || rule || card || goal) && (
          <AccountField
            item={item}
            accounts={accounts}
            required={type === "transfer" || card}
          />
        )}{" "}
        {type === "transfer" && (
          <AccountField
            item={item}
            accounts={accounts}
            name="destination"
            label="Conta de destino"
            required
          />
        )}
        {(type === "expense" || rule) && !confirming && (
          <label>
            Cartão (opcional)
            <select name="card" defaultValue={val("card")}>
              <option value="">Pagar pela conta</option>
              {cards.map((i) => (
                <option key={i.id} value={i.id}>
                  {i.title}
                </option>
              ))}
            </select>
          </label>
        )}
        {tx && type !== "transfer" && !confirming && (
          <label>
            Situação
            <select
              name="status"
              defaultValue={val(
                "status",
                type === "income" ? "received" : "paid",
              )}
            >
              <option value={type === "income" ? "received" : "paid"}>
                {type === "income" ? "Recebido" : "Pago"}
              </option>
              <option value={type === "income" ? "expected" : "pending"}>
                Previsto
              </option>
            </select>
          </label>
        )}
        {type === "expense" && !exists && !confirming && (
          <label>
            Parcelas
            <input
              name="installments"
              type="number"
              min="1"
              max="120"
              defaultValue="1"
            />
          </label>
        )}
        {account && (
          <>
            <label>
              Tipo de conta
              <select name="kind" defaultValue={val("kind", "Conta corrente")}>
                {[
                  "Conta corrente",
                  "Conta poupança",
                  "Dinheiro em espécie",
                  "Carteira digital",
                  "Reserva financeira",
                  "Outra",
                ].map((s) => (
                  <option key={s}>{s}</option>
                ))}
              </select>
            </label>
            <label>
              Incluir no dinheiro disponível
              <select
                name="includeAvailable"
                defaultValue={val("includeAvailable", "yes")}
              >
                <option value="yes">Sim</option>
                <option value="no">Não · separado como reserva</option>
              </select>
            </label>
          </>
        )}
        {card && (
          <>
            <label>
              Dia de fechamento
              <input
                name="closing"
                type="number"
                min="1"
                max="31"
                required
                defaultValue={val("closing", "5")}
              />
            </label>
            <label>
              Dia de vencimento
              <input
                name="due"
                type="number"
                min="1"
                max="31"
                required
                defaultValue={val("due", "12")}
              />
            </label>
          </>
        )}
        {goal && (
          <>
            <label>
              Prazo (opcional)
              <input
                name="targetDate"
                type="date"
                defaultValue={val("targetDate")}
              />
            </label>
            <label>
              Prioridade
              <select name="priority" defaultValue={val("priority", "Média")}>
                <option>Alta</option>
                <option>Média</option>
                <option>Baixa</option>
              </select>
            </label>
          </>
        )}
        {(account || card || goal) && (
          <>
            <label>
              Cor
              <input
                type="color"
                name="color"
                defaultValue={val("color", "#b7f3ce")}
              />
            </label>
            <label>
              Ícone
              <select name="icon" defaultValue={val("icon", "wallet")}>
                <option value="wallet">Carteira</option>
                <option value="bank">Banco</option>
                <option value="target">Objetivo</option>
                <option value="plane">Viagem</option>
                <option value="shield">Reserva</option>
              </select>
            </label>
          </>
        )}
        {(schedule || rule) && (
          <>
            <label>
              Frequência
              <select
                aria-label="Frequência"
                name="frequency"
                value={frequency}
                onChange={(e) => setFrequency(e.target.value)}
              >
                <option value="monthly">Mensal · dia fixo</option>
                {schedule && (
                  <>
                    <option value="business">
                      Dia útil escolhido (inclui 5º)
                    </option>
                    <option value="twice">Duas vezes por mês</option>
                    <option value="once">Avulso</option>
                  </>
                )}
                <option value="weekly">Semanal</option>
                <option value="fortnightly">A cada duas semanas</option>
                {!schedule && <option value="yearly">Anual</option>}
              </select>
            </label>
            <label>
              Início
              <input
                name="startDate"
                type="date"
                required
                defaultValue={val("startDate", item.date)}
              />
            </label>
            <label>
              Término (opcional)
              <input name="endDate" type="date" defaultValue={val("endDate")} />
            </label>
            {schedule && frequency === "business" && (
              <label>
                Qual dia útil?
                <input
                  name="businessNth"
                  type="number"
                  min="1"
                  max="23"
                  defaultValue={val("businessNth", "5")}
                />
              </label>
            )}
            {schedule && ["monthly", "twice"].includes(frequency) && (
              <label>
                Dia do mês
                <input
                  name="day1"
                  type="number"
                  min="1"
                  max="31"
                  defaultValue={val("day1", item.date.slice(8))}
                />
              </label>
            )}
            {schedule && frequency === "twice" && (
              <label>
                Segundo dia
                <input
                  name="day2"
                  type="number"
                  min="1"
                  max="31"
                  defaultValue={val("day2", "20")}
                />
              </label>
            )}
            {schedule && frequency !== "business" && (
              <label>
                Se cair em dia não útil
                <select name="adjust" defaultValue={val("adjust", "keep")}>
                  <option value="keep">Manter a data</option>
                  <option value="before">Antecipar</option>
                  <option value="after">Adiar</option>
                </select>
              </label>
            )}
            <label>
              Lembrete no painel
              <select
                name="reminderDays"
                defaultValue={val("reminderDays", "1")}
              >
                <option value="0">No dia</option>
                <option value="1">1 dia antes</option>
                <option value="3">3 dias antes</option>
                <option value="7">7 dias antes</option>
              </select>
            </label>
            {rule && (
              <label>
                Valor
                <select name="variable" defaultValue={val("variable", "no")}>
                  <option value="no">Fixo</option>
                  <option value="yes">Estimado · ajustar ao pagar</option>
                </select>
              </label>
            )}
            {schedule && (
              <label>
                Registro
                <select name="mode" defaultValue={val("mode", "manual")}>
                  <option value="manual">Confirmar manualmente</option>
                  <option value="automatic">
                    Automático ao abrir (data vencida)
                  </option>
                </select>
              </label>
            )}
            {exists && (
              <>
                <label>
                  Aplicar alteração
                  <select
                    value={scope}
                    onChange={(e) => setScope(e.target.value)}
                  >
                    <option value="one">Somente esta ocorrência</option>
                    <option value="future">Esta e as próximas</option>
                  </select>
                </label>
                <label>
                  Data da ocorrência
                  <input
                    name="effectiveDate"
                    type="date"
                    defaultValue={today()}
                    required
                  />
                </label>
              </>
            )}
          </>
        )}
        {budget && (
          <label>
            Alerta do orçamento (%)
            <input
              name="alertPercent"
              type="number"
              min="1"
              max="100"
              defaultValue={val("alertPercent", "80")}
            />
          </label>
        )}
      </div>
      {!category && (
        <>
          <label>
            Subcategoria (opcional)
            <input
              name="subcategory"
              maxLength={100}
              defaultValue={val("subcategory")}
            />
          </label>
          <label>
            Tags (opcional)
            <input name="tags" maxLength={500} defaultValue={item.tags} />
          </label>
          <label>
            Observações
            <textarea
              name="notes"
              maxLength={10000}
              defaultValue={item.notes}
            />
          </label>
        </>
      )}
      {schedule && (
        <p className="footnote">
          Sábado {data.preferences.saturday === "yes" ? "conta" : "não conta"}.
          Feriados nacionais fixos{" "}
          {data.preferences.national === "no" ? "desativados" : "ativados"};
          feriados locais e móveis são manuais em Configurações. Previsão nunca
          entra no saldo antes do recebimento.
        </p>
      )}
    </>
  );
  if ((item.fields.currency || "BRL") !== "BRL")
    return (
      <Dialog title={"Registro em outra moeda"} close={close}>
        <p>
          Este registro está preservado na moeda original. Use a experiência
          anterior do Android ou o backup estruturado para gerenciá-lo.
        </p>
      </Dialog>
    );
  return (
    <Dialog
      title={
        confirming
          ? type === "income"
            ? "Confirmar recebimento"
            : "Pagar conta"
          : (exists ? "Editar " : "Adicionar ") +
            (typeLabels[type] || "registro").toLowerCase()
      }
      close={close}
    >
      <form onSubmit={submit} className="editor-form">
        {tx && !confirming && !exists && (
          <div className="segmented">
            {["expense", "income", "transfer"].map((t) => (
              <button
                key={t}
                type="button"
                aria-pressed={type === t}
                onClick={() => setType(t)}
              >
                {typeLabels[t]}
              </button>
            ))}
          </div>
        )}
        {category ? (
          <>
            <label>
              Nome da categoria
              <input
                name="title"
                required
                maxLength={100}
                defaultValue={item.title}
              />
            </label>
            <label>
              Situação
              <select name="archived" defaultValue={item.done ? "yes" : "no"}>
                <option value="no">Ativa</option>
                <option value="yes">Arquivada</option>
              </select>
            </label>
          </>
        ) : account ? (
          <MoneyField item={item} label="Saldo inicial" field="opening" />
        ) : card ? (
          <MoneyField item={item} label="Limite total" field="limit" />
        ) : (
          <MoneyField
            item={item}
            label={
              goal ? "Valor desejado" : confirming ? "Valor real" : "Valor"
            }
          />
        )}
        {goal && (
          <div className="form-grid">
            <MoneyField
              item={item}
              label="Efetivamente reservado"
              field="saved"
              optional
            />
            <MoneyField
              item={item}
              label="Aporte mensal planejado"
              field="monthlyContribution"
              optional
            />
          </div>
        )}
        {((tx && type !== "transfer") || schedule || rule || budget) && (
          <label>
            Categoria
            <select
              aria-label="Categoria"
              name="category"
              defaultValue={val("category", budget ? "" : "Outros")}
            >
              <option value="">
                {budget ? "Todas · orçamento geral" : "Sem categoria"}
              </option>
              {cats.map((c) => (
                <option key={c}>{c}</option>
              ))}
            </select>
          </label>
        )}
        {!advanced && (
          <button
            className="text-button"
            type="button"
            onClick={() => setAdvanced(true)}
          >
            Mais detalhes <ChevronRight size={16} />
          </button>
        )}
        <div hidden={!advanced}>{details}</div>
        {error && (
          <div className="notice error" role="alert">
            {error}
          </div>
        )}
        <div className="dialog-actions">
          {exists && !confirming && (
            <button
              type="button"
              className="danger icon"
              aria-label="Mover para lixeira"
              onClick={remove}
            >
              <Trash2 size={18} />
            </button>
          )}
          <button type="button" onClick={close}>
            Cancelar
          </button>
          <button className="primary" disabled={busy} type="submit">
            {busy ? "Salvando…" : confirming ? "Confirmar" : "Salvar"}
          </button>
        </div>
      </form>
    </Dialog>
  );
}

class Boundary extends React.Component<
  { children: React.ReactNode },
  { error: string }
> {
  state = { error: "" };
  static getDerivedStateFromError(error: Error) {
    return { error: error.message };
  }
  render() {
    if (this.state.error)
      return (
        <main className="fatal">
          <h1>Vamos retomar com cuidado</h1>
          <p>
            Não foi possível apresentar seus registros. Seus dados armazenados
            não foram apagados.
          </p>
          <p>{this.state.error}</p>
          <button onClick={() => location.reload()}>Reabrir Veyra</button>
        </main>
      );
    return this.props.children;
  }
}
createRoot(document.getElementById("root")!).render(
  <Boundary>
    <App />
  </Boundary>,
);
