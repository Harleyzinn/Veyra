import { test } from "node:test";
import assert from "node:assert/strict";
import { generateKeyPairSync, sign } from "node:crypto";
import { newer, trustedUrl, verifyManifest } from "../electron/updater";
test("desktop updates compare semantic versions without downgrades", () => {
  assert(newer("3.1.0", "3.0.9"));
  assert(!newer("3.0.0", "3.0.0"));
  assert(!newer("2.99.0", "3.0.0"));
  assert(!newer("3.2.0-beta", "3.0.0"));
});
test("signed manifests reject altered content, wrong assets and untrusted hosts", () => {
  const keys = generateKeyPairSync("ed25519");
  const pub = keys.publicKey.export({ format: "pem", type: "spki" }).toString();
  const url =
    "https://github.com/Harleyzinn/Veyra/releases/download/desktop-v3.1.0/VeyraLife-Setup-3.1.0.exe";
  const release = {
    version: "3.1.0",
    tag: "desktop-v3.1.0",
    notes: "",
    manifestUrl: "",
    installerUrl: url,
  };
  const payload = JSON.stringify({
    version: "3.1.0",
    url,
    name: "VeyraLife-Setup-3.1.0.exe",
    sha256: "a".repeat(64),
    size: 50000,
  });
  const signed = {
    payload,
    signature: sign(null, Buffer.from(payload), keys.privateKey).toString(
      "base64",
    ),
  };
  assert.equal(verifyManifest(signed, pub, release).size, 50000);
  assert.throws(() =>
    verifyManifest(
      { ...signed, payload: payload.replace("50000", "50001") },
      pub,
      release,
    ),
  );
  assert.throws(() =>
    verifyManifest(signed, pub, { ...release, version: "3.2.0" }),
  );
  assert.throws(() => trustedUrl("https://evil.test/app.exe"));
  assert.throws(() =>
    trustedUrl(
      "https://github.com/other/repo/releases/download/desktop-v3.0.0/app.exe",
    ),
  );
});
