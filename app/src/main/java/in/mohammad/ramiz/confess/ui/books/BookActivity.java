package in.mohammad.ramiz.confess.ui.books;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.snackbar.Snackbar;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.api.ErrorCode;
import in.mohammad.ramiz.confess.data.books.ReadingShelf;
import in.mohammad.ramiz.confess.data.model.Book;
import in.mohammad.ramiz.confess.data.model.BookDetail;
import in.mohammad.ramiz.confess.data.model.ChapterEntry;
import in.mohammad.ramiz.confess.data.model.Me;
import in.mohammad.ramiz.confess.data.repo.BookRepository;
import in.mohammad.ramiz.confess.data.session.SessionManager;
import in.mohammad.ramiz.confess.databinding.ActivityBookBinding;
import in.mohammad.ramiz.confess.ui.base.BaseActivity;
import in.mohammad.ramiz.confess.ui.feed.ReportSheet;
import in.mohammad.ramiz.confess.ui.nav.Nav;
import in.mohammad.ramiz.confess.ui.util.EdgeInsets;
import in.mohammad.ramiz.confess.ui.util.Motion;

/**
 * A book's page: cover, writer, blurb, Start / Continue reading, Add to library and the
 * contents (a tick on chapters I've opened). My own book shows the writer's desk instead
 * of the library button.
 */
public class BookActivity extends BaseActivity implements ReportSheet.Outcome {

    public static final String EXTRA_BOOK_ID = "book_id";

    private ActivityBookBinding b;
    private final BookRepository books = new BookRepository();
    private String bookId;
    @Nullable private BookDetail detail;
    private boolean busy = false;
    private boolean shown = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityBookBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeInsets.padTop(b.topBar);
        EdgeInsets.padBottom(b.scroll, false);

        bookId = getIntent().getStringExtra(EXTRA_BOOK_ID);
        if (bookId == null) {
            finish();
            return;
        }
        b.back.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        b.more.setOnClickListener(this::showMenu);
        b.read.setOnClickListener(v -> {
            Haptics.click(this);
            Nav.reader(this, bookId, 0);
        });
        b.library.setOnClickListener(v -> toggleLibrary());
        getSupportFragmentManager().setFragmentResultListener(ReportSheet.RESULT, this,
                (key, result) -> ReportSheet.deliver(this, result, this));
        BookDetail saved = BookRepository.savedDetail(bookId);   // as it was last time, at once
        if (saved != null) {
            detail = saved;
            render(saved);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (detail != null) render(detail);        // back from the reader: my place, from the phone
        // Fresh contents unless it was fetched seconds ago (my own book: always — the desk edits it).
        if (detail == null || detail.book.isMine || BookRepository.detailAge(bookId) > 30_000) load();
    }

    private void load() {
        books.book(bookId, new ApiCallback<BookDetail>() {
            @Override
            public void onSuccess(BookDetail d) {
                if (isFinishing()) return;
                detail = d;
                render(d);
            }

            @Override
            public void onError(ApiException e) {
                if (isFinishing()) return;
                b.progress.setVisibility(View.GONE);
                if (detail != null) {
                    if (e.isOffline()) return;     // offline: the saved page stands, quietly
                    Snackbar.make(b.root, BookUi.error(BookActivity.this, e), Snackbar.LENGTH_SHORT).show();
                    return;
                }
                if (e.is(ErrorCode.NOT_FOUND)) forget();
                b.error.setVisibility(View.VISIBLE);
                b.error.setText(e.is(ErrorCode.NOT_FOUND) ? getString(R.string.book_gone) : BookUi.error(BookActivity.this, e));
            }
        });
    }

    private void forget() {
        Me me = SessionManager.get().cachedMe();
        ReadingShelf.get(this, me == null ? null : me.id).remove(bookId);
    }

