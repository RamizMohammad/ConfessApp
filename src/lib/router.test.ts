import { test } from "node:test";
import assert from "node:assert/strict";
import { href, parse } from "./router.ts";

test("routes round-trip", () => {
  const id = "0b9c3f1e-1234-4abc-8def-0123456789ab";
  const routes = ["/", "/activity", "/me", "/me/books", "/books", "/books/new", `/b/${id}`, `/b/${id}/3`, `/b/${id}/desk`,
    `/b/${id}/edit`, `/b/${id}/write`, `/b/${id}/write/12`, "/s/night_owl", "/story", `/burst/${id}`, `/burst/${id}/following`,
    "/u/quiet_writer", "/settings/appearance", "/settings/avatar", `/p/${id}`];
  for (const r of routes) assert.equal(href(parse(r)), r, r);
  assert.deepEqual(parse(`/b/${id}/7`), { name: "read", id, no: 7 });
  assert.equal(parse("/b/not-a-uuid").name, "feed");
  assert.equal(parse("/s/a").name, "feed");
});
