package in.mohammad.ramiz.confess.ui.tour;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import java.util.ArrayList;
import java.util.List;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.core.push.SoundLibrary;
import in.mohammad.ramiz.confess.databinding.ViewBottomBarBinding;
import in.mohammad.ramiz.confess.ui.books.reader.PagePreview;
import in.mohammad.ramiz.confess.ui.books.reader.ReaderPrefs;
import in.mohammad.ramiz.confess.ui.components.PaperTone;
import in.mohammad.ramiz.confess.ui.components.ReadingFonts;
import in.mohammad.ramiz.confess.ui.home.NavBar;

/**
 * The live samples on the tour's pages: small, real-looking pieces of the app that play on
 * their own and react when touched (tap a card to feel it, tap a story ring, turn a book page,
 * shake-lock a phone…). People don't read long text; they try things.
 *
 * Each sample loops gently until the page changes ({@link Demo#stop}). With the phone's
 * animations off they show their finished state and still respond to taps.
 */
final class TourDemos {

    /** A sample on screen: its view, and how to stop its loops. */
    static final class Demo {
        final View view;
        final Handler handler = new Handler(Looper.getMainLooper());
        final List<ValueAnimator> animators = new ArrayList<>();
        @Nullable SoundLibrary.Preview sound;
        boolean stopped;

        Demo(@NonNull View view) {
            this.view = view;
        }

        void every(long ms, @NonNull Runnable r) {
            handler.postDelayed(new Runnable() {
                @Override public void run() {
                    if (stopped) return;
                    r.run();
                    handler.postDelayed(this, ms);
                }
            }, ms);
        }

        void stop() {
            stopped = true;
            handler.removeCallbacksAndMessages(null);
            for (ValueAnimator a : animators) a.cancel();
            if (sound != null) sound.stop();
        }
    }

    private TourDemos() { }

    /** The sample for a page (by its title), or null for pages without one. */
    @Nullable
    static Demo create(@NonNull Context c, int titleRes) {
        if (titleRes == R.string.tour_kinds_title) return kinds(c);
        if (titleRes == R.string.tour_gif_title) return gif(c);
        if (titleRes == R.string.tour_feel_title) return feel(c);
        if (titleRes == R.string.tour_stories_title) return stories(c);
        if (titleRes == R.string.tour_follow_title) return follow(c);
        if (titleRes == R.string.tour_books_title) return books(c);
        if (titleRes == R.string.tour_bursts_title) return bursts(c);
        if (titleRes == R.string.tour_gentle_title) return gentle(c);
        if (titleRes == R.string.tour_yours_title) return yours(c);
        if (titleRes == R.string.tour_sounds_title) return sounds(c);
        if (titleRes == R.string.tour_private_title) return locked(c);
        return null;
    }

    /** Where the feature lives in the app, as path steps (string-array), or 0. */
    static int where(int titleRes) {
        if (titleRes == R.string.tour_kinds_title) return R.array.tour_where_kinds;
        if (titleRes == R.string.tour_gif_title) return R.array.tour_where_gif;
        if (titleRes == R.string.tour_feel_title) return R.array.tour_where_feel;
        if (titleRes == R.string.tour_stories_title) return R.array.tour_where_stories;
        if (titleRes == R.string.tour_follow_title) return R.array.tour_where_follow;
        if (titleRes == R.string.tour_books_title) return R.array.tour_where_books;
        if (titleRes == R.string.tour_bursts_title) return R.array.tour_where_bursts;
        if (titleRes == R.string.tour_gentle_title) return R.array.tour_where_gentle;
        if (titleRes == R.string.tour_yours_title) return R.array.tour_where_yours;
        if (titleRes == R.string.tour_sounds_title) return R.array.tour_where_sounds;
        if (titleRes == R.string.tour_private_title) return R.array.tour_where_private;
        return 0;
    }

    private static boolean motion() {
        return ValueAnimator.areAnimatorsEnabled();
    }

    // ── Moods · polls · letters ──────────────────────────────────────────────

