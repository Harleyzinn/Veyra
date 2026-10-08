export type PanelMode = "normal" | "compact" | "micro";
export interface Rect {
  x: number;
  y: number;
  width: number;
  height: number;
}
export interface PanelDisplay {
  id: number;
  workArea: Rect;
  scaleFactor?: number;
}
export interface PanelSettings {
  enabled: boolean;
  alwaysOnTop: boolean;
  clickThrough: boolean;
  opacity: number;
  mode: PanelMode;
  snap: boolean;
  reducedEffects: boolean;
  monitor?: number;
  bounds?: Rect;
  normalBounds?: Rect;
}
export const panelSpecs = {
  mini: {
    title: "Seu Veyra",
    width: 390,
    height: 660,
    compactHeight: 340,
    micro: false,
  },
  dock: {
    title: "Veyra Dock",
    width: 330,
    height: 780,
    compactHeight: 440,
    micro: false,
  },
  quick: {
    title: "Captura rápida",
    width: 620,
    height: 590,
    compactHeight: 440,
    micro: false,
  },
  "widget-focus": {
    title: "Foco",
    width: 360,
    height: 330,
    compactHeight: 140,
    micro: true,
  },
  "widget-finance": {
    title: "Seu dinheiro",
    width: 390,
    height: 440,
    compactHeight: 220,
    micro: true,
  },
  "widget-tasks": {
    title: "Próximas tarefas",
    width: 370,
    height: 470,
    compactHeight: 250,
    micro: false,
  },
  "widget-weather": {
    title: "Seu horizonte",
    width: 350,
    height: 410,
    compactHeight: 220,
    micro: true,
  },
  "widget-calendar": {
    title: "Sua agenda",
    width: 370,
    height: 450,
    compactHeight: 240,
    micro: false,
  },
  "widget-habits": {
    title: "Hábitos",
    width: 360,
    height: 440,
    compactHeight: 230,
    micro: false,
  },
  "widget-clock": {
    title: "Hora e data",
    width: 300,
    height: 230,
    compactHeight: 145,
    micro: true,
  },
  "widget-timer": {
    title: "Cronômetro",
    width: 330,
    height: 310,
    compactHeight: 155,
    micro: true,
  },
  "widget-notes": {
    title: "Ideias por perto",
    width: 370,
    height: 450,
    compactHeight: 250,
    micro: false,
  },
} as const;
export type PanelRole = keyof typeof panelSpecs;
export const isPanelRole = (role: string): role is PanelRole =>
  Object.hasOwn(panelSpecs, role);
export function panelSettings(
  value: Partial<PanelSettings> = {},
): PanelSettings {
  return {
    enabled: value.enabled === true,
    alwaysOnTop: value.alwaysOnTop !== false,
    clickThrough: value.clickThrough === true,
    opacity: Math.min(1, Math.max(0.7, Number(value.opacity) || 1)),
    mode: ["normal", "compact", "micro"].includes(value.mode || "")
      ? value.mode!
      : "normal",
    snap: value.snap !== false,
    reducedEffects: value.reducedEffects === true,
    monitor: value.monitor,
    bounds: value.bounds,
    normalBounds: value.normalBounds,
  };
}
export function visibleBounds(
  saved: Partial<Rect> | undefined,
  displays: PanelDisplay[],
  preferred?: number,
  fallback = { width: 360, height: 420 },
): Rect {
  const valid = (n: unknown): n is number =>
    typeof n === "number" && Number.isFinite(n);
  const overlap = (area: Rect) =>
    saved &&
    valid(saved.x) &&
    valid(saved.y) &&
    valid(saved.width) &&
    valid(saved.height) &&
    saved.x + saved.width > area.x &&
    saved.y + saved.height > area.y &&
    saved.x < area.x + area.width &&
    saved.y < area.y + area.height;
  const display =
    displays.find((d) => d.id === preferred) ||
    displays.find((d) => overlap(d.workArea)) ||
    displays[0];
  const area = display.workArea;
  const width = Math.min(
    area.width,
    Math.max(180, valid(saved?.width) ? saved.width : fallback.width),
  );
  const height = Math.min(
    area.height,
    Math.max(86, valid(saved?.height) ? saved.height : fallback.height),
  );
  const x =
    saved && valid(saved.x) && overlap(area)
      ? saved.x
      : area.x + Math.round((area.width - width) / 2);
  const y =
    saved && valid(saved.y) && overlap(area)
      ? saved.y
      : area.y + Math.round((area.height - height) / 2);
  return {
    x: Math.round(Math.max(area.x, Math.min(x, area.x + area.width - width))),
    y: Math.round(Math.max(area.y, Math.min(y, area.y + area.height - height))),
    width: Math.round(width),
    height: Math.round(height),
  };
}
export function magneticBounds(
  bounds: Rect,
  area: Rect,
  neighbors: Rect[],
  threshold = 10,
): Rect {
  let { x, y } = bounds;
  const xs = [area.x, area.x + area.width - bounds.width];
  const ys = [area.y, area.y + area.height - bounds.height];
  for (const n of neighbors) {
    if (y + bounds.height > n.y - threshold && y < n.y + n.height + threshold)
      xs.push(
        n.x,
        n.x + n.width - bounds.width,
        n.x - bounds.width - 8,
        n.x + n.width + 8,
      );
    if (x + bounds.width > n.x - threshold && x < n.x + n.width + threshold)
      ys.push(
        n.y,
        n.y + n.height - bounds.height,
        n.y - bounds.height - 8,
        n.y + n.height + 8,
      );
  }
  const nearest = (v: number, targets: number[]) =>
    targets.reduce(
      (best, t) =>
        Math.abs(v - t) <= threshold && Math.abs(v - t) < Math.abs(v - best)
          ? t
          : best,
      v + threshold + 1,
    );
  const nx = nearest(x, xs),
    ny = nearest(y, ys);
  if (Math.abs(nx - x) <= threshold) x = nx;
  if (Math.abs(ny - y) <= threshold) y = ny;
  return {
    ...bounds,
    x: Math.max(area.x, Math.min(x, area.x + area.width - bounds.width)),
    y: Math.max(area.y, Math.min(y, area.y + area.height - bounds.height)),
  };
}
export function roundedShape(
  width: number,
  height: number,
  radius = 24,
): Rect[] {
  const r = Math.min(radius, Math.floor(Math.min(width, height) / 2));
  const rows: Rect[] = [{ x: 0, y: r, width, height: height - 2 * r }];
  for (let y = 0; y < r; y++) {
    const inset = Math.ceil(r - Math.sqrt(r * r - (r - y - 0.5) ** 2));
    rows.push(
      { x: inset, y, width: width - 2 * inset, height: 1 },
      { x: inset, y: height - y - 1, width: width - 2 * inset, height: 1 },
    );
  }
  return rows;
}
