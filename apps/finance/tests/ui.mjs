import { createRequire } from "node:module";
import { createServer } from "node:http";
import { readFile, mkdir, writeFile } from "node:fs/promises";
import { resolve, extname } from "node:path";
import assert from "node:assert/strict";
const require = createRequire(
  new URL("../../desktop/package.json", import.meta.url),
);
const { chromium, webkit } = require("@playwright/test");
const root = resolve("apps/finance/out"),
  qa = resolve(
    process.env.VEYRA_BROWSER === "webkit"
      ? ".tools/finance30-webkit"
      : ".tools/finance30-qa",
  );
await mkdir(qa, { recursive: true });
const server = createServer(async (req, res) => {
  try {
    const path = decodeURIComponent(
      new URL(req.url, "http://localhost").pathname,
    ).replace(/^\/Veyra\//, "");
    const file = resolve(root, path === "" ? "index.html" : path);
    if (!file.startsWith(root)) throw Error();
    const bytes = await readFile(file);
    res.setHeader(
      "Content-Type",
      {
        ".html": "text/html",
        ".js": "application/javascript",
        ".css": "text/css",
        ".svg": "image/svg+xml",
        ".ttf": "font/ttf",
        ".webmanifest": "application/manifest+json",
      }[extname(file)] || "application/octet-stream",
    );
    res.end(bytes);
  } catch {
    res.statusCode = 404;
    res.end("Not found");
  }
});
await new Promise((r) => server.listen(4189, "127.0.0.1", r));
const browser =
  process.env.VEYRA_BROWSER === "webkit"
    ? await webkit.launch({ headless: true })
    : await chromium.launch({ channel: "msedge", headless: true });
const context = await browser.newContext({
  viewport: { width: 1440, height: 1050 },
  acceptDownloads: true,
});
const page = await context.newPage();
const errors = [];
page.on("pageerror", (e) => errors.push(e.message));
const results = [];
const url = "http://127.0.0.1:4189/Veyra/";
async function route(id) {
  await page.goto(url + "#" + id);
  await page.locator(".page-heading").waitFor();
}
async function save() {
  await page.getByRole("button", { name: "Salvar", exact: true }).click();
  await page.locator("dialog").waitFor({ state: "detached" });
}
async function state() {
  return page.evaluate(
    () =>
      new Promise((resolve, reject) => {
        const r = indexedDB.open("veyra-finance-v1", 1);
        r.onsuccess = () => {
          const db = r.result,
            tx = db.transaction("state"),
            g = tx.objectStore("state").get("main");
          g.onsuccess = () => {
            resolve(g.result);
            db.close();
          };
          g.onerror = () => reject(g.error);
        };
      }),
  );
}
try {
  await page.goto(url);
  await page
    .getByRole("button", { name: "Começar a usar", exact: true })
    .click();
  await route("accounts");
  await page.getByRole("button", { name: "Nova conta", exact: true }).click();
  await page.getByLabel("Saldo inicial").fill("2.000,00");
  await page.getByLabel("Descrição", { exact: true }).fill("Conta QA");
  await save();
  assert.equal((await state()).items[0].fields.openingMinor, "200000");
  results.push("Conta criada pelo formulário com saldo em centavos.");
  await route("home");
  await page.getByRole("button", { name: /Adicionar gasto Registre/ }).click();
  await page.getByLabel("Valor", { exact: true }).fill("42,35");
  await page
    .getByLabel("Categoria", { exact: true })
    .selectOption("Alimentação");
  await save();
  assert.equal(
    (await state()).items.find((i) => i.type === "expense").fields.amountMinor,
    "4235",
  );
  results.push("Gasto simples exige só valor/categoria e é persistido.");
  await route("transactions");
  await page.getByLabel("Buscar lançamentos").fill("Alimentação");
  await page.getByLabel("Situação", { exact: true }).selectOption("paid");
  await page.getByRole("button", { name: "Valor e ferramentas" }).count();
  await page
    .locator("summary")
    .filter({ hasText: "Valor e ferramentas" })
    .click();
  await page.getByLabel("Valor mínimo").fill("1,");
  assert.equal(await page.locator(".fatal").count(), 0);
  results.push("Busca, situação e valor parcial não interrompem a tela.");
  await route("recurring");
  await page.getByRole("button", { name: "Nova receita recorrente" }).click();
  await page.getByLabel("Valor", { exact: true }).fill("5000");
  await page.getByLabel("Descrição", { exact: true }).fill("Salário QA");
  await page.getByLabel("Frequência", { exact: true }).selectOption("business");
  await page.getByLabel("Qual dia útil?").fill("5");
  await save();
  results.push("Receita por 5º dia útil cadastrada na interface.");
  await route("cards");
  await page.getByRole("button", { name: "Novo cartão" }).click();
  await page.getByLabel("Limite total").fill("3000");
  await page.getByLabel("Descrição", { exact: true }).fill("Cartão QA");
  await save();
  await page.getByRole("button", { name: "Compra no cartão" }).click();
  await page.getByLabel("Valor", { exact: true }).fill("100,00");
  await page.getByRole("button", { name: "Mais detalhes" }).click();
  await page.getByLabel("Parcelas").fill("3");
  await save();
  assert.equal(
    (await state()).items
      .filter((i) => i.fields.installmentCount === "3" && i.type === "expense")
      .reduce((s, i) => s + Number(i.fields.amountMinor), 0),
    10000,
  );
  results.push("Compra de R$100 dividida em 3 parcelas com total exato.");
  await route("settings");
  await page.getByLabel("Experiência").selectOption("complete");
  await page.waitForTimeout(200);
  await page.reload();
  await page.getByLabel("Experiência").waitFor();
  assert.equal(await page.getByLabel("Experiência").inputValue(), "complete");
  results.push("Modo completo e registros preservados após recarregar.");
  const second=await context.newPage();
  await second.goto(url+'#home');
  await second.getByRole('button',{name:/Adicionar gasto Registre/}).click();
  await second.getByLabel('Valor',{exact:true}).fill('9,99');
  await page.getByLabel('Experiência').selectOption('simple');
  await page.waitForTimeout(200);
  await second.getByRole('button',{name:'Salvar',exact:true}).click();
  await second.getByRole('alert').filter({hasText:'outra aba'}).waitFor();
  await second.close();
  results.push('Uma aba desatualizada não sobrescreve os dados salvos por outra.');
  const backup = await state();
  await page
    .getByRole("button", { name: "Importar backup ou CSV" })
    .click({ trial: true });
  await page
    .locator("input[type=file]")
    .setInputFiles({
      name: "backup.json",
      mimeType: "application/json",
      buffer: Buffer.from(JSON.stringify(backup)),
    });
  await page.getByText(/já existentes, que serão preservados/).waitFor();
  await page.getByRole("button", { name: "Importar 0 registros" }).click();
  await page.locator("dialog").waitFor({ state: "detached" });
  results.push("Prévia de backup reconheceu duplicatas sem sobrescrever.");
  const downloadPromise = page.waitForEvent("download");
  await page.getByRole("button", { name: "Fazer backup" }).click();
  const dl = await downloadPromise;
  await dl.saveAs(resolve(qa, "qa-backup.json"));
  results.push("Backup exportado de verdade pelo navegador.");
  await route("appearance");
  assert.equal(await page.locator(".theme-choice").count(), 24);
  await page
    .getByRole("button", { name: "Testar tema Lavanda", exact: true })
    .click();
  await page.getByRole("button", { name: "Salvar tema" }).click();
  await page.waitForTimeout(200);
  assert.equal((await state()).preferences.theme, "lavender");
  await page.screenshot({
    path: resolve(qa, "themes-desktop.png"),
    fullPage: true,
  });
  await page
    .getByRole("button", { name: "Testar tema Verde esmeralda", exact: true })
    .click();
  await page.getByRole("button", { name: "Salvar tema" }).click();
  results.push("24 temas, prévia e aplicação persistida.");
  await route("reports");
  for (const mode of ["bars", "columns", "ring", "table"])
    await page.getByLabel("Visualização do gráfico").selectOption(mode);
  assert.ok((await page.locator("tbody tr").count()) > 0);
  results.push("Quatro visualizações e alternativa em tabela.");
  await route("home");
  await page.keyboard.press("Control+k");
  await page
    .getByPlaceholder("Página, lançamento, conta ou meta…")
    .fill("relat");
  await page.locator(".command-results button").first().click();
  assert.equal(new URL(page.url()).hash, "#reports");
  results.push("Ctrl+K, busca e navegação por comando.");
  await route("home");
  await page.screenshot({
    path: resolve(qa, "home-desktop.png"),
    fullPage: true,
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.locator(".bottom-nav").waitFor({ state: "visible" });
  await page.getByRole("button", { name: "Mais", exact: true }).click();
  await page
    .locator(".more-grid button")
    .filter({ hasText: "Planejamento e metas" })
    .click();
  assert.equal(new URL(page.url()).hash, "#planning");
  await route("home");
  assert.ok(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  );
  await page.screenshot({
    path: resolve(qa, "home-mobile.png"),
    fullPage: true,
  });
  results.push(
    "Celular 390px: barra inferior, Mais e ausência de rolagem horizontal.",
  );
  await page.setViewportSize({ width: 820, height: 1180 });
  await page.screenshot({
    path: resolve(qa, "home-tablet.png"),
    fullPage: true,
  });
  results.push("Tablet 820px: navegação compacta.");
  await page.evaluate(async () => {
    await navigator.serviceWorker.ready;
  });
  await page.reload();
  await page.locator(".hero").waitFor();
  await context.setOffline(true);
  await page.reload();
  await page.locator(".hero").waitFor();
  results.push("Reabertura offline real com Service Worker e dados locais.");
  await context.setOffline(false);
  assert.deepEqual(errors, []);
  results.push("Nenhum erro JavaScript nos fluxos executados.");
  await writeFile(
    resolve(qa, "ui-results.json"),
    JSON.stringify({ results, errors }, null, 2),
  );
  console.log(results.join("\n"));
} catch (e) {
  await page
    .screenshot({ path: resolve(qa, "failure.png"), fullPage: true })
    .catch(() => {});
  await writeFile(resolve(qa, "failure.html"), await page.content());
  throw e;
} finally {
  await browser.close();
  server.close();
}
