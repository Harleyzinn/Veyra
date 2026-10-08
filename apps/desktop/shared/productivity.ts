import { Item, addDays, addMonths, createItem, today } from "./model";
export function habitStats(
  items: Item[],
  habitId: string,
  reference = today(),
) {
  const habit = items.find((i) => i.id === habitId);
  const frequency = habit?.fields.frequency || "Diária";
  const scheduled = (day: string) => {
    const weekday = new Date(day + "T12:00:00").getDay();
    return frequency === "Dias úteis"
      ? weekday > 0 && weekday < 6
      : frequency === "Fim de semana"
        ? weekday === 0 || weekday === 6
        : true;
  };
  const dates = [
    ...new Set(
      items
        .filter(
          (i) =>
            !i.deletedAt &&
            i.type === "checkin" &&
            i.parentId === habitId &&
            i.date <= reference,
        )
        .map((i) => i.date),
    ),
  ].sort();
  const previous = (day: string) => {
    let cursor = addDays(day, -1);
    while (!scheduled(cursor)) cursor = addDays(cursor, -1);
    return cursor;
  };
  let cursor = reference;
  if (!scheduled(cursor) || !dates.includes(cursor)) cursor = previous(cursor);
  let current = 0;
  while (dates.includes(cursor)) {
    current++;
    cursor = previous(cursor);
  }
  let longest = 0,
    run = 0,
    last = "";
  for (const day of dates.filter(scheduled)) {
    run = last && previous(day) === last ? run + 1 : 1;
    longest = Math.max(longest, run);
    last = day;
  }
  const expected = Array.from({ length: 30 }, (_, n) =>
    addDays(reference, -n),
  ).filter(scheduled);
  return {
    dates,
    current,
    longest,
    rate: expected.length
      ? Math.round(
          (expected.filter((d) => dates.includes(d)).length / expected.length) *
            100,
        )
      : 0,
    scheduledToday: scheduled(reference),
  };
}
export function repeatedTask(item: Item, before: Item | null): Item | null {
  if (item.type !== "task" || !item.done || before?.done || !item.date)
    return null;
  const frequency = item.fields.recurrence;
  let date = "";
  if (frequency === "Diária") date = addDays(item.date, 1);
  else if (frequency === "Semanal") date = addDays(item.date, 7);
  else if (frequency === "Mensal") date = addMonths(item.date, 1);
  else return null;
  const source = item.fields.repeatRoot || item.id;
  return createItem("task", {
    ...item,
    id: `task-repeat:${source}:${date}`,
    date,
    done: false,
    createdAt: Date.now(),
    fields: { ...item.fields, status: "A fazer", repeatRoot: source },
  });
}
