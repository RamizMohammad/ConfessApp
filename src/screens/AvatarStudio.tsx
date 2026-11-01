import { useMemo, useState } from "react";
import { AURAS, resolveAura } from "../lib/auras.ts";
import { DEFAULT_SPEC, encodeSpec, isSpec, PARTS, parseSpec, randomSpec, swatchColor, withPart, type AvatarSpec, type PartKey } from "../lib/avatar.ts";
import { back } from "../lib/router.ts";
import { errorMessage, useSession } from "../lib/session.tsx";
import * as U from "../lib/users.ts";
import { AvatarArt } from "../components/art.tsx";
import { Shuffle } from "../components/icons.tsx";
import { Confirm } from "../components/post.tsx";
import { useToast } from "../components/toast.tsx";
import { BackIcon, Button, Notice, Portal } from "../components/ui.tsx";

/** Where a new design starts: the current one, or the default face on the current aura. */
export function startingSpec(avatar: string | null | undefined): AvatarSpec {
  if (isSpec(avatar)) return parseSpec(avatar);
  const aura = resolveAura(avatar);
  return { ...DEFAULT_SPEC, backdrop: Math.max(0, AURAS.findIndex((a) => a.id === aura.id)) };
}

/**
 * Make a little you — not the real you. A part at a time, every option previewed on your own
 * face; shuffle for a random look. Used from Settings and during sign-up (`onUse`).
 */
export function AvatarStudio({ initial, onUse, onCancel, cta = "Use this avatar" }:
  { initial: AvatarSpec; onUse: (value: string) => Promise<void> | void; onCancel: () => void; cta?: string }) {
  const [spec, setSpec] = useState<AvatarSpec>(initial);
  const [part, setPart] = useState<PartKey>("hair");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  const [discard, setDiscard] = useState(false);
  const value = encodeSpec(spec);
  const changed = value !== encodeSpec(initial);
  const def = PARTS.find((p) => p.key === part)!;
  const options = useMemo(() => Array.from({ length: def.count }, (_, i) => i), [def.count]);

  async function use() {
    setBusy(true);
    setError(undefined);
    try {
      await onUse(value);
    } catch (e) {
      setError(errorMessage(e));
      setBusy(false);
    }
  }

  return (
    <Portal>
      <main className="screen studio" aria-label="Your avatar">
        <div className="topbar">
          <button className="icon-btn" aria-label="Back" onClick={() => (changed ? setDiscard(true) : onCancel())}><BackIcon /></button>
          <h1 className="screen-title">Your avatar</h1>
          <button className="icon-btn" aria-label="Shuffle" onClick={() => setSpec(randomSpec())}><Shuffle /></button>
        </div>
        <div className="studio-preview">
          <AvatarArt value={value} size={168} label="Your avatar" />
        </div>
        <p className="small center-text">Make a little you — not the real you. Illustrated avatars keep Confess anonymous.</p>

        <div className="chips scroll-x studio-parts" role="tablist" aria-label="Parts">
          {PARTS.map((p) => (
            <button key={p.key} role="tab" aria-selected={part === p.key} className={`chip${part === p.key ? " on" : ""}`}
                    onClick={() => setPart(p.key)}>{p.label}</button>
          ))}
        </div>

        <div className={`studio-grid${def.swatch ? " swatches" : ""}`} role="radiogroup" aria-label={def.label}>
          {options.map((i) => {
            const on = spec[part] === i;
            const label = `${def.label} ${i + 1}`;
            return (
              <button key={i} role="radio" aria-checked={on} aria-label={label} className={`studio-option${on ? " on" : ""}`}
                      onClick={() => setSpec(withPart(spec, part, i))}>
                {def.swatch
                  ? <svg viewBox="0 0 40 40" width="40" height="40" aria-hidden><circle cx="20" cy="20" r="18" fill={swatchColor(part, i)} /></svg>
                  : <AvatarArt value={encodeSpec(withPart(spec, part, i))} size={64} />}
              </button>
            );
          })}
        </div>

        <div className="accept-bar">
          <Notice>{error}</Notice>
          <Button block busy={busy} onClick={use}>{cta}</Button>
        </div>
        {discard ? (
          <Confirm title="Discard this avatar?" body="Your changes won't be saved." confirm="Discard" danger
                   onClose={() => setDiscard(false)} onConfirm={async () => { setDiscard(false); onCancel(); }} />
        ) : null}
      </main>
    </Portal>
  );
}

/** /settings/avatar */
export function AvatarStudioScreen() {
  const { me, patchMe } = useSession();
  const toast = useToast();
  if (!me) return null;
  return (
    <AvatarStudio initial={startingSpec(me.avatar)} onCancel={back}
                  onUse={async (v) => {
                    const r = await U.setAvatar(v);
                    patchMe({ avatar: r.avatar });
                    toast("Avatar saved");
                    back();
                  }} />
  );
}
