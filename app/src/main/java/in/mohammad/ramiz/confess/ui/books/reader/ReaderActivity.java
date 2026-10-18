package in.mohammad.ramiz.confess.ui.books.reader;

import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Formats;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.api.ErrorCode;
import in.mohammad.ramiz.confess.data.books.ReadingShelf;
import in.mohammad.ramiz.confess.data.model.Book;
import in.mohammad.ramiz.confess.data.model.BookDetail;
import in.mohammad.ramiz.confess.data.model.BookDownload;
import in.mohammad.ramiz.confess.data.model.ChapterEntry;
import in.mohammad.ramiz.confess.data.model.ChapterRead;
import in.mohammad.ramiz.confess.data.model.LikeState;
import in.mohammad.ramiz.confess.data.model.Me;
import in.mohammad.ramiz.confess.data.model.Post;
import in.mohammad.ramiz.confess.data.repo.BookRepository;
import in.mohammad.ramiz.confess.data.repo.PostRepository;
import in.mohammad.ramiz.confess.data.session.SessionManager;
import in.mohammad.ramiz.confess.databinding.ActivityReaderBinding;
import in.mohammad.ramiz.confess.databinding.ItemReaderEndBinding;
import in.mohammad.ramiz.confess.databinding.ItemReaderTurnBinding;
import in.mohammad.ramiz.confess.databinding.SheetReaderContentsBinding;
import in.mohammad.ramiz.confess.databinding.SheetReaderSettingsBinding;
import in.mohammad.ramiz.confess.ui.base.BaseActivity;
import in.mohammad.ramiz.confess.ui.books.BookUi;
import in.mohammad.ramiz.confess.ui.feed.Reactions;
import in.mohammad.ramiz.confess.ui.nav.Nav;

/**
 * The e-book reader.
 *
 * Pages: each chapter is typeset once (ChapterPages) and shown as whole pages in a PageTurner:
 * the page curls over under the finger (or slides, or just fades — the reader's choice). Tap the
 * right edge / left edge to turn, the middle for the chrome; the volume keys turn pages too.
 * The neighbouring chapter is read ahead near either end, so turning into it is instant.
 *
 * On the phone: once a book is opened here it is kept whole (BookRepository.download, one
 * request), so it opens straight from the phone — also offline — and only the counting
 * ("I reached chapter n") and the fresh reaction counts go to the server, in the background.
 *
 * Chapters flow into each other: after a chapter's last page comes its end page (react,
 * respond, what's next), then a "turn" page that opens the next chapter; swiping back from page
 * 1 opens the previous chapter at its last page.
 *
 * My place (chapter + the character my page starts with) is saved on this phone after every
 * turn, so it survives text-size changes; opening a chapter also tells the server how far I got.
 */
public class ReaderActivity extends BaseActivity implements ReaderAdapter.Binder {

    public static final String EXTRA_BOOK_ID = "book_id";
    /** 0 = carry on where I left off. */
    public static final String EXTRA_CHAPTER = "chapter_no";

    private static final float EDGE = 0.28f;

    private ActivityReaderBinding b;
    private final BookRepository books = new BookRepository();
    private final PostRepository posts = new PostRepository();
    private ReadingShelf shelf;
    private ReaderPrefs prefs;
    @Nullable private ReaderStyle style;

    private String bookId;
    private int askedChapter;
    @Nullable private BookDetail detail;
    @Nullable private ChapterRead chap;
    @Nullable private ChapterPages pages;
    private final Map<Integer, ChapterRead> cache = new HashMap<>();

    private ReaderAdapter adapter;
    /** Chapters being read ahead / read ahead and not yet opened (the server hasn't counted them). */
    private final java.util.Set<Integer> prefetching = new java.util.HashSet<>();
    private final java.util.Set<Integer> peeked = new java.util.HashSet<>();
    /** The whole book as kept on the phone (null until saved). */
    @Nullable private BookDownload offline;
    private boolean offlineChecked = false, downloading = false;
    /** A chapter is open (from the phone or the server); later answers just refresh. */
    private boolean begun = false;
    private int position = RecyclerView.NO_POSITION;
    private boolean opening = false;
    private boolean chrome = false;
    private int statusPx = 0, navPx = 0;
    private boolean insetsKnown = false;
    private int topPadStart, topPadEnd, bottomPadBottom;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityReaderBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        bookId = getIntent().getStringExtra(EXTRA_BOOK_ID);
        askedChapter = getIntent().getIntExtra(EXTRA_CHAPTER, 0);
        if (bookId == null) {
            finish();
            return;
        }
        Me me = SessionManager.get().cachedMe();
        shelf = ReadingShelf.get(this, me == null ? null : me.id);
        prefs = ReaderPrefs.load(this);
        style = new ReaderStyle(this, prefs, 0, 0);

