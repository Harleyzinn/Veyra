import { test } from "node:test";
import assert from "node:assert/strict";
import { FirebaseClient, envelope, values } from "../electron/firebase";
import { SecureFiles, sha } from "../electron/vault";
import { createItem } from "../shared/model";

function client(fetcher: typeof fetch) {
  const files = { json: () => null } as unknown as SecureFiles;
  const c = new FirebaseClient(
    { apiKey: "test", projectId: "test", appId: "test", authDomain: "test" },
    files,
    fetcher,
  );
  c.session = {
    idToken: "synthetic",
    refreshToken: "synthetic",
    expiresAt: Date.now() + 3600000,
    user: {
      uid: "owner",
      email: "owner@example.test",
      name: "Owner",
      verified: true,
    },
  };
  return c;
}
const response = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), { status });

test("Firebase user-token writes avoid IAM transaction endpoints and keep an atomic revision precondition", async () => {
  const item = createItem("note", { title: "Local note" });
  const calls: string[] = [];
  const c = client((async (url, options) => {
    const endpoint = String(url).split(":").at(-1)!;
    calls.push(endpoint);
    const body = JSON.parse(String(options?.body));
    if (endpoint === "batchGet")
      return response([
        {
          found: {
            name: body.documents[0],
            fields: values({ deleting: false }),
          },
        },
        { missing: body.documents[1] },
      ]);
    assert.equal(endpoint, "commit");
    assert.equal(body.transaction, undefined);
    assert.equal(body.writes.length, 2);
    assert.deepEqual(body.writes[0].currentDocument, { exists: false });
    assert.equal(body.writes[0].update.fields.revision.integerValue, "1");
    assert.equal(
      body.writes[1].updateTransforms[0].fieldPath,
      "latestChangeAt",
    );
    return response({ commitTime: "2026-10-07T00:00:00Z" });
  }) as typeof fetch);
  const result = await c.push("owner", {
    item,
    baseRevision: 0,
    operationId: "local",
    localRevision: 1,
  });
  assert.equal(result.revision, 1);
  assert.deepEqual(calls, ["batchGet", "commit"]);
});

test("a concurrent create is reread and returned as a conflict without overwriting the other device", async () => {
  const item = createItem("note", { title: "Local version" });
  let reads = 0,
    commits = 0;
  const c = client((async (url, options) => {
    const endpoint = String(url).split(":").at(-1)!;
    const body = JSON.parse(String(options?.body));
    if (endpoint === "batchGet") {
      const root = {
        found: { name: body.documents[0], fields: values({ deleting: false }) },
      };
      if (++reads === 1)
        return response([root, { missing: body.documents[1] }]);
      return response([
        root,
        {
          found: {
            name: c.root() + "/users/owner/notes/" + sha(item.id),
            fields: values({
              ...envelope(
                "owner",
                { ...item, title: "Other device" },
                1,
                "remote",
              ),
              updatedAt: "2026-10-07T00:00:00Z",
            }),
          },
        },
      ]);
    }
    assert.equal(endpoint, "commit");
    commits++;
    return response(
      { error: { status: "ALREADY_EXISTS", message: "Concurrent create" } },
      409,
    );
  }) as typeof fetch);
  const result = await c.push("owner", {
    item,
    baseRevision: 0,
    operationId: "local",
    localRevision: 1,
  });
  assert.equal(result.remote?.item.title, "Other device");
  assert.equal(item.title, "Local version");
  assert.equal(reads, 2);
  assert.equal(commits, 1);
});

test("permission refusals preserve canonical error codes and do not falsely blame email verification", async () => {
  const c = client((async () =>
    response(
      {
        error: { status: "PERMISSION_DENIED", message: "Missing permissions" },
      },
      403,
    )) as typeof fetch);
  await assert.rejects(
    c.request("owner", "/users/owner", undefined, "GET"),
    (error: any) => {
      assert.equal(error.status, 403);
      assert.equal(error.code, "PERMISSION_DENIED");
      assert.match(error.message, /continuam salvas/);
      assert.doesNotMatch(error.message, /e-mail/);
      return true;
    },
  );
});
