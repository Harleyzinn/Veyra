import { _electron as electron } from "@playwright/test";
import assert from "node:assert/strict";
import { resolve } from "node:path";
import { mkdirSync, writeFileSync } from "node:fs";
import {
  destinations,
  defaultNavigation,
  financeNavigation,
} from "../shared/navigation";
import { createItem } from "../shared/model";
async function run() {
  const profile = resolve(".qa/navigation-" + Date.now());
  mkdirSync(profile, { recursive: true });
  const env = { ...process.env, VEYRA_QA: "1", VEYRA_QA_DATA: profile };
  delete (env as any).ELECTRON_RUN_AS_NODE;
  const app = await electron.launch({
    executablePath: resolve("node_modules/electron/dist/electron.exe"),
    args: ["."],
    env: env as Record<string, string>,
  });
  const summary: string[] = [];
  try {
    const page = await app.firstWindow();
    const errors: string[] = [];
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
    const record = createItem("note", {
      id: "nav-preserved",
      title: "Nota preservada",
      notes: "Texto completo\n\n**Markdown**",
      favorite: true,
    });
    await call("save", { items: [record] });
    await call("desktop", {
      navigation: { ...defaultNavigation, collapsed: [] },
    });
    for (const d of destinations) {
      await call("navigate", d.id);
      await page.locator(".breadcrumb").filter({ hasText: d.label }).waitFor();
      await page.locator(".content .module-skeleton").waitFor({state:"detached"});
      await page.locator(".content").getByRole("heading").first().waitFor();
      assert.equal(await page.getByText("Não conseguimos abrir este módulo.",{exact:true}).count(), 0);
    }
    summary.push(
      `${destinations.length} destinos abertos pela API de navegação real, incluindo todos os tipos do catálogo.`,
    );
    await call("navigate", "notes");
    await page
      .getByRole("button", { name: "Fixar página", exact: true })
      .click();
    await page.waitForTimeout(180);
    assert.ok(
      (await call("snapshot")).desktop.navigation.favorites.includes("notes"),
    );
    const prefs = {
      ...defaultNavigation,
      home: "notes",
      favorites: ["notes"],
      hidden: ["modules:document"],
      order: ["notes", "tasks"],
      collapsed: ["Vida pessoal"],
    };
    await call("desktop", { navigation: prefs });
    await page.waitForTimeout(180);
    await page.keyboard.press("Control+k");
    await page
      .getByRole("textbox", { name: "Pesquisar Veyra", exact: true })
      .fill("modules:document");
    await page.keyboard.press("Enter");
    await page
      .locator(".breadcrumb")
      .filter({
        hasText: destinations.find((d) => d.id === "modules:document")!.label,
      })
      .waitFor();
    summary.push("Ctrl+K e Enter abrem módulo cujo atalho foi ocultado.");
    await page
      .getByRole("button", { name: "Recolher menu", exact: true })
      .click();
    await page.waitForTimeout(180);
    assert.ok(await page.locator(".app.nav-compact").count());
    const menu = page
      .locator(".sidebar")
      .getByRole("button", { name: "Notas", exact: true })
      .first();
    await menu.focus();
    await page.keyboard.press("Enter");
    await page.locator(".breadcrumb").filter({ hasText: "Notas" }).waitFor();
    summary.push(
      "Modo compacto, favorito e navegação por Tab/foco + Enter funcionam.",
    );
    await call("desktop", { theme: "light" });
    await page.waitForTimeout(200);
    await page.screenshot({
      path: resolve("../../docs/screenshots/navigation-desktop-light.png"),
    });
    await page.setViewportSize({ width: 900, height: 900 });
    await call("desktop", { theme: "dark" });
    await page.emulateMedia({ reducedMotion: "reduce" });
    await page.waitForTimeout(200);
    await page.screenshot({
      path: resolve("../../docs/screenshots/navigation-desktop-tablet.png"),
    });
    await page.setViewportSize({ width: 1440, height: 960 });
    await page
      .getByRole("button", { name: "Expandir menu", exact: true })
      .click();
    await page.waitForTimeout(180);
    await call("navigate", "finance");
    await page.locator(".finance-navigation").waitFor();
    for (const tab of [
      "Visão geral",
      "Movimentações",
      "Contas e cartões",
      "Recorrências",
      "Planejamento",
      "Simulador",
      "Conferência",
      "Calendário financeiro",
      "Assinaturas",
      "Relatórios",
    ]) {
      const button = page
        .locator(".finance-navigation")
        .getByRole("button", { name: tab, exact: true });
      if (!(await button.isVisible())) {
        const group = Object.entries(financeNavigation).find(([, tabs]) =>
          tabs.includes(tab),
        )![0];
        await page
          .locator(".finance-navigation summary")
          .filter({ hasText: group })
          .click();
      }
      await button.click();
      assert.equal(await button.getAttribute("aria-current"), "page");
    }
    await page.screenshot({
      path: resolve("../../docs/screenshots/navigation-desktop-dark.png"),
    });
    summary.push(
      "Dez seções financeiras acessíveis, temas claro/escuro e viewport tablet verificados.",
    );
    const got = await call("item", record.id);
    assert.equal(got.notes, record.notes);
    assert.equal(got.favorite, true);
    await call("navigate", "navigation");
    await page.getByRole("textbox",{name:"Encontrar página",exact:true}).fill("Notas");
    await page.getByRole("button",{name:"Descer Notas",exact:true}).click();
    await page.waitForTimeout(200);
    const reordered=(await call("snapshot")).desktop.navigation.order;
    assert.ok(reordered.indexOf("tasks")<reordered.indexOf("notes"));
    assert.equal(JSON.parse((await call("snapshot")).preferences.desktopNavigation).order[0],"tasks");
    await page
      .getByRole("button", {
        name: "Restaurar organização padrão",
        exact: true,
      })
      .click();
    await page.waitForTimeout(200);
    assert.deepEqual(
      (await call("snapshot")).desktop.navigation,
      defaultNavigation,
    );
    await call("desktop", { navigation: prefs });
    await page.waitForTimeout(200);
    await page.reload();
    await page.waitForSelector(".sidebar");
    await page.locator(".breadcrumb").filter({ hasText: "Notas" }).waitFor();
    assert.equal((await call("snapshot")).desktop.navigation.home, "notes");
    summary.push(
      "Página inicial, ordem e favoritos persistem entre recargas; restauração padrão e registro original conferidos.",
    );
    assert.deepEqual(errors, []);
    writeFileSync(
      resolve(".qa/navigation-results.json"),
      JSON.stringify({ passed: true, summary }, null, 2),
    );
    console.log(JSON.stringify({ passed: true, summary }, null, 2));
  } finally {
    await app.close();
  }
}
run().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});
