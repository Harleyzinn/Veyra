import { encodeBackup, decodeBackup } from "./backup";
import { repeatedTask } from "../shared/productivity";
import { desktopPatch } from "../shared/settings";
import {
  planAutomations,
  taskBlocked,
  validateTaskDependencies,
} from "../shared/platform";
import { platform, release } from "node:os";
import { reportCSV, readCSV } from "../shared/reporting";
import { clipboard } from "electron";
import {
  app,
  BrowserWindow,
  ipcMain,
  protocol,
  net,
  session,
  Menu,
  Tray,
  globalShortcut,
  safeStorage,
  dialog,
  Notification,
  shell,
  screen,
  nativeImage,
  powerMonitor,
  nativeTheme,
} from "electron";
import { join, resolve, basename, sep } from "node:path";
import {
  existsSync,
  readFileSync,
  writeFileSync,
  statSync,
  mkdirSync,
} from "node:fs";
import { pathToFileURL } from "node:url";
import { spawn } from "node:child_process";
import { SecureFiles, Vault, sha } from "./vault";
import { FirebaseClient, FirebaseConfig, SyncEngine } from "./firebase";
import { systemGoogle } from "./google-login";
import { DesktopUpdater } from "./updater";
import { VeyraGlassWindows } from "./glass";
import { isPanelRole, panelSpecs } from "../shared/glass";
import {
  Item,
  Snapshot,
  createItem,
  withoutBinary,
  today,
  parseMinor,
  validDate,
} from "../shared/model";
import {
  finance,
  normalize,
  validateFinance,
  installmentItems,
  invoices,
  currency,
} from "../shared/finance";
const qa = !app.isPackaged && process.env.VEYRA_QA === "1";
// Honor an explicit isolated profile for packaged validation and portable use.
// This never changes Firebase configuration or renderer security.
const profileDirectory = app.commandLine.getSwitchValue("user-data-dir");
if (profileDirectory) {
  const directory = resolve(profileDirectory);
  mkdirSync(directory, { recursive: true });
  app.setPath("userData", directory);
  app.setPath("sessionData", directory);
}
if (qa) {
  const dir = process.env.VEYRA_QA_DATA;
  if (!dir || !resolve(dir).startsWith(resolve(app.getAppPath(), ".qa")))
    throw Error("Cache de teste fora do diretório dedicado.");
  app.setPath("userData", resolve(dir));
}
const instance = app.requestSingleInstanceLock();
if (!instance) app.quit();
protocol.registerSchemesAsPrivileged([
  {
    scheme: "veyra",
    privileges: { standard: true, secure: true, supportFetchAPI: true },
  },
]);
const version = "4.0.0";
const bootAt = Date.now();
let firstPaintMs = 0;
let financeCache: {
  vault: Vault;
  key: string;
  value: Snapshot["finance"];
} | null = null;
let snapshotCache: { vault: Vault; version: number; items: Item[] } | null =
  null;
let files: SecureFiles,
  vault: Vault,
  client: FirebaseClient,
  engine: SyncEngine,
  updater: DesktopUpdater;
let glass: VeyraGlassWindows | undefined;
let quitting = false,
  tray: Tray | null = null;
const windows = new Map<string, BrowserWindow>();
const approvedDrops = new Map<
  number,
  { path: string; uid: string | null; at: number }
>();
let googleAttempt: ReturnType<typeof systemGoogle> | null = null;
let weatherBusy = false;
let importPending: any = null;
let financeImportPending: any = null;
const uiDir = join(__dirname, "../ui");
const resources = () =>
  app.isPackaged ? process.resourcesPath : join(app.getAppPath(), "resources");
