import { _electron as electron } from "@playwright/test";
import assert from "node:assert/strict";
import { resolve } from "node:path";
async function run() {
  const env = { ...process.env };
  delete env.ELECTRON_RUN_AS_NODE;
  const app = await electron.launch({
    executablePath: resolve(process.env.VEYRA_PACKAGED_EXECUTABLE || "release/win-unpacked/Veyra Life.exe"),
    env: env as Record<string, string>,
    args: [],
  });
  try {
    const page = await app.firstWindow();
    await page.waitForSelector(".sidebar", { timeout: 30000 });
    const snapshot = await page.evaluate(() => window.veyra.call("snapshot"));
    assert.equal(snapshot.version, "3.0.0");
    assert(snapshot.configured);
    assert(await app.evaluate(({ app }) => app.isPackaged));
    assert.equal(
      await page.evaluate(() => typeof (window as any).require),
      "undefined",
    );
    console.log(
      "Packaged desktop: launch, embedded Firebase config, version and renderer isolation approved.",
    );
  } finally {
    await app.close();
  }
}
void run().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});
