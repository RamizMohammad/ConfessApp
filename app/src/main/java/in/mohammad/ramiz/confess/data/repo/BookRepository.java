package in.mohammad.ramiz.confess.data.repo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.List;

import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiClient;
import in.mohammad.ramiz.confess.data.api.Calls;
import in.mohammad.ramiz.confess.data.api.ErrorCode;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.model.Book;
import in.mohammad.ramiz.confess.data.local.LocalStore;
import in.mohammad.ramiz.confess.data.model.BookDetail;
import in.mohammad.ramiz.confess.data.model.BookDownload;
import in.mohammad.ramiz.confess.data.model.BookPage;
import in.mohammad.ramiz.confess.data.model.BookRequest;
import in.mohammad.ramiz.confess.data.model.ChapterRead;
import in.mohammad.ramiz.confess.data.model.ChapterRequest;
import in.mohammad.ramiz.confess.data.model.ReportRequest;
import retrofit2.Call;

/**
 * Books: serialised confessions. Chapters are confessions underneath, so reacting to or
 * responding on a chapter goes through {@link PostRepository} with its postId.
 * Writer calls answer with the whole book + contents ({@link BookDetail}).
 *
 * Saved on the phone (LocalStore): the shelves' first pages, every book page opened, and —
 * once I start reading a book — the whole book ({@link #download}), so it opens and reads
 * without the network. Released chapters can't be rewritten, so a saved chapter stays right;
 * only its counts go stale, and the reader refreshes those quietly as it opens a chapter.
 */
public final class BookRepository {

    public static final String TAB_UPDATED = "updated";
    public static final String TAB_NEW = "new";
    public static final String TAB_LIBRARY = "following";

    public static final int TITLE_MAX = 60;
    public static final int BLURB_MAX = 200;
    public static final int CHAPTER_MAX = 2000;
    public static final int CHAPTER_TITLE_MAX = 60;
    /** How far ahead a chapter can be scheduled (server: BOOK_MAX_SCHEDULE_DAYS). */
    public static final int SCHEDULE_DAYS = 90;

    // ── Reading ───────────────────────────────────────────────────────────

    public Call<BookPage> list(@NonNull String tab, @Nullable String cursor, @NonNull ApiCallback<BookPage> cb) {
        return Calls.enqueue(ApiClient.api().books(tab, null, cursor, null), cb);
    }

    public Call<BookPage> byAlias(@NonNull String alias, @Nullable String cursor, @NonNull ApiCallback<BookPage> cb) {
        return Calls.enqueue(ApiClient.api().books(TAB_UPDATED, alias, cursor, null), cb);
    }

    /** The book page; a fresh answer is saved on the phone for next time. */
    public Call<BookDetail> book(@NonNull String bookId, @NonNull ApiCallback<BookDetail> cb) {
        return Calls.enqueue(ApiClient.api().book(bookId), new ApiCallback<BookDetail>() {
            @Override
            public void onSuccess(BookDetail d) {
                saveDetail(d);
                cb.onSuccess(d);
            }

            @Override
            public void onError(ApiException e) {
                if (e.is(ErrorCode.NOT_FOUND)) forget(bookId);
                cb.onError(e);
            }
        });
    }

    /** The whole book (released chapters, in full) — saved on the phone by the caller. */
    public Call<BookDownload> download(@NonNull String bookId, @NonNull ApiCallback<BookDownload> cb) {
        return Calls.enqueue(ApiClient.api().downloadBook(bookId), cb);
    }

    // ── Saved on the phone ────────────────────────────────────────────────

    /** Whole books kept for offline reading (the most recently saved ones). */
    private static final int OFFLINE_BOOKS = 20;

    @Nullable
    public static BookDetail savedDetail(@NonNull String bookId) {
        LocalStore l = LocalStore.get();
        BookDetail d = l == null ? null : l.read("book/" + bookId, BookDetail.class);
        return d != null && d.book != null ? d : null;
    }

    public static long detailAge(@NonNull String bookId) {
        LocalStore l = LocalStore.get();
        return l == null ? Long.MAX_VALUE : l.ageMs("book/" + bookId);
    }

    public static void saveDetail(@Nullable BookDetail d) {
        LocalStore l = LocalStore.get();
        if (l == null || d == null || d.book == null || d.book.bookId == null) return;
        l.write("book/" + d.book.bookId, d);
    }

    @Nullable
    public static BookDownload savedBook(@NonNull String bookId) {
        LocalStore l = LocalStore.get();
        BookDownload d = l == null ? null : l.read("book_dl/" + bookId, BookDownload.class);
        return d != null && d.chapters != null ? d : null;
    }

