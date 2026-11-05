/**
 * /dev/design — the web twin of the Android app's DesignCheckActivity (ui/dev): every token and
 * kit component on one page, for side-by-side checks against the phone (scripts/replica/).
 * Not linked from anywhere; shows no account data.
 */
import { useEffect, useRef, useState, type ReactNode } from "react";
import { Aura, AURAS } from "../components/Aura.tsx";
import { Lockup } from "../components/ui.tsx";
import { back } from "../lib/router.ts";
import { S } from "../lib/strings.ts";
import { useWide } from "../lib/shell.ts";
import { Icon, iconNames } from "../ui/Icon.tsx";
import { Tour } from "./Tour.tsx";
import {
  Btn, Chip, ChoiceSheet, ConfessDialog, GlideToggle, LinearProgress, PaperNote, Plate, PopMenu, ScopeToggle,
  SectionLabel, SettingRow, SkeletonCard, Snackbar, Spinner, StepBar, Switch, Tag, TextField, Toolbar,
} from "../ui/kit.tsx";

const SWATCHES = ["ink", "surface-raised", "hairline", "paper", "paper-ink", "text-primary", "text-secondary", "text-muted", "amber", "brick", "sage"];

function Section({ n, title, children }: { n?: number; title: string; children: ReactNode }) {
  return (
    <section className="dc-section">
      <SectionLabel>{n != null ? `Section ${n} · ${title}` : title}</SectionLabel>
      {children}
    </section>
  );
}

