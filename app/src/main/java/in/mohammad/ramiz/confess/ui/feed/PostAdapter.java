package in.mohammad.ramiz.confess.ui.feed;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import in.mohammad.ramiz.confess.data.model.Post;
import in.mohammad.ramiz.confess.databinding.ItemConfessionCardBinding;
import in.mohammad.ramiz.confess.databinding.ItemFeedFooterBinding;

/**
 * Paper cards + one footer row. Old: PostAdapter (notifyDataSetChanged on every page).
 * Uses DiffUtil, and a "felt" payload so a heart tap updates only the button.
 */
public class PostAdapter extends ListAdapter<PostAdapter.Row, RecyclerView.ViewHolder> {

    private static final int TYPE_POST = 1;
    private static final int TYPE_FOOTER = 2;
    private static final String PAYLOAD_FELT = "felt";

    /** A list row: a confession, or the footer. */
    public static final class Row {
        @Nullable final Post post;
        @Nullable final FeedState.Footer footer;

        private Row(@Nullable Post post, @Nullable FeedState.Footer footer) {
            this.post = post;
            this.footer = footer;
        }
    }

    private final PostCardBinder.Listener listener;
    private final Runnable onRetryMore;

    public PostAdapter(@NonNull PostCardBinder.Listener listener, @NonNull Runnable onRetryMore) {
        super(DIFF);
        this.listener = listener;
        this.onRetryMore = onRetryMore;
    }

    public void submit(@NonNull List<Post> posts, @NonNull FeedState.Footer footer, @Nullable Runnable done) {
        List<Row> rows = new ArrayList<>(posts.size() + 1);
        for (Post p : posts) rows.add(new Row(p, null));
        if (footer != FeedState.Footer.NONE || !posts.isEmpty()) rows.add(new Row(null, footer));
        submitList(rows, done);
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).post != null ? TYPE_POST : TYPE_FOOTER;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_POST) {
            return new PostHolder(ItemConfessionCardBinding.inflate(inf, parent, false));
        }
        return new FooterHolder(ItemFeedFooterBinding.inflate(inf, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = getItem(position);
        if (holder instanceof PostHolder && row.post != null) {
            PostCardBinder.bind(((PostHolder) holder).b, row.post, listener);
        } else if (holder instanceof FooterHolder) {
            bindFooter(((FooterHolder) holder).b, row.footer);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
        Row row = getItem(position);
        if (holder instanceof PostHolder && row.post != null && payloads.contains(PAYLOAD_FELT)) {
            PostHolder h = (PostHolder) holder;
            PostCardBinder.bind(h.b, row.post, listener);         // rebinds click targets to the new Post
            PostCardBinder.bindFelt(h.b, row.post, true);
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    private void bindFooter(ItemFeedFooterBinding b, @Nullable FeedState.Footer footer) {
        FeedState.Footer f = footer == null ? FeedState.Footer.NONE : footer;
        b.loading.setVisibility(f == FeedState.Footer.LOADING || f == FeedState.Footer.NONE ? View.VISIBLE : View.GONE);
        b.error.setVisibility(f == FeedState.Footer.ERROR ? View.VISIBLE : View.GONE);
        b.end.setVisibility(f == FeedState.Footer.END ? View.VISIBLE : View.GONE);
        b.retry.setOnClickListener(v -> onRetryMore.run());
    }

    static final class PostHolder extends RecyclerView.ViewHolder {
        final ItemConfessionCardBinding b;

        PostHolder(ItemConfessionCardBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }

    static final class FooterHolder extends RecyclerView.ViewHolder {
        final ItemFeedFooterBinding b;

        FooterHolder(ItemFeedFooterBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }

    private static final DiffUtil.ItemCallback<Row> DIFF = new DiffUtil.ItemCallback<Row>() {
        @Override
        public boolean areItemsTheSame(@NonNull Row a, @NonNull Row b) {
            if (a.post != null && b.post != null) return Objects.equals(a.post.postId, b.post.postId);
            return a.post == null && b.post == null;          // the one footer
        }

        @Override
        public boolean areContentsTheSame(@NonNull Row a, @NonNull Row b) {
            if (a.post == null || b.post == null) return a.footer == b.footer;
            return sameExceptFelt(a.post, b.post)
                    && a.post.likedByMe == b.post.likedByMe
                    && a.post.likeCount == b.post.likeCount
                    && Objects.equals(a.post.myReaction, b.post.myReaction)
                    && Objects.equals(a.post.reactions, b.post.reactions);
        }

        @Nullable
        @Override
        public Object getChangePayload(@NonNull Row a, @NonNull Row b) {
            if (a.post != null && b.post != null && sameExceptFelt(a.post, b.post)) return PAYLOAD_FELT;
            return null;
        }

        private boolean sameExceptFelt(Post x, Post y) {
            return Objects.equals(x.text, y.text)
                    && PostCardBinder.extrasSignature(x).equals(PostCardBinder.extrasSignature(y))
                    && x.commentsEnabled == y.commentsEnabled
                    && x.commentCount == y.commentCount
                    && x.isMine == y.isMine
                    && Objects.equals(x.createdAt, y.createdAt)
                    && Objects.equals(x.author == null ? null : x.author.alias, y.author == null ? null : y.author.alias)
                    && Objects.equals(x.author == null ? null : x.author.avatar, y.author == null ? null : y.author.avatar);
        }
    };
}
