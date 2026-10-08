import React, { useEffect, useState } from "react";
import {
  Search,
  Plus,
  ArrowUpRight,
  Command,
  Check,
  Inbox,
} from "lucide-react";
import { Item, Snapshot, createItem, today, addDays } from "../shared/model";
import { interpretQuick, fold } from "../shared/platform";
import { modules, quickTypes } from "../shared/commands";
import { api, Modal, Empty, labels, dateLabel } from "./ui";

export function QuickComposer({
  data,
  choose,
}: {
  data: Snapshot;
  choose: (item: Item) => void;
}) {
  const [text, setText] = useState("");
  const [error, setError] = useState("");
  let proposal = null;
  let proposalError = "";
  try {
    proposal = interpretQuick(text, today(), data.finance.currency);
  } catch (e) {
    proposalError = (e as Error).message;
  }
  const interpret = () => {
    try {
      const p = interpretQuick(text, today(), data.finance.currency);
      if (p) choose(p.item);
    } catch (e) {
      setError((e as Error).message);
    }
  };
  return (
    <div className="quick-composer">
      <div className="eyebrow">TIRE DA CABEÇA. ORGANIZE DEPOIS.</div>
      <h1>O que você quer registrar?</h1>
      <p>Escreva como você pensa. Você revisa tudo antes de salvar.</p>
      <label className="composer-input">
        <Search size={20} />
        <input
          autoFocus
          aria-label="Digite uma ação"
          value={text}
          onChange={(e) => {
            setText(e.target.value);
            setError("");
          }}
          placeholder="Gastei 42 no almoço · Academia amanhã 18h"
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              e.preventDefault();
              interpret();
            }
          }}
        />
        <kbd>↵</kbd>
      </label>
      {proposal && (
        <button className="proposal" onClick={interpret}>
          <Check size={18} />
          <div>
            <strong>
              Revisar {labels[proposal.item.type] || proposal.item.type}:{" "}
              {proposal.item.title}
            </strong>
            <small>
              {proposal.item.fields.amount
                ? proposal.item.fields.amount +
                  " " +
                  proposal.item.fields.currency +
                  " · "
                : ""}
              {proposal.explanation.join(" ")}
            </small>
          </div>
          <ArrowUpRight size={17} />
        </button>
      )}
      {(error || proposalError) && (
        <p className="error" role="alert">
          {error || proposalError}
        </p>
      )}
      <div className="quick-type-grid">
        {quickTypes.map(([type, title]) => (
          <button
            key={type}
            onClick={() =>
              choose(
                createItem(type, {
                  title: text,
                  date: type === "inbox" || type === "note" ? "" : today(),
                }),
              )
            }
          >
            {type === "inbox" ? <Inbox size={18} /> : <Plus size={18} />}
            <span>{title}</span>
          </button>
        ))}
      </div>
      <p className="muted small">
        Interpretação local • Nenhum texto enviado para IA externa • Esc fecha
      </p>
    </div>
  );
}
export function CommandPalette({
  data,
  close,
  open,
  choose,
  go,
  inline = false,
}: {
  data: Snapshot;
  close: () => void;
  open: (i: Item) => void;
  choose: (i: Item) => void;
  go: (s: string) => void;
  inline?: boolean;
}) {
  const [query, setQuery] = useState(""),
    [results, setResults] = useState<Item[]>([]),
    [index, setIndex] = useState(0),
    [error, setError] = useState("");
  const q = fold(query).replace(/^>\s*/, ""),
    commands = [
      ...modules.map((m) => ({
        title: "Abrir " + m.label,
        keywords: m.id + " " + m.label,
        run: () => go(m.id),
      })),
      ...quickTypes.map(([type, title]) => ({
        title: "Novo: " + title,
        keywords: type + " " + title,
        run: () => choose(createItem(type)),
      })),
      {
        title: "Tema escuro",
        keywords: "dark escuro",
        run: () => void api("desktop", { theme: "dark" }),
      },
      {
        title: "Tema claro",
        keywords: "light claro",
        run: () => void api("desktop", { theme: "light" }),
      },
      {
        title: "Tema do sistema",
        keywords: "system sistema",
        run: () => void api("desktop", { theme: "system" }),
      },
      {
        title: "Sincronizar agora",
        keywords: "sync sincronizar",
        run: () => void api("sync"),
      },
      {
        title: "Backup Center",
        keywords: "backup exportar importar",
        run: () => go("backup"),
      },
      {
        title: "Privacidade",
        keywords: "privacy privacidade",
        run: () => go("privacy"),
      },
      {
        title: "Diagnóstico seguro",
        keywords: "diagnostics diagnostico",
        run: () => go("diagnostics"),
      },
      {
        title: "Agenda de amanhã",
        keywords: "amanha tomorrow",
        run: () =>
          void api("desktop", { plannerDate: addDays(today(), 1) }).then(() =>
            go("planner"),
          ),
      },
      {
        title: "Mostrar gastos deste mês",
        keywords: "mostrar gastos deste mes",
        run: () => go("finance"),
      },
      {
        title: "Iniciar foco de 25 minutos",
        keywords: "iniciar foco pomodoro",
        run: () =>
          void api("focusStart", {
            minutes: 25,
            title: "Tempo de foco",
            mode: "Pomodoro",
          }),
      },
    ];
  useEffect(() => {
    let live = true;
    setIndex(0);
    setError("");
    const timer = setTimeout(() => {
      void api("universalSearch", {
        query: query || "favorite:true",
        limit: 25,
      })
        .then((r) => {
          if (live) setResults(r.items);
        })
        .catch((e) => {
          if (live) {
            setError(e.message);
            setResults([]);
          }
        });
    }, 120);
    return () => {
      live = false;
      clearTimeout(timer);
    };
  }, [query, data.uid]);
  const choices = commands
    .filter((c) => !q || fold(c.title + " " + c.keywords).includes(q))
    .slice(0, q ? 15 : 6)
    .map((c) => ({ ...c, kind: "Comando" }));
  let proposal = null;
  try {
    if (/^(gastei|gasto|despesa|recebi|entrada|tarefa|evento|nota)\b/.test(q))
      proposal = interpretQuick(query, today(), data.finance.currency);
  } catch {
    /* Invalid proposal stays editable. */
  }
  if (proposal) {
    const item = proposal.item;
    choices.unshift({
      title: "Revisar e criar: " + item.title,
      keywords: "",
      kind: "Captura",
      run: () => choose(item),
    });
  }
  const rows = [
    ...choices,
    ...results.map((i) => ({
      title: i.title,
      kind:
        (labels[i.type] || i.type) + (i.date ? " · " + dateLabel(i.date) : ""),
      run: () => open(i),
    })),
  ];
  const content = (
    <div className="palette">
      <label>
        <Search size={22} />
        <input
          autoFocus
          aria-label="Pesquisar Veyra"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Pesquisar, > dark, gasto 50 mercado…"
          onKeyDown={(e) => {
            if (e.key === "ArrowDown") {
              e.preventDefault();
              setIndex((n) => Math.min(rows.length - 1, n + 1));
            }
            if (e.key === "ArrowUp") {
              e.preventDefault();
              setIndex((n) => Math.max(0, n - 1));
            }
            if (e.key === "Enter" && rows[index]) {
              e.preventDefault();
              rows[index].run();
              close();
            }
            if (e.key === "Escape") {
              close();
            }
          }}
        />
        <kbd>Esc</kbd>
      </label>
      <p className="search-help">
        type:expense · date:hoje · tag:viagem · category:Alimentação ·
        amount:&gt;50
      </p>
      {error && <p className="error">{error}</p>}
      <div className="palette-results">
        {rows.map((row, n) => (
          <button
            key={row.kind + row.title + n}
            className={index === n ? "active" : ""}
            onMouseEnter={() => setIndex(n)}
            onClick={() => {
              row.run();
              close();
            }}
          >
            <span className="command-icon">
              <Command size={16} />
            </span>
            <span>
              {row.title}
              <small>{row.kind}</small>
            </span>
            <ArrowUpRight size={14} />
          </button>
        ))}
        {!rows.length && (
          <Empty
            title="Nada encontrado"
            detail="Tente outra palavra ou revise os filtros."
          />
        )}
      </div>
      <footer>
        <span>↑ ↓ Navegar · ↵ Abrir</span>
        <span>Busca local, sem consultas ao Firebase</span>
      </footer>
    </div>
  );
  return inline ? (
    content
  ) : (
    <Modal title="Pesquisa e comandos" onClose={close}>
      {content}
    </Modal>
  );
}
