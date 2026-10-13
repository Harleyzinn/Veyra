import { destinations } from "./navigation";
// Preserve callers while using the route registry as the sole discovery source.
export const modules = destinations;
export const quickTypes = [
  ["task", "Tarefa"],
  ["expense", "Gasto"],
  ["income", "Entrada"],
  ["note", "Nota"],
  ["event", "Evento"],
  ["habit", "Hábito"],
  ["goal", "Meta"],
  ["project", "Projeto"],
  ["inbox", "Guardar na Inbox"],
] as const;
