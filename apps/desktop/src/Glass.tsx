import React, { useEffect, useRef, useState } from "react";
import {
  Pin,
  PinOff,
  MoreHorizontal,
  X,
  Minus,
  ArrowUpRight,
  GripHorizontal,
} from "lucide-react";
import { Snapshot } from "../shared/model";
import { PanelRole, panelSpecs, PanelSettings } from "../shared/glass";
import { api } from "./ui";
interface Info {
  role: PanelRole;
  settings: PanelSettings;
  nativeGlass: boolean;
  reducedMotion: boolean;
  displays: { id: number; label: string }[];
}

export function VeyraGlassPanel({
  role,
  data,
  children,
  exiting = false,
}: {
  role: PanelRole;
  data: Snapshot;
  children: React.ReactNode;
  exiting?: boolean;
}) {
  const [info, setInfo] = useState<Info | null>(null),
    [menu, setMenu] = useState(false),
    [closing, setClosing] = useState(false);
  const [error, setError] = useState("");
  const menuRef = useRef<HTMLDivElement>(null),
    resizeFrame = useRef<number>(0),
    resizing = useRef(false),
    resizeSending = useRef(false);
  const update = (
    patch: Partial<PanelSettings> | { monitor: number } | { reset: true },
  ) =>
    void api("panelUpdate", patch)
      .then(setInfo)
      .catch((e) => setError(e.message));
  useEffect(() => {
    void api("panelInfo")
      .then(setInfo)
      .catch((e) => setError(e.message));
  }, [data.uid, data.desktop.panels?.[role], data.desktop.reducedEffects]);
  useEffect(() => {
    void api("panelMenu", menu).catch((e) => setError(e.message));
  }, [menu]);
  useEffect(() => {
    const close = () => {
      setClosing(true);
      setTimeout(() => void api("closeWindow"), info?.reducedMotion ? 0 : 110);
    };
    const key = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        if (menu) {
          e.preventDefault();
          e.stopPropagation();
          setMenu(false);
        } else if (
          role === "quick" &&
          !document.querySelector('.modal[aria-modal="true"]')
        ) {
          e.preventDefault();
          e.stopPropagation();
          close();
        }
      }
    };
    const pointer = (e: PointerEvent) => {
      if (menu && !menuRef.current?.contains(e.target as Node)) setMenu(false);
    };
    window.addEventListener("keydown", key, true);
    window.addEventListener("pointerdown", pointer);
    return () => {
      window.removeEventListener("keydown", key, true);
      window.removeEventListener("pointerdown", pointer);
      cancelAnimationFrame(resizeFrame.current);
    };
  }, [menu, role, info?.reducedMotion]);
  const s = info?.settings,
    mode = s?.mode || "normal";
  const resize = (e: React.PointerEvent<HTMLDivElement>, edge: string) => {
    if (e.button !== 0) return;
    e.preventDefault();
    e.currentTarget.setPointerCapture(e.pointerId);
    resizing.current = true;
    void api("panelGesture", { action: "begin", edge });
  };
  const move = () => {
    if (!resizing.current || resizeFrame.current || resizeSending.current)
      return;
    resizeFrame.current = requestAnimationFrame(() => {
      resizeFrame.current = 0;
      resizeSending.current = true;
      void api("panelGesture", { action: "step" })
        .catch((e) => setError(e.message))
        .finally(() => {
          resizeSending.current = false;
        });
    });
  };
  const end = () => {
    if (!resizing.current) return;
    resizing.current = false;
    cancelAnimationFrame(resizeFrame.current);
    resizeFrame.current = 0;
    void api("panelGesture", { action: "end" });
  };
  return (
    <div
      className={
        "glass-window mode-" +
        mode +
        (closing || exiting ? " closing" : "") +
        (menu ? " menu-open" : "") +
        (s?.reducedEffects || data.desktop.reducedEffects || info?.reducedMotion
          ? " reduced-effects"
          : "") +
        (info?.nativeGlass ? " native-glass" : "")
      }
      data-role={role}
      onContextMenu={(e) => {
        if (
          (e.target as HTMLElement).closest("input,textarea,[contenteditable]")
        )
          return;
        e.preventDefault();
        setMenu(true);
      }}
    >
      <section
        className="veyra-glass-panel"
        aria-label={panelSpecs[role].title}
      >
        <header className="glass-header glass-drag">
          <span className="glass-brand">v</span>
          <span className="glass-title">{panelSpecs[role].title}</span>
          <GripHorizontal size={16} className="glass-grip" />
          <div className="glass-controls">
            <button
              aria-label={
                s?.alwaysOnTop
                  ? "Desafixar painel"
                  : "Fixar acima das outras janelas"
              }
              aria-pressed={s?.alwaysOnTop}
              onClick={() => update({ alwaysOnTop: !s?.alwaysOnTop })}
            >
              {s?.alwaysOnTop ? <Pin size={14} /> : <PinOff size={14} />}
            </button>
            {role !== "quick" && (
              <button
                aria-label="Recolher painel"
                onClick={() =>
                  update({ mode: mode === "normal" ? "compact" : "normal" })
                }
              >
                <Minus size={15} />
              </button>
            )}
            <button
              aria-label="Opções do painel"
              aria-expanded={menu}
              onClick={() => setMenu(!menu)}
            >
              <MoreHorizontal size={17} />
            </button>
            <button
              aria-label="Fechar painel"
              onClick={() => {
                setClosing(true);
                setTimeout(
                  () => void api("closeWindow"),
                  info?.reducedMotion ? 0 : 110,
                );
              }}
            >
              <X size={15} />
            </button>
          </div>
        </header>
        <div className="glass-content">{children}</div>
        {error && (
          <div className="glass-error" role="status">
            {error}
            <button aria-label="Fechar aviso" onClick={() => setError("")}>
              <X size={14} />
            </button>
          </div>
        )}
        {menu && (
          <div
            className="glass-menu"
            ref={menuRef}
            role="dialog"
            aria-label="Configurar painel"
          >
            <div className="glass-menu-title">
              Este painel, do seu jeito{" "}
              <button aria-label="Fechar opções" onClick={() => setMenu(false)}>
                <X size={14} />
              </button>
            </div>
            <div className="glass-modes">
              {(
                [
                  "normal",
                  "compact",
                  ...(panelSpecs[role].micro ? ["micro"] : []),
                ] as const
              ).map((m) => (
                <button
                  key={m}
                  className={mode === m ? "selected" : ""}
                  onClick={() => update({ mode: m as PanelSettings["mode"] })}
                >
                  {m === "normal"
                    ? "Expandido"
                    : m === "compact"
                      ? "Compacto"
                      : "Micro"}
                </button>
              ))}
            </div>
            <label>
              <input
                type="checkbox"
                checked={!!s?.alwaysOnTop}
                onChange={(e) => update({ alwaysOnTop: e.target.checked })}
              />
              Manter acima das outras janelas
            </label>
            {role !== "quick" && (
              <label>
                <input
                  type="checkbox"
                  checked={!!s?.clickThrough}
                  onChange={(e) => update({ clickThrough: e.target.checked })}
                />
                Deixar cliques atravessarem
              </label>
            )}
            <label>
              <input
                type="checkbox"
                checked={s?.snap !== false}
                onChange={(e) => update({ snap: e.target.checked })}
              />
              Encaixar nas bordas e painéis
            </label>
            <label>
              <input
                type="checkbox"
                checked={!!s?.reducedEffects}
                onChange={(e) => update({ reducedEffects: e.target.checked })}
              />
              Reduzir efeitos visuais
            </label>
            <label className="glass-opacity">
              Opacidade <strong>{Math.round((s?.opacity || 1) * 100)}%</strong>
              <input
                aria-label="Opacidade do painel"
                type="range"
                min="70"
                max="100"
                step="5"
                value={(s?.opacity || 1) * 100}
                onChange={(e) =>
                  update({ opacity: Number(e.target.value) / 100 })
                }
              />
            </label>
            <label>
              Monitor
              <select
                aria-label="Mover painel para monitor"
                value={s?.monitor || info?.displays[0]?.id}
                onChange={(e) => update({ monitor: Number(e.target.value) })}
              >
                {info?.displays.map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.label}
                  </option>
                ))}
              </select>
            </label>
            <button onClick={() => update({ reset: true })}>
              Restaurar tamanho e posição
            </button>
            <button onClick={() => void api("openWindow", "main")}>
              <ArrowUpRight size={14} />
              Abrir Veyra Life
            </button>
            <p>
              Recupere pelo tray ou pelo atalho definido em Configurações →
              Atalhos.
            </p>
            <small>
              {info?.nativeGlass
                ? "Vidro nativo do Windows"
                : "Transparência real · efeitos reduzidos ou sistema sem Acrylic"}
            </small>
          </div>
        )}
      </section>
      {role !== "quick" &&
        mode === "normal" &&
        ["n", "s", "e", "w", "ne", "nw", "se", "sw"].map((edge) => (
          <div
            key={edge}
            aria-label={"Redimensionar " + edge}
            className={"glass-resize resize-" + edge}
            onPointerDown={(e) => resize(e, edge)}
            onPointerMove={move}
            onPointerUp={end}
            onPointerCancel={end}
          />
        ))}
    </div>
  );
}
