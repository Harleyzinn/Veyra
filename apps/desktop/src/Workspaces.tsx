import React, { useEffect, useState } from "react";
import {
  ArrowRight,
  Plus,
  Play,
  Check,
  Inbox,
  Folder,
  ShieldCheck,
  RefreshCw,
  Bell,
  Copy,
  CalendarDays,
} from "lucide-react";
import {
  Item,
  Snapshot,
  today,
  addDays,
  createItem,
  money,
  amount,
  safe,
  parseMinor,
} from "../shared/model";
import {
  dailySummary,
  linkedItems,
  taskBlocked,
  paymentAgenda,
} from "../shared/platform";
import { active, settled, currency } from "../shared/finance";
import { reportRows } from "../shared/reporting";
import {
  api,
  Button,
  Panel,
  Empty,
  RecordRow,
  Field,
  Modal,
  dateLabel,
  labels,
} from "./ui";

type Props = {
  page: string;
  data: Snapshot;
  open: (i: Item) => void;
  create: (type: string) => void;
  choose: (i: Item) => void;
  go: (page: string) => void;
};
export default function Workspaces(props: Props) {
  const { page } = props;
  if (page === "inbox") return <InboxCenter {...props} />;
  if (page === "projects") return <Projects {...props} />;
  if (page === "planner") return <Planner {...props} />;
  if (page === "review") return <Review {...props} />;
  if (page === "automations") return <Automations {...props} />;
  if (page === "templates") return <Templates {...props} />;
  if (page === "sync" || page === "diagnostics")
    return <SyncCenter {...props} />;
  if (page === "notifications") return <Notifications {...props} />;
  return <Library {...props} />;
}
function Heading({
  label,
  title,
  detail,
  action,
}: {
  label: string;
  title: string;
  detail: string;
  action?: React.ReactNode;
}) {
  return (
    <div className="page-title">
      <div>
        <div className="eyebrow">{label}</div>
        <h1>{title}</h1>
        <p>{detail}</p>
      </div>
      {action}
    </div>
  );
}
function InboxCenter({ data, create, open }: Props) {
  const [converting, setConverting] = useState<Item | null>(null),
    [type, setType] = useState("task"),
    [value, setValue] = useState(""),
    [error, setError] = useState("");
  const items = data.items.filter((i) => i.type === "inbox" && !i.done);
  return (
    <>
      <Heading
        label="CAPTURE PRIMEIRO"
        title="Caixa de entrada"
        detail="Um lugar temporário para pensamentos, ideias e pendências."
        action={
          <Button kind="primary" onClick={() => create("inbox")}>
            <Plus size={16} />
            Capturar ideia
          </Button>
        }
      />
      <div className="inbox-list">
        {items.map((i) => (
          <div className="inbox-entry" key={i.id}>
            <Inbox size={21} />
            <button onClick={() => open(i)}>
              <strong>{i.title}</strong>
              <p>{i.notes.slice(0, 140) || "Organize quando fizer sentido."}</p>
            </button>
            <Button
              onClick={() => {
                setConverting(i);
                setValue("");
                setError("");
              }}
            >
              Organizar
              <ArrowRight size={15} />
            </Button>
          </div>
        ))}
        {!items.length && (
          <Empty
            title="A cabeça pode ficar mais leve"
            detail="Capture agora e escolha depois se é tarefa, nota, evento ou gasto."
            onAdd={() => create("inbox")}
          />
        )}
      </div>
      {converting && (
        <Modal title="Organizar captura" onClose={() => setConverting(null)}>
          <div className="form-body">
            <h3>{converting.title}</h3>
            <Field
              label="Destino"
              value={type}
              onChange={setType}
              options={["task", "note", "expense", "event", "project"].map(
                (t) => ({ value: t, label: labels[t] || t }),
              )}
            />
            {type === "expense" && (
              <Field
                label="Valor"
                value={value}
                onChange={setValue}
                placeholder="0,00"
              />
            )}
            <p className="muted">
              A captura original é preservada e marcada como organizada. O novo
              registro fica vinculado a ela.
            </p>
            {error && <p className="error">{error}</p>}
            <Button
              kind="primary"
              onClick={() =>
                void api("organizeInbox", {
                  id: converting.id,
                  type,
                  amount: value,
                })
                  .then(() => setConverting(null))
                  .catch((e) => setError(e.message))
              }
            >
              Confirmar organização
            </Button>
          </div>
        </Modal>
      )}
    </>
  );
}
function Projects({ data, open, choose }: Props) {
  const projects = data.items.filter((i) => i.type === "project"),
    [selected, setSelected] = useState(projects[0]?.id || "");
  const project = projects.find((i) => i.id === selected),
    related = project ? linkedItems(data.items, project.id) : [],
    tasks = related.filter((i) => i.type === "task"),
    done = tasks.filter((i) => i.done).length;
  return (
    <>
      <Heading
        label="CADA PASSO TEM UM CONTEXTO"
        title="Projetos"
        detail="Tarefas, conhecimento, datas e foco trabalhando juntos."
        action={
          <Button kind="primary" onClick={() => choose(createItem("project"))}>
            <Plus size={16} />
            Novo projeto
          </Button>
        }
      />
      <div className="project-workspace">
        <aside>
          {projects.map((p) => (
            <button
              key={p.id}
              className={p.id === selected ? "active" : ""}
              onClick={() => setSelected(p.id)}
            >
              <Folder size={17} />
              {p.title}
            </button>
          ))}
          {!projects.length && (
            <Empty
              title="Um objetivo maior começa aqui"
              onAdd={() => choose(createItem("project"))}
            />
          )}
        </aside>
        <section>
          {project ? (
            <>
              <div className="project-overview">
                <div>
                  <span className="eyebrow">PROJETO</span>
                  <h2>{project.title}</h2>
                  <p>
                    {project.notes ||
                      "Adicione tarefas e notas para construir o próximo passo."}
                  </p>
                </div>
                <Button onClick={() => open(project)}>Editar projeto</Button>
              </div>
              <div className="progress-bar">
                <i
                  style={{
                    width:
                      (tasks.length ? (done / tasks.length) * 100 : 0) + "%",
                  }}
                />
              </div>
              <div className="project-stats">
                <span>
                  {done}/{tasks.length} tarefas
                </span>
                <span>
                  {related.filter((i) => i.type === "note").length} notas
                </span>
                <span>
                  {Math.round(
                    data.items
                      .filter(
                        (i) =>
                          i.type === "focus" &&
                          (i.parentId === project.id ||
                            tasks.some((t) => t.id === i.parentId)) &&
                          i.fields.mode !== "Pausa",
                      )
                      .reduce((n, i) => n + Number(i.fields.minutes || 0), 0),
                  )}{" "}
                  min de foco
                </span>
                <span>
                  {project.fields.deadline
                    ? "Prazo " + dateLabel(project.fields.deadline)
                    : "Sem prazo definido"}
                </span>
              </div>
              <div className="actions">
                {["task", "note", "event", "goal"].map((type) => (
                  <Button
                    key={type}
                    onClick={() =>
                      choose(
                        createItem(type, {
                          parentId: project.id,
                          date: type === "note" ? "" : today(),
                        }),
                      )
                    }
                  >
                    <Plus size={14} />
                    {labels[type] || type}
                  </Button>
                ))}
              </div>
              <Panel title="Próximos passos">
                {tasks.map((t) => (
                  <div className="project-task" key={t.id}>
                    <RecordRow item={t} open={open} />
                    <Button
                      disabled={t.done || !!taskBlocked(t, data.items).length}
                      onClick={() =>
                        void api("focusStart", {
                          minutes: 25,
                          title: t.title,
                          taskId: t.id,
                          mode: "Pomodoro",
                        })
                      }
                    >
                      <Play size={14} />
                      Focar
                    </Button>
                  </div>
                ))}
                {!tasks.length && (
                  <Empty
                    title="Escolha o primeiro passo"
                    onAdd={() =>
                      choose(createItem("task", { parentId: project.id }))
                    }
                  />
                )}
              </Panel>
              <Panel title="Conhecimento e planejamento">
                {related
                  .filter((i) => i.type !== "task" && i.type !== "focus")
                  .map((i) => (
                    <RecordRow key={i.id} item={i} open={open} />
                  ))}
                {!related.some((i) => !["task", "focus"].includes(i.type)) && (
                  <p className="muted">
                    Notas, metas e eventos vinculados aparecerão aqui.
                  </p>
                )}
              </Panel>
            </>
          ) : (
            <Empty
              title="Selecione um projeto"
              detail="Um único contexto para o que você quer realizar."
            />
          )}
        </section>
      </div>
    </>
  );
}
function Planner({ data, open, choose }: Props) {
  const [day, setDay] = useState(data.desktop.plannerDate || today()),
    [task, setTask] = useState(""),
    [time, setTime] = useState("09:00"),
    [error, setError] = useState("");
  useEffect(() => {
    if (data.desktop.plannerDate) setDay(data.desktop.plannerDate);
  }, [data.desktop.plannerDate]);
  const unscheduled = data.items.filter(
    (i) => i.type === "task" && !i.done && (!i.fields.time || i.date !== day),
  );
  const appointments = data.items
    .filter(
      (i) => ["task", "event"].includes(i.type) && i.date === day && !i.done,
    )
    .sort((a, b) =>
      (a.fields.time || "23:59").localeCompare(b.fields.time || "23:59"),
    );
  const schedule = (id: string, slot: string) => {
    const i = data.items.find(
      (i) => i.id === id && i.type === "task" && !i.done,
    );
    if (i)
      void api("save", {
        base: i,
        item: { ...i, date: day, fields: { ...i.fields, time: slot } },
      }).catch((e) => setError(e.message));
  };
  return (
    <>
      <Heading
        label="DÊ ESPAÇO AO QUE IMPORTA"
        title="Planejar meu dia"
        detail="Arraste uma tarefa para um horário ou agende pelo teclado."
        action={
          <Button onClick={() => choose(createItem("event", { date: day }))}>
            <Plus size={16} />
            Evento
          </Button>
        }
      />
      <div className="planner-toolbar">
        <Field label="Dia" value={day} onChange={setDay} type="date" />
        <Field
          label="Tarefa"
          value={task}
          onChange={setTask}
          options={[
            { value: "", label: "Escolher tarefa" },
            ...unscheduled.map((i) => ({ value: i.id, label: i.title })),
          ]}
        />
        <Field label="Horário" value={time} onChange={setTime} type="time" />
        <Button disabled={!task} onClick={() => schedule(task, time)}>
          Agendar
        </Button>
      </div>
      {error && <p className="error">{error}</p>}
      <div className="planner-layout">
        <Panel
          title="Tarefas disponíveis"
          subtitle="O planejamento muda data e horário; não duplica a tarefa."
        >
          {unscheduled.slice(0, 60).map((i) => (
            <div
              key={i.id}
              draggable
              onDragStart={(e) =>
                e.dataTransfer.setData("application/veyra-task", i.id)
              }
            >
              <RecordRow item={i} open={open} />
            </div>
          ))}
          {!unscheduled.length && <Empty title="Tudo tem seu espaço" />}
        </Panel>
        <Panel title={dateLabel(day)} subtitle="Agenda do dia">
          <div className="planner-slots">
            {Array.from(
              { length: 18 },
              (_, n) => String(n + 6).padStart(2, "0") + ":00",
            ).map((slot) => (
              <div
                key={slot}
                className="planner-slot"
                onDragOver={(e) => e.preventDefault()}
                onDrop={(e) => {
                  e.preventDefault();
                  schedule(
                    e.dataTransfer.getData("application/veyra-task"),
                    slot,
                  );
                }}
              >
                <time>{slot}</time>
                <div>
                  {appointments
                    .filter(
                      (i) =>
                        (i.fields.time || "").slice(0, 2) === slot.slice(0, 2),
                    )
                    .map((i) => (
                      <button key={i.id} onClick={() => open(i)}>
                        <strong>{i.title}</strong>
                        <span>
                          {i.fields.time} · {labels[i.type]}
                        </span>
                      </button>
                    ))}
                </div>
              </div>
            ))}
          </div>
          <h4>Sem horário</h4>
          {appointments
            .filter((i) => !i.fields.time)
            .map((i) => (
              <RecordRow key={i.id} item={i} open={open} />
            ))}
        </Panel>
      </div>
    </>
  );
}
function Review({ data, open, go }: Props) {
  const [period, setPeriod] = useState("week");
  const end = today(),
    start = period === "day" ? end : addDays(end, -6),
    items = data.items.filter((i) => i.date >= start && i.date <= end);
  const tasks = items.filter((i) => i.type === "task"),
    checkins = items.filter((i) => i.type === "checkin"),
    focus = items
      .filter((i) => i.type === "focus" && i.fields.mode !== "Pausa")
      .reduce((n, i) => n + Number(i.fields.minutes || 0), 0);
  const spending = items
    .filter(
      (i) =>
        i.type === "expense" &&
        active(i) &&
        currency(i) === data.finance.currency &&
        i.fields.paymentType !== "card_payment" &&
        (settled(i) || i.fields.card),
    )
    .reduce((n, i) => safe(n + amount(i)), 0);
  const hidden =
    data.preferences.financeHidden === "yes" ||
    data.preferences.financeHideValues === "yes";
  const summary = dailySummary(
    data.items,
    addDays(end, 1),
    data.finance.currency,
  );
  return (
    <>
      <Heading
        label="OLHAR PARA TRÁS. ESCOLHER O PRÓXIMO PASSO."
        title={period === "day" ? "Seu dia" : "Revisão da semana"}
        detail="Informação para planejar, sem cobrança ou comparação."
      />
      <div className="tabs">
        {[
          ["day", "Hoje"],
          ["week", "Últimos 7 dias"],
        ].map(([p, t]) => (
          <button
            key={p}
            className={period === p ? "active" : ""}
            onClick={() => setPeriod(p)}
          >
            {t}
          </button>
        ))}
      </div>
      <div className="review-stats">
        <div>
          <small>Tarefas concluídas</small>
          <strong>
            {tasks.filter((i) => i.done).length}/{tasks.length}
          </strong>
        </div>
        <div>
          <small>Check-ins de hábitos</small>
          <strong>{checkins.length}</strong>
        </div>
        <div>
          <small>Tempo focado</small>
          <strong>{Math.round(focus)} min</strong>
        </div>
        <div>
          <small>Gastos registrados</small>
          <strong>
            {hidden ? "••••" : money(spending, data.finance.currency)}
          </strong>
        </div>
      </div>
      <div className="split">
        <Panel
          title="O que ficou para depois"
          subtitle="Você pode reorganizar essas tarefas."
        >
          {data.items
            .filter(
              (i) => i.type === "task" && !i.done && i.date && i.date <= end,
            )
            .slice(0, 15)
            .map((i) => (
              <RecordRow key={i.id} item={i} open={open} />
            ))}
          <Button onClick={() => go("planner")}>
            Planejar próximo dia
            <ArrowRight size={15} />
          </Button>
        </Panel>
        <Panel title="O próximo passo">
          <p>
            {summary.tasks.length} tarefas e{" "}
            {summary.events.filter((i) => i.date === addDays(end, 1)).length}{" "}
            eventos amanhã.
          </p>
          <p>
            {summary.bills.length} contas previstas para os próximos sete dias.
          </p>
          {data.items
            .filter((i) => ["project", "goal"].includes(i.type) && !i.done)
            .slice(0, 6)
            .map((i) => (
              <RecordRow key={i.id} item={i} open={open} />
            ))}
          <Button onClick={() => go("finance")}>Revisar finanças</Button>
        </Panel>
      </div>
    </>
  );
}
function Automations({ data, open }: Props) {
  const [editing, setEditing] = useState(false),
    [title, setTitle] = useState(""),
    [trigger, setTrigger] = useState("expense"),
    [action, setAction] = useState("alert"),
    [category, setCategory] = useState(""),
    [minimum, setMinimum] = useState("100"),
    [weekday, setWeekday] = useState("1"),
    [message, setMessage] = useState(""),
    [error, setError] = useState("");
  const rules = data.items.filter((i) => i.type === "automation");
  const save = () => {
    try {
      if (!title.trim() || !message.trim())
        throw Error("Preencha nome e mensagem.");
      if (trigger === "expense") parseMinor(minimum, data.finance.currency);
      void api("save", {
        item: createItem("automation", {
          title: title.trim(),
          date: "",
          fields: {
            desktopEnabled: "yes",
            trigger,
            action: trigger === "expense" ? "alert" : action,
            category,
            minimum,
            currency: data.finance.currency,
            weekday,
            message,
          },
        }),
      })
        .then(() => setEditing(false))
        .catch((e) => setError(e.message));
    } catch (e) {
      setError((e as Error).message);
    }
  };
  return (
    <>
      <Heading
        label="QUANDO · SE · ENTÃO"
        title="Automações"
        detail="Regras locais e previsíveis. Nenhum pagamento automático ou código externo."
        action={
          <Button
            kind="primary"
            onClick={() => {
              setEditing(true);
              setError("");
            }}
          >
            <Plus size={16} />
            Nova automação
          </Button>
        }
      />
      <p className="muted">
        Regras só executam enquanto o Veyra estiver aberto. Criações semanais
        têm identificador único por dia; alertas financeiros exigem a mesma
        moeda.
      </p>
      <div className="automation-list">
        {rules.map((rule) => (
          <Panel
            key={rule.id}
            title={rule.title}
            action={
              <Button
                onClick={() =>
                  void api("save", {
                    base: rule,
                    item: {
                      ...rule,
                      fields: {
                        ...rule.fields,
                        desktopEnabled:
                          rule.fields.desktopEnabled === "yes" ? "no" : "yes",
                      },
                    },
                  })
                }
              >
                {rule.fields.desktopEnabled === "yes"
                  ? "Desativar"
                  : "Ativar no PC"}
              </Button>
            }
          >
            <p>
              <strong>QUANDO</strong>{" "}
              {rule.fields.trigger === "weekday"
                ? "No dia da semana escolhido"
                : "Um gasto novo for criado"}
            </p>
            <p>
              <strong>SE</strong> {rule.fields.category || "Qualquer categoria"}
              {rule.fields.minimum
                ? " · valor mínimo " +
                  rule.fields.minimum +
                  " " +
                  (rule.fields.currency || "BRL")
                : ""}
            </p>
            <p>
              <strong>ENTÃO</strong>{" "}
              {rule.fields.action === "task"
                ? "Criar tarefa"
                : "Mostrar alerta"}
              : {rule.fields.message || "Configure esta regra"}
            </p>
            <Button onClick={() => open(rule)}>Editar registro</Button>
          </Panel>
        ))}
        {!rules.length && (
          <Empty
            title="Deixe o Veyra lembrar pequenas coisas"
            detail="Crie um alerta de gasto ou uma tarefa semanal."
            onAdd={() => setEditing(true)}
          />
        )}
      </div>
      {editing && (
        <Modal title="Nova automação" onClose={() => setEditing(false)}>
          <div className="form-body">
            <Field label="Nome" value={title} onChange={setTitle} />
            <Field
              label="QUANDO"
              value={trigger}
              onChange={setTrigger}
              options={[
                { value: "expense", label: "Gasto novo criado" },
                { value: "weekday", label: "Dia da semana" },
              ]}
            />
            {trigger === "expense" ? (
              <>
                <Field
                  label="SE: categoria (opcional)"
                  value={category}
                  onChange={setCategory}
                />
                <Field
                  label="SE: valor mínimo"
                  value={minimum}
                  onChange={setMinimum}
                />
              </>
            ) : (
              <Field
                label="Dia da semana"
                value={weekday}
                onChange={setWeekday}
                options={[
                  "Domingo",
                  "Segunda",
                  "Terça",
                  "Quarta",
                  "Quinta",
                  "Sexta",
                  "Sábado",
                ].map((label, n) => ({ value: String(n), label }))}
              />
            )}
            <Field
              label="ENTÃO"
              value={trigger === "expense" ? "alert" : action}
              onChange={setAction}
              options={[
                { value: "alert", label: "Mostrar alerta" },
                ...(trigger === "weekday"
                  ? [{ value: "task", label: "Criar tarefa" }]
                  : []),
              ]}
            />
            <Field
              label="Mensagem ou título da tarefa"
              value={message}
              onChange={setMessage}
            />
            {error && <p className="error">{error}</p>}
            <Button kind="primary" onClick={save}>
              Salvar regra
            </Button>
          </div>
        </Modal>
      )}
    </>
  );
}
function Templates({ data, open }: Props) {
  const [editing, setEditing] = useState(false),
    [title, setTitle] = useState(""),
    [type, setType] = useState("task"),
    [notes, setNotes] = useState(""),
    [lines, setLines] = useState(""),
    [error, setError] = useState("");
  const templates = data.items.filter((i) => i.type === "template");
  return (
    <>
      <Heading
        label="COMECE UM PASSO À FRENTE"
        title="Modelos"
        detail="Estruturas reutilizáveis de tarefa, nota ou projeto com checklist."
        action={
          <Button kind="primary" onClick={() => setEditing(true)}>
            <Plus size={16} />
            Criar modelo
          </Button>
        }
      />
      <div className="template-gallery">
        {templates.map((t) => (
          <Panel
            key={t.id}
            title={t.title}
            subtitle={labels[t.fields.targetType || "task"]}
          >
            <pre>
              {(
                t.notes ||
                t.fields.lines ||
                "Modelo sem conteúdo adicional."
              ).slice(0, 300)}
            </pre>
            <div className="actions">
              <Button onClick={() => open(t)}>Editar</Button>
              <Button
                kind="primary"
                onClick={() =>
                  void api("applyTemplate", { id: t.id }).then(open)
                }
              >
                Usar modelo
              </Button>
            </div>
          </Panel>
        ))}
        {!templates.length && (
          <Empty
            title="Sua próxima reunião já pode ter uma estrutura"
            onAdd={() => setEditing(true)}
          />
        )}
      </div>
      {editing && (
        <Modal title="Criar modelo" onClose={() => setEditing(false)}>
          <div className="form-body">
            <Field label="Nome" value={title} onChange={setTitle} />
            <Field
              label="Tipo"
              value={type}
              onChange={setType}
              options={["task", "note", "project"].map((t) => ({
                value: t,
                label: labels[t] || t,
              }))}
            />
            <Field
              label="Conteúdo Markdown"
              value={notes}
              onChange={setNotes}
              type="textarea"
            />
            {type === "project" && (
              <Field
                label="Tarefas, uma por linha"
                value={lines}
                onChange={setLines}
                type="textarea"
              />
            )}
            {error && <p className="error">{error}</p>}
            <Button
              kind="primary"
              onClick={() =>
                void api("save", {
                  item: createItem("template", {
                    title,
                    notes,
                    date: "",
                    fields: { targetType: type, lines },
                  }),
                })
                  .then(() => setEditing(false))
                  .catch((e) => setError(e.message))
              }
            >
              Salvar modelo
            </Button>
          </div>
        </Modal>
      )}
    </>
  );
}
function SyncCenter({ page, data, go, choose, open }: Props) {
  const [report, setReport] = useState<any>(null),
    [drafts, setDrafts] = useState<any[]>([]),
    [error, setError] = useState("");
  const refresh = () => {
    void api("diagnostics")
      .then(setReport)
      .catch((e) => setError(e.message));
    void api("drafts").then(setDrafts);
  };
  useEffect(refresh, [data.sync.status, data.uid]);
  return (
    <>
      <Heading
        label="VISIBILIDADE SEM EXPOR SUA CONTA"
        title={page === "diagnostics" ? "Veyra Diagnostics" : "Sync Center"}
        detail="Acompanhe fila, dispositivos, armazenamento e recuperação."
        action={
          <Button onClick={refresh}>
            <RefreshCw size={16} />
            Atualizar diagnóstico
          </Button>
        }
      />
      <div className="split">
        <Panel title="Sua conexão">
          <p>
            {data.user
              ? "Conta conectada: " + data.user.email
              : "Espaço visitante, sem envio à nuvem"}
          </p>
          <p>
            Status: {data.sync.status} · {data.sync.pending} alterações
            pendentes · {data.sync.conflicts} conflitos
          </p>
          <p>
            Última confirmação:{" "}
            {data.sync.lastSync
              ? new Date(data.sync.lastSync).toLocaleString("pt-BR")
              : "Ainda não sincronizado"}
          </p>
          {data.sync.error && <p className="error">{data.sync.error}</p>}
          <div className="actions">
            <Button kind="primary" onClick={() => void api("sync")}>
              Sincronizar agora
            </Button>
            <Button onClick={() => go("backup")}>Backup e conflitos</Button>
          </div>
          <p className="muted">
            Sem rede, as alterações ficam neste PC. A fila só sai depois da
            confirmação do servidor.
          </p>
        </Panel>
        <Panel title="Dispositivos">
          {data.items
            .filter((i) => i.type === "device")
            .map((i) => (
              <div className="privacy-row" key={i.id}>
                <ShieldCheck />
                <div>
                  <strong>{i.title}</strong>
                  <p>
                    {i.fields.platform} · última atividade{" "}
                    {new Date(
                      Number(i.fields.lastSeen) || i.createdAt,
                    ).toLocaleString("pt-BR")}
                  </p>
                </div>
              </div>
            ))}
          {!data.items.some((i) => i.type === "device") && (
            <p className="muted">
              Dispositivos aparecem após entrar e sincronizar.
            </p>
          )}
        </Panel>
      </div>
      <Panel
        title="Rascunhos recuperáveis"
        subtitle="Conteúdo local criptografado. O rascunho não é enviado antes de salvar."
      >
        {drafts.map((d) => (
          <div className="inbox-entry" key={d.item.id}>
            <div>
              <strong>
                {d.item.title || "Sem título"} ·{" "}
                {labels[d.item.type] || d.item.type}
              </strong>
              <p>{new Date(d.at).toLocaleString("pt-BR")}</p>
            </div>
            <Button
              onClick={() =>
                choose(
                  createItem(d.item.type, {
                    ...d.item,
                    id: crypto.randomUUID(),
                    createdAt: Date.now(),
                    title:
                      (d.item.title || "Sem título").slice(0, 175) +
                      " (recuperada)",
                  }),
                )
              }
            >
              Recuperar como cópia
            </Button>
            <Button
              onClick={() => void api("draftDelete", d.item.id).then(refresh)}
            >
              Dispensar rascunho
            </Button>
          </div>
        ))}
        {!drafts.length && (
          <p className="muted">Nenhum rascunho aguardando recuperação.</p>
        )}
      </Panel>
      <Panel
        title="Saúde do aplicativo"
        action={
          <Button onClick={() => void api("diagnosticsCopy")}>
            <Copy size={15} />
            Copiar diagnóstico seguro
          </Button>
        }
      >
        {error && <p className="error">{error}</p>}
        {report && (
          <>
            <div className="diagnostic-grid">
              <span>
                Banco
                <strong>
                  {report.databaseHealthy ? "Íntegro" : "Verificar"}
                </strong>
              </span>
              <span>
                Inicialização<strong>{report.startupMs} ms</strong>
              </span>
              <span>
                Cache
                <strong>{(report.cacheBytes / 1048576).toFixed(2)} MB</strong>
              </span>
              <span>
                Memória dos processos
                <strong>
                  {(
                    report.processes.reduce(
                      (n: number, p: any) => n + p.workingSetKB,
                      0,
                    ) / 1024
                  ).toFixed(0)}{" "}
                  MB
                </strong>
              </span>
              <span>
                Requisições Firestore de leitura
                <strong>{report.networkRequests.firestoreReads}</strong>
              </span>
              <span>
                Requisições Firestore de escrita
                <strong>{report.networkRequests.firestoreWrites}</strong>
              </span>
            </div>
            <p className="muted small">
              Contadores desta execução, não equivalem a documentos faturados. O
              diagnóstico copiado exclui e-mail, UID, títulos, notas, caminhos e
              tokens.
            </p>
            <details>
              <summary>Versões e processos</summary>
              <pre>{JSON.stringify(report, null, 2)}</pre>
            </details>
          </>
        )}
      </Panel>
      <Panel title="Atividade recente">
        <Activity open={open} />
      </Panel>
    </>
  );
}
function Activity({ open }: { open: (i: Item) => void }) {
  const [events, setEvents] = useState<any[]>([]);
  useEffect(() => {
    void api("activity").then(setEvents);
  }, []);
  return (
    <>
      {events.slice(0, 25).map((e) => (
        <button
          className="activity-line"
          key={e.id}
          onClick={() =>
            void api("item", e.itemId).then((i) => {
              if (i) open(i);
            })
          }
        >
          <span>{new Date(e.at).toLocaleString("pt-BR")}</span>
          <strong>{e.title}</strong>
          <small>
            {e.action === "remote" ? "Outro dispositivo" : "Este PC"}
          </small>
        </button>
      ))}
      {!events.length && (
        <p className="muted">As alterações locais e remotas aparecerão aqui.</p>
      )}
    </>
  );
}
function Notifications({ data, open }: Props) {
  const [items, setItems] = useState<any[]>([]);
  const refresh = () => void api("notifications").then(setItems);
  useEffect(() => {
    refresh();
  }, [data.items, data.sync.status]);
  const act = (action: string, id?: string) =>
    void api("notificationAction", { action, id }).then(refresh);
  return (
    <>
      <Heading
        label="LEMBRETES COM CONTEXTO"
        title="Notificações"
        detail="Tarefas, contas, eventos e automações em uma única central."
        action={
          <Button onClick={() => act("readAll")}>
            <Check size={16} />
            Marcar todas como lidas
          </Button>
        }
      />
      <label className="toggle">
        <input
          type="checkbox"
          checked={!!data.desktop.doNotDisturb}
          onChange={(e) =>
            void api("desktop", { doNotDisturb: e.target.checked })
          }
        />
        Não Perturbe
      </label>
      <label className="toggle">
        <input
          type="checkbox"
          checked={!!data.desktop.quietFocus}
          onChange={(e) =>
            void api("desktop", { quietFocus: e.target.checked })
          }
        />
        Silenciar avisos do Windows durante foco
      </label>
      {items.map((n) => (
        <div
          className={"notification-entry " + (n.read ? "read" : "")}
          key={n.id}
        >
          <Bell size={18} />
          <button
            onClick={() => {
              act("read", n.id);
              if (n.itemId)
                void api("item", n.itemId).then((i) => {
                  if (i) open(i);
                });
            }}
          >
            <strong>{n.title}</strong>
            <p>{new Date(n.at).toLocaleString("pt-BR")}</p>
          </button>
          <Button onClick={() => act("dismiss", n.id)}>Dispensar</Button>
        </div>
      ))}
      {!items.length && (
        <Empty
          title="Tudo tranquilo por aqui"
          detail="Lembretes e alertas de regras aparecerão quando ocorrerem."
        />
      )}
    </>
  );
}
function Library({ page, data, open }: Props) {
  const [recent, setRecent] = useState<Item[]>([]);
  useEffect(() => {
    if (page === "recent") void api("recent").then(setRecent);
  }, [page, data.uid]);
  const rows =
    page === "favorites" ? data.items.filter((i) => i.favorite) : recent;
  return (
    <>
      <Heading
        label="VOLTE AO QUE IMPORTA"
        title={page === "favorites" ? "Favoritos" : "Recentes"}
        detail="Seus itens importantes, sem precisar procurar novamente."
      />
      <Panel>
        {rows.map((i) => (
          <RecordRow key={i.id} item={i} open={open} />
        ))}
        {!rows.length && (
          <Empty
            title={
              page === "favorites"
                ? "Marque um registro com a estrela"
                : "Os próximos itens abertos aparecerão aqui"
            }
          />
        )}
      </Panel>
    </>
  );
}

