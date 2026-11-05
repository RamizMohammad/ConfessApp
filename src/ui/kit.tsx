/**
 * The Android app's components, rebuilt for the web at the same sizes (1 dp = 1 CSS px = 1 pt).
 * Each one names the Android source it copies. Styles: src/ui/kit.css (tokens from src/android/).
 *
 * No inline styles anywhere (the CSP forbids them): positions that must be computed are set as
 * CSS variables through the CSSOM (el.style.setProperty), which the CSP allows.
 */
import {
  useCallback, useEffect, useId, useLayoutEffect, useRef, useState,
  type ButtonHTMLAttributes, type InputHTMLAttributes, type PointerEvent as RPointerEvent, type ReactNode, type Ref, type RefObject,
} from "react";
import { createPortal } from "react-dom";
import { Icon, type IconName } from "./Icon.tsx";
import { S } from "../lib/strings.ts";
import { lockScroll } from "../lib/shell.ts";

const cx = (...c: (string | false | null | undefined)[]) => c.filter(Boolean).join(" ");

/** Keeps something on screen while it animates out. */
export function usePresence(open: boolean, exitMs: number): { mounted: boolean; leaving: boolean } {
  const [mounted, setMounted] = useState(open);
  const [leaving, setLeaving] = useState(false);
  useEffect(() => {
    if (open) {
      setMounted(true);
      setLeaving(false);
      return;
    }
    if (!mounted) return;
    setLeaving(true);
    const t = window.setTimeout(() => { setMounted(false); setLeaving(false); }, exitMs);
    return () => window.clearTimeout(t);
  }, [open]); // eslint-disable-line react-hooks/exhaustive-deps
  return { mounted, leaving };
}

/** Light haptic where the platform has one (Android's Haptics.tick / click). iOS Safari has none. */
export function haptic(kind: "tick" | "click" = "click"): void {
  try { navigator.vibrate?.(kind === "tick" ? 5 : 10); } catch { /* not supported */ }
}

// ── Buttons ── styles_components.xml: Widget.Confess.Button(.Light/.Outlined/.Danger/.Ghost/.Link)

export type ButtonKind = "primary" | "light" | "outlined" | "danger" | "ghost" | "link";

export function Btn({ kind = "primary", icon, busy, block, brick, className, children, ...rest }:
  ButtonHTMLAttributes<HTMLButtonElement> & { kind?: ButtonKind; icon?: IconName | ReactNode; busy?: boolean; block?: boolean; brick?: boolean }) {
  const iconSize = kind === "primary" ? 18 : 24;
  return (
    <button type="button" {...rest} disabled={rest.disabled || busy} aria-busy={busy || undefined}
            className={cx("k-btn", `k-btn-${kind}`, block && "k-block", brick && "k-brick", className)}>
      {busy ? <Spinner size={18} /> : typeof icon === "string" ? <Icon name={icon as IconName} size={iconSize} /> : icon}
      {children != null ? <span className="k-btn-label">{children}</span> : null}
    </button>
  );
}

/** Widget.Confess.IconButton: 48 touch target, 24 icon. */
export function IconBtn({ icon, label, className, ...rest }: ButtonHTMLAttributes<HTMLButtonElement> & { icon: IconName; label: string; ref?: Ref<HTMLButtonElement> }) {
  return (
    <button type="button" aria-label={label} {...rest} className={cx("k-icon-btn", className)}>
      <Icon name={icon} />
    </button>
  );
}

// ── Chips, tag, section label ──

/** Widget.Confess.Chip: 36 tall, Meta text; checked = amber tint + amber edge and text. */
export function Chip({ on, onClick, children, icon, disabled }: { on?: boolean; onClick?: () => void; children: ReactNode; icon?: IconName; disabled?: boolean }) {
  return (
    <button type="button" className={cx("k-chip", "t-meta", on && "on")} aria-pressed={on} onClick={onClick} disabled={disabled}>
      {icon ? <Icon name={icon} size={16} /> : null}{children}
    </button>
  );
}

/** Confess.Tag: "• ANONYMOUS DOORWAY". */
export function Tag({ children, dot = true }: { children: ReactNode; dot?: boolean }) {
  return <span className="k-tag t-meta-small">{dot ? <i className="k-dot" aria-hidden /> : null}{children}</span>;
}