        topPadStart = b.top.getPaddingStart();
        topPadEnd = b.top.getPaddingEnd();
        bottomPadBottom = b.bottom.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(b.root, (v, insets) -> {
            Insets bars = insets.getInsetsIgnoringVisibility(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            b.top.setPaddingRelative(topPadStart, bars.top, topPadEnd, 0);
            b.bottom.setPadding(b.bottom.getPaddingLeft(), b.bottom.getPaddingTop(), b.bottom.getPaddingRight(),
                    bottomPadBottom + bars.bottom);
            boolean changed = !insetsKnown || bars.top != statusPx || bars.bottom != navPx;
            statusPx = bars.top;
            navPx = bars.bottom;
            insetsKnown = true;
            if (changed) {
                style = new ReaderStyle(this, prefs, statusPx, navPx);
                if (chap != null) relayout();
            }
            return insets;
        });

        adapter = new ReaderAdapter(this);
        b.pages.setAdapter(adapter);
        b.pages.setTurnStyle(prefs.turn);
        b.pages.setListener(new PageTurner.Listener() {
            @Override
            public void onSettled(int pos) {
                settle(pos);
            }

            @Override
            public void onDragStart() {
                if (chrome) hideChrome();
            }

            @Override
            public void onTap(float x, float y) {
                ReaderActivity.this.onTap(x, y);
            }
        });
        applyBrightness();

        b.back.setOnClickListener(v -> finish());
        b.contents.setOnClickListener(v -> showContents());
        b.settings.setOnClickListener(v -> showSettings());
        b.retry.setOnClickListener(v -> start());
        b.scrubber.addOnChangeListener((slider, value, fromUser) -> {
            if (!fromUser || pages == null) return;
            int page = Math.round(value) - 1;
            position = adapter.positionOfPage(page);
            b.pages.setPosition(position);
            updateChrome();
        });

        b.top.setVisibility(View.INVISIBLE);
        b.bottom.setVisibility(View.INVISIBLE);
        b.top.setAlpha(0f);
        b.bottom.setAlpha(0f);
        applyTheme();
        immersive(true);
        start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        save();
    }

    // ── Loading ───────────────────────────────────────────────────────────

    private void start() {
        if (!begun) {
            BookDetail saved = detail != null ? detail : BookRepository.savedDetail(bookId);
            if (saved != null) {
                detail = saved;
                begin();                                // straight from the phone
            } else {
                showLoading(null);
            }
        }
        books.book(bookId, new ApiCallback<BookDetail>() {
            @Override
            public void onSuccess(BookDetail d) {
                if (isFinishing()) return;
                detail = d;
                if (!begun) begin();
                keepOffline();
            }

            @Override
            public void onError(ApiException e) {
                if (isFinishing()) return;
                if (e.is(ErrorCode.NOT_FOUND)) {
                    shelf.remove(bookId);
                    showError(getString(R.string.book_gone), false);
                    return;
                }
                if (begun) return;                      // reading from the phone: fine offline
                showError(BookUi.error(ReaderActivity.this, e), true);
            }
        });
    }

    /** Open the chapter to read: the one asked for, else my place, else the furthest, else the first. */
    private void begin() {
        BookDetail d = detail;
        if (d == null) return;
        List<ChapterEntry> out = released();
        if (out.isEmpty() && askedChapter <= 0) {
            showError(getString(R.string.book_no_chapters), false);
            return;
        }
        begun = true;
        ReadingShelf.Entry here = shelf.find(bookId);
        if (askedChapter > 0) {
            openChapter(askedChapter, here != null && here.chapterNo == askedChapter ? here.offset : 0, false);
        } else if (here != null && isReleased(here.chapterNo)) {
            openChapter(here.chapterNo, here.offset, false);
        } else if (d.book.lastReadChapter != null && isReleased(d.book.lastReadChapter)) {
            openChapter(d.book.lastReadChapter, 0, false);
        } else {
            openChapter(out.get(0).chapterNo, 0, false);
        }
    }

    // ── The book on the phone ─────────────────────────────────────────────

    /** Keep the whole book on the phone (one request), unless the saved copy already has every chapter. */
    private void keepOffline() {
        if (downloading || detail == null) return;
        int released = released().size();
        if (released == 0) return;
        BookDownload have = savedBook();
        if (have != null && have.chapters.size() >= released) return;
        downloading = true;
        books.download(bookId, new ApiCallback<BookDownload>() {
            @Override
            public void onSuccess(BookDownload dl) {
                downloading = false;
                if (dl.chapters == null) return;
                BookRepository.saveBook(bookId, dl);
                offline = dl;
            }

            @Override
            public void onError(ApiException e) {
                downloading = false;
            }
        });
    }

    @Nullable
    private BookDownload savedBook() {
        if (offline == null && !offlineChecked) {
            offlineChecked = true;
            offline = BookRepository.savedBook(bookId);
        }
        return offline;
    }

    /** A chapter from the book kept on the phone, with its neighbours from the contents. */
    @Nullable
    private ChapterRead fromPhone(int no) {
        BookDownload dl = savedBook();
        if (dl == null) return null;
        Post found = null;
        for (Post p : dl.chapters) {
            if (p != null && p.chapter != null && p.chapter.chapterNo == no) found = p;
        }
        if (found == null) return null;
        List<Integer> nos = new ArrayList<>();
        List<ChapterEntry> rel = released();
        if (!rel.isEmpty()) {
            for (ChapterEntry c : rel) nos.add(c.chapterNo);
        } else {
            for (Post p : dl.chapters) if (p != null && p.chapter != null) nos.add(p.chapter.chapterNo);
        }
        int i = nos.indexOf(no);
        if (i < 0) return null;                          // not out (any more)
        ChapterRead cr = new ChapterRead();
        cr.book = detail != null ? detail.book : dl.book;
        cr.chapter = found;
        cr.prev = i > 0 ? nos.get(i - 1) : null;
        cr.next = i + 1 < nos.size() ? nos.get(i + 1) : null;
        return cr;
    }