export function CommandHome({
  data,
  open,
  go,
  cash,
}: {
  data: Snapshot;
  open: (i: Item) => void;
  go: (p: string) => void;
  cash: (n: number) => string;
}) {
  let summary;
  try {
    summary = dailySummary(data.items, today(), data.finance.currency);
  } catch {
    return (
      <Panel title="Confira seus registros">
        <Button onClick={() => go("finance")}>
          Abrir conferência financeira
        </Button>
      </Panel>
    );
  }
  const payments = paymentAgenda(
    summary.bills,
    data.finance.invoices,
    data.items,
    today(),
    data.finance.currency,
  );
  const first = payments[0];
  const hour = new Date().getHours(),
    next = summary.events.find(
      (i) =>
        i.date > today() ||
        !i.fields.time ||
        i.fields.time >=
          `${String(hour).padStart(2, "0")}:${String(new Date().getMinutes()).padStart(2, "0")}`,
    );
  return (
    <section className="command-home">
      <div className="command-home-primary">
        <span className="eyebrow">SEU PRÓXIMO PASSO</span>
        <h2>
          {summary.overdue.length
            ? `${summary.overdue.length} tarefas precisam de uma nova data`
            : summary.tasks.length
              ? `${summary.tasks.length} tarefas para hoje`
              : "Seu dia tem espaço"}
        </h2>
        <p>
          {summary.habits.length} hábitos ainda disponíveis ·{" "}
          {summary.focusMinutes} minutos de foco hoje
        </p>
        <div className="actions">
          <Button kind="primary" onClick={() => go("planner")}>
            <CalendarDays size={16} />
            Planejar meu dia
          </Button>
          {summary.inbox.length > 0 && (
            <Button onClick={() => go("inbox")}>
              {summary.inbox.length} capturas para organizar
            </Button>
          )}
        </div>
        <small>
          {data.desktop.smartHints === false
            ? ""
            : hour < 12
              ? "Sugestão da manhã: revise a agenda antes de começar."
              : hour < 18
                ? "Sugestão: reserve um bloco de foco para a próxima tarefa."
                : "Sugestão: revise o dia e escolha o primeiro passo de amanhã."}
        </small>
      </div>
      <div className="command-home-facts">
        <button onClick={() => go("finance")}>
          <span>Saldo realizado</span>
          <strong>{cash(data.finance.balance)}</strong>
          <small>Previsão separada no financeiro</small>
        </button>
        <button onClick={() => (next ? open(next) : go("calendar"))}>
          <span>Próximo compromisso</span>
          <strong>{next?.title || "Agenda livre"}</strong>
          <small>
            {next
              ? dateLabel(next.date) +
                (next.fields.time ? " · " + next.fields.time : "")
              : "Abra sua agenda"}
          </small>
        </button>
        <button
          onClick={() =>
            first && data.items.find((i) => i.id === first.itemId)
              ? open(data.items.find((i) => i.id === first.itemId)!)
              : go("finance")
          }
        >
          <span>Contas e faturas próximas</span>
          <strong>
            {payments.length ? first.title : "Nenhuma conta ou fatura prevista"}
          </strong>
          <small>
            {payments.length
              ? `${payments.length} pendências · ${first.overdue ? "vencida " : "primeira "}${dateLabel(first.date)}`
              : "Confira séries e faturas no financeiro"}
          </small>
        </button>
      </div>
    </section>
  );
}
export function ExtraWidget({
  id,
  data,
  open,
  go,
  cash,
}: {
  id: string;
  data: Snapshot;
  open: (i: Item) => void;
  go: (p: string) => void;
  cash: (n: number) => string;
}) {
  let summary;
  try {
    summary = dailySummary(data.items, today(), data.finance.currency);
  } catch {
    return <Empty title="Confira o financeiro antes de continuar" />;
  }
  if (id === "bills") {
    const payments = paymentAgenda(
      summary.bills,
      data.finance.invoices,
      data.items,
      today(),
      data.finance.currency,
    );
    return (
      <Panel
        title="Contas e faturas próximas"
        action={<Button onClick={() => go("finance")}>Ver todas</Button>}
      >
        {payments.slice(0, 5).map((p) => (
          <button
            className="payment-agenda-row"
            key={p.id}
            onClick={() => {
              const item = data.items.find((i) => i.id === p.itemId);
              if (item) open(item);
              else go("finance");
            }}
          >
            <span>
              <strong>{p.title}</strong>
              <small>
                {p.overdue ? "Vencida · " : ""}
                {dateLabel(p.date)}
              </small>
            </span>
            <strong>{cash(p.value)}</strong>
          </button>
        ))}
        {!payments.length && <Empty title="Nenhuma conta ou fatura prevista" />}
      </Panel>
    );
  }
  if (id === "inbox")
    return (
      <Panel
        title="Caixa de entrada"
        action={<Button onClick={() => go("inbox")}>Organizar</Button>}
      >
        {summary.inbox.slice(0, 4).map((i) => (
          <RecordRow key={i.id} item={i} open={open} />
        ))}
        {!summary.inbox.length && <Empty title="Nenhuma captura esperando" />}
      </Panel>
    );
  if (id === "recent-spending")
    return (
      <Panel title="Últimos gastos">
        {data.items
          .filter(
            (i) =>
              i.type === "expense" && i.fields.paymentType !== "card_payment",
          )
          .sort((a, b) => b.createdAt - a.createdAt)
          .slice(0, 4)
          .map((i) => (
            <RecordRow key={i.id} item={i} open={open} />
          ))}
      </Panel>
    );
  const month = today().slice(0, 7),
    tasks = data.items.filter(
      (i) => i.type === "task" && i.date.startsWith(month),
    );
  return (
    <Panel title="Seu mês em andamento">
      <div className="review-stats compact">
        <div>
          <small>Tarefas</small>
          <strong>
            {tasks.filter((i) => i.done).length}/{tasks.length}
          </strong>
        </div>
        <div>
          <small>Despesas do período</small>
          <strong>{cash(data.finance.expense)}</strong>
        </div>
      </div>
      <div className="progress-bar">
        <i
          style={{
            width:
              (tasks.length
                ? (tasks.filter((i) => i.done).length / tasks.length) * 100
                : 0) + "%",
          }}
        />
      </div>
    </Panel>
  );
}

