import { validDate } from "./model";
import { widgetCatalog } from "./platform";
export const desktopKeys = new Set([
  "weatherEnabled",
  "welcomeSeen",
  "layout",
  "theme",
  "accent",
  "density",
  "scale",
  "keepTray",
  "startMinimized",
  "openDay",
  "clipboard",
  "notifications",
  "miniWidgets",
  "kanbanColumns",
  "lastPage",
  "reducedEffects",
  "performanceMode",
  "doNotDisturb",
  "quietFocus",
  "morningBrief",
  "dailyReview",
  "weeklyReview",
  "smartHints",
  "dockSide",
  "animations",
  "onboardingProfile",
  "plannerDate",
]);
const booleans = new Set([
  "weatherEnabled",
  "welcomeSeen",
  "keepTray",
  "startMinimized",
  "openDay",
  "clipboard",
  "notifications",
  "reducedEffects",
  "doNotDisturb",
  "quietFocus",
  "morningBrief",
  "dailyReview",
  "weeklyReview",
  "smartHints",
  "animations",
]);
export function desktopPatch(input: any): Record<string, any> {
  if (
    !input ||
    typeof input !== "object" ||
    Array.isArray(input) ||
    JSON.stringify(input).length > 100000
  )
    throw Error("Configuração inválida.");
  const out: Record<string, any> = {};
  for (const [key, value] of Object.entries(input)) {
    if (!desktopKeys.has(key)) throw Error("Configuração não autorizada.");
    if (booleans.has(key)) {
      if (typeof value !== "boolean") throw Error("Use uma opção válida.");
      out[key] = value;
      continue;
    }
    if (key === "layout") {
      if (!Array.isArray(value) || value.length > 25)
        throw Error("Layout inválido.");
      const seen = new Set<string>();
      out[key] = value.map((w) => {
        if (
          !w ||
          !widgetCatalog.some(([id]) => id === w.id) ||
          seen.has(w.id) ||
          !["normal", "wide", "full"].includes(w.size)
        )
          throw Error("Widget ou tamanho inválido.");
        seen.add(w.id);
        return {
          id: w.id,
          size: w.size,
          ...(w.height === undefined
            ? {}
            : {
                height: Math.min(800, Math.max(180, Number(w.height) || 180)),
              }),
        };
      });
      continue;
    }
    if (key === "miniWidgets") {
      if (
        !Array.isArray(value) ||
        value.length > 20 ||
        value.some((v) => !widgetCatalog.some(([id]) => id === v))
      )
        throw Error("Módulos inválidos.");
      out[key] = [...new Set(value)];
      continue;
    }
    if (key === "kanbanColumns") {
      if (
        !Array.isArray(value) ||
        value.length < 1 ||
        value.length > 10 ||
        value.some((v) => typeof v !== "string" || !v.trim() || v.length > 40)
      )
        throw Error("Colunas inválidas.");
      out[key] = [...new Set(value)];
      continue;
    }
    if (key === "plannerDate") {
      if (typeof value !== "string" || !validDate(value))
        throw Error("Data inválida.");
      out[key] = value;
      continue;
    }
    if (key === "scale") {
      const n = Number(value);
      if (!Number.isFinite(n) || n < 80 || n > 150)
        throw Error("Escala deve ficar entre 80% e 150%.");
      out[key] = n;
      continue;
    }
    const options: Record<string, string[]> = {
      theme: ["dark", "light", "system", "Claro", "Escuro", "Sistema"],
      accent: ["violet", "mint", "blue", "amber"],
      density: ["comfortable", "compact"],
      performanceMode: ["auto", "quality", "economy"],
      dockSide: ["left", "right"],
      onboardingProfile: [
        "all",
        "finance",
        "productivity",
        "studies",
        "organization",
      ],
    };
    if (
      typeof value !== "string" ||
      value.length > 100 ||
      (options[key] && !options[key].includes(value))
    )
      throw Error("Opção inválida.");
    out[key] = value;
  }
  return out;
}
