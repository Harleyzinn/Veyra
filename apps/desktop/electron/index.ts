import { encodeBackup, decodeBackup } from "./backup";
import { repeatedTask } from "../shared/productivity";
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
} from "electron";
import { join, resolve, basename, sep } from "node:path";
import { existsSync, readFileSync, writeFileSync, statSync } from "node:fs";
import { pathToFileURL } from "node:url";
import { spawn } from "node:child_process";
import { SecureFiles, Vault, sha } from "./vault";
import { FirebaseClient, FirebaseConfig, SyncEngine } from "./firebase";
import { systemGoogle } from "./google-login";
import { DesktopUpdater } from "./updater";
import {
  Item,
  Snapshot,
  createItem,
  withoutBinary,
  today,
  parseMinor,
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
const version = "3.0.0";
let files: SecureFiles,
  vault: Vault,
  client: FirebaseClient,
  engine: SyncEngine,
  updater: DesktopUpdater;
let quitting = false,
  tray: Tray | null = null;
const windows = new Map<string, BrowserWindow>();
let googleAttempt: ReturnType<typeof systemGoogle> | null = null;
let weatherBusy = false;
let importPending: any = null;
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
  });
  windows.set(role, w);
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
  w.on("resize", saveBounds);
  w.on("move", saveBounds);
  void w.loadURL("veyra://app/index.html#" + role);
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
      { label: "Sincronizar", click: () => void engine.sync() },
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
};
function shortcuts(value: Record<string, string>) {
  const before = desktop().shortcuts || defaultShortcuts;
  globalShortcut.unregisterAll();
  const registered: string[] = [];
  try {
    for (const [name, accelerator] of Object.entries(value)) {
      if (!accelerator || accelerator.length > 100)
        throw Error("Atalho inválido.");
      if (
        !globalShortcut.register(accelerator, () =>
          name === "capture"
            ? command("capture", "quick")
            : name === "focus"
              ? command("focus", "widget-focus")
              : command("new:" + name, "quick"),
        )
      )
        throw Error(`O atalho ${accelerator} já está em uso.`);
      registered.push(accelerator);
    }
  } catch (e) {
    registered.forEach((s) => globalShortcut.unregister(s));
    for (const [name, key] of Object.entries(before))
      globalShortcut.register(key as string, () =>
        command(
          name === "capture"
            ? "capture"
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
  engine?.stop();
  const previous = vault;
  vault = await Vault.open(files, uid, join(resources(), "sql-wasm.wasm"));
  engine = new SyncEngine(client, vault, changed);
  if (previous) setTimeout(() => previous.close(), 25000);
  importPending = null;
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
  changed();
}
function snapshot(month?: string, days?: number): Snapshot {
  const all = vault.items();
  let financial;
  try {
    financial = finance(
      all,
      month || today().slice(0, 7),
      vault.preferences().financeCurrency || "BRL",
      days || 30,
      Math.min(31, Math.max(1, Number(vault.preferences().financialDay || 1))),
    );
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
    lastBackup: Number(vault.meta("lastBackup") || 0),
  };
  const focus = prefs.focus || {};
  const remaining = focus.active
    ? focus.paused
      ? focus.remaining
      : Math.max(0, Math.ceil((focus.deadline - Date.now()) / 1000))
    : 0;
  return {
    uid: vault.uid,
    user: client.user(),
    items: all
      .filter((i) => !i.deletedAt && i.type !== "cloud_settings")
      .map(withoutBinary),
    preferences: vault.preferences(),
    desktop: prefs,
    sync: {
      status: engine.status,
      pending: vault.counts().pending,
      conflicts: vault.counts().conflicts,
      lastSync: Number(vault.meta("lastSync") || 0),
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
  changed();
  engine.schedule();
}
function notice(title: string, body: string) {
  if (desktop().notifications && Notification.isSupported() && !qa)
    new Notification({ title, body }).show();
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
  "startup",
  "closeWindow",
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
    case "item":
      return vault.item(String(value));
    case "save": {
      const items: Array<Item> = Array.isArray(value.items)
        ? value.items
        : [value.item];
      if (items.length > 200) throw Error("Muitos registros em uma operação.");
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
      if (answer.response === 1) save([{ ...i, deletedAt: Date.now() }]);
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
      ]);
      if (Object.keys(value).some((k) => !allowed.has(k)))
        throw Error("Configuração não autorizada.");
      desktopSave(value);
      if ("keepTray" in value) setupTray();
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
    case "openWindow": {
      const role = String(value);
      if (
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
      shortcuts(value);
      desktopSave({ shortcuts: value });
      return true;
    case "clipboard": {
      if (!desktop().clipboard)
        throw Error("Habilite a leitura opcional da área de transferência.");
      const { clipboard } = await import("electron");
      return (await clipboard.readText()).slice(0, 100000);
    }
    case "attach": {
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
    setupTray();
    if (!qa)
      try {
        shortcuts(desktop().shortcuts || defaultShortcuts);
      } catch {
        desktopSave({
          shortcutWarning: "Um atalho está em uso. Ajuste em Configurações.",
        });
      }
    openWindow();
    setInterval(() => {
      const focus = desktop().focus;
      if (focus?.active && !focus.paused && focus.deadline <= Date.now())
        finishFocus(true);
    }, 1000);
    const notifications = new Set<string>();
    setInterval(() => {
      if (!desktop().notifications) return;
      for (const i of vault.items(false)) {
        if (i.done || !i.date) continue;
        const time = i.fields.reminder || i.fields.time || "09:00";
        const date = new Date(i.date + "T" + time + ":00").valueOf();
        const key = i.id + ":" + i.date + ":" + time;
        if (
          date <= Date.now() &&
          Date.now() - date < 60000 &&
          !notifications.has(key)
        ) {
          notifications.add(key);
          notice("Lembrete do Veyra", i.title);
        }
      }
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
  engine?.stop();
  googleAttempt?.cancel();
  globalShortcut.unregisterAll();
  tray?.destroy();
});
