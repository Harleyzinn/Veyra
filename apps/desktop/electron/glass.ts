import {
  BrowserWindow,
  screen,
  nativeTheme,
  systemPreferences,
  powerMonitor,
} from "electron";
import { release } from "node:os";
import {
  isPanelRole,
  panelSpecs,
  panelSettings,
  visibleBounds,
  magneticBounds,
  roundedShape,
  PanelRole,
  PanelSettings,
  Rect,
} from "../shared/glass";

export class VeyraGlassWindows {
  private timers = new Map<string, ReturnType<typeof setTimeout>>();
  private materials = new Map<string, boolean>();
  private menuBounds = new Map<string, Rect>();
  private gestures = new Map<
    number,
    {
      role: PanelRole;
      bounds: Rect;
      cursor: { x: number; y: number };
      edge: string;
    }
  >();
  private shuttingDown = false;
  private applying = new Set<string>();
  constructor(
    private windows: Map<string, BrowserWindow>,
    private read: () => any,
    private save: (data: Record<string, any>) => void,
    private open: (role: string) => BrowserWindow,
  ) {}
  settings(role: PanelRole) {
    return panelSettings(this.read().panels?.[role]);
  }
  bounds(role: PanelRole) {
    const settings = this.settings(role),
      spec = panelSpecs[role];
    return visibleBounds(
      settings.bounds || this.read().windows?.[role],
      screen.getAllDisplays(),
      settings.monitor,
      { width: spec.width, height: spec.height },
    );
  }
  private remember(role: PanelRole, patch: Partial<PanelSettings>) {
    const next = { ...this.settings(role), ...patch };
    if (JSON.stringify(next) === JSON.stringify(this.read().panels?.[role]))
      return;
    this.save({
      panels: {
        ...this.read().panels,
        [role]: next,
      },
    });
  }
  options(role: PanelRole) {
    return {
      ...this.bounds(role),
      minWidth: 180,
      minHeight: 86,
      frame: false,
      transparent: true,
      backgroundColor: "#00000000",
      thickFrame: false,
      roundedCorners: false,
      resizable: false,
      maximizable: false,
      fullscreenable: false,
      hasShadow: false,
      skipTaskbar: true,
      alwaysOnTop: this.settings(role).alwaysOnTop,
    };
  }
  attach(role: PanelRole, w: BrowserWindow) {
    this.remember(role, { enabled: true });
    const schedule = () => {
      if (
        this.applying.has(role) ||
        this.shuttingDown ||
        this.menuBounds.has(role)
      )
        return;
      clearTimeout(this.timers.get(role));
      this.timers.set(
        role,
        setTimeout(() => {
          this.timers.delete(role);
          if (w.isDestroyed()) return;
          let bounds = w.getBounds();
          const display = screen.getDisplayMatching(bounds);
          if (
            this.settings(role).snap &&
            ![...this.gestures.values()].some((g) => g.role === role)
          ) {
            const neighbors = [...this.windows]
              .filter(
                ([r, n]) =>
                  isPanelRole(r) &&
                  r !== role &&
                  !n.isDestroyed() &&
                  n.isVisible(),
              )
              .map(([, n]) => n.getBounds());
            bounds = magneticBounds(bounds, display.workArea, neighbors);
            this.applying.add(role);
            w.setBounds(bounds);
            this.applying.delete(role);
          }
          this.remember(role, { bounds, monitor: display.id });
        }, 250),
      );
    };
    w.on("move", schedule);
    w.on("resize", () => {
      this.shape(w);
      schedule();
    });
    w.on("close", () => {
      if (!this.shuttingDown)
        this.remember(role, {
          enabled: false,
          bounds: this.menuBounds.get(role) || w.getBounds(),
          monitor: screen.getDisplayMatching(w.getBounds()).id,
        });
    });
    const contentsId = w.webContents.id;
    w.on("closed", () => {
      clearTimeout(this.timers.get(role));
      this.timers.delete(role);
      this.materials.delete(role);
      this.menuBounds.delete(role);
      this.gestures.delete(contentsId);
    });
    this.apply(role, w);
  }
  private shape(w: BrowserWindow) {
    try {
      const { width, height } = w.getBounds();
      w.setShape(roundedShape(width, height));
      return true;
    } catch {
      return false;
    }
  }
  private apply(role: PanelRole, w: BrowserWindow) {
    const settings = this.settings(role);
    w.setAlwaysOnTop(settings.alwaysOnTop, "floating");
    w.setOpacity(settings.opacity);
    // Capture has input fields; it must always remain interactive.
    if (settings.clickThrough && !this.read().panelRecoveryAvailable)
      this.remember(role, { clickThrough: false });
    w.setIgnoreMouseEvents(
      settings.clickThrough &&
        this.read().panelRecoveryAvailable &&
        role !== "quick",
      { forward: true },
    );
    const shaped = this.shape(w);
    let acrylic = false;
    const build = Number(release().split(".")[2] || 0);
    if (process.platform === "win32" && build >= 22621) {
      try {
        acrylic =
          shaped &&
          !settings.reducedEffects &&
          !this.read().reducedEffects &&
          this.read().performanceMode !== "economy" &&
          !(
            this.read().performanceMode === "auto" &&
            powerMonitor.isOnBatteryPower()
          ) &&
          !nativeTheme.shouldUseHighContrastColors &&
          !nativeTheme.prefersReducedTransparency;
        w.setBackgroundMaterial(acrylic ? "acrylic" : "none");
      } catch {
        acrylic = false;
        try {
          w.setBackgroundMaterial("none");
        } catch {}
      }
    }
    this.materials.set(role, acrylic);
  }
  role(w: BrowserWindow): PanelRole {
    const role = [...this.windows].find(([, value]) => value === w)?.[0];
    if (!role || !isPanelRole(role))
      throw Error("Esta janela não é um painel flutuante.");
    return role;
  }
  info(w: BrowserWindow) {
    const role = this.role(w);
    return {
      role,
      settings: this.settings(role),
      nativeGlass: this.materials.get(role) === true,
      reducedMotion:
        systemPreferences.getAnimationSettings().prefersReducedMotion,
      displays: screen.getAllDisplays().map((d) => ({
        id: d.id,
        label: d.label || "Monitor " + d.id,
        workArea: d.workArea,
      })),
    };
  }
  update(w: BrowserWindow, patch: any) {
    const role = this.role(w);
    if (
      !patch ||
      typeof patch !== "object" ||
      Object.keys(patch).some(
        (k) =>
          ![
            "alwaysOnTop",
            "clickThrough",
            "opacity",
            "mode",
            "snap",
            "reducedEffects",
            "monitor",
            "reset",
          ].includes(k),
      )
    )
      throw Error("Configuração de painel inválida.");
    const previous = this.settings(role);
    for (const key of ["alwaysOnTop", "clickThrough", "snap", "reducedEffects"])
      if (key in patch && typeof patch[key] !== "boolean")
        throw Error("Opção inválida.");
    if (
      "opacity" in patch &&
      (!Number.isFinite(patch.opacity) ||
        patch.opacity < 0.7 ||
        patch.opacity > 1)
    )
      throw Error("Opacidade inválida.");
    if (
      "mode" in patch &&
      (!["normal", "compact", "micro"].includes(patch.mode) ||
        (patch.mode === "micro" && !panelSpecs[role].micro))
    )
      throw Error("Modo inválido.");
    if (role === "quick" && patch.clickThrough)
      throw Error("Captura rápida precisa receber cliques.");
    if (patch.clickThrough && !this.read().panelRecoveryAvailable)
      throw Error(
        "Ative um atalho de recuperação nas configurações antes de usar click-through.",
      );
    const clean = { ...patch };
    delete clean.reset;
    delete clean.monitor;
    let target = this.menuBounds.get(role) || w.getBounds();
    if (patch.mode && patch.mode !== previous.mode) {
      if (previous.mode === "normal") clean.normalBounds = target;
      const spec = panelSpecs[role];
      target =
        patch.mode === "normal"
          ? previous.normalBounds || {
              ...target,
              width: spec.width,
              height: spec.height,
            }
          : {
              ...target,
              width: patch.mode === "micro" ? 230 : Math.min(target.width, 390),
              height: patch.mode === "micro" ? 96 : spec.compactHeight,
            };
    }
    const displays = screen.getAllDisplays();
    if ("monitor" in patch) {
      const display = displays.find((d) => d.id === patch.monitor);
      if (!display) throw Error("Monitor indisponível.");
      target = {
        ...target,
        x: display.workArea.x + 32,
        y: display.workArea.y + 32,
      };
    }
    if (patch.reset) {
      const spec = panelSpecs[role];
      target = {
        ...spec,
        x: NaN,
        y: NaN,
        width: spec.width,
        height: spec.height,
      };
      clean.mode = "normal";
      clean.clickThrough = false;
    }
    target = visibleBounds(target, displays, patch.monitor);
    this.applying.add(role);
    if (this.menuBounds.has(role)) {
      this.menuBounds.set(role, target);
      w.setBounds(
        visibleBounds(
          {
            ...target,
            width: Math.max(target.width, 350),
            height: Math.max(target.height, 440),
          },
          displays,
        ),
      );
    } else w.setBounds(target);
    this.applying.delete(role);
    this.remember(role, {
      ...clean,
      bounds: target,
      monitor: screen.getDisplayMatching(target).id,
    });
    this.apply(role, w);
    return this.info(w);
  }
  menu(w: BrowserWindow, open: boolean) {
    const role = this.role(w);
    this.applying.add(role);
    if (open && !this.menuBounds.has(role)) {
      const bounds = w.getBounds();
      this.menuBounds.set(role, bounds);
      w.setBounds(
        visibleBounds(
          {
            ...bounds,
            width: Math.max(bounds.width, 350),
            height: Math.max(bounds.height, 440),
          },
          screen.getAllDisplays(),
        ),
      );
    } else if (!open && this.menuBounds.has(role)) {
      const bounds = this.menuBounds.get(role)!;
      w.setBounds(visibleBounds(bounds, screen.getAllDisplays()));
      this.menuBounds.delete(role);
    }
    this.applying.delete(role);
    return true;
  }
  gesture(w: BrowserWindow, value: any) {
    const role = this.role(w),
      id = w.webContents.id;
    if (!value || !["begin", "step", "end"].includes(value.action))
      throw Error("Movimento inválido.");
    if (value.action === "begin") {
      if (!["n", "s", "e", "w", "ne", "nw", "se", "sw"].includes(value.edge))
        throw Error("Borda inválida.");
      this.gestures.set(id, {
        role,
        bounds: w.getBounds(),
        cursor: screen.getCursorScreenPoint(),
        edge: value.edge,
      });
      return true;
    }
    const gesture = this.gestures.get(id);
    if (!gesture) return false;
    if (value.action === "end") {
      this.gestures.delete(id);
      this.remember(role, {
        bounds: w.getBounds(),
        monitor: screen.getDisplayMatching(w.getBounds()).id,
      });
      return true;
    }
    const cursor = screen.getCursorScreenPoint(),
      dx = cursor.x - gesture.cursor.x,
      dy = cursor.y - gesture.cursor.y;
    const original = gesture.bounds,
      area = screen.getDisplayMatching(original).workArea;
    let { x, y, width, height } = original;
    if (gesture.edge.includes("e")) width += dx;
    if (gesture.edge.includes("s")) height += dy;
    if (gesture.edge.includes("w")) {
      width -= dx;
      x += dx;
    }
    if (gesture.edge.includes("n")) {
      height -= dy;
      y += dy;
    }
    width = Math.max(180, Math.min(width, area.width));
    height = Math.max(86, Math.min(height, area.height));
    if (gesture.edge.includes("w")) x = original.x + original.width - width;
    if (gesture.edge.includes("n")) y = original.y + original.height - height;
    w.setBounds(
      visibleBounds(
        { x, y, width, height },
        screen.getAllDisplays(),
        screen.getDisplayMatching(original).id,
      ),
    );
    return true;
  }
  all(action: string) {
    if (
      !["hide", "show", "recover", "restore", "disableClickThrough"].includes(
        action,
      )
    )
      throw Error("Ação de painéis inválida.");
    if (action === "restore") {
      for (const role of Object.keys(panelSpecs) as PanelRole[])
        if (role !== "quick" && role !== "brief" && this.settings(role).enabled)
          this.open(role);
      return true;
    }
    for (const [role, w] of this.windows)
      if (isPanelRole(role) && !w.isDestroyed()) {
        if (action === "hide") {
          w.hide();
          continue;
        }
        if (action === "recover" || action === "disableClickThrough")
          this.remember(role, { clickThrough: false });
        if (action === "recover") {
          const bounds = visibleBounds(w.getBounds(), screen.getAllDisplays());
          this.applying.add(role);
          w.setBounds(bounds);
          this.applying.delete(role);
          this.remember(role, {
            bounds,
            monitor: screen.getDisplayMatching(bounds).id,
          });
        }
        this.apply(role, w);
        if (action !== "disableClickThrough") w.show();
        if (action === "recover") w.focus();
      }
    return true;
  }
  reapply() {
    this.gestures.clear();
    this.menuBounds.clear();
    for (const [role, w] of this.windows)
      if (isPanelRole(role) && !w.isDestroyed()) {
        this.applying.add(role);
        w.setBounds(this.bounds(role));
        this.applying.delete(role);
        this.apply(role, w);
      }
  }
  shutdown() {
    // Preserve enabled state for restart; close buttons disable individual panels.
    for (const [role, w] of this.windows)
      if (isPanelRole(role) && !w.isDestroyed())
        this.remember(role, {
          enabled: true,
          bounds: this.menuBounds.get(role) || w.getBounds(),
          monitor: screen.getDisplayMatching(w.getBounds()).id,
        });
    this.shuttingDown = true;
    for (const timer of this.timers.values()) clearTimeout(timer);
    this.timers.clear();
    this.gestures.clear();
  }
}
