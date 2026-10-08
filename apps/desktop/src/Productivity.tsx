import { expanded, status } from "../shared/finance";
import { habitStats } from "../shared/productivity";
import React, { useEffect, useRef, useState } from "react";
import {
  Plus,
  Columns3,
  List,
  Check,
  Star,
  Play,
  Pause,
  Square,
  ArrowRight,
  FileText,
  Folder,
  Eye,
  Code2,
  History,
  Link2,
  ExternalLink,
  Paperclip,
} from "lucide-react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { Item, Snapshot, today, addDays, createItem } from "../shared/model";
import {
  api,
  Panel,
  Button,
  Empty,
  Field,
  RecordRow,
  SearchBox,
  MonthControl,
  Modal,
  dateLabel,
  labels,
} from "./ui";
export function Tasks({
  data,
  open,
  create,
}: {
  data: Snapshot;
  open: (i: Item) => void;
  create: (s: string) => void;
}) {
  const [filter, setFilter] = useState("Hoje");
  const [view, setView] = useState("list");
  const [query, setQuery] = useState("");
  const [project, setProject] = useState("");
  const [sort, setSort] = useState("priority");
  const tasks = data.items.filter((i) => i.type === "task");
  const columns: string[] = data.desktop.kanbanColumns || [
    "A fazer",
    "Fazendo",
    "Concluído",
  ];
  const [column, setColumn] = useState("");
  const matches = tasks
    .filter((i) => {
      if (project && i.parentId !== project) return false;
      if (
        query &&
        ![i.title, i.notes, i.tags]
          .join(" ")
          .toLowerCase()
          .includes(query.toLowerCase())
      )
        return false;
      return filter === "Todas"
        ? true
        : filter === "Concluídas"
          ? i.done
          : filter === "Hoje"
            ? !i.done && i.date === today()
            : filter === "Amanhã"
              ? !i.done && i.date === addDays(today(), 1)
              : filter === "Atrasadas"
                ? !i.done && !!i.date && i.date < today()
                : filter === "Próximas"
                  ? !i.done && i.date > today()
                  : filter === "Inbox"
                    ? !i.done && (!i.date || !i.parentId)
                    : !i.done;
    })
    .sort((a, b) =>
      sort === "title"
        ? a.title.localeCompare(b.title)
        : sort === "date"
          ? a.date.localeCompare(b.date)
          : sort === "order"
            ? Number(a.fields.order || 0) - Number(b.fields.order || 0)
            : ["Urgente", "Alta", "Média", "Baixa", ""].indexOf(
                a.fields.priority || "",
              ) -
              ["Urgente", "Alta", "Média", "Baixa", ""].indexOf(
                b.fields.priority || "",
              ),
    );
  const complete = (i: Item) =>
    void api("save", {
      item: {
        ...i,
        done: !i.done,
        fields: { ...i.fields, status: i.done ? "A fazer" : "Concluído" },
      },
    });
  function drop(e: React.DragEvent, target?: string, id?: string) {
    e.preventDefault();
    const dragged = e.dataTransfer.getData("application/veyra-item");
    const item = tasks.find((i) => i.id === dragged);
    if (!item) return;
    if (target)
      void api("save", {
        item: {
          ...item,
          done: target === "Concluído",
          fields: { ...item.fields, status: target },
        },
      });
    else if (id) {
      const ids = matches.map((i) => i.id).filter((i) => i !== dragged);
      ids.splice(Math.max(0, ids.indexOf(id)), 0, dragged);
      void api("reorder", { ids });
    }
  }
  return (
    <>
      <div className="page-title">
        <div>
          <div className="eyebrow">ESPAÇO PARA O QUE IMPORTA</div>
          <h1>Tarefas</h1>
          <p>
            {tasks.filter((i) => !i.done).length} em aberto. Uma de cada vez.
          </p>
        </div>
        <div className="actions">
          <Button onClick={() => create("project")}>
            <Folder size={16} />
            Projeto
          </Button>
          <Button kind="primary" onClick={() => create("task")}>
            <Plus size={16} />
            Nova tarefa
          </Button>
        </div>
      </div>
      <div className="tabs">
        {[
          "Hoje",
          "Amanhã",
          "Próximas",
          "Atrasadas",
          "Inbox",
          "Todas",
          "Concluídas",
        ].map((t) => (
          <button
            key={t}
            className={filter === t ? "active" : ""}
            onClick={() => setFilter(t)}
          >
            {t}
          </button>
        ))}
      </div>
      <div className="filter-bar">
        <SearchBox
          value={query}
          onChange={setQuery}
          placeholder="Títulos, notas ou tags…"
        />
        <select
          value={project}
          onChange={(e) => setProject(e.target.value)}
          aria-label="Filtrar projeto"
        >
          <option value="">Todos os projetos</option>
          {data.items
            .filter((i) => i.type === "project")
            .map((i) => (
              <option value={i.id} key={i.id}>
                {i.title}
              </option>
            ))}
        </select>
        <select
          aria-label="Ordenar tarefas"
          value={sort}
          onChange={(e) => setSort(e.target.value)}
        >
          {[
            ["priority", "Prioridade"],
            ["date", "Data"],
            ["title", "Título"],
            ["order", "Ordem manual"],
          ].map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
        <div className="segmented">
          <button
            className={view === "list" ? "selected" : ""}
            onClick={() => setView("list")}
          >
            <List size={15} />
            Lista
          </button>
          <button
            className={view === "kanban" ? "selected" : ""}
            onClick={() => {
              setView("kanban");
              setFilter("Todas");
            }}
          >
            <Columns3 size={15} />
            Kanban
          </button>
        </div>
      </div>
      {view === "list" ? (
        <Panel>
          {matches.slice(0, 200).map((i) => (
            <div
              key={i.id}
              draggable
              onDragStart={(e) =>
                e.dataTransfer.setData("application/veyra-item", i.id)
              }
              onDragOver={(e) => e.preventDefault()}
              onDrop={(e) => drop(e, undefined, i.id)}
            >
              <RecordRow item={i} open={open} complete={complete} />
            </div>
          ))}
          {!matches.length && (
            <Empty
              title="Um espaço livre para planejar"
              detail="Adicione uma tarefa ou escolha outro filtro."
              onAdd={() => create("task")}
            />
          )}
          <p className="muted small">
            Arraste para mudar a ordem. Use “Ordem manual” para visualizá-la.
          </p>
        </Panel>
      ) : (
        <>
          <div className="kanban">
            {columns.map((c) => (
              <section
                className="kanban-column"
                key={c}
                onDragOver={(e) => e.preventDefault()}
                onDrop={(e) => drop(e, c)}
              >
                <header>
                  <i />
                  <h3>{c === "Fazendo" ? "Em andamento" : c}</h3>
                  <span>
                    {
                      matches.filter(
                        (i) =>
                          (i.done
                            ? "Concluído"
                            : i.fields.status || "A fazer") === c,
                      ).length
                    }
                  </span>
                </header>
                {matches
                  .filter(
                    (i) =>
                      (i.done ? "Concluído" : i.fields.status || "A fazer") ===
                      c,
                  )
                  .slice(0, 80)
                  .map((i) => (
                    <div
                      className="kanban-card"
                      key={i.id}
                      draggable
                      onDragStart={(e) =>
                        e.dataTransfer.setData("application/veyra-item", i.id)
                      }
                    >
                      <button onClick={() => open(i)}>
                        <small
                          className={
                            "priority " +
                            (i.fields.priority === "Alta" ||
                            i.fields.priority === "Urgente"
                              ? "high"
                              : "")
                          }
                        >
                          {i.fields.priority || "Média"}
                        </small>
                        <strong>{i.title}</strong>
                        <p>{i.notes.slice(0, 100)}</p>
                        <footer>
                          {dateLabel(i.date)}
                          {i.tags && <span>{i.tags}</span>}
                        </footer>
                      </button>
                      <Button onClick={() => complete(i)}>
                        <Check size={13} />
                        {i.done ? "Reabrir" : "Concluir"}
                      </Button>
                    </div>
                  ))}
                <button className="kanban-add" onClick={() => create("task")}>
                  <Plus size={15} />
                  Adicionar tarefa
                </button>
              </section>
            ))}
          </div>
          <div className="actions">
            <input
              value={column}
              onChange={(e) => setColumn(e.target.value)}
              placeholder="Nome de uma nova coluna"
              aria-label="Nova coluna Kanban"
            />
            <Button
              disabled={!column.trim() || columns.includes(column.trim())}
              onClick={() => {
                void api("desktop", {
                  kanbanColumns: [...columns, column.trim()],
                });
                setColumn("");
              }}
            >
              <Plus size={15} />
              Adicionar coluna
            </Button>
          </div>
        </>
      )}
    </>
  );
}
export function Notes({
  data,
  create,
}: {
  data: Snapshot;
  create: (s: string) => void;
}) {
  const notes = data.items.filter((i) => i.type === "note");
  const [selected, setSelected] = useState<string | null>(notes[0]?.id || null);
  const [draft, setDraft] = useState<Item | null>(null);
  const [query, setQuery] = useState("");
  const [folder, setFolder] = useState("");
  const [matching, setMatching] = useState<Set<string> | null>(null);
  useEffect(() => {
    let live = true;
    if (!query) {
      setMatching(null);
      return;
    }
    void api("search", { query, types: ["note"], limit: 300 }).then((r) => {
      if (live) setMatching(new Set(r.items.map((i: Item) => i.id)));
    });
    return () => {
      live = false;
    };
  }, [query, data.items]);
  const [preview, setPreview] = useState(false);
  const [saved, setSaved] = useState("");
  const [history, setHistory] = useState<any[] | null>(null);
  const saveQueue = useRef(Promise.resolve());
  const latest = useRef<Item | null>(null);
  useEffect(() => {
    if (!selected && notes.length) setSelected(notes[0].id);
  }, [notes, selected]);
  useEffect(() => {
    let live = true;
    void (async () => {
      await saveQueue.current;
      const item = selected ? await api("item", selected) : null;
      if (live) {
        setDraft(item);
        latest.current = item;
        setSaved("");
      }
    })();
    return () => {
      live = false;
    };
  }, [selected]);
  function update(partial: Partial<Item>) {
    const original = latest.current;
    if (!original) return;
    const next = { ...original, ...partial };
    latest.current = next;
    setDraft(next);
    setSaved("Salvando…");
    saveQueue.current = saveQueue.current
      .then(() => api("save", { item: next, expectedUid: data.uid }))
      .then(() => setSaved("Salvo neste PC"))
      .catch((e) => setSaved(e.message));
  }
  const folders = [
    ...new Set(notes.map((i) => i.fields.folder).filter(Boolean)),
  ].sort();
  return (
    <>
      <div className="page-title">
        <div>
          <div className="eyebrow">IDEIAS QUE GANHAM ESPAÇO</div>
          <h1>Notas</h1>
          <p>Escreva, conecte e volte quando precisar.</p>
        </div>
        <div className="actions">
          <Button onClick={() => void api("openWindow", "notes")}>
            <ExternalLink size={15} />
            Outra janela
          </Button>
          <Button kind="primary" onClick={() => create("note")}>
            <Plus size={16} />
            Nova nota
          </Button>
        </div>
      </div>
      <div className="notes-workspace">
        <aside className="folders">
          <button
            className={!folder ? "active" : ""}
            onClick={() => setFolder("")}
          >
            <FileText size={16} />
            Todas as notas<span>{notes.length}</span>
          </button>
          <button
            className={folder === "@favorite" ? "active" : ""}
            onClick={() => setFolder("@favorite")}
          >
            <Star size={16} />
            Favoritas
          </button>
          <h4>PASTAS</h4>
          {folders.map((f) => (
            <button
              className={folder === f ? "active" : ""}
              key={f}
              onClick={() => setFolder(f)}
            >
              <Folder size={15} />
              {f}
            </button>
          ))}
          <p className="muted small">
            Escolha uma pasta no cabeçalho da nota. Tags e vínculos ficam com o
            registro.
          </p>
        </aside>
        <aside className="note-list">
          <SearchBox
            value={query}
            onChange={setQuery}
            placeholder="Buscar nas notas"
          />
          {notes
            .filter(
              (i) =>
                (!folder || folder === "@favorite"
                  ? !folder || i.favorite
                  : i.fields.folder === folder) &&
                (!matching || matching.has(i.id)),
            )
            .sort(
              (a, b) =>
                Number(b.favorite) - Number(a.favorite) ||
                b.createdAt - a.createdAt,
            )
            .map((i) => (
              <button
                key={i.id}
                className={
                  "note-preview " + (selected === i.id ? "selected" : "")
                }
                onClick={() => setSelected(i.id)}
              >
                <strong>
                  {i.title}
                  {i.favorite && <Star size={12} />}
                </strong>
                <p>
                  {i.notes.replace(/[#*_`]/g, "").slice(0, 95) ||
                    "Uma ideia esperando palavras."}
                </p>
                <small>
                  {dateLabel(i.date)}
                  {i.fields.folder ? " · " + i.fields.folder : ""}
                </small>
              </button>
            ))}
        </aside>
        <section className="note-editor">
          {draft ? (
            <>
              <header>
                <span className="muted small">
                  {saved || "Autosave local ativo"}
                </span>
                <div className="actions">
                  <Button onClick={() => setPreview(!preview)}>
                    {preview ? <Code2 size={15} /> : <Eye size={15} />}{" "}
                    {preview ? "Editar" : "Visualizar"}
                  </Button>
                  <Button onClick={() => update({ favorite: !draft.favorite })}>
                    <Star
                      size={15}
                      fill={draft.favorite ? "currentColor" : "none"}
                    />
                  </Button>
                  <Button
                    onClick={() =>
                      void api("history", draft.id).then(setHistory)
                    }
                  >
                    <History size={15} />
                  </Button>
                </div>
              </header>
              <input
                className="note-title"
                value={draft.title}
                aria-label="Título da nota"
                onChange={(e) => update({ title: e.target.value })}
              />
              <div className="note-metadata">
                <input
                  placeholder="Pasta"
                  aria-label="Pasta da nota"
                  value={draft.fields.folder || ""}
                  onChange={(e) =>
                    update({
                      fields: { ...draft.fields, folder: e.target.value },
                    })
                  }
                />
                <input
                  placeholder="Tags separadas por vírgula"
                  aria-label="Tags da nota"
                  value={draft.tags}
                  onChange={(e) => update({ tags: e.target.value })}
                />
              </div>
              {preview ? (
                <article className="markdown">
                  <ReactMarkdown
                    remarkPlugins={[remarkGfm]}
                    components={{
                      a: ({ href, children }) => (
                        <button
                          className="markdown-link"
                          onClick={() => href && void api("openLink", href)}
                        >
                          {children}
                          <ArrowRight size={12} />
                        </button>
                      ),
                      img: ({ alt }) => (
                        <span className="muted">
                          Imagem externa: {alt || "sem descrição"} •
                          carregamento remoto desativado por privacidade
                        </span>
                      ),
                    }}
                  >
                    {draft.notes}
                  </ReactMarkdown>
                </article>
              ) : (
                <textarea
                  className="markdown-source"
                  aria-label="Conteúdo da nota"
                  value={draft.notes}
                  placeholder={
                    "# Sua próxima ideia\n\nMarkdown, listas, tabelas e blocos de código.\n\n- [ ] Um passo de cada vez"
                  }
                  onChange={(e) => update({ notes: e.target.value })}
                />
              )}
              <footer>
                <span>{draft.notes.length} caracteres</span>
                <Button
                  onClick={() =>
                    void api("attach").then((a) => {
                      if (a) update({ fields: { ...draft.fields, ...a } });
                    })
                  }
                >
                  <Paperclip size={14} />
                  {draft.fields.attachmentName || "Anexar localmente"}
                </Button>
                {draft.fields.attachmentName && (
                  <Button onClick={() => void api("attachment", draft.id)}>
                    Exportar anexo
                  </Button>
                )}
              </footer>
            </>
          ) : (
            <Empty
              title="Dê um lugar às suas ideias"
              detail="Crie uma nota para começar. Ela fica disponível offline."
              onAdd={() => create("note")}
            />
          )}
        </section>
      </div>
      {history && (
        <Modal title="Histórico da nota" onClose={() => setHistory(null)} wide>
          <div className="form-body">
            {history.length ? (
              history.map((h) => (
                <div className="history-entry" key={h.id}>
                  <strong>
                    {new Date(h.at).toLocaleString("pt-BR")} ·{" "}
                    {h.action === "remote" ? "Outro dispositivo" : "Este PC"}
                  </strong>
                  <pre>
                    {(h.after?.notes || h.after?.title || "").slice(0, 500)}
                  </pre>
                  <Button
                    onClick={() => {
                      if (draft)
                        update({ notes: h.after.notes, title: h.after.title });
                      setHistory(null);
                    }}
                  >
                    Restaurar esta versão
                  </Button>
                </div>
              ))
            ) : (
              <Empty title="O histórico começa na primeira edição" />
            )}
          </div>
        </Modal>
      )}
    </>
  );
}
export function Calendar({
  data,
  open,
  create,
}: {
  data: Snapshot;
  open: (i: Item) => void;
  create: (s: string) => void;
}) {
  const [month, setMonth] = useState(today().slice(0, 7));
  const [view, setView] = useState("Mês");
  const [day, setDay] = useState(today());
  let recurring: Item[] = [];
  try {
    const reference = view === "Mês" ? month + "-01" : day;
    recurring = expanded(
      data.items,
      addDays(reference, -7),
      addDays(reference, 42),
    )
      .filter(
        (i) =>
          i.fields.virtual === "yes" &&
          ["pending", "expected", "overdue"].includes(status(i)),
      )
      .map((i) => ({ ...i, date: i.fields.dueDate || i.date }));
  } catch {
    /* Invalid financial records are explained in the finance view. */
  }
  const records = [
    ...recurring,
    ...data.items.filter(
      (i) =>
        ["task", "event", "bill", "inbox", "exam", "birthday"].includes(
          i.type,
        ) &&
        i.date &&
        !i.done,
    ),
  ];
  const first = month + "-01";
  const firstWeekday = (new Date(first + "T12:00:00").getDay() + 6) % 7;
  const start = addDays(first, -firstWeekday);
  const weekStart = addDays(
    day,
    -((new Date(day + "T12:00:00").getDay() + 6) % 7),
  );
  const days = Array.from(
    { length: view === "Semana" ? 7 : view === "Dia" ? 1 : 42 },
    (_, n) =>
      view === "Semana"
        ? addDays(weekStart, n)
        : view === "Dia"
          ? day
          : addDays(start, n),
  );
  function drop(e: React.DragEvent, date: string) {
    e.preventDefault();
    const i = records.find(
      (i) => i.id === e.dataTransfer.getData("application/veyra-item"),
    );
    if (i)
      void api("save", {
        item: {
          ...i,
          date,
          fields: {
            ...i.fields,
            ...(i.fields.dueDate ? { dueDate: date } : {}),
          },
        },
      });
  }
  return (
    <>
      <div className="page-title">
        <div>
          <div className="eyebrow">SEU TEMPO, EM PERSPECTIVA</div>
          <h1>Calendário</h1>
          <p>Tarefas, eventos e contas em um só lugar.</p>
        </div>
        <div className="actions">
          <MonthControl value={month} onChange={setMonth} />
          <Button
            onClick={() => {
              setDay(today());
              setMonth(today().slice(0, 7));
            }}
          >
            Hoje
          </Button>
          <Button kind="primary" onClick={() => create("event")}>
            <Plus size={15} />
            Evento
          </Button>
        </div>
      </div>
      <div className="tabs">
        {["Dia", "Semana", "Mês", "Agenda"].map((t) => (
          <button
            key={t}
            className={view === t ? "active" : ""}
            onClick={() => setView(t)}
          >
            {t}
          </button>
        ))}
      </div>
      {view !== "Mês" && (
        <Field
          label="Dia de referência"
          type="date"
          value={day}
          onChange={setDay}
        />
      )}{" "}
      {view === "Agenda" ? (
        <Panel>
          {records
            .filter((i) => i.date >= day)
            .sort((a, b) =>
              (a.date + (a.fields.time || "")).localeCompare(
                b.date + (b.fields.time || ""),
              ),
            )
            .slice(0, 150)
            .map((i) => (
              <RecordRow
                key={i.id}
                item={i}
                open={open}
                extra={
                  <span>
                    {i.fields.time || "Dia todo"} · {labels[i.type]}
                  </span>
                }
              />
            ))}
          {!records.length && (
            <Empty title="Seu tempo está livre" onAdd={() => create("event")} />
          )}
        </Panel>
      ) : (
        <div
          className={
            "calendar " +
            (view === "Dia" ? "day" : view === "Semana" ? "week" : "")
          }
        >
          {view !== "Dia" &&
            ["SEG", "TER", "QUA", "QUI", "SEX", "SÁB", "DOM"].map((d) => (
              <header key={d}>{d}</header>
            ))}
          {days.map((d) => (
            <section
              key={d}
              className={
                (d === today() ? "today " : "") +
                (d.startsWith(month) ? "" : "other-month")
              }
              onDragOver={(e) => e.preventDefault()}
              onDrop={(e) => drop(e, d)}
            >
              <button
                className="calendar-date"
                onClick={() => {
                  setDay(d);
                  if (view === "Mês") setView("Dia");
                }}
              >
                {Number(d.slice(8))}
                {d === today() && <span>hoje</span>}
              </button>
              {records
                .filter((i) => i.date === d)
                .sort((a, b) =>
                  (a.fields.time || "").localeCompare(b.fields.time || ""),
                )
                .slice(0, view === "Mês" ? 5 : 40)
                .map((i) => (
                  <button
                    className={"calendar-event " + i.type}
                    key={i.id}
                    draggable
                    onDragStart={(e) =>
                      e.dataTransfer.setData("application/veyra-item", i.id)
                    }
                    onClick={() => open(i)}
                  >
                    <span>{i.fields.time || ""}</span>
                    {i.title}
                  </button>
                ))}
            </section>
          ))}
        </div>
      )}
      <p className="muted small">
        Arraste um compromisso para reagendar. As séries financeiras pendentes
        também aparecem aqui.
      </p>
    </>
  );
}
export { habitStats } from "../shared/productivity";
export function Habits({
  data,
  open,
  create,
}: {
  data: Snapshot;
  open: (i: Item) => void;
  create: (s: string) => void;
}) {
  const habits = data.items.filter((i) => i.type === "habit");
  async function check(h: Item) {
    const id = `checkin:${h.id}:${today()}`;
    const old: Item | null = await api("item", id);
    const item = old
      ? { ...old, deletedAt: old.deletedAt ? 0 : Date.now() }
      : createItem("checkin", {
          id,
          title: h.title,
          parentId: h.id,
          date: today(),
          done: true,
        });
    await api("save", { item });
  }
  return (
    <>
      <div className="page-title">
        <div>
          <div className="eyebrow">CONSISTÊNCIA, SEM PRESSA</div>
          <h1>Hábitos</h1>
          <p>Pequenos passos que fazem parte de você.</p>
        </div>
        <Button kind="primary" onClick={() => create("habit")}>
          <Plus size={15} />
          Novo hábito
        </Button>
      </div>
      <div className="habit-grid">
        {habits.map((h) => {
          const stats = habitStats(data.items, h.id);
          const checked = stats.dates.includes(today());
          return (
            <Panel
              key={h.id}
              title={h.title}
              subtitle={h.fields.frequency || "Diária"}
              action={<Button onClick={() => open(h)}>Editar</Button>}
            >
              <div className="habit-metrics">
                <div>
                  <strong>{stats.current}</strong>
                  <small>Sequência atual</small>
                </div>
                <div>
                  <strong>{stats.longest}</strong>
                  <small>Melhor sequência</small>
                </div>
                <div>
                  <strong>{stats.rate}%</strong>
                  <small>Últimos 30 dias</small>
                </div>
              </div>
              <div className="habit-calendar">
                {Array.from({ length: 84 }, (_, n) =>
                  addDays(today(), n - 83),
                ).map((d) => (
                  <span
                    title={
                      dateLabel(d) +
                      (stats.dates.includes(d)
                        ? " · concluído"
                        : " · sem registro")
                    }
                    className={stats.dates.includes(d) ? "filled" : ""}
                    key={d}
                  />
                ))}
              </div>
              <Button
                kind={checked ? "" : "primary"}
                onClick={() => void check(h)}
              >
                <Check size={15} />
                {checked ? "Registrado hoje" : "Registrar hoje"}
              </Button>
            </Panel>
          );
        })}
      </div>
      {!habits.length && (
        <Panel>
          <Empty
            title="Qual hábito você quer cultivar?"
            detail="Acompanhe consistência, sequência e histórico sem cobranças."
            onAdd={() => create("habit")}
          />
        </Panel>
      )}
    </>
  );
}
export function Goals({
  data,
  open,
  create,
}: {
  data: Snapshot;
  open: (i: Item) => void;
  create: (s: string) => void;
}) {
  const goals = data.items.filter((i) =>
    ["goal", "savings_goal", "project", "plan"].includes(i.type),
  );
  return (
    <>
      <div className="page-title">
        <div>
          <div className="eyebrow">UMA DIREÇÃO PARA SEUS PLANOS</div>
          <h1>Metas e projetos</h1>
          <p>Conecte etapas, tarefas e ideias.</p>
        </div>
        <div className="actions">
          <Button onClick={() => create("project")}>
            <Folder size={15} />
            Projeto
          </Button>
          <Button kind="primary" onClick={() => create("goal")}>
            <Plus size={15} />
            Meta
          </Button>
        </div>
      </div>
      <div className="goal-grid">
        {goals.map((g) => {
          const tasks = data.items.filter(
            (i) =>
              i.type === "task" &&
              (i.parentId === g.id || i.fields.linkedId === g.id),
          );
          const notes = data.items.filter(
            (i) =>
              i.type === "note" &&
              (i.parentId === g.id || i.fields.linkedId === g.id),
          );
          const target = Number(g.fields.target || g.fields.amount || 0),
            progress = Number(g.fields.progress || g.fields.saved || 0);
          const rate =
            target > 0
              ? (progress / target) * 100
              : tasks.length
                ? (tasks.filter((i) => i.done).length / tasks.length) * 100
                : 0;
          return (
            <Panel
              key={g.id}
              title={g.title}
              subtitle={
                labels[g.type] + " · " + dateLabel(g.fields.deadline || g.date)
              }
              action={<Button onClick={() => open(g)}>Editar</Button>}
            >
              <p>
                {g.notes.slice(0, 200) ||
                  "Conecte tarefas e notas para dar o próximo passo."}
              </p>
              <div className="progress">
                <i style={{ width: Math.min(100, rate) + "%" }} />
              </div>
              <p className="muted small">
                {Math.round(rate)}% · {tasks.filter((t) => t.done).length}/
                {tasks.length} tarefas · {notes.length} notas
              </p>
              {tasks.slice(0, 4).map((t) => (
                <RecordRow key={t.id} item={t} open={open} />
              ))}
              {notes.slice(0, 2).map((n) => (
                <Button key={n.id} onClick={() => open(n)}>
                  <Link2 size={14} />
                  {n.title}
                </Button>
              ))}
            </Panel>
          );
        })}
      </div>
      {!goals.length && (
        <Panel>
          <Empty
            title="O que você quer construir?"
            onAdd={() => create("goal")}
          />
        </Panel>
      )}
    </>
  );
}
export function Focus({ data }: { data: Snapshot }) {
  const [minutes, setMinutes] = useState(data.preferences.focusMinutes || "25");
  const [mode, setMode] = useState("Pomodoro");
  const [title, setTitle] = useState("");
  const [taskId, setTaskId] = useState("");
  const [tick, setTick] = useState(0);
  useEffect(() => {
    const timer = setInterval(() => setTick((v) => v + 1), 1000);
    return () => clearInterval(timer);
  }, []);
  const focus = data.desktop.focus || {};
  const remaining = data.focus.active
    ? focus.paused
      ? focus.remaining
      : Math.max(0, Math.ceil((focus.deadline - Date.now()) / 1000))
    : Number(minutes) * 60;
  const left =
    data.focus.active && focus.mode === "Cronômetro"
      ? focus.seconds - remaining
      : remaining;
  const session = data.items.filter((i) => i.type === "focus");
  return (
    <>
      <div className="page-title">
        <div>
          <div className="eyebrow">PRESENÇA NO QUE VOCÊ ESCOLHEU</div>
          <h1>Foco</h1>
          <p>Um tempo só para você e sua próxima tarefa.</p>
        </div>
        <Button onClick={() => void api("openWindow", "widget-focus")}>
          <ExternalLink size={15} />
          Overlay
        </Button>
      </div>
      <div className="split two-thirds">
        <Panel className="focus-panel">
          <div className="eyebrow">
            {data.focus.active ? data.focus.title : mode}
          </div>
          <div className="focus-clock" aria-live="off" data-tick={tick}>
            {String(Math.floor(left / 60)).padStart(2, "0")}
            <span>:</span>
            {String(left % 60).padStart(2, "0")}
          </div>
          <p className="muted">
            {data.focus.active
              ? "Sua sessão fica protegida ao fechar a janela."
              : "Escolha uma intenção e comece."}
          </p>
          {data.focus.active ? (
            <div className="actions center">
              <Button kind="primary" onClick={() => void api("focusPause")}>
                {data.focus.paused ? <Play size={16} /> : <Pause size={16} />}{" "}
                {data.focus.paused ? "Retomar" : "Pausar"}
              </Button>
              <Button onClick={() => void api("focusStop")}>
                <Square size={15} />
                Encerrar e registrar
              </Button>
            </div>
          ) : (
            <>
              <div className="form-grid">
                <Field
                  label="Modo"
                  value={mode}
                  onChange={(v) => {
                    setMode(v);
                    if (v === "Pausa")
                      setMinutes(data.preferences.breakMinutes || "5");
                  }}
                  options={[
                    "Pomodoro",
                    "Cronômetro",
                    "Personalizado",
                    "Pausa",
                  ].map((v) => ({ label: v, value: v }))}
                />
                <Field
                  label="Duração • minutos"
                  value={minutes}
                  onChange={setMinutes}
                  type="number"
                />
                <Field
                  label="Intenção da sessão"
                  value={title}
                  onChange={setTitle}
                  placeholder="Em que você quer se concentrar?"
                />
                <Field
                  label="Tarefa relacionada"
                  value={taskId}
                  onChange={setTaskId}
                  options={[
                    { value: "", label: "Sem tarefa" },
                    ...data.items
                      .filter((i) => i.type === "task" && !i.done)
                      .map((i) => ({ value: i.id, label: i.title })),
                  ]}
                />
              </div>
              <Button
                kind="primary"
                onClick={() =>
                  void api("focusStart", {
                    minutes: Number(minutes),
                    title:
                      title ||
                      data.items.find((i) => i.id === taskId)?.title ||
                      "Tempo de foco",
                    taskId,
                    mode,
                  })
                }
              >
                <Play size={16} />
                Começar sessão
              </Button>
            </>
          )}
        </Panel>
        <Panel
          title="Seu tempo investido"
          subtitle="Sessões registradas neste ecossistema"
        >
          <p className="big-number">
            {Math.round(
              session.reduce((n, i) => n + Number(i.fields.minutes || 0), 0),
            )}
            <small> min</small>
          </p>
          <p>{session.length} sessões registradas</p>
          <hr />
          {session.slice(0, 10).map((i) => (
            <div className="timeline-entry" key={i.id}>
              <span>{dateLabel(i.date)}</span>
              <div>
                <strong>{i.title}</strong>
                <small>{i.fields.minutes} minutos</small>
              </div>
            </div>
          ))}
          {!session.length && (
            <p className="muted">Seu histórico começa na primeira sessão.</p>
          )}
        </Panel>
      </div>
    </>
  );
}
