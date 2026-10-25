import { useEffect, useRef, useState } from "react";
import terms from "../data/terms.json";
import { api } from "../lib/api.ts";
import { errorMessage, useSession } from "../lib/session.tsx";
import { AURAS, Aura, resolveAura } from "../components/Aura.tsx";
import { Button, Notice, TopBar } from "../components/ui.tsx";

const TOTAL = 3;

/* ── S3 · Guidelines ────────────────────────────────────────────────────── */

type Section = { heading: string; terms: string[] };

export function GuidelinesList() {
  const [open, setOpen] = useState(0);
  return (
    <div>
      {(terms as Section[]).map((s, i) => (
        <div className="terms-section" key={s.heading}>
          <button aria-expanded={open === i} onClick={() => setOpen(open === i ? -1 : i)}>
            <span>{s.heading}</span>
            <span className="meta">{open === i ? "−" : `${s.terms.length} points`}</span>
          </button>
          {open === i ? <ul>{s.terms.map((t) => <li key={t}>{t}</li>)}</ul> : null}
        </div>
      ))}
    </div>
  );
}

export function Guidelines() {
  const { goTo, cancelOnboarding } = useSession();
  const [accepted, setAccepted] = useState(false);
  return (
    <main className="screen">
      <TopBar onBack={cancelOnboarding} step={1} total={TOTAL} />
      <div className="stack">
        <div className="stack-sm">
          <span className="tag">Community covenant</span>
          <h1 className="headline">Community guidelines</h1>
          <p className="body">To keep this room kind, safe and anonymous, please read these before you write.</p>
        </div>
        <p className="quote amber">“Here, write as if speaking only to a flame that will never repeat your voice.”</p>
        <GuidelinesList />
      </div>
      <div className="accept-bar stack">
        <label className="check">
          <input type="checkbox" checked={accepted} onChange={(e) => setAccepted(e.target.checked)} />
          <span className="box" aria-hidden />
          <span>I'm 18 or older, and I accept the guidelines</span>
        </label>
        <Button block disabled={!accepted} onClick={() => goTo("alias")}>Continue to alias</Button>
      </div>
    </main>
  );
}

/* ── S4 · Alias ─────────────────────────────────────────────────────────── */

const ALIAS_RE = /^[A-Za-z0-9_]{3,15}$/;

interface Verdict {
  alias: string;
  valid: boolean;
  available: boolean;
  allowed: boolean;
  reason: string | null;
  message: string | null;
  suggestions: string[];
}

type Check = { state: "idle" | "checking" | "offline" } | { state: "done"; v: Verdict };