/** Confess.SectionLabel: "SECURITY", "TODAY". */
export function SectionLabel({ children }: { children: ReactNode }) {
  return <h2 className="k-section t-meta-small">{children}</h2>;
}

// ── Switch ── Widget.Confess.Switch (Material 3 switch: 52×32 track, 16 → 24 thumb)

export function Switch({ checked, onChange, label, disabled, className }:
  { checked: boolean; onChange?: (on: boolean) => void; label?: string; disabled?: boolean; className?: string }) {
  return (
    <input type="checkbox" role="switch" className={cx("k-switch", className)} checked={checked} disabled={disabled}
           aria-label={label} onChange={(e) => { haptic("tick"); onChange?.(e.target.checked); }} />
  );
}

// ── Setting row ── view_setting_row.xml

export function SettingRow({ icon, title, subtitle, value, toggle, chevron, danger, onClick, disabled }: {
  icon?: IconName; title: ReactNode; subtitle?: ReactNode; value?: ReactNode;
  toggle?: { on: boolean; onChange: (on: boolean) => void; busy?: boolean };
  chevron?: boolean; danger?: boolean; onClick?: () => void; disabled?: boolean;
}) {
  const body = (
    <>
      {icon ? <Icon name={icon} className="k-row-icon" /> : null}
      <span className="k-row-text">
        <span className="k-row-title t-body">{title}</span>
        {subtitle ? <span className="k-row-sub t-meta">{subtitle}</span> : null}
      </span>
      {value ? <span className="k-row-value t-meta">{value}</span> : null}
      {toggle?.busy ? <Spinner size={18} /> : null}
      {toggle ? <span className="k-switch-host" aria-hidden><input type="checkbox" className="k-switch" checked={toggle.on} readOnly tabIndex={-1} /></span> : null}
      {chevron ? <Icon name="chevron_right" size={18} className="k-row-chev" /> : null}
    </>
  );
  if (toggle) {
    return (
      <button type="button" role="switch" aria-checked={toggle.on} disabled={disabled}
              className={cx("k-row", danger && "danger")}
              onClick={() => { haptic("tick"); toggle.onChange(!toggle.on); }}>{body}</button>
    );
  }
  if (!onClick) return <div className={cx("k-row", "static", danger && "danger")}>{body}</div>;
  return <button type="button" disabled={disabled} className={cx("k-row", danger && "danger")} onClick={onClick}>{body}</button>;
}

// ── Step bar ── view_step_bar.xml

export function StepBar({ step, total, onBack }: { step: number; total: number; onBack?: () => void }) {
  return (
    <div className="k-stepbar">
      {onBack ? <IconBtn icon="arrow_left" label={S("action_back")} onClick={onBack} className="k-stepbar-back" /> : null}
      <div className="k-stepbar-mid">
        <span className="t-meta-small k-stepbar-text">{S("onb_step", step, total)}</span>
        <span className="k-stepbar-dots" aria-hidden>
          {Array.from({ length: total }, (_, i) => <i key={i} className={i < step ? "on" : ""} />)}
        </span>
      </div>
    </div>
  );
}

// ── Toolbar ── Widget.Confess.Toolbar (56 tall)

export function Toolbar({ title, onBack, right, big }: { title?: ReactNode; onBack?: () => void; right?: ReactNode; big?: boolean }) {
  return (
    <header className={cx("k-toolbar", big && "big")}>
      {onBack ? <IconBtn icon="arrow_left" label={S("action_back")} onClick={onBack} /> : <span className="k-toolbar-pad" />}
      {title ? <h1 className={cx("k-toolbar-title", big ? "t-headline" : "t-headline-small")}>{title}</h1> : <span className="grow" />}
      <span className="k-toolbar-right">{right}</span>
    </header>
  );
}

// ── Scope toggle ── view_scope_toggle.xml (Everyone / Following)

export function ScopeToggle<T extends string>({ options, value, onChange, label }:
  { options: { key: T; label: string }[]; value: T; onChange: (v: T) => void; label?: string }) {
  return (
    <div className="k-scope" role="radiogroup" aria-label={label}>
      {options.map((o) => (
        <button key={o.key} type="button" role="radio" aria-checked={o.key === value}
                className={cx("k-scope-opt", "t-meta-small", o.key === value && "on")}
                onClick={() => { if (o.key !== value) { haptic("tick"); onChange(o.key); } }}>{o.label}</button>
      ))}
    </div>
  );
}

