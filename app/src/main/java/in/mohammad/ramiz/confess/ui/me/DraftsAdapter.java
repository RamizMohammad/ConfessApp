package in.mohammad.ramiz.confess.ui.me;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.time.Instant;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Formats;
import in.mohammad.ramiz.confess.data.drafts.Drafts;
import in.mohammad.ramiz.confess.databinding.ItemDraftBinding;

/** Drafts as cards: the opening lines + "Edited 5m ago · 214 chars". */
public final class DraftsAdapter extends ListAdapter<Drafts.Draft, DraftsAdapter.Holder> {

    public interface Listener {
        void onOpen(@NonNull Drafts.Draft draft);
        void onDelete(@NonNull Drafts.Draft draft, int position);
    }

    private final Listener listener;

    public DraftsAdapter(@NonNull Listener listener) {
        super(new DiffUtil.ItemCallback<Drafts.Draft>() {
            @Override public boolean areItemsTheSame(@NonNull Drafts.Draft a, @NonNull Drafts.Draft b) {
                return a.id.equals(b.id);
            }
            @Override public boolean areContentsTheSame(@NonNull Drafts.Draft a, @NonNull Drafts.Draft b) {
                return a.updatedAt == b.updatedAt && a.text.equals(b.text) && a.allowResponses == b.allowResponses;
            }
        });
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemDraftBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Drafts.Draft d = getItem(position);
        h.b.text.setText(d.text.trim());
        String meta = h.itemView.getContext().getString(R.string.drafts_edited,
                Formats.relative(Instant.ofEpochMilli(d.updatedAt).toString()).toLowerCase())
                + " · " + h.itemView.getContext().getString(R.string.drafts_chars, d.text.trim().length());
        if (!d.allowResponses) meta += " · " + h.itemView.getContext().getString(R.string.drafts_responses_off);
        h.b.meta.setText(meta);
        h.b.card.setOnClickListener(v -> listener.onOpen(d));
        h.b.delete.setOnClickListener(v -> {
            int pos = h.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) listener.onDelete(getItem(pos), pos);
        });
    }

    public Drafts.Draft itemAt(int position) {
        return getItem(position);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemDraftBinding b;

        Holder(ItemDraftBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
