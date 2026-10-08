import React, { useEffect, useRef, useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { Plus, Star, History, Paperclip, Link2 } from "lucide-react";
import { Item, Snapshot } from "../shared/model";
import { backlinks } from "../shared/platform";
import { api, Button, Empty, SearchBox, Field, Modal, dateLabel } from "./ui";
import RichEditor from "./RichEditor";
export default function Notes({
  data,
  create,
}: {
  data: Snapshot;
  create: (type: string) => void;
}) {
  const notes = data.items.filter((i) => i.type === "note"),
    [selected, setSelected] = useState(notes[0]?.id || ""),
    [draft, setDraft] = useState<Item | null>(null),
    [query, setQuery] = useState(""),
    [folder, setFolder] = useState(""),
    [mode, setMode] = useState("markdown"),
    [saved, setSaved] = useState(""),
    [history, setHistory] = useState<any[] | null>(null),
    [matching, setMatching] = useState<Set<string> | null>(null),
    [image, setImage] = useState<string | null>(null);
  const pending = useRef(new Map<string, Item>()),
    bases = useRef(new Map<string, Item>()),
    queue = useRef(Promise.resolve()),
    timer = useRef<ReturnType<typeof setTimeout> | null>(null),
    latest = useRef<Item | null>(null),
    uid = useRef(data.uid),
    draftTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const flush = (id: string) => {
    const next = pending.current.get(id);
    if (!next) return;
    pending.current.delete(id);
    const scope = uid.current;
    queue.current = queue.current
      .then(async () => {
        await api("draftWrite", { item: next, expectedUid: scope });
        await api("save", {
          item: next,
          base: bases.current.get(id),
          expectedUid: scope,
        });
        bases.current.set(id, next);
        if (latest.current?.id === id) setSaved("Salvo neste PC");
      })
      .catch((e) => {
        setSaved(e.message + " Seu rascunho permanece neste PC.");
      });
  };
  useEffect(() => {
    if (!selected && notes.length) setSelected(notes[0].id);
  }, [selected, notes.length]);
  useEffect(() => {
    let live = true;
    void queue.current
      .then(() => api("item", selected))
      .then((item) => {
        if (live) {
          setDraft(item);
          latest.current = item;
          if (item) bases.current.set(item.id, item);
          setSaved("");
        }
      });
    return () => {
      live = false;
      if (timer.current) clearTimeout(timer.current);
      flush(selected);
    };
  }, [selected]);
  useEffect(() => {
    let live = true;
    const t = setTimeout(() => {
      void api("universalSearch", {
        query: "type:note " + query,
        limit: 100,
      }).then((r) => {
        if (live) setMatching(new Set(r.items.map((i: Item) => i.id)));
      });
    }, 120);
    return () => {
      live = false;
      clearTimeout(t);
    };
  }, [query, data.items]);
  useEffect(
    () => () => {
      if (draftTimer.current) clearTimeout(draftTimer.current);
    },
    [],
  );
  const update = (patch: Partial<Item>) => {
    if (!latest.current) return;
    const next = { ...latest.current, ...patch };
    latest.current = next;
    setDraft(next);
    pending.current.set(next.id, next);
    setSaved("Salvando…");
    if (!draftTimer.current) {
      const scope = uid.current;
      draftTimer.current = setTimeout(() => {
        draftTimer.current = null;
        const current = latest.current;
        if (current && pending.current.has(current.id))
          void api("draftWrite", { item: current, expectedUid: scope }).catch(
            (e) =>
              setSaved("Não foi possível guardar o rascunho: " + e.message),
          );
      }, 250);
    }
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(() => flush(next.id), 650);
  };
  const insert = (text: string) => {
    if (draft)
      update({ notes: draft.notes + (draft.notes ? "\n" : "") + text });
  };
  useEffect(() => {
    let live = true;
    setImage(null);
    if (draft?.fields.hasAttachment === "yes")
      void api("attachmentPreview", draft.id)
        .then((src) => {
          if (live) setImage(src);
        })
        .catch((e) => setSaved(e.message));
    return () => {
      live = false;
    };
  }, [draft?.id, draft?.fields.attachmentHash, data.items]);
  const folders = [
    ...new Set(notes.map((n) => n.fields.folder).filter(Boolean)),
  ];
  const links = draft ? backlinks(data.items, draft) : [];
  const wikilinks = (text: string) =>
    text.replace(/\[\[([^\]\n]{1,200})\]\]/g, (original, target: string) => {
      const matches = notes.filter(
        (n) => n.id === target || n.title === target,
      );
      return matches.length === 1
        ? "[" +
            target.replace(/[\[\]]/g, "") +
            "](#note=" +
            encodeURIComponent(matches[0].id) +
            ")"
        : original;
    });
  return (
    <>
      <div className="page-title">
        <div>
          <div className="eyebrow">CONHECIMENTO QUE SE CONECTA</div>
          <h1>Notas</h1>
          <p>
            Markdown, edição visual, vínculos e histórico. Autosave local
            agrupado.
          </p>
        </div>
        <Button kind="primary" onClick={() => create("note")}>
          <Plus size={16} />
          Nova nota
        </Button>
      </div>
      <div className="notes-workspace">
        <aside className="folders">
          <button
            className={!folder ? "active" : ""}
            onClick={() => setFolder("")}
          >
            Todas as notas <span>{notes.length}</span>
          </button>
          <button
            className={folder === "@favorite" ? "active" : ""}
            onClick={() => setFolder("@favorite")}
          >
            Favoritas
          </button>
          <h4>PASTAS</h4>
          {folders.map((f) => (
            <button key={f} onClick={() => setFolder(f)}>
              {f}
            </button>
          ))}
          <p className="muted small">
            Use [[Título da nota]] para criar conexões. Títulos duplicados
            precisam de [[ID]].
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
                (!query || !matching || matching.has(i.id)),
            )
            .sort(
              (a, b) =>
                Number(b.favorite) - Number(a.favorite) ||
                Number(b.fields.pinned === "yes") -
                  Number(a.fields.pinned === "yes") ||
                b.createdAt - a.createdAt,
            )
            .map((i) => (
              <button
                key={i.id}
                className={
                  "note-preview " + (selected === i.id ? "selected" : "")
                }
                onClick={() => {
                  flush(selected);
                  setSelected(i.id);
                  setMode("markdown");
                }}
              >
                <strong>
                  {i.title}
                  {i.favorite && <Star size={12} />}
                </strong>
                <p>
                  {i.notes.slice(0, 100) || "Uma ideia esperando palavras."}
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
                  <Button onClick={() => update({ favorite: !draft.favorite })}>
                    <Star size={15} />
                    Favoritar
                  </Button>
                  <Button
                    onClick={() =>
                      update({
                        fields: {
                          ...draft.fields,
                          pinned: draft.fields.pinned === "yes" ? "no" : "yes",
                        },
                      })
                    }
                  >
                    {draft.fields.pinned === "yes" ? "Desafixar" : "Fixar"}
                  </Button>
                  <Button
                    onClick={() =>
                      void api("history", draft.id).then(setHistory)
                    }
                  >
                    <History size={15} />
                    Histórico
                  </Button>
                </div>
              </header>
              <input
                className="note-title"
                aria-label="Título da nota"
                value={draft.title}
                onChange={(e) => update({ title: e.target.value })}
              />
              <div className="note-metadata">
                <Field
                  label="Pasta"
                  value={draft.fields.folder || ""}
                  onChange={(v) =>
                    update({ fields: { ...draft.fields, folder: v } })
                  }
                />
                <Field
                  label="Tags"
                  value={draft.tags}
                  onChange={(v) => update({ tags: v })}
                />
                <Field
                  label="Projeto ou meta"
                  value={draft.parentId}
                  onChange={(v) => update({ parentId: v })}
                  options={[
                    { value: "", label: "Sem vínculo" },
                    ...data.items
                      .filter((i) => ["project", "goal"].includes(i.type))
                      .map((i) => ({ value: i.id, label: i.title })),
                  ]}
                />
              </div>
              <div className="tabs">
                {[
                  ["markdown", "Markdown"],
                  ["visual", "Edição visual"],
                  ["preview", "Visualizar"],
                ].map(([v, t]) => (
                  <button
                    key={v}
                    className={mode === v ? "active" : ""}
                    onClick={() => setMode(v)}
                  >
                    {t}
                  </button>
                ))}
              </div>
              {mode === "markdown" ? (
                <>
                  <div className="note-format-toolbar">
                    {[
                      ["Título", "## Título"],
                      ["Checklist", "- [ ] Primeiro passo"],
                      [
                        "Tabela",
                        "| Coluna | Valor |\n| --- | --- |\n| Item | Conteúdo |",
                      ],
                      ["Código", "```\n// Seu código\n```"],
                      ["Link", "[Nome](https://exemplo.com)"],
                    ].map(([label, text]) => (
                      <Button key={label} onClick={() => insert(text)}>
                        {label}
                      </Button>
                    ))}
                  </div>
                  <textarea
                    className="markdown-source"
                    aria-label="Conteúdo da nota"
                    value={draft.notes}
                    onChange={(e) => update({ notes: e.target.value })}
                    onBlur={() => flush(draft.id)}
                    placeholder="# Sua próxima ideia"
                  />
                </>
              ) : mode === "visual" ? (
                <RichEditor
                  key={draft.id}
                  initial={draft.notes}
                  onChange={(notes) => update({ notes })}
                />
              ) : (
                <article className="markdown">
                  <ReactMarkdown
                    remarkPlugins={[remarkGfm]}
                    components={{
                      a: ({ href, children }) => (
                        <button
                          className="markdown-link"
                          onClick={() => {
                            if (href?.startsWith("#note=")) {
                              flush(selected);
                              setSelected(decodeURIComponent(href.slice(6)));
                            } else if (href) void api("openLink", href);
                          }}
                        >
                          {children}
                        </button>
                      ),
                      img: ({ alt }) => (
                        <span className="muted">
                          Imagem externa não carregada: {alt || "sem descrição"}
                        </span>
                      ),
                    }}
                  >
                    {wikilinks(draft.notes)}
                  </ReactMarkdown>
                </article>
              )}
              {image && (
                <figure className="note-local-image">
                  <img
                    src={image}
                    alt={
                      draft.fields.attachmentName || "Imagem anexada localmente"
                    }
                  />
                  <figcaption>
                    Imagem local · {draft.fields.attachmentName}
                  </figcaption>
                </figure>
              )}
              <footer>
                <span>{draft.notes.length} caracteres</span>
                <Button
                  onClick={() =>
                    void api("attach").then((a) => {
                      if (a)
                        update({ fields: { ...latest.current!.fields, ...a } });
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
              <div className="backlinks">
                <h4>
                  <Link2 size={14} /> Referenciado por
                </h4>
                {links.map((i) => (
                  <button
                    key={i.id}
                    onClick={() => {
                      flush(selected);
                      setSelected(i.id);
                    }}
                  >
                    {i.title}
                  </button>
                ))}
                {!links.length && (
                  <p className="muted small">
                    Outras notas que mencionam esta aparecerão aqui.
                  </p>
                )}
              </div>
            </>
          ) : (
            <Empty
              title="Dê um lugar às suas ideias"
              onAdd={() => create("note")}
            />
          )}
        </section>
      </div>
      {history && (
        <Modal title="Histórico da nota" onClose={() => setHistory(null)}>
          <div className="form-body">
            {history.map((h) => (
              <div className="history-entry" key={h.id}>
                <strong>{new Date(h.at).toLocaleString("pt-BR")}</strong>
                <pre>
                  {(h.after?.notes || h.after?.title || "").slice(0, 500)}
                </pre>
                <Button
                  onClick={() => {
                    if (h.after)
                      update({ notes: h.after.notes, title: h.after.title });
                    setHistory(null);
                  }}
                >
                  Restaurar esta versão
                </Button>
              </div>
            ))}
            {!history.length && (
              <Empty title="O histórico começa na primeira edição" />
            )}
          </div>
        </Modal>
      )}
    </>
  );
}