    @NonNull
    private List<ChapterEntry> released() {
        List<ChapterEntry> out = new ArrayList<>();
        if (detail != null) for (ChapterEntry c : detail.chapters) if (c.isReleased()) out.add(c);
        return out;
    }

    private boolean isReleased(int no) {
        for (ChapterEntry c : released()) if (c.chapterNo == no) return true;
        return false;
    }

    /** 1-based place of a chapter among the released ones. */
    private int indexOf(int no) {
        List<ChapterEntry> out = released();
        for (int i = 0; i < out.size(); i++) if (out.get(i).chapterNo == no) return i + 1;
        return Math.max(1, out.size());
    }

    private void openChapter(int no, int offset, boolean atEnd) {
        ChapterRead cached = cache.get(no);
        if (cached != null) {
            show(cached, offset, atEnd);
            return;
        }
        ChapterRead kept = fromPhone(no);
        if (kept != null) {
            cache.put(no, kept);
            peeked.add(no);                              // the server hasn't counted it: show() does
            show(kept, offset, atEnd);
            return;
        }
        if (opening) return;
        opening = true;
        int type = position != RecyclerView.NO_POSITION && position < adapter.getItemCount()
                ? adapter.getItemViewType(position) : -1;
        boolean onTurnPage = type == ReaderAdapter.TURN_NEXT || type == ReaderAdapter.TURN_PREV;
        if (!onTurnPage) showLoading(getString(R.string.reader_loading_chapter, no));
        books.chapter(bookId, no, new ApiCallback<ChapterRead>() {
            @Override
            public void onSuccess(ChapterRead cr) {
                opening = false;
                if (isFinishing()) return;
                cache.put(no, cr);
                if (detail != null && cr.book != null) detail.book = cr.book;
                show(cr, offset, atEnd);
            }

            @Override
            public void onError(ApiException e) {
                opening = false;
                if (isFinishing()) return;
                if (chap != null && onTurnPage) {
                    // Couldn't turn: step back onto this chapter's end / first page.
                    int back = type == ReaderAdapter.TURN_NEXT ? adapter.endPosition() : adapter.positionOfPage(0);
                    if (!b.pages.turn(type == ReaderAdapter.TURN_NEXT ? -1 : 1, b.pages.getHeight())) {
                        position = back;
                        b.pages.setPosition(back);
                    }
                    Toast.makeText(ReaderActivity.this, BookUi.error(ReaderActivity.this, e), Toast.LENGTH_SHORT).show();
                } else {
                    showError(e.is(ErrorCode.NOT_FOUND) ? getString(R.string.reader_error)
                            : BookUi.error(ReaderActivity.this, e), true);
                }
            }
        });
    }

    /** Lay the chapter out into pages and land on the page that shows `offset` (or the last page). */
    private void show(@NonNull ChapterRead cr, int offset, boolean atEnd) {
        chap = cr;
        if (!insetsKnown || b.pages.getWidth() == 0 || b.pages.getHeight() == 0) {
            b.pages.post(() -> show(cr, offset, atEnd));
            return;
        }
        String title = BookUi.chapterTitle(this, cr.chapter.chapter != null ? cr.chapter.chapter.chapterNo : 0, chapterTitleRaw(cr));
        pages = ChapterPages.build(style, cr.chapter.text == null ? "" : cr.chapter.text, title,
                b.pages.getWidth(), b.pages.getHeight());
        adapter.set(pages, style, chapterNo(cr), bookTitle(), title, cr.prev != null, cr.next != null);
        int pos = atEnd ? adapter.positionOfPage(pages.count() - 1) : adapter.positionOfPage(pages.pageOf(offset));
        position = pos;
        b.pages.setPosition(pos);
        int no = chapterNo(cr);
        if (peeked.remove(no)) {
            // Read ahead or opened from the phone: now it's really open — let the server count it
            // (and refresh its reactions and responses).
            books.chapter(bookId, no, new ApiCallback<ChapterRead>() {
                @Override
                public void onSuccess(ChapterRead fresh) {
                    if (isFinishing()) return;
                    if (detail != null && fresh.book != null) detail.book = fresh.book;
                    cache.put(no, fresh);
                    if (chap == null || chapterNo(chap) != no) return;
                    boolean neighbours = (fresh.next == null) != (chap.next == null)
                            || (fresh.prev == null) != (chap.prev == null);
                    chap = fresh;                                   // same text; fresh counts and reactions
                    if (neighbours) relayout();                     // e.g. a new chapter came out meanwhile
                    else adapter.notifyItemChanged(adapter.endPosition());
                }

                @Override
                public void onError(ApiException e) {
                    // Taken down since it was saved: drop the kept copy (it's fetched again without it).
                    if (e.is(ErrorCode.NOT_FOUND)) {
                        BookRepository.forget(bookId);
                        offline = null;
                        offlineChecked = true;
                    }
                }
            });
        }
        hideOverlay();
        updateChrome();
        save();
    }

