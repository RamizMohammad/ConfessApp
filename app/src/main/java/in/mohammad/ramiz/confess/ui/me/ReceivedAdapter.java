package in.mohammad.ramiz.confess.ui.me;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Formats;
import in.mohammad.ramiz.confess.data.model.Comment;
import in.mohammad.ramiz.confess.data.model.Liker;
import in.mohammad.ramiz.confess.data.model.Post;
import in.mohammad.ramiz.confess.databinding.ItemFeedFooterBinding;
import in.mohammad.ramiz.confess.databinding.ItemReceivedGroupBinding;
import in.mohammad.ramiz.confess.databinding.ItemReceivedMoreBinding;
import in.mohammad.ramiz.confess.databinding.ItemReceivedPersonBinding;
import in.mohammad.ramiz.confess.databinding.ItemResponseBinding;
import in.mohammad.ramiz.confess.ui.components.Aura;

/** Letter (collapsible) → the people who felt it / their responses → more · open. */
public class ReceivedAdapter extends ListAdapter<ReceivedAdapter.Row, RecyclerView.ViewHolder> {

    public interface Listener {
        void onToggle(@NonNull Post post);

        void onPerson(@NonNull String alias);

        void onMorePeople(@NonNull String postId);

        void onOpenLetter(@NonNull String postId);

        void onRetryMore();
    }

    private static final int T_GROUP = 1, T_LIKER = 2, T_COMMENT = 3, T_MORE = 4, T_FOOTER = 5;

    /** What the row under a letter's people shows. */
    enum MoreKind { LOADING, MORE, FAILED, OPEN }

    static final class Row {
        final int type;
        @Nullable final Post post;
        final boolean expanded;
        @Nullable final Liker liker;
        @Nullable final Comment comment;
        @Nullable final String postId;
        @Nullable final MoreKind more;
        @Nullable final ReceivedViewModel.Footer footer;
        final String key;

        Row(int type, @Nullable Post post, boolean expanded, @Nullable Liker liker, @Nullable Comment comment,
            @Nullable String postId, @Nullable MoreKind more, @Nullable ReceivedViewModel.Footer footer, String key) {
            this.type = type;
            this.post = post;
            this.expanded = expanded;
            this.liker = liker;
            this.comment = comment;
            this.postId = postId;
            this.more = more;
            this.footer = footer;
            this.key = key;
        }
    }

    private final Listener listener;
    private final boolean felt;

    public ReceivedAdapter(@NonNull Listener listener, boolean felt) {
        super(DIFF);
        this.listener = listener;
        this.felt = felt;
    }

    public void submit(@NonNull ReceivedViewModel.State s, @Nullable Runnable done) {
        List<Row> rows = new ArrayList<>();
        for (Post p : s.posts) {
            ReceivedViewModel.Group g = s.groups.get(p.postId);
            boolean open = g != null && g.expanded;
            rows.add(new Row(T_GROUP, p, open, null, null, p.postId, null, null, "g:" + p.postId));
            if (!open) continue;
            if (felt) {
                for (Liker l : g.likers) {
                    rows.add(new Row(T_LIKER, null, false, l, null, p.postId, null, null,
                            "l:" + p.postId + ":" + l.alias));
                }
            } else {
                for (Comment c : g.comments) {
                    rows.add(new Row(T_COMMENT, null, false, null, c, p.postId, null, null, "c:" + c.commentId));
                }
            }
            MoreKind kind = g.loading ? MoreKind.LOADING : g.failed ? MoreKind.FAILED
                    : g.cursor != null ? MoreKind.MORE : MoreKind.OPEN;
            rows.add(new Row(T_MORE, null, false, null, null, p.postId, kind, null, "m:" + p.postId));
        }
        if (!s.posts.isEmpty() || s.footer != ReceivedViewModel.Footer.NONE) {
            rows.add(new Row(T_FOOTER, null, false, null, null, null, null, s.footer, "footer"));
        }
        submitList(rows, done);
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
            case T_GROUP: return new Holder<>(ItemReceivedGroupBinding.inflate(inf, parent, false));
            case T_LIKER: return new Holder<>(ItemReceivedPersonBinding.inflate(inf, parent, false));
            case T_COMMENT: {
                ItemResponseBinding b = ItemResponseBinding.inflate(inf, parent, false);
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) b.getRoot().getLayoutParams();
                lp.setMarginStart(Math.round(36 * parent.getResources().getDisplayMetrics().density));
                return new Holder<>(b);
            }
            case T_MORE: return new Holder<>(ItemReceivedMoreBinding.inflate(inf, parent, false));
            default: return new Holder<>(ItemFeedFooterBinding.inflate(inf, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = getItem(position);
        Object b = ((Holder<?>) holder).b;
        switch (row.type) {
            case T_GROUP: bindGroup((ItemReceivedGroupBinding) b, row); break;
            case T_LIKER: bindLiker((ItemReceivedPersonBinding) b, row); break;
            case T_COMMENT: bindComment((ItemResponseBinding) b, row); break;
            case T_MORE: bindMore((ItemReceivedMoreBinding) b, row); break;
            default: bindFooter((ItemFeedFooterBinding) b, row);
        }
    }

    private void bindGroup(ItemReceivedGroupBinding b, Row row) {
        Post p = row.post;
        if (p == null) return;
        Context ctx = b.getRoot().getContext();
        String text = p.text == null ? "" : p.text.trim();
        b.text.setText("“" + text + "”");
        b.time.setText(Formats.relative(p.createdAt));
        int n = felt ? p.likeCount : p.commentCount;
        b.count.setText(Formats.count(n));
        b.count.setCompoundDrawablesRelativeWithIntrinsicBounds(
                felt ? R.drawable.ic_heart_filled : R.drawable.ic_message, 0, 0, 0);
        b.count.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(
                ctx.getColor(felt ? R.color.brick : R.color.paper_ink)));
        b.chevron.animate().rotation(row.expanded ? 180f : 0f).setDuration(180).start();
        b.card.setContentDescription(text + ", " + n + ". "
                + ctx.getString(row.expanded ? R.string.cd_collapse : R.string.cd_expand));
        b.card.setOnClickListener(v -> listener.onToggle(p));
    }

