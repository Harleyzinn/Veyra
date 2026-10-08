import { FocusAnalytics } from "./Workspaces";
import { expanded, status } from "../shared/finance";
import { habitStats } from "../shared/productivity";
import React, { useEffect, useState } from "react";
import {
  Plus,
  Columns3,
  List,
  Check,
  Play,
  Pause,
  Square,
  Folder,
  Link2,
  ExternalLink,
} from "lucide-react";
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
              : filter === "Semana"
                ? !i.done && i.date >= today() && i.date <= addDays(today(), 6)
                : filter === "Atrasadas"
                  ? !i.done && !!i.date && i.date < today()
                  : filter === "Próximas"
                    ? !i.done && i.date > today()
                    : filter === "Inbox"
                      ? !i.done && !i.date
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
          "Semana",
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
    if (!data.focus.active || data.focus.paused) return;
    const timer = setInterval(() => {
      if (!document.hidden) setTick((v) => v + 1);
    }, 1000);
    return () => clearInterval(timer);
  }, [data.focus.active, data.focus.paused]);
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
      <FocusAnalytics data={data} />
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
