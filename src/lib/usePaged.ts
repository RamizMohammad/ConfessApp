/** Cursor paging + infinite scroll for any list screen (people, activity, letters…). */
import { useCallback, useEffect, useRef, useState } from "react";
import { errorMessage } from "./session.tsx";

export type PageState = "loading" | "idle" | "more" | "error" | "moreError";

export function usePaged<T>(fetch: (cursor: string | null) => Promise<{ items: T[]; next: string | null }>,
                            key: (t: T) => string, deps: unknown[] = []) {
  const [items, setItems] = useState<T[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [state, setState] = useState<PageState>("loading");
  const [error, setError] = useState<string>();
  const seq = useRef(0);
  const sentinel = useRef<HTMLDivElement>(null);
  const keyOf = useRef(key);
  keyOf.current = key;
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const fetchRef = useCallback(fetch, deps);

  const load = useCallback(async (more: boolean, c: string | null) => {
    const mine = ++seq.current;
    setState(more ? "more" : "loading");
    try {
      const r = await fetchRef(more ? c : null);
      if (mine !== seq.current) return;
      setItems((cur) => (more ? [...cur, ...r.items.filter((x) => !cur.some((y) => keyOf.current(y) === keyOf.current(x)))] : r.items));
      setCursor(r.next);
      setState("idle");
    } catch (e) {
      if (mine !== seq.current) return;
      setError(errorMessage(e));
      setState(more ? "moreError" : "error");
    }
  }, [fetchRef]);

  useEffect(() => {
    void load(false, null);
  }, [load]);

  useEffect(() => {
    const el = sentinel.current;
    if (!el) return;
    const io = new IntersectionObserver((e) => {
      if (e.some((x) => x.isIntersecting) && cursor && state === "idle") void load(true, cursor);
    }, { rootMargin: "600px" });
    io.observe(el);
    return () => io.disconnect();
  }, [cursor, state, load]);

  return {
    items, setItems, state, error, cursor, sentinel,
    reload: () => load(false, null),
    more: () => load(true, cursor),
  };
}
