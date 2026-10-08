import {
  Item,
  Remote,
  Pending,
  PublicUser,
  bucketFor,
  buckets,
  cloudItem,
  amount,
  validateItem,
  withoutBinary,
} from "../shared/model";
import { SecureFiles, Vault, sha } from "./vault";
export interface FirebaseConfig {
  apiKey: string;
  projectId: string;
  appId: string;
  authDomain: string;
}
interface Session {
  idToken: string;
  refreshToken: string;
  expiresAt: number;
  user: PublicUser;
}
type Value = {
  stringValue?: string;
  integerValue?: string;
  booleanValue?: boolean;
  timestampValue?: string;
  mapValue?: { fields: Record<string, Value> };
  nullValue?: null;
};
export const values = (input: Record<string, any>): Record<string, Value> =>
  Object.fromEntries(
    Object.entries(input).map(([key, value]) => [
      key,
      typeof value === "string"
        ? { stringValue: value }
        : typeof value === "number"
          ? { integerValue: String(value) }
          : typeof value === "boolean"
            ? { booleanValue: value }
            : value === null
              ? { nullValue: null }
              : { mapValue: { fields: values(value) } },
    ]),
  );
export function decodeValues(
  input: Record<string, Value>,
): Record<string, any> {
  return Object.fromEntries(
    Object.entries(input).map(([key, v]) => [
      key,
      v.stringValue ??
        (v.integerValue !== undefined ? Number(v.integerValue) : undefined) ??
        v.booleanValue ??
        v.timestampValue ??
        (v.mapValue ? decodeValues(v.mapValue.fields || {}) : null),
    ]),
  );
}
export function envelope(
  uid: string,
  item: Item,
  revision: number,
  operationId: string,
) {
  const i = cloudItem(item);
  const fields = { ...i.fields };
  const minor = amount(i);
  if (
    !["workspace", "notes", "tasks", "settings"].includes(bucketFor(i.type)) &&
    (fields.amount || fields.amountMinor)
  )
    fields.amountMinor = String(minor);
  const data = {
    ...withoutBinary(i),
    fields,
    ownerUid: uid,
    amountMinor: minor,
    currency: fields.currency || "BRL",
    category: fields.category || "",
    accountId: fields.account || "",
    accountDocId: fields.account ? sha(fields.account) : "",
    destinationId: fields.destination || "",
    destinationDocId: fields.destination ? sha(fields.destination) : "",
    cardId: fields.card || "",
    cardDocId: fields.card ? sha(fields.card) : "",
    status: [
      "income",
      "expense",
      "transfer",
      "invoice_payment",
      "bill",
      "receivable",
    ].includes(i.type)
      ? fields.status || ""
      : "",
    recurrenceId:
      fields.recurrenceRuleId || fields.recurrenceId || fields.source || "",
    revision,
    operationId,
  };
  if (Buffer.byteLength(JSON.stringify(data)) > 600000)
    throw Error(
      "Registro grande demais para sincronizar. Seus dados locais continuam preservados.",
    );
  return data;
}
export function decodeDocument(document: any, uid: string): Remote {
  const data = decodeValues(document.fields || {});
  if (
    data.ownerUid !== uid ||
    document.name.split("/").at(-1) !== sha(data.id) ||
    !Number.isSafeInteger(data.revision) ||
    data.revision < 1 ||
    typeof data.operationId !== "string"
  )
    throw Error("Documento remoto inválido.");
  const item: Item = {
    id: data.id,
    type: data.type,
    title: data.title,
    notes: data.notes || "",
    date: data.date || "",
    done: data.done,
    favorite: data.favorite,
    tags: data.tags || "",
    parentId: data.parentId || "",
    fields: data.fields || {},
    createdAt: data.createdAt,
    deletedAt: data.deletedAt,
  };
  validateItem(item);
  return {
    item,
    revision: data.revision,
    operationId: data.operationId,
    updatedAt: data.updatedAt || document.updateTime || "",
  };
}
export class FirebaseClient {
  session: Session | null;
  private refreshing: Promise<void> | null = null;
  constructor(
    readonly config: FirebaseConfig | null,
    private files: SecureFiles,
    private fetcher: typeof fetch = fetch,
    readonly emulator?: { auth: string; firestore: string },
  ) {
    this.session = files.json<Session>("session.safe");
  }
  user() {
    return this.session?.user || null;
  }
  private endpoint(method: string) {
    return this.emulator
      ? `${this.emulator.auth}/identitytoolkit.googleapis.com/v1/accounts:${method}?key=fake-key`
      : `https://identitytoolkit.googleapis.com/v1/accounts:${method}?key=${this.config!.apiKey}`;
  }
  private async raw(
    url: string,
    body: any,
    headers: Record<string, string> = {},
    method = "POST",
  ) {
    const response = await this.fetcher(url, {
      method,
      headers: { "Content-Type": "application/json", ...headers },
      body:
        body === undefined
          ? undefined
          : typeof body === "string"
            ? body
            : JSON.stringify(body),
      signal: AbortSignal.timeout(20000),
    });
    const text = await response.text();
    let result: any;
    try {
      result = JSON.parse(text || "{}");
    } catch {
      throw Error("Resposta de rede inválida.");
    }
    if (!response.ok) {
      const e = Error(
        response.status === 401
          ? "Sua sessão precisa ser renovada."
          : response.status === 403
            ? "Acesso negado. Confira a conta e a verificação do e-mail."
            : response.status >= 500
              ? "Servidor temporariamente indisponível."
              : result.error?.message || "Não foi possível concluir a conexão.",
      ) as Error & { status: number };
      e.status = response.status;
      throw e;
    }
    return result;
  }
  private persist() {
    this.files.setJSON("session.safe", this.session);
  }
  async login(email: string, password: string) {
    if (!this.config && !this.emulator)
      throw Error("Configure o Firebase para entrar.");
    const result = await this.raw(this.endpoint("signInWithPassword"), {
      email,
      password,
      returnSecureToken: true,
    });
    await this.accept(result);
  }
  async google(googleIdToken: string) {
    if (!this.config) throw Error("Firebase indisponível.");
    const result = await this.raw(this.endpoint("signInWithIdp"), {
      postBody: new URLSearchParams({
        id_token: googleIdToken,
        providerId: "google.com",
      }).toString(),
      requestUri: "http://localhost",
      returnIdpCredential: false,
      returnSecureToken: true,
    });
    await this.accept(result);
  }
  private async accept(result: any) {
    const data = await this.raw(this.endpoint("lookup"), {
      idToken: result.idToken,
    });
    const profile = data.users?.[0];
    if (!profile) throw Error("Conta não encontrada.");
    this.session = {
      idToken: result.idToken,
      refreshToken: result.refreshToken,
      expiresAt: Date.now() + Number(result.expiresIn || 3600) * 1000,
      user: {
        uid: profile.localId,
        name: profile.displayName || profile.email.split("@")[0],
        email: profile.email,
        verified: profile.emailVerified === true,
      },
    };
    this.persist();
  }
  async token() {
    if (!this.session) throw Error("Entre na sua conta.");
    if (this.session.expiresAt - Date.now() < 60000) {
      this.refreshing ??= this.refresh().finally(
        () => (this.refreshing = null),
      );
      await this.refreshing;
    }
    return this.session!.idToken;
  }
  private async refresh() {
    if (!this.session) return;
    const previous = this.session;
    const url = this.emulator
      ? `${this.emulator.auth}/securetoken.googleapis.com/v1/token?key=fake-key`
      : `https://securetoken.googleapis.com/v1/token?key=${this.config!.apiKey}`;
    const result = await this.raw(
      url,
      new URLSearchParams({
        grant_type: "refresh_token",
        refresh_token: previous.refreshToken,
      }).toString(),
      { "Content-Type": "application/x-www-form-urlencoded" },
    );
    if (this.session !== previous) return;
    this.session = {
      ...previous,
      idToken: result.id_token,
      refreshToken: result.refresh_token,
      expiresAt: Date.now() + Number(result.expires_in) * 1000,
    };
    this.persist();
  }
  async refreshUser() {
    const token = await this.token();
    const result = await this.raw(this.endpoint("lookup"), { idToken: token });
    if (!this.session) return;
    const p = result.users?.[0];
    if (!p) throw Error("Conta indisponível.");
    this.session.user = {
      uid: p.localId,
      name: p.displayName || p.email.split("@")[0],
      email: p.email,
      verified: p.emailVerified === true,
    };
    this.session.expiresAt = 0;
    await this.token();
    this.persist();
  }
  logout() {
    this.session = null;
    this.persist();
  }
  root() {
    const project = this.emulator
      ? "demo-veyra"
      : this.config?.projectId || "demo-veyra";
    return `projects/${project}/databases/(default)/documents`;
  }
  base() {
    return this.emulator
      ? `${this.emulator.firestore}/v1/${this.root()}`
      : `https://firestore.googleapis.com/v1/${this.root()}`;
  }
  private check(uid: string) {
    if (this.user()?.uid !== uid)
      throw Error("A conta mudou. A resposta anterior foi descartada.");
  }
  async request(uid: string, path: string, body?: any, method = "POST") {
    this.check(uid);
    const token = await this.token();
    const result = await this.raw(
      this.base() + path,
      body,
      { Authorization: "Bearer " + token },
      method,
    );
    this.check(uid);
    return result;
  }
  async ensureProfile(uid: string) {
    let doc: any;
    try {
      doc = await this.request(uid, "/users/" + uid, undefined, "GET");
    } catch (e) {
      if ((e as any).status !== 404) throw e;
    }
    if (doc) {
      if (decodeValues(doc.fields).deleting)
        throw Error("A exclusão desta conta está em andamento.");
      return;
    }
    const user = this.user()!;
    const data = {
      ownerUid: uid,
      name: user.name,
      email: user.email,
      photo: "",
      emailVerified: user.verified,
      deleting: false,
    };
    await this.request(uid, ":commit", {
      writes: [
        {
          update: { name: this.root() + "/users/" + uid, fields: values(data) },
          currentDocument: { exists: false },
          updateTransforms: [
            "createdAt",
            "lastAccessAt",
            "updatedAt",
            "latestChangeAt",
          ].map((fieldPath) => ({
            fieldPath,
            setToServerValue: "REQUEST_TIME",
          })),
        },
      ],
    });
  }
  async marker(uid: string) {
    const doc = await this.request(uid, "/users/" + uid, undefined, "GET");
    const data = decodeValues(doc.fields);
    if (data.deleting) throw Error("Conta em exclusão.");
    return data.latestChangeAt || "";
  }
  async push(
    uid: string,
    op: Pending,
  ): Promise<{ revision: number; updatedAt: string; remote?: Remote }> {
    const name =
      this.root() +
      `/users/${uid}/${bucketFor(op.item.type)}/${sha(op.item.id)}`;
    const root = this.root() + "/users/" + uid;
    const transaction = (await this.request(uid, ":beginTransaction", {}))
      .transaction;
    try {
      const documents = await this.request(uid, ":batchGet", {
        documents: [root, name],
        transaction,
      });
      const current = documents.find((d: any) => d.found?.name === name)?.found;
      const profile = documents.find((d: any) => d.found?.name === root)?.found;
      if (!profile || decodeValues(profile.fields).deleting)
        throw Error("Conta indisponível.");
      const data = current ? decodeValues(current.fields) : null;
      if (data?.operationId === op.operationId)
        return { revision: data.revision, updatedAt: data.updatedAt };
      if ((data?.revision || 0) !== op.baseRevision) {
        if (!current)
          throw Error(
            "Registro removido definitivamente na nuvem. Exporte sua alteração antes de restaurar.",
          );
        return {
          revision: data!.revision,
          updatedAt: data!.updatedAt,
          remote: decodeDocument(current, uid),
        };
      }
      const revision = op.baseRevision + 1;
      const result = await this.request(uid, ":commit", {
        transaction,
        writes: [
          {
            update: {
              name,
              fields: values(envelope(uid, op.item, revision, op.operationId)),
            },
            currentDocument: current
              ? { updateTime: current.updateTime }
              : { exists: false },
            updateTransforms: [
              { fieldPath: "updatedAt", setToServerValue: "REQUEST_TIME" },
            ],
          },
          {
            update: { name: root, fields: {} },
            updateMask: { fieldPaths: [] },
            updateTransforms: [
              { fieldPath: "latestChangeAt", setToServerValue: "REQUEST_TIME" },
              { fieldPath: "updatedAt", setToServerValue: "REQUEST_TIME" },
            ],
          },
        ],
      });
      return { revision, updatedAt: result.commitTime };
    } finally {
      await this.request(uid, ":rollback", { transaction }).catch(() => {});
    }
  }
  async pull(uid: string, vault: Vault, onChange: () => void) {
    let more = false;
    for (const bucket of buckets) {
      let cursor = vault.meta("cursor:" + bucket);
      for (let page = 0; page < 3; page++) {
        const saved = cursor ? JSON.parse(cursor) : null;
        const parent = this.root() + "/users/" + uid;
        const query: any = {
          from: [{ collectionId: bucket }],
          orderBy: [
            { field: { fieldPath: "updatedAt" }, direction: "ASCENDING" },
            { field: { fieldPath: "__name__" }, direction: "ASCENDING" },
          ],
          limit: 100,
        };
        if (saved)
          query.startAt = {
            before: false,
            values: [
              { timestampValue: saved.at },
              { referenceValue: parent + "/" + bucket + "/" + saved.id },
            ],
          };
        const result = await this.request(uid, `/users/${uid}:runQuery`, {
          structuredQuery: query,
        });
        this.check(uid);
        const docs = result
          .filter((r: any) => r.document)
          .map((r: any) => r.document);
        for (const doc of docs) {
          const remote = decodeDocument(doc, uid);
          if (bucketFor(remote.item.type) !== bucket)
            throw Error("Coleção remota incompatível.");
          vault.applyRemote(remote);
          cursor = JSON.stringify({
            at: remote.updatedAt,
            id: doc.name.split("/").at(-1),
          });
          vault.setMeta("cursor:" + bucket, cursor);
        }
        if (docs.length) onChange();
        if (docs.length < 100) break;
        if (page === 2) more = true;
      }
    }
    return more;
  }
}
export class SyncEngine {
  status = "local";
  error = "";
  private running = false;
  private timer: ReturnType<typeof setTimeout> | null = null;
  private stopped = false;
  constructor(
    private client: FirebaseClient,
    readonly vault: Vault,
    private changed: () => void,
  ) {}
  start() {
    this.stopped = false;
    if (this.vault.uid) this.schedule(200);
  }
  stop() {
    this.stopped = true;
    if (this.timer) clearTimeout(this.timer);
  }
  schedule(delay = 1200) {
    if (this.stopped || !this.vault.uid) return;
    if (this.timer) clearTimeout(this.timer);
    this.timer = setTimeout(() => void this.sync(), delay);
  }
  async sync() {
    if (this.running || this.stopped || !this.vault.uid) return;
    const uid = this.vault.uid;
    if (this.client.user()?.uid !== uid) return;
    this.running = true;
    this.status = "syncing";
    this.error = "";
    this.changed();
    try {
      if (!this.client.user()?.verified) {
        this.status = "verification";
        return;
      }
      await this.client.ensureProfile(uid);
      if (this.stopped) return;
      if (this.vault.meta("bootstrapped") !== "yes") {
        const more = await this.client.pull(uid, this.vault, this.changed);
        if (more) {
          this.schedule(1000);
          return;
        }
        this.vault.setMeta("bootstrapped", "yes");
      }
      const visited = new Set<string>();
      const push = async (op: Pending) => {
        if (
          visited.has(op.item.id) ||
          this.stopped ||
          this.vault.pendingFor(op.item.id)?.operationId !== op.operationId
        )
          return;
        visited.add(op.item.id);
        for (const key of ["account", "destination", "card"]) {
          const id = op.item.fields[key];
          if (id) {
            const pending = this.vault.pendingFor(id);
            if (pending) await push(pending);
          }
        }
        const result = await this.client.push(uid, op);
        if (this.stopped) return;
        if (result.remote) this.vault.recordConflict(result.remote);
        else this.vault.acknowledge(op, result.revision, result.updatedAt);
      };
      for (const op of this.vault.pending()) await push(op);
      const marker = await this.client.marker(uid);
      let more = false;
      if (marker !== this.vault.meta("marker") || !marker) {
        more = await this.client.pull(uid, this.vault, this.changed);
        if (!this.stopped && !more) this.vault.setMeta("marker", marker);
      }
      if (this.stopped) return;
      const counts = this.vault.counts();
      this.status = counts.conflicts
        ? "conflict"
        : counts.pending || more
          ? "pending"
          : "ready";
      if (this.status === "ready")
        this.vault.setMeta("lastSync", String(Date.now()));
      this.schedule(more || this.status === "pending" ? 2000 : 30000);
    } catch (e) {
      if (!this.stopped) {
        this.error = (e as Error).message;
        this.status = /fetch|network|timeout|internet|offline/i.test(this.error)
          ? "offline"
          : "error";
        this.schedule(30000);
      }
    } finally {
      this.running = false;
      if (!this.stopped) {
        this.changed();
        if (!this.timer) this.schedule(30000);
      }
    }
  }
}
