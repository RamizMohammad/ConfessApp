// The Home Screen icon and the startup images, drawn from the Android app's own drawables
// (src/android/*.svg, written by scripts/android_sync.py). Run after a sync that changed them:
//
//   node scripts/make_app_images.mjs        (needs Playwright: npm i --no-save playwright)
//
//  - public/icons/: apple-touch-icon (180), icon-192, icon-512, icon-maskable-512, favicon-32 —
//    the adaptive launcher icon (cream tile + Letty). iOS rounds the corners itself.
//  - public/splash/: the Android 12 splash (ink, Letty 240 dp in the middle) for every iPhone / iPad
//    size; the <link rel="apple-touch-startup-image"> tags in index.html are rewritten to match
//    (between the SPLASH markers).
// Shrink the PNGs afterwards if you like: pngquant --ext .png --force public/splash/*.png
import { chromium } from "playwright";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const WEB = join(dirname(fileURLToPath(import.meta.url)), "..");
const read = (f) => readFileSync(join(WEB, f), "utf8").replace(/<!--.*?-->\n?/s, "");
const splash = read("src/android/splash.svg");
const bg = read("src/android/launcher-background.svg");
const fg = read("src/android/launcher-foreground.svg");
const ink = /--c-ink:\s*([^;]+);/.exec(readFileSync(join(WEB, "src/android/tokens.css"), "utf8"))[1];

// CSS size (pt) × pixel ratio, portrait. iPads also get landscape.
const SCREENS = [
  [440, 956, 3, "iPhone 16 Pro Max"], [402, 874, 3, "iPhone 16 Pro"], [430, 932, 3, "iPhone 15/16 Plus, 14 Pro Max"],
  [393, 852, 3, "iPhone 14 Pro – 16"], [428, 926, 3, "iPhone 12–14 Plus/Pro Max"], [390, 844, 3, "iPhone 12–14"],
  [375, 812, 3, "iPhone X/XS/11 Pro, 12/13 mini"], [360, 780, 3, "iPhone 12/13 mini (scaled)"], [414, 896, 3, "iPhone XS Max/11 Pro Max"],
  [414, 896, 2, "iPhone XR/11"], [414, 736, 3, "iPhone 8 Plus"], [375, 667, 2, "iPhone SE 2/3, 8"], [320, 568, 2, "iPhone SE 1"],
  [1032, 1376, 2, "iPad Pro 13 (M4)", true], [1024, 1366, 2, "iPad Pro 12.9", true], [834, 1210, 2, "iPad Pro 11 (M4)", true],
  [834, 1194, 2, "iPad Pro 11, Air 11", true], [820, 1180, 2, "iPad Air 10.9, iPad 10", true], [834, 1112, 2, "iPad Air 10.5", true],
  [810, 1080, 2, "iPad 10.2", true], [768, 1024, 2, "iPad 9.7, mini 5", true], [744, 1133, 2, "iPad mini 6/7", true],
];

const browser = await chromium.launch();
const page = await browser.newPage();

// Icons: an adaptive icon is a 108 dp canvas of which the middle 72 dp shows (crop = full-bleed square).
// Maskable keeps the whole 108 canvas (its safe zone is the middle 80 %, which the 72 dp fits inside).
const layer = (svg, size) => svg.replace(/width="[\d.]+" height="[\d.]+"/, `width="${size}" height="${size}"`);
const adaptive = (px, crop) => {
  const canvas = crop ? (px * 108) / 72 : px;
  const off = crop ? -(canvas - px) / 2 : 0;
  const pos = `position:absolute;left:${off}px;top:${off}px;width:${canvas}px;height:${canvas}px`;
  return `<div style="position:relative;width:${px}px;height:${px}px;overflow:hidden"><div style="${pos}">${layer(bg, canvas)}</div><div style="${pos}">${layer(fg, canvas)}</div></div>`;
};
mkdirSync(join(WEB, "public/icons"), { recursive: true });
for (const [file, px, crop] of [["apple-touch-icon.png", 180, true], ["icon-192.png", 192, true], ["icon-512.png", 512, true],
  ["icon-maskable-512.png", 512, false], ["favicon-32.png", 32, true]]) {
  await page.setViewportSize({ width: px, height: px });
  await page.setContent(`<html><head><style>html,body{margin:0}</style></head><body>${adaptive(px, crop)}</body></html>`);
  await page.screenshot({ path: join(WEB, "public/icons", file) });
}

mkdirSync(join(WEB, "public/splash"), { recursive: true });
const links = [];
for (const [w, h, dpr, name, ipad] of SCREENS) {
  for (const land of ipad ? [false, true] : [false]) {
    const [vw, vh] = land ? [h, w] : [w, h];
    const ctx = await browser.newContext({ viewport: { width: vw, height: vh }, deviceScaleFactor: dpr });
    const p = await ctx.newPage();
    await p.setContent(`<html><body style="margin:0;background:${ink};display:grid;place-items:center;height:100vh">${splash}</body></html>`);
    const file = `splash-${vw * dpr}x${vh * dpr}.png`;
    await p.screenshot({ path: join(WEB, "public/splash", file) });
    await ctx.close();
    links.push(`    <link rel="apple-touch-startup-image" href="/splash/${file}" media="(device-width: ${w}px) and (device-height: ${h}px) and (-webkit-device-pixel-ratio: ${dpr}) and (orientation: ${land ? "landscape" : "portrait"})" /> <!-- ${name} -->`);
  }
}
await browser.close();

const html = join(WEB, "index.html");
const doc = readFileSync(html, "utf8");
const block = `    <!-- SPLASH (scripts/make_app_images.mjs) -->\n${links.join("\n")}\n    <!-- /SPLASH -->`;
writeFileSync(html, /    <!-- SPLASH[\s\S]*?\/SPLASH -->/.test(doc)
  ? doc.replace(/    <!-- SPLASH[\s\S]*?\/SPLASH -->/, block)
  : doc.replace("    <title>", `${block}\n    <title>`));
console.log(`5 icons → public/icons/, ${links.length} startup images → public/splash/, links in index.html`);
