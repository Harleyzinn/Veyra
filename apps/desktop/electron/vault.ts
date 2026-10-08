import { status } from "../shared/finance";
import { desktopPatch } from "../shared/settings";
import { parseSearch, matchesSearch, sameContent } from "../shared/platform";
import {
  createCipheriv,
  createDecipheriv,
  randomBytes,
  createHash,
} from "node:crypto";
import {
  mkdirSync,
  existsSync,
  readFileSync,
  writeFileSync,
  renameSync,
  openSync,
  fsyncSync,
  closeSync,
  copyFileSync,
} from "node:fs";
import { join } from "node:path";
import initSqlJs, { Database, SqlJsStatic } from "sql.js";
import {
  Item,
  Remote,
  Pending,
  Conflict,
  validateItem,
  withoutBinary,
  syncPreferences,
  cloudItem,
} from "../shared/model";
export interface Protector {
  protect: (plain: Buffer) => Buffer;
  unprotect: (cipher: Buffer) => Buffer;
}
const fileRetryClock = new Int32Array(new SharedArrayBuffer(4));
export function retryBusyFile<T>(operation: () => T): T {
  for (let attempt = 0; ; attempt++) {
    try {
      return operation();
    } catch (error) {
      if (
        attempt >= 8 ||
        !["EPERM", "EBUSY", "EACCES"].includes(
          (error as NodeJS.ErrnoException).code || "",
        )
      )
        throw error;
      Atomics.wait(fileRetryClock, 0, 0, 25 * (attempt + 1));
    }
  }
}
export function atomicWrite(path: string, data: Buffer) {
  const tmp = path + ".next";
  const fd = openSync(tmp, "w", 0o600);
  try {
    writeFileSync(fd, data);
    fsyncSync(fd);
  } finally {
    closeSync(fd);
  }
  if (existsSync(path))
    retryBusyFile(() => copyFileSync(path, path + ".previous"));
  retryBusyFile(() => renameSync(tmp, path));
}
export class SecureFiles {
  private key: Buffer;
  constructor(
    readonly directory: string,
    protector: Protector,
  ) {
    mkdirSync(directory, { recursive: true });
    const path = join(directory, "vault.key");
    if (existsSync(path)) this.key = protector.unprotect(readFileSync(path));
    else {
      this.key = randomBytes(32);
      atomicWrite(path, protector.protect(this.key));
    }
    if (this.key.length !== 32)
      throw Error("Chave local inválida. Seus arquivos foram preservados.");
  }
  encrypt(data: Buffer) {
    const nonce = randomBytes(12);
    const cipher = createCipheriv("aes-256-gcm", this.key, nonce);
    const bytes = Buffer.concat([cipher.update(data), cipher.final()]);
    return Buffer.concat([
      Buffer.from("VEYRA3"),
      nonce,
      cipher.getAuthTag(),
      bytes,
    ]);
  }
  decrypt(data: Buffer) {
    if (data.subarray(0, 6).toString() !== "VEYRA3")
      throw Error("Arquivo local inválido.");
    const cipher = createDecipheriv(
      "aes-256-gcm",
      this.key,
      data.subarray(6, 18),
    );
    cipher.setAuthTag(data.subarray(18, 34));
    return Buffer.concat([cipher.update(data.subarray(34)), cipher.final()]);
  }
  read(name: string): Buffer | null {
    const path = join(this.directory, name);
    if (!existsSync(path)) return null;
    return this.decrypt(readFileSync(path));
  }
  write(name: string, data: Buffer) {
    atomicWrite(join(this.directory, name), this.encrypt(data));
  }
  json<T>(name: string): T | null {
    const value = this.read(name);
    return value ? JSON.parse(value.toString()) : null;
  }
  setJSON(name: string, value: any) {
    this.write(name, Buffer.from(JSON.stringify(value)));
  }
}
export const sha = (value: string) =>
  createHash("sha256").update(value, "utf8").digest("hex");
