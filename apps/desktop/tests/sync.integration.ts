import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, rmSync, mkdirSync } from "node:fs";
import { resolve } from "node:path";
import { FirebaseClient, SyncEngine } from "../electron/firebase";
import { SecureFiles, Vault } from "../electron/vault";
import { createItem } from "../shared/model";
const auth = "http://127.0.0.1:9095";
const firestore = "http://127.0.0.1:8085";
async function account() {
  const email = `desktop-${crypto.randomUUID()}@example.test`,
    password = "Synthetic-test-only-482";
  const response = await fetch(
    `${auth}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake-key`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password, returnSecureToken: true }),
    },
  );
  assert(response.ok);
  const user = await response.json();
  const verify = await fetch(
    `${auth}/identitytoolkit.googleapis.com/v1/accounts:update?key=fake-key`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: "Bearer owner",
      },
      body: JSON.stringify({
        localId: user.localId,
        emailVerified: true,
        targetProjectId: "demo-veyra",
      }),
    },
  );
  assert(verify.ok);
  return { email, password, uid: user.localId };
}
test("real Firestore rules: two devices, revisions, conflicts, offline recovery and UID isolation", async () => {
  mkdirSync(".qa", { recursive: true });
  const dir = mkdtempSync(resolve(".qa/sync-"));
  const user = await account();
  const vaults: Vault[] = [];
  const engines: SyncEngine[] = [];
  async function device(name: string) {
    const files = new SecureFiles(resolve(dir, name), {
      protect: (b) => b,
      unprotect: (b) => b,
    });
    const network = { offline: false };
    const client = new FirebaseClient(
      {
        apiKey: "fake-key",
        projectId: "production-must-never-be-used",
        appId: "fake",
        authDomain: "localhost",
      },
      files,
      (async (...args: Parameters<typeof fetch>) => {
        if (network.offline) throw Error("network offline synthetic test");
        return fetch(...args);
      }) as typeof fetch,
      { auth, firestore },
    );
    await client.login(user.email, user.password);
    assert(client.user()?.verified);
    const vault = await Vault.open(
      files,
      user.uid,
      resolve("node_modules/sql.js/dist/sql-wasm.wasm"),
    );
    vaults.push(vault);
    const engine = new SyncEngine(client, vault, () => {});
    engines.push(engine);
    return { files, client, vault, engine, network };
  }
  try {
    const a = await device("pc"),
      b = await device("phone");
    const note = createItem("note", {
      title: "Original",
      notes: "Offline first",
    });
    a.vault.save([note]);
    await a.engine.sync();
    assert.equal(a.engine.error, "");
    assert.equal(a.vault.counts().pending, 0);
    await b.engine.sync();
    assert.equal(b.engine.error, "");
    assert.equal(b.vault.item(note.id)?.notes, "Offline first");
    a.vault.save([{ ...note, notes: "PC edit" }]);
    b.vault.save([{ ...b.vault.item(note.id)!, notes: "Phone edit" }]);
    await a.engine.sync();
    assert.equal(a.engine.error, "");
    await b.engine.sync();
    assert.equal(b.engine.error, "");
    assert.equal(b.vault.counts().conflicts, 1);
    b.vault.resolve(note.id, false);
    await b.engine.sync();
    assert.equal(b.engine.error, "");
    await a.engine.sync();
    assert.equal(a.vault.item(note.id)?.notes, "Phone edit");
    const task = createItem("task", { title: "Offline persisted task" });
    b.vault.save([task]);
    b.network.offline = true;
    await b.engine.sync();
    assert.equal(b.engine.status, "offline");
    assert.equal(b.vault.counts().pending, 1);
    b.network.offline = false;
    b.engine.stop();
    b.vault.close();
    const reopened = await Vault.open(
      b.files,
      user.uid,
      resolve("node_modules/sql.js/dist/sql-wasm.wasm"),
    );
    vaults.push(reopened);
    const resumed = new SyncEngine(b.client, reopened, () => {});
    engines.push(resumed);
    await resumed.sync();
    assert.equal(resumed.error, "");
    await a.engine.sync();
    assert.equal(a.vault.item(task.id)?.title, task.title);
    await assert.rejects(
      a.client.request("foreign-uid", "/users/foreign-uid", undefined, "GET"),
      /conta mudou/,
    );
    const token = await a.client.token();
    const denied = await fetch(a.client.base() + "/users/foreign-uid", {
      headers: { Authorization: "Bearer " + token },
    });
    assert.equal(denied.status, 403);
  } finally {
    engines.forEach((e) => e.stop());
    vaults.forEach((v) => {
      try {
        v.close();
      } catch {}
    });
    rmSync(dir, { recursive: true, force: true });
  }
});
