import {
  addDays,
  addMonths,
  daysBetween,
  validDate,
  Item,
} from "../desktop/shared/model";

export interface CalendarRules {
  saturday: boolean;
  national: boolean;
  holidays: string[];
  state?: string;
  city?: string;
}
export function nationalHolidays(year: number) {
  // National fixed holidays only. Local holidays and optional observances must be supplied explicitly.
  return [
    "01-01",
    "04-21",
    "05-01",
    "09-07",
    "10-12",
    "11-02",
    "11-15",
    ...(year >= 2024 ? ["11-20"] : []),
    "12-25",
  ].map((d) => `${year}-${d}`);
}
export function isBusinessDay(date: string, rules: CalendarRules) {
  if (!validDate(date)) throw Error("Data inválida.");
  const day = new Date(date + "T12:00:00Z").getUTCDay();
  return (
    day !== 0 &&
    (rules.saturday || day !== 6) &&
    !rules.holidays.includes(date) &&
    !(
      rules.national &&
      nationalHolidays(Number(date.slice(0, 4))).includes(date)
    )
  );
}
export function businessDay(month: string, nth: number, rules: CalendarRules) {
  if (!Number.isInteger(nth) || nth < 1 || nth > 23)
    throw Error("Escolha do 1º ao 23º dia útil.");
  let date = month + "-01",
    found = 0;
  while (date.startsWith(month)) {
    if (isBusinessDay(date, rules) && ++found === nth) return date;
    date = addDays(date, 1);
  }
  throw Error("O mês não tem a quantidade de dias úteis escolhida.");
}
export function scheduleDates(
  rule: Item,
  from: string,
  through: string,
  rules: CalendarRules,
): string[] {
  const start = rule.fields.startDate || rule.date;
  const end = [
    through,
    rule.fields.endDate || through,
    rule.fields.pausedAt ? addDays(rule.fields.pausedAt, -1) : through,
  ].sort()[0];
  if (rule.deletedAt || (rule.fields.paused === "yes" && !rule.fields.pausedAt))
    return [];
  if (
    !validDate(start) ||
    !validDate(from) ||
    !validDate(end) ||
    daysBetween(start, end) > 36600
  )
    throw Error("Confira as datas da recorrência (limite de 100 anos).");
  const frequency = rule.fields.frequency || "monthly";
  if (
    !["once", "weekly", "fortnightly", "business", "monthly", "twice"].includes(
      frequency,
    )
  )
    throw Error("Frequência inválida.");
  for (const key of ["day1", "day2"])
    if (
      rule.fields[key] &&
      (!Number.isInteger(Number(rule.fields[key])) ||
        Number(rule.fields[key]) < 1 ||
        Number(rule.fields[key]) > 31)
    )
      throw Error("Escolha um dia entre 1 e 31.");
  const out: string[] = [];
  const adjust = (date: string) => {
    if (!["before", "after"].includes(rule.fields.adjust)) return date;
    for (let n = 0; !isBusinessDay(date, rules); n++) {
      if (n > 370)
        throw Error("Calendário sem dias úteis; revise os feriados.");
      date = addDays(date, rule.fields.adjust === "before" ? -1 : 1);
    }
    return date;
  };
  const push = (raw: string) => {
    const d = frequency === "business" ? raw : adjust(raw);
    const paused = (rule.fields.pauseWindows || "")
      .split(";")
      .filter(Boolean)
      .some((v) => {
        const [a, b] = v.split("/");
        return d >= a && d <= b;
      });
    if (
      raw >= start &&
      raw <= end &&
      d >= start &&
      d >= from &&
      d <= end &&
      !paused
    )
      out.push(d);
  };
  if (frequency === "once") push(start);
  else if (["weekly", "fortnightly"].includes(frequency)) {
    for (
      let d = start;
      d <= end;
      d = addDays(d, frequency === "weekly" ? 7 : 14)
    )
      push(d);
  } else {
    for (
      let month = start.slice(0, 7) + "-01";
      month <= end;
      month = addMonths(month, 1, 1)
    ) {
      if (frequency === "business")
        push(
          businessDay(
            month.slice(0, 7),
            Number(rule.fields.businessNth || 5),
            rules,
          ),
        );
      else {
        push(addMonths(month, 0, Number(rule.fields.day1 || start.slice(8))));
        if (frequency === "twice")
          push(addMonths(month, 0, Number(rule.fields.day2 || 20)));
      }
    }
  }
  return [...new Set(out)].sort();
}
