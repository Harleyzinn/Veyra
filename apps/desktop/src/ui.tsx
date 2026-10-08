import { legacyCard } from "../shared/finance";
import React, { useEffect, useRef, useState } from "react";
import {
  X,
  Plus,
  Repeat2,
  Search,
  ArrowUpRight,
  ChevronLeft,
  ChevronRight,
  Check,
  MoreHorizontal,
  Trash2,
  Copy,
  Edit3,
  Star,
  Paperclip,
} from "lucide-react";
import {
  Item,
  createItem,
  amount,
  decimal,
  parseMinor,
  money,
  validDate,
} from "../shared/model";
import { catalog } from "../shared/catalog";
export const api = (method: string, value?: any) =>
  window.veyra.call(method, value);
export const labels: Record<string, string> = Object.fromEntries(
  catalog.map((s) => [s.type, s.label]),
);
export const dateLabel = (date: string) =>
  date
    ? new Date(date + "T12:00:00").toLocaleDateString("pt-BR", {
        day: "2-digit",
        month: "short",
      })
    : "Sem data";
export function Button({
  children,
  onClick,
  kind = "",
  disabled = false,
  title = "",
  type = "button",
  "aria-label": ariaLabel,
}: {
  children: React.ReactNode;
  onClick?: () => void;
  kind?: string;
  disabled?: boolean;
  title?: string;
  type?: "button" | "submit";
  "aria-label"?: string;
}) {
  return (
    <button
      type={type}
      className={"button " + kind}
      onClick={onClick}
      disabled={disabled}
      title={title}
      aria-label={ariaLabel}
    >
      {children}
    </button>
  );
}
export function IconButton({
  children,
  onClick,
  label,
}: {
  children: React.ReactNode;
  onClick: () => void;
  label: string;
}) {
  return (
    <button
      className="icon-button"
      aria-label={label}
      title={label}
      onClick={onClick}
    >
      {children}
    </button>
  );
}
export function Panel({
  title,
  subtitle,
  children,
  action,
  className = "",
}: {
  title?: string;
  subtitle?: string;
  children: React.ReactNode;
  action?: React.ReactNode;
  className?: string;
}) {
  return (
    <section className={"panel " + className}>
      {(title || action) && (
        <header className="panel-head">
          <div>
            <h2>{title}</h2>
            {subtitle && <p>{subtitle}</p>}
          </div>
          {action}
        </header>
      )}
      {children}
    </section>
  );
}
export function Empty({
  title,
  detail,
  onAdd,
}: {
  title: string;
  detail?: string;
  onAdd?: () => void;
}) {
  return (
    <div className="empty">
      <div className="empty-symbol">
        <Plus size={24} />
      </div>
      <h3>{title}</h3>
      {detail && <p>{detail}</p>}
      {onAdd && (
        <Button kind="primary" onClick={onAdd}>
          <Plus size={16} />
          Adicionar primeiro registro
        </Button>
      )}
    </div>
  );
}
export function Modal({
  title,
  children,
  onClose,
  wide = false,
}: {
  title: string;
  children: React.ReactNode;
  onClose: () => void;
  wide?: boolean;
}) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const before = document.activeElement as HTMLElement;
    const focus = ref.current?.querySelector(
      "input,button,textarea,select",
    ) as HTMLElement;
    focus?.focus();
    const key = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        e.preventDefault();
        onClose();
      }
      if (e.key === "Tab") {
        const nodes = Array.from(
          ref.current?.querySelectorAll<HTMLElement>(
            'button:not(:disabled),input,textarea,select,[tabindex="0"]',
          ) || [],
        );
        const first = nodes[0],
          last = nodes.at(-1);
        if (e.shiftKey && document.activeElement === first) {
          e.preventDefault();
          last?.focus();
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault();
          first?.focus();
        }
      }
    };
    window.addEventListener("keydown", key);
    return () => {
      window.removeEventListener("keydown", key);
      before?.focus();
    };
  }, [onClose]);
  return (
    <div
      className="modal-backdrop"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div
        className={"modal " + (wide ? "wide" : "")}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        ref={ref}
      >
        <header>
          <h2>{title}</h2>
          <IconButton label="Fechar" onClick={onClose}>
            <X size={20} />
          </IconButton>
        </header>
        {children}
      </div>
    </div>
  );
}
export function Field({
  label,
  value,
  onChange,
  type = "text",
  options,
  placeholder = "",
  required = false,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  type?: string;
  options?: { value: string; label: string }[];
  placeholder?: string;
  required?: boolean;
}) {
  return (
    <label className="field">
      <span>{label}</span>
      {options ? (
        <select value={value} onChange={(e) => onChange(e.target.value)}>
          {options.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
      ) : type === "textarea" ? (
        <textarea
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder={placeholder}
          rows={4}
        />
      ) : (
        <input
          type={type}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder={placeholder}
          required={required}
        />
      )}
    </label>
  );
}
export function Chart({
  points,
  cur = "BRL",
  hidden = false,
}: {
  points: { date: string; balance: number }[];
  cur?: string;
  hidden?: boolean;
}) {
  if (!points.length) return null;
  const min = Math.min(...points.map((p) => p.balance), 0),
    max = Math.max(...points.map((p) => p.balance), 1);
  const x = (index: number) =>
    20 + (index / Math.max(1, points.length - 1)) * 760;
  const y = (value: number) =>
    170 - ((value - min) / Math.max(1, max - min)) * 135;
  const path = points
    .map((p, index) => `${index ? "L" : "M"}${x(index)},${y(p.balance)}`)
    .join(" ");
  return (
    <div className="chart">
      <svg
        viewBox="0 0 800 210"
        role="img"
        aria-label={
          hidden
            ? "Gráfico de fluxo oculto"
            : "Previsão do saldo ao fim de cada dia"
        }
      >
        {[0, 1, 2, 3].map((n) => (
          <line
            key={n}
            x1="20"
            y1={35 + n * 45}
            x2="780"
            y2={35 + n * 45}
            className="grid-line"
          />
        ))}
        {!hidden && (
          <>
            <path d={path + " L780,170 L20,170 Z"} className="chart-fill" />
            <path d={path} className="chart-line" />
            {points.length < 35 &&
              points.map((p, index) => (
                <circle
                  key={p.date + index}
                  cx={x(index)}
                  cy={y(p.balance)}
                  r="3"
                  className="chart-dot"
                >
                  <title>
                    {dateLabel(p.date)} · {money(p.balance, cur)}
                  </title>
                </circle>
              ))}
          </>
        )}
        <text x="20" y="200">
          {dateLabel(points[0].date)}
        </text>
        <text x="780" y="200" textAnchor="end">
          {dateLabel(points.at(-1)!.date)}
        </text>
      </svg>
    </div>
  );
}
export function RecordRow({
  item,
  open,
  complete,
  extra,
}: {
  item: Item;
  open: (i: Item) => void;
  complete?: (i: Item) => void;
  extra?: React.ReactNode;
}) {
  const [menu, setMenu] = useState(false);
  return (
    <div
      className="record-row"
      onContextMenu={(e) => {
        e.preventDefault();
        setMenu(true);
      }}
    >
      {complete && (
        <button
          className={"check " + (item.done ? "checked" : "")}
          aria-label={item.done ? "Reabrir tarefa" : "Concluir tarefa"}
          onClick={() => complete(item)}
        >
          {item.done && <Check size={13} />}
        </button>
      )}
      <button className="record-main" onClick={() => open(item)}>
        <strong className={item.done ? "done" : ""}>{item.title}</strong>
        <small>
          {dateLabel(item.date)}
          {item.fields.priority ? " · " + item.fields.priority : ""}
          {item.fields.category ? " · " + item.fields.category : ""}
          {item.tags ? " · " + item.tags : ""}
        </small>
      </button>
      {extra}
      {item.favorite && <Star size={14} className="accent" />}
      <IconButton label="Opções do registro" onClick={() => setMenu(!menu)}>
        <MoreHorizontal size={17} />
      </IconButton>
      {menu && (
        <>
          <div className="menu-dismiss" onClick={() => setMenu(false)} />
          <div className="context-menu">
            <button
              onClick={() => {
                setMenu(false);
                open(item);
              }}
            >
              <Edit3 size={15} />
              Editar
            </button>
            {complete && (
              <button
                onClick={() => {
                  setMenu(false);
                  complete(item);
                }}
              >
                <Check size={15} />
                Concluir / reabrir
              </button>
            )}
            <button
              onClick={() => {
                setMenu(false);
                void api("duplicate", item.id);
              }}
            >
              <Copy size={15} />
              Duplicar
            </button>
            <button
              onClick={() => {
                setMenu(false);
                void api("save", {
                  item: { ...item, favorite: !item.favorite },
                });
              }}
            >
              <Star size={15} />
              Favorito
            </button>
            {item.type === "task" && (
              <button
                onClick={() => {
                  setMenu(false);
                  open(
                    createItem("task", {
                      parentId: item.id,
                      date: item.date,
                      title: "Nova subtarefa",
                    }),
                  );
                }}
              >
                <Plus size={15} />
                Adicionar subtarefa
              </button>
            )}
            {["expense", "income"].includes(item.type) && (
              <button
                onClick={() => {
                  setMenu(false);
                  open(
                    createItem("recurring_rule", {
                      title: item.title,
                      date: item.date,
                      fields: {
                        ...item.fields,
                        transactionType: item.type,
                        frequency: "Mensal",
                        startDate: item.date,
                      },
                    }),
                  );
                }}
              >
                <Repeat2 size={15} />
                Transformar em recorrente
              </button>
            )}
            <button
              className="danger"
              onClick={() => {
                setMenu(false);
                void api("trash", item.id);
              }}
            >
              <Trash2 size={15} />
              Mover para a lixeira
            </button>
          </div>
        </>
      )}
    </div>
  );
}
const transactionFields = [
  ["category", "Categoria"],
  ["subcategory", "Subcategoria"],
  ["account", "Conta"],
  ["card", "Cartão"],
  ["paymentMethod", "Método de pagamento"],
  ["time", "Horário"],
  ["dueDate", "Vencimento"],
  ["settledDate", "Data do pagamento"],
];
export function Editor({
  expectedUid,
  item,
  all,
  onClose,
  onSaved,
  initialType = "task",
}: {
  item?: Item;
  all: Item[];
  onClose: () => void;
  onSaved: () => void;
  initialType?: string;
  expectedUid: string | null;
}) {
  const [draft, setDraft] = useState<Item>(item || createItem(initialType));
  const [advanced, setAdvanced] = useState(!!item);
  const [count, setCount] = useState("1");
  const [recurring, setRecurring] = useState(false);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [value, setValue] = useState(() =>
    item && (item.fields.amount || item.fields.amountMinor)
      ? decimal(amount(item), item.fields.currency || "BRL")
      : "",
  );
  const [attachment, setAttachment] = useState<Record<string, string>>({});
  const spec = catalog.find((s) => s.type === draft.type);
  const financial = [
    "income",
    "expense",
    "transfer",
    "bill",
    "receivable",
  ].includes(draft.type);
  const update = (key: string, v: any) => setDraft((d) => ({ ...d, [key]: v }));
  const field = (key: string, v: string) =>
    setDraft((d) => ({ ...d, fields: { ...d.fields, [key]: v } }));
  const references = (key: string) => [
    { value: "", label: "Selecionar" },
    ...all
      .filter((i) =>
        key === "card"
          ? i.type === "card"
          : key === "account" || key === "destination"
            ? i.type === "account"
            : ["project", "task", "note", "goal", "event"].includes(i.type),
      )
      .map((i) => ({ value: i.id, label: i.title })),
  ];
  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    setBusy(true);
    try {
      let next = {
        ...draft,
        title: draft.title.trim(),
        fields: { ...draft.fields, ...attachment },
      };
      if (financial) {
        const currency = next.fields.currency || "BRL";
        const minor = parseMinor(value, currency);
        if (minor <= 0) throw Error("Informe um valor maior que zero.");
        next.fields = {
          ...next.fields,
          amount: decimal(minor, currency),
          amountMinor: String(minor),
          currency,
          status:
            next.fields.status ||
            (next.type === "income" ? "received" : "paid"),
        };
        if (
          next.fields.card &&
          next.type === "expense" &&
          next.fields.paymentType !== "card_payment" &&
          !(item && legacyCard(item))
        )
          next.fields.status = "pending";
      }
      if (recurring && !item) {
        next = {
          ...next,
          type: "recurring_rule",
          fields: {
            ...next.fields,
            transactionType: draft.type,
            startDate: draft.date,
            frequency: draft.fields.frequency || "monthly",
            status: "expected",
          },
        };
      }
      if (!next.title) throw Error("Preencha a descrição.");
      if (next.date && !validDate(next.date)) throw Error("Data inválida.");
      await api("save", {
        expectedUid,
        item: next,
        installments:
          !item && !recurring && financial && Number(count) > 1
            ? Number(count)
            : undefined,
      });
      onSaved();
      onClose();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  const builtIn = new Set(financial ? transactionFields.map(([k]) => k) : []);
  const generic = (spec?.fields || []).filter(
    (f) =>
      !builtIn.has(f.key) &&
      !(
        financial
          ? [
              "amount",
              "amountMinor",
              "currency",
              "status",
              "planned",
              "installments",
              "firstInstallment",
            ]
          : ["amountMinor", "planned"]
      ).includes(f.key),
  );
  return (
    <Modal
      title={
        item
          ? "Editar " + (labels[draft.type] || "registro")
          : "Um novo registro"
      }
      onClose={onClose}
      wide
    >
      <form onSubmit={submit}>
        <div className="editor-mode">
          {!item &&
            ["task", "expense", "income", "note", "event", "inbox"].map(
              (type) => (
                <button
                  type="button"
                  className={draft.type === type ? "selected" : ""}
                  key={type}
                  onClick={() => setDraft(createItem(type))}
                >
                  {labels[type] || "Lembrete"}
                </button>
              ),
            )}
        </div>
        <div className="form-body">
          <Field
            label={financial ? "Descrição" : "Título"}
            value={draft.title}
            onChange={(v) => update("title", v)}
            required
            placeholder={
              financial
                ? "Ex.: almoço, salário, internet…"
                : "O que você quer organizar?"
            }
          />
          {financial && (
            <div className="form-grid">
              <Field
                label="Valor"
                value={value}
                onChange={setValue}
                placeholder="0,00"
                required
              />
              <Field
                label="Categoria"
                value={draft.fields.category || ""}
                onChange={(v) => field("category", v)}
                placeholder="Ex.: Alimentação"
              />
            </div>
          )}
          <div className="form-grid">
            <Field
              label="Data"
              type="date"
              value={draft.date}
              onChange={(v) => update("date", v)}
            />
            {draft.type === "task" && (
              <Field
                label="Prioridade"
                value={draft.fields.priority || "Média"}
                onChange={(v) => field("priority", v)}
                options={["Baixa", "Média", "Alta", "Urgente"].map((v) => ({
                  value: v,
                  label: v,
                }))}
              />
            )}
          </div>
          {draft.type === "note" && (
            <Field
              label="Texto da nota • Markdown"
              type="textarea"
              value={draft.notes}
              onChange={(v) => update("notes", v)}
            />
          )}
          <button
            type="button"
            className="text-button"
            onClick={() => setAdvanced(!advanced)}
          >
            {advanced ? "Ocultar detalhes" : "Mais detalhes e vínculos"}
            <ArrowUpRight size={14} />
          </button>
          {advanced && (
            <>
              <div className="form-grid">
                {financial && (
                  <>
                    <Field
                      label="Moeda"
                      value={draft.fields.currency || "BRL"}
                      onChange={(v) => field("currency", v)}
                      options={["BRL", "USD", "EUR", "GBP", "JPY", "KWD"].map(
                        (v) => ({ label: v, value: v }),
                      )}
                    />
                    <Field
                      label="Situação"
                      value={
                        draft.fields.status ||
                        (draft.type === "income" ? "received" : "paid")
                      }
                      onChange={(v) => field("status", v)}
                      options={[
                        {
                          value: draft.type === "income" ? "received" : "paid",
                          label: "Realizado",
                        },
                        { value: "pending", label: "Pendente" },
                        { value: "expected", label: "Previsto" },
                        { value: "cancelled", label: "Cancelado" },
                      ]}
                    />
                    {transactionFields.map(([key, label]) => (
                      <Field
                        key={key}
                        label={label}
                        value={draft.fields[key] || ""}
                        onChange={(v) => field(key, v)}
                        type={
                          key.includes("Date")
                            ? "date"
                            : key === "time"
                              ? "time"
                              : "text"
                        }
                        options={
                          ["account", "card"].includes(key)
                            ? references(key)
                            : undefined
                        }
                      />
                    ))}
                    {draft.type === "transfer" && (
                      <Field
                        label="Conta de destino"
                        value={draft.fields.destination || ""}
                        onChange={(v) => field("destination", v)}
                        options={references("destination")}
                      />
                    )}
                  </>
                )}
                {generic.map((f) => (
                  <Field
                    key={f.key}
                    label={f.label}
                    value={draft.fields[f.key] || ""}
                    onChange={(v) => {
                      field(f.key, v);
                      if (f.kind === "MONEY") {
                        const minor =
                          f.key === "amount" ? "amountMinor" : f.key + "Minor";
                        setDraft((d) => {
                          const fields = { ...d.fields, [f.key]: v };
                          delete fields[minor];
                          return { ...d, fields };
                        });
                      }
                    }}
                    type={f.kind === "DATE" ? "date" : "text"}
                    options={
                      f.kind === "REFERENCE"
                        ? references(f.key)
                        : f.kind === "CHOICE" && f.choices.length
                          ? [
                              { value: "", label: "Selecionar" },
                              ...f.choices.map((v) => ({ label: v, value: v })),
                            ]
                          : undefined
                    }
                  />
                ))}
              </div>
              {financial && !item && (
                <>
                  <label className="toggle">
                    <input
                      type="checkbox"
                      checked={recurring}
                      onChange={(e) => setRecurring(e.target.checked)}
                    />
                    Repetir como série financeira
                  </label>
                  {recurring ? (
                    <Field
                      label="Frequência"
                      value={draft.fields.frequency || "monthly"}
                      onChange={(v) => field("frequency", v)}
                      options={[
                        ["daily", "Diária"],
                        ["weekly", "Semanal"],
                        ["monthly", "Mensal"],
                        ["yearly", "Anual"],
                      ].map(([value, label]) => ({ value, label }))}
                    />
                  ) : (
                    draft.type === "expense" && (
                      <Field
                        label="Quantidade de parcelas"
                        value={count}
                        onChange={setCount}
                        type="number"
                      />
                    )
                  )}
                </>
              )}
              <Field
                label="Projeto ou contexto"
                value={draft.parentId}
                onChange={(v) => update("parentId", v)}
                options={references("parentId")}
              />
              <Field
                label="Tags • separadas por vírgula"
                value={draft.tags}
                onChange={(v) => update("tags", v)}
              />
              {draft.type !== "note" && (
                <Field
                  label="Observações"
                  type="textarea"
                  value={draft.notes}
                  onChange={(v) => update("notes", v)}
                />
              )}
              <Field
                label="Vínculos • escolha registros relacionados"
                value={draft.fields.linkedId || ""}
                onChange={(v) => field("linkedId", v)}
                options={references("linkedId")}
              />
              <label className="toggle">
                <input
                  type="checkbox"
                  checked={draft.favorite}
                  onChange={(e) => update("favorite", e.target.checked)}
                />
                Fixar como favorito
              </label>
              <Button
                onClick={() =>
                  void api("attach")
                    .then((a) => {
                      if (a) setAttachment(a);
                    })
                    .catch((e) => setError(e.message))
                }
              >
                <Paperclip size={15} />
                {attachment.attachmentName ||
                  draft.fields.attachmentName ||
                  "Anexar arquivo local"}
              </Button>
              <small className="muted">
                Anexos ficam neste aparelho. Nenhum arquivo será enviado à
                nuvem.
              </small>
            </>
          )}
          {error && (
            <p className="error" role="alert">
              {error}
            </p>
          )}
        </div>
        <footer className="modal-footer">
          <span className="muted">
            Salvo no PC. Sincroniza quando conectado.
          </span>
          <Button onClick={onClose}>Cancelar</Button>
          <Button kind="primary" type="submit" disabled={busy}>
            {busy ? "Salvando…" : "Salvar registro"}
            <Check size={16} />
          </Button>
        </footer>
      </form>
    </Modal>
  );
}
export function MonthControl({
  value,
  onChange,
}: {
  value: string;
  onChange: (v: string) => void;
}) {
  const move = (n: number) => {
    const d = new Date(value + "-01T12:00:00");
    d.setMonth(d.getMonth() + n);
    onChange(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`);
  };
  return (
    <div className="month-control">
      <IconButton label="Mês anterior" onClick={() => move(-1)}>
        <ChevronLeft size={16} />
      </IconButton>
      <input
        aria-label="Mês selecionado"
        type="month"
        value={value}
        onChange={(e) => onChange(e.target.value)}
      />
      <IconButton label="Próximo mês" onClick={() => move(1)}>
        <ChevronRight size={16} />
      </IconButton>
    </div>
  );
}
export function SearchBox({
  value,
  onChange,
  placeholder = "Pesquisar",
}: {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
}) {
  return (
    <label className="search-box">
      <Search size={17} />
      <input
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
      />
    </label>
  );
}