export function Brief({
  data,
  go,
  cash,
}: {
  data: Snapshot;
  go: (page: string) => void;
  cash: (n: number) => string;
}) {
  const summary = dailySummary(data.items, today(), data.finance.currency),
    kind = data.desktop.briefKind || "morning";
  const from = kind === "week" ? addDays(today(), -6) : today();
  const period = data.items.filter((i) => i.date >= from && i.date <= today());
  const tasks = period.filter((i) => i.type === "task");
  const focus = Math.round(
    period
      .filter((i) => i.type === "focus" && i.fields.mode !== "Pausa")
      .reduce((n, i) => n + Number(i.fields.minutes || 0), 0),
  );
  const spending = reportRows(data.items, {
    from,
    to: today(),
    currency: data.finance.currency,
  }).expense;
  const next = dailySummary(
    data.items,
    addDays(today(), 1),
    data.finance.currency,
  );
  const invoices = data.finance.invoices.filter(
    (i) => i.remaining > 0 && i.due >= today() && i.due <= addDays(today(), 7),
  );
  return (
    <div className="brief-panel">
      <div className="eyebrow">
        {kind === "morning"
          ? "MORNING BRIEF"
          : kind === "week"
            ? "REVISÃO DA SEMANA"
            : "SEU DIA"}
      </div>
      <h1>{kind === "morning" ? "Um novo dia." : "Um passo de cada vez."}</h1>
      <div className="brief-lines">
        {kind === "morning" ? (
          <>
            <span>{summary.tasks.length} tarefas hoje</span>
            <span>
              {summary.events.filter((i) => i.date === today()).length} eventos
              hoje
            </span>
            <span>{summary.habits.length} hábitos disponíveis</span>
            <span>
              {summary.bills.length + invoices.length} contas e faturas próximas
            </span>
          </>
        ) : (
          <>
            <span>
              {tasks.filter((i) => i.done).length}/{tasks.length} tarefas
              concluídas
            </span>
            <span>
              {period.filter((i) => i.type === "checkin").length} check-ins de
              hábitos
            </span>
            <span>{focus} minutos de foco no período</span>
            <span>{cash(spending)} em gastos reconhecidos</span>
            <span>
              Amanhã: {next.tasks.length} tarefas e{" "}
              {next.events.filter((i) => i.date === addDays(today(), 1)).length}{" "}
              eventos
            </span>
          </>
        )}
      </div>
      <p className="muted">
        Saldo realizado <strong>{cash(data.finance.balance)}</strong>
      </p>
      <Button
        kind="primary"
        onClick={() => {
          go(kind === "morning" ? "day" : "review");
          void api("closeWindow");
        }}
      >
        Abrir {kind === "morning" ? "meu dia" : "revisão"}
        <ArrowRight size={15} />
      </Button>
    </div>
  );
}
export function FocusAnalytics({ data }: { data: Snapshot }) {
  const [period, setPeriod] = useState("week");
  const from =
    period === "day"
      ? today()
      : period === "month"
        ? today().slice(0, 7) + "-01"
        : addDays(today(), -6);
  const sessions = data.items.filter(
      (i) =>
        i.type === "focus" &&
        i.date >= from &&
        i.date <= today() &&
        i.fields.mode !== "Pausa",
    ),
    groups = new Map<string, number>();
  for (const i of sessions) {
    const task = data.items.find((t) => t.id === i.parentId),
      project = data.items.find(
        (p) =>
          p.type === "project" &&
          (p.id === task?.parentId || p.id === i.parentId),
      );
    const name = project?.title || task?.title || "Sem vínculo";
    groups.set(name, (groups.get(name) || 0) + Number(i.fields.minutes || 0));
  }
  return (
    <Panel
      title="Seu tempo em perspectiva"
      subtitle="Registro para organizar o próximo passo, sem metas de cobrança."
    >
      <div className="tabs">
        {[
          ["day", "Hoje"],
          ["week", "Semana"],
          ["month", "Mês"],
        ].map(([v, t]) => (
          <button
            key={v}
            className={period === v ? "active" : ""}
            onClick={() => setPeriod(v)}
          >
            {t}
          </button>
        ))}
      </div>
      <h2>
        {Math.round(
          sessions.reduce((n, i) => n + Number(i.fields.minutes || 0), 0),
        )}{" "}
        minutos focados
      </h2>
      {[...groups].map(([name, minutes]) => (
        <div className="invoice-row" key={name}>
          <span>{name}</span>
          <strong>{Math.round(minutes)} min</strong>
        </div>
      ))}
      {!sessions.length && (
        <Empty title="Seu histórico começa ao registrar uma sessão" />
      )}
    </Panel>
  );
}