    private static Demo kinds(Context c) {
        LinearLayout card = card(c);
        card.addView(author(c, "sam", "Tonight · 11:42 pm"));
        TextView text = body(c, "Should I text him back?");
        card.addView(text);
        FrameLayout extra = new FrameLayout(c);
        extra.setMinimumHeight(dp(c, 64));
        card.addView(extra, lp(-1, -2, 0, 10));
        Demo d = new Demo(wrap(c, card));
        TextView caption = caption(c, "");
        ((LinearLayout) d.view).addView(caption);
        int[] state = {-1};
        Runnable next = () -> {
            state[0] = (state[0] + 1) % 3;
            extra.removeAllViews();
            View v;
            String label;
            if (state[0] == 0) {
                v = chip(c, "💬  Advice wanted");
                label = "MOOD  ·  tells people how to respond";
            } else if (state[0] == 1) {
                LinearLayout poll = new LinearLayout(c);
                poll.setOrientation(LinearLayout.VERTICAL);
                poll.addView(bar(c, d, "Yes", 0.64f));
                poll.addView(bar(c, d, "Wait a week", 0.36f), lp(-1, -2, 0, 6));
                v = poll;
                label = "POLL  ·  anonymous votes, final";
            } else {
                TextView to = small(c, "✉  To: my dad", PaperTone.meta(c));
                to.setTypeface(ResourcesCompat.getFont(c, R.font.newsreader));
                to.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                v = to;
                label = "UNSENT LETTER  ·  addressed to someone";
            }
            extra.addView(v);
            caption.setText(label);
            pop(v);
        };
        next.run();
        card.setOnClickListener(x -> { Haptics.tick(c); d.handler.removeCallbacksAndMessages(null); next.run(); d.every(2600, next); });
        d.every(2600, next);
        return d;
    }

    private static View bar(Context c, Demo d, String label, float share) {
        FrameLayout f = new FrameLayout(c);
        GradientDrawable track = round(c, 10, PaperTone.rule(c));
        f.setBackground(track);
        View fill = new View(c);
        fill.setBackground(round(c, 10, 0x55E8A04C));
        f.addView(fill, new FrameLayout.LayoutParams(0, dp(c, 28)));
        TextView t = small(c, label + "   " + Math.round(share * 100) + "%", PaperTone.ink(c));
        t.setPadding(dp(c, 10), 0, 0, 0);
        t.setGravity(Gravity.CENTER_VERTICAL);
        f.addView(t, new FrameLayout.LayoutParams(-1, dp(c, 28)));
        f.post(() -> {
            int full = Math.round(f.getWidth() * share);
            if (!motion()) { setWidth(fill, full); return; }
            ValueAnimator a = ValueAnimator.ofInt(0, full);
            a.setDuration(700);
            a.setInterpolator(new DecelerateInterpolator());
            a.addUpdateListener(x -> setWidth(fill, (int) x.getAnimatedValue()));
            d.animators.add(a);
            a.start();
        });
        return f;
    }

    // ── GIF ──────────────────────────────────────────────────────────────────

