import { test } from "node:test";
import assert from "node:assert/strict";
import { habitStats, repeatedTask } from "../shared/productivity";
import { createItem } from "../shared/model";
test("business-day habit streak skips weekends and excludes deleted check-ins", () => {
  const habit = createItem("habit", {
    id: "h",
    fields: { frequency: "Dias úteis" },
  });
  const check = (date: string, deletedAt = 0) =>
    createItem("checkin", { id: date, parentId: "h", date, deletedAt });
  const stats = habitStats(
    [habit, check("2026-10-02"), check("2026-10-05"), check("2026-10-06", 1)],
    "h",
    "2026-10-05",
  );
  assert.equal(stats.current, 2);
  assert.equal(stats.longest, 2);
});
test("monthly task repetition preserves fields and has a deterministic ID without duplicating completion", () => {
  const task = createItem("task", {
    id: "t",
    date: "2026-01-31",
    done: true,
    fields: { recurrence: "Mensal", priority: "Alta" },
  });
  const next = repeatedTask(task, { ...task, done: false })!;
  assert.equal(next.date, "2026-02-28");
  assert.equal(next.fields.priority, "Alta");
  assert.equal(next.done, false);
  assert.equal(repeatedTask(task, task), null);
});
