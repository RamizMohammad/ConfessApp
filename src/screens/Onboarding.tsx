/**
 * Sign-up, as the Android app does it:
 *   S3 · Community guidelines (GuidelinesActivity + activity_guidelines.xml) — also read-only
 *   S4 · Choose your alias (AliasActivity + activity_alias.xml), step 1 of 2, with the optional
 *        password lock
 *   S5 · Pick a face that isn't yours (AvatarActivity + activity_avatar.xml), step 2 of 2:
 *        creates the account
 * Nothing reaches the server before S5 except the alias availability check.
 */
import { useCallback, useEffect, useRef, useState } from "react";
import terms from "../data/terms.json";
import { api, ApiError } from "../lib/api.ts";
import { errorMessage, useSession } from "../lib/session.tsx";
import { S } from "../lib/strings.ts";
import { AURAS, Aura, resolveAura } from "../components/Aura.tsx";
import { useToast } from "../components/toast.tsx";
import { isSpec } from "../lib/avatar.ts";
import { AvatarStudio, startingSpec } from "./AvatarStudio.tsx";
import { Icon, type IconName } from "../ui/Icon.tsx";
import { Btn, Checkbox, Chip, haptic, IconBtn, LinearProgress, SectionLabel, StepBar, Switch, Tag, TextField } from "../ui/kit.tsx";

const TOTAL_STEPS = 2;

/* ── S3 · Community guidelines ──────────────────────────────────────────── */

type Section = { heading: string; terms: string[] };

/** "📖 Introduction" → ["📖", "Introduction"]; text without a leading emoji → ["", text]. */
export function splitEmoji(s: string): [string, string] {
  const t = (s ?? "").trim();
  const space = t.indexOf(" ");
  if (space > 0) {
    const first = t.slice(0, space);
    if (first.includes("⃣") || !/[\p{L}\p{N}]/u.test(first)) return [first, t.slice(space + 1).trim()];
  }
  return ["", t];
}

function GuidelineSection({ s, initiallyOpen, onToggle }: { s: Section; initiallyOpen: boolean; onToggle: () => void }) {
  const [open, setOpen] = useState(initiallyOpen);
  const [emoji, title] = splitEmoji(s.heading);
  return (
    <div className="gl-section">
      <button type="button" className="gl-head" aria-expanded={open}
              onClick={() => { setOpen(!open); onToggle(); }}>
        <span className="gl-emoji" aria-hidden>{emoji || "•"}</span>
        <span className="gl-head-text">
          <span className="t-headline-small gl-title">{title}</span>
          <span className="t-meta gl-count">{S("guidelines_points", s.terms.length)}</span>
        </span>
        <Icon name="chevron_down" className={open ? "gl-chev open" : "gl-chev"} />
      </button>
      {open ? (
        <div className="gl-body">
          {s.terms.map((term) => {
            const [mark, text] = splitEmoji(term);
            return (
              <div className="gl-term" key={term}>
                <span className="gl-mark" aria-hidden>{mark || "•"}</span>
                <span className="t-body-medium gl-text">{text}</span>
              </div>
            );
          })}
        </div>
      ) : null}
    </div>
  );
}

/** The guideline cards on their own (Settings, Compose). */
export function GuidelinesList() {
  return (
    <div className="gl-sections">
      {(terms as Section[]).map((s, i) => <GuidelineSection key={s.heading} s={s} initiallyOpen={i === 0} onToggle={() => {}} />)}
    </div>
  );
}

/** How far down the page I've read (0–1), for the progress bar under the top bar. */
function useReadProgress(): [number, () => void] {
  const [p, setP] = useState(0);
  const update = useCallback(() => {
    const range = document.documentElement.scrollHeight - window.innerHeight;
    setP(range <= 0 ? 1 : Math.min(1, window.scrollY / range));
  }, []);
  useEffect(() => {
    update();
    window.addEventListener("scroll", update, { passive: true });
    window.addEventListener("resize", update);
    return () => { window.removeEventListener("scroll", update); window.removeEventListener("resize", update); };
  }, [update]);
  return [p, update];
}