    private static Demo gif(Context c) {
        LinearLayout card = card(c);
        card.addView(author(c, "you", "Tonight · 2:17 am"));
        TextView text = body(c, "");
        card.addView(text);
        View gifBox = new View(c);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF6D5BD0, 0xFFE8A04C, 0xFFD2553F});
        g.setCornerRadius(dp(c, 12));
        gifBox.setBackground(g);
        FrameLayout gifFrame = new FrameLayout(c);
        gifFrame.addView(gifBox, new FrameLayout.LayoutParams(-1, -1));
        TextView tag = small(c, "GIF", Color.WHITE);
        tag.setTypeface(Typeface.DEFAULT_BOLD);
        tag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        gifFrame.addView(tag, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        card.addView(gifFrame, lp(-1, dp(c, 72), 0, 8));
        TextView count = small(c, "", PaperTone.meta(c));
        count.setGravity(Gravity.END);
        card.addView(count, lp(-1, -2, 0, 6));
        Demo d = new Demo(wrap(c, card));
        ((LinearLayout) d.view).addView(caption(c, "Words + one GIF · up to 100 characters"));

        String words = "this is me on mondays";
        String over = " and tuesdays and every day ending in y, honestly all week long if I'm being real with you";
        int[] step = {0};
        Runnable tick = new Runnable() {
            @Override public void run() {
                int total = words.length() + over.length();
                step[0] = Math.min(step[0] + 2, total + 30);
                int shown = Math.min(step[0], total);
                String all = words + over;
                SpannableStringBuilder sb = new SpannableStringBuilder(all.substring(0, shown));
                if (sb.length() > 100) {
                    sb.setSpan(new ForegroundColorSpan(ContextCompat.getColor(c, R.color.brick)), 100, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    sb.setSpan(new StrikethroughSpan(), 100, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                text.setText(sb);
                count.setText(shown + " / 100");
                count.setTextColor(shown > 100 ? ContextCompat.getColor(c, R.color.brick) : PaperTone.meta(c));
                if (shown == words.length()) pop(gifFrame);
                gifFrame.setVisibility(shown >= words.length() ? View.VISIBLE : View.INVISIBLE);
                if (step[0] >= total + 30) step[0] = 0;
                d.handler.postDelayed(this, shown >= total ? 90 : 45);
            }
        };
        if (motion()) d.handler.post(tick);
        else { step[0] = words.length() + over.length(); tick.run(); d.handler.removeCallbacksAndMessages(null); }
        gifFrame.setOnClickListener(x -> {                      // "pick another": the colours shift
            Haptics.tick(c);
            int[] cols = {0xFF2F9E8F, 0xFF6D5BD0, 0xFFE8A04C};
            g.setColors(new int[]{cols[(int) (Math.random() * 3)], cols[(int) (Math.random() * 3)], 0xFFD2553F});
            pop(gifFrame);
        });
        return d;
    }

    // ── Feel it ──────────────────────────────────────────────────────────────

    private static Demo feel(Context c) {
        int amber = ContextCompat.getColor(c, R.color.amber);
        LinearLayout card = card(c);
        card.addView(author(c, "nightowl", "2h"));
        card.addView(body(c, "I still look for your car at every red light."));
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView heart = icon(c, R.drawable.ic_heart_filled, amber, 20);
        row.addView(heart);
        TextView emoji = small(c, "", PaperTone.ink(c));
        emoji.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        emoji.setVisibility(View.GONE);
        row.addView(emoji);
        TextView num = small(c, "12", PaperTone.ink(c));
        num.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-2, -2);
        nl.setMarginStart(dp(c, 8));
        row.addView(num, nl);
        card.addView(row, lp(-1, -2, 0, 12));
        TextView picker = small(c, "💛   🫂   🤝   💪   🥺", PaperTone.ink(c));
        picker.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        picker.setBackground(round(c, 24, PaperTone.rule(c)));
        picker.setPadding(dp(c, 14), dp(c, 6), dp(c, 14), dp(c, 6));
        picker.setVisibility(View.GONE);
        card.addView(picker, lp(-2, -2, 0, 8));
        ImageView burst = icon(c, R.drawable.ic_heart_filled, amber, 72);
        burst.setAlpha(0f);
        FrameLayout stack = new FrameLayout(c);
        stack.addView(card);
        stack.addView(burst, new FrameLayout.LayoutParams(dp(c, 72), dp(c, 72), Gravity.CENTER));
        Demo d = new Demo(wrap(c, stack));
        ((LinearLayout) d.view).addView(caption(c, "Tap · double-tap · hold"));
        int[] count = {12};
        boolean[] touched = {false};
        // Shows what you felt: the amber heart, or the reaction you held for.
        java.util.function.Consumer<String> felt = mine -> {
            count[0]++;
            boolean isHeart = mine == null;
            heart.setVisibility(isHeart ? View.VISIBLE : View.GONE);
            emoji.setVisibility(isHeart ? View.GONE : View.VISIBLE);
            if (!isHeart) emoji.setText(mine);
            num.setText(String.valueOf(count[0]));
            pop(row);
        };
        android.view.GestureDetector g = new android.view.GestureDetector(c, new android.view.GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(@NonNull android.view.MotionEvent e) { return true; }
            @Override public boolean onSingleTapConfirmed(@NonNull android.view.MotionEvent e) {
                touched[0] = true; Haptics.tick(c); felt.accept(null); return true;
            }
            @Override public boolean onDoubleTap(@NonNull android.view.MotionEvent e) {
                touched[0] = true; Haptics.click(c); felt.accept(null); heartBurst(burst); return true;
            }
            @Override public void onLongPress(@NonNull android.view.MotionEvent e) {
                touched[0] = true; Haptics.click(c);
                picker.setVisibility(View.VISIBLE); pop(picker);
                String[] all = {null, "🫂", "🤝", "💪", "🥺"};
                String mine = all[(int) (Math.random() * all.length)];
                d.handler.postDelayed(() -> { felt.accept(mine); picker.setVisibility(View.GONE); }, 900);
            }
        });
        card.setOnTouchListener((v, ev) -> g.onTouchEvent(ev));
        d.every(3200, () -> { if (!touched[0]) { heartBurst(burst); felt.accept(null); } });
        return d;
    }

    private static void heartBurst(View heart) {
        if (!motion()) return;
        heart.setAlpha(1f);
        heart.setScaleX(0.3f);
        heart.setScaleY(0.3f);
        heart.animate().scaleX(1.2f).scaleY(1.2f).setDuration(260).setInterpolator(new OvershootInterpolator(2f))
                .withEndAction(() -> heart.animate().alpha(0f).scaleX(1.5f).scaleY(1.5f).setDuration(260).start()).start();
    }

    // ── Stories ──────────────────────────────────────────────────────────────

    private static Demo stories(Context c) {
        String[] names = {"sam", "mira", "owl", "kai"};
        String[] lines = {"Couldn't sleep again. 3am thoughts hit different.", "Told her today. Lighter already.",
                "Rain on the window, chai in hand. Okay.", "First day at the new job. Terrified."};
        int[] colors = {0xFF5B8DEF, 0xFFE07A5F, 0xFF81B29A, 0xFFB388EB};
        int[][] skies = {{0xFF2B3A6B, 0xFF5B8DEF}, {0xFF6B2F3A, 0xFFE07A5F}, {0xFF234B3F, 0xFF81B29A}, {0xFF3B2F6B, 0xFFB388EB}};
        int amber = ContextCompat.getColor(c, R.color.amber);

        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout rings = new LinearLayout(c);
        rings.setGravity(Gravity.CENTER);
        box.addView(rings, lp(-1, -2, 0, 0));

        // The story being watched: a progress line, who, and the words.
        GradientDrawable sky = gradient(c, skies[0][0], skies[0][1]);
        LinearLayout story = new LinearLayout(c);
        story.setOrientation(LinearLayout.VERTICAL);
        story.setBackground(sky);
        story.setPadding(dp(c, 14), dp(c, 10), dp(c, 14), dp(c, 16));
        FrameLayout track = new FrameLayout(c);
        track.setBackground(round(c, 2, 0x55FFFFFF));
        View progress = new View(c);
        progress.setBackground(round(c, 2, Color.WHITE));
        track.addView(progress, new FrameLayout.LayoutParams(0, -1));
        story.addView(track, lp(-1, dp(c, 3), 0, 0));
        TextView who = small(c, "", 0xCCFFFFFF);
        story.addView(who, lp(-1, -2, 0, 10));
        TextView words = new TextView(c);
        words.setTextColor(Color.WHITE);
        words.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        words.setTypeface(ReadingFonts.typeface(c, ReadingFonts.DEFAULT));
        words.setMinLines(2);
        story.addView(words, lp(-1, -2, 0, 6));
        box.addView(story, lp(-1, -2, 0, 16));

        Demo d = new Demo(wrap(c, box));
        ((LinearLayout) d.view).addView(caption(c, "Tap a ring to watch theirs"));

        List<GradientDrawable> strokes = new ArrayList<>();
        List<View> ringViews = new ArrayList<>();
        ValueAnimator[] running = {null};
        java.util.function.IntConsumer[] play = {null};
        play[0] = k -> {
            if (d.stopped) return;
            if (running[0] != null) running[0].cancel();
            if (k == 0) for (GradientDrawable st : strokes) st.setStroke(dp(c, 3), amber);   // a fresh round
            for (int i = 0; i < ringViews.size(); i++) {
                float s = i == k ? 1.12f : 1f;
                if (motion()) ringViews.get(i).animate().scaleX(s).scaleY(s).setDuration(220).start();
            }
            sky.setColors(skies[k]);
            who.setText("@" + names[k] + "  ·  " + (k + 1) + "h");
            words.setText(lines[k]);
            pop(words);
            setWidth(progress, 0);
            if (!motion()) { strokes.get(k).setStroke(dp(c, 2), 0x55E8A04C); return; }
            ValueAnimator p = ValueAnimator.ofInt(0, Math.max(1, track.getWidth()));
            p.setDuration(3000);
            p.addUpdateListener(a -> setWidth(progress, (int) a.getAnimatedValue()));
            boolean[] cancelled = {false};
            p.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationCancel(Animator animation) { cancelled[0] = true; }
                @Override public void onAnimationEnd(Animator animation) {
                    d.animators.remove(p);
                    strokes.get(k).setStroke(dp(c, 2), 0x55E8A04C);              // seen: the ring dims
                    if (!cancelled[0]) play[0].accept((k + 1) % names.length);
                }
            });
            running[0] = p;
            d.animators.add(p);
            p.start();
        };

        for (int i = 0; i < names.length; i++) {
            final int k = i;
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            FrameLayout ring = new FrameLayout(c);
            GradientDrawable stroke = new GradientDrawable();
            stroke.setShape(GradientDrawable.OVAL);
            stroke.setStroke(dp(c, 3), amber);
            ring.setBackground(stroke);
            View face = new View(c);
            GradientDrawable f = new GradientDrawable();
            f.setShape(GradientDrawable.OVAL);
            f.setColor(colors[i]);
            face.setBackground(f);
            ring.addView(face, new FrameLayout.LayoutParams(dp(c, 40), dp(c, 40), Gravity.CENTER));
            col.addView(ring, new LinearLayout.LayoutParams(dp(c, 52), dp(c, 52)));
            col.addView(small(c, "@" + names[i], ContextCompat.getColor(c, R.color.text_secondary)), lp(-2, -2, 0, 4));
            rings.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
            strokes.add(stroke);
            ringViews.add(ring);
            col.setOnClickListener(x -> { Haptics.tick(c); play[0].accept(k); });
        }
        story.post(() -> play[0].accept(0));
        return d;
    }

    // ── Follow ───────────────────────────────────────────────────────────────

    private static Demo follow(Context c) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = card(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        View face = new View(c);
        GradientDrawable f = new GradientDrawable();
        f.setShape(GradientDrawable.OVAL);
        f.setColor(0xFF81B29A);
        face.setBackground(f);
        row.addView(face, new LinearLayout.LayoutParams(dp(c, 40), dp(c, 40)));
        TextView name = body(c, "@nightowl");
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, -2, 1f);
        nl.setMarginStart(dp(c, 12));
        row.addView(name, nl);
        TextView btn = small(c, "Follow", ContextCompat.getColor(c, R.color.paper_ink));
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setPadding(dp(c, 16), dp(c, 8), dp(c, 16), dp(c, 8));
        GradientDrawable bb = round(c, 20, ContextCompat.getColor(c, R.color.amber));
        btn.setBackground(bb);
        row.addView(btn);
        box.addView(row);
        TextView note = small(c, "🔔  @nightowl just confessed", ContextCompat.getColor(c, R.color.text_primary));
        note.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        note.setBackground(round(c, 14, ContextCompat.getColor(c, R.color.surface_high)));
        note.setVisibility(View.INVISIBLE);
        box.addView(note, lp(-1, -2, 0, 14));
        Demo d = new Demo(wrap(c, box));
        ((LinearLayout) d.view).addView(caption(c, "Tap Follow"));
        boolean[] on = {false};
        boolean[] touched = {false};
        Runnable toggle = () -> {
            on[0] = !on[0];
            btn.setText(on[0] ? "Following ✓" : "Follow");
            bb.setColor(on[0] ? ContextCompat.getColor(c, R.color.surface_high) : ContextCompat.getColor(c, R.color.amber));
            btn.setTextColor(on[0] ? ContextCompat.getColor(c, R.color.amber) : ContextCompat.getColor(c, R.color.paper_ink));
            pop(btn);
            if (on[0]) {
                d.handler.postDelayed(() -> {
                    note.setVisibility(View.VISIBLE);
                    if (motion()) { note.setTranslationY(-dp(c, 16)); note.setAlpha(0f);
                        note.animate().translationY(0).alpha(1f).setDuration(300).start(); }
                }, 700);
            } else {
                note.setVisibility(View.INVISIBLE);
            }
        };
        btn.setOnClickListener(x -> { touched[0] = true; Haptics.tick(c); toggle.run(); });
        d.every(3000, () -> { if (!touched[0]) toggle.run(); });
        return d;
    }

    // ── Books ────────────────────────────────────────────────────────────────

    private static Demo books(Context c) {
        PagePreview page = new PagePreview(c);
        page.update(ReaderPrefs.load(c));
        FrameLayout f = new FrameLayout(c);
        f.addView(page, new FrameLayout.LayoutParams(-1, dp(c, 190)));
        Demo d = new Demo(wrap(c, f));
        ((LinearLayout) d.view).addView(caption(c, "Tap the page to turn it"));
        boolean[] touched = {false};
        page.setOnTouchListener((v, ev) -> {
            if (ev.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) touched[0] = true;
            return false;
        });
        d.every(3600, () -> { if (!touched[0]) page.turn(); });
        return d;
    }

    // ── Bursts ───────────────────────────────────────────────────────────────

    private static Demo bursts(Context c) {
        FrameLayout stage = new FrameLayout(c);
        String[] lines = {"It started with a missed call.", "Then I found the letters.", "I never told anyone. Until now."};
        List<View> cards = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            LinearLayout cd = card(c);
            cd.addView(author(c, "sam", (i + 1) + " of 3"));
            cd.addView(body(c, lines[i]));
            cd.setElevation(dp(c, 2 + 2 * i));                   // later ones sit on top
            stage.addView(cd, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
            cards.add(cd);
        }
        Demo d = new Demo(wrap(c, stage));
        TextView label = caption(c, "");
        ((LinearLayout) d.view).addView(label);
        boolean[] stacked = {false};
        int gap = dp(c, 8), peek = dp(c, 10);
        Runnable flip = () -> {
            stacked[0] = !stacked[0];
            int h = cards.get(0).getHeight();
            int hStacked = h + 2 * peek, hFanned = 3 * h + 2 * gap;
            for (int i = 0; i < 3; i++) {
                View v = cards.get(i);
                // Fanned: one under another. Stacked: the first two peek out above the last.
                float ty = stacked[0] ? i * peek : i * (h + gap);
                float sc = stacked[0] ? 1f - (2 - i) * 0.05f : 1f;
                if (motion()) v.animate().translationY(ty).scaleX(sc).scaleY(sc).setDuration(520)
                        .setInterpolator(new TourActivity.Spring(8f)).start();
                else { v.setTranslationY(ty); v.setScaleX(sc); v.setScaleY(sc); }
            }
            // The stage (and the frame around it) grows and shrinks with them.
            ViewGroup.LayoutParams p = stage.getLayoutParams();
            int target = stacked[0] ? hStacked : hFanned;
            if (!motion() || p.height <= 0) { p.height = target; stage.setLayoutParams(p); }
            else {
                ValueAnimator a = ValueAnimator.ofInt(p.height, target);
                a.setDuration(520);
                a.setInterpolator(new TourActivity.Spring(8f));
                a.addUpdateListener(x -> { p.height = (int) x.getAnimatedValue(); stage.setLayoutParams(p); });
                d.animators.add(a);
                a.start();
            }
            label.setText(stacked[0] ? "Read together as one burst · tap to fan out"
                    : "3 confessions from @sam in a row · tap to stack");
        };
        stage.post(() -> {
            if (d.stopped) return;
            flip.run();                                          // starts stacked
            d.every(2800, flip);
        });
        View.OnClickListener tap = x -> { Haptics.tick(c); d.handler.removeCallbacksAndMessages(null); flip.run(); d.every(2800, flip); };
        stage.setOnClickListener(tap);
        for (View v : cards) v.setOnClickListener(tap);
        return d;
    }

    // ── Sensitive ────────────────────────────────────────────────────────────

    private static Demo gentle(Context c) {
        LinearLayout card = card(c);
        card.addView(author(c, "anon", "5h"));
        TextView text = body(c, "Some days are heavier than others. Today I couldn't get out of bed, and that's okay to say.");
        FrameLayout frame = new FrameLayout(c);
        frame.addView(text);
        TextView pill = small(c, "  Sensitive · tap to read  ", ContextCompat.getColor(c, R.color.text_primary));
        pill.setBackground(round(c, 16, 0xE60F0E13));
        pill.setPadding(dp(c, 10), dp(c, 6), dp(c, 10), dp(c, 6));
        frame.addView(pill, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        card.addView(frame);
        Demo d = new Demo(wrap(c, card));
        ((LinearLayout) d.view).addView(caption(c, "Tap to read when you're ready"));
        Runnable cover = () -> {
            text.setRenderEffect(RenderEffect.createBlurEffect(dp(c, 8), dp(c, 8), Shader.TileMode.CLAMP));
            pill.setVisibility(View.VISIBLE);
            pop(pill);
        };
        cover.run();
        card.setOnClickListener(x -> {
            Haptics.tick(c);
            if (pill.getVisibility() != View.VISIBLE) return;
            pill.setVisibility(View.GONE);
            if (!motion()) { text.setRenderEffect(null); return; }
            ValueAnimator a = ValueAnimator.ofFloat(dp(c, 8), 0.01f);
            a.setDuration(380);
            a.addUpdateListener(v -> {
                float r = (float) v.getAnimatedValue();
                text.setRenderEffect(r < 0.5f ? null : RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP));
            });
            d.animators.add(a);
            a.start();
            d.handler.postDelayed(cover, 4500);                   // and back, so it can be tried again
        });
        return d;
    }

    // ── Make it yours ────────────────────────────────────────────────────────

    private static Demo yours(Context c) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout card = card(c);
        TextView sample = body(c, "I still look for your car at every red light.");
        card.addView(sample);
        TextView label = small(c, "", PaperTone.meta(c));
        card.addView(label, lp(-1, -2, 0, 8));
        box.addView(card);
        ViewBottomBarBinding bar = ViewBottomBarBinding.inflate(LayoutInflater.from(c), box, false);
        box.addView(bar.getRoot(), lp(-1, -2, 0, 18));
        bar.compose.setClickable(false);
        NavBar.arrange(bar, NavBar.order(c));
        String[] tabs = {NavBar.FEED, NavBar.BOOKS, NavBar.ME};
        int[] t = {0};
        NavBar.render(c, bar, tabs[0], false);
        for (String key : NavBar.DEFAULT_ORDER) {
            NavBar.tab(bar, key).setOnClickListener(x -> {
                Haptics.tick(c);
                if (NavBar.SEARCH.equals(key)) NavBar.bump(c, bar, key); else NavBar.render(c, bar, key, true);
            });
        }
        Demo d = new Demo(wrap(c, box));
        ((LinearLayout) d.view).addView(caption(c, "Tap the card for another font · tap a tab"));
        int[] fonts = ReadingFonts.ORDER;
        int[] sizes = {16, 18, 20};
        int[] f = {0};
        Runnable restyle = () -> {
            int font = fonts[f[0] % fonts.length];
            int size = sizes[f[0] % sizes.length];
            sample.setTypeface(ReadingFonts.typeface(c, font));
            sample.setTextSize(TypedValue.COMPLEX_UNIT_SP, size * ReadingFonts.scale(font));
            label.setText("Aa · " + c.getString(ReadingFonts.name(font)) + " · " + size);
            pop(sample);
        };
        restyle.run();
        card.setOnClickListener(x -> { Haptics.tick(c); f[0]++; restyle.run(); });
        d.every(2200, () -> { f[0]++; restyle.run(); t[0] = (t[0] + 1) % tabs.length; NavBar.render(c, bar, tabs[t[0]], true); });
        return d;
    }

    // ── Sounds ───────────────────────────────────────────────────────────────

    private static Demo sounds(Context c) {
        LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        Demo d = new Demo(wrap(c, list));
        d.sound = new SoundLibrary.Preview();
        ((LinearLayout) d.view).addView(caption(c, "Tap a sound to hear it"));
        for (int i = 0; i < 3; i++) {
            SoundLibrary.Sound s = SoundLibrary.ALL.get(i);
            LinearLayout row = new LinearLayout(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
            row.setBackgroundResource(R.drawable.bg_choice_option);
            ImageView icon = new ImageView(c);
            icon.setImageResource(R.drawable.ic_music);
            icon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(c, R.color.amber)));
            row.addView(icon, new LinearLayout.LayoutParams(dp(c, 22), dp(c, 22)));
            TextView name = small(c, c.getString(s.name), ContextCompat.getColor(c, R.color.text_primary));
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, -2, 1f);
            nl.setMarginStart(dp(c, 12));
            row.addView(name, nl);
            LinearLayout eq = new LinearLayout(c);
            eq.setGravity(Gravity.BOTTOM);
            List<View> bars = new ArrayList<>();
            for (int b = 0; b < 3; b++) {
                View v = new View(c);
                v.setBackground(round(c, 2, ContextCompat.getColor(c, R.color.amber)));
                LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(dp(c, 4), dp(c, 6));
                bl.setMarginStart(dp(c, 3));
                eq.addView(v, bl);
                bars.add(v);
            }
            eq.setMinimumHeight(dp(c, 20));
            row.addView(eq, new LinearLayout.LayoutParams(-2, dp(c, 20)));
            list.addView(row, lp(-1, -2, 0, i == 0 ? 0 : 8));
            row.setOnClickListener(x -> {
                Haptics.tick(c);
                d.sound.play(c, s.uri(c));
                if (!motion()) return;
                for (int b = 0; b < bars.size(); b++) {
                    View v = bars.get(b);
                    ValueAnimator a = ValueAnimator.ofInt(dp(c, 4), dp(c, 18));
                    a.setDuration(180 + 60L * b);
                    a.setRepeatMode(ValueAnimator.REVERSE);
                    a.setRepeatCount(7);
                    a.addUpdateListener(u -> { ViewGroup.LayoutParams p = v.getLayoutParams(); p.height = (int) u.getAnimatedValue(); v.setLayoutParams(p); });
                    d.animators.add(a);
                    a.start();
                }
            });
        }
        return d;
    }

    // ── Locked ───────────────────────────────────────────────────────────────

    private static Demo locked(Context c) {
        FrameLayout phone = new FrameLayout(c);
        GradientDrawable frame = new GradientDrawable();
        frame.setCornerRadius(dp(c, 22));
        frame.setColor(ContextCompat.getColor(c, R.color.surface_raised));
        frame.setStroke(dp(c, 2), ContextCompat.getColor(c, R.color.hairline));
        phone.setBackground(frame);
        phone.setPadding(dp(c, 12), dp(c, 14), dp(c, 12), dp(c, 14));
        LinearLayout inside = card(c);
        inside.addView(author(c, "you", "now"));
        inside.addView(body(c, "The thing I never told anyone…"));
        phone.addView(inside, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        LinearLayout lock = new LinearLayout(c);
        lock.setOrientation(LinearLayout.VERTICAL);
        lock.setGravity(Gravity.CENTER);
        lock.setBackground(round(c, 18, 0xF20F0E13));
        ImageView finger = new ImageView(c);
        finger.setImageResource(R.drawable.ic_fingerprint);
        finger.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(c, R.color.amber)));
        lock.addView(finger, new LinearLayout.LayoutParams(dp(c, 44), dp(c, 44)));
        lock.addView(small(c, "Locked · touch to open", ContextCompat.getColor(c, R.color.text_secondary)), lp(-2, -2, 0, 8));
        lock.setVisibility(View.INVISIBLE);
        phone.addView(lock, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout holder = new FrameLayout(c);
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(-1, dp(c, 170));
        holder.addView(phone, pl);
        Demo d = new Demo(wrap(c, holder));
        ((LinearLayout) d.view).addView(caption(c, "Shake to lock · touch the fingerprint to open"));
        Runnable shakeLock = () -> {
            if (lock.getVisibility() == View.VISIBLE) return;
            Runnable showLock = () -> { lock.setVisibility(View.VISIBLE); pop(lock); };
            if (!motion()) { showLock.run(); return; }
            ValueAnimator s = ValueAnimator.ofFloat(0, 1);
            s.setDuration(480);
            s.addUpdateListener(a -> phone.setRotation((float) Math.sin((float) a.getAnimatedValue() * Math.PI * 6) * 6f));
            s.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) { phone.setRotation(0); showLock.run(); }
            });
            d.animators.add(s);
            s.start();
        };
        lock.setOnClickListener(x -> {
            Haptics.click(c);
            if (motion()) lock.animate().alpha(0f).setDuration(220).withEndAction(() -> { lock.setVisibility(View.INVISIBLE); lock.setAlpha(1f); }).start();
            else lock.setVisibility(View.INVISIBLE);
        });
        d.handler.postDelayed(shakeLock, 900);
        d.every(4200, shakeLock);
        return d;
    }

    // ── Building blocks ──────────────────────────────────────────────────────

    /** The sample, plus room under it for a caption. */
    private static LinearLayout wrap(Context c, View content) {
        LinearLayout w = new LinearLayout(c);
        w.setOrientation(LinearLayout.VERTICAL);
        w.setGravity(Gravity.START);
        w.addView(content, new LinearLayout.LayoutParams(-1, -2));
        return w;
    }

    private static TextView caption(Context c, String text) {
        TextView t = small(c, text, ContextCompat.getColor(c, R.color.amber));
        t.setGravity(Gravity.START);
        t.setLetterSpacing(0.04f);
        t.setLayoutParams(lp(-1, -2, 0, 12));
        return t;
    }

    private static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(round(c, 16, PaperTone.paper(c)));
        l.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        l.setClickable(true);
        return l;
    }

    private static View author(Context c, String alias, String when) {
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(c);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(0xFFB388EB);
        dot.setBackground(g);
        row.addView(dot, new LinearLayout.LayoutParams(dp(c, 26), dp(c, 26)));
        TextView t = small(c, "@" + alias + "  ·  " + when, PaperTone.meta(c));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.setMarginStart(dp(c, 10));
        row.addView(t, p);
        return row;
    }

    private static TextView body(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(PaperTone.ink(c));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setTypeface(ReadingFonts.typeface(c, ReadingFonts.DEFAULT));
        t.setLineSpacing(0f, 1.2f);
        t.setLayoutParams(lp(-1, -2, 0, 10));
        return t;
    }

    private static TextView small(Context c, String text, int color) {
        TextView t = new TextView(c);
        t.setText(text);
        if (color != 0) t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        t.setTypeface(ResourcesCompat.getFont(c, R.font.manrope));
        return t;
    }

    private static ImageView icon(Context c, @DrawableRes int res, int tint, int sizeDp) {
        ImageView v = new ImageView(c);
        v.setImageResource(res);
        v.setImageTintList(ColorStateList.valueOf(tint));
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return v;
    }

    private static TextView chip(Context c, String text) {
        TextView t = small(c, text, PaperTone.ink(c));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setPadding(dp(c, 14), dp(c, 8), dp(c, 14), dp(c, 8));
        t.setBackground(round(c, 18, PaperTone.rule(c)));
        return t;
    }

    private static GradientDrawable round(Context c, int radiusDp, int color) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(c, radiusDp));
        g.setColor(color);
        return g;
    }

    private static GradientDrawable gradient(Context c, int from, int to) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{from, to});
        g.setCornerRadius(dp(c, 16));
        return g;
    }

    /** Width/height in px (or -1 match / -2 wrap), with a top margin in dp. */
    private static LinearLayout.LayoutParams lp(int w, int h, int unused, int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.topMargin = Math.round(topDp * android.content.res.Resources.getSystem().getDisplayMetrics().density);
        return p;
    }

    private static void setWidth(View v, int w) {
        ViewGroup.LayoutParams p = v.getLayoutParams();
        p.width = w;
        v.setLayoutParams(p);
    }

    private static void pop(View v) {
        if (!motion()) return;
        v.setAlpha(0f);
        v.setScaleX(0.92f);
        v.setScaleY(0.92f);
        v.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260)
                .setInterpolator(new OvershootInterpolator(1.6f)).start();
    }

    private static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }
}
