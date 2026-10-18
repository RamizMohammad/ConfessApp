package in.mohammad.ramiz.confess.ui.books;

import android.os.Bundle;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.books.ReadingShelf;
import in.mohammad.ramiz.confess.data.model.Book;
import in.mohammad.ramiz.confess.data.model.BookPage;
import in.mohammad.ramiz.confess.data.model.Me;
import in.mohammad.ramiz.confess.data.repo.BookRepository;
import in.mohammad.ramiz.confess.data.session.SessionManager;
import in.mohammad.ramiz.confess.databinding.FragmentBooksBinding;
import in.mohammad.ramiz.confess.ui.nav.Nav;
import in.mohammad.ramiz.confess.ui.util.EdgeInsets;
import in.mohammad.ramiz.confess.ui.util.Motion;
import retrofit2.Call;

/**
 * Books tab — the shelf, like an e-reader's library:
 *   Continue reading  — books I'm in the middle of (my place is kept on this phone)
 *   Library           — books I added (a push when a chapter comes out); NEW = unread chapter
 *   Updated · New     — every published book, by latest chapter / by first release
 * The writer's desk (my own books) is behind the feather in the top bar.
 */
public class BooksFragment extends Fragment implements ShelfAdapter.Listener {

    private static final String PREFS = "books_tab";
    private static final String KEY_TAB = "tab";
    private static final long STALE_MS = 60_000;

    private FragmentBooksBinding b;
    private ShelfAdapter adapter;
    private final BookRepository books = new BookRepository();
    @Nullable private Call<BookPage> inFlight;
    @NonNull private String tab = BookRepository.TAB_UPDATED;
    @Nullable private String cursor;
    private boolean end = false;
    private boolean loading = false;
    private long loadedAt = 0;
    private boolean animatedIn = false;
    /** The state under the tabs is a load error (its button retries). */
    private boolean errorShown = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentBooksBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        EdgeInsets.padTop(b.topBar);
        tab = requireContext().getSharedPreferences(PREFS, 0).getString(KEY_TAB, BookRepository.TAB_UPDATED);

        adapter = new ShelfAdapter(this);
        adapter.setTab(tab);
        GridLayoutManager grid = new GridLayoutManager(requireContext(), 3);
        grid.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return adapter.isFullWidth(position) ? 3 : 1;
            }
        });
        b.list.setLayoutManager(grid);
        b.list.setAdapter(adapter);
        b.list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy > 0 && !loading && !end && grid.findLastVisibleItemPosition() >= adapter.getItemCount() - 6) {
                    load(false);
                }
            }
        });

        b.refresh.setColorSchemeColors(ContextCompat.getColor(requireContext(), R.color.amber));
        b.refresh.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(requireContext(), R.color.surface_high));
        b.refresh.setOnRefreshListener(() -> {
            renderContinuing();
            load(true);
        });
        b.write.setOnClickListener(v -> Nav.myBooks(requireContext()));

        renderContinuing();
        showSaved();
        load(true);
    }

    /** The shelf as it was last time, at once (the fresh one replaces it). */
    private boolean showingSaved = false;

    private void showSaved() {
        BookPage saved = BookRepository.savedShelf(tab);
        showingSaved = saved != null;
        if (saved == null) return;
        adapter.setBooks(saved.books, false);
        if (!animatedIn) {
            animatedIn = true;
            Motion.enterList(b.list);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (b == null || isHidden()) return;
        renderContinuing();                                        // back from the reader: new place
        if (SystemClock.elapsedRealtime() - loadedAt > STALE_MS) load(true);
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && b != null) {
            renderContinuing();
            if (SystemClock.elapsedRealtime() - loadedAt > STALE_MS) load(true);
        }
    }

    /** Home: tapping the Books tab again. */
    public void scrollToTop() {
        if (b != null) b.list.smoothScrollToPosition(0);
    }

    private void renderContinuing() {
        Me me = SessionManager.get().cachedMe();
        List<ReadingShelf.Entry> list = ReadingShelf.get(requireContext(), me == null ? null : me.id).recent();
        if (list.size() > 10) list = list.subList(0, 10);
        adapter.setContinuing(list);
    }

    private void load(boolean reset) {
        if (b == null) return;
        if (reset) {
            if (inFlight != null) inFlight.cancel();
            cursor = null;
            end = false;
        } else if (loading || end) {
            return;
        }
        loading = true;
        adapter.setLoading(!b.refresh.isRefreshing() && !(reset && showingSaved));
        final String forTab = tab;
        inFlight = books.list(forTab, reset ? null : cursor, new ApiCallback<BookPage>() {
            @Override
            public void onSuccess(BookPage page) {
                if (b == null || !forTab.equals(tab)) return;
                loading = false;
                loadedAt = SystemClock.elapsedRealtime();
                b.refresh.setRefreshing(false);
                adapter.setLoading(false);
                errorShown = false;
                if (reset) {
                    showingSaved = false;
                    BookRepository.saveShelf(forTab, page);
                }
                cursor = page.nextCursor;
                end = page.nextCursor == null;
                if (reset && page.books.isEmpty()) {
                    adapter.setState(emptyState());
                } else {
                    adapter.setBooks(page.books, !reset);
                }
                if (!animatedIn && !page.books.isEmpty()) {
                    animatedIn = true;
                    Motion.enterList(b.list);
                }
            }

            @Override
            public void onError(ApiException e) {
                if (b == null || !forTab.equals(tab)) return;
                loading = false;
                b.refresh.setRefreshing(false);
                adapter.setLoading(false);
                if (reset && showingSaved) {
                    // Offline: keep the saved shelf (pull to try again).
                } else if (reset) {
                    errorShown = true;
                    adapter.setState(new ShelfAdapter.State(R.drawable.ic_wifi_off,
                            R.string.books_empty_title, e.isOffline() ? R.string.error_offline : R.string.error_generic,
                            R.string.action_retry));
                }
            }
        });
    }

    @NonNull
    private ShelfAdapter.State emptyState() {
        if (BookRepository.TAB_LIBRARY.equals(tab)) {
            return new ShelfAdapter.State(R.drawable.ic_library_add, R.string.books_empty_library_title,
                    R.string.books_empty_library_body, R.string.books_tab_updated);
        }
        return new ShelfAdapter.State(R.drawable.ic_book_open, R.string.books_empty_title,
                R.string.books_empty_body, R.string.books_empty_write);
    }

    // ── ShelfAdapter.Listener ─────────────────────────────────────────────

    @Override
    public void onBook(@NonNull Book book) {
        Nav.book(requireContext(), book.bookId);
    }

    @Override
    public void onContinue(@NonNull ReadingShelf.Entry entry) {
        Nav.reader(requireContext(), entry.bookId, 0);
    }

    @Override
    public void onTab(@NonNull String next) {
        tab = next;
        requireContext().getSharedPreferences(PREFS, 0).edit().putString(KEY_TAB, next).apply();
        adapter.setBooks(java.util.Collections.emptyList(), false);
        showSaved();
        load(true);
    }

    @Override
    public void onStateAction() {
        if (errorShown) {
            load(true);                                            // "Try again"
        } else if (BookRepository.TAB_LIBRARY.equals(tab)) {
            adapter.setTab(BookRepository.TAB_UPDATED);            // "Browse": empty library → Updated
            onTab(BookRepository.TAB_UPDATED);
        } else {
            Nav.myBooks(requireContext());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (inFlight != null) inFlight.cancel();
        b = null;
    }
}
