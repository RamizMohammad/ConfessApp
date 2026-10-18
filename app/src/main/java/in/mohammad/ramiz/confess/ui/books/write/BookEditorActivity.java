package in.mohammad.ramiz.confess.ui.books.write;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.model.Book;
import in.mohammad.ramiz.confess.data.model.BookDetail;
import in.mohammad.ramiz.confess.data.model.BookRequest;
import in.mohammad.ramiz.confess.data.model.ChapterEntry;
import in.mohammad.ramiz.confess.data.repo.BookRepository;
import in.mohammad.ramiz.confess.databinding.ActivityBookEditorBinding;
import in.mohammad.ramiz.confess.databinding.ItemDeskChapterBinding;
import in.mohammad.ramiz.confess.databinding.ItemDeskHeaderBinding;
import in.mohammad.ramiz.confess.ui.base.BaseActivity;
import in.mohammad.ramiz.confess.ui.books.BookUi;
import in.mohammad.ramiz.confess.ui.nav.Nav;
import in.mohammad.ramiz.confess.ui.util.EdgeInsets;

/**
 * The writer's desk for one book: the book itself (tap to edit), every chapter with its state —
 * out (date), scheduled (when) or draft — and "Add chapter". Chapters that aren't out yet can
 * be dragged into a new order; their release times stay in place (the server keeps the slots).
 * Overflow: see it as a reader, mark complete / reopen, delete.
 */
public class BookEditorActivity extends BaseActivity {

    public static final String EXTRA_BOOK_ID = "book_id";

    private ActivityBookEditorBinding b;
    private final BookRepository books = new BookRepository();
    private String bookId;
    @Nullable private BookDetail detail;
    private final List<ChapterEntry> chapters = new ArrayList<>();
    private Adapter adapter;
    private ItemTouchHelper dragger;
    private boolean busy = false;
    /** Unreleased chapter numbers before a drag, to know whether the order changed. */
    @Nullable private List<Integer> orderBefore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityBookEditorBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeInsets.padTop(b.topBar);
        EdgeInsets.padBottom(b.list, false);
        EdgeInsets.padBottom(b.add, false);

        bookId = getIntent().getStringExtra(EXTRA_BOOK_ID);
        if (bookId == null) {
            finish();
            return;
        }
        b.back.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        b.more.setOnClickListener(this::showMenu);
        b.add.setOnClickListener(v -> {
            Haptics.click(this);
            Nav.chapterEditor(this, bookId, 0);
        });

