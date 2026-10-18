package in.mohammad.ramiz.confess.ui.books.reader;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import in.mohammad.ramiz.confess.databinding.ItemReaderEndBinding;
import in.mohammad.ramiz.confess.databinding.ItemReaderTurnBinding;

/**
 * The pages of the open chapter, in order, for the PageTurner:
 *   [turn to the previous chapter] · page 1 … page n · end of chapter · [turn to the next chapter]
 * Landing on a "turn" page asks the reader to open that chapter.
 */
final class ReaderAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    static final int TEXT = 0, END = 1, TURN_PREV = 2, TURN_NEXT = 3;

    interface Binder {
        void bindEnd(@NonNull ItemReaderEndBinding b);

        void bindTurn(@NonNull ItemReaderTurnBinding b, boolean next);
    }

    private final Binder binder;
    @Nullable private ChapterPages pages;
    @Nullable private ReaderStyle style;
    private int chapterNo;
    private String bookTitle = "";
    private String chapterTitle = "";
    private boolean hasPrev, hasNext;

    ReaderAdapter(@NonNull Binder binder) {
        this.binder = binder;
    }

    void set(@NonNull ChapterPages pages, @NonNull ReaderStyle style, int chapterNo, @NonNull String bookTitle,
             @NonNull String chapterTitle, boolean hasPrev, boolean hasNext) {
        this.pages = pages;
        this.style = style;
        this.chapterNo = chapterNo;
        this.bookTitle = bookTitle;
        this.chapterTitle = chapterTitle;
        this.hasPrev = hasPrev;
        this.hasNext = hasNext;
        notifyDataSetChanged();
    }

    int pageCount() {
        return pages == null ? 0 : pages.count();
    }

    /** Adapter position of text page `page`. */
    int positionOfPage(int page) {
        return (hasPrev ? 1 : 0) + Math.max(0, Math.min(page, pageCount() - 1));
    }

    int endPosition() {
        return (hasPrev ? 1 : 0) + pageCount();
    }

    /** Text page index at an adapter position, or -1. */
    int pageAt(int position) {
        int p = position - (hasPrev ? 1 : 0);
        return p >= 0 && p < pageCount() ? p : -1;
    }

    @Override
    public int getItemCount() {
        if (pages == null) return 0;
        return (hasPrev ? 1 : 0) + pages.count() + 1 + (hasNext ? 1 : 0);
    }

    @Override
    public int getItemViewType(int position) {
        if (hasPrev && position == 0) return TURN_PREV;
        int p = position - (hasPrev ? 1 : 0);
        if (p < pageCount()) return TEXT;
        if (p == pageCount()) return END;
        return TURN_NEXT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        View v;
        Object binding = null;
        if (type == TEXT) {
            v = new PageView(parent.getContext());
        } else if (type == END) {
            ItemReaderEndBinding b = ItemReaderEndBinding.inflate(inf, parent, false);
            v = b.getRoot();
            binding = b;
        } else {
            ItemReaderTurnBinding b = ItemReaderTurnBinding.inflate(inf, parent, false);
            v = b.getRoot();
            binding = b;
        }
        v.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return new Holder(v, binding);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        resetTurn(holder.itemView);
        Holder h = (Holder) holder;
        int type = getItemViewType(position);
        if (type == TEXT && pages != null && style != null) {
            ((PageView) h.itemView).bind(pages, style, pageAt(position), chapterNo, bookTitle, chapterTitle);
        } else if (type == END) {
            binder.bindEnd((ItemReaderEndBinding) h.binding);
        } else if (h.binding instanceof ItemReaderTurnBinding) {
            binder.bindTurn((ItemReaderTurnBinding) h.binding, type == TURN_NEXT);
        }
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        resetTurn(holder.itemView);
    }

    /** Undo the page-turn transform (see ReaderActivity.applyTurn). */
    static void resetTurn(@NonNull View v) {
        v.setTranslationX(0);
        v.setTranslationZ(0);
        v.setScaleX(1);
        v.setScaleY(1);
        v.setAlpha(1);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        @Nullable final Object binding;

        Holder(@NonNull View v, @Nullable Object binding) {
            super(v);
            this.binding = binding;
        }
    }
}