/** S3. `readOnly`: just reading (from the sign-in sheet / settings) — no accept bar. */
export function Guidelines({ readOnly, onClose }: { readOnly?: boolean; onClose?: () => void }) {
  const { goTo, cancelOnboarding } = useSession();
  const [accepted, setAccepted] = useState(false);
  const [progress, update] = useReadProgress();
  useEffect(() => { window.scrollTo(0, 0); }, []);
  return (
    <main className="a-col gl">
      <header className="a-topbar">
        <div className="gl-bar">
          <IconBtn icon="arrow_left" label={S("action_back")} onClick={() => (readOnly ? onClose?.() : cancelOnboarding())} />
          <span className="t-meta-small gl-step"><i className="k-dot" aria-hidden />{S("guidelines_step")}</span>
        </div>
        <LinearProgress value={progress} />
      </header>
      <div className="gl-content">
        <Tag>{S("guidelines_tag")}</Tag>
        <h1 className="t-headline gl-h">{S("guidelines_title")}</h1>
        <p className="t-body-medium gl-sub">{S("guidelines_subtitle")}</p>
        <p className="t-quote gl-quote">{S("guidelines_quote")}</p>
        <div className="gl-sections">
          {(terms as Section[]).map((s, i) => (
            <GuidelineSection key={s.heading} s={s} initiallyOpen={i === 0} onToggle={() => requestAnimationFrame(update)} />
          ))}
        </div>
      </div>
      {readOnly ? null : (
        <div className="a-bottombar raised">
          <Checkbox checked={accepted} onChange={setAccepted}>{S("guidelines_accept")}</Checkbox>
          <Btn block icon="arrow_right" disabled={!accepted} className="gl-continue"
               onClick={() => { haptic(); goTo("alias"); }}>{S("guidelines_continue")}</Btn>
        </div>
      )}
    </main>
  );
}

/* ── S4 · Alias ─────────────────────────────────────────────────────────── */

const ALIAS_RE = /^[A-Za-z0-9_]{3,15}$/;
const DEBOUNCE_MS = 450;

interface Verdict {
  alias: string;
  valid: boolean;
  available: boolean;
  allowed: boolean;
  reason: string | null;
  message: string | null;
  suggestions: string[];
}

type Status = "rules" | "checking" | "available" | "taken" | "notAllowed" | "offline";

/** AliasSuggestions: night-themed ideas (≤ 15 chars) for the Shuffle chips. */
const FIRST = ["quiet", "silent", "midnight", "velvet", "hollow", "amber", "paper", "lunar", "misty", "faded", "gentle",
  "ember", "dusk", "still", "folded", "drifting", "candle", "night", "soft", "wistful"];
const SECOND = ["owl", "echo", "ash", "star", "tide", "moth", "letter", "ink", "wander", "lamp", "fern", "rain", "frost",
  "willow", "harbor", "sparrow", "ember", "quill", "lantern", "moon"];
const rnd = (n: number) => crypto.getRandomValues(new Uint32Array(1))[0]! % n;
export function aliasIdeas(count: number): string[] {
  const out = new Set<string>();
  for (let guard = 0; out.size < count && guard < 200; guard++) {
    const a = FIRST[rnd(FIRST.length)]!, b = SECOND[rnd(SECOND.length)]!;
    if (a === b) continue;
    let alias = `${a}_${b}`;
    if (alias.length > 15) continue;
    if (alias.length <= 12 && rnd(3) === 0) alias += String(rnd(90) + 10);
    out.add(alias);
  }
  return [...out];
}

/** Only characters the server accepts (spaces → underscores), at most 15. */
const cleanAlias = (v: string) => v.replace(/ /g, "_").replace(/[^A-Za-z0-9_]/g, "").slice(0, 15);

