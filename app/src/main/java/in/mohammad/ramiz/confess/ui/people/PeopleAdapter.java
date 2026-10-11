package in.mohammad.ramiz.confess.ui.people;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Objects;

import in.mohammad.ramiz.confess.data.model.Person;
import in.mohammad.ramiz.confess.databinding.ItemFollowPersonBinding;

/** Followers / following: aura · @alias · about · Follow / Following. */
public final class PeopleAdapter extends ListAdapter<Person, PeopleAdapter.Holder> {

    public interface Listener {
        void onPerson(@NonNull Person p);

        void onFollow(@NonNull Person p);
    }

    private final Listener listener;

    public PeopleAdapter(@NonNull Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemFollowPersonBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Person p = getItem(position);
        h.b.avatar.setAvatar(p.avatar);
        h.b.avatar.setStoryAlias(p.alias);
        h.b.alias.setText("@" + p.alias);
        boolean hasAbout = p.about != null && !p.about.trim().isEmpty();
        h.b.about.setVisibility(hasAbout ? View.VISIBLE : View.GONE);
        h.b.about.setText(hasAbout ? p.about.trim() : "");
        h.b.followBtn.setVisibility(p.followedByMe ? View.GONE : View.VISIBLE);
        h.b.followingBtn.setVisibility(p.followedByMe ? View.VISIBLE : View.GONE);
        h.b.row.setOnClickListener(v -> listener.onPerson(p));
        h.b.followBtn.setOnClickListener(v -> listener.onFollow(p));
        h.b.followingBtn.setOnClickListener(v -> listener.onFollow(p));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemFollowPersonBinding b;

        Holder(ItemFollowPersonBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }

    private static final DiffUtil.ItemCallback<Person> DIFF = new DiffUtil.ItemCallback<Person>() {
        @Override
        public boolean areItemsTheSame(@NonNull Person a, @NonNull Person b) {
            return Objects.equals(a.alias, b.alias);
        }

        @Override
        public boolean areContentsTheSame(@NonNull Person a, @NonNull Person b) {
            return a.followedByMe == b.followedByMe && Objects.equals(a.about, b.about)
                    && Objects.equals(a.avatar, b.avatar);
        }
    };
}
