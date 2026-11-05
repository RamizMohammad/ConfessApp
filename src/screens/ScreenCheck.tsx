/**
 * /dev/screen — what the screen map measured on this iPhone / iPad: the device it matched, the real
 * screen against the viewport iOS gave us (the bottom gap), safe areas, the iPad UI scale, and
 * what's saved on this device. Two markers show the real bottom and the viewport's bottom, and the
 * logo sits both just under the status bar and lower down, to see the iOS top-edge blur. Opened
 * from the Home Screen app by typing nothing — Settings will link it in R7; for now open /dev/screen.
 */
import { useEffect, useState } from "react";
import { forgetScreen, onScreenChange, remeasure, screenProfile, storedScreen, type ScreenProfile } from "../lib/screenMap.ts";
import { Art } from "../ui/Art.tsx";
import { Btn, Switch, Toolbar } from "../ui/kit.tsx";

function raw() {
  const vv = window.visualViewport;
  const cs = getComputedStyle(document.documentElement);
  return {
    screen: `${screen.width} × ${screen.height}`,
    inner: `${window.innerWidth} × ${window.innerHeight}`,
    client: `${document.documentElement.clientWidth} × ${document.documentElement.clientHeight}`,
    visual: vv ? `${Math.round(vv.width)} × ${Math.round(vv.height)} @ ${vv.scale.toFixed(2)} (top ${Math.round(vv.offsetTop)})` : "—",
    dpr: String(window.devicePixelRatio),
    appH: cs.getPropertyValue("--app-h").trim(),
    gapVar: cs.getPropertyValue("--gap").trim(),
    viewport: document.querySelector<HTMLMetaElement>('meta[name="viewport"]')?.content ?? "",
    ua: navigator.userAgent,
  };
}

export default function ScreenCheck() {
  const [p, setP] = useState<ScreenProfile | null>(screenProfile);
  const [r, setR] = useState(raw);
  const [shim, setShim] = useState(true);
  const [copied, setCopied] = useState(false);
  useEffect(() => onScreenChange((q) => { setP(q); setR(raw()); }), []);
  useEffect(() => {
    document.documentElement.classList.toggle("no-shim", !shim);
    return () => document.documentElement.classList.remove("no-shim");
  }, [shim]);

  const rows: [string, string][] = p ? [
    ["Device", p.device ?? "not in the map"],
    ["Class", p.cls],
    ["Screen (pt, portrait)", `${p.screenW} × ${p.screenH} @${p.dpr}x`],
    ["Window (pt)", `${p.windowW} × ${p.windowH} · ${p.orientation}`],
    ["Home Screen app", p.standalone ? "yes" : "no (browser tab)"],
    ["UI scale", `${p.scale}×`],
    ["Bottom gap (iOS bug)", p.gap ? `${p.gap} pt — corrected` : "none"],
    ["Safe area (pt)", `top ${p.safe.top} · bottom ${p.safe.bottom} · left ${p.safe.left} · right ${p.safe.right}`],
  ] : [["Profile", "not measured yet"]];
  const rawRows: [string, string][] = [
    ["screen", r.screen], ["inner", r.inner], ["client", r.client], ["visual viewport", r.visual], ["dpr", r.dpr],
    ["--app-h", r.appH || "100dvh"], ["--gap", r.gapVar], ["viewport meta", r.viewport], ["user agent", r.ua],
  ];
  const text = [...rows, ...rawRows].map(([k, v]) => `${k}: ${v}`).join("\n") + `\nsaved: ${JSON.stringify(storedScreen())}`;

  return (
    <main className="a-screen screen-check">
      <div className="welcome-top sc-logo-top"><Art name="logo_lockup" height={26} label="Confess" /><span className="t-meta">top</span></div>
      <Toolbar title="Screen check" />
      <dl className="sc-table">
        {rows.map(([k, v]) => <div key={k}><dt className="t-meta-small">{k}</dt><dd className="t-body-medium">{v}</dd></div>)}
      </dl>
      <div className="sc-logo-mid"><Art name="logo_lockup" height={26} label="Confess" /><span className="t-meta">lower down</span></div>
      <div className="sc-switch">
        <Switch checked={shim} onChange={setShim} label="Solid status bar strip" />
        <span className="t-body-medium">Solid strip under the status bar</span>
      </div>
      <div className="sc-actions">
        <Btn kind="outlined" onClick={() => { remeasure(); setR(raw()); }}>Measure again</Btn>
        <Btn kind="outlined" onClick={() => { forgetScreen(); window.location.reload(); }}>Forget saved</Btn>
        <Btn onClick={() => { void navigator.clipboard?.writeText(text).then(() => setCopied(true)); }}>{copied ? "Copied" : "Copy all"}</Btn>
      </div>
      <details className="sc-raw">
        <summary className="t-meta-small">Raw values</summary>
        <dl className="sc-table">
          {rawRows.map(([k, v]) => <div key={k}><dt className="t-meta-small">{k}</dt><dd className="t-meta">{v}</dd></div>)}
        </dl>
      </details>
      <a className="t-meta sc-home" href="/">Back to Confess</a>
      <span className="sc-marker real" aria-hidden><span className="t-meta-small">real bottom</span></span>
      <span className="sc-marker view" aria-hidden><span className="t-meta-small">viewport bottom</span></span>
    </main>
  );
}
