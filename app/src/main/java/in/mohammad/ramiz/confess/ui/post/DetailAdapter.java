package in.mohammad.ramiz.confess.ui.post;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Formats;
import in.mohammad.ramiz.confess.data.model.Comment;
import in.mohammad.ramiz.confess.data.model.Post;
import in.mohammad.ramiz.confess.databinding.ItemConfessionCardBinding;
import in.mohammad.ramiz.confess.databinding.ItemResponseBinding;
import in.mohammad.ramiz.confess.databinding.ItemResponsesFooterBinding;
import in.mohammad.ramiz.confess.databinding.ItemResponsesHeaderBinding;
import in.mohammad.ramiz.confess.ui.components.Aura;
import in.mohammad.ramiz.confess.ui.feed.PostCardBinder;

/** The letter (paper card), "RESPONSES (n)", the response cards, and a footer. */
public class DetailAdapter extends ListAdapter<DetailAdapter.Row, RecyclerView.ViewHolder> {

    public interface Listener extends PostCardBinder.Listener {
        void onResponseMore(@NonNull Comment c, @NonNull View anchor);

        void onResponseAuthor(@NonNull Comment c);

        void onRetryMore();
    }

    private static final int T_POST = 1, T_HEADER = 2, T_RESPONSE = 3, T_FOOTER = 4;

    static final class Row {
        final int type;
        @Nullable final Post post;
        @Nullable final Comment comment;
        final int count;
        @Nullable final PostDetailViewModel.Footer footer;
        final boolean closed;
        final boolean deletable;

        Row(int type, @Nullable Post post, @Nullable Comment comment, int count,
            @Nullable PostDetailViewModel.Footer footer, boolean closed, boolean deletable) {
            this.type = type;
            this.post = post;
            this.comment = comment;
            this.count = count;
            this.footer = footer;
            this.closed = closed;
            this.deletable = deletable;
        }
    }

    private final Listener listener;