/** 0 = empty, 1 weak, 2 fair, 3 strong (AliasActivity.strength). */
export function strength(pw: string): number {
  if (!pw) return 0;
  let score = 0;
  if (pw.length >= 8) score++;
  if (pw.length >= 12) score++;
  let lower = false, upper = false, digit = false, symbol = false;
  for (const c of pw) {
    if (c.toLowerCase() !== c.toUpperCase() && c === c.toLowerCase()) lower = true;
    else if (c.toLowerCase() !== c.toUpperCase() && c === c.toUpperCase()) upper = true;
    else if (/\d/.test(c)) digit = true;
    else symbol = true;
  }
  if ((lower && upper) || (digit && (lower || upper))) score++;
  if (symbol) score++;
  if (pw.length < 8) return 1;
  return Math.max(1, Math.min(3, score));
}

const STATUS: Record<Status, { text: string; tone: string; icon?: IconName }> = {
  rules: { text: "alias_rules", tone: "muted" },
  checking: { text: "alias_checking", tone: "secondary" },
  available: { text: "alias_available", tone: "sage", icon: "check" },
  taken: { text: "alias_taken", tone: "brick", icon: "close" },
  notAllowed: { text: "alias_not_allowed", tone: "brick", icon: "alert" },
  offline: { text: "alias_offline", tone: "secondary", icon: "wifi_off" },
};

