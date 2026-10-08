import React, { useState } from "react";
import {
  Snapshot,
  Item,
  today,
  addDays,
  addMonths,
  money,
  amount,
  safe,
} from "../shared/model";
import { expanded, active, currency, due } from "../shared/finance";
import { reportRows } from "../shared/reporting";
import {
  api,
  Panel,
  Button,
  Field,
  Empty,
  Modal,
  RecordRow,
  dateLabel,
} from "./ui";
export default function FinanceExtras({
  tab,
  data,
  month,
  open,
  create,
}: {
  tab: string;
  data: Snapshot;
  month: string;
  open: (i: Item) => void;
  create: (type: string) => void;
}) {
  const [from, setFrom] = useState(month + "-01"),
    [to, setTo] = useState(addDays(addMonths(month + "-01", 1), -1)),
    [account, setAccount] = useState(""),
    [card, setCard] = useState(""),
    [category, setCategory] = useState(""),
    [preview, setPreview] = useState<any>(null),
    [error, setError] = useState(""),
    [basis, setBasis] = useState<"recognized" | "cash">("recognized");
  const cur = data.finance.currency,
    hidden =
      data.preferences.financeHidden === "yes" ||
      data.preferences.financeHideValues === "yes",
    cash = (n: number) => (hidden ? "••••" : money(n, cur));
  if (tab === "Calendário financeiro") {
    const start = month + "-01",
      end = addDays(addMonths(start, 1), -1),
      first = addDays(
        start,
        -((new Date(start + "T12:00:00").getDay() + 6) % 7),
      );
    const transactions = expanded(data.items, start, end).filter(
      (i) =>
        active(i) &&
        currency(i) === cur &&
        i.type !== "transfer" &&
        !(i.type === "expense" && i.fields.card),
    );
    return (
      <Panel
        title="O mês, dia a dia"
        subtitle="Lançamentos e recorrências; faturas aparecem pelo vencimento e saldo restante."
      >
        <div className="finance-calendar-grid">
          {Array.from({ length: 42 }, (_, n) => addDays(first, n)).map(
            (day) => (
              <div className="finance-day" key={day}>
                <small>{dateLabel(day)}</small>
                {day.startsWith(month) &&
                  transactions
                    .filter((i) => due(i) === day)
                    .slice(0, 5)
                    .map((i) => (
                      <button key={i.id} onClick={() => open(i)}>
                        {i.title}
                        <strong>
                          {cash(amount(i))} ·{" "}
                          {i.fields.virtual === "yes"
                            ? "Previsto"
                            : i.fields.status || "Registrado"}
                        </strong>
                      </button>
                    ))}
                {data.finance.invoices
                  .filter((inv) => inv.due === day && inv.remaining > 0)
                  .map((inv) => (
                    <button
                      key={inv.id}
                      onClick={() => {
                        const card = data.items.find(
                          (i) => i.id === inv.cardId,
                        );
                        if (card) open(card);
                      }}
                    >
                      Fatura ·{" "}
                      {data.items.find((i) => i.id === inv.cardId)?.title}
                      <strong>{cash(inv.remaining)} restante</strong>
                    </button>
                  ))}
              </div>
            ),
          )}
        </div>
      </Panel>
    );
  }
  if (tab === "Assinaturas") {
    const subscriptions = data.items.filter(
      (i) =>
        i.type === "subscription" &&
        !i.deletedAt &&
        currency(i) === cur &&
        i.fields.pausedAt !== today(),
    );
    let annual: Item[] = [];
    try {
      annual = expanded(data.items, today(), addDays(today(), 364)).filter(
        (i) =>
          subscriptions.some(
            (s) =>
              i.fields.source === s.id ||
              i.fields.recurrenceRuleId === s.id ||
              i.fields.ruleId === s.id ||
              i.parentId === s.id,
          ),
      );
    } catch {
      /* Error is displayed by the main finance view. */
    }
    const total = annual.reduce((n, i) => safe(n + amount(i)), 0);
    return (
      <Panel
        title="Assinaturas"
        subtitle="Cadastros explícitos, sem leitura de bancos ou detecção externa."
        action={
          <Button kind="primary" onClick={() => create("subscription")}>
            Cadastrar assinatura
          </Button>
        }
      >
        <div className="review-stats compact">
          <div>
            <small>Custo previsto nos próximos 12 meses</small>
            <strong>{cash(total)}</strong>
          </div>
          <div>
            <small>Equivalente mensal aproximado</small>
            <strong>{cash(Math.round(total / 12))}</strong>
          </div>
        </div>
        {subscriptions.map((s) => (
          <div key={s.id}>
            <RecordRow item={s} open={open} />
            <p className="muted small">
              {s.fields.frequency || "Mensal"} · próxima ocorrência{" "}
              {annual
                .filter(
                  (i) =>
                    i.fields.source === s.id ||
                    i.fields.recurrenceRuleId === s.id ||
                    i.fields.ruleId === s.id ||
                    i.parentId === s.id,
                )
                .sort((a, b) => a.date.localeCompare(b.date))[0]?.date ||
                "Confira início, fim e pausas"}
            </p>
          </div>
        ))}
        {!subscriptions.length && (
          <Empty
            title="Veja o custo dos serviços que você usa"
            onAdd={() => create("subscription")}
          />
        )}
      </Panel>
    );
  }
  const filter = { from, to, currency: cur, account, card, category, basis };
  let report = null,
    reportError = "";
  try {
    report = reportRows(data.items, filter);
  } catch (e) {
    reportError = (e as Error).message;
  }
  return (
    <>
      <Panel
        title="Relatórios e importação"
        subtitle="Escolha período e filtros. A exportação inclui somente os lançamentos selecionados."
      >
        <div className="form-grid">
          <Field
            label="Como analisar"
            value={basis}
            onChange={(v) => setBasis(v as "recognized" | "cash")}
            options={[
              {
                value: "recognized",
                label: "Gastos registrados — data da compra",
              },
              { value: "cash", label: "Fluxo de caixa — data do pagamento" },
            ]}
          />
          <Field label="De" value={from} onChange={setFrom} type="date" />
          <Field label="Até" value={to} onChange={setTo} type="date" />
          <Field
            label="Conta"
            value={account}
            onChange={setAccount}
            options={[
              { value: "", label: "Todas" },
              ...data.items
                .filter((i) => i.type === "account" && currency(i) === cur)
                .map((i) => ({ value: i.id, label: i.title })),
            ]}
          />
          <Field
            label="Cartão"
            value={card}
            onChange={setCard}
            options={[
              { value: "", label: "Todos" },
              ...data.items
                .filter((i) => i.type === "card" && currency(i) === cur)
                .map((i) => ({ value: i.id, label: i.title })),
            ]}
          />
          <Field label="Categoria" value={category} onChange={setCategory} />
        </div>
        <div className="actions">
          <Button
            onClick={() => {
              setFrom(month + "-01");
              setTo(addDays(addMonths(month + "-01", 1), -1));
            }}
          >
            Mês
          </Button>
          <Button
            onClick={() => {
              setFrom(addMonths(month + "-01", -2));
              setTo(addDays(addMonths(month + "-01", 1), -1));
            }}
          >
            3 meses
          </Button>
          <Button
            onClick={() => {
              setFrom(month.slice(0, 4) + "-01-01");
              setTo(month.slice(0, 4) + "-12-31");
            }}
          >
            Ano
          </Button>
          <Button
            disabled={!report}
            kind="primary"
            onClick={() =>
              void api("exportReport", filter).catch((e) => setError(e.message))
            }
          >
            Exportar CSV
          </Button>
          <Button
            onClick={() =>
              void api("financeImportPreview")
                .then(setPreview)
                .catch((e) => setError(e.message))
            }
          >
            Importar CSV com prévia
          </Button>
        </div>
        <p className="muted small">
          {basis === "cash"
            ? "Fluxo de caixa considera recebimentos e pagamentos confirmados, incluindo a quitação da fatura. Compras no cartão e transferências internas não entram novamente."
            : "Gastos registrados consideram a data da compra, incluindo cartão; pagamento de fatura não duplica a despesa. Valores previstos ficam identificados."}{" "}
          Notas, anexos e números de cartão não são exportados.
        </p>
        {(error || reportError) && (
          <p className="error">{error || reportError}</p>
        )}
      </Panel>
      {report && (
        <>
          <div className="review-stats">
            <div>
              <small>Entradas realizadas</small>
              <strong>{cash(report.income)}</strong>
            </div>
            <div>
              <small>
                {basis === "cash"
                  ? "Saídas efetivamente pagas"
                  : "Despesas reconhecidas"}
              </small>
              <strong>{cash(report.expense)}</strong>
            </div>
            <div>
              <small>Resultado do período</small>
              <strong>{cash(report.net)}</strong>
            </div>
            <div>
              <small>
                {basis === "cash"
                  ? "Lançamentos confirmados"
                  : "Lançamentos, incluindo previsão"}
              </small>
              <strong>{report.rows.length}</strong>
            </div>
          </div>
          <Panel title="Por categoria">
            {report.categories.map((c) => (
              <div key={c.name}>
                <div className="invoice-row">
                  <span>{c.name}</span>
                  <strong>{cash(c.value)}</strong>
                </div>
                <div className="progress-bar">
                  <i
                    style={{
                      width:
                        (report.expense
                          ? (c.value / report.expense) * 100
                          : 0) + "%",
                    }}
                  />
                </div>
              </div>
            ))}
          </Panel>
        </>
      )}
      {preview && (
        <Modal
          title="Revisar importação financeira"
          onClose={() => setPreview(null)}
        >
          <div className="form-body">
            <p>
              {preview.total} linhas · {preview.newCount} novas ·{" "}
              {preview.duplicates} já existentes
            </p>
            <p className="muted">
              Datas ISO, valores decimais e colunas
              type/date/title/amount/currency. Transferências e referências
              desconhecidas precisam ser cadastradas antes.
            </p>
            {preview.items.slice(0, 12).map((i: Item) => (
              <p key={i.id}>
                {dateLabel(i.date)} · {i.title} · {cash(amount(i))}
              </p>
            ))}
            {error && <p className="error">{error}</p>}
            <Button
              kind="primary"
              disabled={!preview.newCount}
              onClick={() =>
                void api("financeImportConfirm", { token: preview.token })
                  .then(() => setPreview(null))
                  .catch((e) => setError(e.message))
              }
            >
              Importar somente novos registros
            </Button>
          </div>
        </Modal>
      )}
    </>
  );
}
