package in.mohammad.ramiz.confess.ui.entry;

import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.snackbar.Snackbar;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.data.model.Author;
import in.mohammad.ramiz.confess.data.model.Post;
import in.mohammad.ramiz.confess.databinding.ActivityWelcomeBinding;
import in.mohammad.ramiz.confess.databinding.SheetWelcomePeekBinding;
import in.mohammad.ramiz.confess.databinding.ViewPaperNoteBinding;
import in.mohammad.ramiz.confess.ui.base.BaseActivity;
import in.mohammad.ramiz.confess.ui.feed.FeedState;
import in.mohammad.ramiz.confess.ui.feed.PostAdapter;
import in.mohammad.ramiz.confess.ui.feed.PostCardBinder;
import in.mohammad.ramiz.confess.ui.util.EdgeInsets;

/**
 * S1 · Welcome. Old: MainActivity ("Share your thoughts without guilt" →
 * "Join the world"). Both actions open the sign-in sheet; the server decides
 * whether this Google account is new (→ onboarding) or returning (→ app).
 *
 * The paper notes fill whatever height the screen has (3 on a short phone, up to 7 on a tall
 * one), fanned like letters on a table. Tap one: a sheet opens on it in a little sample feed of
 * ten made-up confessions, drawn with the real feed cards — a glimpse of the app before signing
 * in. Felt / respond there just say "join to…".
 */
public class WelcomeActivity extends BaseActivity {

    /** Rotation and sideways nudge for each note, back to front. */
    private static final float[] ROT = {-7f, 5f, -3f, 6f, -5f, 3f, -2f};
    private static final int[] DX = {-18, 22, -10, 16, -20, 12, 0};
    private static final int[] ICONS = {R.drawable.ic_eye_off, R.drawable.ic_heart, R.drawable.ic_lock,
            R.drawable.ic_mail_open};
    private static final int MAX_NOTES = 7;

    private ActivityWelcomeBinding b;
    private boolean built = false;

    @Override
    protected boolean requiresSession() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityWelcomeBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeInsets.padSystemBars(b.root, false);