export function AliasStep() {
  const { draft, setDraft, goTo } = useSession();
  const [value, setValue] = useState(draft.alias);
  const [status, setStatus] = useState<Status>("rules");
  const [check, setCheck] = useState<Verdict | null>(null);
  const [ideas, setIdeas] = useState(() => aliasIdeas(3));
  const [lock, setLock] = useState(draft.password != null);
  const [password, setPassword] = useState(draft.password ?? "");
  const seq = useRef(0);
  const pwRef = useRef<HTMLInputElement>(null);
  const panel = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const alias = value.trim();
    setCheck(null);
    if (!ALIAS_RE.test(alias)) { setStatus("rules"); return; }
    const mine = ++seq.current;
    setStatus("checking");
    const t = window.setTimeout(async () => {
      try {
        const v = await api<Verdict>("/users/alias-available", { query: { alias }, raw: true });
        if (mine !== seq.current) return;
        setCheck(v);
        const s: Status = !v.valid ? "rules" : !v.allowed ? "notAllowed" : v.available ? "available" : "taken";
        setStatus(s);
        if (s === "taken" || s === "notAllowed") haptic();
      } catch {
        if (mine === seq.current) setStatus("offline");
      }
    }, DEBOUNCE_MS);
    return () => window.clearTimeout(t);
  }, [value]);

  const alias = value.trim();
  const aliasOk = status === "available" && check?.alias?.toLowerCase() === alias.toLowerCase();
  const lockOk = !lock || password.length >= 8;
  const ok = aliasOk && lockOk;
  const st = STATUS[status];
  const reason = status === "notAllowed" && check
    ? (check.reason === "identity" ? S("alias_not_allowed_identity") : check.reason === "hateful" ? S("alias_not_allowed_hateful")
      : check.reason === "offensive" ? S("alias_not_allowed_offensive") : check.message ?? S("alias_not_allowed"))
    : null;
  const free = status === "taken" ? (check?.suggestions ?? []).filter((s) => ALIAS_RE.test(s)) : [];
  const s = strength(password);
  const tone = s <= 1 ? "brick" : s === 2 ? "amber" : "sage";

  const next = () => {
    if (!ok) return;
    haptic();
    setDraft({ alias, password: lock ? password : null });
    goTo("avatar");
  };
  const pick = (v: string) => { haptic("tick"); setValue(v); };

  return (
    <main className="a-col">
      <header className="a-topbar"><StepBar step={1} total={TOTAL_STEPS} onBack={() => goTo("guidelines")} /></header>
      <div className="ob-content">
        <h1 className="t-headline">{S("alias_title")}</h1>
        <p className="t-body ob-sub">{S("alias_subtitle")}</p>

        <div className="ob-plate alias-card">
          <span className="t-meta-small ob-label">{S("alias_label")}</span>
          <TextField aria-label={S("alias_label")} prefix="@" value={value} placeholder={S("alias_hint")} maxLength={15} counter={15}
                     autoComplete="off" autoCapitalize="none" autoCorrect="off" spellCheck={false} enterKeyHint="done"
                     onChange={(e) => setValue(cleanAlias(e.target.value))} onClear={() => setValue("")}
                     onKeyDown={(e) => { if (e.key === "Enter") next(); }}
                     bad={status === "taken" || status === "notAllowed"} className="alias-field" />
          <span className={`t-meta alias-status ${st.tone}`} role="status" aria-live="polite">
            {st.icon ? <Icon name={st.icon} size={24} /> : null}{S(st.text)}
          </span>
          {reason ? <p className="t-meta alias-reason">{reason}</p> : null}
          {free.length ? (
            <div className="alias-free">
              <span className="t-meta-small alias-free-title">{S("alias_free_title")}</span>
              <div className="chip-group">{free.map((f) => <Chip key={f} onClick={() => pick(f)}>{f}</Chip>)}</div>
            </div>
          ) : null}
        </div>
        <p className="t-meta ob-note">{S("alias_permanent")}</p>

        <div className="alias-sugg-head">
          <span className="t-meta-small ob-label grow">{S("alias_suggestions")}</span>
          <button type="button" className="alias-shuffle t-meta" onClick={() => { haptic("tick"); setIdeas(aliasIdeas(3)); }}>
            <Icon name="shuffle" size={18} />{S("alias_shuffle")}
          </button>
        </div>
        <div className="chip-group">{ideas.map((f) => <Chip key={f} onClick={() => pick(f)}>{f}</Chip>)}</div>

        <div className="ob-plate lock-card">
          <div className="lock-row">
            <span className="ob-tile amber"><Icon name="lock" size={20} /></span>
            <span className="lock-text">
              <span className="t-label">{S("lock_title")}</span>
              <span className="t-meta lock-sub">{S("lock_subtitle")}</span>
            </span>
            <Switch checked={lock} label={S("lock_title")} onChange={(on) => {
              setLock(on);
              if (on) requestAnimationFrame(() => { pwRef.current?.focus(); panel.current?.scrollIntoView({ block: "end", behavior: "smooth" }); });
            }} />
          </div>
          {lock ? (
            <div className="lock-panel" ref={panel}>
              <TextField password inputRef={pwRef} value={password} placeholder={S("lock_hint")} aria-label={S("lock_hint")}
                         maxLength={64} autoComplete="new-password" onChange={(e) => setPassword(e.target.value)} />
              <div className="strength">
                {[1, 2, 3].map((i) => <i key={i} className={i <= s ? `seg ${tone}` : "seg"} />)}
                <span className={`t-meta strength-text ${s ? tone : ""}`}>
                  {s === 0 ? "" : S(s === 1 ? "strength_weak" : s === 2 ? "strength_fair" : "strength_strong")}
                </span>
                <span className="t-meta strength-min">{S("lock_min")}</span>
              </div>
              <p className="t-meta lock-helper">{S("lock_helper")}</p>
            </div>
          ) : null}
        </div>
      </div>
      <div className="a-bottombar">
        <Btn block icon="arrow_right" disabled={!ok} onClick={next}>{S("alias_next")}</Btn>
      </div>
    </main>
  );
}

/* ── S5 · Aura ──────────────────────────────────────────────────────────── */

