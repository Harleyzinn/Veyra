import { _electron as electron } from "@playwright/test";
import assert from "node:assert/strict";
import { resolve } from "node:path";
import { mkdirSync, writeFileSync } from "node:fs";
import { reportCSV, reportRows } from "../shared/reporting";
import { createItem, today, addDays } from "../shared/model";

async function run() {
  const profile = resolve(".qa/refinement-" + Date.now());
  mkdirSync(profile, { recursive: true });
  const env = { ...process.env, VEYRA_QA: "1", VEYRA_QA_DATA: profile };
  delete (env as any).ELECTRON_RUN_AS_NODE;
  const packaged = process.env.VEYRA_PLATFORM_PACKAGED === "1",
    app = await electron.launch({
      executablePath: resolve(
        packaged
          ? "release/win-unpacked/Veyra Life.exe"
          : "node_modules/electron/dist/electron.exe",
      ),
      args: packaged ? ["--user-data-dir=" + profile] : ["."],
      env: env as Record<string, string>,
    });
  try {
    const page = await app.firstWindow(),
      errors: string[] = [];
    page.on("pageerror", (e) => errors.push(e.message));
    await page.waitForSelector(".sidebar");
    const guest = page.getByRole("button", {
      name: "Continuar no espaço visitante",
      exact: true,
    });
    if (await guest.isVisible()) await guest.click();
    const call = (method: string, value?: any) =>
      page.evaluate(({ method, value }) => window.veyra.call(method, value), {
        method,
        value,
      });
    const nav = (name: string) =>
      page
        .locator(".sidebar")
        .getByRole("button", { name, exact: true })
        .click();
    const source =
      "## Título\n\n* Item original\n\n```typescript\nconst valor = 42;\n```\n\n| Nome | Valor |\n| --- | --- |\n| Café | 42 |\n";
    const note = createItem("note", {
      id: "n",
      title: "Formatação preservada",
      notes: source,
    });
    await call("save", {
      items: [
        note,
        createItem("card", {
          id: "c",
          title: "Cartão principal",
          fields: { currency: "BRL", limit: "1000", closing: "1", due: "10" },
        }),
        createItem("card_carry", {
          id: "carry",
          title: "Saldo anterior",
          date: today(),
          fields: {
            card: "c",
            currency: "BRL",
            amount: "300",
            paid: "100",
            dueDate: addDays(today(), 1),
          },
        }),
        ...Array.from({ length: 150 }, (_, n) =>
          createItem("note", {
            id: "q:" + n,
            title: "Pesquisa extensa " + n,
            notes: "Conteúdo de busca",
          }),
        ),
      ],
    });
    await nav("Visão geral");
    await page
      .locator(".command-home-facts")
      .getByText("Fatura · Cartão principal", { exact: true })
      .waitFor();
    assert(
      (await page.locator(".payment-agenda-row").first().innerText()).includes(
        "200,00",
      ),
    );
    await nav("Notas");
    await page
      .getByPlaceholder("Buscar nas notas")
      .fill("Formatação preservada");
    await page.getByRole("button", { name: /^Formatação preservada/ }).click();
    await page
      .getByRole("button", { name: "Edição visual", exact: true })
      .click();
    await page.getByLabel("Editor visual da nota").focus();
    await page.getByRole("button", { name: "Markdown", exact: true }).click();
    assert.equal((await call("item", "n")).notes, source);
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
    const formatted = await call("item", "n");
    assert(formatted.notes.includes("```typescript\nconst valor = 42;\n```"));
    assert(formatted.notes.includes("* Item original"));
    assert(formatted.notes.includes("| Nome | Valor |"));
    await page.getByRole("button", { name: "Markdown", exact: true }).click();
    await call("save", {
      item: { ...formatted, notes: "Versão remota limpa" },
      base: formatted,
    });
    await page.getByLabel("Conteúdo da nota").getAttribute("value");
    await page.waitForFunction(
      () =>
        (
          document.querySelector(
            '[aria-label="Conteúdo da nota"]',
          ) as HTMLTextAreaElement
        )?.value === "Versão remota limpa",
    );
    const base = await call("item", "n");
    await page.getByLabel("Conteúdo da nota").fill("Minha edição concorrente");
    await call("save", {
      item: { ...base, notes: "Outra versão salva" },
      base,
    });
    await page
      .getByRole("button", { name: "Comparar versões", exact: true })
      .waitFor();
    await page
      .getByRole("button", { name: "Comparar versões", exact: true })
      .click();
    await page
      .getByRole("dialog", { name: "Comparar versões da nota" })
      .getByText("Minha edição concorrente", { exact: true })
      .waitFor();
    await page
      .getByRole("button", {
        name: "Salvar minha edição como cópia",
        exact: true,
      })
      .click();
    assert.equal((await call("item", "n")).notes, "Outra versão salva");
    assert.equal(
      (await call("universalSearch", { query: "Minha edição concorrente" }))
        .total,
      1,
    );
    assert(!(await call("drafts")).some((d: any) => d.item.id === "n"));
    await page.getByPlaceholder("Buscar nas notas").fill("Pesquisa extensa");
    await page.getByText("150 notas", { exact: true }).waitFor();
    assert.equal(await page.locator(".note-preview").count(), 100);
    await page.getByRole("button", { name: "Mostrar mais notas" }).click();
    assert.equal(await page.locator(".note-preview").count(), 150);
    await nav("Centro financeiro");
    await page.getByRole("button", { name: "Relatórios", exact: true }).click();
    await page.getByLabel("Como analisar").selectOption("cash");
    await page
      .getByText("Saídas efetivamente pagas", { exact: true })
      .waitFor();
    await page.screenshot({
      path: ".qa/refinement-reports.png",
      fullPage: true,
    });
    const csvPath = resolve(profile, "roundtrip.csv");
    await app.evaluate(({ dialog }, path) => {
      dialog.showOpenDialog = (async () => ({
        canceled: false,
        filePaths: [path],
      })) as any;
    }, csvPath);
    const transactions = [
      createItem("card", { id: "c", title: "Cartão principal" }),
      createItem("expense", {
        title: "Compra CSV",
        date: today(),
        fields: {
          amount: "42.00",
          currency: "BRL",
          card: "c",
          status: "paid",
          financialVersion: "3",
        },
      }),
      createItem("expense", {
        title: "Pagamento CSV",
        date: today(),
        fields: {
          amount: "42.00",
          currency: "BRL",
          card: "c",
          status: "paid",
          settledDate: today(),
          paymentType: "card_payment",
          invoiceId: "invoice:c:" + addDays(today(), 1),
          financialVersion: "3",
        },
      }),
    ];
    for (const basis of ["recognized", "cash"] as const) {
      writeFileSync(
        csvPath,
        reportCSV(transactions, {
          from: today(),
          to: today(),
          currency: "BRL",
          basis,
        }),
      );
      const preview = await call("financeImportPreview");
      assert.equal(preview.newCount, 1);
      await call("financeImportConfirm", { token: preview.token });
      assert.equal((await call("financeImportPreview")).newCount, 0);
    }
    const imported = (await call("snapshot")).items.filter((i: any) =>
      i.id.startsWith("csv:"),
    );
    assert.equal(imported.length, 2);
    assert.equal(
      imported.find((i: any) => i.title === "Pagamento CSV").fields.invoiceId,
      "invoice:c:" + addDays(today(), 1),
    );
    assert(imported.every((i: any) => i.fields.financialVersion === "3"));
    assert.equal(
      reportRows(imported, {
        from: today(),
        to: today(),
        currency: "BRL",
        basis: "cash",
      }).expense,
      4200,
    );
    writeFileSync(
      csvPath,
      "type;date;title;amount;currency;card;paymentType;invoiceDue\nexpense;" +
        today() +
        ";Inválido;42;BRL;c;unknown;" +
        today(),
    );
    await assert.rejects(call("financeImportPreview"), /fatura/i);
    assert.equal(errors.length, 0, errors.join("\n"));
    console.log(
      "Refinement UI: invoice reminder, exact remaining amount, Markdown preserved without edit and across rich formatting, live clean-note refresh, conflict comparison/copy, all 150 search results and cash-report selector passed.",
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
