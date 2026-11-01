import { test } from "node:test";
import assert from "node:assert/strict";
import { floorMod, JavaRandom, javaHash } from "./javaRandom.ts";
import { avatarShapes, DEFAULT_SPEC, encodeSpec, isSpec, PARTS, parseSpec, randomSpec, withPart } from "./avatar.ts";
import { automaticBanner, bannerSpec, CONSTELLATION, pieces, resolveBanner } from "./banner.ts";
import { cellPath, colorOf, level, parseStyle, styleSpec } from "./graph.ts";
import { mix, shade } from "./color.ts";

test("JavaRandom matches java.util.Random", () => {
  assert.equal(new JavaRandom(42).nextInt(), -1170105035);
  assert.equal(new JavaRandom(0).nextInt(), -1155484576);
  assert.equal(new JavaRandom(42).nextInt(10), 0);
  assert.equal(Math.fround(new JavaRandom(42).nextFloat()), Math.fround(0.7275637));
  assert.equal(javaHash("hello"), 99162322);
  assert.equal(floorMod(-7, 10), 3);
});

test("avatar specs round-trip and match the server's rules", () => {
  const s = parseSpec("av1:k2.h3.c1.e0.b1.m2.f0.g0.x0.o3.d5");
  assert.equal(s.hair, 3);
  assert.equal(s.backdrop, 5);
  assert.equal(encodeSpec(s), "av1:k2.h3.c1.e0.b1.m2.f0.g0.x0.o3.d5");
  // Out of range / junk → defaults, never an invalid value.
  const bad = parseSpec("av1:k99.hx.zz.d11");
  assert.equal(bad.skin, 0);
  assert.equal(bad.hair, DEFAULT_SPEC.hair);
  assert.equal(bad.backdrop, 11);
  assert.equal(withPart(s, "eyes", 6).eyes, 0);
  assert.ok(isSpec("av1:k1") && !isSpec("aura_01"));
  // Server: ^av1:[a-z]\d{1,2}(\.[a-z]\d{1,2}){0,15}$, each key once, n < count, ≤ 64 chars.
  for (let i = 0; i < 200; i++) {
    const enc = encodeSpec(randomSpec());
    assert.match(enc, /^av1:[a-z]\d{1,2}(\.[a-z]\d{1,2}){0,15}$/);
    assert.ok(enc.length <= 64);
  }
  assert.deepEqual(PARTS.map((p) => `${p.letter}${p.count}`).join(","), "k8,h10,c10,e6,b4,m6,f5,g4,x5,o10,d12");
});

test("every avatar option draws clean SVG", () => {
  for (const p of PARTS) {
    for (let v = 0; v < p.count; v++) {
      const d = avatarShapes(withPart({ ...DEFAULT_SPEC, hat: 0 }, p.key, v));
      const json = JSON.stringify(d);
      assert.ok(!/NaN|undefined|Infinity/.test(json), `${p.key}=${v}`);
      assert.ok(d.shapes.length > 10);
    }
  }
  // Hats change the hair underneath (beanie over curly → short).
  const curly = avatarShapes({ ...DEFAULT_SPEC, hair: 4 }).shapes.filter((s) => s.el === "circle").length;
  const beanie = avatarShapes({ ...DEFAULT_SPEC, hair: 4, hat: 1 }).shapes.filter((s) => s.el === "circle").length;
  assert.ok(curly > beanie);
});

test("colour maths matches ColorUtils", () => {
  assert.equal(mix(0xFF000000, 0xFFFFFFFF, 0.5) >>> 0, 0xFF7F7F7F);
  assert.equal(shade(0x80FF8000, 0.5) >>> 0, 0x807F4000);
});

test("banners: spec, automatic choice, Android shapes", () => {
  assert.deepEqual(resolveBanner("bn1:p3.c11", "aura_01", "x"), { pattern: 3, colour: 11, automatic: false });
  assert.equal(bannerSpec({ pattern: 3, colour: 11, automatic: false }), "bn1:p3.c11");
  assert.equal(bannerSpec(resolveBanner("junk", "aura_05", "Night_Owl")), "");
  const auto = automaticBanner("aura_05", "Night_Owl");
  assert.equal(auto.colour, 4);
  assert.equal(auto.pattern, floorMod(javaHash("night_owl"), 10));
  assert.equal(resolveBanner("", "av1:k1.d7", "x").colour, 7);
  // Same random numbers as BannerView.build(): new Random(7919 * (pattern + 1)).
  const r = new JavaRandom(7919 * 2);
  assert.equal(pieces(1).ex[0], r.nextFloat());
  assert.equal(pieces(1).ex.length, 120);
  assert.ok(pieces(CONSTELLATION).linkA.length > 0);
});

test("graph styles", () => {
  assert.deepEqual(parseStyle(""), { palette: "amber", shape: "round" });
  assert.deepEqual(parseStyle("g1:ocean.heart"), { palette: "ocean", shape: "heart" });
  assert.deepEqual(parseStyle("g1:nope.nope"), { palette: "amber", shape: "round" });
  assert.equal(styleSpec({ palette: "amber", shape: "round" }), "");
  assert.equal(styleSpec({ palette: "mint", shape: "circle" }), "g1:mint.circle");
  assert.deepEqual([0, 1, 2, 3, 4, 5, 9].map(level), [0, 1, 2, 3, 3, 4, 4]);
  assert.equal(colorOf({ palette: "mono", shape: "round" }, 4)[0], "#ECE8E1");
  for (const s of ["round", "square", "circle", "heart", "diamond"] as const) assert.ok(!/NaN/.test(cellPath(s, 1, 2, 12)));
});

test("web push: VAPID key bytes from base64url", async () => {
  const { base64UrlToBytes } = await import("./webpush.ts");
  const b = base64UrlToBytes("BJ9_oDkVgjMXNnP_G-LpcdwqA9M0fgTGyx8-E1LD-I0dpBSTS2D5QuNX2OxVE-CQuhJat-i57_OfDUa5TD7TjwI");
  assert.equal(b.length, 65);
  assert.equal(b[0], 4);
  assert.deepEqual([...base64UrlToBytes("_-8")], [0xff, 0xef]);
});
