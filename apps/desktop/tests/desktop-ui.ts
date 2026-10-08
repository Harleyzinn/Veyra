import { _electron as electron } from "@playwright/test";
import assert from "node:assert/strict";
import { resolve } from "node:path";
import { mkdirSync } from "node:fs";
const data = resolve(".qa/ui-" + Date.now());
mkdirSync(data, { recursive: true });
const errors: string[] = [];
async function launch() {
  const env: NodeJS.ProcessEnv = {
    ...process.env,
    VEYRA_QA: "1",
    VEYRA_QA_DATA: data,
  };
  delete env.ELECTRON_RUN_AS_NODE;
  const app = await electron.launch({
    executablePath: resolve("node_modules/electron/dist/electron.exe"),
    args: ["."],
    env: env as Record<string, string>,
    timeout: 30000,
  });
  await app.firstWindow();
  let page = app.windows().find((p) => p.url().endsWith("#main"));
  for (let n = 0; !page && n < 60; n++) {
    await new Promise((r) => setTimeout(r, 100));
    page = app.windows().find((p) => p.url().endsWith("#main"));
  }
  if (!page) throw Error("Janela principal indisponível");
  page.on("pageerror", (e) => errors.push(e.message));
  await page.waitForSelector(".sidebar", { timeout: 30000 });
  if (
    await page
      .getByRole("button", {
        name: "Continuar no espaço visitante",
        exact: true,
      })
      .isVisible()
  )
    await page
      .getByRole("button", {
        name: "Continuar no espaço visitante",
        exact: true,
      })
      .click();
  return { app, page };
}
async function shutdown(app: any) {
  const owner = app.windows().find((p: any) => p.url().endsWith("#main"));
  if (owner)
    await owner.evaluate(() => window.veyra.call("quit")).catch(() => {});
  try {
    await Promise.race([
      app.close(),
      new Promise((_, reject) =>
        setTimeout(() => reject(Error("Shutdown timeout")), 10000),
      ),
    ]);
  } catch {
    app.process().kill();
  }
}
async function run() {
  let { app, page } = await launch();
  try {
    const date = new Date().toLocaleDateString("sv-SE");
    const item = (
      id: string,
      type: string,
      title: string,
      fields: any = {},
    ) => ({
      id,
      type,
      title,
      notes: "",
      date,
      done: false,
      favorite: false,
      tags: "",
      parentId: "",
      fields,
      createdAt: Date.now(),
      deletedAt: 0,
    });
    const records = [
      item("qa-account", "account", "Conta de testes", {
        opening: "1000",
        currency: "BRL",
      }),
      item("qa-income", "income", "Salário de testes", {
        amount: "3000",
        currency: "BRL",
        account: "qa-account",
        status: "received",
      }),
      item("qa-expense", "expense", "Mercado de testes", {
        amount: "150.25",
        currency: "BRL",
        account: "qa-account",
        status: "paid",
      }),
      item("qa-task", "task", "Organizar a semana", { priority: "Alta" }),
      item("qa-note", "note", "Ideias de teste", { folder: "Pessoal" }),
    ];
    await page.evaluate(async (records) => {
      const snap = await window.veyra.call("snapshot");
      await window.veyra.call("save", {
        items: records,
        expectedUid: snap.uid,
      });
    }, records);
    await page
      .getByRole("button", { name: "Centro financeiro", exact: true })
      .first()
      .click();
    await page
      .getByRole("heading", { name: "Finanças", exact: true })
      .waitFor();
    const snap = await page.evaluate(() => window.veyra.call("snapshot"));
    assert.equal(snap.finance.balance, 384975);
    await page.screenshot({ path: ".qa/finance-desktop.png", fullPage: true });
    await page
      .getByRole("button", { name: "Notas", exact: true })
      .first()
      .click();
    await page
      .getByLabel("Conteúdo da nota")
      .fill("# Minha nota\n\nPersistência offline **confirmada**.");
    await page.getByText("Salvo neste PC", { exact: true }).waitFor();
    await page
      .getByRole("button", { name: /^Tarefas/ })
      .first()
      .click();
    await page.getByRole("button", { name: "Kanban", exact: true }).click();
    await page.locator(".kanban-card").first().waitFor();
    await page
      .getByRole("button", { name: "Concluir", exact: true })
      .first()
      .click();
    await page.waitForTimeout(250);
    assert.equal(
      (await page.evaluate(() => window.veyra.call("item", "qa-task"))).done,
      true,
    );
    const created = app.waitForEvent("window");
    await page.evaluate(() => window.veyra.call("openWindow", "mini"));
    const mini = await created;
    await mini.waitForSelector(".veyra-glass-panel");
    await mini.close();
    const quickCreated = app.waitForEvent("window");
    await page.evaluate(() => window.veyra.call("openWindow", "quick"));
    const quick = await quickCreated;
    await quick.getByRole("button", { name: "Tarefa", exact: true }).click();
    await quick
      .getByLabel("Título", { exact: true })
      .fill("Captura rápida validada");
    await quick
      .getByRole("button", { name: "Salvar registro", exact: true })
      .click();
    await page.waitForTimeout(150);
    const captured = await page.evaluate(() =>
      window.veyra.call("search", {
        query: "Captura rápida validada",
        types: ["task"],
      }),
    );
    assert.equal(captured.total, 1);
    await page
      .getByRole("button", { name: "Visão geral", exact: true })
      .first()
      .click();
    await page.screenshot({
      path: ".qa/dashboard-desktop.png",
      fullPage: true,
    });
    await shutdown(app);
    ({ app, page } = await launch());
    const note = await page.evaluate(() =>
      window.veyra.call("item", "qa-note"),
    );
    assert(note.notes.includes("Persistência offline"));
    assert.equal(errors.length, 0, errors.join("\n"));
    console.log(
      "Desktop UI: financeiro, Markdown autosave, Kanban, mini janela e recuperação offline aprovados.",
    );
  } finally {
    await app.close();
  }
}
void run().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});
