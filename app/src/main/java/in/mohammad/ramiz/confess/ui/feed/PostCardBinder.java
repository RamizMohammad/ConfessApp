package in.mohammad.ramiz.confess.ui.feed;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Formats;
import in.mohammad.ramiz.confess.core.Haptics;
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

        /** Picked from the long-press reaction bar (felt · hug · same · strength · tears). */
        default void onReact(@NonNull Post post, @NonNull String reaction) { }

        void onMore(@NonNull Post post, @NonNull View anchor);

        /** Tapped the writer's aura or alias. Screens that can't open profiles ignore it. */
        default void onAuthor(@NonNull Post post) { }
    }

    private PostCardBinder() { }

    public static void bind(@NonNull ItemConfessionCardBinding b, @NonNull Post post, @NonNull Listener l) {
        Context ctx = b.getRoot().getContext();
        String alias = post.author != null && post.author.alias != null ? post.author.alias : "";

        b.avatar.setAvatar(post.author != null ? post.author.avatar : null);
        b.avatar.setStoryAlias(alias);
        b.alias.setText("@" + alias);
        String when = Formats.relative(post.createdAt);
        b.time.setText(post.isMine ? when + " · " + ctx.getString(R.string.feed_you) : when);
        b.text.setText(post.text == null ? "" : post.text.trim());
        bindResponses(b, post);
        b.responses.setText(post.commentCount > 0 ? Formats.count(post.commentCount) : "");
        bindFelt(b, post, false);

        b.card.setContentDescription(ctx.getString(R.string.cd_card, alias, when));
        bindTaps(b, post, l, true);
        b.responses.setOnClickListener(v -> l.onOpen(post, true));
        b.felt.setOnClickListener(v -> {
            if (Reactions.mine(post) == null) burst(b, Reactions.FELT);
            l.onFelt(post);
        });
        ReactionPicker.attach(b.felt, () -> Reactions.mine(post), r -> {
            l.onReact(post, r);
            if (!r.equals(Reactions.mine(post))) burst(b, r);
        });
        // Mine: the menu only deletes → bin icon. Theirs: report → flag icon.
        b.more.setIconResource(post.isMine ? R.drawable.ic_trash : R.drawable.ic_flag);
        b.more.setContentDescription(ctx.getString(post.isMine ? R.string.cd_delete_menu : R.string.cd_report_menu));
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
        b.text.setTextIsSelectable(false);           // double-tap = feel it, not "select text"; long-press copies
        b.more.setVisibility(View.GONE);
        bindTaps(b, post, l, false);
        b.responses.setText(ctx.getResources().getQuantityString(R.plurals.detail_responses,
                post.commentCount, Formats.count(post.commentCount)));
        detailFelt(b, post);
    }

    // ── Taps on the card ─────────────────────────────────────────────────────

    /**
     * One tap opens the confession (feed) or does nothing (detail); two quick taps feel it
     * with a heart burst; a long press copies the text. Done on the click listener (not a
     * touch listener) so the card keeps its press animation and TalkBack still works.
     */
    private static void bindTaps(@NonNull ItemConfessionCardBinding b, @NonNull Post post,
                                 @NonNull Listener l, boolean opens) {
        long window = android.view.ViewConfiguration.getDoubleTapTimeout();
        b.card.setClickable(true);
        b.card.setFocusable(true);
        b.card.setOnClickListener(v -> {
            Runnable pending = (Runnable) v.getTag(R.id.tag_single_tap);
            if (pending != null) {                                    // second tap in time → double tap
                v.removeCallbacks(pending);
                v.setTag(R.id.tag_single_tap, null);
                doubleTap(b, post, l);
                return;
            }
            Runnable single = () -> {
                v.setTag(R.id.tag_single_tap, null);
                if (opens) l.onOpen(post, false);
            };
            v.setTag(R.id.tag_single_tap, single);
            v.postDelayed(single, window);
        });
        b.card.setOnLongClickListener(v -> {
            copy(v.getContext(), post.text);
            return true;
        });
    }

    /** Double tap = ❤️ (never takes a reaction back, like every app people know). */
    private static void doubleTap(@NonNull ItemConfessionCardBinding b, @NonNull Post post, @NonNull Listener l) {
        String mine = Reactions.mine(post);
        if (mine == null) l.onFelt(post);                             // the screen ticks
        else Haptics.tick(b.getRoot().getContext());
        burst(b, mine != null ? mine : Reactions.FELT);
    }

    private static void copy(@NonNull Context ctx, @androidx.annotation.Nullable String text) {
        if (text == null || text.trim().isEmpty()) return;
        android.content.ClipboardManager cm =
                (android.content.ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) return;
        cm.setPrimaryClip(android.content.ClipData.newPlainText("confession", text.trim()));
        Haptics.tick(ctx);
        // Android 13+ shows its own "copied" chip; earlier versions get a toast.
        if (android.os.Build.VERSION.SDK_INT < 33) {
            android.widget.Toast.makeText(ctx, R.string.copied, android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    // ── Like burst ───────────────────────────────────────────────────────────

    /**
     * A big heart (or the chosen reaction) pops in the middle of the card and floats away,
     * with a ring of small amber sparks flying out over the card.
     */
    public static void burst(@NonNull ItemConfessionCardBinding b, @NonNull String reaction) {
        Context ctx = b.getRoot().getContext();
        float d = ctx.getResources().getDisplayMetrics().density;
        android.widget.FrameLayout layer = b.burstLayer;

        // Centre: the heart image for ❤️, the emoji for the others.
        View centre;
        if (Reactions.FELT.equals(reaction)) {
            b.burst.animate().cancel();
            centre = b.burst;
        } else {
            android.widget.TextView e = new android.widget.TextView(ctx);
            e.setText(Reactions.emoji(reaction));
            e.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 64);
            android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.Gravity.CENTER);
            e.setLayoutParams(lp);
            layer.addView(e);
            centre = e;
        }
        centre.setVisibility(View.VISIBLE);
        centre.setAlpha(0f);
        centre.setScaleX(0.2f);
        centre.setScaleY(0.2f);
        centre.setTranslationY(0f);
        centre.animate().alpha(1f).scaleX(1.15f).scaleY(1.15f).setStartDelay(0).setDuration(240)
                .setInterpolator(new android.view.animation.OvershootInterpolator(3f))
                .withEndAction(() -> centre.animate().alpha(0f).scaleX(1.5f).scaleY(1.5f)
                        .translationY(-36 * d).setStartDelay(220).setDuration(320)
                        .setInterpolator(new android.view.animation.AccelerateInterpolator())
                        .withEndAction(() -> {
                            centre.animate().setStartDelay(0);   // reset for the next burst
                            if (centre == b.burst) centre.setVisibility(View.GONE);
                            else layer.removeView(centre);
                        }).start())
                .start();

        // Sparks: small amber hearts flying out in a ring.
        int sparks = 8;
        int size = Math.round(14 * d);
        float reach = 70 * d;
        java.util.Random rnd = new java.util.Random();
        for (int i = 0; i < sparks; i++) {
            android.widget.ImageView s = new android.widget.ImageView(ctx);
            s.setImageResource(R.drawable.ic_heart_filled);
            s.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(ctx,
                    i % 2 == 0 ? R.color.amber : R.color.story_gold)));
            android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(size, size,
                    android.view.Gravity.CENTER);
            s.setLayoutParams(lp);
            s.setAlpha(0f);
            layer.addView(s);
            double angle = (Math.PI * 2 * i / sparks) + rnd.nextDouble() * 0.4;
            float dist = reach * (0.75f + rnd.nextFloat() * 0.5f);
            s.animate().alpha(1f).setDuration(80).setStartDelay(60).withEndAction(() ->
                    s.animate().translationX((float) Math.cos(angle) * dist)
                            .translationY((float) Math.sin(angle) * dist - 10 * d)
                            .rotation(rnd.nextInt(90) - 45)
                            .scaleX(0.4f).scaleY(0.4f).alpha(0f)
                            .setDuration(520).setStartDelay(0)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator())
                            .withEndAction(() -> layer.removeView(s)).start()).start();
        }
    }

    /** Responses closed → the bubble is struck through (no "comments closed" label). */
    private static void bindResponses(@NonNull ItemConfessionCardBinding b, @NonNull Post post) {
        Context ctx = b.getRoot().getContext();
        b.responses.setIconResource(post.commentsEnabled ? R.drawable.ic_message : R.drawable.ic_message_off);
        b.responses.setContentDescription(ctx.getString(post.commentsEnabled ? R.string.cd_responses
                : R.string.cd_responses_closed));
    }

    /** Felt label on the detail card: "1.4k felt" (or just "Felt"). */
    public static void detailFelt(@NonNull ItemConfessionCardBinding b, @NonNull Post post) {
        Context ctx = b.getRoot().getContext();
        String label = post.likeCount > 0
                ? ctx.getString(R.string.detail_felt_count, Formats.count(post.likeCount))
                : ctx.getString(R.string.cd_felt);
        String emoji = Reactions.summary(post, 3);
        b.felt.setText(emoji.isEmpty() ? label : emoji + "  " + label);
    }

    /**
     * Only the felt button — used for the quick in-place update after a tap.
     *   none  → outline heart · "12"
     *   ❤️    → amber filled heart · "13"
     *   other → that emoji (no heart) · "🫂 13"
     * Other people's non-heart reactions show as small emoji before the count.
     */
    public static void bindFelt(@NonNull ItemConfessionCardBinding b, @NonNull Post post, boolean animate) {
        Context ctx = b.getRoot().getContext();
        String mine = Reactions.mine(post);
        int color = ContextCompat.getColor(ctx, mine != null ? R.color.amber : R.color.paper_meta);
        if (mine == null || Reactions.FELT.equals(mine)) {
            b.felt.setIconResource(mine != null ? R.drawable.ic_heart_filled : R.drawable.ic_heart);
            b.felt.setIconTint(ColorStateList.valueOf(color));
        } else {
            b.felt.setIcon(null);                                   // the emoji leads instead
        }
        b.felt.setTextColor(color);
        String emoji = Reactions.summary(post, 3);
        String count = post.likeCount > 0 ? Formats.count(post.likeCount) : "";
        b.felt.setText(emoji.isEmpty() ? count : (count.isEmpty() ? emoji : emoji + " " + count));
        b.felt.setContentDescription((mine != null
                ? ctx.getString(R.string.cd_reacted, ctx.getString(Reactions.label(mine)))
                : ctx.getString(R.string.cd_felt))
                + (post.likeCount > 0 ? ", " + post.likeCount : "") + ". " + ctx.getString(R.string.cd_react_hint));
        if (animate && mine != null) {
            b.felt.setScaleX(0.8f);
            b.felt.setScaleY(0.8f);
            b.felt.animate().scaleX(1f).scaleY(1f).setDuration(260)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(3f)).start();
        }
    }
}