// ── Glide toggle ── GlideToggleGroup.java: an amber pill glides between segments

export function GlideToggle<T extends string>({ options, value, onChange, label, onClear, disabled }:
  { options: { key: T; label: string; wide?: boolean }[]; value: T | null; onChange: (v: T) => void; label?: string;
    /** selectionRequired=false: tapping the selected one unselects it (the pill fades away). */
    onClear?: () => void; disabled?: boolean }) {
  const track = useRef<HTMLDivElement>(null);
  const pill = useRef<HTMLSpanElement>(null);
  const prev = useRef<{ l: number; r: number } | null>(null);
  const place = useCallback((animate: boolean) => {
    const t = track.current, p = pill.current;
    if (!t || !p) return;
    const i = options.findIndex((o) => o.key === value);
    const btn = i >= 0 ? (t.children[i + 1] as HTMLElement | undefined) : undefined;
    if (!btn) { p.classList.add("off"); prev.current = null; return; }
    const l = btn.offsetLeft, r = t.clientWidth - (btn.offsetLeft + btn.offsetWidth);
    const was = prev.current;
    // Leading edge runs ahead (Android: lead = min(1, f × 1.25)) → it finishes in 1/1.25 of the time.
    const right = was ? l > was.l : true;
    p.classList.toggle("instant", !animate || !was);
    p.classList.toggle("to-right", right);
    p.classList.remove("off");
    p.style.setProperty("--l", `${l}px`);
    p.style.setProperty("--r", `${r}px`);
    prev.current = { l, r };
  }, [options, value]);
  useLayoutEffect(() => { place(true); }, [place]);
  useEffect(() => {
    const t = track.current;
    if (!t || typeof ResizeObserver === "undefined") return;
    const ro = new ResizeObserver(() => place(false));
    ro.observe(t);
    return () => ro.disconnect();
  }, [place]);
  return (
    <div className="k-glide" ref={track} role="radiogroup" aria-label={label}>
      <span className="k-glide-pill off" ref={pill} aria-hidden />
      {options.map((o) => (
        <button key={o.key} type="button" role="radio" aria-checked={o.key === value}
                className={cx("k-glide-opt", "t-button", o.key === value && "on", o.wide && "wide")} disabled={disabled}
                onClick={() => {
                  if (o.key !== value) { haptic("tick"); onChange(o.key); } else if (onClear) { haptic("tick"); onClear(); }
                }}>{o.label}</button>
      ))}
    </div>
  );
}

// ── Progress ── Widget.Confess.Progress.Circular / .Linear

export function Spinner({ size = 28, className }: { size?: number; className?: string }) {
  return (
    <svg className={cx("k-spinner", className)} viewBox="0 0 28 28" width={size} height={size} aria-hidden>
      <circle cx="14" cy="14" r="12" fill="none" strokeWidth="2" strokeLinecap="round" />
    </svg>
  );
}

export function LinearProgress({ value }: { value?: number }) {
  const bar = useRef<HTMLSpanElement>(null);
  useLayoutEffect(() => { bar.current?.style.setProperty("--p", String(Math.max(0, Math.min(1, value ?? 0)))); }, [value]);
  return (
    <span className={cx("k-linear", value == null && "indeterminate")} role="progressbar"
          aria-valuemin={0} aria-valuemax={100} aria-valuenow={value == null ? undefined : Math.round(value * 100)}>
      <span ref={bar} />
    </span>
  );
}

// ── Text field ── Widget.Confess.TextInputLayout (outlined, radius 14, hint off)

