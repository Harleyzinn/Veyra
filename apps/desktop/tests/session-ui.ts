import type { Snapshot } from "../shared/model";
import { _electron as electron } from "@playwright/test";
import assert from "node:assert/strict";
import { resolve } from "node:path";
import { mkdirSync } from "node:fs";
async function account() {
  const email = `session-${crypto.randomUUID()}@example.test`,
    password = "Synthetic-session-password-592";
  const endpoint =
    "http://127.0.0.1:9095/identitytoolkit.googleapis.com/v1/accounts:";
  const response = await fetch(endpoint + "signUp?key=fake-key", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password, returnSecureToken: true }),
  });
  assert(response.ok);
  const user = await response.json();
  const verified = await fetch(endpoint + "update?key=fake-key", {
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
  });
  assert(verified.ok);
  return { email, password, uid: user.localId };
}
async function run() {
  const a = await account(),
    b = await account(),
    path = resolve(".qa/session-" + Date.now());
  mkdirSync(path, { recursive: true });
  const env: NodeJS.ProcessEnv = {
    ...process.env,
    VEYRA_QA: "1",
    VEYRA_QA_DATA: path,
    VEYRA_EMULATOR: "1",
  };
  delete env.ELECTRON_RUN_AS_NODE;
  const app = await electron.launch({
    executablePath: resolve("node_modules/electron/dist/electron.exe"),
    args: ["."],
    env: env as Record<string, string>,
  });
  try {
    const page = await app.firstWindow();
    await page.waitForSelector(".sidebar");
    await page
      .getByRole("button", { name: "Entrar com e-mail e senha", exact: true })
      .click();
    await page.getByLabel("E-mail", { exact: true }).fill(a.email);
    await page.getByLabel("Senha", { exact: true }).fill(a.password);
    await page.getByRole("button", { name: "Entrar", exact: true }).click();
    await page.waitForFunction(
      () =>
        document
          .querySelector(".account-switch")
          ?.textContent?.includes("session-") ||
        !document.querySelector(".onboarding"),
    );
    let snap: Snapshot = await page.evaluate(() =>
      window.veyra.call("snapshot"),
    );
    assert.equal(snap.uid, a.uid);
    const record = {
      id: "qa-private-session",
      type: "note",
      title: "A private note",
      notes: "Full notes preserved: " + "private ".repeat(120),
      date: "2026-10-07",
      done: false,
      favorite: false,
      tags: "",
      parentId: "",
      fields: {},
      createdAt: Date.now(),
      deletedAt: 0,
    };
    await page.evaluate(
      (record) => window.veyra.call("save", { item: record }),
      record,
    );
    await page.evaluate(() => window.veyra.call("sync"));
    snap = await page.evaluate(() => window.veyra.call("snapshot"));
    assert.equal(snap.sync.error, "");
    assert.equal(snap.sync.pending, 0);
    assert.equal(
      snap.items.find((i) => i.id === "qa-private-session")?.notes,
      record.notes,
    );
    await app.evaluate(({ dialog }) => {
      dialog.showMessageBox = async () => ({
        response: 1,
        checkboxChecked: false,
      });
    });
    await page.evaluate(() => window.veyra.call("logout"));
    assert.equal(
      (await page.evaluate(() => window.veyra.call("snapshot"))).uid,
      null,
    );
    await page.evaluate(
      (b) =>
        window.veyra.call("login", { email: b.email, password: b.password }),
      b,
    );
    snap = await page.evaluate(() => window.veyra.call("snapshot"));
    assert.equal(snap.uid, b.uid);
    assert(!snap.items.some((i) => i.id === record.id));
    const blocked = await page.evaluate(
      async (input) => {
        try {
          await window.veyra.call("save", {
            item: input.record,
            expectedUid: input.uid,
          });
          return false;
        } catch {
          return true;
        }
      },
      { record, uid: a.uid },
    );
    assert(blocked);
    await page.evaluate(() => window.veyra.call("logout"));
    await page.evaluate(() => window.veyra.call("snapshot"));
    await page.evaluate(
      (a) =>
        window.veyra.call("login", { email: a.email, password: a.password }),
      a,
    );
    snap = await page.evaluate(() => window.veyra.call("snapshot"));
    assert.equal(snap.uid, a.uid);
    assert.equal(
      snap.items.find((i) => i.id === record.id)?.notes,
      record.notes,
    );
    console.log(
      "Desktop sessions: login, cloud sync, logout, account isolation and stale-editor UID rejection approved.",
    );
  } finally {
    await app.close();
  }
}
void run().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});
