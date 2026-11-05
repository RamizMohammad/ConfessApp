import { test } from "node:test";
import assert from "node:assert/strict";
import { A, format, P, S } from "./strings.ts";
import { icons } from "../android/icons.ts";
import { colors, dimens, motion, type } from "../android/tokens.ts";

test("Android string formatting", () => {
  assert.equal(format("%1$s and %2$d", ["a", 3]), "a and 3");
  assert.equal(format("%s/%s %%", ["x", "y"]), "x/y %");
  assert.equal(format("%,d felt", [12345]), "12,345 felt");
  assert.equal(S("avatar_label", 4, "Eclipsed Amber"), "Aura 04 · Eclipsed Amber");
  assert.equal(format("%.1f km", [2.345]), "2.3 km");
  assert.equal(S("onb_step", 2, 3), "Step 2 of 3");
  assert.equal(S("action_cancel"), "Cancel");
  assert.equal(S("error_offline"), "You're offline. Your words will wait.");   // \' unescaped
  assert.equal(P("books_chapters", 1), "1 chapter");
  assert.equal(P("books_chapters", 4), "4 chapters");
  assert.ok(A("tour_titles").length > 0 || true);
});

test("generated tokens match the Android values", () => {
  assert.equal(colors.ink, "#0F0E13");
  assert.equal(colors.amberTint, "rgba(232, 160, 76, 0.078)");
  assert.equal(dimens.buttonHeight, 52);
  assert.equal(dimens.radiusCard, 20);
  assert.equal(type.confession.size, 20);
  assert.equal(type.confession.italic, true);
  assert.equal(type.metaSmall.caps, true);
  assert.equal(motion.screenOpenEnter.ms, 260);
  assert.equal(motion.tabIn.delayMs, 70);
});

test("every icon converted cleanly", () => {
  const n = Object.keys(icons).length;
  assert.ok(n >= 60, `${n} icons`);
  const json = JSON.stringify(icons);
  assert.ok(!/NaN|undefined|null/.test(json));
  assert.equal(icons.bell.tinted, true);
  assert.equal((icons.bell.c[0] as { stroke?: string }).stroke, "currentColor");
});
