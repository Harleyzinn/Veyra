import { _electron as electron } from "@playwright/test";
import { resolve } from "node:path";
import { mkdirSync, writeFileSync } from "node:fs";
import { createItem, today } from "../shared/model";
async function run() {
  const label = process.env.VEYRA_BENCH_LABEL || "current",
    profile = resolve(".qa/performance-" + Date.now());
  mkdirSync(profile, { recursive: true });
  const env = { ...process.env };
  delete env.ELECTRON_RUN_AS_NODE;
  const before = performance.now(),
    app = await electron.launch({
      executablePath: resolve(
        process.env.VEYRA_BENCH_EXE || "release/win-unpacked/Veyra Life.exe",
      ),
      args: ["--user-data-dir=" + profile],
      env: env as Record<string, string>,
    });
  try {
    const page = await app.firstWindow();
    await page.waitForSelector(".sidebar");
    const startup = performance.now() - before;
    await page.evaluate(() =>
      window.veyra.call("desktop", { welcomeSeen: true }),
    );
    const items = Array.from({ length: 1500 }, (_, n) =>
      createItem(n % 3 === 0 ? "expense" : "note", {
        id: "bench:" + n,
        title: "Synthetic record " + n,
        notes: "Local synthetic benchmark content. ".repeat(30),
        date: today(),
        fields:
          n % 3 === 0
            ? {
                amountMinor: "1000",
                amount: "10",
                currency: "BRL",
                status: "paid",
                category: "Synthetic",
              }
            : {},
      }),
    );
    for (let n = 0; n < items.length; n += 200)
      await page.evaluate(
        (items) => window.veyra.call("save", { items }),
        items.slice(n, n + 200),
      );
    const latency = await page.evaluate(async () => {
      const snapshot: number[] = [],
        search: number[] = [];
      for (let n = 0; n < 12; n++) {
        let start = performance.now();
        await window.veyra.call("snapshot");
        snapshot.push(performance.now() - start);
        start = performance.now();
        await window.veyra.call("search", {
          query: "Synthetic record 150",
          limit: 40,
        });
        search.push(performance.now() - start);
      }
      return { snapshot, search };
    });
    await page.waitForTimeout(1500);
    const processes = await app.evaluate(({ app }) =>
      app.getAppMetrics().map((p) => ({
        type: p.type,
        cpu: p.cpu.percentCPUUsage,
        workingSetKB: p.memory.workingSetSize,
      })),
    );
    const activeSample = await app.evaluate(({ app }) =>
      app
        .getAppMetrics()
        .map((p) => ({
          type: p.type,
          cpu: p.cpu.percentCPUUsage,
          workingSetKB: p.memory.workingSetSize,
        })),
    );
    const panelsStarted = performance.now();
    for (const role of ["dock", "widget-focus", "mini"])
      await page.evaluate(
        (role) => window.veyra.call("openWindow", role),
        role,
      );
    for (const panel of app.windows().filter((p) => !p.url().endsWith("#main")))
      await panel.waitForSelector(".veyra-glass-panel");
    const panelsMs = performance.now() - panelsStarted;
    await page.waitForTimeout(2000);
    const panelProcesses = await app.evaluate(({ app }) =>
      app
        .getAppMetrics()
        .map((p) => ({
          type: p.type,
          cpu: p.cpu.percentCPUUsage,
          workingSetKB: p.memory.workingSetSize,
        })),
    );
    for (const panel of app.windows().filter((p) => !p.url().endsWith("#main")))
      await panel
        .evaluate(() => window.veyra.call("closeWindow"))
        .catch(() => {});
    await page.evaluate(() => window.veyra.call("quit")).catch(() => {});
    await app.close();
    const warmBefore = performance.now();
    const warm = await electron.launch({
      executablePath: resolve(
        process.env.VEYRA_BENCH_EXE || "release/win-unpacked/Veyra Life.exe",
      ),
      args: ["--user-data-dir=" + profile],
      env: env as Record<string, string>,
    });
    let warmMs: number;
    try {
      await warm.firstWindow();
      let owner = warm.windows().find((p) => p.url().endsWith("#main"));
      for (let n = 0; !owner && n < 60; n++) {
        await new Promise((r) => setTimeout(r, 100));
        owner = warm.windows().find((p) => p.url().endsWith("#main"));
      }
      if (!owner) throw Error("Main window missing");
      await owner.waitForSelector(".sidebar");
      warmMs = performance.now() - warmBefore;
      await owner.evaluate(() => window.veyra.call("quit")).catch(() => {});
    } finally {
      await warm.close();
    }
    const report = {
      label,
      startupMs: startup,
      records: items.length,
      latency,
      processes,
      activeSample,
      panelsMs,
      panelProcesses,
      warmMs,
    };
    writeFileSync(
      ".qa/performance-" + label + ".json",
      JSON.stringify(report, null, 2),
    );
    console.log(
      JSON.stringify({
        label,
        startupMs: Math.round(startup),
        snapshotMedianMs: latency.snapshot.toSorted((a, b) => a - b)[6],
        searchMedianMs: latency.search.toSorted((a, b) => a - b)[6],
        memoryMiB: processes.reduce((n, p) => n + p.workingSetKB, 0) / 1024,
        cpuPercent: processes.reduce((n, p) => n + p.cpu, 0),
        warmMs: Math.round(warmMs),
        panelsMs: Math.round(panelsMs),
        panelsMemoryMiB:
          panelProcesses.reduce((n, p) => n + p.workingSetKB, 0) / 1024,
      }),
    );
  } finally {
    await app.close();
  }
}
run().catch((e) => {
  console.error(e.message);
  process.exitCode = 1;
});
