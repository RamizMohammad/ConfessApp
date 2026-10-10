package in.mohammad.ramiz.confess.ui.feed;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Formats;
import in.mohammad.ramiz.confess.data.model.Post;
import in.mohammad.ramiz.confess.databinding.ItemConfessionCardBinding;

/**
 * Fills the paper card (item_confession_card) from a {@link Post}.
 * Shared by the feed, "my letters" and the detail header so they always look alike.
 */
public final class PostCardBinder {

    public interface Listener {
        void onOpen(@NonNull Post post, boolean toResponses);

        void onFelt(@NonNull Post post);

        void onMore(@NonNull Post post, @NonNull View anchor);

        /** Tapped the writer's aura or alias. Screens that can't open profiles ignore it. */
        default void onAuthor(@NonNull Post post) { }
    }

    private PostCardBinder() { }

    public static void bind(@NonNull ItemConfessionCardBinding b, @NonNull Post post, @NonNull Listener l) {
        Context ctx = b.getRoot().getContext();
        String alias = post.author != null && post.author.alias != null ? post.author.alias : "";

        b.avatar.setAvatar(post.author != null ? post.author.avatar : null);
        b.alias.setText("@" + alias);
        String when = Formats.relative(post.createdAt);
        b.time.setText(post.isMine ? when + " · " + ctx.getString(R.string.feed_you) : when);
        b.text.setText(quoted(post.text));
        b.commentsClosed.setVisibility(post.commentsEnabled ? View.GONE : View.VISIBLE);
        b.responses.setText(post.commentCount > 0 ? Formats.count(post.commentCount) : "");
        bindFelt(b, post, false);

        b.card.setContentDescription(ctx.getString(R.string.cd_card, alias, when));
        b.card.setOnClickListener(v -> l.onOpen(post, false));
        b.responses.setOnClickListener(v -> l.onOpen(post, true));
        b.felt.setOnClickListener(v -> l.onFelt(post));
        b.more.setOnClickListener(v -> l.onMore(post, v));
        View.OnClickListener author = v -> l.onAuthor(post);
        b.avatar.setOnClickListener(author);
        b.alias.setOnClickListener(author);
        b.avatar.setContentDescription(ctx.getString(R.string.cd_author, "@" + alias));
    }

    /**
     * The big version on the confession screen: full text, larger type, "1.4k felt" /
     * "86 responses" labels, no overflow (the top bar has it), card itself not clickable.
     */
    public static void bindDetail(@NonNull ItemConfessionCardBinding b, @NonNull Post post, @NonNull Listener l) {
        bind(b, post, l);
        Context ctx = b.getRoot().getContext();
        b.text.setMaxLines(Integer.MAX_VALUE);
        b.text.setEllipsize(null);
        b.text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 22);
        b.text.setLineSpacing(0f, 1.15f);
        b.text.setTextIsSelectable(true);
        b.more.setVisibility(View.GONE);
        b.card.setOnClickListener(null);
        b.card.setClickable(false);
        b.card.setFocusable(false);
        b.responses.setText(ctx.getResources().getQuantityString(R.plurals.detail_responses,
                post.commentCount, Formats.count(post.commentCount)));
        detailFelt(b, post);
    }

    /** Felt label on the detail card: "1.4k felt" (or just "Felt"). */
    public static void detailFelt(@NonNull ItemConfessionCardBinding b, @NonNull Post post) {
        Context ctx = b.getRoot().getContext();
        b.felt.setText(post.likeCount > 0
                ? ctx.getString(R.string.detail_felt_count, Formats.count(post.likeCount))
                : ctx.getString(R.string.cd_felt));
    }

    /** Only the felt button — used for the quick in-place update after a tap. */
    public static void bindFelt(@NonNull ItemConfessionCardBinding b, @NonNull Post post, boolean animate) {
        Context ctx = b.getRoot().getContext();
        int color = ContextCompat.getColor(ctx, post.likedByMe ? R.color.brick : R.color.paper_meta);
        b.felt.setIconResource(post.likedByMe ? R.drawable.ic_heart_filled : R.drawable.ic_heart);
        b.felt.setIconTint(ColorStateList.valueOf(color));
        b.felt.setTextColor(color);
        b.felt.setText(post.likeCount > 0 ? Formats.count(post.likeCount) : "");
        b.felt.setContentDescription(ctx.getString(post.likedByMe ? R.string.cd_felt_on : R.string.cd_felt)
                + (post.likeCount > 0 ? ", " + post.likeCount : ""));
        if (animate && post.likedByMe) {
            b.felt.setScaleX(0.8f);
            b.felt.setScaleY(0.8f);
            b.felt.animate().scaleX(1f).scaleY(1f).setDuration(260)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(3f)).start();
        }
    }

    /** Wrap in curly quotes, like a letter excerpt — unless the writer already did. */
    static String quoted(String text) {
        if (text == null) return "";
        String t = text.trim();
        if (t.isEmpty()) return "";
        char first = t.charAt(0), last = t.charAt(t.length() - 1);
        boolean already = (first == '"' || first == '“') && (last == '"' || last == '”');
        return already ? t : "“" + t + "”";
    }
}
