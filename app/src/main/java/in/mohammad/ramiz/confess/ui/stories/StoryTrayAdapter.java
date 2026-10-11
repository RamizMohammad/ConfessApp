package in.mohammad.ramiz.confess.ui.stories;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.data.model.Me;
import in.mohammad.ramiz.confess.data.model.StoryGroup;
import in.mohammad.ramiz.confess.databinding.ItemStoryBubbleBinding;

/**
 * The tray: "Your story" first (with + to add), then everyone with a live story.
 * Unseen → amber ring; all seen → hairline ring.
 */
public final class StoryTrayAdapter extends RecyclerView.Adapter<StoryTrayAdapter.Holder> {

    public interface Listener {
        /** Open the viewer at this group (index into the list given to {@link #submit}). */
        void onOpenStories(int groupIndex);

        void onAddStory();
    }

    private final Listener listener;
    private final List<StoryGroup> groups = new ArrayList<>();
    @Nullable private Me me;
    private boolean meHasStories;

    public StoryTrayAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    /** groups as the server sends them (mine, if any, is first). */
    public void submit(@Nullable Me me, @NonNull List<StoryGroup> groups) {
        this.me = me;
        this.groups.clear();
        this.groups.addAll(groups);
        meHasStories = !groups.isEmpty() && groups.get(0).isMe;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return groups.size() + (meHasStories ? 0 : 1);           // "Your story" placeholder when I have none
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemStoryBubbleBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        boolean placeholder = !meHasStories && position == 0;
        int groupIndex = meHasStories ? position : position - 1;

        if (placeholder) {
            h.b.avatar.setAvatar(me != null ? me.avatar : null);
            h.b.ring.setBackgroundResource(R.drawable.bg_story_ring_seen);
            h.b.label.setText(R.string.story_yours);
            h.b.add.setVisibility(View.VISIBLE);
            h.b.bubble.setOnClickListener(v -> listener.onAddStory());
            h.b.bubble.setContentDescription(h.itemView.getContext().getString(R.string.story_add));
            return;
        }
        StoryGroup g = groups.get(groupIndex);
        h.b.avatar.setAvatar(g.author != null ? g.author.avatar : null);
        h.b.ring.setBackgroundResource(g.hasUnseen ? R.drawable.bg_story_ring_unseen : R.drawable.bg_story_ring_seen);
        h.b.label.setText(g.isMe ? h.itemView.getContext().getString(R.string.story_yours)
                : (g.author != null ? g.author.alias : ""));
        h.b.add.setVisibility(g.isMe ? View.VISIBLE : View.GONE);
        h.b.add.setOnClickListener(g.isMe ? v -> listener.onAddStory() : null);
        h.b.bubble.setOnClickListener(v -> listener.onOpenStories(groupIndex));
        h.b.bubble.setContentDescription(h.itemView.getContext().getString(
                g.hasUnseen ? R.string.story_cd_unseen : R.string.story_cd_seen,
                g.isMe ? h.itemView.getContext().getString(R.string.story_yours) : "@" + (g.author != null ? g.author.alias : "")));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemStoryBubbleBinding b;

        Holder(ItemStoryBubbleBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
