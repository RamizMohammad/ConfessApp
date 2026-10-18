package in.mohammad.ramiz.confess.ui.feed;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Formats;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.data.model.Poll;
import in.mohammad.ramiz.confess.data.model.Post;
import in.mohammad.ramiz.confess.databinding.ItemConfessionCardBinding;
import in.mohammad.ramiz.confess.databinding.ItemPollOptionBinding;

/**
 * Fills the paper card (item_confession_card) from a {@link Post}.
 * Shared by the feed, "my letters" and the detail header so they always look alike.
 */
public final class PostCardBinder {

    public interface Listener {
        void onOpen(@NonNull Post post, boolean toResponses);

        void onFelt(@NonNull Post post);

        /** Tapped a poll choice (only offered before voting, while it's open). */
        default void onVote(@NonNull Post post, int option) { }

        /** Picked from the long-press reaction bar (felt · hug · same · strength · tears). */
        default void onReact(@NonNull Post post, @NonNull String reaction) { }

        void onMore(@NonNull Post post, @NonNull View anchor);

        /** Tapped the writer's aura or alias. Screens that can't open profiles ignore it. */
        default void onAuthor(@NonNull Post post) { }
    }

    private PostCardBinder() { }

    public static void bind(@NonNull ItemConfessionCardBinding b, @NonNull Post post, @NonNull Listener l) {
        Context ctx = b.getRoot().getContext();
        b.card.setCardBackgroundColor(in.mohammad.ramiz.confess.ui.components.PaperTone.paper(ctx));
        String alias = post.author != null && post.author.alias != null ? post.author.alias : "";

        b.avatar.setAvatar(post.author != null ? post.author.avatar : null);
        b.avatar.setStoryAlias(alias);
        b.alias.setText("@" + alias);
        String when = Formats.relative(post.createdAt);
        b.time.setText(post.isMine ? when + " · " + ctx.getString(R.string.feed_you) : when);
        b.text.setText(post.text == null ? "" : post.text.trim());
        bindMood(b, post);
        bindLetter(b, post);
        bindPoll(b, post, l);
        bindResponses(b, post);
        b.responses.setText(post.commentCount > 0 ? Formats.count(post.commentCount) : "");
        bindFelt(b, post, false);

        b.card.setContentDescription(ctx.getString(R.string.cd_card, alias, when));
        in.mohammad.ramiz.confess.ui.components.PaperTone.apply(b.card);                     // text + icons follow the paper / text tone
        bindSensitive(b, post);
        bindScore(b, post);
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
                if (Sensitive.hidden(post)) {                         // first tap on a blurred one reads it
                    reveal(b, post);
                    return;
                }
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

    // ── Mood, unsent letter, poll ────────────────────────────────────────────

    private static void bindMood(@NonNull ItemConfessionCardBinding b, @NonNull Post post) {
        if (!Moods.known(post.mood)) {
            b.mood.setVisibility(View.GONE);
            return;
        }
        Context ctx = b.getRoot().getContext();
        b.mood.setText(Moods.emoji(post.mood) + "  " + ctx.getString(Moods.label(post.mood)));
        b.mood.setVisibility(View.VISIBLE);
    }

    private static void bindLetter(@NonNull ItemConfessionCardBinding b, @NonNull Post post) {
        boolean letter = post.letterTo != null && !post.letterTo.trim().isEmpty();
        b.letterTo.setVisibility(letter ? View.VISIBLE : View.GONE);
        b.airmail.setVisibility(letter ? View.VISIBLE : View.GONE);
        if (!letter) return;
        Context ctx = b.getRoot().getContext();
        b.letterTo.setText(ctx.getString(R.string.letter_to, post.letterTo.trim()));
        android.graphics.drawable.Drawable env = ContextCompat.getDrawable(ctx, R.drawable.ic_mail_open);
        if (env != null) {
            env = env.mutate();
            int px = Math.round(16 * ctx.getResources().getDisplayMetrics().density);
            env.setBounds(0, 0, px, px);
            env.setTint(in.mohammad.ramiz.confess.ui.components.PaperTone.meta(ctx));
        }
        b.letterTo.setCompoundDrawablesRelative(env, null, null, null);
        if (b.airmail.getBackground() == null) b.airmail.setBackground(airmail(ctx));
    }

    @androidx.annotation.Nullable private static android.graphics.Bitmap airmailTile;
    private static int airmailTone = -1;

    /** Classic airmail edge: brick / paper / navy diagonal stripes, tiled across the card. */
    private static android.graphics.drawable.Drawable airmail(@NonNull Context ctx) {
        float d = ctx.getResources().getDisplayMetrics().density;
        int tone = in.mohammad.ramiz.confess.ui.components.PaperTone.get(ctx);
        if (airmailTile == null || airmailTone != tone) {
            airmailTone = tone;
            int w = Math.round(36 * d), h = Math.round(6 * d);
            android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas c = new android.graphics.Canvas(bmp);
            c.drawColor(in.mohammad.ramiz.confess.ui.components.PaperTone.paper(ctx));
            android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            int[] cols = {ContextCompat.getColor(ctx, R.color.brick), 0xFF2F4A6D};
            float stripe = w / 4f;
            for (int i = -1; i < 5; i++) {
                p.setColor(cols[(i + 2) % 2]);
                android.graphics.Path path = new android.graphics.Path();
                float x = i * stripe * 2;
                path.moveTo(x, h);
                path.lineTo(x + h, 0);
                path.lineTo(x + h + stripe * 0.75f, 0);
                path.lineTo(x + stripe * 0.75f, h);
                path.close();
                c.drawPath(path, p);
            }
            airmailTile = bmp;
        }
        android.graphics.drawable.BitmapDrawable bd = new android.graphics.drawable.BitmapDrawable(ctx.getResources(), airmailTile);
        bd.setTileModeX(android.graphics.Shader.TileMode.REPEAT);
        return bd;
    }

    private static void bindPoll(@NonNull ItemConfessionCardBinding b, @NonNull Post post, @NonNull Listener l) {
        Poll poll = post.poll;
        if (poll == null || poll.options == null || poll.options.isEmpty()) {
            b.poll.setVisibility(View.GONE);
            b.pollOptions.removeAllViews();
            b.poll.setTag(null);
            return;
        }
        Context ctx = b.getRoot().getContext();
        b.poll.setVisibility(View.VISIBLE);
        b.pollOptions.removeAllViews();
        boolean results = poll.resultsVisible();
        String sig = post.postId + "|" + poll.signature();
        boolean animate = results && !sig.equals(b.poll.getTag());
        b.poll.setTag(sig);

        int total = Math.max(0, poll.totalVotes);
        int best = -1, bestVotes = -1;
        for (int i = 0; i < poll.options.size(); i++) {
            Integer v = poll.options.get(i).votes;
            if (v != null && v > bestVotes) { bestVotes = v; best = i; }
        }
        LayoutInflater inf = LayoutInflater.from(ctx);
        for (int i = 0; i < poll.options.size(); i++) {
            Poll.Option o = poll.options.get(i);
            ItemPollOptionBinding r = ItemPollOptionBinding.inflate(inf, b.pollOptions, false);
            r.label.setText(o.text);
            int index = i;
            if (results) {
                int votes = o.votes == null ? 0 : o.votes;
                float share = total > 0 ? votes / (float) total : 0f;
                int pct = Math.round(share * 100);
                r.pct.setText(pct + "%");
                r.fill.setVisibility(View.VISIBLE);
                r.fill.setPivotX(0f);
                if (i == best && votes > 0) r.fill.getBackground().mutate().setAlpha(255);
                else r.fill.getBackground().mutate().setAlpha(150);
                if (animate) {
                    r.fill.setScaleX(0f);
                    r.fill.animate().scaleX(share).setStartDelay(60L * i).setDuration(420)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
                } else {
                    r.fill.setScaleX(share);
                }
                boolean mine = poll.myVote != null && poll.myVote == i;
                r.label.setTypeface(null, mine ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
                if (mine) {
                    android.graphics.drawable.Drawable tick = ContextCompat.getDrawable(ctx, R.drawable.ic_check);
                    if (tick != null) {
                        tick = tick.mutate();
                        int px = Math.round(16 * ctx.getResources().getDisplayMetrics().density);
                        tick.setBounds(0, 0, px, px);
                        tick.setTint(ContextCompat.getColor(ctx, R.color.amber));
                    }
                    r.label.setCompoundDrawablesRelative(tick, null, null, null);
                }
                r.row.setClickable(false);
                r.row.setForeground(null);
                r.row.setContentDescription(ctx.getString(R.string.cd_poll_result, o.text, pct));
            } else {
                r.pct.setText("");
                r.fill.setVisibility(View.INVISIBLE);
                r.row.setContentDescription(ctx.getString(R.string.cd_poll_option, o.text, i + 1, poll.options.size()));
                r.row.setOnClickListener(v -> {
                    Haptics.tick(ctx);
                    v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80)
                            .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()).start();
                    l.onVote(post, index);
                });
            }
            b.pollOptions.addView(r.getRoot());
        }

        String votes = ctx.getResources().getQuantityString(R.plurals.poll_votes, total, Formats.count(total));
        if (poll.closed) {
            b.pollMeta.setText(ctx.getString(R.string.poll_final) + " · " + votes);
        } else {
            String left = timeLeft(ctx, poll.closesAt);
            b.pollMeta.setText((results ? votes : ctx.getString(R.string.poll_vote_to_see))
                    + (left.isEmpty() ? "" : " · " + left));
        }
    }

