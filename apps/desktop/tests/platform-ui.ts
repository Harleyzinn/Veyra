import { _electron as electron } from "@playwright/test";
import assert from "node:assert/strict";
import { resolve } from "node:path";
import { mkdirSync, writeFileSync, readFileSync } from "node:fs";
import { createItem, today } from "../shared/model";

async function run() {
  const profile = resolve(".qa/platform-" + Date.now());
  mkdirSync(profile, { recursive: true });
  const env = { ...process.env, VEYRA_QA: "1", VEYRA_QA_DATA: profile };
  delete (env as any).ELECTRON_RUN_AS_NODE;
  const packaged = process.env.VEYRA_PLATFORM_PACKAGED === "1";
  const app = await electron.launch({
    executablePath: resolve(
      packaged
        ? "release/win-unpacked/Veyra Life.exe"
        : "node_modules/electron/dist/electron.exe",
    ),
    args: packaged ? ["--user-data-dir=" + profile] : ["."],
    env: env as Record<string, string>,
  });
  const errors: string[] = [];
  try {
    const page = await app.firstWindow();
    page.on("pageerror", (e) => errors.push(e.message));
    await page.waitForSelector(".sidebar");
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
    await page.evaluate(() =>
      window.veyra.call("desktop", { welcomeSeen: true }),
    );
    const call = (method: string, value?: any) =>
      page.evaluate(({ method, value }) => window.veyra.call(method, value), {
        method,
        value,
      });
    const nav = async (label: string) => {
      await page
        .getByRole("button", { name: label, exact: true })
        .first()
        .click();
    };
    const records = [
      createItem("project", { id: "p", title: "Viagem integrada" }),
      createItem("task", {
        id: "t",
        title: "Reservar hotel",
        date: "",
        parentId: "p",
      }),
      createItem("note", {
        id: "n",
        title: "Referência",
        notes: "Conteúdo antigo",
        parentId: "p",
      }),
      createItem("note", {
        id: "b",
        title: "Roteiro",
        notes: "Veja [[Referência]]",
      }),
      createItem("inbox", {
        id: "i",
        title: "Comprar adaptador",
        notes: "Captura preservada",
        date: "",
      }),
      createItem("template", {
        id: "tpl",
        title: "Preparar viagem",
        fields: {
          targetType: "project",
          lines: "Comprar passagem\nReservar pousada",
        },
      }),
      createItem("automation", {
        id: "auto",
        title: "Alerta alimentação",
        date: "",
        fields: {
          desktopEnabled: "yes",
          trigger: "expense",
          action: "alert",
          category: "Alimentação",
          minimum: "40",
          currency: "BRL",
          message: "Revisar refeições",
        },
      }),
    ];
    await call("save", { items: records });
    await nav("Visão geral");
    await page
      .getByRole("button", { name: "Personalizar", exact: true })
      .click();
    await page.locator(".widget-gallery").waitFor();
    await page.getByLabel("Tamanho de tasks").selectOption("full");
    assert.equal(
      (await call("snapshot")).desktop.layout.find((w: any) => w.id === "tasks")
        .size,
      "full",
    );
    await page.getByRole("button", { name: "Restaurar layout padrão" }).click();
    await page.getByRole("button", { name: "Concluir edição" }).click();
    await page.keyboard.press("Control+n");
    await page.getByLabel("Digite uma ação").fill("Gastei 42,35 no almoço");
    await page.locator(".proposal").click();
    await page
      .getByRole("button", { name: "Salvar registro", exact: true })
      .click();
    const search = await call("universalSearch", {
      query: "type:expense category:Alimentação amount:>40",
    });
    assert.equal(search.total, 1);
    assert.equal(search.items[0].fields.amountMinor, "4235");
    assert.equal(
      (await call("notifications")).filter(
        (n: any) => n.title === "Revisar refeições",
      ).length,
      1,
    );
    await page.keyboard.press("Control+k");
    await page.getByLabel("Pesquisar Veyra").fill("> dark");
    await page.locator(".palette-results button").first().click();
    assert.equal((await call("snapshot")).desktop.theme, "dark");
    await nav("Caixa de entrada");
    await page.getByRole("button", { name: "Organizar", exact: true }).click();
    await page.getByLabel("Destino").selectOption("task");
    await page.getByRole("button", { name: "Confirmar organização" }).click();
    const original = await call("item", "i");
    assert(original.done);
    assert.equal(original.notes, "Captura preservada");
    assert.equal(
      (await call("item", original.fields.organizedId)).type,
      "task",
    );
    await nav("Projetos");
    await page.getByText("Viagem integrada", { exact: true }).first().waitFor();
    await page.getByText("Reservar hotel", { exact: true }).waitFor();
    await nav("Planejar meu dia");
    await page.getByLabel("Tarefa", { exact: true }).selectOption("t");
    await page.getByLabel("Horário", { exact: true }).fill("14:00");
    await page.getByRole("button", { name: "Agendar", exact: true }).click();
    assert.equal((await call("item", "t")).fields.time, "14:00");
    await nav("Notas");
    await page.getByRole("button", { name: /^Referência Conteúdo/ }).click();
    await page
      .getByLabel("Conteúdo da nota")
      .fill("# Planejamento\n\nOffline confirmado.");
    await page.getByText("Salvo neste PC", { exact: true }).waitFor();
    await page.getByRole("button", { name: "Roteiro", exact: true }).waitFor();
    const base = await call("item", "n");
    await page.getByLabel("Conteúdo da nota").fill("Meu rascunho concorrente");
    await call("save", {
      item: { ...base, notes: "Alteração de outro editor" },
      base,
    });
    await page.getByText(/rascunho permanece neste PC/).waitFor();
    assert.equal((await call("item", "n")).notes, "Alteração de outro editor");
    assert(
      (await call("drafts")).some(
        (d: any) => d.item.notes === "Meu rascunho concorrente",
      ),
    );
    const project = await call("applyTemplate", { id: "tpl" });
    const snap = await call("snapshot");
    assert.equal(
      snap.items.filter(
        (i: any) => i.parentId === project.id && i.type === "task",
      ).length,
      2,
    );
    const csv = resolve(profile, "import.csv");
    writeFileSync(
      csv,
      "type;date;title;amount;currency;category\nexpense;" +
        today() +
        ";Importação exata;12.34;BRL;Teste",
    );
    await app.evaluate(({ dialog }, path) => {
      dialog.showOpenDialog = (async () => ({
        canceled: false,
        filePaths: [path],
      })) as any;
    }, csv);
    const preview = await call("financeImportPreview");
    assert.equal(preview.newCount, 1);
    assert.equal(
      (await call("universalSearch", { query: "Importação exata" })).total,
      0,
    );
    await call("financeImportConfirm", { token: preview.token });
    assert.equal((await call("financeImportPreview")).newCount, 0);
    const output = resolve(profile, "report.csv");
    await app.evaluate(({ dialog }, path) => {
      dialog.showSaveDialog = (async () => ({
        canceled: false,
        filePath: path,
      })) as any;
    }, output);
    await call("exportReport", {
      from: today(),
      to: today(),
      currency: "BRL",
      category: "Teste",
    });
    const exported = readFileSync(output, "utf8");
    assert(exported.includes("12.34"));
    assert(!exported.includes("almoço"));
    assert(!exported.includes("Conteúdo antigo"));
    await assert.rejects(
      call("attach", { path: csv }),
      /arquivo|arrast|autoriza/i,
    );
    const png =
      "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jG7sAAAAASUVORK5CYII=";
    const imageNote = createItem("note", {
      title: "Imagem local",
      fields: {
        attachment: png,
        attachmentName: "pixel.png",
        hasAttachment: "yes",
      },
    });
    await call("save", { item: imageNote });
    assert(
      (await call("attachmentPreview", imageNote.id)).startsWith(
        "data:image/png;base64,",
      ),
    );
    const unsafeNote = createItem("note", {
      title: "SVG como arquivo",
      fields: {
        attachment: Buffer.from("<svg onload=alert(1)></svg>").toString(
          "base64",
        ),
        hasAttachment: "yes",
        attachmentName: "unsafe.svg",
      },
    });
    await call("save", { item: unsafeNote });
    assert.equal(await call("attachmentPreview", unsafeNote.id), null);
    const diagnostic = JSON.stringify(await call("diagnostics"));
    for (const secret of [
      "Viagem integrada",
      "rascunho concorrente",
      profile,
      "idToken",
      "refreshToken",
    ])
      assert(!diagnostic.includes(secret));
    await page.keyboard.press("Control+k");
    await page.getByLabel("Pesquisar Veyra").fill("diagnóstico");
    await page.locator(".palette-results button").first().click();
    await page
      .getByRole("button", { name: "Recuperar como cópia" })
      .first()
      .click();
    await page
      .getByRole("button", { name: "Salvar registro", exact: true })
      .click();
    assert.equal((await call("item", "n")).notes, "Alteração de outro editor");
    assert.equal(
      (await call("universalSearch", { query: "rascunho concorrente" })).total,
      1,
    );
    await nav("Notas");
    await page.getByRole("button", { name: /^Roteiro Veja/ }).click();
    await page
      .getByRole("button", { name: "Edição visual", exact: true })
      .click();
    await page.getByLabel("Editor visual da nota").evaluate((node) => {
      const range = document.createRange();
      range.selectNodeContents(node.firstChild!);
      const selection = window.getSelection()!;
      selection.removeAllRanges();
      selection.addRange(range);
    });
    await page.getByRole("button", { name: "Negrito", exact: true }).click();
    await page.getByText("Salvo neste PC", { exact: true }).waitFor();
    assert((await call("item", "b")).notes.includes("**Veja"));
    await nav("Centro financeiro");
    await page
      .getByRole("button", { name: "Calendário financeiro", exact: true })
      .click();
    await page.locator(".finance-calendar-grid").waitFor();
    await page
      .getByRole("button", { name: "Assinaturas", exact: true })
      .click();
    await page
      .getByRole("heading", { name: "Assinaturas", exact: true })
      .waitFor();
    await page.getByRole("button", { name: "Relatórios", exact: true }).click();
    await page
      .getByRole("button", { name: "Importar CSV com prévia" })
      .waitFor();
    for (const label of [
      "Revisão da semana",
      "Modelos",
      "Automações",
      "Notificações",
      "Favoritos",
      "Recentes",
    ]) {
      await nav(label);
      await page.locator(".page-title").waitFor();
      assert.equal(
        await page.getByText("Não conseguimos abrir este módulo.").count(),
        0,
      );
    }
    const opened = app.waitForEvent("window");
    await call("openWindow", "brief");
    const brief = await opened;
    brief.on("pageerror", (e) => errors.push(e.message));
    await brief.locator(".brief-lines").waitFor();
    assert.equal(await brief.locator(".veyra-glass-panel").count(), 1);
    await brief
      .evaluate(() => window.veyra.call("closeWindow"))
      .catch(() => {});
    await nav("Visão geral");
    await page.waitForTimeout(300);
    await page.screenshot({ path: ".qa/platform-home.png", fullPage: true });
    assert.equal(errors.length, 0, errors.join("\n"));
    console.log(
      "Platform integration: capture review, exact finance, gallery, palette, inbox, linked projects, planner, notes concurrency and draft recovery, templates, CSV preview/idempotency/filtered export, file access and private diagnostics passed.",
    );
  } finally {
    const owner = app.windows().find((p) => p.url().endsWith("#main"));
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
}
run().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});
