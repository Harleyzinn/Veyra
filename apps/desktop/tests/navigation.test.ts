import test from "node:test";
import assert from "node:assert/strict";
import { catalog } from "../shared/catalog";
import {
  destinations,
  defaultNavigation,
  navigationPreferences,
  orderedDestinations,
  visit,
  frequent,
  financeNavigation,
} from "../shared/navigation";
import { desktopPatch } from "../shared/settings";
test("all existing pages and catalog types remain discoverable with unique routes", () => {
  const ids = destinations.map((d) => d.id);
  assert.equal(new Set(ids).size, ids.length);
  for (const id of [
    "home",
    "day",
    "inbox",
    "tasks",
    "calendar",
    "planner",
    "projects",
    "goals",
    "notes",
    "habits",
    "focus",
    "finance",
    "review",
    "favorites",
    "recent",
    "templates",
    "automations",
    "notifications",
    "modules",
    "settings",
    "backup",
    "privacy",
    "sync",
    "diagnostics",
  ])
    assert.ok(ids.includes(id), id);
  for (const s of catalog) assert.ok(ids.includes("modules:" + s.type), s.type);
});
test("personalization sanitizes stale routes without destroying catalog or mandatory recovery paths", () => {
  const prefs = navigationPreferences({
    home: "removed",
    order: ["notes", "notes", "bad"],
    hidden: ["notes", "navigation", "settings", "modules"],
    favorites: ["notes", "bad"],
    collapsed: ["Sistema", "bad"],
    compact: true,
  });
  assert.equal(prefs.home, "home");
  assert.deepEqual(prefs.hidden, ["notes"]);
  assert.deepEqual(prefs.favorites, ["notes"]);
  assert.equal(orderedDestinations(prefs)[0].id, "notes");
  assert.equal(orderedDestinations(prefs).length, destinations.length);
  assert.deepEqual(desktopPatch({ navigation: prefs }).navigation, prefs);
});
test("history is bounded, counts valid routes and rejects malformed IPC input", () => {
  let history = {
    recent: [] as string[],
    counts: {} as Record<string, number>,
  };
  for (const d of destinations.slice(0, 20)) history = visit(history, d.id);
  history = visit(history, "notes");
  history = visit(history, "notes");
  assert.equal(history.recent.length, 8);
  assert.equal(history.recent[0], "notes");
  assert.equal(frequent(history)[0], "notes");
  assert.deepEqual(visit(history, "invalid"), history);
  assert.throws(() =>
    desktopPatch({
      navigationHistory: { recent: [], counts: { notes: Infinity } },
    }),
  );
  assert.throws(() =>
    desktopPatch({
      navigationHistory: { recent: ["not-a-route"], counts: {} },
    }),
  );
});
test("finance families preserve all ten old tabs exactly once", () => {
  const tabs = Object.values(financeNavigation).flat();
  assert.equal(new Set(tabs).size, 10);
  assert.deepEqual(
    new Set(tabs),
    new Set([
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
    ]),
  );
  assert.equal(defaultNavigation.home, "home");
});