const desktop = () => JSON.parse(vault.meta("desktop") || "{}");
function desktopSave(data: Record<string, any>) {
  vault.setMeta("desktop", JSON.stringify({ ...desktop(), ...data }));
  changed();
}
function changed() {
  for (const w of windows.values())
    if (!w.isDestroyed()) w.webContents.send("veyra:changed");
}
function command(name: string, role = "main") {
  const w = openWindow(role);
  if (w.webContents.isLoading())
    w.webContents.once("did-finish-load", () =>
      w.webContents.send("veyra:command", name),
    );
  else w.webContents.send("veyra:command", name);
}
function bounds(role: string) {
  const saved = desktop().windows?.[role];
  const defaults =
    role === "main"
      ? { width: 1440, height: 960 }
      : role === "quick"
        ? { width: 620, height: 620 }
        : role === "mini"
          ? { width: 390, height: 680 }
          : { width: 360, height: 470 };
  if (!saved) return defaults;
  const valid = screen
    .getAllDisplays()
    .some(
      (d) =>
        saved.x + 100 > d.workArea.x &&
        saved.y + 100 > d.workArea.y &&
        saved.x < d.workArea.x + d.workArea.width &&
        saved.y < d.workArea.y + d.workArea.height,
    );
  return valid
    ? {
        ...saved,
        width: Math.max(
          320,
          Math.min(saved.width, screen.getPrimaryDisplay().workArea.width),
        ),
        height: Math.max(
          280,
          Math.min(saved.height, screen.getPrimaryDisplay().workArea.height),
        ),
      }
    : defaults;
}
function openWindow(role = "main") {
  if (role === "notes") role = "widget-notes";
  const old = windows.get(role);
  if (old && !old.isDestroyed()) {
    old.show();
    old.focus();
    return old;
  }
  const w = new BrowserWindow({
    ...bounds(role),
    minWidth: role === "main" ? 1000 : 320,
    minHeight: role === "main" ? 650 : 280,
    title: "Veyra Life",
    show: false,
    backgroundColor: "#101218",
    autoHideMenuBar: true,
    icon: join(resources(), "icon.ico"),
    alwaysOnTop: role !== "main" && role !== "quick",
    webPreferences: {
      preload: join(__dirname, "preload.cjs"),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      webSecurity: true,
    },
    ...(isPanelRole(role) && glass ? glass.options(role) : {}),
  });
  windows.set(role, w);
  if (isPanelRole(role)) glass?.attach(role, w);
  w.webContents.setWindowOpenHandler(() => ({ action: "deny" }));
  w.webContents.on("will-navigate", (event, url) => {
    if (!url.startsWith("veyra://app/")) event.preventDefault();
  });
  w.on("ready-to-show", () => {
    if (!(role === "main" && desktop().startMinimized && !qa)) w.show();
  });
  w.on("close", (event) => {
    if (!quitting && role === "main" && desktop().keepTray !== false && !qa) {
      event.preventDefault();
      w.hide();
    }
  });
  w.on("closed", () => windows.delete(role));
  let saveTimer: ReturnType<typeof setTimeout>;
  const saveBounds = () => {
    clearTimeout(saveTimer);
    saveTimer = setTimeout(() => {
      if (!w.isDestroyed())
        desktopSave({
          windows: { ...desktop().windows, [role]: w.getBounds() },
        });
    }, 600);
  };
  if (!isPanelRole(role)) {
    w.on("resize", saveBounds);
    w.on("move", saveBounds);
  }
  void w.loadURL("veyra://app/index.html#" + role);
  if (role === "main")
    w.webContents.once("did-finish-load", () => {
      firstPaintMs ||= Date.now() - bootAt;
    });
  return w;
}
function setupTray() {
  if (qa) return;
  tray?.destroy();
  const icon = nativeImage.createFromPath(join(resources(), "tray.png"));
  tray = new Tray(icon);
  tray.setToolTip("Veyra Life");
  tray.setContextMenu(
    Menu.buildFromTemplate([
      { label: "Abrir Veyra Life", click: () => openWindow() },
      { type: "separator" },
      { label: "+ Tarefa", click: () => command("new:task", "quick") },
      { label: "+ Gasto", click: () => command("new:expense", "quick") },
      { label: "+ Entrada", click: () => command("new:income", "quick") },
      { label: "+ Nota", click: () => command("new:note", "quick") },
      { type: "separator" },
      { label: "Iniciar foco", click: () => command("focus") },
      { label: "Meu dia", click: () => command("day") },
      { label: "Resumo do dia", click: () => openBrief("day") },
      { label: "Captura universal", click: () => command("capture", "quick") },
      { label: "Pesquisar Veyra", click: () => command("spotlight", "quick") },
      { label: "Notificações", click: () => command("notifications") },
      { label: "Configurações", click: () => command("settings") },
      { label: "Sincronizar", click: () => void engine.sync() },
      { type: "separator" },
      { label: "Mini Dashboard", click: () => openWindow("mini") },
      { label: "Focus Overlay", click: () => openWindow("widget-focus") },
      { label: "Veyra Dock", click: () => openWindow("dock") },
      {
        label: "Widgets",
        submenu: Object.entries(panelSpecs)
          .filter(([role]) => role.startsWith("widget-"))
          .map(([role, spec]) => ({
            label: spec.title,
            click: () => openWindow(role),
          })),
      },
      { label: "Ocultar todos os painéis", click: () => glass?.all("hide") },
      {
        label: "Mostrar todos os painéis",
        click: () => {
          glass?.all("restore");
          glass?.all("show");
        },
      },
      {
        label: "Recuperar painéis / desativar click-through",
        click: () => glass?.all("recover"),
      },
      { type: "separator" },
      {
        label: "Sair",
        click: () => {
          quitting = true;
          app.quit();
        },
      },
    ]),
  );
  tray.on("double-click", () => openWindow());
}
const defaultShortcuts = {
  capture: "CommandOrControl+Shift+Space",
  task: "CommandOrControl+Alt+T",
  expense: "CommandOrControl+Alt+G",
  note: "CommandOrControl+Alt+N",
  focus: "CommandOrControl+Alt+F",
  recover: "CommandOrControl+Shift+R",
  spotlight: "CommandOrControl+Alt+Space",
};
let registeredShortcuts: Record<string, string> = {};
function applyDesktopTheme() {
  const theme = desktop().theme || vault.preferences().theme;
  nativeTheme.themeSource =
    theme === "dark" || theme === "Escuro"
      ? "dark"
      : theme === "light" || theme === "Claro"
        ? "light"
        : "system";
}
function shortcuts(value: Record<string, string>) {
  const before = registeredShortcuts;
  if (
    !value ||
    Object.keys(value).some((name) => !Object.hasOwn(defaultShortcuts, name)) ||
    Object.values(value).some(
      (v) => typeof v !== "string" || !v || v.length > 100,
    )
  )
    throw Error("Atalho inválido.");
  globalShortcut.unregisterAll();
  const registered: string[] = [];
  try {
    for (const [name, accelerator] of Object.entries(value)) {
      if (!accelerator || accelerator.length > 100)
        throw Error("Atalho inválido.");
      if (
        !globalShortcut.register(accelerator, () =>
          name === "recover"
            ? glass?.all("recover")
            : name === "capture"
              ? command("capture", "quick")
              : name === "spotlight"
                ? command("spotlight", "quick")
                : name === "focus"
                  ? command("focus", "widget-focus")
                  : command("new:" + name, "quick"),
        )
      )
        throw Error(`O atalho ${accelerator} já está em uso.`);
      registered.push(accelerator);
    }
    registeredShortcuts = { ...value };
  } catch (e) {
    registered.forEach((s) => globalShortcut.unregister(s));
    for (const [name, key] of Object.entries(before))
      globalShortcut.register(key as string, () =>
        name === "recover"
          ? glass?.all("recover")
          : command(
              name === "capture"
                ? "capture"
                : name === "spotlight"
                  ? "spotlight"
                  : name === "focus"
                    ? "focus"
                    : "new:" + name,
              name === "focus" ? "widget-focus" : "quick",
            ),
      );
    throw e;
  }
}
async function switchVault(uid: string | null) {
  approvedDrops.clear();
  engine?.stop();
  const previous = vault;
  vault = await Vault.open(files, uid, join(resources(), "sql-wasm.wasm"));
  engine = new SyncEngine(client, vault, changed);
  if (previous) setTimeout(() => previous.close(), 25000);
  importPending = null;
  financeImportPending = null;
  for (const w of windows.values())
    if (!w.isDestroyed()) w.webContents.send("veyra:invalidate");
  if (uid) {
    const deviceId = files.json<{ id: string }>("device.safe")!.id;
    const id = "device:" + deviceId;
    const old = vault.item(id);
    if (!old || Date.now() - Number(old.fields.lastSeen || 0) > 86400000)
      vault.save([
        createItem("device", {
          id,
          title: "Veyra Life • Windows",
          createdAt: old?.createdAt || Date.now(),
          fields: {
            deviceId,
            platform: "Windows",
            version,
            lastSeen: String(Date.now()),
          },
        }),
      ]);
  }
  engine.start();
  applyDesktopTheme();
  if (glass && !qa)
    try {
      shortcuts({ ...defaultShortcuts, ...desktop().shortcuts });
      desktopSave({ panelRecoveryAvailable: true, shortcutWarning: "" });
    } catch {
      desktopSave({
        panelRecoveryAvailable: false,
        shortcutWarning: "Um atalho está em uso. Ajuste em Configurações.",
      });
    }
  glass?.reapply();
  changed();
}
function snapshot(month?: string, days?: number): Snapshot {
  const all = vault.items();
  const key = [
    vault.dataVersion,
    month || today().slice(0, 7),
    days || 30,
    today(),
    vault.preferences().financeCurrency || "BRL",
    vault.preferences().financialDay || 1,
  ].join("|");
  let financial;
  try {
    financial =
      financeCache?.vault === vault && financeCache.key === key
        ? financeCache.value
        : finance(
            all,
            month || today().slice(0, 7),
            vault.preferences().financeCurrency || "BRL",
            days || 30,
            Math.min(
              31,
              Math.max(1, Number(vault.preferences().financialDay || 1)),
            ),
          );
    financeCache = { vault, key, value: financial };
  } catch (e) {
    financial = finance([], month || today().slice(0, 7));
    financial.error = (e as Error).message;
    financial.insights = [
      (e as Error).message +
        " Confira os registros financeiros antes de usar a projeção.",
    ];
  }
  const cachePath = join(
    files.directory,
    "workspace-" + sha(vault.uid || "guest") + ".safe",
  );
  const prefs = {
    ...desktop(),
    storageBytes: existsSync(cachePath) ? statSync(cachePath).size : 0,
    dataVersion: vault.dataVersion,
    lastBackup: Number(vault.meta("lastBackup") || 0),
  };
  const focus = prefs.focus || {};
  const remaining = focus.active
    ? focus.paused
      ? focus.remaining
      : Math.max(0, Math.ceil((focus.deadline - Date.now()) / 1000))
    : 0;
  if (
    snapshotCache?.vault !== vault ||
    snapshotCache.version !== vault.dataVersion
  )
    snapshotCache = {
      vault,
      version: vault.dataVersion,
      items: all
        .filter((i) => !i.deletedAt && i.type !== "cloud_settings")
        .map(withoutBinary),
    };
  return {
    uid: vault.uid,
    user: client.user(),
    items: snapshotCache.items,
    preferences: vault.preferences(),
    desktop: prefs,
    sync: {
      status: engine.status,
      pending: vault.counts().pending,
      conflicts: vault.counts().conflicts,
      lastSync: engine.lastSuccessful || Number(vault.meta("lastSync") || 0),
      error: engine.error,
    },
    finance: financial,
    focus: {
      active: !!focus.active,
      paused: !!focus.paused,
      remaining,
      title: focus.title || "",
      taskId: focus.taskId || "",
    },
    version,
    configured: !!client.config,
    deviceId: files.json<{ id: string }>("device.safe")!.id,
  };
}
function save(items: Item[]) {
  const all = vault.items();
  const repeats = items
    .map((i) => repeatedTask(i, vault.item(i.id)))
    .filter((i): i is Item => !!i && !vault.item(i.id));
  const normalized = [...items, ...repeats].map(normalize);
  const replaced = new Set(normalized.map((i) => i.id));
  const merged = [...all.filter((i) => !replaced.has(i.id)), ...normalized];
  for (const i of normalized) {
    validateTaskDependencies(i, merged);
    if (
      i.type === "task" &&
      i.done &&
      !vault.item(i.id)?.done &&
      taskBlocked(i, merged).length
    )
      throw Error("Conclua as tarefas das quais esta tarefa depende primeiro.");
    validateFinance(i, merged);
    const before = vault.item(i.id);
    if (
      before &&
      ["account", "card"].includes(i.type) &&
      currency(before) !== currency(i) &&
      all.some(
        (p) =>
          !p.deletedAt &&
          ["account", "destination", "card"].some((k) => p.fields[k] === i.id),
      )
    )
      throw Error(
        "Crie outra conta para trocar a moeda com lançamentos vinculados.",
      );
  }
  vault.save(normalized);
  for (const i of normalized)
    if (i.type === "expense" && !all.some((old) => old.id === i.id))
      runAutomations(i);
  changed();
  engine.schedule();
}
function notice(title: string, body: string) {
  if (
    desktop().notifications &&
    !desktop().doNotDisturb &&
    !(desktop().quietFocus && desktop().focus?.active) &&
    Notification.isSupported() &&
    !qa
  )
    new Notification({ title, body }).show();
}
function openBrief(kind: "morning" | "day" | "week") {
  desktopSave({ briefKind: kind });
  openWindow("brief");
}
function pushNotices(
  entries: Array<{ id: string; title: string; itemId?: string }>,
) {
  const existing = JSON.parse(vault.meta("notifications") || "[]");
  const marks: string[] = JSON.parse(vault.meta("notificationMarks") || "[]");
  const seen = new Set([...marks, ...existing.map((p: any) => p.id)]);
  const fresh = entries.filter((e) => {
    if (seen.has(e.id)) return false;
    seen.add(e.id);
    return true;
  });
  if (!fresh.length) return;
  vault.setMeta("notificationMarks", JSON.stringify([...seen].slice(-4000)));
  vault.setMeta(
    "notifications",
    JSON.stringify(
      [
        ...fresh.map((e) => ({ ...e, at: Date.now(), read: false })),
        ...existing,
      ].slice(0, 200),
    ),
  );
  notice(
    "Veyra Life",
    fresh.length === 1
      ? fresh[0].title
      : `${fresh.length} lembretes disponíveis na central.`,
  );
  changed();
}
function runAutomations(input?: Item) {
  const marks: string[] = JSON.parse(vault.meta("automationMarks") || "[]");
  const plan = planAutomations(
    vault.items(false),
    input,
    today(),
    new Set(marks),
  );
  if (!plan.marks.length) return;
  const records = plan.records.filter((i) => !vault.item(i.id));
  if (records.length) vault.save(records);
  pushNotices(plan.alerts);
  vault.setMeta(
    "automationMarks",
    JSON.stringify([...marks, ...plan.marks].slice(-2000)),
  );
  if (records.length) engine.schedule();
  changed();
}
function startFocus(input: any) {
  const seconds =
    input.mode === "Cronômetro" ? 86400 : Number(input.minutes) * 60;
  if (!Number.isInteger(seconds) || seconds < 60 || seconds > 86400)
    throw Error("Use uma duração de 1 a 1440 minutos.");
  const current = desktop().focus;
  if (current?.active) throw Error("Conclua ou encerre a sessão atual.");
  desktopSave({
    focus: {
      active: true,
      paused: false,
      deadline: Date.now() + seconds * 1000,
      remaining: seconds,
      seconds,
      title: String(input.title || "Tempo de foco").slice(0, 200),
      taskId: input.taskId || "",
      id: crypto.randomUUID(),
      startedAt: Date.now(),
      mode: input.mode || "Pomodoro",
    },
  });
}
function finishFocus(completed: boolean) {
  const focus = desktop().focus;
  if (!focus?.active) return;
  const remaining = focus.paused
    ? focus.remaining
    : Math.max(0, (focus.deadline - Date.now()) / 1000);
  const seconds = Math.max(0, Math.round(focus.seconds - remaining));
  if (seconds > 0)
    save([
      createItem("focus", {
        id: "focus:" + focus.id,
        title: focus.title,
        date: today(),
        done: completed,
        parentId: focus.taskId,
        createdAt: focus.startedAt,
        fields: {
          minutes: (seconds / 60).toFixed(2),
          completed: completed ? "yes" : "no",
          mode: focus.mode,
        },
      }),
    ]);
  desktopSave({ focus: { ...focus, active: false } });
  if (completed)
    notice("Sessão concluída", "Seu tempo de foco foi registrado.");
}
async function refreshWeather() {
  if (weatherBusy || !desktop().weatherEnabled) return;
  const p = vault.preferences();
  if (p.weatherMode === "Manual") return;
  const city = vault
    .items(false)
    .find((i) => i.type === "city" && i.id === p.weatherCity);
  if (!city) return;
  if (
    Date.now() - Number(city.fields.cacheAt || city.fields.updated || 0) <
    1800000
  )
    return;
  const lat = Number(city.fields.latitude),
    lon = Number(city.fields.longitude);
  if (!Number.isFinite(lat) || !Number.isFinite(lon)) return;
  const identity = vault.uid;
  weatherBusy = true;
  try {
    const response = await fetch(
      `https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lon}&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m&hourly=temperature_2m,precipitation_probability&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset,uv_index_max&timezone=auto&forecast_days=7`,
      { signal: AbortSignal.timeout(15000) },
    );
    if (!response.ok) throw Error("Previsão indisponível.");
    const data = await response.json();
    if (vault.uid !== identity) return;
    save([
      {
        ...city,
        fields: {
          ...city.fields,
          cache: JSON.stringify(data),
          cacheAt: String(Date.now()),
          updated: String(Date.now()),
        },
      },
    ]);
  } finally {
    weatherBusy = false;
  }
}
const allowedMethods = new Set([
  "exportReport",
  "financeImportPreview",
  "financeImportConfirm",
  "universalSearch",
  "draftWrite",
  "drafts",
  "draftDelete",
  "activity",
  "recent",
  "notifications",
  "notificationAction",
  "diagnostics",
  "diagnosticsCopy",
  "organizeInbox",
  "applyTemplate",
  "dockPosition",
  "attachmentPreview",
  "startup",
  "closeWindow",
  "panelInfo",
  "panelUpdate",
  "panelGesture",
  "panelMenu",
  "panelsAll",
  "capture",
  "navigate",
  "snapshot",
  "search",
  "item",
  "save",
  "trash",
  "restore",
  "duplicate",
  "reorder",
  "preference",
  "desktop",
  "google",
  "login",
  "logout",
  "refreshUser",
  "sync",
  "conflicts",
  "resolve",
  "history",
  "backup",
  "importPreview",
  "importConfirm",
  "focusStart",
  "focusPause",
  "focusStop",
  "focusComplete",
  "openWindow",
  "shortcuts",
  "clipboard",
  "attach",
  "attachment",
  "payInvoice",
  "weatherSearch",
  "weatherSelect",
  "weatherRefresh",
  "updatesCheck",
  "updatesDownload",
  "updatesInstall",
  "openLink",
  "quit",
]);
async function dispatch(method: string, value: any, owner: BrowserWindow) {
  const workspace = vault;
  const checkScope = () => {
    if (vault !== workspace)
      throw Error("A conta mudou. Abra novamente esta ação.");
  };
  switch (method) {
    case "exportReport": {
      const data = reportCSV(vault.items(false), value);
      const target = await dialog.showSaveDialog(owner, {
        title: "Exportar relatório selecionado",
        defaultPath: "Veyra-Financeiro.csv",
        filters: [{ name: "CSV", extensions: ["csv"] }],
      });
      checkScope();
      if (target.canceled || !target.filePath) return false;
      writeFileSync(target.filePath, data, { encoding: "utf8", mode: 0o600 });
      return true;
    }
    case "financeImportPreview": {
      const source = await dialog.showOpenDialog(owner, {
        properties: ["openFile"],
        filters: [{ name: "CSV financeiro", extensions: ["csv"] }],
      });
      checkScope();
      if (source.canceled) return null;
      const path = source.filePaths[0];
      if (statSync(path).size > 10000000) throw Error("Arquivo excede 10 MB.");
      const rows = readCSV(readFileSync(path, "utf8")),
        header = (rows.shift() || []).map((v) => v.trim().toLowerCase());
      if (
        !["type", "date", "title", "amount", "currency"].every((k) =>
          header.includes(k),
        )
      )
        throw Error("Use colunas type, date, title, amount e currency.");
      if (rows.length > 2000)
        throw Error("Importe até 2 mil lançamentos por arquivo.");
      const all = vault.items(false);
      const items = rows.map((row, n) => {
        const record = Object.fromEntries(
          header.map((key, i) => [key, (row[i] || "").trim()]),
        );
        if (
          !["income", "expense"].includes(record.type) ||
          !validDate(record.date) ||
          !record.title
        )
          throw Error("Confira tipo, título e data na linha " + (n + 2));
        const minor = parseMinor(record.amount, record.currency);
        if (minor <= 0)
          throw Error("Use valores positivos na linha " + (n + 2));
        const fields: Record<string, string> = {
          amount: record.amount,
          amountMinor: String(minor),
          currency: record.currency,
          category: record.category || "",
          status:
            record.status || (record.type === "income" ? "received" : "paid"),
        };
        for (const type of ["account", "card"])
          if (record[type]) {
            const matches = all.filter(
              (i) =>
                i.type === type &&
                (i.id === record[type] || i.title === record[type]),
            );
            if (matches.length !== 1)
              throw Error(
                "Conta ou cartão desconhecido/ambíguo na linha " + (n + 2),
              );
            fields[type] = matches[0].id;
          }
        return createItem(record.type, {
          id: "csv:" + sha(JSON.stringify({ record, n })),
          title: record.title,
          date: record.date,
          fields,
        });
      });
      const token = crypto.randomUUID();
      financeImportPending = { uid: vault.uid, items, token };
      const newItems = items.filter((i) => !vault.item(i.id));
      return {
        total: items.length,
        newCount: newItems.length,
        duplicates: items.length - newItems.length,
        items: newItems.slice(0, 15),
        token,
      };
    }
    case "financeImportConfirm": {
      if (
        !financeImportPending ||
        financeImportPending.uid !== vault.uid ||
        financeImportPending.token !== value?.token
      )
        throw Error("Abra a prévia novamente.");
      const items = financeImportPending.items.filter(
        (i: Item) => !vault.item(i.id),
      );
      save(items);
      financeImportPending = null;
      return true;
    }
    case "universalSearch":
      return vault.universalSearch(
        String(value?.query || "").slice(0, 500),
        value?.limit,
        value?.offset,
      );
    case "draftWrite":
      vault.writeDraft(value.item);
      return true;
    case "drafts":
      return vault.drafts();
    case "draftDelete":
      vault.clearDraft(String(value));
      changed();
      return true;
    case "activity":
      return vault.activity();
    case "recent":
      return JSON.parse(vault.meta("recent") || "[]")
        .map((id: string) => vault.item(id))
        .filter(Boolean)
        .map(withoutBinary);
    case "notifications":
      return JSON.parse(vault.meta("notifications") || "[]");
    case "notificationAction": {
      if (!["read", "dismiss", "readAll"].includes(value?.action))
        throw Error("Ação inválida.");
      let rows = JSON.parse(vault.meta("notifications") || "[]");
      rows = rows.flatMap((n: any) => {
        if (value.action === "readAll") return [{ ...n, read: true }];
        if (n.id !== value.id) return [n];
        return value.action === "dismiss" ? [] : [{ ...n, read: true }];
      });
      vault.setMeta("notifications", JSON.stringify(rows));
      changed();
      return true;
    }
    case "diagnostics":
    case "diagnosticsCopy": {
      const metrics = app.getAppMetrics();
      const report = {
        version,
        os: platform() + " " + release(),
        electron: process.versions.electron,
        chrome: process.versions.chrome,
        startupMs: firstPaintMs,
        uptimeSeconds: Math.round(process.uptime()),
        database: "encrypted SQLite",
        databaseHealthy:
          vault.query("PRAGMA quick_check")[0]?.quick_check === "ok",
        cacheBytes: snapshot().desktop.storageBytes,
        records: vault.items(false).length,
        localDrafts: vault.drafts().length,
        auth: client.user() ? "connected" : "local",
        firebaseConfigured: !!client.config,
        sync: engine.status,
        pending: vault.counts().pending,
        conflicts: vault.counts().conflicts,
        lastSync: engine.lastSuccessful || Number(vault.meta("lastSync") || 0),
        networkRequests: { ...client.metrics },
        processes: metrics.map((m) => ({
          type: m.type,
          cpuPercent: m.cpu.percentCPUUsage,
          workingSetKB: m.memory.workingSetSize,
        })),
        panels: [...windows.keys()].filter(isPanelRole),
        health: {
          database: true,
          cache: true,
          auth: !!client.user(),
          cloud: engine.status === "ready",
        },
      };
      if (method === "diagnosticsCopy")
        clipboard.writeText(JSON.stringify(report, null, 2));
      return report;
    }
    case "dockPosition": {
      if (!["left", "right"].includes(value)) throw Error("Lado inválido.");
      desktopSave({ dockSide: value });
      const w = openWindow("dock"),
        b = w.getBounds(),
        area = screen.getDisplayMatching(b).workArea;
      w.setBounds({
        ...b,
        x: value === "left" ? area.x + 12 : area.x + area.width - b.width - 12,
        y: area.y + 12,
      });
      return true;
    }
    case "organizeInbox": {
      const original = vault.item(String(value?.id));
      if (
        !original ||
        original.type !== "inbox" ||
        original.done ||
        original.deletedAt
      )
        throw Error("Captura não disponível.");
      if (
        !["task", "note", "expense", "event", "project"].includes(value?.type)
      )
        throw Error("Destino inválido.");
      const fields: Record<string, string> =
        value.type === "expense"
          ? {
              amount: String(value.amount || ""),
              currency: vault.preferences().financeCurrency || "BRL",
              status: "paid",
            }
          : {};
      const converted = createItem(value.type, {
        title: original.title,
        notes: original.notes,
        date: value.type === "note" ? "" : today(),
        tags: original.tags,
        parentId: original.parentId,
        fields: { ...fields, capturedFrom: original.id },
      });
      save([
        converted,
        {
          ...original,
          done: true,
          fields: { ...original.fields, organizedId: converted.id },
        },
      ]);
      return withoutBinary(converted);
    }
    case "applyTemplate": {
      const template = vault.item(String(value?.id));
      if (!template || template.type !== "template" || template.deletedAt)
        throw Error("Modelo não disponível.");
      const type = template.fields.targetType || "task";
      if (!["task", "note", "project"].includes(type))
        throw Error("Tipo inválido.");
      const root = createItem(type, {
        title: String(value?.title || template.title)
          .trim()
          .slice(0, 200),
        notes: template.notes,
        date: today(),
        fields: { templateId: template.id },
      });
      const children =
        type === "project"
          ? (template.fields.lines || "")
              .split("\n")
              .filter((v) => v.trim())
              .slice(0, 50)
              .map((v) =>
                createItem("task", {
                  title: v.trim().slice(0, 200),
                  parentId: root.id,
                  date: "",
                }),
              )
          : [];
      save([root, ...children]);
      return withoutBinary(root);
    }
    case "startup": {
      const enabled = value === true;
      app.setLoginItemSettings({
        openAtLogin: enabled,
        path: process.execPath,
        args: ["--startup"],
      });
      desktopSave({ startWindows: enabled });
      return true;
    }
    case "closeWindow":
      owner.close();
      return true;
    case "panelInfo":
      return glass!.info(owner);
    case "panelUpdate":
      return glass!.update(owner, value);
    case "panelGesture":
      return glass!.gesture(owner, value);
    case "panelMenu":
      return glass!.menu(owner, value === true);
    case "panelsAll":
      return glass!.all(String(value));
    case "capture": {
      if (value?.id) {
        const item = vault.item(String(value.id));
        if (!item) throw Error("Registro não encontrado.");
        command("edit:" + item.id, "quick");
      } else {
        const type = String(value?.type || "task");
        if (!/^[a-z_]{1,40}$/.test(type)) throw Error("Tipo inválido.");
        command("new:" + type, "quick");
      }
      return true;
    }
    case "navigate": {
      if (
        ![
          "home",
          "day",
          "finance",
          "tasks",
          "notes",
          "calendar",
          "habits",
          "goals",
          "focus",
          "modules",
          "settings",
          "inbox",
          "projects",
          "planner",
          "review",
          "automations",
          "templates",
          "notifications",
          "sync",
          "diagnostics",
          "recent",
          "favorites",
          "backup",
          "privacy",
        ].includes(value)
      )
        throw Error("Página inválida.");
      command(value);
      return true;
    }
    case "snapshot":
      return snapshot(value?.month, value?.days);
    case "search":
      return vault.search(
        String(value?.query || "").slice(0, 200),
        Array.isArray(value?.types)
          ? value.types.filter((v: any) => typeof v === "string").slice(0, 100)
          : [],
        value?.limit,
        value?.offset,
        value?.deleted === true,
        {
          category: String(value?.category || "").slice(0, 200),
          status: String(value?.status || "").slice(0, 40),
          currency: String(value?.currency || "").slice(0, 3),
          month: /^\d{4}-\d{2}$/.test(value?.month || "")
            ? value.month
            : undefined,
        },
      );
    case "item": {
      const item = vault.item(String(value));
      if (item) {
        const ids: string[] = JSON.parse(vault.meta("recent") || "[]");
        if (ids[0] !== item.id)
          vault.setMeta(
            "recent",
            JSON.stringify(
              [item.id, ...ids.filter((id) => id !== item.id)].slice(0, 30),
            ),
          );
      }
      return item;
    }
    case "save": {
      const items: Array<Item> = Array.isArray(value.items)
        ? value.items
        : [value.item];
      if (items.length > 200) throw Error("Muitos registros em uma operação.");
      if (value.base && items.length === 1)
        vault.checkBase(items[0].id, value.base);
      if (items.some((i) => i.fields.virtual === "yes"))
        items.forEach((i) => {
          if (i.fields.virtual === "yes")
            i.fields = { ...i.fields, virtual: "no" };
        });
      if (value.installments && items.length === 1)
        save(
          installmentItems(items[0], Number(value.installments), vault.items()),
        );
      else save(items);
      return true;
    }
    case "trash": {
      const i = vault.item(String(value));
      if (!i) throw Error("Registro não encontrado.");
      if (
        ["account", "card"].includes(i.type) &&
        vault
          .items(false)
          .some((p) =>
            ["account", "destination", "card"].some(
              (k) => p.fields[k] === i.id,
            ),
          )
      )
        throw Error("Essa conta ou cartão possui registros vinculados.");
      const answer = await dialog.showMessageBox(owner, {
        type: "question",
        buttons: ["Cancelar", "Mover para a lixeira"],
        defaultId: 0,
        cancelId: 0,
        message: "Mover este registro para a lixeira?",
        detail: i.title,
      });
      checkScope();
      if (answer.response === 1) {
        save([{ ...i, deletedAt: Date.now() }]);
        desktopSave({ lastUndo: { id: i.id, until: Date.now() + 15000 } });
      }
      return answer.response === 1;
    }
    case "restore": {
      const i = vault.item(String(value));
      if (i?.fields.purged === "yes")
        throw Error("Este registro foi excluído definitivamente.");
      if (i) save([{ ...i, deletedAt: 0 }]);
      return true;
    }
    case "duplicate": {
      const i = vault.item(String(value));
      if (i)
        save([
          createItem(i.type, {
            ...i,
            id: crypto.randomUUID(),
            createdAt: Date.now(),
            title: (i.title + " — cópia").slice(0, 200),
            deletedAt: 0,
            fields: { ...i.fields },
          }),
        ]);
      return true;
    }
    case "reorder": {
      const changes = (value.ids as string[]).slice(0, 300).map((id, index) => {
        const i = vault.item(id);
        if (!i) throw Error("Registro não encontrado.");
        return {
          ...i,
          fields: {
            ...i.fields,
            order: String(index),
            ...(value.column ? { status: String(value.column) } : {}),
          },
        };
      });
      save(changes);
      return true;
    }
    case "preference":
      vault.preference(String(value.key), String(value.value));
      engine.schedule();
      changed();
      return true;
    case "desktop": {
      if (!value || Buffer.byteLength(JSON.stringify(value)) > 100000)
        throw Error("Configuração inválida.");
      const allowed = new Set([
        "weatherEnabled",
        "welcomeSeen",
        "layout",
        "theme",
        "accent",
        "density",
        "scale",
        "keepTray",
        "startMinimized",
        "openDay",
        "clipboard",
        "notifications",
        "miniWidgets",
        "kanbanColumns",
        "lastPage",
        "reducedEffects",
      ]);
      for (const key of [
        "performanceMode",
        "doNotDisturb",
        "quietFocus",
        "morningBrief",
        "dailyReview",
        "weeklyReview",
        "smartHints",
        "dockSide",
        "animations",
        "onboardingProfile",
        "plannerDate",
      ])
        allowed.add(key);
      if (Object.keys(value).some((k) => !allowed.has(k)))
        throw Error("Configuração não autorizada.");
      value = desktopPatch(value);
      desktopSave(value);
      if ("keepTray" in value) setupTray();
      if ("theme" in value) applyDesktopTheme();
      if ("reducedEffects" in value) glass?.reapply();
      if ("performanceMode" in value) {
        desktopSave({ reducedEffects: value.performanceMode === "economy" });
        glass?.reapply();
      }
      return true;
    }
    case "google": {
      if (!client.config) throw Error("Firebase indisponível.");
      googleAttempt?.cancel();
      googleAttempt = systemGoogle(client.config, uiDir, (url) =>
        shell.openExternal(url),
      );
      const identity = vault.uid;
      const token = await googleAttempt.result;
      if (vault.uid !== identity) throw Error("A conta mudou durante o login.");
      await client.google(token);
      await switchVault(client.user()!.uid);
      return true;
    }
    case "login":
      await client.login(value.email, value.password);
      await switchVault(client.user()!.uid);
      return true;
    case "logout": {
      const answer = await dialog.showMessageBox(owner, {
        buttons: ["Cancelar", "Sair da conta"],
        defaultId: 0,
        cancelId: 0,
        message: "Sair desta conta?",
        detail:
          "O cache e as alterações pendentes ficam protegidos neste PC. O espaço visitante será aberto.",
      });
      checkScope();
      if (answer.response !== 1) return false;
      googleAttempt?.cancel();
      engine.stop();
      client.logout();
      await switchVault(null);
      return true;
    }
    case "refreshUser":
      await client.refreshUser();
      engine.schedule(100);
      changed();
      return true;
    case "sync":
      await engine.sync();
      return true;
    case "conflicts":
      return vault.conflicts().map((c) => ({
        ...c,
        local: withoutBinary(c.local),
        remote: { ...c.remote, item: withoutBinary(c.remote.item) },
      }));
    case "resolve":
      vault.resolve(value.id, value.useRemote === true);
      engine.schedule();
      changed();
      return true;
    case "history":
      return vault.audit(String(value));
    case "backup": {
      const result = await dialog.showSaveDialog(owner, {
        title: "Salvar backup completo",
        defaultPath: "Veyra-backup-" + today() + ".json",
        filters: [{ name: "Backup Veyra", extensions: ["json"] }],
      });
      if (result.canceled || !result.filePath) return false;
      checkScope();
      const backup = encodeBackup(
        vault.backup(),
        String(value?.password || ""),
      );
      if (Buffer.byteLength(backup) > 30000000)
        throw Error("Backup acima de 30 MB. Exporte anexos separadamente.");
      writeFileSync(result.filePath, backup, { mode: 0o600 });
      desktopSave({});
      vault.setMeta("lastBackup", String(Date.now()));
      return true;
    }
    case "importPreview": {
      const result = await dialog.showOpenDialog(owner, {
        title: "Escolher backup Veyra",
        properties: ["openFile"],
        filters: [{ name: "Backup Veyra", extensions: ["json"] }],
      });
      if (result.canceled) return null;
      const path = result.filePaths[0];
      if (statSync(path).size > 30000000) throw Error("Backup acima de 30 MB.");
      const backup = decodeBackup(
        readFileSync(path, "utf8"),
        String(value?.password || ""),
      );
      checkScope();
      const preview = vault.importPreview(backup);
      importPending = {
        uid: vault.uid,
        value: backup,
        hash: sha(JSON.stringify(backup)),
      };
      return { ...preview, value: undefined };
    }
    case "importConfirm": {
      if (!importPending || importPending.uid !== vault.uid)
        throw Error("Selecione o backup novamente.");
      const preview = vault.importPreview(importPending.value);
      if (
        preview.changes.length &&
        value.replace !== true &&
        value.replace !== false
      )
        throw Error("Escolha como tratar registros existentes.");
      const answer = await dialog.showMessageBox(owner, {
        type: "question",
        buttons: ["Cancelar", "Importar"],
        defaultId: 0,
        cancelId: 0,
        message: "Importar o backup validado?",
        detail: `${preview.newCount} novos registros; ${preview.changes.length} registros diferentes. ${value.replace ? "As versões substituídas serão preservadas no histórico." : "Os registros existentes serão mantidos."}`,
      });
      checkScope();
      if (answer.response !== 1) return false;
      vault.importBackup(importPending.value, value.replace === true);
      importPending = null;
      changed();
      engine.schedule();
      return true;
    }
    case "focusStart":
      startFocus(value);
      return true;
    case "focusPause": {
      const focus = desktop().focus;
      if (!focus?.active) return false;
      desktopSave({
        focus: {
          ...focus,
          paused: !focus.paused,
          remaining: focus.paused
            ? focus.remaining
            : Math.max(0, Math.ceil((focus.deadline - Date.now()) / 1000)),
          deadline: focus.paused
            ? Date.now() + focus.remaining * 1000
            : focus.deadline,
        },
      });
      return true;
    }
    case "focusStop":
      finishFocus(false);
      return true;
    case "focusComplete":
      finishFocus(true);
      return true;
    case "openWindow": {
      const role = String(value);
      if (
        !isPanelRole(role) &&
        ![
          "main",
          "mini",
          "quick",
          "widget-finance",
          "widget-tasks",
          "widget-weather",
          "widget-calendar",
          "widget-habits",
          "widget-focus",
          "notes",
        ].includes(role)
      )
        throw Error("Janela inválida.");
      openWindow(role);
      return true;
    }
    case "shortcuts":
      shortcuts({ ...defaultShortcuts, ...value });
      desktopSave({
        shortcuts: { ...defaultShortcuts, ...value },
        panelRecoveryAvailable: true,
      });
      return true;
    case "clipboard": {
      if (!desktop().clipboard)
        throw Error("Habilite a leitura opcional da área de transferência.");
      const { clipboard } = await import("electron");
      return (await clipboard.readText()).slice(0, 100000);
    }
    case "attach": {
      if (value?.path) {
        const grant = approvedDrops.get(owner.webContents.id);
        approvedDrops.delete(owner.webContents.id);
        if (
          !grant ||
          grant.path !== value.path ||
          grant.uid !== vault.uid ||
          Date.now() - grant.at > 300000
        )
          throw Error(
            "Escolha o arquivo pelo diálogo ou arraste-o novamente para o Veyra.",
          );
      }
      let path: string;
      if (value?.path) {
        path = String(value.path);
        const answer = await dialog.showMessageBox(owner, {
          buttons: ["Cancelar", "Anexar localmente"],
          defaultId: 0,
          cancelId: 0,
          message: "Anexar este arquivo ao registro?",
          detail: basename(path) + "\nO arquivo ficará somente neste PC.",
        });
        if (answer.response !== 1) return null;
      } else {
        const result = await dialog.showOpenDialog(owner, {
          title: "Adicionar arquivo local",
          properties: ["openFile"],
        });
        if (result.canceled) return null;
        path = result.filePaths[0];
      }
      checkScope();
      if (statSync(path).size > 10000000)
        throw Error("Use arquivos de até 10 MB.");
      const bytes = readFileSync(path);
      return {
        attachment: bytes.toString("base64"),
        attachmentHash: sha(bytes.toString("base64")),
        attachmentName: basename(path),
        hasAttachment: "yes",
        attachmentLocalOnly: "yes",
      };
    }
    case "attachmentPreview": {
      const item = vault.item(String(value));
      if (!item || item.deletedAt || !item.fields.attachment) return null;
      const bytes = Buffer.from(item.fields.attachment, "base64");
      if (bytes.length > 10000000) return null;
      const mime = bytes
        .subarray(0, 8)
        .equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]))
        ? "image/png"
        : bytes[0] === 255 && bytes[1] === 216 && bytes[2] === 255
          ? "image/jpeg"
          : /^GIF8[79]a$/.test(bytes.subarray(0, 6).toString("ascii"))
            ? "image/gif"
            : bytes.subarray(0, 4).toString("ascii") === "RIFF" &&
                bytes.subarray(8, 12).toString("ascii") === "WEBP"
              ? "image/webp"
              : null;
      return mime
        ? "data:" + mime + ";base64," + bytes.toString("base64")
        : null;
    }
    case "attachment": {
      const item = vault.item(value);
      if (!item?.fields.attachment)
        throw Error("Este arquivo está somente no aparelho onde foi anexado.");
      const result = await dialog.showSaveDialog(owner, {
        title: "Exportar anexo local",
        defaultPath: basename(item.fields.attachmentName || "anexo.bin"),
      });
      checkScope();
      if (result.canceled || !result.filePath) return false;
      checkScope();
      writeFileSync(
        result.filePath,
        Buffer.from(item.fields.attachment, "base64"),
      );
      return true;
    }
    case "payInvoice": {
      const all = vault.items();
      const card = all.find((i) => i.id === value.cardId && i.type === "card");
      if (!card) throw Error("Cartão não encontrado.");
      const current = invoices(all, card).find((i) => i.id === value.invoiceId);
      if (!current || current.remaining <= 0) throw Error("Fatura já quitada.");
      if (current.paid !== value.paid || current.remaining !== value.remaining)
        throw Error("A fatura mudou. Abra novamente antes de pagar.");
      const paid = parseMinor(value.amount, current.currency);
      if (paid <= 0 || paid > current.remaining || value.date > today())
        throw Error("Confira o valor e a data do pagamento.");
      save([
        createItem("expense", {
          id: `invoice-payment:${current.id}:${current.paid + paid}`,
          title: "Pagamento de fatura",
          date: value.date,
          fields: {
            amountMinor: String(paid),
            currency: current.currency,
            account: value.account,
            card: card.id,
            status: "paid",
            paymentType: "card_payment",
            invoiceId: current.id,
            invoiceDueDate: current.due,
            settledDate: value.date,
            category: "Cartão",
          },
        }),
      ]);
      return true;
    }
    case "weatherSearch": {
      const response = await fetch(
        "https://geocoding-api.open-meteo.com/v1/search?" +
          new URLSearchParams({
            name: String(value).slice(0, 100),
            count: "8",
            language: "pt",
            format: "json",
          }),
        { signal: AbortSignal.timeout(15000) },
      );
      if (!response.ok) throw Error("Busca de cidades indisponível.");
      const result = await response.json();
      return result.results || [];
    }
    case "weatherSelect": {
      const latitude = Number(value.latitude),
        longitude = Number(value.longitude);
      if (
        !Number.isFinite(latitude) ||
        latitude < -90 ||
        latitude > 90 ||
        !Number.isFinite(longitude) ||
        longitude < -180 ||
        longitude > 180
      )
        throw Error("Cidade inválida.");
      desktopSave({ weatherEnabled: true });
      const city = createItem("city", {
        id:
          Number.isSafeInteger(value.id) && value.id > 0
            ? `city:${value.id}`
            : `city:${latitude}:${longitude}`,
        title: String(value.name).slice(0, 200),
        fields: {
          latitude: String(latitude),
          longitude: String(longitude),
          country: String(value.country || ""),
          cacheAt: "0",
        },
      });
      const old = vault.item(city.id);
      save([{ ...city, createdAt: old?.createdAt || city.createdAt }]);
      vault.preference("weatherCity", city.id);
      vault.preference("weatherMode", "Real");
      await refreshWeather();
      return true;
    }
    case "weatherRefresh":
      desktopSave({ weatherEnabled: true });
      await refreshWeather();
      changed();
      return true;
    case "updatesCheck":
      return updater.check();
    case "updatesDownload":
      await updater.download();
      return true;
    case "updatesInstall": {
      if (!updater.downloaded)
        throw Error("Baixe a atualização verificada primeiro.");
      const answer = await dialog.showMessageBox(owner, {
        buttons: ["Agora não", "Reiniciar e atualizar"],
        defaultId: 0,
        cancelId: 0,
        message: "Reiniciar o Veyra para atualizar?",
        detail: "Os dados e alterações pendentes continuam salvos neste PC.",
      });
      checkScope();
      if (answer.response !== 1) return false;
      updater.verifyDownloaded();
      const child = spawn(updater.downloaded, ["/S"], {
        detached: true,
        stdio: "ignore",
        windowsHide: true,
      });
      child.unref();
      quitting = true;
      app.quit();
      return true;
    }
    case "openLink": {
      const url = new URL(value);
      if (
        !["https:", "http:"].includes(url.protocol) ||
        url.username ||
        url.password
      )
        throw Error("Link inválido.");
      await shell.openExternal(url.toString());
      return true;
    }
    case "quit":
      quitting = true;
      app.quit();
      return true;
    default:
      throw Error("Ação indisponível.");
  }
}
app
  .whenReady()
  .then(async () => {
    if (!instance) return;
    if (!safeStorage.isEncryptionAvailable())
      throw Error("A proteção de dados do Windows não está disponível.");
    files = new SecureFiles(join(app.getPath("userData"), "vault"), {
      protect: (b) => safeStorage.encryptString(b.toString("base64")),
      unprotect: (b) => Buffer.from(safeStorage.decryptString(b), "base64"),
    });
    if (!files.json("device.safe"))
      files.setJSON("device.safe", { id: crypto.randomUUID() });
    const configPath = join(resources(), "firebase.json");
    const config: FirebaseConfig | null = existsSync(configPath)
      ? JSON.parse(readFileSync(configPath, "utf8"))
      : null;
    client = new FirebaseClient(
      config,
      files,
      fetch,
      qa && process.env.VEYRA_EMULATOR === "1"
        ? { auth: "http://127.0.0.1:9095", firestore: "http://127.0.0.1:8085" }
        : undefined,
    );
    updater = new DesktopUpdater(
      version,
      join(app.getPath("userData"), "updates"),
      readFileSync(join(resources(), "update-public.pem"), "utf8"),
    );
    await switchVault(client.user()?.uid || null);
    glass = new VeyraGlassWindows(windows, desktop, desktopSave, openWindow);
    protocol.handle("veyra", (request) => {
      const url = new URL(request.url);
      if (url.hostname !== "app") return new Response("", { status: 403 });
      const path = resolve(uiDir, "." + decodeURIComponent(url.pathname));
      if (!path.startsWith(resolve(uiDir) + sep))
        return new Response("", { status: 403 });
      return net.fetch(pathToFileURL(path).toString());
    });
    session.defaultSession.setPermissionRequestHandler(
      (_webContents, _permission, callback) => callback(false),
    );
    session.defaultSession.setPermissionCheckHandler(() => false);
    ipcMain.handle("veyra:call", async (event, method, value, scopeUid) => {
      const owner = BrowserWindow.fromWebContents(event.sender);
      if (
        !owner ||
        event.senderFrame !== event.sender.mainFrame ||
        !event.senderFrame.url.startsWith("veyra://app/") ||
        !allowedMethods.has(method)
      )
        throw Error("Acesso não autorizado.");
      if (
        method !== "snapshot" &&
        (scopeUid !== vault.uid ||
          (value &&
            Object.hasOwn(value, "expectedUid") &&
            value.expectedUid !== vault.uid))
      )
        throw Error("A conta mudou. Aguarde a atualização da janela.");
      try {
        return await dispatch(method, value, owner);
      } catch (e) {
        throw Error((e as Error).message.slice(0, 500));
      }
    });
    ipcMain.on("veyra:drop", (event, path, scopeUid) => {
      if (
        event.senderFrame !== event.sender.mainFrame ||
        !event.senderFrame.url.startsWith("veyra://app/") ||
        scopeUid !== vault.uid ||
        typeof path !== "string" ||
        path.length > 32768
      )
        return;
      approvedDrops.set(event.sender.id, {
        path,
        uid: vault.uid,
        at: Date.now(),
      });
    });
    setupTray();
    if (!qa)
      try {
        shortcuts({ ...defaultShortcuts, ...desktop().shortcuts });
        desktopSave({ panelRecoveryAvailable: true });
      } catch {
        desktopSave({
          shortcutWarning: "Um atalho está em uso. Ajuste em Configurações.",
          panelRecoveryAvailable: false,
        });
      }
    openWindow();
    glass.all("restore");
    if (
      app.commandLine.hasSwitch("startup") &&
      desktop().morningBrief &&
      vault.meta("morningSeen") !== today()
    ) {
      vault.setMeta("morningSeen", today());
      openBrief("morning");
    }
    const displaysChanged = () => {
      glass?.all("recover");
    };
    screen.on("display-removed", displaysChanged);
    screen.on("display-metrics-changed", displaysChanged);
    nativeTheme.on("updated", () => {
      glass?.reapply();
      changed();
    });
    powerMonitor.on("resume", () => {
      glass?.reapply();
      void engine.sync();
      changed();
    });
    powerMonitor.on("on-battery", () => {
      glass?.reapply();
      changed();
    });
    powerMonitor.on("on-ac", () => {
      glass?.reapply();
      changed();
    });
    setInterval(() => {
      const focus = desktop().focus;
      if (focus?.active && !focus.paused && focus.deadline <= Date.now())
        finishFocus(true);
    }, 1000);
    setInterval(() => {
      runAutomations();
      const hour = new Date().getHours(),
        day = today();
      if (
        desktop().dailyReview &&
        hour >= 21 &&
        vault.meta("dayReviewSeen") !== day
      ) {
        vault.setMeta("dayReviewSeen", day);
        openBrief("day");
      }
      if (
        desktop().weeklyReview &&
        new Date().getDay() === 0 &&
        hour >= 18 &&
        vault.meta("weekReviewSeen") !== day
      ) {
        vault.setMeta("weekReviewSeen", day);
        openBrief("week");
      }
      const financial = snapshot().finance;
      pushNotices(
        financial.budgets.flatMap((b) =>
          b.limit > 0
            ? [80, 100, 120]
                .filter((n) => (b.used / b.limit) * 100 >= n)
                .map((n) => ({
                  id: "budget:" + b.item.id + ":" + financial.month + ":" + n,
                  title:
                    "Orçamento " +
                    b.item.title +
                    ": " +
                    n +
                    "% do limite atingido",
                  itemId: b.item.id,
                }))
            : [],
        ),
      );
      const reminders: Array<{ id: string; title: string; itemId: string }> =
        [];
      for (const i of vault.items(false)) {
        if (
          i.done ||
          !i.date ||
          !["task", "event", "bill"].includes(i.type) ||
          (!i.fields.reminder && i.type !== "event" && i.type !== "bill")
        )
          continue;
        const time = i.fields.reminder || i.fields.time || "09:00";
        const date = new Date(i.date + "T" + time + ":00").valueOf();
        const key = i.id + ":" + i.date + ":" + time;
        if (
          date <= Date.now() &&
          i.date === today() &&
          /^([01]\d|2[0-3]):[0-5]\d$/.test(time)
        ) {
          reminders.push({ id: key, title: i.title, itemId: i.id });
        }
      }
      pushNotices(reminders);
    }, 15000);
    if (!qa)
      void updater
        .check()
        .then((r) => {
          if (r) {
            desktopSave({ updateAvailable: r.version });
            notice(
              "Veyra Life " + r.version + " disponível",
              "Confira as novidades em Configurações.",
            );
          }
        })
        .catch(() => {});
    void refreshWeather().catch(() => {});
  })
  .catch(() => {
    dialog.showErrorBox(
      "Veyra Life",
      "Não foi possível abrir o armazenamento protegido. Seus arquivos foram preservados. Consulte o guia de recuperação.",
    );
    quitting = true;
    app.quit();
  });
app.on("second-instance", () => openWindow());
app.on("window-all-closed", () => {
  if (qa || desktop().keepTray === false) {
    quitting = true;
    app.quit();
  }
});
app.on("before-quit", () => {
  quitting = true;
  glass?.shutdown();
  engine?.stop();
  googleAttempt?.cancel();
  globalShortcut.unregisterAll();
  tray?.destroy();
});
