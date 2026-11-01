import { useCallback, useEffect, useMemo, useState } from "react";
import { ApiError } from "../lib/api.ts";
import * as B from "../lib/books.ts";
import { count, length, moderationReason } from "../lib/format.ts";
import { back, navigate } from "../lib/router.ts";
import { errorMessage } from "../lib/session.tsx";
import { MOODS, REPORT_REASONS, type Mood } from "../lib/types.ts";
import { usePaged } from "../lib/usePaged.ts";
import { BookCard, BookCover } from "../components/books.tsx";
import { Close, Flag, More, Pencil, Trash } from "../components/icons.tsx";
import { Empty, ListStatus } from "../components/list.tsx";
import { Confirm, Sheet } from "../components/post.tsx";
import { useToast } from "../components/toast.tsx";
import { Button, Header, Notice, Toggle } from "../components/ui.tsx";

/** Error text for Books calls: moderation reason for 451, the server's own sentence for rule errors. */
function bookError(e: unknown): string {
  if (e instanceof ApiError && e.status === 451) return `This can't be published. ${moderationReason(e.message)}`;
  if (e instanceof ApiError && e.status === 422 && e.message && e.message !== "Invalid request body") return e.message;
  return errorMessage(e);
}

/* ── The Books tab ──────────────────────────────────────────────────────── */

const TABS: { key: B.ShelfTab; label: string }[] = [
  { key: "following", label: "Library" }, { key: "updated", label: "Updated" }, { key: "new", label: "New" },
];

export function BooksShelf() {
  const [tab, setTab] = useState<B.ShelfTab>("updated");
  const list = usePaged<B.Book>(async (c) => {
    const r = await B.list(tab, c);
    return { items: r.books, next: r.nextCursor };
  }, (b) => b.bookId, [tab]);

  return (
    <section className="books" aria-label="Books">
      <div className="books-head">
        <div className="stack-xs">
          <h1 className="headline">Books</h1>
          <p className="small">Stories told chapter by chapter</p>
        </div>
        <Button variant="outline" className="btn-small" onClick={() => navigate({ name: "myBooks" })}><Pencil width={16} height={16} /> Your books</Button>
      </div>
      <ContinueRow />
      <div className="segmented three" role="tablist">
        {TABS.map((t) => <button key={t.key} role="tab" aria-selected={tab === t.key} onClick={() => setTab(t.key)}>{t.label}</button>)}
      </div>
      <div className="book-grid">{list.items.map((b) => <BookCard key={b.bookId} book={b} />)}</div>
      <ListStatus state={list.state} error={list.error} count={list.items.length} sentinel={list.sentinel}
                  onRetry={() => void list.reload()} onMore={() => void list.more()}
                  empty={tab === "following"
                    ? <Empty title="Your library is empty" body="Add a book to your library and you'll hear the moment a new chapter comes out." />
                    : (
                      <Empty title="No books yet" body="Be the first to tell a story in chapters.">
                        <Button onClick={() => navigate({ name: "bookSetup" })}>Write a book</Button>
                      </Empty>
                    )} />
    </section>
  );
}

/** Books I'm in the middle of — my place is kept on this phone (ids and numbers only). */
function ContinueRow() {
  const [rows, setRows] = useState<{ place: B.Place; book: B.Book }[]>([]);
  useEffect(() => {
    let stop = false;
    const places = B.places().slice(0, 6);
    void Promise.all(places.map((p) => B.get(p.bookId).then((r) => ({ place: p, book: r.book })).catch((e) => {
      if (e instanceof ApiError && e.status === 404) B.forgetPlace(p.bookId);
      return null;
    }))).then((r) => {
      if (stop) return;
      setRows(r.filter((x): x is { place: B.Place; book: B.Book } => !!x)
        .filter((x) => !(x.book.status === "completed" && x.place.chapter >= x.book.chapterCount && x.place.at > 0.97)));
    });
    return () => { stop = true; };
  }, []);
  if (!rows.length) return null;
  return (
    <div className="continue">
      <span className="tag">Continue reading</span>
      <div className="continue-row scroll-x">
        {rows.map(({ place, book }) => {
          const pct = Math.round(Math.min(1, (place.chapter - 1 + place.at) / Math.max(1, book.chapterCount)) * 100);
          return (
            <button key={book.bookId} className="continue-card" onClick={() => navigate({ name: "read", id: book.bookId, no: place.chapter })}>
              <BookCover cover={book.cover} title={book.title} alias={book.author.alias} className="mini" />
              <span className="stack-xs grow">
                <span className="continue-title clamp-2">{book.title}</span>
                <span className="meta">Chapter {place.chapter} of {book.chapterCount}</span>
                <span className="progress" aria-label={`${pct}%`}><i className={`w${Math.round(pct / 5) * 5}`} /></span>
              </span>
            </button>
          );
        })}
      </div>
    </div>
  );
}

