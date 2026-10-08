import { test } from "node:test";
import assert from "node:assert/strict";
import { encodeBackup, decodeBackup } from "../electron/backup";
test("Android-compatible password backup roundtrip, wrong password and tampering", () => {
  const original = { schema: 3, items: [{ title: "Private synthetic text" }] };
  const text = encodeBackup(original, "test-only-password");
  assert(!text.includes("Private synthetic"));
  assert.deepEqual(decodeBackup(text, "test-only-password"), original);
  assert.throws(() => decodeBackup(text, "wrong-password"), /Senha incorreta/);
  const envelope = JSON.parse(text);
  const data = Buffer.from(envelope.data, "base64");
  data[0] ^= 1;
  envelope.data = data.toString("base64");
  assert.throws(() =>
    decodeBackup(JSON.stringify(envelope), "test-only-password"),
  );
});