    private void bindLiker(ItemReceivedPersonBinding b, Row row) {
        Liker l = row.liker;
        if (l == null) return;
        b.avatar.setAvatar(l.avatar);
        b.alias.setText("@" + l.alias);
        b.time.setText(Formats.relative(l.likedAt));
        b.row.setOnClickListener(v -> listener.onPerson(l.alias));
    }

    private void bindComment(ItemResponseBinding b, Row row) {
        Comment c = row.comment;
        if (c == null) return;
        String alias = c.author != null && c.author.alias != null ? c.author.alias : "";
        String avatar = c.author != null ? c.author.avatar : null;
        b.avatar.setAvatar(avatar);
        b.alias.setText("@" + alias);
        b.authorTag.setVisibility(c.isPostAuthor ? View.VISIBLE : View.GONE);
        b.time.setText(Formats.relative(c.createdAt));
        b.text.setText(c.text);
        b.text.setTextIsSelectable(false);
        b.stripe.setBackgroundColor(ColorUtils.setAlphaComponent(Aura.resolve(avatar).glow, 0xCC));
        b.more.setVisibility(View.GONE);
        b.avatar.setOnClickListener(v -> listener.onPerson(alias));
        b.alias.setOnClickListener(v -> listener.onPerson(alias));
        b.card.setOnClickListener(v -> listener.onOpenLetter(c.postId));
    }

    private void bindMore(ItemReceivedMoreBinding b, Row row) {
        MoreKind k = row.more == null ? MoreKind.OPEN : row.more;
        String postId = row.postId == null ? "" : row.postId;
        b.loading.setVisibility(k == MoreKind.LOADING ? View.VISIBLE : View.GONE);
        b.action.setVisibility(k == MoreKind.LOADING ? View.GONE : View.VISIBLE);
        switch (k) {
            case MORE:
                b.action.setText(R.string.received_show_more);
                b.action.setIconResource(R.drawable.ic_chevron_down);
                b.action.setOnClickListener(v -> listener.onMorePeople(postId));
                break;
            case FAILED:
                b.action.setText(R.string.received_load_failed);
                b.action.setIconResource(R.drawable.ic_refresh);
                b.action.setOnClickListener(v -> listener.onMorePeople(postId));
                break;
            default:
                b.action.setText(R.string.received_open_letter);
                b.action.setIconResource(R.drawable.ic_arrow_right);
                b.action.setOnClickListener(v -> listener.onOpenLetter(postId));
        }
    }

    private void bindFooter(ItemFeedFooterBinding b, Row row) {
        ReceivedViewModel.Footer f = row.footer == null ? ReceivedViewModel.Footer.NONE : row.footer;
        b.loading.setVisibility(f == ReceivedViewModel.Footer.LOADING || f == ReceivedViewModel.Footer.NONE
                ? View.VISIBLE : View.GONE);
        b.error.setVisibility(f == ReceivedViewModel.Footer.ERROR ? View.VISIBLE : View.GONE);
        b.end.setVisibility(f == ReceivedViewModel.Footer.END ? View.VISIBLE : View.GONE);
        b.retry.setOnClickListener(v -> listener.onRetryMore());
    }

    static final class Holder<B extends ViewBinding> extends RecyclerView.ViewHolder {
        final B b;

        Holder(B b) {
            super(b.getRoot());
            this.b = b;
        }
    }

    private static final DiffUtil.ItemCallback<Row> DIFF = new DiffUtil.ItemCallback<Row>() {
        @Override
        public boolean areItemsTheSame(@NonNull Row a, @NonNull Row b) {
            return a.key.equals(b.key);
        }

        @Override
        public boolean areContentsTheSame(@NonNull Row a, @NonNull Row b) {
            switch (a.type) {
                case T_GROUP:
                    return a.expanded == b.expanded && a.post != null && b.post != null
                            && a.post.likeCount == b.post.likeCount && a.post.commentCount == b.post.commentCount;
                case T_MORE:
                    return a.more == b.more;
                case T_FOOTER:
                    return a.footer == b.footer;
                case T_COMMENT:
                    return a.comment != null && b.comment != null && Objects.equals(a.comment.text, b.comment.text);
                default:
                    return true;
            }
        }
    };
}