export class Vault {
  dataVersion = 0;
  private cachedItems: Item[] | null = null;
  db: Database;
  private name: string;
  private constructor(
    private SQL: SqlJsStatic,
    private files: SecureFiles,
    readonly uid: string | null,
  ) {
    this.name = "workspace-" + sha(uid || "guest") + ".safe";
    const saved = files.read(this.name);
    this.db = new SQL.Database(saved ? new Uint8Array(saved) : undefined);
    const schema = this.query("PRAGMA user_version")[0]?.user_version || 0;
    if (Number(schema) > 1)
      throw Error("Este cache exige uma versão mais recente do Veyra.");
    this.db.run(
      `CREATE TABLE IF NOT EXISTS items(id TEXT PRIMARY KEY,type TEXT NOT NULL,date TEXT NOT NULL,deleted INTEGER NOT NULL,search TEXT NOT NULL,payload TEXT NOT NULL,localRevision INTEGER NOT NULL,serverRevision INTEGER NOT NULL,serverUpdated TEXT NOT NULL);CREATE INDEX IF NOT EXISTS item_type_date ON items(type,date);CREATE TABLE IF NOT EXISTS pending(id TEXT PRIMARY KEY,operationId TEXT NOT NULL,baseRevision INTEGER NOT NULL,localRevision INTEGER NOT NULL,payload TEXT NOT NULL);CREATE TABLE IF NOT EXISTS conflicts(id TEXT PRIMARY KEY,payload TEXT NOT NULL);CREATE TABLE IF NOT EXISTS audit(id TEXT PRIMARY KEY,itemId TEXT NOT NULL,at INTEGER NOT NULL,payload TEXT NOT NULL);CREATE INDEX IF NOT EXISTS audit_item ON audit(itemId,at);CREATE TABLE IF NOT EXISTS metadata(key TEXT PRIMARY KEY,value TEXT NOT NULL);PRAGMA user_version=1;`,
    );
    if (!saved) this.persist();
  }
  static async open(files: SecureFiles, uid: string | null, wasmPath: string) {
    const SQL = await initSqlJs({ locateFile: () => wasmPath });
    return new Vault(SQL, files, uid);
  }
  query(sql: string, args: any[] = []): Record<string, any>[] {
    const statement = this.db.prepare(sql);
    try {
      statement.bind(args);
      const result: Record<string, any>[] = [];
      while (statement.step()) result.push(statement.getAsObject());
      return result;
    } finally {
      statement.free();
    }
  }
  private persist() {
    this.files.write(this.name, Buffer.from(this.db.export()));
  }
  transaction<T>(action: () => T): T {
    const previous = this.db.export();
    const version = this.dataVersion;
    this.db.run("BEGIN");
    try {
      const result = action();
      this.db.run("COMMIT");
      this.persist();
      return result;
    } catch (error) {
      this.dataVersion = version;
      this.cachedItems = null;
      this.db.close();
      this.db = new this.SQL.Database(previous);
      throw error;
    }
  }
  private row(id: string) {
    return this.query("SELECT * FROM items WHERE id=?", [id])[0];
  }
  item(id: string): Item | null {
    const row = this.row(id);
    return row ? JSON.parse(row.payload) : null;
  }
  items(includeDeleted = true): Item[] {
    if (!this.cachedItems)
      this.cachedItems = this.query(
        "SELECT payload FROM items ORDER BY date DESC,id",
      ).map((r) => JSON.parse(r.payload));
    return includeDeleted
      ? this.cachedItems
      : this.cachedItems.filter((i) => !i.deletedAt);
  }
  universalSearch(query: string, limit = 40, offset = 0) {
    const syntax = parseSearch(
      query,
      this.preferences().financeCurrency || "BRL",
    );
    if (syntax.error) throw Error(syntax.error);
    const words = syntax.text
      .toLocaleLowerCase("pt-BR")
      .normalize("NFD")
      .replace(/\p{M}/gu, "")
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 12);
    const clauses = [
        "deleted=0",
        "type!='cloud_settings'",
        ...words.map(() => "instr(search,?)>0"),
      ],
      args: any[] = [...words];
    if (syntax.types.length) {
      clauses.push(`type IN (${syntax.types.map(() => "?").join(",")})`);
      args.push(...syntax.types);
    }
    if (syntax.date) {
      clauses.push("date LIKE ?");
      args.push(syntax.date + "%");
    }
    if (syntax.favorite !== undefined) {
      clauses.push("json_extract(payload,'$.favorite')=?");
      args.push(syntax.favorite ? 1 : 0);
    }
    const rows = this.query(
      "SELECT payload FROM items WHERE " +
        clauses.join(" AND ") +
        " ORDER BY date DESC,id",
      args,
    )
      .map((r) => JSON.parse(r.payload) as Item)
      .filter((i) => matchesSearch(i, { ...syntax, text: "" }));
    const start = Math.max(0, Math.floor(Number(offset) || 0));
    return {
      total: rows.length,
      items: rows
        .slice(start, start + Math.max(1, Math.min(100, Number(limit) || 40)))
        .map(withoutBinary),
    };
  }
  checkBase(id: string, base: Item | undefined) {
    if (!base) return;
    const current = this.item(id);
    if (current && !sameContent(current, base))
      throw Error(
        "Este registro mudou em outra janela ou dispositivo. Sua edição foi preservada como rascunho; reabra para comparar.",
      );
  }
  drafts() {
    return this.query("SELECT value FROM metadata WHERE key LIKE 'draft:%'")
      .map((r) => JSON.parse(r.value))
      .sort((a, b) => b.at - a.at);
  }
  writeDraft(item: Item) {
    const draft = withoutBinary(item);
    validateItem({ ...draft, title: draft.title.trim() || "Rascunho" });
    if (Buffer.byteLength(JSON.stringify(draft)) > 300000)
      throw Error("Rascunho excede o limite local.");
    this.transaction(() => {
      this.setRaw(
        "draft:" + item.id,
        JSON.stringify({ item: draft, at: Date.now() }),
      );
      this.db.run(
        "DELETE FROM metadata WHERE key LIKE 'draft:%' AND key NOT IN (SELECT key FROM metadata WHERE key LIKE 'draft:%' ORDER BY CAST(json_extract(value,'$.at') AS INTEGER) DESC LIMIT 30)",
      );
    });
  }
  clearDraft(id: string) {
    this.transaction(() =>
      this.db.run("DELETE FROM metadata WHERE key=?", ["draft:" + id]),
    );
  }
  activity() {
    return this.query(
      "SELECT payload FROM audit ORDER BY at DESC LIMIT 100",
    ).map((r) => {
      const e = JSON.parse(r.payload);
      return {
        id: e.id,
        itemId: e.itemId,
        at: e.at,
        action: e.action,
        title: e.after?.title || e.before?.title || "",
        type: e.after?.type || e.before?.type || "",
      };
    });
  }
  search(
    query: string,
    types: string[] = [],
    limit = 80,
    offset = 0,
    deleted = false,
    filters: {
      category?: string;
      status?: string;
      currency?: string;
      month?: string;
    } = {},
  ) {
    const words = query
      .toLocaleLowerCase("pt-BR")
      .normalize("NFD")
      .replace(/\p{M}/gu, "")
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 8);
    const clauses = [
      deleted ? "deleted>0" : "deleted=0",
      "type!='cloud_settings'",
      ...words.map(() => "instr(search,?)>0"),
    ];
    const args: any[] = [...words];
    if (types.length) {
      clauses.push(`type IN (${types.map(() => "?").join(",")})`);
      args.push(...types);
    }
    for (const key of ["category", "currency"] as const)
      if (filters[key]) {
        clauses.push(
          "COALESCE(json_extract(payload,'$.fields." + key + "'),?)=?",
        );
        args.push(key === "currency" ? "BRL" : "", filters[key]);
      }
    if (filters.month) {
      clauses.push("date LIKE ?");
      args.push(filters.month + "%");
    }
    const condition = clauses.join(" AND ");
    if (filters.status) {
      const matched = this.query(
        "SELECT payload FROM items WHERE " +
          condition +
          " ORDER BY date DESC,id",
        args,
      )
        .map((r) => JSON.parse(r.payload))
        .filter((i) => status(i) === filters.status);
      return {
        total: matched.length,
        items: matched
          .slice(
            Math.max(0, offset),
            Math.max(0, offset) + Math.max(1, Math.min(limit, 300)),
          )
          .map(withoutBinary),
      };
    }
    const total = Number(
      this.query(
        "SELECT count(*) AS count FROM items WHERE " + condition,
        args,
      )[0].count,
    );
    return {
      total,
      items: this.query(
        "SELECT payload FROM items WHERE " +
          condition +
          " ORDER BY date DESC,id LIMIT ? OFFSET ?",
        [...args, Math.max(1, Math.min(limit, 300)), Math.max(0, offset)],
      ).map((r) => withoutBinary(JSON.parse(r.payload))),
    };
  }
  meta(key: string): string {
    return (
      this.query("SELECT value FROM metadata WHERE key=?", [key])[0]?.value ||
      ""
    );
  }
  private setRaw(key: string, value: string) {
    this.db.run("INSERT OR REPLACE INTO metadata VALUES(?,?)", [key, value]);
  }
  setMeta(key: string, value: string) {
    this.transaction(() => this.setRaw(key, value));
  }
  preferences(): Record<string, string> {
    return Object.fromEntries(
      this.query(
        "SELECT key,value FROM metadata WHERE key LIKE 'preference:%'",
      ).map((r) => [String(r.key).slice(11), r.value]),
    );
  }
  private store(
    i: Item,
    localRevision: number,
    serverRevision: number,
    updated: string,
  ) {
    this.dataVersion++;
    this.cachedItems = null;
    const text = [
      i.title,
      i.notes,
      i.tags,
      i.type,
      ...Object.values(withoutBinary(i).fields),
    ]
      .join(" ")
      .toLocaleLowerCase("pt-BR")
      .normalize("NFD")
      .replace(/\p{M}/gu, "");
    this.db.run("INSERT OR REPLACE INTO items VALUES(?,?,?,?,?,?,?,?,?)", [
      i.id,
      i.type,
      i.date,
      i.deletedAt,
      text,
      JSON.stringify(i),
      localRevision,
      serverRevision,
      updated,
    ]);
    if (i.type === "cloud_settings" && !i.deletedAt)
      for (const [key, value] of Object.entries(i.fields))
        if (syncPreferences.has(key)) this.setRaw("preference:" + key, value);
  }
  private history(
    id: string,
    before: Item | null,
    after: Item,
    action: string,
  ) {
    const event = {
      id: crypto.randomUUID(),
      itemId: id,
      at: Date.now(),
      action,
      before: before ? withoutBinary(before) : null,
      after: withoutBinary(after),
    };
    this.db.run("INSERT INTO audit VALUES(?,?,?,?)", [
      event.id,
      id,
      event.at,
      JSON.stringify(event),
    ]);
  }
  private saveLocal(i: Item, force = false) {
    validateItem(i);
    const row = this.row(i.id);
    const before = row ? JSON.parse(row.payload) : null;
    if (before?.fields.attachment && !Object.hasOwn(i.fields, "attachment"))
      i = {
        ...i,
        fields: {
          ...i.fields,
          attachment: before.fields.attachment,
          attachmentName: before.fields.attachmentName,
          attachmentHash: before.fields.attachmentHash || "",
          hasAttachment: "yes",
        },
      };
    if (before && (before.type !== i.type || before.createdAt !== i.createdAt))
      throw Error("O tipo e a criação do registro não podem ser substituídos.");
    if (!force && before && JSON.stringify(before) === JSON.stringify(i))
      return;
    const revision = (row?.localRevision || 0) + 1;
    this.store(i, revision, row?.serverRevision || 0, row?.serverUpdated || "");
    this.history(i.id, before, i, "local");
    this.db.run("DELETE FROM metadata WHERE key=?", ["draft:" + i.id]);
    if (this.uid) {
      const pending = this.query(
        "SELECT baseRevision FROM pending WHERE id=?",
        [i.id],
      )[0];
      this.db.run("INSERT OR REPLACE INTO pending VALUES(?,?,?,?,?)", [
        i.id,
        crypto.randomUUID(),
        pending?.baseRevision ?? row?.serverRevision ?? 0,
        revision,
        JSON.stringify(i),
      ]);
    }
  }
  save(items: Item[]) {
    if (new Set(items.map((i) => i.id)).size !== items.length)
      throw Error("IDs duplicados.");
    items.forEach(validateItem);
    this.transaction(() => items.forEach((i) => this.saveLocal(i)));
  }
  preference(key: string, value: string) {
    if (!syncPreferences.has(key))
      throw Error("Preferência não sincronizável.");
    this.transaction(() => {
      const old = this.item("workspace-settings");
      const fields = {
        ...(old?.fields || {}),
        ...this.preferences(),
        [key]: value,
      };
      this.saveLocal({
        id: "workspace-settings",
        type: "cloud_settings",
        title: "Preferências da conta",
        notes: "",
        date: "",
        done: false,
        favorite: false,
        tags: "",
        parentId: "",
        fields,
        deletedAt: 0,
        createdAt: old?.createdAt || Date.now(),
      });
    });
  }
  pending(): Pending[] {
    return this.query(
      "SELECT p.* FROM pending p LEFT JOIN conflicts c ON c.id=p.id WHERE c.id IS NULL ORDER BY CASE WHEN json_extract(p.payload,'$.type') IN ('account','card') THEN 0 ELSE 1 END,p.rowid LIMIT 100",
    ).map((r) => ({
      item: JSON.parse(r.payload),
      operationId: r.operationId,
      baseRevision: r.baseRevision,
      localRevision: r.localRevision,
    }));
  }
  pendingFor(id: string): Pending | null {
    const r = this.query("SELECT * FROM pending WHERE id=?", [id])[0];
    return r
      ? {
          item: JSON.parse(r.payload),
          operationId: r.operationId,
          baseRevision: r.baseRevision,
          localRevision: r.localRevision,
        }
      : null;
  }
  counts() {
    return {
      pending: Number(this.query("SELECT count(*) n FROM pending")[0].n),
      conflicts: Number(this.query("SELECT count(*) n FROM conflicts")[0].n),
    };
  }
  acknowledge(op: Pending, revision: number, updated: string) {
    this.transaction(() => {
      const row = this.row(op.item.id);
      if (!row) return;
      this.db.run(
        "UPDATE items SET serverRevision=?,serverUpdated=? WHERE id=?",
        [Math.max(row.serverRevision, revision), updated, op.item.id],
      );
      const current = this.pendingFor(op.item.id);
      if (current?.operationId === op.operationId)
        this.db.run("DELETE FROM pending WHERE id=?", [op.item.id]);
      else if (current && current.baseRevision === op.baseRevision)
        this.db.run("UPDATE pending SET baseRevision=? WHERE id=?", [
          revision,
          op.item.id,
        ]);
    });
  }
  private conflictRaw(remote: Remote) {
    const local = this.item(remote.item.id);
    if (!local) return;
    const conflict: Conflict = {
      id: local.id,
      local,
      remote,
      createdAt: Date.now(),
    };
    this.db.run("INSERT OR REPLACE INTO conflicts VALUES(?,?)", [
      local.id,
      JSON.stringify(conflict),
    ]);
  }
  recordConflict(remote: Remote) {
    this.transaction(() => this.conflictRaw(remote));
  }
  private incoming(remote: Remote) {
    const row = this.row(remote.item.id);
    const before = row ? JSON.parse(row.payload) : null;
    const fields = { ...remote.item.fields };
    if (before?.fields.attachment) {
      fields.attachment = before.fields.attachment;
      fields.attachmentHash = before.fields.attachmentHash || "";
      fields.attachmentName =
        before.fields.attachmentName || fields.attachmentName || "";
      fields.hasAttachment = "yes";
    }
    const item = { ...remote.item, fields };
    this.store(
      item,
      (row?.localRevision || 0) + 1,
      remote.revision,
      remote.updatedAt,
    );
    this.history(item.id, before, item, "remote");
  }
  applyRemote(remote: Remote) {
    validateItem(remote.item);
    this.transaction(() => {
      const row = this.row(remote.item.id);
      if (row && remote.revision <= row.serverRevision) return;
      const pending = this.pendingFor(remote.item.id);
      if (pending) {
        if (pending.operationId === remote.operationId) {
          this.db.run(
            "UPDATE items SET serverRevision=?,serverUpdated=? WHERE id=?",
            [remote.revision, remote.updatedAt, remote.item.id],
          );
          this.db.run("DELETE FROM pending WHERE id=?", [remote.item.id]);
        } else this.conflictRaw(remote);
        return;
      }
      this.incoming(remote);
    });
  }
  conflicts(): Conflict[] {
    return this.query("SELECT payload FROM conflicts ORDER BY rowid DESC").map(
      (r) => JSON.parse(r.payload),
    );
  }
  resolve(id: string, useRemote: boolean) {
    this.transaction(() => {
      const raw = this.query("SELECT payload FROM conflicts WHERE id=?", [
        id,
      ])[0];
      if (!raw) throw Error("Conflito já resolvido.");
      const c: Conflict = JSON.parse(raw.payload);
      const current = this.item(id);
      this.db.run("DELETE FROM conflicts WHERE id=?", [id]);
      this.db.run("DELETE FROM pending WHERE id=?", [id]);
      if (useRemote) this.incoming(c.remote);
      else {
        this.db.run(
          "UPDATE items SET serverRevision=?,serverUpdated=? WHERE id=?",
          [c.remote.revision, c.remote.updatedAt, id],
        );
        if (current) this.saveLocal(current, true);
      }
    });
  }
  audit(id: string) {
    return this.query(
      "SELECT payload FROM audit WHERE itemId=? ORDER BY at DESC LIMIT 100",
      [id],
    ).map((r) => JSON.parse(r.payload));
  }
  backup() {
    return {
      schema: 3,
      workspace: { uid: this.uid },
      items: this.items(),
      preferences: this.preferences(),
      desktop: JSON.parse(this.meta("desktop") || "{}"),
    };
  }
  importPreview(value: any) {
    if (
      !value ||
      ![2, 3].includes(value.schema) ||
      !Array.isArray(value.items) ||
      value.items.length > 30000
    )
      throw Error("Backup incompatível.");
    const items: Item[] = value.items;
    items.forEach(validateItem);
    if (new Set(items.map((i) => i.id)).size !== items.length)
      throw Error("IDs repetidos no backup.");
    return {
      total: items.length,
      newCount: items.filter((i) => !this.item(i.id)).length,
      changes: items
        .filter((i) => {
          const old = this.item(i.id);
          return old && JSON.stringify(old) !== JSON.stringify(i);
        })
        .map((i) => ({ id: i.id, title: i.title, type: i.type })),
      value,
    };
  }
  importBackup(value: any, replace: boolean) {
    this.importPreview(value);
    this.transaction(() => {
      const preferences = this.preferences();
      for (const [key, pref] of Object.entries(value.preferences || {}))
        if (
          syncPreferences.has(key) &&
          typeof pref === "string" &&
          pref.length <= 10000 &&
          (!Object.hasOwn(preferences, key) || replace)
        )
          preferences[key] = pref;
      for (const item of value.items) {
        const previous = this.item(item.id);
        if (!previous || replace) this.saveLocal(item);
      }
      if (Object.keys(preferences).length) {
        const old = this.item("workspace-settings");
        this.saveLocal({
          id: "workspace-settings",
          type: "cloud_settings",
          title: "Preferências da conta",
          notes: "",
          date: "",
          done: false,
          favorite: false,
          tags: "",
          parentId: "",
          fields: preferences,
          deletedAt: 0,
          createdAt: old?.createdAt || Date.now(),
        });
      }
      const desktop = JSON.parse(this.meta("desktop") || "{}");
      const appearance = new Set([
        "layout",
        "theme",
        "accent",
        "density",
        "scale",
        "kanbanColumns",
        "miniWidgets",
        "lastPage",
      ]);
      if (
        value.desktop &&
        Buffer.byteLength(JSON.stringify(value.desktop)) <= 100000
      ) {
        for (const [key, setting] of Object.entries(value.desktop))
          if (appearance.has(key) && (!Object.hasOwn(desktop, key) || replace))
            try {
              desktop[key] = desktopPatch({ [key]: setting })[key];
            } catch {
              /* Invalid appearance never changes the workspace. */
            }
        this.setRaw("desktop", JSON.stringify(desktop));
      }
    });
  }
  outgoing(op: Pending) {
    return cloudItem(op.item);
  }
  close() {
    this.db.close();
  }
}
