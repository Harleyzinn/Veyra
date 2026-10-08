import FinanceExtras from "./FinanceExtras";
import React, { useEffect, useState } from "react";
import {
  ArrowDownLeft,
  ArrowUpRight,
  Plus,
  Wallet,
  Eye,
  EyeOff,
  Download,
  Check,
  ArrowRight,
} from "lucide-react";
import {
  Snapshot,
  Item,
  today,
  money,
  amount,
  parseMinor,
} from "../shared/model";
import { active, status, simulate, expanded, isRule } from "../shared/finance";
import {
  api,
  Panel,
  Button,
  Empty,
  Chart,
  RecordRow,
  SearchBox,
  Field,
  MonthControl,
  Modal,
  dateLabel,
} from "./ui";
export function FinanceView({
  data,
  open,
  create,
  month,
  setMonth,
  days,
  setDays,
}: {
  data: Snapshot;
  open: (i: Item) => void;
  create: (type: string) => void;
  month: string;
  setMonth: (s: string) => void;
  days: number;
  setDays: (n: number) => void;
}) {
  const f = data.finance;
  const [tab, setTab] = useState("Visão geral");
  const [query, setQuery] = useState("");
  const [type, setType] = useState("");
  const [page, setPage] = useState(0);
  const [list, setList] = useState<{ total: number; items: Item[] }>({
    total: 0,
    items: [],
  });
  const [category, setCategory] = useState("");
  const [state, setState] = useState("");
  const [invoice, setInvoice] = useState<any>(null);
  const [payment, setPayment] = useState({
    amount: "",
    date: today(),
    account: "",
  });
  const [input, setInput] = useState("");
  const [output, setOutput] = useState("");
  const [reserve, setReserve] = useState("");
  const [date, setDate] = useState(today());
  const hidden =
    data.preferences.financeHidden === "yes" ||
    data.preferences.financeHideValues === "yes";
  const cash = (n: number) =>
    f.error ? "—" : hidden ? "••••" : money(n, f.currency);
  useEffect(() => {
    let live = true;
    void api("search", {
      query,
      types: type
        ? [type]
        : ["income", "expense", "transfer", "bill", "receivable"],
      limit: 80,
      offset: page * 80,
      category,
      status: state,
      currency: f.currency,
      month,
    }).then((result) => {
      if (live) setList(result);
    });
    return () => {
      live = false;
    };
  }, [query, type, page, data.items, category, state, f.currency, month]);
  const filtered = list.items;
  let scenario: any = null,
    scenarioError = "";
  try {
    scenario = simulate(
      f.forecast,
      date,
      input ? parseMinor(input, f.currency) : 0,
      output ? parseMinor(output, f.currency) : 0,
      reserve ? parseMinor(reserve, f.currency) : 0,
    );
  } catch (e) {
    scenarioError = (e as Error).message;
  }
  const rules = data.items.filter(isRule);
  const categoryOptions = [
    ...new Set(data.items.map((i) => i.fields.category).filter(Boolean)),
  ].sort();
  const financialRecords = data.items.filter(
    (i) =>
      ["income", "expense"].includes(i.type) &&
      i.date.startsWith(month) &&
      active(i) &&
      i.fields.paymentType !== "card_payment",
  );
  const groups = new Map<string, Item[]>();
  for (const i of financialRecords.filter(
    (i) =>
      !i.fields.source &&
      !i.fields.recurrenceRuleId &&
      !i.fields.installmentPlanId,
  )) {
    const key = [
      i.type,
      i.date,
      amount(i),
      i.title.trim().toLowerCase().normalize("NFD").replace(/\p{M}/gu, ""),
      i.fields.account,
      i.fields.card,
      i.fields.currency || "BRL",
    ].join("|");
    groups.set(key, [...(groups.get(key) || []), i]);
  }
  const duplicates = [...groups.values()].filter((g) => g.length > 1);
  return (
    <>
      {f.error && (
        <Panel title="Confira os registros financeiros">
          <p role="alert">
            {f.error}. Os totais e projeções estão temporariamente
            indisponíveis. Seus dados continuam preservados.
          </p>
        </Panel>
      )}
      <div className="page-title">
        <div>
          <div className="eyebrow">SEU DINHEIRO, COM CLAREZA</div>
          <h1>Finanças</h1>
          <p>Registre com agilidade. Planeje com controle.</p>
        </div>
        <div className="actions">
          <MonthControl value={month} onChange={setMonth} />
          <Button
            onClick={() =>
              void api("preference", {
                key: "financeHidden",
                value: hidden ? "no" : "yes",
              })
            }
          >
            {hidden ? <EyeOff size={16} /> : <Eye size={16} />}Valores
          </Button>
          <Button kind="primary" onClick={() => create("expense")}>
            <Plus size={17} />
            Transação
          </Button>
        </div>
      </div>
      <div className="tabs">
        {[
          "Visão geral",
          "Movimentações",
          "Contas e cartões",
          "Recorrências",
          "Planejamento",
          "Simulador",
          "Conferência",
          "Calendário financeiro",
          "Assinaturas",
          "Relatórios",
        ].map((t) => (
          <button
            key={t}
            className={tab === t ? "active" : ""}
            onClick={() => setTab(t)}
          >
            {t}
          </button>
        ))}
      </div>
      {["Calendário financeiro", "Assinaturas", "Relatórios"].includes(tab) && (
        <FinanceExtras
          tab={tab}
          data={data}
          month={month}
          open={open}
          create={create}
        />
      )}
      {tab === "Visão geral" && (
        <>
          <div className="metric-grid">
            <Metric
              label="Saldo disponível"
              value={cash(f.balance)}
              detail="Caixa registrado até hoje"
              icon={<Wallet size={17} />}
            />
            <Metric
              label="Entradas do período"
              value={cash(f.income)}
              detail="Receitas realizadas"
              icon={<ArrowDownLeft size={17} />}
              tone="mint"
            />
            <Metric
              label="Saídas do período"
              value={cash(f.expense)}
              detail="Inclui compras no cartão"
              icon={<ArrowUpRight size={17} />}
              tone="coral"
            />
            <Metric
              label="Economia do período"
              value={cash(f.savings)}
              detail={
                f.income > 0
                  ? ((f.savings / f.income) * 100).toFixed(1) + "% das entradas"
                  : "Adicione receitas para comparar"
              }
              icon={<Check size={17} />}
            />
          </div>
          <div className="split two-thirds">
            <Panel
              title="Um olhar para frente"
              subtitle="Previsão de caixa • baseada nos seus registros"
              action={
                <select
                  aria-label="Horizonte da projeção"
                  value={days}
                  onChange={(e) => setDays(Number(e.target.value))}
                >
                  {[7, 30, 90, 180, 365].map((d) => (
                    <option value={d} key={d}>
                      {d < 90
                        ? d + " dias"
                        : d === 365
                          ? "12 meses"
                          : d / 30 + " meses"}
                    </option>
                  ))}
                </select>
              }
            >
              <div className="forecast-summary">
                <div>
                  <small>Saldo projetado</small>
                  <strong>{cash(f.forecast.balance)}</strong>
                </div>
                <span className="mint">+ {cash(f.forecast.income)}</span>
                <span className="coral">− {cash(f.forecast.expense)}</span>
              </div>
              <Chart
                points={f.forecast.points}
                cur={f.currency}
                hidden={hidden}
              />
              <p className="muted small">
                Considera compromissos, séries e faturas cadastrados. Dados
                ausentes alteram a previsão.
              </p>
            </Panel>
            <Panel
              title="Onde seu dinheiro foi"
              subtitle="Gastos reconhecidos por categoria"
            >
              {f.categories.length ? (
                f.categories.slice(0, 8).map((c, index) => (
                  <div className="category" key={c.name}>
                    <div>
                      <span>
                        <i
                          style={{
                            background: [
                              "#b6a3ff",
                              "#94d6b4",
                              "#dfb37f",
                              "#d998a8",
                            ][index % 4],
                          }}
                        />
                        {c.name}
                      </span>
                      <strong>{cash(c.value)}</strong>
                    </div>
                    <div className="progress">
                      <i
                        style={{
                          width:
                            Math.min(
                              100,
                              (c.value / Math.max(1, f.expense)) * 100,
                            ) + "%",
                        }}
                      />
                    </div>
                  </div>
                ))
              ) : (
                <Empty
                  title="Cada gasto conta uma história"
                  detail="Registre sua primeira despesa para conhecer suas categorias."
                  onAdd={() => create("expense")}
                />
              )}
            </Panel>
          </div>
          <div className="split">
            <Panel
              title="Próximos compromissos"
              action={
                <Button onClick={() => setTab("Movimentações")}>
                  Ver todos
                  <ArrowRight size={14} />
                </Button>
              }
            >
              {expanded(data.items, today(), f.forecast.points.at(-1)!.date)
                .filter((i) =>
                  ["pending", "expected", "overdue"].includes(status(i)),
                )
                .slice(0, 5)
                .map((i) => (
                  <RecordRow
                    key={i.id}
                    item={i}
                    open={open}
                    extra={
                      <span className={i.type === "income" ? "mint" : "coral"}>
                        {cash(amount(i))}
                      </span>
                    }
                  />
                ))}
              {f.forecast.expense === 0 && f.forecast.income === 0 && (
                <Empty
                  title="Sem compromissos previstos"
                  detail="Adicione contas ou recorrências para antecipar seu mês."
                />
              )}
            </Panel>
            <Panel
              title="Insights do seu dinheiro"
              subtitle="Calculados localmente, a partir dos seus dados"
            >
              {f.insights.length ? (
                f.insights.map((s, index) => (
                  <p className="insight" key={index}>
                    <span>{String(index + 1).padStart(2, "0")}</span>
                    {hidden ? "Exiba os valores para ler este insight." : s}
                  </p>
                ))
              ) : (
                <Empty
                  title="Seu histórico abre perspectivas"
                  detail="Com os primeiros lançamentos, suas comparações aparecem aqui."
                />
              )}
            </Panel>
          </div>
        </>
      )}
      {tab === "Movimentações" && (
        <Panel
          title="Tudo que entrou e saiu"
          subtitle={`${list.total} registros • ordenados por data`}
          action={
            <Button onClick={() => void api("backup")}>
              <Download size={15} />
              Exportar backup
            </Button>
          }
        >
          <div className="filter-bar">
            <SearchBox
              value={query}
              onChange={(v) => {
                setQuery(v);
                setPage(0);
              }}
              placeholder="Descrição, tags ou categoria…"
            />
            <select
              aria-label="Tipo de transação"
              value={type}
              onChange={(e) => {
                setType(e.target.value);
                setPage(0);
              }}
            >
              {[
                ["", "Todos os tipos"],
                ["income", "Entradas"],
                ["expense", "Saídas"],
                ["transfer", "Transferências"],
                ["bill", "Contas"],
              ].map(([v, l]) => (
                <option value={v} key={v}>
                  {l}
                </option>
              ))}
            </select>
            <select
              aria-label="Categoria"
              value={category}
              onChange={(e) => setCategory(e.target.value)}
            >
              <option value="">Todas as categorias</option>
              {categoryOptions.map((c) => (
                <option key={c}>{c}</option>
              ))}
            </select>
            <select
              aria-label="Situação"
              value={state}
              onChange={(e) => setState(e.target.value)}
            >
              <option value="">Todas as situações</option>
              {[
                ["paid", "Pago"],
                ["received", "Recebido"],
                ["pending", "Pendente"],
                ["expected", "Previsto"],
              ].map(([v, l]) => (
                <option value={v} key={v}>
                  {l}
                </option>
              ))}
            </select>
          </div>
          <div className="table-head">
            <span>DESCRIÇÃO / CATEGORIA</span>
            <span>VALOR</span>
          </div>
          {filtered.map((i) => (
            <RecordRow
              key={i.id}
              item={i}
              open={open}
              extra={
                <span
                  className={"amount " + (i.type === "income" ? "mint" : "")}
                >
                  {cash(amount(i))}
                </span>
              }
            />
          ))}
          {!filtered.length && (
            <Empty
              title="Nenhum lançamento neste filtro"
              onAdd={() => create("expense")}
            />
          )}
          <div className="pagination">
            <Button disabled={!page} onClick={() => setPage((p) => p - 1)}>
              Anterior
            </Button>
            <span>Página {page + 1}</span>
            <Button
              disabled={(page + 1) * 80 >= list.total}
              onClick={() => setPage((p) => p + 1)}
            >
              Próxima
            </Button>
          </div>
        </Panel>
      )}
      {tab === "Contas e cartões" && (
        <>
          <div className="split">
            <Panel
              title="Suas contas"
              action={
                <Button onClick={() => create("account")}>
                  <Plus size={15} />
                  Conta
                </Button>
              }
            >
              {f.accounts.map((a) => (
                <RecordRow
                  key={a.item.id}
                  item={a.item}
                  open={open}
                  extra={<strong>{cash(a.balance)}</strong>}
                />
              ))}
              {!f.accounts.length && (
                <Empty
                  title="Um lugar para cada saldo"
                  detail="Cadastre uma conta ou carteira."
                  onAdd={() => create("account")}
                />
              )}
            </Panel>
            <Panel
              title="Cartões"
              action={
                <Button onClick={() => create("card")}>
                  <Plus size={15} />
                  Cartão
                </Button>
              }
            >
              {data.items
                .filter((i) => i.type === "card")
                .map((c) => (
                  <div key={c.id}>
                    <RecordRow
                      item={c}
                      open={open}
                      extra={<span>Limite {cash(amount(c, "limit"))}</span>}
                    />
                    {f.invoices
                      .filter((i) => i.cardId === c.id)
                      .slice(0, 12)
                      .map((i) => (
                        <div className="invoice-row" key={i.id}>
                          <div>
                            <strong>Fatura • {dateLabel(i.due)}</strong>
                            <small>
                              Total {cash(i.total)} · pago {cash(i.paid)}
                            </small>
                          </div>
                          <span>{cash(i.remaining)}</span>
                          <Button
                            disabled={i.remaining === 0}
                            onClick={() => {
                              setInvoice(i);
                              setPayment({
                                amount: hidden
                                  ? ""
                                  : String(
                                      i.remaining /
                                        10 **
                                          new Intl.NumberFormat("en", {
                                            style: "currency",
                                            currency: i.currency,
                                          }).resolvedOptions()
                                            .maximumFractionDigits!,
                                    ),
                                date: today(),
                                account: f.accounts[0]?.item.id || "",
                              });
                            }}
                          >
                            {i.remaining ? "Registrar pagamento" : "Quitada"}
                          </Button>
                        </div>
                      ))}
                  </div>
                ))}
              {!data.items.some((i) => i.type === "card") && (
                <Empty
                  title="Faturas sob controle"
                  detail="Cadastre seu cartão e suas compras."
                  onAdd={() => create("card")}
                />
              )}
            </Panel>
          </div>
        </>
      )}
      {tab === "Recorrências" && (
        <Panel
          title="Séries e assinaturas"
          subtitle="Previsão automática sem duplicar lançamentos"
          action={
            <Button kind="primary" onClick={() => create("recurring_rule")}>
              <Plus size={16} />
              Nova série
            </Button>
          }
        >
          {rules.map((i) => (
            <RecordRow
              key={i.id}
              item={i}
              open={open}
              extra={
                <>
                  <span>
                    {cash(amount(i))} · {i.fields.frequency || "Mensal"}
                  </span>
                  <Button
                    onClick={() => {
                      const paused = !i.done;
                      void api("save", {
                        item: {
                          ...i,
                          done: paused,
                          fields: paused
                            ? { ...i.fields, paused: "yes", pausedAt: today() }
                            : {
                                ...i.fields,
                                paused: "no",
                                pausedAt: "",
                                pauseWindows: [
                                  i.fields.pauseWindows,
                                  ...(i.fields.pausedAt &&
                                  i.fields.pausedAt < today()
                                    ? [
                                        i.fields.pausedAt +
                                          "/" +
                                          new Date(Date.now() - 86400000)
                                            .toISOString()
                                            .slice(0, 10),
                                      ]
                                    : []),
                                ]
                                  .filter(Boolean)
                                  .join(";"),
                              },
                        },
                      });
                    }}
                  >
                    {i.done ? "Retomar" : "Pausar"}
                  </Button>
                </>
              }
            />
          ))}
          {!rules.length && (
            <Empty
              title="Antecipe os próximos meses"
              detail="Salário, aluguel e assinaturas podem ter séries próprias."
              onAdd={() => create("recurring_rule")}
            />
          )}
        </Panel>
      )}
      {tab === "Planejamento" && (
        <div className="split">
          <Panel
            title="Orçamentos"
            action={
              <Button onClick={() => create("budget")}>
                <Plus size={15} />
                Orçamento
              </Button>
            }
          >
            {f.budgets.map((b) => (
              <div className="budget" key={b.item.id}>
                <button onClick={() => open(b.item)}>
                  <strong>{b.item.title}</strong>
                  <span>
                    {cash(b.used)} de {cash(b.limit)}
                  </span>
                </button>
                <div className="progress">
                  <i
                    style={{
                      width:
                        Math.min(100, (b.used / Math.max(1, b.limit)) * 100) +
                        "%",
                    }}
                  />
                </div>
                <small>
                  {Math.round((b.used / Math.max(1, b.limit)) * 100)}% utilizado
                </small>
              </div>
            ))}
            {!f.budgets.length && (
              <Empty
                title="Dê um limite para cada categoria"
                onAdd={() => create("budget")}
              />
            )}
          </Panel>
          <Panel
            title="Metas e reservas"
            action={
              <Button onClick={() => create("savings_goal")}>
                <Plus size={15} />
                Meta
              </Button>
            }
          >
            {data.items
              .filter((i) =>
                ["savings_goal", "emergency_reserve"].includes(i.type),
              )
              .map((i) => (
                <div key={i.id}>
                  <RecordRow
                    item={i}
                    open={open}
                    extra={
                      <span>
                        {cash(amount(i, "saved"))} / {cash(amount(i))}
                      </span>
                    }
                  />
                  <div className="progress">
                    <i
                      style={{
                        width:
                          Math.min(
                            100,
                            (amount(i, "saved") / Math.max(1, amount(i))) * 100,
                          ) + "%",
                      }}
                    />
                  </div>
                </div>
              ))}
            {!data.items.some((i) => i.type === "savings_goal") && (
              <Empty
                title="Seu próximo objetivo começa aqui"
                onAdd={() => create("savings_goal")}
              />
            )}
          </Panel>
        </div>
      )}
      {tab === "Simulador" && (
        <div className="split two-thirds">
          <Panel
            title="Teste antes de decidir"
            subtitle="Este cenário não altera seus saldos nem cria lançamentos"
          >
            {hidden ? (
              <p>Exiba os valores para usar o simulador.</p>
            ) : (
              <>
                <div className="form-grid">
                  <Field
                    label="Entrada extra"
                    value={input}
                    onChange={setInput}
                  />
                  <Field
                    label="Gasto extra"
                    value={output}
                    onChange={setOutput}
                  />
                  <Field
                    label="Data do cenário"
                    type="date"
                    value={date}
                    onChange={setDate}
                  />
                  <Field
                    label="Reserva mínima no caixa"
                    value={reserve}
                    onChange={setReserve}
                  />
                  <Field
                    label="Horizonte"
                    value={String(days)}
                    onChange={(v) => setDays(Number(v))}
                    options={[7, 30, 90, 180, 365].map((n) => ({
                      value: String(n),
                      label: n + " dias",
                    }))}
                  />
                </div>
                <Button
                  onClick={() => {
                    setInput("");
                    setOutput("");
                    setReserve("");
                    setDate(today());
                  }}
                >
                  Limpar cenário
                </Button>
                {scenarioError && <p className="error">{scenarioError}</p>}
                {scenario && (
                  <Chart points={scenario.points} cur={f.currency} />
                )}
              </>
            )}
          </Panel>
          <Panel title="Seu cenário" subtitle="Saldo ao fim de cada dia">
            {hidden ? (
              <p>Valores ocultos.</p>
            ) : (
              scenario && (
                <>
                  <p className="big-number">{cash(scenario.balance)}</p>
                  <p className="muted">
                    Sem cenário: {cash(f.forecast.balance)}
                  </p>
                  <hr />
                  <p>
                    Menor saldo: <strong>{cash(scenario.lowest)}</strong>
                  </p>
                  <p>
                    Margem diária adicional:{" "}
                    <strong>{cash(scenario.daily)}</strong>
                  </p>
                  {scenario.below && (
                    <p className="error">
                      Abaixo da reserva em {dateLabel(scenario.below)}.
                    </p>
                  )}
                  <p className="muted small">
                    A reserva é um piso, não uma despesa. A margem verifica
                    também os dias anteriores às entradas futuras.
                  </p>
                </>
              )
            )}
          </Panel>
        </div>
      )}
      {tab === "Conferência" && (
        <div className="split">
          <Panel
            title="Possíveis duplicidades"
            subtitle="Revise antes de corrigir; compras iguais podem ser legítimas"
          >
            {duplicates.slice(0, 40).map((group) => (
              <div className="duplicate-group" key={group[0].id}>
                <small>{group.length} registros com os mesmos dados</small>
                {group.slice(0, 10).map((i) => (
                  <RecordRow
                    key={i.id}
                    item={i}
                    open={open}
                    extra={<span>{cash(amount(i))}</span>}
                  />
                ))}
              </div>
            ))}
            {!duplicates.length && (
              <Empty
                title="Nenhuma duplicidade sugerida"
                detail="A conferência não remove registros automaticamente."
              />
            )}
          </Panel>
          <Panel title="Sem categoria">
            {financialRecords
              .filter(
                (i) =>
                  !i.fields.category || i.fields.category === "Sem categoria",
              )
              .slice(0, 80)
              .map((i) => (
                <RecordRow key={i.id} item={i} open={open} />
              ))}
            {financialRecords.every((i) => i.fields.category) && (
              <Empty title="Categorias em dia" />
            )}
          </Panel>
        </div>
      )}
      {invoice && (
        <Modal
          title="Registrar pagamento de fatura"
          onClose={() => setInvoice(null)}
        >
          <div className="form-body">
            <p>Em aberto: {cash(invoice.remaining)}</p>
            <Field
              label="Valor pago"
              value={payment.amount}
              onChange={(v) => setPayment((p) => ({ ...p, amount: v }))}
            />
            <Field
              label="Data do pagamento realizado"
              value={payment.date}
              type="date"
              onChange={(v) => setPayment((p) => ({ ...p, date: v }))}
            />
            <Field
              label="Conta"
              value={payment.account}
              onChange={(v) => setPayment((p) => ({ ...p, account: v }))}
              options={[
                { value: "", label: "Escolher conta" },
                ...f.accounts.map((a) => ({
                  value: a.item.id,
                  label: a.item.title,
                })),
              ]}
            />
            <p className="muted">
              Você pode pagar uma parte ou quitar o restante. A compra não será
              contada novamente como gasto.
            </p>
            <Button
              kind="primary"
              onClick={() =>
                void api("payInvoice", {
                  ...invoice,
                  invoiceId: invoice.id,
                  ...payment,
                }).then(() => setInvoice(null))
              }
            >
              Confirmar pagamento
            </Button>
          </div>
        </Modal>
      )}
    </>
  );
}
export function Metric({
  label,
  value,
  detail,
  icon,
  tone = "",
}: {
  label: string;
  value: string;
  detail: string;
  icon: React.ReactNode;
  tone?: string;
}) {
  return (
    <div className={"metric " + tone}>
      <div>
        <span>{label}</span>
        {icon}
      </div>
      <strong>{value}</strong>
      <small>{detail}</small>
    </div>
  );
}
