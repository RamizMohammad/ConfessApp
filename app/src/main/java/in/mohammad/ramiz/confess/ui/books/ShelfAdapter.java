package in.mohammad.ramiz.confess.ui.books;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.data.books.ReadingShelf;
import in.mohammad.ramiz.confess.data.model.Book;
import in.mohammad.ramiz.confess.data.repo.BookRepository;
import in.mohammad.ramiz.confess.databinding.ItemBookCoverBinding;
import in.mohammad.ramiz.confess.databinding.ItemBooksContinueBinding;
import in.mohammad.ramiz.confess.databinding.ItemBooksStateBinding;
import in.mohammad.ramiz.confess.databinding.ItemBooksTabsBinding;
import in.mohammad.ramiz.confess.databinding.ItemContinueBookBinding;

/**
 * The shelf: [continue reading row] · [tabs] · covers (3 a row) | state | loading.
 * Everything but the covers spans the full width (see {@link #isFullWidth}).
 */
final class ShelfAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    interface Listener {
        void onBook(@NonNull Book book);

        void onContinue(@NonNull ReadingShelf.Entry entry);

        void onTab(@NonNull String tab);

        void onStateAction();
    }

    static final int CONTINUE = 0, TABS = 1, BOOK = 2, STATE = 3, LOADING = 4;

    /** Empty / error message shown under the tabs. */
    static final class State {
        @DrawableRes final int icon;
        @StringRes final int title;
        @StringRes final int body;
        @StringRes final int action;

        State(@DrawableRes int icon, @StringRes int title, @StringRes int body, @StringRes int action) {
            this.icon = icon;
            this.title = title;
            this.body = body;
            this.action = action;
        }
    }

    private final Listener listener;
    private List<ReadingShelf.Entry> continuing = new ArrayList<>();
    private final List<Book> books = new ArrayList<>();
    @NonNull private String tab = BookRepository.TAB_UPDATED;
    @Nullable private State state;
    private boolean loading;

    ShelfAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    // ── Data ──────────────────────────────────────────────────────────────

    void setContinuing(@NonNull List<ReadingShelf.Entry> list) {
        continuing = list;
        notifyDataSetChanged();
    }

    void setTab(@NonNull String tab) {
        this.tab = tab;
    }

    void setBooks(@NonNull List<Book> list, boolean append) {
        if (!append) books.clear();
        books.addAll(list);
        state = null;
        notifyDataSetChanged();
    }

    void setState(@Nullable State s) {
        state = s;
        if (s != null) books.clear();
        notifyDataSetChanged();
    }

    void setLoading(boolean on) {
        if (loading == on) return;
        loading = on;
        notifyDataSetChanged();
    }

    /** Swap one book (after a library change elsewhere). */
    void update(@NonNull Book b) {
        for (int i = 0; i < books.size(); i++) {
            if (books.get(i).bookId.equals(b.bookId)) {
                books.set(i, b);
                notifyItemChanged(headerCount() + i);
                return;
            }
        }
    }

    private boolean hasContinue() {
        return !continuing.isEmpty();
    }

    private int headerCount() {
        return (hasContinue() ? 1 : 0) + 1;
    }

    boolean isFullWidth(int position) {
        return getItemViewType(position) != BOOK;
    }

    @Override
    public int getItemCount() {
        return headerCount() + books.size() + (state != null ? 1 : 0) + (loading ? 1 : 0);
    }

    @Override
    public int getItemViewType(int position) {
        if (hasContinue()) {
            if (position == 0) return CONTINUE;
            position--;
        }
        if (position == 0) return TABS;
        position--;
        if (position < books.size()) return BOOK;
        position -= books.size();
        if (state != null && position == 0) return STATE;
        return LOADING;
    }

    // ── Views ─────────────────────────────────────────────────────────────

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        switch (type) {
            case CONTINUE: {
                ItemBooksContinueBinding b = ItemBooksContinueBinding.inflate(inf, parent, false);
                b.row.setLayoutManager(new LinearLayoutManager(parent.getContext(), LinearLayoutManager.HORIZONTAL, false));
                return new Holder<>(b.getRoot(), b);
            }
            case TABS: {
                ItemBooksTabsBinding b = ItemBooksTabsBinding.inflate(inf, parent, false);
                b.tabs.setOnCheckedStateChangeListener((group, ids) -> {
                    if (ids.isEmpty()) return;
                    int id = ids.get(0);
                    String next = id == R.id.tabLibrary ? BookRepository.TAB_LIBRARY
                            : id == R.id.tabNew ? BookRepository.TAB_NEW : BookRepository.TAB_UPDATED;
                    if (!next.equals(tab)) {
                        Haptics.tick(group.getContext());
                        tab = next;
                        listener.onTab(next);
                    }
                });
                return new Holder<>(b.getRoot(), b);
            }
            case BOOK: {
                ItemBookCoverBinding b = ItemBookCoverBinding.inflate(inf, parent, false);
                return new Holder<>(b.getRoot(), b);
            }
            case STATE: {
                ItemBooksStateBinding b = ItemBooksStateBinding.inflate(inf, parent, false);
                return new Holder<>(b.getRoot(), b);
            }
            default:
                return new Holder<>(inf.inflate(R.layout.item_books_loading, parent, false), null);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object binding = ((Holder<?>) holder).binding;
        if (binding instanceof ItemBooksContinueBinding) {
            ItemBooksContinueBinding b = (ItemBooksContinueBinding) binding;
            b.row.setAdapter(new ContinueAdapter(continuing, listener));
        } else if (binding instanceof ItemBooksTabsBinding) {
            ItemBooksTabsBinding b = (ItemBooksTabsBinding) binding;
            int id = BookRepository.TAB_LIBRARY.equals(tab) ? R.id.tabLibrary
                    : BookRepository.TAB_NEW.equals(tab) ? R.id.tabNew : R.id.tabUpdated;
            if (b.tabs.getCheckedChipId() != id) b.tabs.check(id);
        } else if (binding instanceof ItemBookCoverBinding) {
            bindBook((ItemBookCoverBinding) binding, books.get(position - headerCount()));
        } else if (binding instanceof ItemBooksStateBinding && state != null) {
            ItemBooksStateBinding b = (ItemBooksStateBinding) binding;
            b.icon.setImageResource(state.icon);
            b.title.setText(state.title);
            b.body.setText(state.body);
            b.action.setVisibility(state.action != 0 ? View.VISIBLE : View.GONE);
            if (state.action != 0) b.action.setText(state.action);
            b.action.setOnClickListener(v -> listener.onStateAction());
        }
    }

    private void bindBook(@NonNull ItemBookCoverBinding b, @NonNull Book book) {
        b.cover.bind(book);
        b.title.setText(book.title);
        String chapters = b.getRoot().getResources().getQuantityString(R.plurals.books_chapters,
                book.chapterCount, book.chapterCount);
        b.meta.setText(b.getRoot().getContext().getString(R.string.books_card_meta,
                book.author != null ? book.author.alias : "", chapters));
        if (book.hasUnread && book.lastReadChapter != null) {
            b.badge.setVisibility(View.VISIBLE);
            b.badge.setText(R.string.books_badge_new);
        } else if (book.isCompleted()) {
            b.badge.setVisibility(View.VISIBLE);
            b.badge.setText(R.string.books_badge_complete);
        } else {
            b.badge.setVisibility(View.GONE);
        }
        b.card.setOnClickListener(v -> listener.onBook(book));
    }

    static final class Holder<B> extends RecyclerView.ViewHolder {
        @Nullable final B binding;

        Holder(@NonNull View v, @Nullable B binding) {
            super(v);
            this.binding = binding;
        }
    }

    /** The horizontal "continue reading" row. */
    static final class ContinueAdapter extends RecyclerView.Adapter<Holder<ItemContinueBookBinding>> {
        private final List<ReadingShelf.Entry> items;
        private final Listener listener;

        ContinueAdapter(List<ReadingShelf.Entry> items, Listener listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public Holder<ItemContinueBookBinding> onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemContinueBookBinding b = ItemContinueBookBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new Holder<>(b.getRoot(), b);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder<ItemContinueBookBinding> h, int position) {
            ItemContinueBookBinding b = h.binding;
            if (b == null) return;
            ReadingShelf.Entry e = items.get(position);
            b.cover.set(e.cover, e.title, e.alias);
            b.title.setText(e.title);
            b.where.setText(b.getRoot().getContext().getString(R.string.books_continue_progress,
                    e.chapterIndex, Math.max(e.chapterIndex, e.chapters)));
            int pct = Math.round(e.bookProgress() * 100);
            b.progress.setProgressCompat(pct, false);
            b.pct.setText(b.getRoot().getContext().getString(R.string.books_continue_pct, pct));
            b.card.setOnClickListener(v -> listener.onContinue(e));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }
}