    private void render(@NonNull BookDetail d) {
        Book book = d.book;
        b.progress.setVisibility(View.GONE);
        b.error.setVisibility(View.GONE);
        b.scroll.setVisibility(View.VISIBLE);

        b.cover.bind(book);
        b.title.setText(book.title);
        String alias = book.author != null ? book.author.alias : "";
        b.alias.setText("@" + alias);
        b.avatar.setAvatar(book.author != null ? book.author.avatar : null);
        b.authorRow.setOnClickListener(v -> Nav.profile(this, alias));

        b.status.setText(book.isCompleted() ? R.string.book_status_complete
                : book.isDraft() ? R.string.book_status_draft : R.string.book_status_ongoing);
        String chapters = getResources().getQuantityString(R.plurals.books_chapters, book.chapterCount, book.chapterCount);
        String readers = getResources().getQuantityString(R.plurals.books_readers, book.followerCount, book.followerCount);
        b.stats.setText(chapters + " · " + readers);
        b.blurb.setVisibility(book.blurb == null || book.blurb.isEmpty() ? View.GONE : View.VISIBLE);
        b.blurb.setText(book.blurb);
        b.hiddenNote.setVisibility(Boolean.TRUE.equals(book.hidden) ? View.VISIBLE : View.GONE);

        // Start / Continue / Read again — from my place on this phone, else the server's furthest chapter.
        Me me = SessionManager.get().cachedMe();
        ReadingShelf.Entry here = ReadingShelf.get(this, me == null ? null : me.id).find(book.bookId);
        Integer at = here != null ? Integer.valueOf(here.chapterNo) : book.lastReadChapter;
        int released = 0;
        int lastNo = 0;
        for (ChapterEntry c : d.chapters) {
            if (c.isReleased()) {
                released++;
                lastNo = c.chapterNo;
            }
        }
        b.read.setEnabled(released > 0 || book.isMine);
        if (at == null || released == 0) {
            b.read.setText(R.string.book_start);
        } else if (book.isCompleted() && at >= lastNo && here != null && here.chapterProgress >= 0.98f) {
            b.read.setText(R.string.book_read_again);
        } else {
            b.read.setText(getString(R.string.book_continue, at));
        }

        if (book.isMine) {
            b.library.setText(R.string.book_open_desk);
            b.library.setIconResource(R.drawable.ic_feather);
            b.library.setOnClickListener(v -> Nav.bookEditor(this, book.bookId));
        } else {
            b.library.setText(book.following ? R.string.book_in_library : R.string.book_add_library);
            b.library.setIconResource(book.following ? R.drawable.ic_library_check : R.drawable.ic_library_add);
            b.library.setOnClickListener(v -> toggleLibrary());
        }

        // Contents
        b.contents.removeAllViews();
        LayoutInflater inf = getLayoutInflater();
        int furthest = book.lastReadChapter != null ? book.lastReadChapter : 0;
        boolean any = false;
        for (ChapterEntry c : d.chapters) {
            if (!book.isMine && !c.isReleased()) continue;
            if (any) {
                View rule = new View(this);
                rule.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT, getResources().getDimensionPixelSize(R.dimen.hairline)));
                rule.setBackgroundResource(R.color.hairline);
                b.contents.addView(rule);
            }
            boolean read = c.chapterNo <= furthest || (here != null && c.chapterNo < here.chapterNo);
            final int no = c.chapterNo;
            View row = BookUi.contentsRow(inf, b.contents, c, read, here != null && here.chapterNo == no,
                    v -> {
                        if (c.isReleased()) Nav.reader(this, book.bookId, no);
                        else Nav.chapterEditor(this, book.bookId, no);
                    });
            if (!c.isReleased()) row.setAlpha(0.6f);
            b.contents.addView(row);
            any = true;
        }
        b.contents.setVisibility(any ? View.VISIBLE : View.GONE);
        b.noChapters.setVisibility(any ? View.GONE : View.VISIBLE);

        if (!shown) {
            shown = true;
            Motion.enterStack(b.column);
        }
    }

    private void toggleLibrary() {
        if (detail == null || busy || detail.book.isMine) return;
        busy = true;
        Haptics.click(this);
        boolean on = !detail.book.following;
        books.setInLibrary(bookId, on, new ApiCallback<Book>() {
            @Override
            public void onSuccess(Book book) {
                busy = false;
                if (isFinishing() || detail == null) return;
                detail.book = book;
                render(detail);
                Snackbar.make(b.root, on ? R.string.book_added : R.string.book_removed, Snackbar.LENGTH_SHORT).show();
            }

            @Override
            public void onError(ApiException e) {
                busy = false;
                if (!isFinishing()) Snackbar.make(b.root, BookUi.error(BookActivity.this, e), Snackbar.LENGTH_SHORT).show();
            }
        });
    }

    private void showMenu(@NonNull View anchor) {
        if (detail == null) return;
        if (detail.book.isMine) {
            in.mohammad.ramiz.confess.ui.components.PopMenu.at(anchor).item(R.drawable.ic_feather, R.string.book_open_desk, () -> Nav.bookEditor(this, bookId)).show();
        } else {
            in.mohammad.ramiz.confess.ui.components.PopMenu.at(anchor).danger(R.drawable.ic_flag, R.string.book_report,
                    () -> ReportSheet.showBook(getSupportFragmentManager(), bookId)).show();
        }
    }

    // ── ReportSheet.Outcome ───────────────────────────────────────────────

    @Override
    public void reportPost(@NonNull String postId, @NonNull String reason) { }

    @Override
    public void say(@NonNull String text) {
        Snackbar.make(b.root, text, Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void bookReported(@NonNull String id) {
        forget();
        BookRepository.forget(id);
        finish();                                  // hidden for me now
    }
}
