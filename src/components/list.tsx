import type { ReactNode, RefObject } from "react";
import type { PageState } from "../lib/usePaged.ts";
import { Button } from "./ui.tsx";

export function Empty({ title, body, children }: { title: string; body?: string; children?: ReactNode }) {
  return (
    <div className="empty">
      <h2 className="title">{title}</h2>
      {body ? <p className="small">{body}</p> : null}
      {children}
    </div>
  );
}

/** First load, errors, "load more" and the end of a paged list. */
export function ListStatus({ state, error, count, sentinel, onRetry, onMore, end, empty }:
  { state: PageState; error?: string; count: number; sentinel: RefObject<HTMLDivElement | null>;
    onRetry: () => void; onMore: () => void; end?: ReactNode; empty: ReactNode }) {
  if (state === "loading" && !count) return <div className="list-loading" aria-busy="true"><span className="spinner" aria-label="Loading" /></div>;
  if (state === "error" && !count) {
    return (
      <Empty title="Couldn't load this." body={error}>
        <Button variant="outline" onClick={onRetry}>Try again</Button>
      </Empty>
    );
  }
  if (state === "idle" && !count) return <>{empty}</>;
  return (
    <div ref={sentinel} className="feed-end">
      {state === "more" ? <span className="spinner" aria-label="Loading more" /> : null}
      {state === "moreError" ? <Button variant="ghost" onClick={onMore}>Couldn't load more. Try again</Button> : null}
      {state === "idle" && end ? end : null}
    </div>
  );
}