export function TextField({ label, error, helper, counter, password, multiline, className, inputRef, prefix, onClear, bad, inputClass, ...rest }:
  Omit<InputHTMLAttributes<HTMLInputElement>, "className" | "prefix"> & {
    label?: string; error?: string | null; helper?: string; counter?: number; password?: boolean; multiline?: boolean;
    className?: string; inputRef?: RefObject<HTMLInputElement | null>;
    /** prefixText (e.g. an amber "@"). */
    prefix?: string;
    /** endIconMode="clear_text": an ✕ while there's text. */
    onClear?: () => void;
    /** Brick edge without an error line (boxStrokeColor = brick). */
    bad?: boolean;
    /** Text appearance of what's typed (default Body). */
    inputClass?: string;
  }) {
  const id = useId();
  const [shown, setShown] = useState(false);
  const len = typeof rest.value === "string" ? rest.value.length : 0;
  const over = counter != null && len > counter;
  return (
    <div className={cx("k-field", (error || bad) && "error", rest.disabled && "disabled", className)}>
      {label ? <label className="k-field-label t-meta-small" htmlFor={id}>{label}</label> : null}
      <div className="k-field-box">
        {prefix ? <span className="k-field-prefix t-body" aria-hidden>{prefix}</span> : null}
        <input id={id} ref={inputRef} {...rest} type={password && !shown ? "password" : rest.type ?? "text"}
               className={cx("k-field-input", inputClass ?? "t-body", prefix && "prefixed")} aria-invalid={error ? true : undefined}
               aria-describedby={error || helper ? `${id}-help` : undefined} />
        {onClear && len ? (
          <button type="button" className="k-field-end" onClick={onClear} aria-label="Clear text">
            <Icon name="close" />
          </button>
        ) : null}
        {password ? (
          <button type="button" className="k-field-end" onClick={() => setShown(!shown)}
                  aria-label={shown ? "Hide password" : "Show password"}>
            <Icon name={shown ? "eye_off" : "eye"} />
          </button>
        ) : null}
      </div>
      {(error || helper || counter != null) && !multiline ? (
        <div className="k-field-foot t-meta">
          <span id={`${id}-help`} className={error ? "k-field-error" : "k-field-helper"} role={error ? "alert" : undefined}>{error || helper}</span>
          {counter != null ? <span className={cx("k-field-counter", over && "over")}>{len}/{counter}</span> : null}
        </div>
      ) : null}
    </div>
  );
}

/** Widget.Confess.CheckBox: M3 checkbox (18 box, 2 stroke), BodyMedium text_primary label. */
export function Checkbox({ checked, onChange, children, disabled }:
  { checked: boolean; onChange: (on: boolean) => void; children: ReactNode; disabled?: boolean }) {
  return (
    <label className={cx("k-check", disabled && "disabled")}>
      <input type="checkbox" checked={checked} disabled={disabled}
             onChange={(e) => { haptic("tick"); onChange(e.target.checked); }} />
      <span className="k-check-box" aria-hidden><Icon name="check" size={14} /></span>
      <span className="t-body-medium k-check-text">{children}</span>
    </label>
  );
}

// ── Layers: scrim, dialog, sheet, menu ──

function Layer({ children }: { children: ReactNode }) {
  return createPortal(children, document.body);
}

function useEscape(on: boolean, close: () => void) {
  useEffect(() => {
    if (!on) return;
    const h = (e: KeyboardEvent) => { if (e.key === "Escape") close(); };
    window.addEventListener("keydown", h);
    return () => window.removeEventListener("keydown", h);
  }, [on, close]);
}

/** Keeps keyboard focus inside an open layer and gives it back afterwards. */
function useFocusTrap(ref: RefObject<HTMLElement | null>, on: boolean) {
  useEffect(() => {
    if (!on) return;
    const before = document.activeElement as HTMLElement | null;
    const el = ref.current;
    const first = el?.querySelector<HTMLElement>("[data-autofocus], input, button:not([disabled])");
    first?.focus({ preventScroll: true });
    const trap = (e: KeyboardEvent) => {
      if (e.key !== "Tab" || !el) return;
      const f = [...el.querySelectorAll<HTMLElement>("button:not([disabled]), input:not([disabled]), [tabindex='0']")];
      if (!f.length) return;
      const a = f[0]!, z = f[f.length - 1]!;
      if (e.shiftKey && document.activeElement === a) { e.preventDefault(); z.focus(); }
      else if (!e.shiftKey && document.activeElement === z) { e.preventDefault(); a.focus(); }
    };
    document.addEventListener("keydown", trap);
    return () => { document.removeEventListener("keydown", trap); before?.focus?.({ preventScroll: true }); };
  }, [on, ref]);
}

