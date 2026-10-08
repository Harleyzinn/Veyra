import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { systemGoogle } from "../electron/google-login";

test("Google callback rejects foreign origin, malformed content, wrong nonce and arbitrary files", async () => {
  const folder = mkdtempSync(resolve(".qa/oauth-"));
  writeFileSync(
    resolve(folder, "google.html"),
    "<html>Local login fixture</html>",
  );
  writeFileSync(resolve(folder, "google.js"), "// local fixture");
  let opened!: (url: string) => void;
  const urlPromise = new Promise<string>((r) => (opened = r));
  const flow = systemGoogle(
    {
      apiKey: "synthetic",
      projectId: "synthetic",
      appId: "synthetic",
      authDomain: "synthetic.firebaseapp.com",
    },
    folder,
    async (url) => opened(url),
  );
  const rejected = flow.result.catch((e) => e);
  try {
    const url = new URL(await urlPromise),
      base = url.origin,
      nonce = url.hash.slice("#session=".length);
    const post = (origin: string, contentType: string, session: string) =>
      fetch(base + "/complete", {
        method: "POST",
        headers: { Origin: origin, "Content-Type": contentType },
        body: JSON.stringify({
          nonce: session,
          idToken: "synthetic-token-for-protocol-test",
        }),
      });
    assert.equal((await fetch(base + "/../private.key")).status, 404);
    assert.equal(
      (await post("https://foreign.example", "application/json", nonce)).status,
      404,
    );
    assert.equal((await post(base, "text/plain", nonce)).status, 404);
    assert.equal(
      (await post(base, "application/json", "wrong-nonce")).status,
      403,
    );
    const page = await fetch(base);
    assert.equal(page.headers.get("cache-control"), "no-store");
    assert(
      page.headers
        .get("content-security-policy")
        ?.includes("object-src 'none'"),
    );
    assert.equal((await post(base, "application/json", nonce)).status, 200);
    assert.equal(await flow.result, "synthetic-token-for-protocol-test");
  } finally {
    flow.cancel();
    await rejected;
  }
});