        b.noteStack.addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, or, ob) -> {
            if (!built && v.getHeight() > 0) {
                built = true;
                v.post(this::buildNotes);
            }
        });
        b.join.setOnClickListener(v -> SignInSheet.show(getSupportFragmentManager()));
        b.haveAccount.setOnClickListener(v -> SignInSheet.show(getSupportFragmentManager()));

        if (savedInstanceState == null) animateIn();
    }

    // ── The note stack ────────────────────────────────────────────────────

    /** As many notes as the space holds; the front one (fully readable) is the first sample. */
    private void buildNotes() {
        if (isFinishing()) return;
        String[] texts = getResources().getStringArray(R.array.demo_texts);
        String[] aliases = getResources().getStringArray(R.array.demo_aliases);
        int h = b.noteStack.getHeight();
        int card = dp(150), step = dp(66), minStep = dp(42);
        int n = Math.max(3, Math.min(MAX_NOTES, 1 + Math.max(0, h - card) / step));
        if (card + (n - 1) * step > h) step = Math.max(minStep, (h - card) / Math.max(1, n - 1));
        int used = card + (n - 1) * step;
        int top = Math.max(0, (h - used) / 2);
        b.noteStack.removeAllViews();
        for (int i = 0; i < n; i++) {
            int sample = n - 1 - i;                          // back → front: the front note is sample 0
            addNote(i, sample, texts[sample % texts.length], aliases[sample % aliases.length], top + i * step);
        }
    }

    private void addNote(int i, int sample, @NonNull String text, @NonNull String alias, int topMargin) {
        ViewPaperNoteBinding note = ViewPaperNoteBinding.inflate(LayoutInflater.from(this), b.noteStack, false);
        note.noteText.setText("“" + text + "”");
        note.noteAlias.setText("· " + alias);
        note.noteIcon.setImageResource(ICONS[sample % ICONS.length]);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(280), FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        lp.topMargin = topMargin;
        View card = note.getRoot();
        card.setLayoutParams(lp);
        card.setTranslationX(dp(DX[i % DX.length]));
        card.setRotation(ROT[i % ROT.length]);
        card.setClickable(true);
        card.setFocusable(true);
        card.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        card.setContentDescription(getString(R.string.welcome_peek_hint));
        card.setOnClickListener(v -> {
            Haptics.click(this);
            v.animate().scaleX(1.05f).scaleY(1.05f).rotation(0f).setDuration(140)
                    .withEndAction(() -> {
                        v.animate().scaleX(1f).scaleY(1f).rotation(ROT[i % ROT.length]).setDuration(220).start();
                        openPeek(sample);
                    }).start();
        });
        // Dealt onto the table one after another.
        card.setAlpha(0f);
        card.setTranslationY(dp(28));
        card.animate().alpha(1f).translationY(0f).setStartDelay(260L + 70L * i).setDuration(420)
                .setInterpolator(new DecelerateInterpolator(1.6f)).start();
        b.noteStack.addView(card);
    }

    // ── A glimpse inside ──────────────────────────────────────────────────

    /** The sample feed, opened at the note that was tapped. */
    private void openPeek(int sample) {
        BottomSheetDialog d = new BottomSheetDialog(this);
        SheetWelcomePeekBinding s = SheetWelcomePeekBinding.inflate(getLayoutInflater());
        ViewGroup.LayoutParams lp = s.list.getLayoutParams();
        lp.height = Math.round(getResources().getDisplayMetrics().heightPixels * 0.58f);
        s.list.setLayoutParams(lp);

        PostCardBinder.Listener nudge = new PostCardBinder.Listener() {
            @Override public void onOpen(@NonNull Post post, boolean toResponses) { say(s); }
            @Override public void onFelt(@NonNull Post post) { say(s); }
            @Override public void onReact(@NonNull Post post, @NonNull String reaction) { say(s); }
            @Override public void onMore(@NonNull Post post, @NonNull View anchor) { say(s); }
        };
        PostAdapter adapter = new PostAdapter(nudge, () -> { });
        LinearLayoutManager layout = new LinearLayoutManager(this);
        s.list.setLayoutManager(layout);
        s.list.setAdapter(adapter);
        adapter.submit(samples(), FeedState.Footer.END, () -> layout.scrollToPositionWithOffset(sample, 0));

        s.join.setOnClickListener(v -> {
            d.dismiss();
            SignInSheet.show(getSupportFragmentManager());
        });
        d.setContentView(s.getRoot());
        d.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        d.getBehavior().setSkipCollapsed(true);
        d.show();
    }

    private void say(@NonNull SheetWelcomePeekBinding s) {
        Snackbar.make(s.root, R.string.welcome_peek_nudge, Snackbar.LENGTH_SHORT)
                .setAction(R.string.welcome_join, v -> SignInSheet.show(getSupportFragmentManager()))
                .show();
    }

    /** Ten made-up confessions with believable reactions, newest first. */
    @NonNull
    private List<Post> samples() {
        String[] texts = getResources().getStringArray(R.array.demo_texts);
        String[] aliases = getResources().getStringArray(R.array.demo_aliases);
        String[] moods = getResources().getStringArray(R.array.demo_moods);
        int[] minutesAgo = {4, 18, 42, 75, 130, 190, 260, 400, 610, 900};
        int[] felt = {38, 112, 241, 57, 304, 23, 86, 167, 92, 211};
        int[] hugs = {6, 31, 64, 19, 12, 4, 27, 58, 9, 40};
        int[] responses = {7, 0, 19, 12, 25, 9, 14, 0, 6, 17};
        List<Post> out = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now();
        for (int i = 0; i < texts.length; i++) {
            Post p = new Post();
            p.postId = "sample-" + i;
            p.author = new Author();
            p.author.alias = aliases[i % aliases.length];
            p.author.avatar = String.format(java.util.Locale.ROOT, "aura_%02d", (i * 5) % 12 + 1);
            p.text = texts[i];
            String mood = i < moods.length ? moods[i] : "";
            p.mood = mood.isEmpty() ? null : mood;
            p.commentsEnabled = !"listen".equals(p.mood);
            p.commentCount = p.commentsEnabled ? responses[i % responses.length] : 0;
            Map<String, Integer> reactions = new HashMap<>();
            reactions.put("felt", felt[i % felt.length]);
            reactions.put("hug", hugs[i % hugs.length]);
            p.reactions = reactions;
            p.likeCount = felt[i % felt.length] + hugs[i % hugs.length];
            p.createdAt = now.minusMinutes(minutesAgo[i % minutesAgo.length]).toString();
            if (i == 7) p.letterTo = getString(R.string.welcome_sample_letter_to);   // an unsent letter
            out.add(p);
        }
        return out;
    }

    private void animateIn() {
        View[] seq = {b.headline1, b.headline2, b.sub, b.noteStack, b.peekHint, b.join, b.footer, b.haveAccount};
        for (int i = 0; i < seq.length; i++) {
            View v = seq[i];
            v.setAlpha(0f);
            v.setTranslationY(dp(14));
            v.animate().alpha(1f).translationY(0f).setStartDelay(80L * i).setDuration(520).start();
        }
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }
}
