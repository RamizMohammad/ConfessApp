package in.mohammad.ramiz.confess.ui.profile;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Formats;
import in.mohammad.ramiz.confess.data.model.Post;
import in.mohammad.ramiz.confess.databinding.ItemConfessionCardBinding;
import in.mohammad.ramiz.confess.databinding.ItemProfileHeaderBinding;
import in.mohammad.ramiz.confess.databinding.ItemResponsesFooterBinding;
import in.mohammad.ramiz.confess.ui.feed.PostCardBinder;

/** Profile header, the letters (paper cards), and a footer (loading / empty / end). */
public class ProfileAdapter extends ListAdapter<ProfileAdapter.Row, RecyclerView.ViewHolder> {

    public interface Listener extends PostCardBinder.Listener {
        void onEditAura();

        void onEditAbout();

        /** Follow / Following button on someone else's profile. */
        void onFollow(boolean currentlyFollowing, @NonNull String alias);

        void onRetryMore();

        /** The Unblock button on a profile I blocked. */
        default void onUnblock(@NonNull String alias) { }
    }

    private static final int T_HEADER = 1, T_POST = 2, T_FOOTER = 3;
    private static final String PAYLOAD_FELT = "felt";

    static final class Row {
        final int type;
        @Nullable final ProfileViewModel.Header header;
        @Nullable final Post post;
        @Nullable final ProfileViewModel.Footer footer;
        final int count;

        Row(int type, @Nullable ProfileViewModel.Header header, @Nullable Post post,
            @Nullable ProfileViewModel.Footer footer, int count) {
            this.type = type;
            this.header = header;
            this.post = post;
            this.footer = footer;
            this.count = count;
        }
    }

    private final Listener listener;
    private final boolean withHeader;
    @Nullable private ProfileViewModel.Header lastHeader;

    /** withHeader = false → just the letters (the "My letters" screen). */
    public ProfileAdapter(@NonNull Listener listener, boolean withHeader) {
        super(DIFF);
        this.listener = listener;
        this.withHeader = withHeader;
    }