export default function DesignCheck() {
  const wide = useWide();
  const [sort, setSort] = useState<"latest" | "felt">("latest");
  const [scope, setScope] = useState<"all" | "following">("all");
  const [glide, setGlide] = useState<"posts" | "books" | "stories">("posts");
  const [comments, setComments] = useState(true);
  const [bio, setBio] = useState(false);
  const [accept, setAccept] = useState(false);
  const [alias, setAlias] = useState("quiet_wanderer");
  const [pw, setPw] = useState("candlelight");
  const [aura, setAura] = useState("aura_04");
  const [felt, setFelt] = useState(false);
  const [dialog, setDialog] = useState<null | "plain" | "password" | "danger">(null);
  const [dialogPw, setDialogPw] = useState("");
  const [dialogErr, setDialogErr] = useState<string | null>(null);
  const [sheet, setSheet] = useState<null | "pick" | "confirm">(null);
  const [picked, setPicked] = useState(0);
  const [menu, setMenu] = useState(false);
  const [snack, setSnack] = useState(false);
  const menuAnchor = useRef<HTMLButtonElement>(null);
  const [tour, setTour] = useState(false);

  return (
    <main className="screen dc">
      <Toolbar title="Design check" onBack={back} right={<span className="t-meta dc-wide">{wide ? "wide ≥600" : "phone"}</span>} />

      <Section n={0} title="Design check">
        <div className="dc-hero">
          <Lockup />
          <p className="t-display dc-tag">{S("tagline_line1")}<br /><em>{S("tagline_line2")}</em></p>
          <p className="t-body dc-muted">{S("tagline_sub")}</p>
          <Tag>Anonymous doorway</Tag>
        </div>
      </Section>

      <Section title="Type">
        <p className="t-display">Display · Newsreader 32</p>
        <p className="t-headline">Headline · Newsreader 28</p>
        <p className="t-headline-small">Headline small · Newsreader 22</p>
        <p className="t-confession">“A confession, in italic serif, set loose.”</p>
        <p className="t-body">Body · Manrope 16 — a safe haven for your untold stories.</p>
        <p className="t-body-medium dc-2">Body medium · Manrope 14 — your email is never shown.</p>
        <p className="t-label">Label · Manrope 15 medium</p>
        <p className="t-meta dc-2">@quiet_wanderer · 2h ago · 1.4k felt</p>
        <p className="t-meta-small dc-muted">Step 1 of 3 · meta small caps</p>
      </Section>

      <Section title="Colors">
        <div className="dc-swatches">
          {SWATCHES.map((c) => (
            <div key={c} className="dc-swatch">
              <i className={`dc-chip sw-${c}`} />
              <span className="t-meta">{c.replace(/-/g, "_")}</span>
            </div>
          ))}
        </div>
      </Section>

      <Section title="Buttons">
        <div className="dc-stack">
          <Btn block>Join the world</Btn>
          <Btn block disabled>Continue to alias (disabled)</Btn>
          <Btn block kind="light" icon="google">Continue with Google</Btn>
          <Btn block kind="outlined" icon="fingerprint">Use biometrics</Btn>
          <Btn block kind="danger" icon="trash">Delete account</Btn>
          <div className="dc-row">
            <Btn kind="ghost">Forgot password?</Btn>
            <Btn kind="link">Review guidelines</Btn>
          </div>
          <Btn block busy>Busy</Btn>
        </div>
      </Section>

      <Section title="Inputs">
        <div className="dc-stack">
          <TextField label="ALIAS" value={alias} onChange={(e) => setAlias(e.target.value)} counter={15} helper="3–15 letters, numbers or _" />
          <TextField label="PASSWORD" password value={pw} onChange={(e) => setPw(e.target.value)} autoComplete="off" />
          <TextField label="WITH AN ERROR" value="x" readOnly error={S("error_wrong_password")} />
        </div>
      </Section>

      <Section title="Chips and controls">
        <div className="dc-row wrap">
          <Chip on={sort === "latest"} onClick={() => setSort("latest")}>Latest</Chip>
          <Chip on={sort === "felt"} onClick={() => setSort("felt")}>Most felt</Chip>
          <Chip icon="user">whispering_ash</Chip>
        </div>
        <div className="dc-row">
          <ScopeToggle label="Scope" value={scope} onChange={setScope}
                       options={[{ key: "all", label: S("scope_everyone") }, { key: "following", label: S("scope_following") }]} />
        </div>
        <GlideToggle label="Kind" value={glide} onChange={setGlide}
                     options={[{ key: "posts", label: "Letters" }, { key: "books", label: "Books" }, { key: "stories", label: "Stories" }]} />
        <div className="dc-card">
          <SettingRow icon="message" title="Allow comments" toggle={{ on: comments, onChange: setComments }} />
          <SettingRow icon="fingerprint" title="Biometric unlock" subtitle="Face ID on this iPhone" toggle={{ on: bio, onChange: setBio }} />
          <SettingRow icon="bell" title="Notifications" value="On" chevron onClick={() => setSnack(true)} />
          <SettingRow icon="log_out" title="Sign out" danger onClick={() => setDialog("danger")} />
        </div>
        <label className="dc-check">
          <Switch checked={accept} onChange={setAccept} label="I've read and accept the guidelines" />
          <span className="t-body-medium">I've read and accept the guidelines</span>
        </label>
        <StepBar step={2} total={3} onBack={() => {}} />
      </Section>

      <Section title="Confession card (tap the heart)">
        <PaperNote meta={<span className="dc-meta-row"><span>@quiet_wanderer · 2h</span>
          <button type="button" className={`dc-felt${felt ? " on" : ""}`} aria-pressed={felt} onClick={() => setFelt(!felt)}>
            <Icon name={felt ? "heart_filled" : "heart"} size={20} /> 1.4k
          </button></span>}>
          I still keep the voicemail you left the night before you moved. I've never played it.
        </PaperNote>
        <Plate kind="error" icon="alert">This can't be posted. It breaks our guidelines on private identification.</Plate>
        <SkeletonCard />
      </Section>

      <Section title="Auras (tap to select)">
        <p className="t-meta dc-2">{AURAS.find((a) => a.id === aura)?.name}</p>
        <div className="dc-auras">
          {AURAS.map((a) => (
            <button key={a.id} type="button" className={`dc-aura${a.id === aura ? " on" : ""}`} aria-pressed={a.id === aura}
                    onClick={() => setAura(a.id)} aria-label={a.name}>
              <Aura value={a.id} size={56} />
            </button>
          ))}
        </div>
      </Section>

      <Section title="Icons">
        <div className="dc-icons">
          {iconNames.filter((n) => !n.startsWith("launcher")).map((n) => (
            <div key={n} className="dc-icon"><Icon name={n} /><span className="t-meta-small">{n}</span></div>
          ))}
        </div>
      </Section>

      <Section title="Progress and feedback">
        <div className="dc-row"><Spinner /><div className="grow"><LinearProgress value={0.6} /></div></div>
        <LinearProgress />
        <div className="dc-row wrap">
          <Btn kind="outlined" onClick={() => setSnack(true)}>Snackbar</Btn>
          <Btn kind="outlined" onClick={() => setSheet("pick")}>Sheet</Btn>
          <Btn kind="outlined" onClick={() => setSheet("confirm")}>Report sheet</Btn>
          <Btn kind="outlined" onClick={() => setDialog("plain")}>Dialog</Btn>
          <Btn kind="outlined" onClick={() => { setDialogPw(""); setDialogErr(null); setDialog("password"); }}>Password dialog</Btn>
          <Btn kind="outlined" onClick={() => setTour(true)}>Tour</Btn>
          <button ref={menuAnchor} type="button" className="k-icon-btn" aria-label="More" onClick={() => setMenu(true)}><Icon name="more_vertical" /></button>
        </div>
      </Section>

      {tour ? <Tour onDone={() => setTour(false)} /> : null}
      {snack ? <Snackbar text="Saved to your drafts." action="Undo" onAction={() => setSnack(false)} /> : null}
      {snack ? <SnackTimer done={() => setSnack(false)} /> : null}

      <ChoiceSheet open={sheet === "pick"} onClose={() => setSheet(null)} title="Notification sound" subtitle="What you hear when someone feels your letter."
                   options={[
                     { icon: "bell", label: "Phone's default", sub: "Whatever your phone uses", selected: picked === 0 },
                     { icon: "music", label: "Soft chime", selected: picked === 1 },
                     { icon: "vibrate", label: "Vibrate only", selected: picked === 2 },
                     { icon: "moon", label: "Silent", sub: "Still shows in Activity", selected: picked === 3 },
                   ]} onPick={setPicked} />
      <ChoiceSheet open={sheet === "confirm"} onClose={() => setSheet(null)} title="Report this confession" subtitle="Tell us what's wrong. The writer won't know it was you."
                   options={[{ icon: "flag", label: "Harassment or bullying" }, { icon: "shield", label: "Private information" },
                     { icon: "alert", label: "Self-harm" }, { icon: "ban", label: "Spam" }]}
                   confirm={{ label: "Send report", danger: true, onConfirm: () => setSnack(true) }} />
      <ConfessDialog open={dialog === "plain"} icon="lock" title="Lock Confess?" body="You'll need Face ID or your password to open it again."
                     primary="Lock now" onPrimary={() => setDialog(null)} onClose={() => setDialog(null)} />
      <ConfessDialog open={dialog === "danger"} icon="log_out" title="Sign out?" body="Your letters stay safe. Sign in again any time."
                     primary="Sign out" danger onPrimary={() => setDialog(null)} onClose={() => setDialog(null)} />
      <ConfessDialog open={dialog === "password"} icon="lock" title="Enter your password" body="To turn off the app lock."
                     password={{ hint: S("password_current_hint"), value: dialogPw, onChange: (v) => { setDialogPw(v); setDialogErr(null); } }}
                     error={dialogErr} primary="Continue"
                     onPrimary={() => (dialogPw === "candlelight" ? setDialog(null) : setDialogErr(S("error_wrong_password")))}
                     onClose={() => setDialog(null)} />
      <PopMenu anchor={menuAnchor} open={menu} onClose={() => setMenu(false)} items={[
        { icon: "eye", label: "Preview", onClick: () => setSnack(true) },
        { icon: "pencil", label: "Edit", onClick: () => setSnack(true) },
        { icon: "share", label: "Share", onClick: () => setSnack(true) },
        { icon: "trash", label: "Delete", danger: true, onClick: () => setDialog("danger") },
      ]} />
    </main>
  );
}

function SnackTimer({ done }: { done: () => void }) {
  useEffect(() => {
    const t = window.setTimeout(done, 3000);
    return () => window.clearTimeout(t);
  }, [done]);
  return null;
}
