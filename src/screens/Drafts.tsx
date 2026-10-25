import { useEffect, useState } from "react";
import { deleteDraft, listDrafts, saveDraft, type Draft } from "../lib/drafts.ts";
import { ago, length } from "../lib/format.ts";
import { back, navigate } from "../lib/router.ts";
import { Trash } from "../components/icons.tsx";
import { useToast } from "../components/toast.tsx";
import { TopBar } from "../components/ui.tsx";

export function Drafts() {
  const [drafts, setDrafts] = useState<Draft[] | null>(null);
  const toast = useToast();
  useEffect(() => { void listDrafts().then(setDrafts); }, []);

  async function remove(d: Draft) {
    await deleteDraft(d.id);
    setDrafts((cur) => cur?.filter((x) => x.id !== d.id) ?? null);
    toast("Draft deleted", { label: "Undo", run: () => { void saveDraft(d).then(() => listDrafts().then(setDrafts)); } });
  }

  return (
    <main className="screen">
      <TopBar onBack={back} />
      <div className="stack">
        <div className="stack-sm">
          <span className="tag">Drafts</span>
          <p className="small">Only on this device · encrypted · gone when you sign out</p>
        </div>
        {drafts && !drafts.length ? (
          <div className="empty small-empty">
            <p className="title">No drafts</p>
            <p className="small">If you leave a confession half-written, it waits here.</p>
          </div>
        ) : null}
        <div className="list">
          {drafts?.map((d) => (
            <div className="list-row draft-row" key={d.id}>
              <button className="grow draft-open" onClick={() => navigate({ name: "write", draftId: d.id }, true)}>
                <span className="quote clamp">{d.text || (d.gif ? "GIF" : "Poll")}</span>
                <span className="meta">
                  Edited {ago(new Date(d.updatedAt).toISOString())} · {length(d.text)} chars{d.commentsEnabled ? "" : " · responses off"}
                </span>
              </button>
              <button className="icon-btn" aria-label="Delete draft" onClick={() => void remove(d)}><Trash /></button>
            </div>
          ))}
        </div>
      </div>
    </main>
  );
}