export function AvatarStep() {
  const { draft, setDraft, goTo, register } = useSession();
  const toast = useToast();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  const [aboutError, setAboutError] = useState<string>();
  const [studio, setStudio] = useState(false);
  const preview = useRef<HTMLDivElement>(null);
  const aboutRef = useRef<HTMLInputElement>(null);
  const designed = isSpec(draft.avatar);
  const current = resolveAura(draft.avatar);
  const number = AURAS.findIndex((a) => a.id === current.id) + 1;

  // A random aura to start from (unless one was chosen before).
  useEffect(() => {
    if (!draft.avatar) setDraft({ avatar: AURAS[rnd(AURAS.length)]!.id });
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const bump = () => preview.current?.animate([{ scale: "0.9" }, { scale: "1" }], { duration: 220 });
  const choose = (id: string) => { haptic("tick"); setDraft({ avatar: id }); bump(); };
  const random = () => {
    let next: string;
    do { next = AURAS[rnd(AURAS.length)]!.id; } while (next === draft.avatar);
    choose(next);
  };

  async function enter() {
    if (busy) return;
    setError(undefined);
    setAboutError(undefined);
    setBusy(true);
    try {
      await register();
      haptic();
    } catch (e) {
      setBusy(false);
      haptic();
      if (e instanceof ApiError && (e.code === "ALIAS_TAKEN" || e.code === "ALIAS_NOT_ALLOWED")) {
        toast(S(e.code === "ALIAS_TAKEN" ? "register_alias_taken" : "register_alias_not_allowed"));
      } else if (e instanceof ApiError && e.code === "CONTENT_FLAGGED") {
        setAboutError(errorMessage(e));
        aboutRef.current?.scrollIntoView({ block: "center", behavior: "smooth" });
      } else {
        setError(errorMessage(e));
      }
    }
  }

  return (
    <main className="a-col">
      <header className="a-topbar"><StepBar step={2} total={TOTAL_STEPS} onBack={() => { if (!busy) goTo("alias"); }} /></header>
      <div className="ob-content">
        <h1 className="t-headline">{S("avatar_title")}</h1>
        <p className="t-body ob-sub">{S("avatar_subtitle")}</p>

        <div className="ob-plate av-preview">
          <div className="av-ring" ref={preview}><Aura value={draft.avatar} size={62} /></div>
          <span className="av-preview-text">
            <span className="t-meta-small av-assigned"><i className="k-dot" aria-hidden />{S("avatar_assigned")}</span>
            <span className="t-headline-small av-alias">@{draft.alias}</span>
            <span className="t-meta av-name">{designed ? S("avs_name") : S("avatar_label", number, current.name)}</span>
          </span>
          <IconBtn icon="refresh" label={S("avatar_random")} onClick={random} disabled={busy} className="av-random" />
        </div>
        <Btn kind="outlined" block icon="user" disabled={busy} className="av-design" onClick={() => setStudio(true)}>
          {designed ? S("avs_edit") : S("avs_design")}
        </Btn>

        <SectionLabel>{S("avatar_collection")}</SectionLabel>
        <div className="av-grid" role="radiogroup" aria-label={S("avatar_collection")}>
          {AURAS.map((a, i) => {
            const on = !designed && a.id === current.id;
            return (
              <button key={a.id} type="button" role="radio" aria-checked={on} className={on ? "av-tile on" : "av-tile"}
                      aria-label={S("avatar_label", i + 1, a.name)} onClick={() => choose(a.id)}>
                <Aura value={a.id} size={64} />
                <span className="t-meta av-tile-label">{S("avatar_short", i + 1)}</span>
                {on ? <span className="av-check" aria-hidden><Icon name="check" size={14} /></span> : null}
              </button>
            );
          })}
        </div>

        <SectionLabel>{S("about_label")}</SectionLabel>
        <TextField inputRef={aboutRef} value={draft.about} placeholder={S("about_hint")} aria-label={S("about_label")}
                   maxLength={50} counter={50} autoCapitalize="sentences" inputClass="t-quote" error={aboutError}
                   onChange={(e) => { setAboutError(undefined); setDraft({ about: e.target.value }); }} />

        <div className="ob-plate av-privacy">
          <Icon name="lock" size={18} />
          <p className="t-body-medium">{S("avatar_privacy")}</p>
        </div>
      </div>
      <div className="a-bottombar">
        {error ? <p className="t-meta ob-error" role="alert">{error}</p> : null}
        <Btn block icon="arrow_right" disabled={busy} onClick={() => void enter()}>
          {busy ? S("avatar_creating") : S("avatar_enter")}
        </Btn>
      </div>
      {studio ? (
        <AvatarStudio initial={startingSpec(draft.avatar)} cta={S("avs_use")} onCancel={() => setStudio(false)}
                      onUse={(v) => { setDraft({ avatar: v }); setStudio(false); haptic(); bump(); }} />
      ) : null}
    </main>
  );
}