    private int chapterNo(@NonNull ChapterRead cr) {
        return cr.chapter.chapter != null ? cr.chapter.chapter.chapterNo : 0;
    }

    @Nullable
    private String chapterTitleRaw(@NonNull ChapterRead cr) {
        return cr.chapter.chapter != null ? cr.chapter.chapter.title : null;
    }

    @NonNull
    private String bookTitle() {
        if (detail != null) return detail.book.title;
        return chap != null && chap.book != null ? chap.book.title : "";
    }

    /** Settings changed / window insets changed: new pages, same place. */
    private void relayout() {
        if (chap == null || pages == null) return;
        int type = position != RecyclerView.NO_POSITION && position < adapter.getItemCount()
                ? adapter.getItemViewType(position) : ReaderAdapter.TEXT;
        if (type == ReaderAdapter.END) {
            show(chap, Integer.MAX_VALUE, true);
            b.pages.setPosition(adapter.endPosition());
            position = adapter.endPosition();
            updateChrome();
            return;
        }
        int page = Math.max(0, adapter.pageAt(position));
        show(chap, pages.offsetOf(page), false);
    }

    // ── Turning pages ─────────────────────────────────────────────────────

    /** A turn landed: where are we now? (A chapter-turn page opens that chapter.) */
    private void settle(int pos) {
        if (chap == null || pos >= adapter.getItemCount()) return;
        position = pos;
        int type = adapter.getItemViewType(pos);
        if (type == ReaderAdapter.TURN_NEXT && chap.next != null) {
            openChapter(chap.next, 0, false);
        } else if (type == ReaderAdapter.TURN_PREV && chap.prev != null) {
            openChapter(chap.prev, 0, true);
        } else {
            updateChrome();
            save();
            // Close to either end of the chapter: fetch the neighbour now, so the turn into it is instant.
            int page = adapter.pageAt(pos);
            if (chap.next != null && (type == ReaderAdapter.END || page >= adapter.pageCount() - 2)) prefetch(chap.next);
            if (chap.prev != null && page == 0) prefetch(chap.prev);
        }
    }

    private void prefetch(int no) {
        if (cache.containsKey(no) || fromPhone(no) != null || !prefetching.add(no)) return;
        books.peekChapter(bookId, no, new ApiCallback<ChapterRead>() {
            @Override
            public void onSuccess(ChapterRead cr) {
                prefetching.remove(no);
                if (cache.containsKey(no)) return;
                cache.put(no, cr);
                peeked.add(no);
            }

            @Override
            public void onError(ApiException e) {
                prefetching.remove(no);
            }
        });
    }

    private void turn(int dir) {
        turn(dir, b.pages.getHeight());
    }

    private void turn(int dir, float y) {
        if (adapter.getItemCount() == 0) return;
        b.pages.turn(dir, y);
    }

    private void onTap(float x, float y) {
        int pos = b.pages.position();
        if (pos < adapter.getItemCount() && adapter.getItemViewType(pos) == ReaderAdapter.END) return;
        if (chrome) {
            hideChrome();
            return;
        }
        float w = b.pages.getWidth();
        if (x < w * EDGE) turn(-1, y);
        else if (x > w * (1 - EDGE)) turn(1, y);
        else showChrome();
    }

    private void applyBrightness() {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = prefs.brightness < 0 ? WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                : Math.max(0.01f, prefs.brightness / 100f);
        getWindow().setAttributes(lp);
    }

