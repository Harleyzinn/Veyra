import { createRequire } from "node:module";
import {
  mkdir,
  readFile,
  writeFile,
  copyFile,
  readdir,
} from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { resolve, dirname } from "node:path";
const root = dirname(fileURLToPath(import.meta.url));
const require = createRequire(resolve(root, "../desktop/package.json"));
const { build } = require("esbuild");
const out = resolve(root, "out");
await mkdir(out, { recursive: true });
await build({
  entryPoints: [resolve(root, "main.tsx")],
  bundle: true,
  platform: "browser",
  target: ["safari16", "chrome110"],
  format: "esm",
  minify: true,
  outfile: resolve(out, "app.js"),
  nodePaths: [resolve(root, "../desktop/node_modules")],
  external: ["./fonts/*"],
  define: { "process.env.NODE_ENV": '"production"' },
});
for (const name of [
  "index.html",
  "manifest.webmanifest",
  "icon.svg",
  "icon-192.png",
  "icon-512.png",
])
  await copyFile(resolve(root, name), resolve(out, name));
await mkdir(resolve(out, "fonts"), { recursive: true });
for (const weight of [400, 600, 700])
  await copyFile(
    resolve(
      root,
      `../../core/designsystem/src/main/res/font/manrope_${weight}.ttf`,
    ),
    resolve(out, `fonts/manrope_${weight}.ttf`),
  );
await copyFile(
  resolve(root, "../../docs/Manrope-OFL.txt"),
  resolve(out, "fonts/OFL.txt"),
);
// Use a content-derived cache ID so a new deployment cannot reuse stale code.
const { createHash } = await import("node:crypto");
const digest = createHash("sha256");
for (const file of ["app.js", "app.css", "index.html"])
  digest.update(await readFile(resolve(out, file)));
const assets = [
  "./",
  "./index.html",
  "./app.js",
  "./app.css",
  "./manifest.webmanifest",
  "./icon.svg",
  "./icon-192.png",
  "./icon-512.png",
  "./fonts/manrope_400.ttf",
  "./fonts/manrope_600.ttf",
  "./fonts/manrope_700.ttf",
];
await writeFile(
  resolve(out, "sw.js"),
  `const CACHE='veyra-finance-${digest.digest("hex").slice(0, 16)}',ASSETS=${JSON.stringify(assets)};
self.addEventListener('install',e=>e.waitUntil(caches.open(CACHE).then(c=>c.addAll(ASSETS))));
self.addEventListener('activate',e=>e.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(k=>k.startsWith('veyra-finance-')&&k!==CACHE).map(k=>caches.delete(k)))).then(()=>self.clients.claim())));
self.addEventListener('fetch',e=>{if(e.request.method!=='GET'||new URL(e.request.url).origin!==location.origin)return;e.respondWith(caches.match(e.request).then(hit=>hit||fetch(e.request)));});`,
);
// The Android app ships the exact same local UI, with no remote scripts.
const android = resolve(root, "../../app/src/main/assets/finance");
await mkdir(android, { recursive: true });
for (const f of await readdir(out, { withFileTypes: true }))
  if (f.isFile())
    await copyFile(resolve(out, f.name), resolve(android, f.name));
await mkdir(resolve(android, "fonts"), { recursive: true });
for (const f of await readdir(resolve(out, "fonts")))
  await copyFile(resolve(out, "fonts", f), resolve(android, "fonts", f));
console.log("PWA e assets Android compilados.");
