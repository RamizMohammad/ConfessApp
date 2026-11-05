/**
 * DraftsActivity (activity_drafts + item_draft): DRAFTS · "Only on this phone · encrypted",
 * cards with the words in Newsreader italic, "Edited 3m ago · 120 chars · responses off",
 * bin or swipe away → "Draft deleted" with Undo. Tap one to keep writing it.
 */
import { useEffect, useRef, useState } from "react";
import { deleteDraft, listDrafts, saveDraft, type Draft } from "../lib/drafts.ts";
import { length, relative } from "../lib/format.ts";
import { back, navigate } from "../lib/router.ts";
import { useOnResume } from "../lib/screen.ts";
import { S } from "../lib/strings.ts";
import { useToast } from "../components/toast.tsx";
import { Icon } from "../ui/Icon.tsx";
import { haptic, IconBtn } from "../ui/kit.tsx";
import { tween } from "../ui/motion.ts";

function DraftCard({ d, onOpen, onDelete }: { d: Draft; onOpen: () => void; onDelete: () => void }) {
  const el = useRef<HTMLDivElement>(null);
  const drag = useRef<{ x: number; y: number; dx: number; on: boolean } | null>(null);
  let meta = `${S("drafts_edited", relative(new Date(d.updatedAt).toISOString()).toLowerCase())} · ${S("drafts_chars", length(d.text.trim()))}`;
  if (!d.commentsEnabled) meta += ` · ${S("drafts_responses_off")}`;
  const setX = (x: number) => {
    const c = el.current;
    if (!c) return;
    c.style.setProperty("translate", `${x}px 0`);
    c.style.setProperty("opacity", String(1 - Math.min(1, Math.abs(x) / c.offsetWidth)));
  };
  return (
    <div ref={el} className="draft-card" role="button" tabIndex={0}
         onClick={(e) => { if (!(e.target as Element).closest("button") && !drag.current?.on) onOpen(); }}
         onKeyDown={(e) => { if (e.key === "Enter") onOpen(); }}
         onPointerDown={(e) => { drag.current = { x: e.clientX, y: e.clientY, dx: 0, on: false }; }}
         onPointerMove={(e) => {
           const g = drag.current;
           if (!g) return;
           const dx = e.clientX - g.x, dy = e.clientY - g.y;
           if (!g.on && Math.abs(dx) > 12 && Math.abs(dx) > Math.abs(dy) * 1.5) { g.on = true; el.current?.setPointerCapture(e.pointerId); }
           if (g.on) { g.dx = dx; setX(dx); }
         }}
         onPointerUp={() => {
           const g = drag.current, c = el.current;
           window.setTimeout(() => { drag.current = null; }, 0);
           if (!g?.on || !c) return;
           if (Math.abs(g.dx) > c.offsetWidth / 2) onDelete();
           else { c.style.removeProperty("translate"); c.style.removeProperty("opacity"); void tween(c, { x: [g.dx, 0] }, 180); }
         }}>
      <div className="draft-row">
        <p className="draft-text">{d.text.trim()}</p>
        <button type="button" className="draft-delete" aria-label={S("cd_delete_draft")}
                onPointerDown={(e) => e.stopPropagation()} onClick={(e) => { e.stopPropagation(); onDelete(); }}><Icon name="trash" size={18} /></button>
      </div>
      <span className="t-meta draft-meta">{meta}</span>
    </div>
  );
}

export function Drafts() {
  const [drafts, setDrafts] = useState<Draft[] | null>(null);
  const toast = useToast();
  const entered = useRef(false);
  const list = useRef<HTMLDivElement>(null);
  const load = () => listDrafts().then((all) => setDrafts(all.filter((d) => d.text.trim())));
  useEffect(() => { void load(); }, []);
  useOnResume(() => void load());
  useEffect(() => {
    if (!entered.current && drafts?.length) { entered.current = true; list.current?.classList.add("m-layout-enter"); }
  }, [drafts]);

  async function remove(d: Draft) {
    haptic("tick");
    await deleteDraft(d.id);
    setDrafts((cur) => cur?.filter((x) => x.id !== d.id) ?? null);
    toast(S("drafts_deleted"), { label: S("drafts_undo"), run: () => { void saveDraft(d).then(load); } });
  }

  return (
    <main className="a-screen drafts-a">
      <header className="drafts-top">
        <IconBtn icon="arrow_left" label={S("action_back")} onClick={back} />
        <span className="drafts-titles">
          <span className="t-meta-small drafts-title"><i className="dot-amber" />{S("drafts_title")}</span>
          <span className="t-meta drafts-sub">{S("drafts_summary")}</span>
        </span>
      </header>
      {drafts && !drafts.length ? (
        <div className="feed-state drafts-empty">
          <span className="icon-tile-56"><Icon name="pencil" size={24} /></span>
          <h2 className="t-headline feed-state-title">{S("drafts_empty_title")}</h2>
          <p className="t-body-medium feed-state-body">{S("drafts_empty_body")}</p>
        </div>
      ) : null}
      <div className="drafts-list" ref={list}>
        {drafts?.map((d) => (
          <DraftCard key={d.id} d={d} onDelete={() => void remove(d)} onOpen={() => navigate({ name: "write", draftId: d.id })} />
        ))}
      </div>
    </main>
  );
}
