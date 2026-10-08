import { spawn } from "node:child_process";
const run = (cmd, args) =>
  new Promise((resolve, reject) => {
    const p = spawn(cmd, args, { stdio: "inherit", shell: false });
    p.on("exit", (c) => (c ? reject(new Error("Processo falhou")) : resolve()));
  });
await run(process.execPath, ["scripts/build.mjs"]);
await run(process.execPath, ["node_modules/electron/cli.js", "."]);