export type DialogProps = {
  open: boolean;
  icon?: IconName;
  title: ReactNode;
  body?: ReactNode;
  /** Adds a password field (focused, keyboard up). */
  password?: { hint: string; value: string; onChange: (v: string) => void; autoComplete?: string };
  /** A plain text field instead (dialog_text_input.xml). */
  input?: { hint: string; value: string; onChange: (v: string) => void; maxLength?: number };
  primary: string;
  onPrimary: () => void;
  /** Amber turns brick (turning something off, signing out). */
  danger?: boolean;
  secondary?: string | null;
  onClose: () => void;
  cancelable?: boolean;
  busy?: boolean;
  error?: string | null;
  children?: ReactNode;
};

/** ConfessDialog.java + dialog_confess.xml. */
export function ConfessDialog(p: DialogProps) {
  const { mounted, leaving } = usePresence(p.open, 150);
  const card = useRef<HTMLDivElement>(null);
  const cancelable = p.cancelable ?? true;
  const close = useCallback(() => { if (cancelable && !p.busy) p.onClose(); }, [cancelable, p]);
  useEscape(p.open, close);
  useFocusTrap(card, p.open && mounted);
  useEffect(() => (p.open ? lockScroll() : undefined), [p.open]);
  const shake = () => {
    const c = card.current;
    if (!c) return;
    c.classList.remove("k-shake");
    void c.offsetWidth;
    c.classList.add("k-shake");
  };
  useEffect(() => { if (p.error) shake(); }, [p.error]);
  if (!mounted) return null;
  const field = p.password ?? p.input;
  const submit = () => {
    if (p.password && !p.password.value) { shake(); return; }
    haptic();
    p.onPrimary();
  };
  return (
    <Layer>
      <div className={cx("k-dialog-scrim", leaving && "leaving")} onClick={close} />
      <div className={cx("k-dialog-wrap", leaving && "leaving")} role="presentation">
        <div ref={card} className="k-dialog" role="dialog" aria-modal="true" aria-labelledby="k-dialog-title">
          {p.icon ? <span className={cx("k-dialog-disc", p.danger && "danger")}><Icon name={p.icon} size={26} /></span> : null}
          <h2 id="k-dialog-title" className="k-dialog-title t-headline-small">{p.title}</h2>
          {p.body ? <p className="k-dialog-body t-body-medium">{p.body}</p> : null}
          {p.children}
          {field ? (
            <form className="k-dialog-field" onSubmit={(e) => { e.preventDefault(); submit(); }}>
              <TextField password={!!p.password} placeholder={field.hint} aria-label={field.hint} value={field.value}
                         onChange={(e) => field.onChange(e.target.value)} maxLength={p.input?.maxLength ?? 64}
                         autoComplete={p.password ? p.password.autoComplete ?? "current-password" : "off"}
                         enterKeyHint="done" disabled={p.busy} error={p.error} data-autofocus />
            </form>
          ) : p.error ? <p className="k-dialog-error t-meta" role="alert">{p.error}</p> : null}
          <Btn kind="primary" block brick={p.danger} busy={p.busy} onClick={submit} className="k-dialog-primary">{p.primary}</Btn>
          {p.secondary !== null ? (
            <Btn kind="ghost" block disabled={p.busy} onClick={() => p.onClose()} className="k-dialog-secondary">
              {p.secondary ?? S("action_cancel")}
            </Btn>
          ) : null}
        </div>
      </div>
    </Layer>
  );
}