/* ── A book ─────────────────────────────────────────────────────────────── */

export function BookPage({ id }: { id: string }) {
  const [data, setData] = useState<{ book: B.Book; chapters: B.ChapterEntry[] } | null>(null);
  const [gone, setGone] = useState(false);
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);
  const [menu, setMenu] = useState(false);
  const toast = useToast();

  useEffect(() => {
    B.get(id).then(setData).catch((e) => {
      if (e instanceof ApiError && e.status === 404) { setGone(true); B.forgetPlace(id); } else setError(errorMessage(e));
    });
  }, [id]);

  if (gone || error || !data) {
    return (
      <main className="screen">
        <Header title="Book" onBack={back} />
        {gone ? <Empty title="This book isn't available any more." />
          : error ? <Empty title="Couldn't open this book." body={error} />
            : <div className="list-loading"><span className="spinner" aria-label="Loading" /></div>}
      </main>
    );
  }

  const { book } = data;
  const released = data.chapters.filter((c) => !c.state || c.state === "released");
  const place = B.placeOf(book.bookId);
  const at = place?.chapter ?? book.lastReadChapter ?? 0;
  const startNo = released[0]?.chapterNo;
  const allRead = !!at && at >= (released[released.length - 1]?.chapterNo ?? 0) && (place?.at ?? 1) > 0.97;
  const readLabel = !at ? "Start reading" : allRead ? (book.status === "completed" ? "Read again" : `Continue · Chapter ${at}`) : `Continue · Chapter ${at}`;
  const readNo = !at || (allRead && book.status === "completed") ? startNo : at;

  async function toggleLibrary() {
    setBusy(true);
    try {
      const b = await B.follow(book.bookId, !book.following);
      setData((d) => (d ? { ...d, book: b } : d));
      toast(b.following ? "Added to your library — you'll hear about new chapters" : "Taken out of your library");
    } catch (e) {
      toast(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="screen book-page">
      <Header title="Book" onBack={back}
              right={book.isMine ? <button className="icon-btn" aria-label="Open writer's desk" onClick={() => navigate({ name: "desk", id: book.bookId })}><Pencil /></button>
                : <button className="icon-btn" aria-label="More" onClick={() => setMenu(true)}><More /></button>} />
      <div className="book-hero">
        <BookCover cover={book.cover} title={book.title} alias={book.author.alias} className="large" />
        <span className="tag">{book.status === "completed" ? "Complete" : book.status === "draft" ? "Not published" : "Ongoing"}</span>
        <h1 className="headline center-text">{book.title}</h1>
        <button className="link" onClick={() => navigate(book.isMine ? { name: "me" } : { name: "profile", alias: book.author.alias })}>@{book.author.alias}</button>
        {book.blurb ? <p className="quote book-blurb">{book.blurb}</p> : null}
        <span className="meta">{book.chapterCount} chapter{book.chapterCount === 1 ? "" : "s"} · {count(book.followerCount)} in libraries</span>
        <div className="hero-actions">
          {readNo ? <Button onClick={() => navigate({ name: "read", id: book.bookId, no: readNo })}>{readLabel}</Button> : null}
          {book.isMine ? <Button variant="outline" onClick={() => navigate({ name: "desk", id: book.bookId })}>Open writer's desk</Button>
            : <Button variant="outline" busy={busy} aria-pressed={book.following} onClick={toggleLibrary}>{book.following ? "In your library" : "Add to library"}</Button>}
        </div>
      </div>
      <section className="contents" aria-label="Contents">
        <span className="tag">Contents</span>
        {!released.length ? <p className="small">The first chapter hasn't come out yet.</p> : null}
        {released.map((c) => {
          const read = !!at && c.chapterNo < at, current = c.chapterNo === at;
          return (
            <button key={c.chapterNo} className={`contents-row${current ? " current" : ""}${read ? " read" : ""}`}
                    onClick={() => navigate({ name: "read", id: book.bookId, no: c.chapterNo })}>
              <span className="contents-no">{String(c.chapterNo).padStart(2, "0")}</span>
              <span className="stack-xs grow">
                <span className="contents-title">{B.chapterTitle(c.chapterNo, c.title)}</span>
                <span className="meta">
                  {[c.releasedAt ? new Date(c.releasedAt).toLocaleDateString(undefined, { day: "numeric", month: "short" }) : "",
                    c.likeCount ? `${count(c.likeCount)} felt` : "", c.commentCount ? `${count(c.commentCount)} response${c.commentCount === 1 ? "" : "s"}` : ""]
                    .filter(Boolean).join(" · ")}
                </span>
              </span>
              {read ? <span className="read-mark" aria-label="Read">✓</span> : null}
            </button>
          );
        })}
      </section>
      {menu ? (
        <BookMenu book={book} onClose={() => setMenu(false)} />
      ) : null}
    </main>
  );
}

function BookMenu({ book, onClose }: { book: B.Book; onClose: () => void }) {
  const [report, setReport] = useState(false);
  const [reason, setReason] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  const toast = useToast();
  if (!report) {
    return (
      <Sheet title="Options" onClose={onClose}>
        <div className="menu-list">
          <button className="menu-item" onClick={() => setReport(true)}><Flag /> Report this book</button>
          <button className="menu-item" onClick={onClose}><Close /> Cancel</button>
        </div>
      </Sheet>
    );
  }
  return (
    <Sheet title="Report this book" onClose={onClose}>
      <div className="stack">
        <div className="stack-sm"><h2 className="title">Report this book</h2>
          <p className="small">About its title or description. To report one chapter, use the chapter's own menu.</p></div>
        <div className="choice-list" role="radiogroup">
          {REPORT_REASONS.map((r) => (
            <label key={r.key} className={`choice${reason === r.key ? " on" : ""}`}>
              <input type="radio" name="book-reason" checked={reason === r.key} onChange={() => setReason(r.key)} /><span>{r.label}</span>
            </label>
          ))}
        </div>
        <Notice>{error}</Notice>
        <Button block variant="danger" busy={busy} disabled={!reason} onClick={async () => {
          setBusy(true);
          try { await B.report(book.bookId, reason!); B.forgetPlace(book.bookId); toast("Reported. You won't see this book again."); onClose(); back(); }
          catch (e) { setError(errorMessage(e)); setBusy(false); }
        }}>Report and hide</Button>
      </div>
    </Sheet>
  );
}

/* ── Writer: my books ───────────────────────────────────────────────────── */

export function MyBooks() {
  const [books, setBooks] = useState<B.Book[] | null>(null);
  const [error, setError] = useState<string>();
  useEffect(() => { B.mine().then((r) => setBooks(r.books)).catch((e) => setError(errorMessage(e))); }, []);
  return (
    <main className="screen">
      <Header title="Your books" onBack={back} />
      <p className="small settings-lead">Write chapters now, release them when you choose.</p>
      <Button block onClick={() => navigate({ name: "bookSetup" })}>New book</Button>
      <Notice>{error}</Notice>
      {books === null && !error ? <div className="list-loading"><span className="spinner" aria-label="Loading" /></div> : null}
      {books && !books.length ? (
        <Empty title="Your first book" body="Some stories don't fit in one confession. Write them in chapters and let them out one at a time." />
      ) : null}
      <div className="my-books">
        {books?.map((b) => (
          <button key={b.bookId} className="my-book" onClick={() => navigate({ name: "desk", id: b.bookId })}>
            <BookCover cover={b.cover} title={b.title} alias={b.author.alias} className="mini" />
            <span className="stack-xs grow">
              <span className="continue-title">{b.title}</span>
              <span className="small">{b.status === "draft" ? "Not published yet" : b.status === "completed" ? "Complete" : "Ongoing"}</span>
              <span className="meta">
                {[`${b.chapterCount} out`, b.scheduledCount ? `${b.scheduledCount} scheduled` : "", b.draftCount ? `${b.draftCount} drafts` : "",
                  b.nextReleaseAt ? `next ${B.when(b.nextReleaseAt)}` : ""].filter(Boolean).join(" · ")}
              </span>
              {b.hidden ? <span className="small error-text">Hidden after reports</span> : null}
            </span>
          </button>
        ))}
      </div>
    </main>
  );
}

/* ── Writer: book setup (new / edit) ────────────────────────────────────── */

export function BookSetup({ id }: { id?: string }) {
  const [title, setTitle] = useState("");
  const [blurb, setBlurb] = useState("");
  const [cover, setCover] = useState(0);
  const [alias, setAlias] = useState("");
  const [loading, setLoading] = useState(!!id);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  const toast = useToast();

  useEffect(() => {
    if (!id) return;
    B.get(id).then(({ book }) => { setTitle(book.title); setBlurb(book.blurb ?? ""); setCover(book.cover); setAlias(book.author.alias); })
      .catch((e) => setError(errorMessage(e))).finally(() => setLoading(false));
  }, [id]);

  const ok = title.trim().length > 0 && length(title.trim()) <= B.TITLE_MAX && length(blurb.trim()) <= B.BLURB_MAX;

  async function save() {
    setBusy(true);
    setError(undefined);
    try {
      if (id) {
        await B.update(id, { title: title.trim(), blurb: blurb.trim(), cover });
        toast("Saved");
        back();
      } else {
        const r = await B.create({ title: title.trim(), blurb: blurb.trim() || undefined, cover });
        navigate({ name: "desk", id: r.book.bookId }, true);
      }
    } catch (e) {
      setError(bookError(e));
      setBusy(false);
    }
  }

  return (
    <main className="screen">
      <Header title={id ? "Edit book" : "New book"} onBack={back} />
      {loading ? <div className="list-loading"><span className="spinner" aria-label="Loading" /></div> : (
        <div className="stack">
          <div className="setup-preview"><BookCover cover={cover} title={title} alias={alias || undefined} className="large" /></div>
          <span className="tag">Cover</span>
          <div className="cover-picks" role="radiogroup" aria-label="Cover">
            {Array.from({ length: B.COVERS }, (_, i) => (
              <button key={i} role="radio" aria-checked={cover === i} aria-label={`Cover ${i + 1}`}
                      className={`cover-pick${cover === i ? " on" : ""}`} onClick={() => setCover(i)}>
                <BookCover cover={i} title="" className="swatch" />
              </button>
            ))}
          </div>
          <label className="field"><span className="tag">Title</span>
            <input className="input" value={title} maxLength={B.TITLE_MAX + 10} placeholder="Title" onChange={(e) => setTitle(e.target.value)} />
            <span className="meta counter">{length(title)}/{B.TITLE_MAX}</span>
          </label>
          <label className="field"><span className="tag">About</span>
            <textarea className="input" rows={3} value={blurb} maxLength={B.BLURB_MAX + 20} placeholder="What is it about? (optional)"
                      onChange={(e) => setBlurb(e.target.value)} />
            <span className="meta counter">{length(blurb)}/{B.BLURB_MAX}</span>
          </label>
          <Notice>{error}</Notice>
          <Button block busy={busy} disabled={!ok} onClick={save}>{id ? "Save" : "Start writing"}</Button>
        </div>
      )}
    </main>
  );
}

/* ── Writer: the desk ───────────────────────────────────────────────────── */

export function Desk({ id }: { id: string }) {
  const [data, setData] = useState<{ book: B.Book; chapters: B.ChapterEntry[] } | null>(null);
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState<string | null>(null);
  const [confirm, setConfirm] = useState(false);
  const toast = useToast();

  const load = useCallback(() => B.get(id).then(setData).catch((e) => setError(errorMessage(e))), [id]);
  useEffect(() => { void load(); }, [load]);

  if (!data) {
    return (
      <main className="screen"><Header title="Writer's desk" onBack={back} />
        {error ? <Empty title="Couldn't open this book." body={error} /> : <div className="list-loading"><span className="spinner" aria-label="Loading" /></div>}
      </main>
    );
  }
  const { book, chapters } = data;
  const unreleased = chapters.filter((c) => c.state && c.state !== "released").map((c) => c.chapterNo);

  async function move(no: number, dir: -1 | 1) {
    const order = [...unreleased];
    const i = order.indexOf(no), j = i + dir;
    if (i < 0 || j < 0 || j >= order.length) return;
    [order[i], order[j]] = [order[j]!, order[i]!];
    setBusy(`move${no}`);
    setError(undefined);
    try { setData(await B.reorder(id, order)); toast("New order saved"); }
    catch (e) { setError(bookError(e)); }
    finally { setBusy(null); }
  }

  async function complete(on: boolean) {
    setBusy("complete");
    setError(undefined);
    try {
      setData(await B.update(id, { completed: on }));
      toast(on ? "Marked as complete — readers will see “The end”" : "Reopened — you can add more chapters");
    } catch (e) { setError(bookError(e)); }
    finally { setBusy(null); }
  }

  return (
    <main className="screen">
      <Header title="Writer's desk" onBack={back}
              right={<button className="icon-btn" aria-label="Edit book" onClick={() => navigate({ name: "bookSetup", id })}><Pencil /></button>} />
      <div className="desk-head">
        <BookCover cover={book.cover} title={book.title} alias={book.author.alias} className="mini" />
        <span className="stack-xs grow">
          <span className="title">{book.title}</span>
          <span className="meta">{book.status === "draft" ? "Not published yet" : book.status === "completed" ? "Complete" : "Ongoing"}
            {" · "}{book.chapterCount} out{book.scheduledCount ? ` · ${book.scheduledCount} scheduled` : ""}{book.draftCount ? ` · ${book.draftCount} drafts` : ""}</span>
        </span>
      </div>
      {book.hidden ? <Notice>This book is hidden after reports. Only you can see it.</Notice> : null}
      <Notice>{error}</Notice>
      <div className="row desk-actions">
        <Button className="grow" onClick={() => navigate({ name: "chapter", id })}>Add chapter</Button>
        {book.chapterCount ? <Button variant="outline" onClick={() => navigate({ name: "book", id })}>See it as a reader</Button> : null}
      </div>

      <span className="tag group-tag">Chapters</span>
      {!chapters.length ? <p className="small">No chapters yet. Write the first one — you can keep it as a draft until you're ready.</p> : null}
      {unreleased.length > 1 ? <p className="small">Move chapters that aren't out yet with the arrows. Release times stay in place.</p> : null}
      <div className="desk-list">
        {chapters.map((c) => {
          const out = !c.state || c.state === "released";
          const i = unreleased.indexOf(c.chapterNo);
          return (
            <div key={c.chapterNo} className={`desk-row ${c.state ?? "released"}`}>
              <button className="desk-open" onClick={() => navigate({ name: "chapter", id, no: c.chapterNo })}>
                <span className="contents-no">{String(c.chapterNo).padStart(2, "0")}</span>
                <span className="stack-xs grow">
                  <span className="contents-title">{B.chapterTitle(c.chapterNo, c.title)}</span>
                  <span className={`meta state-${c.state ?? "released"}`}>
                    {out ? `Out ${c.releasedAt ? new Date(c.releasedAt).toLocaleDateString(undefined, { day: "numeric", month: "short" }) : ""}`
                      : c.state === "scheduled" ? B.when(c.publishAt) : "Draft"}
                  </span>
                </span>
              </button>
              {!out && unreleased.length > 1 ? (
                <span className="desk-move">
                  <button className="icon-btn small" aria-label={`Move chapter ${c.chapterNo} up`} disabled={i <= 0 || busy !== null}
                          onClick={() => void move(c.chapterNo, -1)}>↑</button>
                  <button className="icon-btn small" aria-label={`Move chapter ${c.chapterNo} down`} disabled={i >= unreleased.length - 1 || busy !== null}
                          onClick={() => void move(c.chapterNo, 1)}>↓</button>
                </span>
              ) : null}
            </div>
          );
        })}
      </div>

      <div className="stack desk-foot">
        {book.chapterCount > 0 ? (
          book.status === "completed"
            ? <Button variant="outline" block busy={busy === "complete"} onClick={() => void complete(false)}>Reopen the book</Button>
            : <Button variant="outline" block busy={busy === "complete"} onClick={() => void complete(true)}>Mark as complete</Button>
        ) : null}
        <Button variant="danger" block onClick={() => setConfirm(true)}><Trash /> Delete book</Button>
      </div>
      {confirm ? (
        <Confirm title="Delete this book?" body="Every chapter, reaction and response goes with it. This can't be undone." confirm="Delete" danger
                 onClose={() => setConfirm(false)}
                 onConfirm={async () => { await B.destroy(id); B.forgetPlace(id); toast("Book deleted"); navigate({ name: "myBooks" }, true); }} />
      ) : null}
    </main>
  );
}

/* ── Writer: a chapter ──────────────────────────────────────────────────── */

export function ChapterEditor({ id, no }: { id: string; no?: number }) {
  const [book, setBook] = useState<{ book: B.Book; chapters: B.ChapterEntry[] } | null>(null);
  const [text, setText] = useState("");
  const [title, setTitle] = useState("");
  const [mood, setMood] = useState<Mood | null>(null);
  const [responses, setResponses] = useState(true);
  const [release, setRelease] = useState<B.Release>("now");
  const [at, setAt] = useState(() => B.localInput(new Date(Date.now() + 24 * 3600_000)));
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string>();
  const [confirm, setConfirm] = useState<"delete" | "discard" | null>(null);
  const toast = useToast();
  const entry = useMemo(() => book?.chapters.find((c) => c.chapterNo === no), [book, no]);
  const released = !!entry && (!entry.state || entry.state === "released");
  const number = no ?? ((book?.chapters.reduce((m, c) => Math.max(m, c.chapterNo), 0) ?? 0) + 1);
  const [initial, setInitial] = useState("");

  useEffect(() => {
    void (async () => {
      try {
        const full = await B.get(id);
        setBook(full);
        if (no) {
          const e = full.chapters.find((c) => c.chapterNo === no);
          const r = await B.chapter(id, no, true);
          setText(r.chapter.text);
          setInitial(r.chapter.text);
          setTitle(e?.title ?? "");
          setMood(r.chapter.mood);
          setResponses(r.chapter.commentsEnabled);
          if (e?.state === "scheduled" && e.publishAt) { setRelease("schedule"); setAt(B.localInput(new Date(e.publishAt))); }
          else if (e?.state === "draft") setRelease("draft");
        }
      } catch (e) {
        setError(errorMessage(e));
      } finally {
        setLoading(false);
      }
    })();
  }, [id, no]);

  const n = length(text.trim());
  const ok = n > 0 && n <= B.CHAPTER_MAX && length(title.trim()) <= B.CHAPTER_TITLE_MAX;
  const dirty = text !== initial;

  function body(): B.ChapterIn | null {
    let publishAt: string | null = null;
    if (release === "schedule") {
      publishAt = B.withOffset(at);
      if (!publishAt) { setError("Pick when it should come out"); return null; }
      if (new Date(publishAt).getTime() <= Date.now() + 30_000) { setError("Pick a time in the future"); return null; }
    }
    return {
      text: text.trim(), title: title.trim() || null, mood, commentsEnabled: mood === "listen" ? false : responses,
      release, publishAt,
    };
  }

  async function save(andNext = false) {
    setError(undefined);
    if (released && no) {
      setBusy("save");
      try {
        await B.editChapter(id, no, { title: title.trim() || undefined, clearTitle: !title.trim(), commentsEnabled: responses });
        toast("Saved");
        back();
      } catch (e) { setError(bookError(e)); setBusy(null); }
      return;
    }
    const b = body();
    if (!b) return;
    setBusy(andNext ? "next" : "save");
    try {
      if (no) await B.editChapter(id, no, { ...b, clearTitle: !b.title, title: b.title ?? undefined });
      else await B.addChapter(id, b);
      toast(release === "now" ? `Chapter ${number} is out` : release === "schedule" ? `Chapter ${number} is scheduled` : `Chapter ${number} saved as a draft`);
      if (andNext) navigate({ name: "chapter", id }, true);
      else back();
    } catch (e) {
      setError(bookError(e));
      setBusy(null);
    }
  }

  if (loading) return <main className="screen"><Header title="Chapter" onBack={back} /><div className="list-loading"><span className="spinner" aria-label="Loading" /></div></main>;

  const action = released ? "Save changes" : no ? "Save changes" : release === "now" ? "Publish" : release === "schedule" ? "Schedule" : "Save draft";
  return (
    <main className="screen compose">
      <Header title={no ? `Chapter ${no}` : `New chapter · ${number}`} onBack={() => (dirty && !released ? setConfirm("discard") : back())}
              right={no ? <button className="icon-btn" aria-label="Delete chapter" onClick={() => setConfirm("delete")}><Trash /></button> : undefined} />
      <div className="paper compose-paper">
        <span className="meta">{book?.book.title}</span>
        <input className="letter-input chapter-title-input" value={title} maxLength={B.CHAPTER_TITLE_MAX + 5}
               placeholder="Chapter title (optional)" aria-label="Chapter title" onChange={(e) => setTitle(e.target.value)} />
        <textarea className="compose-text" value={text} readOnly={released} maxLength={B.CHAPTER_MAX + 50} placeholder="Begin here…"
                  aria-label="Chapter text" onChange={(e) => setText(e.target.value)} />
        <span className={`meta counter${n > B.CHAPTER_MAX ? " error-text" : ""}`}>{n} / {B.CHAPTER_MAX}</span>
      </div>
      {released ? <Notice kind="info">This chapter is out — only its title and responses can change.</Notice> : (
        <>
          <span className="tag group-tag compose-label">Mood</span>
          <div className="chips">
            {MOODS.map((m) => (
              <button key={m.key} className={`chip${mood === m.key ? " on" : ""}`} aria-pressed={mood === m.key}
                      onClick={() => setMood(mood === m.key ? null : m.key)}>{m.label}</button>
            ))}
          </div>
        </>
      )}
      <div className="settings-group compose-label">
        <Toggle label="Allow responses" sub={mood === "listen" ? "Just listen keeps responses off" : "Readers can answer this chapter"}
                on={mood === "listen" ? false : responses} disabled={mood === "listen"} onChange={setResponses} />
      </div>
      {!released ? (
        <section className="settings-group" aria-label="When it comes out">
          <span className="tag group-tag">When it comes out</span>
          <div className="choice-list" role="radiogroup">
            {([["now", "Publish now", "Readers can open it the moment you tap Publish."],
               ["schedule", "Schedule", "Pick a day and time, up to 90 days ahead. Library readers get a push when it's out."],
               ["draft", "Keep as draft", "Only you can see it until you publish or schedule it."]] as const).map(([k, l, sub]) => (
              <label key={k} className={`choice release-choice${release === k ? " on" : ""}`}>
                <input type="radio" name="release" checked={release === k} onChange={() => setRelease(k)} />
                <span className="stack-xs"><span>{l}</span><span className="small">{sub}</span></span>
              </label>
            ))}
          </div>
          {release === "schedule" ? (
            <input className="input" type="datetime-local" value={at} min={B.localInput(new Date())}
                   max={B.localInput(new Date(Date.now() + 90 * 86400_000))} aria-label="Release date and time"
                   onChange={(e) => setAt(e.target.value)} />
          ) : null}
        </section>
      ) : null}
      <Notice>{error}</Notice>
      <div className="stack-sm compose-actions">
        <Button block busy={busy === "save"} disabled={!ok || busy !== null} onClick={() => void save()}>{action}</Button>
        {!no ? <Button variant="ghost" block busy={busy === "next"} disabled={!ok || busy !== null} onClick={() => void save(true)}>Save &amp; write the next one</Button> : null}
      </div>
      {confirm === "delete" && no ? (
        <Confirm title="Delete this chapter?" body="Its reactions and responses go with it." confirm="Delete" danger onClose={() => setConfirm(null)}
                 onConfirm={async () => { await B.deleteChapter(id, no); toast("Chapter deleted"); setConfirm(null); back(); }} />
      ) : null}
      {confirm === "discard" ? (
        <Confirm title="Discard this chapter?" body="What you wrote here hasn't been saved." confirm="Discard" danger onClose={() => setConfirm(null)}
                 onConfirm={async () => { setConfirm(null); back(); }} />
      ) : null}
    </main>
  );
}