    private static String timeLeft(@NonNull Context ctx, @androidx.annotation.Nullable String iso) {
        if (iso == null) return "";
        try {
            long mins = java.time.Duration.between(java.time.OffsetDateTime.now(),
                    java.time.OffsetDateTime.parse(iso)).toMinutes();
            if (mins <= 0) return ctx.getString(R.string.poll_left_soon);
            long hours = mins / 60;
            if (hours >= 48) return ctx.getString(R.string.poll_left_days, (int) (hours / 24));
            if (hours >= 24) return ctx.getString(R.string.poll_left_day);
            if (hours >= 1) return ctx.getString(R.string.poll_left_hours, (int) hours);
            return ctx.getString(R.string.poll_left_soon);
        } catch (Exception e) {
            return "";
        }
    }

    /** For DiffUtil: anything about mood / letter / poll that the card shows. */
    public static String extrasSignature(@NonNull Post p) {
        return p.mood + "|" + p.letterTo + "|" + (p.poll == null ? "" : p.poll.signature()) + "|" + p.sensitive;
    }

    // ── Score ────────────────────────────────────────────────────────────────

    /** Blur threshold the server uses (SENSITIVE_THRESHOLD) — for the colour and the explanation. */
    private static final int SCORE_BLUR = 85;

    /** The sensitivity score beside the ⋯: green under 60, amber to 84, red from 85 (blurred). */
    private static void bindScore(@NonNull ItemConfessionCardBinding b, @NonNull Post post) {
        Integer s = post.riskScore;
        if (s == null) {
            b.score.setVisibility(View.GONE);
            return;
        }
        Context ctx = b.getRoot().getContext();
        int color = androidx.core.content.ContextCompat.getColor(ctx,
                s >= SCORE_BLUR ? R.color.brick : s >= 60 ? R.color.amber : R.color.sage);
        float d = ctx.getResources().getDisplayMetrics().density;
        android.graphics.drawable.GradientDrawable pill = new android.graphics.drawable.GradientDrawable();
        pill.setCornerRadius(999f);
        pill.setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(color, 28));
        pill.setStroke(Math.max(1, Math.round(d)), androidx.core.graphics.ColorUtils.setAlphaComponent(color, 140));
        b.score.setBackground(pill);
        b.score.setText(String.valueOf(s));
        b.score.setTextColor(color);
        b.score.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(color));
        android.graphics.drawable.Drawable[] ds = b.score.getCompoundDrawablesRelative();
        if (ds[0] != null) {
            int px = Math.round(12 * d);
            ds[0].setBounds(0, 0, px, px);
            b.score.setCompoundDrawablesRelative(ds[0], null, null, null);
        }
        String what = ctx.getString(s >= SCORE_BLUR ? R.string.score_explain_blurred : R.string.score_explain, s, SCORE_BLUR);
        b.score.setContentDescription(ctx.getString(R.string.cd_score, s));
        b.score.setTooltipText(what);
        b.score.setOnClickListener(v -> android.widget.Toast.makeText(ctx, what, android.widget.Toast.LENGTH_LONG).show());
        b.score.setVisibility(View.VISIBLE);
    }

    // ── Sensitive ────────────────────────────────────────────────────────────

    /**
     * Sensitive (scored high by moderation): the words, the letter line and the poll are blurred
     * under a "Sensitive · tap to read" pill until tapped. My own: clear, with a note.
     */
    private static void bindSensitive(@NonNull ItemConfessionCardBinding b, @NonNull Post post) {
        Context ctx = b.getRoot().getContext();
        boolean hide = Sensitive.hidden(post);
        float r = hide ? Sensitive.BLUR_DP * ctx.getResources().getDisplayMetrics().density : 0f;
        Sensitive.blur(b.text, r);
        Sensitive.blur(b.letterTo, r);
        Sensitive.blur(b.poll, r);
        b.poll.setEnabled(!hide);
        for (int i = 0; i < b.pollOptions.getChildCount(); i++) b.pollOptions.getChildAt(i).setEnabled(!hide);
        b.text.setMinHeight(hide ? Math.round(56 * ctx.getResources().getDisplayMetrics().density) : 0);  // room for the pill
        b.sensitive.setVisibility(hide ? View.VISIBLE : View.GONE);
        b.sensitive.setOnClickListener(hide ? v -> reveal(b, post) : null);
        b.sensitiveNote.setVisibility(post.sensitive && post.isMine ? View.VISIBLE : View.GONE);
        b.text.setContentDescription(hide ? ctx.getString(R.string.cd_sensitive) : null);
    }

    private static void reveal(@NonNull ItemConfessionCardBinding b, @NonNull Post post) {
        Context ctx = b.getRoot().getContext();
        Sensitive.reveal(post);
        Haptics.tick(ctx);
        b.sensitive.animate().alpha(0f).setDuration(160).withEndAction(() -> {
            b.sensitive.setVisibility(View.GONE);
            b.sensitive.setAlpha(1f);
        }).start();
        Sensitive.unblur(new View[]{b.text, b.letterTo, b.poll}, Sensitive.BLUR_DP * ctx.getResources().getDisplayMetrics().density);
        b.poll.setEnabled(true);
        for (int i = 0; i < b.pollOptions.getChildCount(); i++) b.pollOptions.getChildAt(i).setEnabled(true);
        b.text.setContentDescription(null);
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
        int color = mine != null ? ContextCompat.getColor(ctx, R.color.amber) : in.mohammad.ramiz.confess.ui.components.PaperTone.meta(ctx);
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
