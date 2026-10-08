import { readdir, readFile, writeFile } from "node:fs/promises";
async function files(dir) {
  const out = [];
  for (const f of await readdir(dir, { withFileTypes: true })) {
    if (["build", "node_modules"].includes(f.name)) continue;
    const path = dir + "/" + f.name;
    if (f.isDirectory()) out.push(...(await files(path)));
    else if (f.name.endsWith(".kt")) out.push(path);
  }
  return out;
}
function block(text, start) {
  let depth = 0,
    quoted = false,
    escape = false;
  for (let i = start; i < text.length; i++) {
    const c = text[i];
    if (escape) {
      escape = false;
      continue;
    }
    if (c === "\\" && quoted) {
      escape = true;
      continue;
    }
    if (c === '"') {
      quoted = !quoted;
      continue;
    }
    if (!quoted) {
      if (c === "(") depth++;
      if (c === ")" && --depth === 0) return text.slice(start, i + 1);
    }
  }
  return "";
}
function fields(text) {
  const result = [];
  for (const m of text.matchAll(/Field\(\s*"([^"]+)"\s*,\s*"([^"]+)"/g)) {
    const source = block(text, m.index + 5);
    const kind = /FieldKind\.(\w+)/.exec(source)?.[1] || "TEXT";
    const choices =
      /listOf\(([^)]*)\)/
        .exec(source)?.[1]
        .match(/"([^"]+)"/g)
        ?.map((v) => JSON.parse(v)) || [];
    result.push({
      key: m[1],
      label: m[2],
      kind,
      choices,
      required: /required\s*=\s*true/.test(source),
    });
  }
  return result;
}
const specs = new Map();
for (const path of [
  ...(await files("../../feature")),
  ...(await files("../../app/src/main")),
]) {
  const text = await readFile(path, "utf8");
  for (const m of text.matchAll(
    /ItemSpec\(\s*"([a-z_]+)"\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"/g,
  )) {
    const source = block(text, m.index + 8);
    let members = fields(source);
    if (/\btransaction\s*\+|\btransaction\s*[,)]/.test(source)) {
      const pre = text.slice(0, text.indexOf("val specs"));
      members = [...fields(pre), ...members];
    }
    specs.set(m[1], {
      type: m[1],
      label: m[2],
      group: m[3],
      fields: [...new Map(members.map((f) => [f.key, f])).values()],
      checkable: /,\s*true\s*\)$/.test(source),
    });
  }
}
for (const [type, label, group] of [
  ["city", "Cidades", "Clima"],
  ["checkin", "Registros de hábitos", "Rotina"],
  ["focus_session", "Sessões de foco", "Estudos"],
])
  if (!specs.has(type))
    specs.set(type, { type, label, group, fields: [], checkable: false });
await writeFile(
  "shared/catalog.ts",
  "// Generated from the existing Kotlin catalogs. IDs and field names remain compatible.\nexport const catalog = " +
    JSON.stringify([...specs.values()], null, 2) +
    " as const;\n",
);
console.log("Catálogo compatível: " + specs.size + " módulos.");
