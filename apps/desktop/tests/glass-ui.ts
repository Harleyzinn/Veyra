import {
  _electron as electron,
  ElectronApplication,
  Page,
} from "@playwright/test";
import assert from "node:assert/strict";
import { resolve } from "node:path";
import { mkdirSync, writeFileSync } from "node:fs";
import { spawnSync } from "node:child_process";
const profile = resolve(".qa/glass-" + Date.now());
mkdirSync(profile, { recursive: true });
const packaged = process.env.VEYRA_PANEL_PACKAGED === "1";
async function launch() {
  const env: NodeJS.ProcessEnv = {
    ...process.env,
    VEYRA_QA: "1",
    VEYRA_QA_DATA: profile,
  };
  delete env.ELECTRON_RUN_AS_NODE;
  return electron.launch({
    executablePath: resolve(
      packaged
        ? "release/win-unpacked/Veyra Life.exe"
        : "node_modules/electron/dist/electron.exe",
    ),
    args: packaged ? ["--user-data-dir=" + profile] : ["."],
    env: env as Record<string, string>,
  });
}
async function native(
  app: ElectronApplication,
  role: string,
  offset = { x: 80, y: 20 },
) {
  const meta = await app.evaluate(
    ({ BrowserWindow, screen }, { role, offset }) => {
      const w = BrowserWindow.getAllWindows().find((w) =>
        w.webContents.getURL().endsWith("#" + role),
      )!;
      const b = w.getBounds(),
        point = screen.dipToScreenPoint({
          x: b.x + offset.x,
          y: b.y + offset.y,
        });
      return {
        handle: w.getNativeWindowHandle().readBigUInt64LE().toString(16),
        point,
      };
    },
    { role, offset },
  );
  const ps = spawnSync(
    process.env.VEYRA_POWERSHELL || "powershell.exe",
    [
      "-NoProfile",
      "-File",
      resolve("tests/native-window-read.ps1"),
      "-WindowHandle",
      meta.handle,
      "-X",
      String(meta.point.x),
      "-Y",
      String(meta.point.y),
    ],
    { encoding: "utf8", windowsHide: true },
  );
  assert.equal(ps.status, 0, ps.stderr);
  return JSON.parse(ps.stdout.trim());
}
async function panel(app: ElectronApplication, page: Page, role: string) {
  await page.evaluate((role) => window.veyra.call("openWindow", role), role);
  for (let retry = 0; retry < 40; retry++) {
    for (const p of app.windows())
      if (p.url().endsWith("#" + role)) {
        await p.waitForSelector(".veyra-glass-panel");
        return p;
      }
    await page.waitForTimeout(100);
  }
  throw Error("Panel did not open: " + role);
}
async function run() {
  let app = await launch();
  try {
    const main = await app.firstWindow();
    await main.waitForSelector(".sidebar");
    if (
      await main
        .getByRole("button", {
          name: "Continuar no espaço visitante",
          exact: true,
        })
        .isVisible()
    )
      await main
        .getByRole("button", {
          name: "Continuar no espaço visitante",
          exact: true,
        })
        .click();
    await main.evaluate(() => window.veyra.call("snapshot"));
    const mini = await panel(app, main, "mini");
    const nativeMini = await native(app, "mini");
    assert.equal(nativeMini.caption, false);
    assert.equal(nativeMini.thickFrame, false);
    assert.equal(nativeMini.cornerInside, false);
    assert(nativeMini.regionKind > 0);
    assert.equal(nativeMini.hitTest, 2);
    assert.equal(nativeMini.topMost, true);
    const material = await mini.evaluate(() => window.veyra.call("panelInfo"));
    if (material.nativeGlass) assert.equal(nativeMini.backdrop, 3);
    console.log("Native background material:", nativeMini.backdrop);
    assert.equal(
      await mini.evaluate(
        () => getComputedStyle(document.documentElement).backgroundColor,
      ),
      "rgba(0, 0, 0, 0)",
    );
    await mini.screenshot({
      path: resolve(".qa/glass-mini.png"),
      omitBackground: true,
    });
    console.log(
      "Mini Dashboard: real frameless HWND, transparent root and native corner region passed.",
    );
    const focus = await panel(app, main, "widget-focus");
    await main.evaluate(() =>
      window.veyra.call("focusStart", {
        minutes: 25,
        title: "Estudar matemática",
        mode: "Pomodoro",
      }),
    );
    await focus
      .getByRole("button", { name: "Pausar foco", exact: true })
      .click();
    await focus
      .getByRole("button", { name: "Retomar foco", exact: true })
      .waitFor();
    await focus.screenshot({
      path: resolve(".qa/glass-focus.png"),
      omitBackground: true,
    });
    await focus.evaluate(() =>
      window.veyra.call("panelUpdate", {
        mode: "micro",
        alwaysOnTop: false,
        opacity: 0.8,
      }),
    );
    assert.equal(
      await app.evaluate(({ BrowserWindow }) =>
        BrowserWindow.getAllWindows()
          .find((w) => w.webContents.getURL().endsWith("#widget-focus"))!
          .isAlwaysOnTop(),
      ),
      false,
    );
    const dims = await app.evaluate(({ BrowserWindow }) =>
      BrowserWindow.getAllWindows()
        .find((w) => w.webContents.getURL().endsWith("#widget-focus"))!
        .getBounds(),
    );
    assert.equal(dims.height, 96);
    await focus.getByRole("button", { name: "Opções do painel" }).click();
    await focus.getByRole("dialog", { name: "Configurar painel" }).waitFor();
    await focus.getByRole("button", { name: "Expandido", exact: true }).click();
    await focus
      .getByRole("button", { name: "Fechar opções", exact: true })
      .click();
    const resizeBefore = await app.evaluate(({ BrowserWindow }) =>
      BrowserWindow.getAllWindows()
        .find((w) => w.webContents.getURL().endsWith("#widget-focus"))!
        .getBounds(),
    );
    await app.evaluate(({ screen }) => {
      (globalThis as any).veyraCursorRead = screen.getCursorScreenPoint;
      screen.getCursorScreenPoint = () => ({ x: 100, y: 100 });
    });
    try {
      await focus.evaluate(() =>
        window.veyra.call("panelGesture", { action: "begin", edge: "se" }),
      );
      await app.evaluate(({ screen }) => {
        screen.getCursorScreenPoint = () => ({ x: 140, y: 130 });
      });
      await focus.evaluate(() =>
        window.veyra.call("panelGesture", { action: "step" }),
      );
      await focus.evaluate(() =>
        window.veyra.call("panelGesture", { action: "end" }),
      );
      const resizeAfter = await app.evaluate(({ BrowserWindow }) =>
        BrowserWindow.getAllWindows()
          .find((w) => w.webContents.getURL().endsWith("#widget-focus"))!
          .getBounds(),
      );
      assert.equal(resizeAfter.width, resizeBefore.width + 40);
      assert.equal(resizeAfter.height, resizeBefore.height + 30);
      assert.equal((await native(app, "widget-focus")).cornerInside, false);
    } finally {
      await app.evaluate(({ screen }) => {
        screen.getCursorScreenPoint = (globalThis as any).veyraCursorRead;
        delete (globalThis as any).veyraCursorRead;
      });
    }
    await main.evaluate(() =>
      window.veyra.call("shortcuts", {
        capture: "CommandOrControl+Alt+Shift+F6",
        task: "CommandOrControl+Alt+Shift+F7",
        expense: "CommandOrControl+Alt+Shift+F8",
        note: "CommandOrControl+Alt+Shift+F9",
        focus: "CommandOrControl+Alt+Shift+F10",
        recover: "CommandOrControl+Alt+Shift+F12",
      }),
    );
    await focus.evaluate(() =>
      window.veyra.call("panelUpdate", { clickThrough: true }),
    );
    assert.equal((await native(app, "widget-focus")).clickThrough, true);
    await main.evaluate(() => window.veyra.call("panelsAll", "recover"));
    assert.equal((await native(app, "widget-focus")).clickThrough, false);
    console.log(
      "Focus Overlay: live focus pause, micro mode, menu expansion, native pin/opacity and click-through recovery passed.",
    );
    const quick = await panel(app, main, "quick");
    await assert.rejects(
      quick.evaluate(() =>
        window.veyra.call("panelUpdate", { clickThrough: true }),
      ),
      /receber cliques/,
    );
    await quick
      .getByRole("textbox", { name: "Digite uma ação" })
      .fill("Captura descartável de teste");
    await quick.screenshot({
      path: resolve(".qa/glass-capture.png"),
      omitBackground: true,
    });
    await quick.getByRole("button", { name: /Nota/ }).click();
    await quick
      .getByRole("button", { name: "Salvar registro", exact: true })
      .click();
    await quick.waitForEvent("close");
    const state = await main.evaluate(() => window.veyra.call("snapshot"));
    assert(
      state.items.some((i: any) => i.title === "Captura descartável de teste"),
    );
    for (const role of [
      "widget-finance",
      "widget-tasks",
      "widget-weather",
      "widget-habits",
      "widget-calendar",
      "widget-timer",
      "dock",
    ]) {
      const p = await panel(app, main, role);
      assert.equal((await native(app, role)).caption, false);
      await p.evaluate(() =>
        window.veyra.call("panelUpdate", { reducedEffects: true }),
      );
      assert.equal((await native(app, role)).backdrop, 1);
    }
    await mini.evaluate(() =>
      window.veyra.call("panelUpdate", {
        mode: "compact",
        alwaysOnTop: false,
        opacity: 0.9,
      }),
    );
    await main.evaluate(() => window.veyra.call("desktop", { theme: "light" }));
    await mini.waitForTimeout(200);
    assert.equal(
      await mini.evaluate(() => document.documentElement.dataset.theme),
      "light",
    );
    await mini.screenshot({
      path: resolve(".qa/glass-light.png"),
      omitBackground: true,
    });
    await main.evaluate(() =>
      window.veyra.call("desktop", { theme: "system" }),
    );
    await app.evaluate(({ nativeTheme }) => {
      nativeTheme.themeSource = "dark";
    });
    await mini.emulateMedia({ colorScheme: "dark" });
    await mini.waitForTimeout(200);
    assert.equal(
      await mini.evaluate(() => document.documentElement.dataset.theme),
      "dark",
    );
    await app.evaluate(({ nativeTheme }) => {
      nativeTheme.themeSource = "light";
    });
    await mini.emulateMedia({ colorScheme: "light" });
    await mini.waitForTimeout(200);
    assert.equal(
      await mini.evaluate(() => document.documentElement.dataset.theme),
      "light",
    );
    await app.evaluate(({ BrowserWindow }) => {
      BrowserWindow.getAllWindows()
        .find((w) => w.webContents.getURL().endsWith("#mini"))!
        .setBounds({ x: -30000, y: -30000 });
    });
    await main.evaluate(() => window.veyra.call("panelsAll", "recover"));
    const visible = await mini.evaluate(() => window.veyra.call("panelInfo"));
    assert(
      visible.displays.some((d: any) => {
        const a = d.workArea,
          b = visible.settings.bounds;
        return (
          b.x >= a.x &&
          b.y >= a.y &&
          b.x + b.width <= a.x + a.width &&
          b.y + b.height <= a.y + a.height
        );
      }),
    );
    await app.evaluate(({ powerMonitor }) => powerMonitor.emit("resume"));
    await app.evaluate(({ app }) => app.getAppMetrics());
    await mini.waitForTimeout(2000);
    const metrics = await app.evaluate(({ app }) =>
      app.getAppMetrics().map((m) => ({
        pid: m.pid,
        type: m.type,
        cpu: m.cpu.percentCPUUsage,
        workingSetKB: m.memory.workingSetSize,
      })),
    );
    writeFileSync(
      resolve(".qa/glass-process-metrics.json"),
      JSON.stringify(metrics, null, 2),
    );
    const gpu = metrics.find((m) => m.type === "GPU");
    if (gpu) {
      const counters = spawnSync(
        process.env.VEYRA_POWERSHELL || "powershell.exe",
        [
          "-NoProfile",
          "-File",
          resolve("tests/gpu-read.ps1"),
          "-GpuPid",
          String(gpu.pid),
        ],
        { encoding: "utf8", windowsHide: true },
      );
      if (counters.status === 0)
        writeFileSync(
          resolve(".qa/glass-gpu-metrics.json"),
          counters.stdout.trim(),
        );
    }
    await app.close();
    app = await launch();
    let reopened = app.windows().find((p) => p.url().endsWith("#main"));
    for (let retry = 0; !reopened && retry < 40; retry++) {
      await new Promise((r) => setTimeout(r, 100));
      reopened = app.windows().find((p) => p.url().endsWith("#main"));
    }
    assert(reopened);
    await reopened.waitForSelector(".sidebar");
    const restored = await panel(app, reopened, "mini");
    const info = await restored.evaluate(() => window.veyra.call("panelInfo"));
    assert.equal(info.settings.mode, "compact");
    assert.equal(info.settings.alwaysOnTop, false);
    assert.equal(info.settings.opacity, 0.9);
    console.log(
      "Windows panels: quick save/close, remaining widgets, light/dark styling, per-panel settings and restart restoration passed.",
    );
  } finally {
    try {
      const owner = app.windows().find((p) => p.url().endsWith("#main"));
      if (owner)
        await owner.evaluate(() => window.veyra.call("quit")).catch(() => {});
      await Promise.race([
        app.close(),
        new Promise((_, reject) =>
          setTimeout(() => reject(Error("Native shutdown timed out")), 10000),
        ),
      ]);
    } catch (e) {
      app.process().kill();
      throw e;
    }
  }
}
run().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});
