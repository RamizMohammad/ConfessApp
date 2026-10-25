import { test } from "node:test";
import assert from "node:assert/strict";
import { count, length, moderationReason } from "./format.ts";
import { apply } from "./postStore.ts";
import { parse } from "./router.ts";
import type { Post } from "./types.ts";

const post = (id: string, alias = "a"): Post => ({
  postId: id, author: { alias, avatar: "aura_01" }, text: "t", commentsEnabled: true, likeCount: 0, commentCount: 0,
  likedByMe: false, myReaction: null, reactions: {}, mood: null, letterTo: null, gif: null, poll: null, isMine: false,
  createdAt: new Date().toISOString(), chapter: null, riskScore: null, sensitive: false, burst: null,
});

test("counts like the Android app", () => {
  assert.equal(count(999), "999");
  assert.equal(count(1200), "1.2k");
  assert.equal(count(1000), "1k");
  assert.equal(count(14_500), "14k");
  assert.equal(count(1_400_000), "1.4M");
});

test("length counts emoji as one, like the server", () => {
  assert.equal(length("🫂🫂"), 2);
  assert.equal(length("abc"), 3);
});

test("moderation label → the Android wording", () => {
  assert.match(moderationReason("This can't be posted — it breaks our guidelines (religious hate)"), /religion/);
  assert.match(moderationReason("… (self harm promotion)"), /14416/);
  assert.match(moderationReason("something else"), /community guidelines/);
});

test("list updates: update, remove, block, created", () => {
  const list = [post("1", "x"), post("2", "y"), post("3", "x")];
  assert.equal(apply(list, { type: "update", post: { ...post("2", "y"), likeCount: 5 } })[1]!.likeCount, 5);
  assert.deepEqual(apply(list, { type: "remove", postId: "2" }).map((p) => p.postId), ["1", "3"]);
  assert.deepEqual(apply(list, { type: "blocked", alias: "X" }).map((p) => p.postId), ["2"]);
  assert.deepEqual(apply(list, { type: "created", post: post("9") }).map((p) => p.postId), ["9", "1", "2", "3"]);
});

test("routes", () => {
  assert.deepEqual(parse("/p/5FB0CC12-E710-4BD5-9886-3D0A61AA0F6E"), { name: "post", id: "5fb0cc12-e710-4bd5-9886-3d0a61aa0f6e" });
  assert.deepEqual(parse("/p/not-an-id"), { name: "feed" });
  assert.deepEqual(parse("/write"), { name: "write" });
  assert.deepEqual(parse("/anything"), { name: "feed" });
});
