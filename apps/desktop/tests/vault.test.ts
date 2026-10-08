import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, readFileSync, readdirSync, rmSync } from "node:fs";
import { join, resolve } from "node:path";
import { SecureFiles, Vault, sha, retryBusyFile } from "../electron/vault";
import { createItem, cloudItem } from "../shared/model";
const wasm = resolve("node_modules/sql.js/dist/sql-wasm.wasm");
test("temporary Windows file locks retry without swallowing persistent or unrelated failures", () => {
  let attempts = 0;
  const result = retryBusyFile(() => {
    if (attempts++ < 2) throw Object.assign(Error("locked"), { code: "EPERM" });
    return "committed";
  });
  assert.equal(result, "committed");
  assert.equal(attempts, 3);
  assert.throws(
    () =>
      retryBusyFile(() => {
        throw Object.assign(Error("disk full"), { code: "ENOSPC" });
      }),
    /disk full/,
  );
});
test("backup restores permitted preferences and appearance without importing device permissions", async () => {
  const s = await setup();
  try {
    s.vault.preference("name", "Existing name");
    s.vault.importBackup(
      {
        schema: 3,
        items: [],
        preferences: {
          name: "Incoming name",
          financeCurrency: "USD",
          password: "must not import",
        },
        desktop: { theme: "light", clipboard: true, startWindows: true },
      },
      false,
    );
    assert.equal(s.vault.preferences().name, "Existing name");
    assert.equal(s.vault.preferences().financeCurrency, "USD");
    assert(!s.vault.preferences().password);
    const desktop = JSON.parse(s.vault.meta("desktop"));
    assert.equal(desktop.theme, "light");
    assert(!desktop.clipboard);
    assert(!desktop.startWindows);
  } finally {
    s.cleanup();
  }
});
async function setup(uid: string | null = "uid-a") {
  const dir = mkdtempSync(resolve(".qa/vault-"));
  const files = new SecureFiles(dir, {
    protect: (b) => Buffer.from(b),
    unprotect: (b) => Buffer.from(b),
  });
  const vault = await Vault.open(files, uid, wasm);
  return {
    dir,
    files,
    vault,
    cleanup: () => {
      vault.close();
      rmSync(dir, { recursive: true, force: true });
    },
  };
}
test("partial UI saves keep local attachments and concurrent edits retain a recoverable encrypted draft", async () => {
  const s = await setup();
  try {
    const note = createItem("note", {
      title: "Original",
      notes: "Original text",
      fields: {
        attachment: Buffer.from("fixture").toString("base64"),
        attachmentName: "fixture.txt",
        attachmentHash: "hash",
      },
    });
    s.vault.save([note]);
    const partial = {
      ...note,
      title: "From UI",
      fields: { attachmentName: "fixture.txt", attachmentHash: "hash" },
    };
    s.vault.save([partial]);
    assert.equal(
      s.vault.item(note.id)?.fields.attachment,
      note.fields.attachment,
    );
    const base = s.vault.item(note.id)!;
    const draft = { ...base, notes: "My unsaved text" };
    s.vault.writeDraft(draft);
    s.vault.save([{ ...base, notes: "Other window edit" }]);
    s.vault.writeDraft(draft);
    assert.throws(() => s.vault.checkBase(note.id, base), /outra janela/);
    assert.equal(s.vault.item(note.id)?.notes, "Other window edit");
    assert.equal(s.vault.drafts()[0].item.notes, "My unsaved text");
    const other = await Vault.open(s.files, "uid-b", wasm);
    assert.equal(other.drafts().length, 0);
    other.close();
    const reopened = await Vault.open(s.files, "uid-a", wasm);
    assert.equal(reopened.drafts()[0].item.notes, "My unsaved text");
    reopened.close();
  } finally {
    s.cleanup();
  }
});
test("universal SQL search applies combined filters without returning deleted or unrelated records", async () => {
  const s = await setup();
  try {
    s.vault.save([
      createItem("expense", {
        title: "Café viagem",
        date: "2026-10-08",
        tags: "viagem",
        fields: {
          amountMinor: "4200",
          category: "Alimentação",
          currency: "BRL",
        },
      }),
      createItem("expense", {
        title: "Café removido",
        date: "2026-10-08",
        deletedAt: 1,
        fields: { amountMinor: "5000", category: "Alimentação" },
      }),
    ]);
    const result = s.vault.universalSearch(
      "cafe type:expense tag:viagem amount:>40",
    );
    assert.equal(result.total, 1);
    assert.equal(result.items[0].title, "Café viagem");
  } finally {
    s.cleanup();
  }
});
test("encrypted cache does not contain private note text", async () => {
  const s = await setup();
  try {
    s.vault.save([
      createItem("note", {
        title: "Private finance token test",
        notes: "Secret synthetic content",
      }),
    ]);
    const raw = readFileSync(
      join(s.dir, "workspace-" + sha("uid-a") + ".safe"),
    );
    assert(!raw.includes(Buffer.from("Secret synthetic content")));
    assert(!raw.includes(Buffer.from("SQLite format")));
  } finally {
    s.cleanup();
  }
});
test("offline item and queue recover after a crash/reopen", async () => {
  const s = await setup();
  try {
    const note = createItem("note", {
      title: "Offline",
      notes: "Texto preservado",
    });
    s.vault.save([note]);
    s.vault.close();
    const reopened = await Vault.open(s.files, "uid-a", wasm);
    assert.equal(reopened.item(note.id)?.notes, "Texto preservado");
    assert.equal(reopened.counts().pending, 1);
    reopened.close();
    s.vault = await Vault.open(s.files, "uid-a", wasm);
  } finally {
    s.cleanup();
  }
});
test("physical workspace isolation keeps guests and other UIDs separate", async () => {
  const s = await setup();
  const other = await Vault.open(s.files, "uid-b", wasm);
  const guest = await Vault.open(s.files, null, wasm);
  try {
    s.vault.save([createItem("note", { id: "private", title: "Somente A" })]);
    assert.equal(other.item("private"), null);
    assert.equal(guest.item("private"), null);
    assert(readdirSync(s.dir).some((n) => n.includes(sha("uid-b"))));
  } finally {
    other.close();
    guest.close();
    s.cleanup();
  }
});
test("coalesced outbox retains base revision and current intent during acknowledgment", async () => {
  const s = await setup();
  try {
    const item = createItem("task", { title: "Primeira" });
    s.vault.save([item]);
    const old = s.vault.pending()[0];
    s.vault.save([{ ...item, title: "Mais recente" }]);
    s.vault.acknowledge(old, 1, "2026-10-07T10:00:00.000000001Z");
    const current = s.vault.pending()[0];
    assert.equal(current.item.title, "Mais recente");
    assert.equal(current.baseRevision, 1);
    assert.notEqual(current.operationId, old.operationId);
  } finally {
    s.cleanup();
  }
});
test("remote conflicting changes preserve both versions and require a choice", async () => {
  const s = await setup();
  try {
    const item = createItem("note", { title: "Local", notes: "Minha versão" });
    s.vault.save([item]);
    s.vault.applyRemote({
      item: { ...item, notes: "Outro dispositivo" },
      revision: 1,
      operationId: "remote-op",
      updatedAt: "2026-10-07T10:00:00Z",
    });
    assert.equal(s.vault.item(item.id)?.notes, "Minha versão");
    assert.equal(s.vault.conflicts().length, 1);
    assert.equal(s.vault.pending().length, 0);
    s.vault.resolve(item.id, false);
    assert.equal(s.vault.pending()[0].baseRevision, 1);
    assert.equal(s.vault.item(item.id)?.notes, "Minha versão");
    assert.equal(s.vault.conflicts().length, 0);
  } finally {
    s.cleanup();
  }
});
test("accepting remote conflict leaves the displaced version in audit", async () => {
  const s = await setup();
  try {
    const item = createItem("note", { title: "Nota", notes: "Original local" });
    s.vault.save([item]);
    s.vault.applyRemote({
      item: { ...item, notes: "Remota" },
      revision: 1,
      operationId: "remote",
      updatedAt: "2026-10-07T10:00:00Z",
    });
    s.vault.resolve(item.id, true);
    assert.equal(s.vault.item(item.id)?.notes, "Remota");
    assert(
      s.vault.audit(item.id).some((e) => e.before?.notes === "Original local"),
    );
    assert.equal(s.vault.counts().pending, 0);
  } finally {
    s.cleanup();
  }
});
test("local attachment survives an incoming edit and is removed only from cloud envelope", async () => {
  const s = await setup();
  try {
    const item = createItem("note", {
      title: "Arquivo",
      fields: {
        attachment: "c3ludGhldGlj",
        attachmentName: "teste.txt",
        hasAttachment: "yes",
      },
    });
    s.vault.save([item]);
    const op = s.vault.pending()[0];
    s.vault.acknowledge(op, 1, "2026-10-07T10:00:00Z");
    s.vault.applyRemote({
      item: {
        ...item,
        notes: "Texto remoto",
        fields: { attachmentLocalOnly: "yes", hasAttachment: "no" },
      },
      revision: 2,
      operationId: "other",
      updatedAt: "2026-10-07T11:00:00Z",
    });
    assert.equal(s.vault.item(item.id)?.fields.attachment, "c3ludGhldGlj");
    assert.equal(
      cloudItem(s.vault.item(item.id)!).fields.attachment,
      undefined,
    );
  } finally {
    s.cleanup();
  }
});
test("backup import requires valid identities and never overwrites by default", async () => {
  const s = await setup();
  try {
    const item = createItem("note", { title: "Original" });
    s.vault.save([item]);
    const backup = {
      schema: 3,
      items: [{ ...item, title: "Backup diferente" }],
    };
    assert.equal(s.vault.importPreview(backup).changes.length, 1);
    s.vault.importBackup(backup, false);
    assert.equal(s.vault.item(item.id)?.title, "Original");
    s.vault.importBackup(backup, true);
    assert.equal(s.vault.item(item.id)?.title, "Backup diferente");
    assert.throws(() =>
      s.vault.importBackup({ schema: 3, items: [item, item] }, true),
    );
    assert.throws(() => s.vault.save([{ ...item, type: "task" }]));
  } finally {
    s.cleanup();
  }
});
test("preferences sync permitted fields only and preserve unknown future item fields", async () => {
  const s = await setup();
  try {
    s.vault.preference("weatherCity", "city-id");
    assert.equal(s.vault.preferences().weatherCity, "city-id");
    assert.throws(() => s.vault.preference("password", "secret"));
    const item = createItem("custom_module", {
      title: "Compatibilidade",
      fields: { futureField: "preservado" },
    });
    s.vault.save([item]);
    assert.equal(s.vault.item(item.id)?.fields.futureField, "preservado");
  } finally {
    s.cleanup();
  }
});