export function AliasStep() {
  const { draft, setDraft, goTo } = useSession();
  const [value, setValue] = useState(draft.alias);
  const [check, setCheck] = useState<Check>({ state: "idle" });
  const seq = useRef(0);

  useEffect(() => {
    const alias = value.trim();
    if (!ALIAS_RE.test(alias)) {
      setCheck({ state: "idle" });
      return;
    }
    const mine = ++seq.current;
    setCheck({ state: "checking" });
    const t = window.setTimeout(async () => {
      try {
        const v = await api<Verdict>("/users/alias-available", { query: { alias }, raw: true });
        if (mine === seq.current) setCheck({ state: "done", v });
      } catch {
        if (mine === seq.current) setCheck({ state: "offline" });
      }
    }, 350);
    return () => window.clearTimeout(t);
  }, [value]);

  const alias = value.trim();
  const shapeOk = ALIAS_RE.test(alias);
  const ok = check.state === "done" && check.v.valid && check.v.available && check.v.allowed;
  const bad = (check.state === "done" && !ok) || (alias.length >= 3 && !shapeOk);

  let status: { text: string; cls: string } = { text: "3–15 letters, numbers or _", cls: "muted" };
  if (alias && !shapeOk) status = { text: "Only letters, numbers or _ — 3 to 15 of them", cls: "error-text" };
  else if (check.state === "checking") status = { text: "Checking…", cls: "muted" };
  else if (check.state === "offline") status = { text: "Can't check right now", cls: "muted" };
  else if (check.state === "done") {
    if (!check.v.allowed) status = { text: check.v.message ?? "Not allowed", cls: "error-text" };
    else if (!check.v.available) status = { text: "Taken", cls: "error-text" };
    else if (ok) status = { text: "Available", cls: "ok-text" };
  }
  const suggestions = check.state === "done" && !check.v.available ? check.v.suggestions : [];

  return (
    <main className="screen">
      <TopBar onBack={() => goTo("guidelines")} step={2} total={TOTAL} />
      <div className="grow stack">
        <div className="stack-sm">
          <h1 className="headline">Choose your alias.</h1>
          <p className="body">This is the only name the world will ever know you by.</p>
        </div>
        <div className="field">
          <label htmlFor="alias">Alias</label>
          <div className="input-prefix">
            <span aria-hidden>@</span>
            <input id="alias" className={`input${bad ? " bad" : ok ? " good" : ""}`} value={value} maxLength={15}
                   placeholder="your_alias" autoComplete="off" autoCapitalize="none" autoCorrect="off" spellCheck={false}
                   inputMode="text" enterKeyHint="next" aria-describedby="alias-status"
                   onChange={(e) => setValue(e.target.value.replace(/\s/g, ""))}
                   onKeyDown={(e) => { if (e.key === "Enter" && ok) { setDraft({ alias }); goTo("avatar"); } }} />
          </div>
          <span id="alias-status" className={`meta ${status.cls}`} aria-live="polite">{status.text}</span>
        </div>
        {suggestions.length ? (
          <div className="stack-sm">
            <span className="tag">Free right now</span>
            <div className="chips">
              {suggestions.map((s) => <button key={s} className="chip" onClick={() => setValue(s)}>@{s}</button>)}
            </div>
          </div>
        ) : null}
        <Notice kind="info">Alias can't be changed once confirmed. Choose with intention.</Notice>
      </div>
      <Button block disabled={!ok} onClick={() => { setDraft({ alias }); goTo("avatar"); }}>Next: choose aura</Button>
    </main>
  );
}

/* ── S5 · Aura ──────────────────────────────────────────────────────────── */

export function AvatarStep() {
  const { draft, setDraft, goTo, register } = useSession();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  const current = resolveAura(draft.avatar);
  const number = AURAS.findIndex((a) => a.id === current.id) + 1;

  async function enter() {
    setError(undefined);
    setBusy(true);
    try {
      await register();
    } catch (e) {
      setError(errorMessage(e));
      setBusy(false);
    }
  }

  return (
    <main className="screen">
      <TopBar onBack={() => goTo("alias")} step={3} total={TOTAL} />
      <div className="grow stack">
        <div className="stack-sm">
          <h1 className="headline">Pick a face that isn't yours.</h1>
          <p className="body">An abstract aura to carry your words into the dark.</p>
        </div>
        <div className="row">
          <Aura value={current.id} size={72} label={current.name} />
          <div className="stack-sm">
            <span className="tag">Your aura</span>
            <span className="title">Aura {String(number).padStart(2, "0")} · {current.name}</span>
            <span className="meta">@{draft.alias}</span>
          </div>
        </div>
        <div className="aura-grid" role="radiogroup" aria-label="Aura collection (12)">
          {AURAS.map((a) => (
            <button key={a.id} role="radio" aria-checked={a.id === current.id} aria-label={a.name}
                    className={`aura-pick${a.id === current.id ? " on" : ""}`} onClick={() => setDraft({ avatar: a.id })}>
              <Aura value={a.id} size={64} />
            </button>
          ))}
        </div>
        <Button variant="ghost" onClick={() => setDraft({ avatar: AURAS[Math.floor(Math.random() * AURAS.length)]!.id })}>
          Random aura
        </Button>
        <div className="field">
          <label htmlFor="about">A line about you · optional</label>
          <input id="about" className="input" maxLength={50} value={draft.about}
                 placeholder="Writing what I can only say at night…" onChange={(e) => setDraft({ about: e.target.value })} />
        </div>
        <p className="small muted">Your aura isn't linked to your Google account. Everyone else only ever sees this and your alias.</p>
        <Notice>{error}</Notice>
      </div>
      <Button block busy={busy} onClick={enter}>{busy ? "Creating your space…" : "Enter Confess"}</Button>
    </main>
  );
}
