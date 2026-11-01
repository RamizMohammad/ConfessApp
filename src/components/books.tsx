import type { Book } from "../lib/books.ts";
import { navigate } from "../lib/router.ts";

/**
 * A book cover like a printed paperback (Android BookCoverView): the story colour as a wash,
 * a darker spine, an inset frame, CONFESS at the head, the title in Newsreader and the writer
 * at the foot. Always 2:3; the type scales with the cover's width (container units).
 */
export function BookCover({ cover, title, alias, className = "" }: { cover: number; title: string; alias?: string; className?: string }) {
  const t = title.trim() || "Your title";
  const size = t.length <= 14 ? "t-s" : t.length <= 28 ? "t-m" : t.length <= 44 ? "t-l" : "t-xl";
  return (
    <div className={`book-cover cover-${cover >= 0 && cover <= 4 ? cover : 0} ${className}`} role="img" aria-label={`${t}${alias ? ` by @${alias}` : ""}`}>
      <span className="bc-spine" aria-hidden />
      <span className="bc-frame" aria-hidden>
        <span className="bc-imprint">CONFESS</span>
        <span className={`bc-title ${size}`}>{t}</span>
        <span className="bc-rule" />
        {alias ? <span className="bc-alias">@{alias}</span> : null}
      </span>
    </div>
  );
}

export function BookCard({ book }: { book: Book }) {
  const status = book.status === "completed" ? "Complete" : book.hasUnread ? "New" : null;
  return (
    <button className="book-card" onClick={() => navigate({ name: "book", id: book.bookId })}
            aria-label={`${book.title} by @${book.author.alias}, ${book.chapterCount} chapter${book.chapterCount === 1 ? "" : "s"}${status ? `, ${status}` : ""}`}>
      <span className="book-card-cover">
        <BookCover cover={book.cover} title={book.title} alias={book.author.alias} />
        {status ? <span className={`book-badge${book.hasUnread ? " new" : ""}`}>{status.toUpperCase()}</span> : null}
      </span>
      <span className="book-card-title clamp-2">{book.title}</span>
      <span className="meta clamp-1">@{book.author.alias} · {book.chapterCount} ch</span>
    </button>
  );
}