/** A modal bottom sheet (Widget.Confess.BottomSheet): drag the handle or the top down to close. */
export function Sheet({ open, onClose, children, label, className, handle = true, cancelable = true }:
  { open: boolean; onClose: () => void; children: ReactNode; label?: string; className?: string;
    /** false: the content draws its own (e.g. M3's BottomSheetDragHandleView). */
    handle?: boolean;
    /** false: only the content's buttons close it (Android's setCancelable(false) while working). */
    cancelable?: boolean }) {
  const { mounted, leaving } = usePresence(open, 220);
  const sheet = useRef<HTMLDivElement>(null);
  const close = useCallback(() => { if (cancelable) onClose(); }, [cancelable, onClose]);
  useEscape(open, close);
  useFocusTrap(sheet, open && mounted);
  useEffect(() => (open ? lockScroll() : undefined), [open]);
  // Drag to dismiss, like BottomSheetBehavior: past a third of its height (or a quick flick) closes it.
  const drag = useRef<{ y: number; t: number; dy: number; scroller: HTMLElement | null } | null>(null);
  const onDown = (e: RPointerEvent) => {
    if (e.pointerType === "mouse" && e.button !== 0) return;
    const scroller = (e.target as HTMLElement).closest<HTMLElement>(".k-sheet-scroll");
    if (scroller && scroller.scrollTop > 0) return;
    drag.current = { y: e.clientY, t: performance.now(), dy: 0, scroller };
  };
  const onMove = (e: RPointerEvent) => {
    const d = drag.current, el = sheet.current;
    if (!d || !el) return;
    d.dy = Math.max(0, e.clientY - d.y);
    if (d.dy > 4) {
      if (!el.classList.contains("dragging")) {
        try { el.setPointerCapture(e.pointerId); } catch { /* already released */ }
      }
      el.classList.add("dragging");
      el.style.setProperty("--drag", `${d.dy}px`);
    }
  };
  const onUp = () => {
    const d = drag.current, el = sheet.current;
    drag.current = null;
    if (!d || !el) return;
    el.classList.remove("dragging");
    const fast = d.dy / Math.max(1, performance.now() - d.t) > 0.5;
    if (cancelable && (d.dy > el.offsetHeight / 3 || (fast && d.dy > 24))) close();
    else el.style.setProperty("--drag", "0px");
  };
  if (!mounted) return null;
  return (
    <Layer>
      <div className={cx("k-sheet-scrim", leaving && "leaving")} onClick={close} />
      <div ref={sheet} className={cx("k-sheet", leaving && "leaving", className)} role="dialog" aria-modal="true" aria-label={label}
           onPointerDown={onDown} onPointerMove={onMove} onPointerUp={onUp} onPointerCancel={onUp}>
        {handle ? <span className="k-sheet-handle" aria-hidden /> : null}
        {children}
      </div>
    </Layer>
  );
}

export type ChoiceOption = { icon?: IconName; label: string; sub?: string; selected?: boolean; danger?: boolean };

/** ChoiceSheet.java + sheet_choice.xml + item_choice_option.xml. */
export function ChoiceSheet({ open, onClose, title, subtitle, options, onPick, confirm, onMark }: {
  open: boolean; onClose: () => void; title: string; subtitle?: string; options: ChoiceOption[];
  /** Tap picks and closes (after the check lands). */
  onPick?: (i: number) => void;
  /** Or: tap marks, the confirm button does the rest. */
  confirm?: { label: string; danger?: boolean; onConfirm: (i: number) => void };
  onMark?: (i: number) => void;
}) {
  const initial = options.findIndex((o) => o.selected);
  const [chosen, setChosen] = useState(initial);
  const [bump, setBump] = useState(0);
  useEffect(() => { if (open) { setChosen(options.findIndex((o) => o.selected)); setBump(0); } }, [open]); // eslint-disable-line react-hooks/exhaustive-deps
  const tap = (i: number) => {
    setChosen(i);
    setBump((b) => b + 1);
    if (confirm) { haptic("tick"); onMark?.(i); return; }
    haptic();
    window.setTimeout(() => { onClose(); onPick?.(i); }, 140);
  };
  return (
    <Sheet open={open} onClose={onClose} label={title}>
      <div className="k-choice">
        <h2 className="t-headline-small k-choice-title">{title}</h2>
        {subtitle ? <p className="t-body-medium k-choice-sub">{subtitle}</p> : null}
        <div className="k-sheet-scroll k-choice-list" role={confirm ? "radiogroup" : "menu"}>
          {options.map((o, i) => (
            <button key={i} type="button" role={confirm ? "radio" : "menuitemradio"} aria-checked={i === chosen}
                    className={cx("k-choice-opt", i === chosen && "on", o.danger && "danger")} onClick={() => tap(i)}
                    data-stagger={i}>
              {o.icon ? <span className="k-choice-tile"><Icon name={o.icon} size={20} /></span> : null}
              <span className="k-choice-text">
                <span className="t-body k-choice-label">{o.label}</span>
                {o.sub ? <span className="t-meta k-choice-line">{o.sub}</span> : null}
              </span>
              <Icon name="check" size={22} className={cx("k-choice-check", i === chosen && `on b${bump % 2}`)} />
            </button>
          ))}
        </div>
        <div className="k-choice-actions">
          <Btn kind="outlined" onClick={onClose}>{S("action_cancel")}</Btn>
          {confirm ? (
            <Btn kind="primary" brick={confirm.danger} disabled={chosen < 0}
                 onClick={() => { if (chosen < 0) return; haptic(); onClose(); confirm.onConfirm(chosen); }}>{confirm.label}</Btn>
          ) : null}
        </div>
      </div>
    </Sheet>
  );
}

