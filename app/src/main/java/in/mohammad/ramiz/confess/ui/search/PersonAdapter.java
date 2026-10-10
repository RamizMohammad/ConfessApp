package in.mohammad.ramiz.confess.ui.search;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Objects;

import in.mohammad.ramiz.confess.data.model.UserSummary;
import in.mohammad.ramiz.confess.databinding.ItemPersonBinding;

/** Search results: aura · @alias · about. */
public class PersonAdapter extends ListAdapter<UserSummary, PersonAdapter.Holder> {

    public interface Listener {
        void onPerson(@NonNull UserSummary user);
    }

    private final Listener listener;

    public PersonAdapter(@NonNull Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemPersonBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        UserSummary u = getItem(position);
        h.b.avatar.setAvatar(u.avatar);
        h.b.alias.setText("@" + u.alias);
        boolean hasAbout = u.about != null && !u.about.trim().isEmpty();
        h.b.about.setVisibility(hasAbout ? View.VISIBLE : View.GONE);
        h.b.about.setText(hasAbout ? u.about.trim() : "");
        h.b.row.setOnClickListener(v -> listener.onPerson(u));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemPersonBinding b;

        Holder(ItemPersonBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }

    private static final DiffUtil.ItemCallback<UserSummary> DIFF = new DiffUtil.ItemCallback<UserSummary>() {
        @Override
        public boolean areItemsTheSame(@NonNull UserSummary a, @NonNull UserSummary b) {
            return Objects.equals(a.alias, b.alias);
        }

        @Override
        public boolean areContentsTheSame(@NonNull UserSummary a, @NonNull UserSummary b) {
            return Objects.equals(a.about, b.about) && Objects.equals(a.avatar, b.avatar);
        }
    };
}
