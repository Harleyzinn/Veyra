import "./catalog.mjs";
import { build } from "esbuild";
import { mkdir, copyFile, readFile, writeFile } from "node:fs/promises";
await mkdir("resources", { recursive: true });
await copyFile(
  "node_modules/sql.js/dist/sql-wasm.wasm",
  "resources/sql-wasm.wasm",
);
await mkdir("out/main", { recursive: true });
await mkdir("out/ui", { recursive: true });
await build({
  entryPoints: ["electron/index.ts"],
  bundle: true,
  platform: "node",
  target: "node24",
  format: "cjs",
  outfile: "out/main/index.cjs",
  external: ["electron", "sql.js"],
});
await build({
  entryPoints: ["electron/preload.ts"],
  bundle: true,
  platform: "node",
  format: "cjs",
  outfile: "out/main/preload.cjs",
  external: ["electron"],
});
await build({
  entryPoints: ["src/main.tsx"],
  bundle: true,
  platform: "browser",
  target: "chrome144",
  format: "esm",
  outfile: "out/ui/app.js",
  minify: true,
  define: { "process.env.NODE_ENV": '"production"' },
  external: ["./fonts/*"],
});
await build({
  entryPoints: ["src/google.ts"],
  bundle: true,
  platform: "browser",
  format: "esm",
  outfile: "out/ui/google.js",
  minify: true,
});
await copyFile("src/index.html", "out/ui/index.html");
await copyFile("src/google.html", "out/ui/google.html");
// The renderer never receives credentials or Firebase configuration. Google uses the system browser.
const fonts = [
  "manrope_400.ttf",
  "manrope_500.ttf",
  "manrope_600.ttf",
  "manrope_700.ttf",
];
await mkdir("out/ui/fonts", { recursive: true });
for (const font of fonts)
  await copyFile(
    "../../core/designsystem/src/main/res/font/" + font,
    "out/ui/fonts/" + font,
  );
await copyFile("../../docs/Manrope-OFL.txt","out/ui/fonts/OFL.txt");
const packageInfo = JSON.parse(await readFile("package.json", "utf8"));
await writeFile(
  "out/version.json",
  JSON.stringify({ version: packageInfo.version }),
);