    /** The phone's brightness, 0–100 (where the slider starts when "use phone brightness" is turned off). */
    private int systemBrightness() {
        try {
            return Math.round(android.provider.Settings.System.getInt(getContentResolver(),
                    android.provider.Settings.System.SCREEN_BRIGHTNESS) * 100f / 255f);
        } catch (Exception e) {
            return 50;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (prefs.volumeKeys && (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)) {
            turn(keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ? 1 : -1);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (prefs.volumeKeys && (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    // ── My place ──────────────────────────────────────────────────────────

    private void save() {
        if (chap == null || pages == null || detail == null || position == RecyclerView.NO_POSITION
                || position >= adapter.getItemCount()) return;
        int type = adapter.getItemViewType(position);
        int page;
        float progress;
        if (type == ReaderAdapter.END) {
            page = pages.count() - 1;
            progress = 1f;
        } else if (type == ReaderAdapter.TEXT) {
            page = adapter.pageAt(position);
            progress = pages.count() <= 1 ? 0.5f : page / (float) pages.count();
        } else {
            return;
        }
        int no = chapterNo(chap);
        shelf.save(detail.book, no, chapterTitleRaw(chap), indexOf(no), pages.offsetOf(page), progress);
    }

    // ── Chrome ────────────────────────────────────────────────────────────

    private void updateChrome() {
        if (chap == null || pages == null) return;
        int no = chapterNo(chap);
        b.bookTitle.setText(bookTitle().toUpperCase(Locale.getDefault()));
        b.chapterTitle.setText(BookUi.chapterTitle(this, no, chapterTitleRaw(chap)));
        int count = pages.count();
        int page = adapter.pageAt(position);
        if (page < 0) page = count - 1;
        String where = getString(R.string.reader_position, page + 1, count, indexOf(no), Math.max(indexOf(no), released().size()));
        boolean onText = position != RecyclerView.NO_POSITION && position < adapter.getItemCount()
                && adapter.getItemViewType(position) == ReaderAdapter.TEXT;
        int words = onText ? pages.wordsFrom(pages.offsetOf(page)) : 0;
        if (words > 0) {
            where += "\n" + (words < 200 ? getString(R.string.reader_time_left_soon)
                    : getString(R.string.reader_time_left, Math.round(words / 230f)));
        }
        b.position.setText(where);
        if (count > 1) {
            b.scrubber.setVisibility(View.VISIBLE);
            b.scrubber.setValueFrom(1);
            b.scrubber.setValueTo(count);
            b.scrubber.setValue(Math.max(1, Math.min(count, page + 1)));
        } else {
            b.scrubber.setVisibility(View.GONE);
        }
    }

    private void showChrome() {
        chrome = true;
        updateChrome();
        fade(b.top, true, -1);
        fade(b.bottom, true, 1);
        immersive(false);
    }

    private void hideChrome() {
        chrome = false;
        fade(b.top, false, -1);
        fade(b.bottom, false, 1);
        immersive(true);
    }

    private void fade(@NonNull View v, boolean show, int dir) {
        v.animate().cancel();
        if (show) {
            v.setVisibility(View.VISIBLE);
            v.setTranslationY(dir * v.getHeight() * 0.25f);
            v.animate().alpha(1f).translationY(0).setDuration(180).start();
        } else {
            v.animate().alpha(0f).translationY(dir * v.getHeight() * 0.25f).setDuration(160)
                    .withEndAction(() -> v.setVisibility(View.INVISIBLE)).start();
        }
    }

    private void immersive(boolean on) {
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        c.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        boolean light = style != null && !style.dark;
        c.setAppearanceLightStatusBars(light);
        c.setAppearanceLightNavigationBars(light);
        if (on) c.hide(WindowInsetsCompat.Type.systemBars());
        else c.show(WindowInsetsCompat.Type.systemBars());
    }

    private void applyTheme() {
        ReaderStyle s = style;
        if (s == null) return;
        b.root.setBackgroundColor(s.background);
        b.pages.setPaperColor(s.background);
        int bar = ColorUtils.setAlphaComponent(s.background, 0xF5);
        b.top.setBackgroundColor(bar);
        b.bottom.setBackgroundColor(bar);
        b.bookTitle.setTextColor(s.meta);
        b.chapterTitle.setTextColor(s.ink);
        b.position.setTextColor(s.meta);
        ColorStateList ink = ColorStateList.valueOf(s.ink);
        for (MaterialButton m : new MaterialButton[]{b.back, b.contents, b.settings}) m.setIconTint(ink);
        b.scrubber.setTrackActiveTintList(ColorStateList.valueOf(s.accent));
        b.scrubber.setTrackInactiveTintList(ColorStateList.valueOf(ColorUtils.setAlphaComponent(s.meta, 80)));
        b.scrubber.setThumbTintList(ColorStateList.valueOf(s.accent));
        b.scrubber.setHaloTintList(ColorStateList.valueOf(ColorUtils.setAlphaComponent(s.accent, 40)));
        b.message.setTextColor(s.ink);
        b.spinner.setIndicatorColor(s.accent);
        b.retry.setTextColor(s.ink);
        immersive(!chrome);
    }

    // ── Overlay ───────────────────────────────────────────────────────────

    private void showLoading(@Nullable String text) {
        b.overlay.setVisibility(View.VISIBLE);
        b.overlay.setBackgroundColor(style != null ? style.background : 0);
        b.spinner.setVisibility(View.VISIBLE);
        b.message.setVisibility(text != null ? View.VISIBLE : View.GONE);
        b.message.setText(text);
        b.retry.setVisibility(View.GONE);
    }

    private void showError(@NonNull String text, boolean retry) {
        b.overlay.setVisibility(View.VISIBLE);
        b.overlay.setBackgroundColor(style != null ? style.background : 0);
        b.spinner.setVisibility(View.GONE);
        b.message.setVisibility(View.VISIBLE);
        b.message.setText(text);
        b.retry.setVisibility(retry ? View.VISIBLE : View.GONE);
        if (!chrome) showChrome();
    }

    private void hideOverlay() {
        b.overlay.setVisibility(View.GONE);
    }

    // ── End-of-chapter + turn pages (ReaderAdapter.Binder) ────────────────

    @Override
    public void bindEnd(@NonNull ItemReaderEndBinding e) {
        ReaderStyle s = style;
        if (s == null || chap == null) return;
        Post p = chap.chapter;
        int no = chapterNo(chap);
        e.root.setBackgroundColor(s.background);
        e.ornament.setTextColor(s.accent);
        e.endLabel.setTextColor(s.meta);
        e.endLabel.setText(getString(R.string.reader_end_label, no));
        e.endTitle.setTextColor(s.ink);
        e.endTitle.setText(BookUi.chapterTitle(this, no, chapterTitleRaw(chap)));
        e.felt.setTextColor(s.meta);
        e.felt.setText(p.likeCount > 0 ? getString(R.string.reader_felt_count, Formats.count(p.likeCount))
                : getString(R.string.reader_felt_none));
        bindReactions(e.reactions, p, s);

        boolean canRespond = p.commentsEnabled || p.commentCount > 0;
        e.responses.setVisibility(canRespond ? View.VISIBLE : View.GONE);
        e.responses.setText(p.commentCount > 0 ? getString(R.string.reader_responses, Formats.count(p.commentCount))
                : getString(R.string.reader_respond));
        e.responses.setTextColor(s.ink);
        e.responses.setIconTint(ColorStateList.valueOf(s.ink));
        e.responses.setOnClickListener(v -> Nav.post(this, p.postId, true));
        e.rule.setBackgroundColor(ColorUtils.setAlphaComponent(s.meta, 90));

        Book book = detail != null ? detail.book : chap.book;
        e.nextLabel.setTextColor(s.accent);
        e.nextTitle.setTextColor(s.ink);
        e.nextBody.setTextColor(s.meta);
        e.library.setTextColor(s.ink);
        e.library.setIconTint(ColorStateList.valueOf(s.ink));
        e.library.setVisibility(View.GONE);
        e.nextBody.setVisibility(View.GONE);
        e.nextLabel.setVisibility(View.VISIBLE);
        if (chap.next != null) {
            e.nextLabel.setText(R.string.reader_next_label);
            e.nextTitle.setText(getString(R.string.chapter_default, chap.next) + " · " + titleOf(chap.next));
            e.next.setVisibility(View.VISIBLE);
            e.next.setText(R.string.reader_turn_page);
            e.next.setIconResource(R.drawable.ic_arrow_right);
            e.next.setOnClickListener(v -> turn(1));
        } else if (book != null && book.isCompleted()) {
            e.nextLabel.setText(R.string.reader_the_end);
            e.nextTitle.setText(R.string.reader_the_end_body);
            e.next.setVisibility(View.VISIBLE);
            e.next.setText(R.string.reader_back_to_book);
            e.next.setIconResource(R.drawable.ic_book_open);
            e.next.setOnClickListener(v -> finish());
        } else {
            e.nextLabel.setVisibility(View.GONE);
            e.nextTitle.setText(R.string.reader_caught_up);
            e.nextBody.setVisibility(View.VISIBLE);
            boolean offer = book != null && !book.isMine && !book.following;
            e.nextBody.setText(offer ? getString(R.string.reader_caught_up_body) + "\n"
                    + getString(R.string.reader_caught_up_library) : getString(R.string.reader_caught_up_body));
            e.next.setVisibility(View.GONE);
            if (offer) {
                e.library.setVisibility(View.VISIBLE);
                e.library.setOnClickListener(v -> addToLibrary(e));
            }
        }
    }

    @NonNull
    private String titleOf(int no) {
        if (detail != null) {
            for (ChapterEntry c : detail.chapters) {
                if (c.chapterNo == no && c.title != null && !c.title.trim().isEmpty()) return c.title;
            }
        }
        return getString(R.string.chapter_default, no);
    }

    private void bindReactions(@NonNull LinearLayout row, @NonNull Post p, @NonNull ReaderStyle s) {
        row.removeAllViews();
        String mine = Reactions.mine(p);
        int pad = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics()));
        int gap = pad / 2;
        for (String key : Reactions.KEYS) {
            TextView pill = new TextView(this);
            int n = p.reactions != null && p.reactions.get(key) != null ? p.reactions.get(key) : 0;
            pill.setText(n > 0 ? Reactions.emoji(key) + " " + Formats.count(n) : Reactions.emoji(key));
            pill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            pill.setTextColor(s.ink);
            pill.setPadding(pad, pad / 2, pad, pad / 2);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(999f);
            boolean on = key.equals(mine);
            bg.setColor(ColorUtils.setAlphaComponent(on ? s.accent : s.ink, on ? 46 : 14));
            if (on) bg.setStroke(Math.max(1, gap / 3), s.accent);
            pill.setBackground(bg);
            pill.setContentDescription(getString(Reactions.label(key)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginStart(gap);
            lp.setMarginEnd(gap);
            pill.setLayoutParams(lp);
            pill.setOnClickListener(v -> react(key));
            row.addView(pill);
        }
    }

    private void react(@NonNull String key) {
        if (chap == null) return;
        Post p = chap.chapter;
        if (p.isMine) return;                                   // can't react to my own chapter
        Haptics.click(this);
        Post before = p.copy();
        String want = key.equals(Reactions.mine(p)) ? null : key;
        Reactions.apply(p, want);
        adapter.notifyItemChanged(adapter.endPosition());
        posts.setReaction(p.postId, want, new ApiCallback<LikeState>() {
            @Override
            public void onSuccess(LikeState st) {
                p.likeCount = st.likeCount;
                p.myReaction = st.myReaction;
                p.likedByMe = st.liked;
                if (st.reactions != null) p.reactions = st.reactions;
                if (!isFinishing()) adapter.notifyItemChanged(adapter.endPosition());
            }

            @Override
            public void onError(ApiException e) {
                p.likeCount = before.likeCount;
                p.myReaction = before.myReaction;
                p.likedByMe = before.likedByMe;
                p.reactions = before.reactions;
                if (isFinishing()) return;
                adapter.notifyItemChanged(adapter.endPosition());
                Toast.makeText(ReaderActivity.this, BookUi.error(ReaderActivity.this, e), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addToLibrary(@NonNull ItemReaderEndBinding e) {
        Haptics.click(this);
        e.library.setEnabled(false);
        books.setInLibrary(bookId, true, new ApiCallback<Book>() {
            @Override
            public void onSuccess(Book book) {
                if (isFinishing()) return;
                if (detail != null) detail.book = book;
                Toast.makeText(ReaderActivity.this, R.string.book_added, Toast.LENGTH_SHORT).show();
                adapter.notifyItemChanged(adapter.endPosition());
            }

            @Override
            public void onError(ApiException err) {
                if (isFinishing()) return;
                e.library.setEnabled(true);
                Toast.makeText(ReaderActivity.this, BookUi.error(ReaderActivity.this, err), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void bindTurn(@NonNull ItemReaderTurnBinding t, boolean next) {
        ReaderStyle s = style;
        if (s == null || chap == null) return;
        Integer no = next ? chap.next : chap.prev;
        t.root.setBackgroundColor(s.background);
        t.label.setTextColor(s.meta);
        t.label.setText(no == null ? "" : getString(R.string.reader_loading_chapter, no).toUpperCase(Locale.getDefault()));
        t.spinner.setIndicatorColor(s.accent);
    }

    // ── Sheets ────────────────────────────────────────────────────────────

    private void showContents() {
        if (detail == null) return;
        BottomSheetDialog d = new BottomSheetDialog(this);
        SheetReaderContentsBinding s = SheetReaderContentsBinding.inflate(getLayoutInflater());
        s.title.setText(detail.book.title);
        ReadingShelf.Entry here = shelf.find(bookId);
        int furthest = detail.book.lastReadChapter != null ? detail.book.lastReadChapter : 0;
        int current = chap != null ? chapterNo(chap) : -1;
        View currentRow = null;
        for (ChapterEntry c : released()) {
            boolean read = c.chapterNo <= furthest || (here != null && c.chapterNo < here.chapterNo);
            View row = BookUi.contentsRow(getLayoutInflater(), s.list, c, read, c.chapterNo == current, v -> {
                d.dismiss();
                if (c.chapterNo != current) openChapter(c.chapterNo, 0, false);
            });
            if (c.chapterNo == current) currentRow = row;
            s.list.addView(row);
        }
        d.setContentView(s.getRoot());
        d.getBehavior().setSkipCollapsed(false);
        d.show();
        View focus = currentRow;
        if (focus != null) s.scroll.post(() -> s.scroll.scrollTo(0, Math.max(0, focus.getTop() - 2 * focus.getHeight())));
    }

    private void showSettings() {
        BottomSheetDialog d = new BottomSheetDialog(this);
        SheetReaderSettingsBinding s = SheetReaderSettingsBinding.inflate(getLayoutInflater());
        Runnable render = () -> {
            s.sizeValue.setText(String.valueOf(prefs.sizeSp));
            s.smaller.setEnabled(prefs.sizeSp > ReaderPrefs.MIN_SIZE);
            s.bigger.setEnabled(prefs.sizeSp < ReaderPrefs.MAX_SIZE);
            s.spacing.check(prefs.spacing == 0 ? R.id.spacingCompact : prefs.spacing == 2 ? R.id.spacingAiry : R.id.spacingNormal);
            s.font.check(prefs.font == ReaderPrefs.FONT_SANS ? R.id.fontSans : R.id.fontSerif);
            themeTile(s.themePaper, R.color.reader_paper_bg, prefs.theme == ReaderPrefs.THEME_PAPER);
            themeTile(s.themeSepia, R.color.reader_sepia_bg, prefs.theme == ReaderPrefs.THEME_SEPIA);
            themeTile(s.themeNight, R.color.reader_night_bg, prefs.theme == ReaderPrefs.THEME_NIGHT);
            s.lines.setChecked(prefs.lines);
            s.turnStyle.check(prefs.turn == ReaderPrefs.TURN_SLIDE ? R.id.turnSlide
                    : prefs.turn == ReaderPrefs.TURN_INSTANT ? R.id.turnInstant : R.id.turnCurl);
            s.brightnessSystem.setChecked(prefs.brightness < 0);
            s.brightness.setEnabled(prefs.brightness >= 0);
            s.brightness.setValue(Math.round((prefs.brightness < 0 ? systemBrightness() : prefs.brightness) / 5f) * 5f);
            boolean lightTheme = prefs.theme != ReaderPrefs.THEME_NIGHT;
            int textTone = in.mohammad.ramiz.confess.ui.components.PaperTone.text(this);
            s.tone.setEnabled(lightTheme);
            s.tone.setAlpha(lightTheme ? 1f : 0.4f);
            s.textAuto.setEnabled(lightTheme);
            s.textAuto.setChecked(textTone == in.mohammad.ramiz.confess.ui.components.PaperTone.AUTO);
            s.textTone.setEnabled(lightTheme && textTone != in.mohammad.ramiz.confess.ui.components.PaperTone.AUTO);
            s.textTone.setAlpha(lightTheme ? 1f : 0.4f);
            if (textTone != in.mohammad.ramiz.confess.ui.components.PaperTone.AUTO) s.textTone.setValue(Math.round(textTone / 5f) * 5f);
            s.justify.setChecked(prefs.justify);
            s.volumeKeys.setChecked(prefs.volumeKeys);
        };
        render.run();
        s.smaller.setOnClickListener(v -> { prefs.sizeSp = Math.max(ReaderPrefs.MIN_SIZE, prefs.sizeSp - 1); changed(); render.run(); });
        s.bigger.setOnClickListener(v -> { prefs.sizeSp = Math.min(ReaderPrefs.MAX_SIZE, prefs.sizeSp + 1); changed(); render.run(); });
        s.spacing.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked) return;
            int sp = id == R.id.spacingCompact ? 0 : id == R.id.spacingAiry ? 2 : 1;
            if (sp != prefs.spacing) { prefs.spacing = sp; changed(); }
        });
        s.font.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked) return;
            int f = id == R.id.fontSans ? ReaderPrefs.FONT_SANS : ReaderPrefs.FONT_SERIF;
            if (f != prefs.font) { prefs.font = f; changed(); }
        });
        s.themePaper.setOnClickListener(v -> { prefs.theme = ReaderPrefs.THEME_PAPER; changed(); render.run(); });
        s.themeSepia.setOnClickListener(v -> { prefs.theme = ReaderPrefs.THEME_SEPIA; changed(); render.run(); });
        s.themeNight.setOnClickListener(v -> { prefs.theme = ReaderPrefs.THEME_NIGHT; changed(); render.run(); });
        s.tone.setValue(Math.round(in.mohammad.ramiz.confess.ui.components.PaperTone.get(this) / 5f) * 5f);
        s.tone.addOnChangeListener((sl, value, fromUser) -> {
            if (!fromUser) return;
            in.mohammad.ramiz.confess.ui.components.PaperTone.set(this, Math.round(value));
            recolour();                                   // pages change colour as you slide
        });
        s.textTone.addOnChangeListener((sl, value, fromUser) -> {
            if (!fromUser) return;
            in.mohammad.ramiz.confess.ui.components.PaperTone.setText(this, Math.round(value));
            recolour();
        });
        s.textAuto.setOnCheckedChangeListener((v, auto) -> {
            boolean wasAuto = in.mohammad.ramiz.confess.ui.components.PaperTone.text(this)
                    == in.mohammad.ramiz.confess.ui.components.PaperTone.AUTO;
            if (auto == wasAuto) return;                  // render() re-stating the saved value
            if (auto) {
                in.mohammad.ramiz.confess.ui.components.PaperTone.setText(this, in.mohammad.ramiz.confess.ui.components.PaperTone.AUTO);
            } else {
                int now = style != null && androidx.core.graphics.ColorUtils.calculateLuminance(style.ink) > 0.5 ? 100 : 0;
                s.textTone.setValue(now);
                in.mohammad.ramiz.confess.ui.components.PaperTone.setText(this, now);
            }
            s.textTone.setEnabled(!auto);
            recolour();
        });
        s.turnStyle.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked) return;
            int t = id == R.id.turnSlide ? ReaderPrefs.TURN_SLIDE : id == R.id.turnInstant ? ReaderPrefs.TURN_INSTANT : ReaderPrefs.TURN_CURL;
            if (t == prefs.turn) return;
            prefs.turn = t;
            prefs.save(this);
            b.pages.setTurnStyle(t);
            Haptics.tick(this);
        });
        s.brightness.addOnChangeListener((sl, value, fromUser) -> {
            if (!fromUser) return;
            prefs.brightness = Math.round(value);
            applyBrightness();
            prefs.save(this);
        });
        s.brightnessSystem.setOnCheckedChangeListener((v, system) -> {
            if (system == (prefs.brightness < 0)) return;           // render() re-stating it
            prefs.brightness = system ? -1 : Math.round(s.brightness.getValue());
            s.brightness.setEnabled(!system);
            applyBrightness();
            prefs.save(this);
        });
        s.lines.setOnCheckedChangeListener((v, on) -> { if (on != prefs.lines) { prefs.lines = on; changed(); } });
        s.justify.setOnCheckedChangeListener((v, on) -> { if (on != prefs.justify) { prefs.justify = on; changed(); } });
        s.volumeKeys.setOnCheckedChangeListener((v, on) -> { prefs.volumeKeys = on; prefs.save(this); });
        d.setContentView(s.getRoot());
        d.show();
    }

    private void themeTile(@NonNull TextView tile, int color, boolean selected) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics()));
        g.setColor(getColor(color));
        int px = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, selected ? 2 : 1, getResources().getDisplayMetrics()));
        g.setStroke(px, getColor(selected ? R.color.amber : R.color.hairline));
        tile.setBackground(g);
        tile.setSelected(selected);
    }

    /** Only colours changed (paper tone): new style + redraw, same pages, same place. */
    private void recolour() {
        style = new ReaderStyle(this, prefs, statusPx, navPx);
        applyTheme();
        relayout();
    }

    /** A reading setting changed: save it, recolour, and re-typeset the chapter at the same place. */
    private void changed() {
        Haptics.tick(this);
        prefs.save(this);
        style = new ReaderStyle(this, prefs, statusPx, navPx);
        applyTheme();
        relayout();
    }
}