    public static void saveBook(@NonNull String bookId, @NonNull BookDownload d) {
        LocalStore l = LocalStore.get();
        if (l == null) return;
        l.write("book_dl/" + bookId, d);
        l.trim("book_dl/", OFFLINE_BOOKS);
    }

    @Nullable
    public static BookPage savedShelf(@NonNull String tab) {
        LocalStore l = LocalStore.get();
        BookPage p = l == null ? null : l.read("shelf/" + tab, BookPage.class);
        return p != null && p.books != null && !p.books.isEmpty() ? p : null;
    }

    public static void saveShelf(@NonNull String tab, @NonNull BookPage page) {
        LocalStore l = LocalStore.get();
        if (l != null) l.write("shelf/" + tab, page);
    }

    /** Gone, hidden or reported: nothing of it stays on the phone. */
    public static void forget(@NonNull String bookId) {
        LocalStore l = LocalStore.get();
        if (l == null) return;
        l.remove("book/" + bookId);
        l.remove("book_dl/" + bookId);
    }

    public Call<ChapterRead> peekChapter(@NonNull String bookId, int chapterNo, @NonNull ApiCallback<ChapterRead> cb) {
        return Calls.enqueue(ApiClient.api().peekChapter(bookId, chapterNo), cb);
    }

    public Call<ChapterRead> chapter(@NonNull String bookId, int chapterNo, @NonNull ApiCallback<ChapterRead> cb) {
        return Calls.enqueue(ApiClient.api().chapter(bookId, chapterNo), cb);
    }

    /** Add to / take out of my library (a push when a new chapter comes out). */
    public Call<Book> setInLibrary(@NonNull String bookId, boolean on, @NonNull ApiCallback<Book> cb) {
        return Calls.enqueue(on ? ApiClient.api().followBook(bookId) : ApiClient.api().unfollowBook(bookId), cb);
    }

    public Call<JsonObject> report(@NonNull String bookId, @NonNull String reason, @NonNull ApiCallback<JsonObject> cb) {
        return Calls.enqueue(ApiClient.api().reportBook(bookId, new ReportRequest(reason)), cb);
    }

    // ── Writing ───────────────────────────────────────────────────────────

    public Call<BookPage> mine(@NonNull ApiCallback<BookPage> cb) {
        return Calls.enqueue(ApiClient.api().myBooks(), cb);
    }

    /** 422 when 5 books are in progress; 451 CONTENT_FLAGGED for the title / blurb. */
    public Call<BookDetail> create(@NonNull String title, @NonNull String blurb, int cover,
                                   @NonNull ApiCallback<BookDetail> cb) {
        return Calls.enqueue(ApiClient.api().createBook(BookRequest.create(title, blurb, cover)), cb);
    }

    public Call<BookDetail> update(@NonNull String bookId, @NonNull BookRequest body, @NonNull ApiCallback<BookDetail> cb) {
        return Calls.enqueue(ApiClient.api().updateBook(bookId, body), cb);
    }

    public Call<JsonObject> delete(@NonNull String bookId, @NonNull ApiCallback<JsonObject> cb) {
        return Calls.enqueue(ApiClient.api().deleteBook(bookId), cb);
    }

    public Call<BookDetail> addChapter(@NonNull String bookId, @NonNull ChapterRequest chapter,
                                       @NonNull ApiCallback<BookDetail> cb) {
        return addChapters(bookId, Collections.singletonList(chapter), cb);
    }

    /** Up to 20 at a time. 422 when the order rule is broken ("chapters are released in order"). */
    public Call<BookDetail> addChapters(@NonNull String bookId, @NonNull List<ChapterRequest> chapters,
                                        @NonNull ApiCallback<BookDetail> cb) {
        return Calls.enqueue(ApiClient.api().addChapters(bookId, new ChapterRequest.Batch(chapters)), cb);
    }

    public Call<BookDetail> updateChapter(@NonNull String bookId, int chapterNo, @NonNull ChapterRequest change,
                                          @NonNull ApiCallback<BookDetail> cb) {
        return Calls.enqueue(ApiClient.api().updateChapter(bookId, chapterNo, change), cb);
    }

    public Call<BookDetail> deleteChapter(@NonNull String bookId, int chapterNo, @NonNull ApiCallback<BookDetail> cb) {
        return Calls.enqueue(ApiClient.api().deleteChapter(bookId, chapterNo), cb);
    }

    /** Every unreleased chapter number once; release times stay in their slots. */
    public Call<BookDetail> reorder(@NonNull String bookId, @NonNull List<Integer> order, @NonNull ApiCallback<BookDetail> cb) {
        return Calls.enqueue(ApiClient.api().reorderChapters(bookId, new ChapterRequest.Order(order)), cb);
    }
}