        adapter = new Adapter();
        b.list.setLayoutManager(new LinearLayoutManager(this));
        b.list.setAdapter(adapter);
        dragger = new ItemTouchHelper(new DragCallback());
        dragger.attachToRecyclerView(b.list);
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        books.book(bookId, new ApiCallback<BookDetail>() {
            @Override
            public void onSuccess(BookDetail d) {
                if (!isFinishing()) render(d);
            }

            @Override
            public void onError(ApiException e) {
                if (isFinishing()) return;
                b.progress.setVisibility(View.GONE);
                Snackbar.make(b.root, BookUi.error(BookEditorActivity.this, e), Snackbar.LENGTH_LONG)
                        .setAction(R.string.action_retry, v -> load()).show();
            }
        });
    }

    @SuppressLint("NotifyDataSetChanged")
    private void render(@NonNull BookDetail d) {
        detail = d;
        chapters.clear();
        chapters.addAll(d.chapters);
        b.progress.setVisibility(View.GONE);
        b.list.setVisibility(View.VISIBLE);
        b.screenSummary.setText(d.book.title);
        b.add.setVisibility(d.book.isCompleted() ? View.GONE : View.VISIBLE);
        adapter.notifyDataSetChanged();
    }

    // ── Menu ──────────────────────────────────────────────────────────────

    private void showMenu(@NonNull View anchor) {
        if (detail == null) return;
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, R.string.desk_preview);
        menu.getMenu().add(0, 2, 0, detail.book.isCompleted() ? R.string.desk_reopen : R.string.desk_complete);
        menu.getMenu().add(0, 3, 0, R.string.desk_delete);
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: Nav.book(this, bookId); break;
                case 2: setCompleted(!detail.book.isCompleted()); break;
                case 3: confirmDelete(); break;
                default: break;
            }
            return true;
        });
        menu.show();
    }

    private void setCompleted(boolean done) {
        if (busy) return;
        busy = true;
        books.update(bookId, BookRequest.completed(done), new ApiCallback<BookDetail>() {
            @Override
            public void onSuccess(BookDetail d) {
                busy = false;
                if (isFinishing()) return;
                render(d);
                Snackbar.make(b.root, done ? R.string.desk_completed : R.string.desk_reopened, Snackbar.LENGTH_LONG).show();
            }

            @Override
            public void onError(ApiException e) {
                busy = false;
                if (!isFinishing()) Snackbar.make(b.root, BookUi.error(BookEditorActivity.this, e), Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.desk_delete_title)
                .setMessage(R.string.desk_delete_body)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete, (dlg, w) -> books.delete(bookId, new ApiCallback<JsonObject>() {
                    @Override
                    public void onSuccess(JsonObject data) {
                        if (isFinishing()) return;
                        android.widget.Toast.makeText(BookEditorActivity.this, R.string.desk_deleted,
                                android.widget.Toast.LENGTH_SHORT).show();
                        finish();
                    }

                    @Override
                    public void onError(ApiException e) {
                        if (!isFinishing()) Snackbar.make(b.root, BookUi.error(BookEditorActivity.this, e), Snackbar.LENGTH_LONG).show();
                    }
                }))
                .show();
    }

    // ── Reordering ────────────────────────────────────────────────────────

    @NonNull
    private List<Integer> unreleasedOrder() {
        List<Integer> out = new ArrayList<>();
        for (ChapterEntry c : chapters) if (!c.isReleased()) out.add(c.chapterNo);
        return out;
    }

    private void saveOrder() {
        List<Integer> now = unreleasedOrder();
        if (orderBefore == null || orderBefore.equals(now)) return;
        orderBefore = null;
        books.reorder(bookId, now, new ApiCallback<BookDetail>() {
            @Override
            public void onSuccess(BookDetail d) {
                if (isFinishing()) return;
                render(d);
                Snackbar.make(b.root, R.string.desk_reordered, Snackbar.LENGTH_SHORT).show();
            }

            @Override
            public void onError(ApiException e) {
                if (isFinishing()) return;
                Snackbar.make(b.root, BookUi.error(BookEditorActivity.this, e), Snackbar.LENGTH_LONG).show();
                load();                                   // back to the server's order
            }
        });
    }

    private final class DragCallback extends ItemTouchHelper.Callback {

        @Override
        public int getMovementFlags(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
            ChapterEntry c = adapter.chapterAt(vh.getBindingAdapterPosition());
            if (c == null || c.isReleased()) return 0;
            return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
        }

        @Override
        public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder from, @NonNull RecyclerView.ViewHolder to) {
            int a = from.getBindingAdapterPosition(), z = to.getBindingAdapterPosition();
            ChapterEntry target = adapter.chapterAt(z);
            if (target == null || target.isReleased()) return false;     // released chapters stay put
            if (orderBefore == null) orderBefore = unreleasedOrder();
            Collections.swap(chapters, a - 1, z - 1);
            adapter.notifyItemMoved(a, z);
            Haptics.tick(BookEditorActivity.this);
            return true;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) { }

        @Override
        public void onSelectedChanged(@Nullable RecyclerView.ViewHolder vh, int state) {
            super.onSelectedChanged(vh, state);
            if (state == ItemTouchHelper.ACTION_STATE_DRAG && vh != null) {
                vh.itemView.setAlpha(0.92f);
                vh.itemView.setTranslationZ(12f);
            }
        }

        @Override
        public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
            super.clearView(rv, vh);
            vh.itemView.setAlpha(1f);
            vh.itemView.setTranslationZ(0f);
            saveOrder();
        }
    }

    // ── List ──────────────────────────────────────────────────────────────

    private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @Nullable
        ChapterEntry chapterAt(int position) {
            return position >= 1 && position - 1 < chapters.size() ? chapters.get(position - 1) : null;
        }

        @Override
        public int getItemCount() {
            return detail == null ? 0 : 1 + chapters.size();
        }

        @Override
        public int getItemViewType(int position) {
            return position == 0 ? 0 : 1;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            LayoutInflater inf = LayoutInflater.from(parent.getContext());
            if (type == 0) {
                ItemDeskHeaderBinding h = ItemDeskHeaderBinding.inflate(inf, parent, false);
                return new RecyclerView.ViewHolder(h.getRoot()) { };
            }
            ItemDeskChapterBinding c = ItemDeskChapterBinding.inflate(inf, parent, false);
            return new RecyclerView.ViewHolder(c.getRoot()) { };
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (detail == null) return;
            if (position == 0) {
                bindHeader(ItemDeskHeaderBinding.bind(holder.itemView), detail.book);
                return;
            }
            ChapterEntry c = chapters.get(position - 1);
            ItemDeskChapterBinding r = ItemDeskChapterBinding.bind(holder.itemView);
            r.number.setText(getString(R.string.chapter_number, c.chapterNo));
            r.title.setText(BookUi.chapterTitle(BookEditorActivity.this, c.chapterNo, c.title));
            r.excerpt.setText(c.excerpt);
            int color;
            String state;
            int icon;
            if (ChapterEntry.SCHEDULED.equals(c.state)) {
                state = getString(R.string.state_scheduled, BookUi.when(c.publishAt));
                color = R.color.amber;
                icon = R.drawable.ic_clock;
            } else if (ChapterEntry.DRAFT.equals(c.state)) {
                state = getString(R.string.state_draft);
                color = R.color.text_secondary;
                icon = R.drawable.ic_draft;
            } else {
                state = getString(R.string.state_released, in.mohammad.ramiz.confess.core.Formats.relative(c.releasedAt));
                color = R.color.sage;
                icon = R.drawable.ic_check_check;
            }
            r.state.setText(state.toUpperCase(Locale.getDefault()));
            int tint = ContextCompat.getColor(BookEditorActivity.this, color);
            r.state.setTextColor(tint);
            android.graphics.drawable.Drawable d = ContextCompat.getDrawable(BookEditorActivity.this, icon);
            if (d != null) {
                d = d.mutate();
                int px = Math.round(12 * getResources().getDisplayMetrics().density);
                d.setBounds(0, 0, px, px);
                d.setTintList(ColorStateList.valueOf(tint));
            }
            r.state.setCompoundDrawablesRelative(d, null, null, null);
            boolean movable = !c.isReleased();
            r.handle.setVisibility(movable ? View.VISIBLE : View.INVISIBLE);
            r.handle.setOnTouchListener(movable ? (v, ev) -> {
                if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) dragger.startDrag(holder);
                return false;
            } : null);
            r.card.setOnClickListener(v -> Nav.chapterEditor(BookEditorActivity.this, bookId, c.chapterNo));
        }

        private void bindHeader(@NonNull ItemDeskHeaderBinding h, @NonNull Book book) {
            h.cover.bind(book);
            h.title.setText(book.title);
            h.blurb.setText(book.blurb);
            h.blurb.setVisibility(book.blurb == null || book.blurb.isEmpty() ? View.GONE : View.VISIBLE);
            h.status.setText(book.isCompleted() ? R.string.book_status_complete
                    : book.isDraft() ? R.string.book_status_draft : R.string.book_status_ongoing);
            List<String> parts = new ArrayList<>();
            parts.add(getString(R.string.mybooks_out, book.chapterCount));
            if (book.scheduledCount != null && book.scheduledCount > 0) parts.add(getString(R.string.mybooks_scheduled, book.scheduledCount));
            if (book.draftCount != null && book.draftCount > 0) parts.add(getString(R.string.mybooks_drafts, book.draftCount));
            parts.add(getResources().getQuantityString(R.plurals.books_readers, book.followerCount, book.followerCount));
            h.stats.setText(android.text.TextUtils.join(" · ", parts));
            h.hiddenNote.setVisibility(Boolean.TRUE.equals(book.hidden) ? View.VISIBLE : View.GONE);
            boolean anyUnreleased = !unreleasedOrder().isEmpty();
            h.hint.setVisibility(unreleasedOrder().size() > 1 ? View.VISIBLE : View.GONE);
            h.empty.setVisibility(chapters.isEmpty() ? View.VISIBLE : View.GONE);
            h.bookCard.setOnClickListener(v -> Nav.bookSetup(BookEditorActivity.this, bookId));
            if (!anyUnreleased) h.hint.setVisibility(View.GONE);
        }
    }
}