export type MenuItem = { icon: IconName; label: string; danger?: boolean; onClick: () => void };

/** PopMenu.java + popup_post_menu.xml: opens from the anchor's corner, right-aligned to it. */
export function PopMenu({ anchor, open, onClose, items }:
  { anchor: RefObject<HTMLElement | null>; open: boolean; onClose: () => void; items: MenuItem[] }) {
  const { mounted, leaving } = usePresence(open, 120);
  const menu = useRef<HTMLDivElement>(null);
  useEscape(open, onClose);
  useFocusTrap(menu, open && mounted);
  useLayoutEffect(() => {
    const a = anchor.current, m = menu.current;
    if (!mounted || !a || !m) return;
    const r = a.getBoundingClientRect();
    const vw = document.documentElement.clientWidth, vh = window.visualViewport?.height ?? window.innerHeight;
    const w = m.offsetWidth, h = m.offsetHeight;
    // Android: x = anchor.width - w (right edges line up), below the anchor; above if it won't fit.
    const x = Math.max(8, Math.min(vw - w - 8, r.right - w));
    const below = r.bottom + h + 8 <= vh;
    const y = below ? r.bottom : Math.max(8, r.top - h);
    m.style.setProperty("--x", `${x}px`);
    m.style.setProperty("--y", `${y}px`);
    m.classList.toggle("up", !below);
  }, [mounted, anchor]);
  if (!mounted) return null;
  return (
    <Layer>
      <div className="k-menu-scrim" onClick={onClose} />
      <div ref={menu} className={cx("k-menu", leaving ? "leaving" : "m-popup-in")} role="menu">
        {items.map((it, i) => (
          <button key={i} type="button" role="menuitem" className={cx("k-menu-row", it.danger && "danger")}
                  onClick={() => { onClose(); it.onClick(); }}>
            <Icon name={it.icon} size={20} />
            <span className="t-body-medium">{it.label}</span>
          </button>
        ))}
      </div>
    </Layer>
  );
}

// ── Surfaces ──

/** bg_skeleton_card + blocks: the loading placeholder for a card. */
export function SkeletonCard({ lines = 3, avatar = true }: { lines?: number; avatar?: boolean }) {
  return (
    <div className="k-skel-card" aria-hidden>
      {avatar ? <div className="k-skel-head"><i className="k-skel-circle" /><i className="k-skel-block w40" /></div> : null}
      {Array.from({ length: lines }, (_, i) => <i key={i} className={cx("k-skel-block", i === lines - 1 ? "w60" : "w100")} />)}
    </div>
  );
}

/** The paper note (PaperTone): cream card with the confession on it. */
export function PaperNote({ children, meta, className }: { children: ReactNode; meta?: ReactNode; className?: string }) {
  return (
    <div className={cx("k-paper", className)}>
      <div className="t-quote k-paper-text">{children}</div>
      {meta ? <div className="t-meta k-paper-meta">{meta}</div> : null}
    </div>
  );
}

/** A plate (radius_plate 16, raised + hairline) — error / info blocks. */
export function Plate({ kind = "plain", icon, children }: { kind?: "plain" | "error" | "ok"; icon?: IconName; children: ReactNode }) {
  return (
    <div className={cx("k-plate", kind)} role={kind === "error" ? "alert" : undefined}>
      {icon ? <Icon name={icon} size={20} /> : null}
      <div className="t-body-medium">{children}</div>
    </div>
  );
}

/** Widget.Confess.Snackbar: surface_high, BodyMedium, amber action. */
export function Snackbar({ text, action, onAction }: { text: ReactNode; action?: string; onAction?: () => void }) {
  return (
    <div className="k-snackbar m-slide-up-in" role="status">
      <span className="t-body-medium">{text}</span>
      {action ? <button type="button" className="k-snackbar-action t-button" onClick={onAction}>{action}</button> : null}
    </div>
  );
}