    public void submit(@NonNull ProfileViewModel.State s, @Nullable Runnable done) {
        List<Row> rows = new ArrayList<>();
        if (s.header != null) {
            lastHeader = s.header;
            if (withHeader) rows.add(new Row(T_HEADER, s.header, null, null, 0));
            for (Post p : s.posts) rows.add(new Row(T_POST, null, p, null, 0));
            rows.add(new Row(T_FOOTER, null, null, s.footer, s.posts.size()));
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
        if (type == T_HEADER) return new Holder<>(ItemProfileHeaderBinding.inflate(inf, parent, false));
        if (type == T_POST) return new Holder<>(ItemConfessionCardBinding.inflate(inf, parent, false));
        return new Holder<>(ItemResponsesFooterBinding.inflate(inf, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = getItem(position);
        Object b = ((Holder<?>) holder).b;
        if (row.type == T_HEADER && row.header != null) {
            bindHeader((ItemProfileHeaderBinding) b, row.header);
        } else if (row.type == T_POST && row.post != null) {
            PostCardBinder.bind((ItemConfessionCardBinding) b, row.post, listener);
        } else if (row.type == T_FOOTER) {
            bindFooter((ItemResponsesFooterBinding) b, row);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
        Row row = getItem(position);
        if (row.type == T_POST && row.post != null && payloads.contains(PAYLOAD_FELT)) {
            ItemConfessionCardBinding b = (ItemConfessionCardBinding) ((Holder<?>) holder).b;
            PostCardBinder.bind(b, row.post, listener);
            PostCardBinder.bindFelt(b, row.post, true);
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    private void bindHeader(ItemProfileHeaderBinding b, ProfileViewModel.Header h) {
        Context ctx = b.getRoot().getContext();
        b.avatar.setAvatar(h.avatar);
        b.avatar.setStoryAlias(h.alias);
        b.alias.setText("@" + h.alias);

        boolean hasAbout = h.about != null && !h.about.trim().isEmpty();
        if (hasAbout) {
            b.about.setVisibility(View.VISIBLE);
            b.about.setText("“" + h.about.trim() + "”");
            b.about.setTextColor(ctx.getColor(R.color.text_secondary));
            b.about.setOnClickListener(h.isMe ? v -> listener.onEditAbout() : null);
            b.about.setClickable(h.isMe);
        } else if (h.isMe) {
            b.about.setVisibility(View.VISIBLE);
            b.about.setText(R.string.profile_add_about);
            b.about.setTextColor(ctx.getColor(R.color.amber));
            b.about.setOnClickListener(v -> listener.onEditAbout());
        } else {
            b.about.setVisibility(View.GONE);
        }

        String since = Formats.monthYear(h.createdAt);
        b.since.setVisibility(since.isEmpty() ? View.GONE : View.VISIBLE);
        b.since.setText(ctx.getString(R.string.me_since, since.toUpperCase(java.util.Locale.getDefault())));

        b.actions.setVisibility(h.isMe ? View.VISIBLE : View.GONE);
        b.followBox.setVisibility(h.isMe ? View.GONE : View.VISIBLE);
        if (!h.isMe && h.blocked) {
            b.followBtn.setVisibility(View.VISIBLE);
            b.followingBtn.setVisibility(View.GONE);
            b.followBtn.setText(R.string.unblock_btn);
            b.followBtn.setOnClickListener(v -> listener.onUnblock(h.alias));
        } else if (!h.isMe) {
            b.followBtn.setText(R.string.follow);
            b.followBtn.setVisibility(h.isFollowing ? View.GONE : View.VISIBLE);
            b.followingBtn.setVisibility(h.isFollowing ? View.VISIBLE : View.GONE);
            b.followBtn.setOnClickListener(v -> listener.onFollow(false, h.alias));
            b.followingBtn.setOnClickListener(v -> listener.onFollow(true, h.alias));
        }
        b.followCounts.setText(ctx.getString(R.string.profile_follow_counts,
                Formats.count(h.followers), ctx.getString(R.string.followers_label),
                Formats.count(h.following), ctx.getString(R.string.following_label)));
        b.editAura.setOnClickListener(v -> listener.onEditAura());
        b.editAbout.setOnClickListener(v -> listener.onEditAbout());

        b.postCount.setText(Formats.count(h.postCount));
        b.feltCell.setVisibility(h.feltCount != null ? View.VISIBLE : View.GONE);
        if (h.feltCount != null) b.feltCount.setText(Formats.count(h.feltCount));

        b.lettersTitle.setText(ctx.getString(h.isMe ? R.string.profile_my_letters : R.string.profile_their_letters,
                Formats.count(h.postCount)));
    }

    private void bindFooter(ItemResponsesFooterBinding b, Row row) {
        Context ctx = b.getRoot().getContext();
        ProfileViewModel.Footer f = row.footer == null ? ProfileViewModel.Footer.NONE : row.footer;
        b.loading.setVisibility(f == ProfileViewModel.Footer.LOADING || f == ProfileViewModel.Footer.NONE
                ? View.VISIBLE : View.GONE);
        b.error.setVisibility(f == ProfileViewModel.Footer.ERROR ? View.VISIBLE : View.GONE);
        b.end.setVisibility(f == ProfileViewModel.Footer.END ? View.VISIBLE : View.GONE);
        b.errorText.setText(R.string.feed_more_failed);
        b.retry.setOnClickListener(v -> listener.onRetryMore());
        if (f != ProfileViewModel.Footer.END) return;

        boolean me = lastHeader != null && lastHeader.isMe;
        if (row.count == 0 && lastHeader != null && lastHeader.blocked) {
            b.endTitle.setText(R.string.profile_blocked_title);
            b.endBody.setText(ctx.getString(R.string.profile_blocked_body, "@" + lastHeader.alias));
            b.endBody.setVisibility(View.VISIBLE);
        } else if (row.count == 0) {
            b.endTitle.setText(me ? R.string.profile_no_letters_me : R.string.profile_no_letters);
            b.endBody.setText(me ? ctx.getString(R.string.profile_no_letters_me_body)
                    : ctx.getString(R.string.profile_no_letters_body, lastHeader != null ? "@" + lastHeader.alias : ""));
            b.endBody.setVisibility(View.VISIBLE);
        } else {
            b.endTitle.setText(R.string.profile_all_letters);
            b.endBody.setVisibility(View.GONE);
        }
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
            if (a.type != b.type) return false;
            if (a.type == T_POST && a.post != null && b.post != null) return Objects.equals(a.post.postId, b.post.postId);
            return true;
        }

        @Override
        public boolean areContentsTheSame(@NonNull Row a, @NonNull Row b) {
            switch (a.type) {
                case T_HEADER:
                    return a.header == b.header;               // a new Header object means it changed
                case T_POST:
                    return a.post != null && b.post != null && same(a.post, b.post)
                            && a.post.likedByMe == b.post.likedByMe && a.post.likeCount == b.post.likeCount
                            && Objects.equals(a.post.myReaction, b.post.myReaction) && Objects.equals(a.post.reactions, b.post.reactions);
                default:
                    return a.footer == b.footer && a.count == b.count;
            }
        }

        @Nullable
        @Override
        public Object getChangePayload(@NonNull Row a, @NonNull Row b) {
            if (a.type == T_POST && a.post != null && b.post != null && same(a.post, b.post)) return PAYLOAD_FELT;
            return null;
        }

        private boolean same(Post x, Post y) {
            return Objects.equals(x.text, y.text) && x.commentsEnabled == y.commentsEnabled
                    && x.commentCount == y.commentCount
                    && PostCardBinder.extrasSignature(x).equals(PostCardBinder.extrasSignature(y));
        }
    };
}
