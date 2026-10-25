import { test } from "node:test";
import assert from "node:assert/strict";
import { fromBase64url, jwtPayload, toBase64url } from "./base64url.ts";
import { creationOptions, credentialJson, requestOptions } from "./webauthn.ts";

test("base64url round trip, no padding", () => {
  const b = new Uint8Array([0, 251, 255, 1, 2, 3, 62, 63]);
  const s = toBase64url(b);
  assert.doesNotMatch(s, /[+/=]/);
  assert.deepEqual([...fromBase64url(s)], [...b]);
  assert.throws(() => fromBase64url("a+b"));
});

test("jwtPayload reads claims without trusting them", () => {
  const p = toBase64url(new TextEncoder().encode(JSON.stringify({ nonce: "n" })));
  assert.deepEqual(jwtPayload(`x.${p}.y`), { nonce: "n" });
  assert.equal(jwtPayload("garbage"), null);
});

test("creation options → binary fields", () => {
  const o = creationOptions({
    rp: { id: "confess.test", name: "Confess" }, challenge: "AAEC",
    user: { id: "BAUG", name: "Confess account", displayName: "Confess account" },
    pubKeyCredParams: [{ type: "public-key", alg: -7 }],
    excludeCredentials: [{ type: "public-key", id: "BwgJ", transports: ["internal"] }],
    authenticatorSelection: { userVerification: "required" },
  });
  assert.deepEqual([...new Uint8Array(o.challenge as ArrayBuffer)], [0, 1, 2]);
  assert.deepEqual([...new Uint8Array(o.user.id as ArrayBuffer)], [4, 5, 6]);
  assert.equal(o.user.name, "Confess account");
  assert.deepEqual([...new Uint8Array(o.excludeCredentials![0]!.id as ArrayBuffer)], [7, 8, 9]);
});

test("request options → binary fields", () => {
  const o = requestOptions({ challenge: "AAEC", rpId: "confess.test", allowCredentials: [{ type: "public-key", id: "BwgJ" }] });
  assert.equal(o.rpId, "confess.test");
  assert.deepEqual([...new Uint8Array(o.allowCredentials![0]!.id as ArrayBuffer)], [7, 8, 9]);
});

test("assertion → JSON the server expects", () => {
  const buf = (...n: number[]) => new Uint8Array(n).buffer;
  const fake = {
    id: "BwgJ", rawId: buf(7, 8, 9), type: "public-key", authenticatorAttachment: "platform",
    response: { clientDataJSON: buf(1), authenticatorData: buf(2), signature: buf(3), userHandle: buf(4) },
    getClientExtensionResults: () => ({}),
  } as unknown as PublicKeyCredential;
  const j = credentialJson(fake) as { rawId: string; response: Record<string, string> };
  assert.equal(j.rawId, "BwgJ");
  assert.deepEqual(Object.keys(j.response).sort(), ["authenticatorData", "clientDataJSON", "signature", "userHandle"]);
});