    public DetailAdapter(@NonNull Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    public void submit(@NonNull PostDetailViewModel.State s, @NonNull PostDetailViewModel vm, @Nullable Runnable done) {
        List<Row> rows = new ArrayList<>();
        if (s.post != null) {
            rows.add(new Row(T_POST, s.post, null, 0, null, false, false));
            rows.add(new Row(T_HEADER, null, null, s.post.commentCount, null, false, false));
            for (Comment c : s.comments) rows.add(new Row(T_RESPONSE, null, c, 0, null, false, vm.canDelete(c)));
            rows.add(new Row(T_FOOTER, null, null, s.comments.size(), s.footer, !s.post.commentsEnabled, false));
        }
        submitList(rows, done);
    }

    /** Adapter position of the last response (for scrolling to a new one). */
    public int lastResponsePosition() {
        for (int i = getItemCount() - 1; i >= 0; i--) if (getItem(i).type == T_RESPONSE) return i;
        return -1;
    }

    /** Position of the "RESPONSES" header. */
    public int headerPosition() {
        for (int i = 0; i < getItemCount(); i++) if (getItem(i).type == T_HEADER) return i;
        return -1;
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        switch (type) {
            case T_POST: return new Holder<>(ItemConfessionCardBinding.inflate(inf, parent, false));
            case T_HEADER: return new Holder<>(ItemResponsesHeaderBinding.inflate(inf, parent, false));
            case T_RESPONSE: return new Holder<>(ItemResponseBinding.inflate(inf, parent, false));
            default: return new Holder<>(ItemResponsesFooterBinding.inflate(inf, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = getItem(position);
        Object b = ((Holder<?>) holder).b;
        if (row.type == T_POST && row.post != null) {
            PostCardBinder.bindDetail((ItemConfessionCardBinding) b, row.post, listener);
        } else if (row.type == T_HEADER) {
            ItemResponsesHeaderBinding h = (ItemResponsesHeaderBinding) b;
            h.title.setText(h.getRoot().getContext().getString(R.string.detail_responses_header, Formats.count(row.count)));
        } else if (row.type == T_RESPONSE && row.comment != null) {
            bindResponse((ItemResponseBinding) b, row.comment, row.deletable);
        } else if (row.type == T_FOOTER) {
            bindFooter((ItemResponsesFooterBinding) b, row);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
        Row row = getItem(position);
        if (row.type == T_POST && row.post != null && payloads.contains(PAYLOAD_FELT)) {
            ItemConfessionCardBinding b = (ItemConfessionCardBinding) ((Holder<?>) holder).b;
            PostCardBinder.bindDetail(b, row.post, listener);
            PostCardBinder.bindFelt(b, row.post, true);
            PostCardBinder.detailFelt(b, row.post);
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    private void bindResponse(ItemResponseBinding b, Comment c, boolean deletable) {
        String alias = c.author != null && c.author.alias != null ? c.author.alias : "";
        String avatar = c.author != null ? c.author.avatar : null;
        b.avatar.setAvatar(avatar);
        b.avatar.setStoryAlias(alias);
        b.alias.setText("@" + alias);
        b.authorTag.setVisibility(c.isPostAuthor ? View.VISIBLE : View.GONE);
        String when = Formats.relative(c.createdAt);
        b.time.setText(c.isMine ? when + " · " + b.getRoot().getContext().getString(R.string.feed_you) : when);
        b.text.setText(c.text);
        b.stripe.setBackgroundColor(ColorUtils.setAlphaComponent(Aura.resolve(avatar).glow, 0xCC));
        b.avatar.setOnClickListener(v -> listener.onResponseAuthor(c));
        b.alias.setOnClickListener(v -> listener.onResponseAuthor(c));
        // Everyone gets the menu: delete (mine / on my confession), report and block (anyone else's).
        b.more.setVisibility(View.VISIBLE);
        b.more.setOnClickListener(v -> listener.onResponseMore(c, v));
        b.card.setOnLongClickListener(v -> { listener.onResponseMore(c, b.more); return true; });
    }

    private void bindFooter(ItemResponsesFooterBinding b, Row row) {
        PostDetailViewModel.Footer f = row.footer == null ? PostDetailViewModel.Footer.LOADING : row.footer;
        b.loading.setVisibility(f == PostDetailViewModel.Footer.LOADING ? View.VISIBLE : View.GONE);
        b.error.setVisibility(f == PostDetailViewModel.Footer.ERROR ? View.VISIBLE : View.GONE);
        b.end.setVisibility(f == PostDetailViewModel.Footer.END ? View.VISIBLE : View.GONE);
        b.retry.setOnClickListener(v -> listener.onRetryMore());
        if (f != PostDetailViewModel.Footer.END) return;

        if (row.closed) {
            b.endTitle.setText(row.count == 0 ? R.string.responses_empty_title : R.string.responses_kindness);
            b.endBody.setText(R.string.responses_closed_end);
        } else if (row.count == 0) {
            b.endTitle.setText(R.string.responses_empty_title);
            b.endBody.setText(R.string.responses_empty_body);
        } else {
            b.endTitle.setText(R.string.responses_kindness);
            b.endBody.setText("");
        }
        b.endBody.setVisibility(b.endBody.getText().length() == 0 ? View.GONE : View.VISIBLE);
    }

    static final class Holder<B extends androidx.viewbinding.ViewBinding> extends RecyclerView.ViewHolder {
        final B b;

        Holder(B b) {
            super(b.getRoot());
            this.b = b;
        }
    }

    private static final String PAYLOAD_FELT = "felt";

    private static final DiffUtil.ItemCallback<Row> DIFF = new DiffUtil.ItemCallback<Row>() {
        @Override
        public boolean areItemsTheSame(@NonNull Row a, @NonNull Row b) {
            if (a.type != b.type) return false;
            if (a.type == T_RESPONSE && a.comment != null && b.comment != null) {
                return Objects.equals(a.comment.commentId, b.comment.commentId);
            }
            return true;                       // one post, one header, one footer
        }

        @Override
        public boolean areContentsTheSame(@NonNull Row a, @NonNull Row b) {
            switch (a.type) {
                case T_POST:
                    return a.post != null && b.post != null && samePostExceptFelt(a.post, b.post)
                            && a.post.likedByMe == b.post.likedByMe && a.post.likeCount == b.post.likeCount
                            && Objects.equals(a.post.myReaction, b.post.myReaction) && Objects.equals(a.post.reactions, b.post.reactions);
                case T_HEADER:
                    return a.count == b.count;
                case T_RESPONSE:
                    return a.deletable == b.deletable && a.comment != null && b.comment != null
                            && Objects.equals(a.comment.text, b.comment.text);
                default:
                    return a.footer == b.footer && a.count == b.count && a.closed == b.closed;
            }
        }

        @Nullable
        @Override
        public Object getChangePayload(@NonNull Row a, @NonNull Row b) {
            if (a.type == T_POST && a.post != null && b.post != null && samePostExceptFelt(a.post, b.post)) {
                return PAYLOAD_FELT;
            }
            return null;
        }

        private boolean samePostExceptFelt(Post x, Post y) {
            return Objects.equals(x.text, y.text) && x.commentsEnabled == y.commentsEnabled
                    && x.commentCount == y.commentCount && x.isMine == y.isMine
                    && PostCardBinder.extrasSignature(x).equals(PostCardBinder.extrasSignature(y));
        }
    };
}
