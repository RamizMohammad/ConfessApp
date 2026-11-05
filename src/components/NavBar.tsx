/**
 * view_bottom_bar + NavBar.java: Feed · Search · (Confess) · Books · Me, in the reader's order
 * (two tabs, the Confess button, two tabs). Labels always / selected only / never. Picking a tab
 * draws its icon (avd_tab_*) with a 0.72 → 1 overshoot pop.
 */
import { useEffect, useRef, useState } from "react";
import { LABELS_ALWAYS, LABELS_SELECTED, useAppearance, type NavTab } from "../lib/appearance.ts";
import { S } from "../lib/strings.ts";
import { AnimatedArt, overshoot, reducedMotion } from "../ui/Art.tsx";
import { Icon, type IconName } from "../ui/Icon.tsx";
import { tween } from "../ui/motion.ts";

const ICON: Record<NavTab, IconName> = { feed: "letters", search: "search", books: "book", me: "user" };
const NAME: Record<NavTab, string> = { feed: "tab_feed", search: "tab_search", books: "tab_books", me: "tab_me" };
const CD: Record<NavTab, string> = { feed: "tab_feed", search: "cd_search", books: "tab_books", me: "tab_me" };

export function NavBar({ selected, onTab, onCompose }:
  { selected: NavTab | null; onTab: (t: NavTab) => void; onCompose: () => void }) {
  const a = useAppearance();
  const animate = a.navAnimate && !reducedMotion();
  // Which tab icons are showing their animated drawable, and a counter to replay it.
  const [plays, setPlays] = useState<Partial<Record<NavTab, number>>>({});
  const icons = useRef<Partial<Record<NavTab, HTMLSpanElement | null>>>({});
  const first = useRef(true);

  const bump = (t: NavTab) => {
    if (!animate) return;
    setPlays((p) => ({ ...p, [t]: (p[t] ?? 0) + 1 }));
    void tween(icons.current[t], { scale: [0.72, 1] }, 360, { ease: overshoot(2.6) });
  };

  // A new tab selected: its icon draws itself; the others go back to their still icons.
  useEffect(() => {
    if (first.current) { first.current = false; return; }
    setPlays((p) => (selected && p[selected] ? { [selected]: p[selected] } : {}));
    if (selected) bump(selected);
  }, [selected]); // eslint-disable-line react-hooks/exhaustive-deps

  const order = a.navOrder;
  const tab = (t: NavTab) => {
    const sel = t === selected;
    const showLabel = a.navLabels === LABELS_ALWAYS || (a.navLabels === LABELS_SELECTED && sel);
    const n = plays[t];
    return (
      <button key={t} type="button" className={`nav-tab${sel ? " on" : ""}`} aria-label={S(CD[t])}
              aria-current={sel ? "page" : undefined}
              onClick={() => { if (t === "search") bump("search"); onTab(t); }}>
        <span className="nav-icon" ref={(el) => { icons.current[t] = el; }}>
          {n && animate ? <AnimatedArt key={n} avd={`avd_tab_${t}`} size={24} /> : <Icon name={ICON[t]} size={24} />}
        </span>
        {showLabel ? <span className="nav-label t-meta-small">{S(NAME[t])}</span> : null}
      </button>
    );
  };

  return (
    <nav className="nav-bar" aria-label="Main">
      {tab(order[0]!)}
      {tab(order[1]!)}
      <div className="nav-compose">
        <button type="button" className="nav-compose-btn" aria-label={S("cd_compose")} onClick={onCompose}>
          <Icon name="compose" size={24} />
        </button>
        {a.navLabels === LABELS_ALWAYS ? <span className="nav-label t-meta-small">{S("tab_confess")}</span> : null}
      </div>
      {tab(order[2]!)}
      {tab(order[3]!)}
    </nav>
  );
}
