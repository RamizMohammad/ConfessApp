package in.mohammad.ramiz.confess.ui.stories;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.os.Bundle;
import in.mohammad.ramiz.confess.ui.feed.Reactions;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;
import android.view.Gravity;
import android.util.TypedValue;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Formats;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.model.Story;
import in.mohammad.ramiz.confess.data.model.StoryGroup;
import in.mohammad.ramiz.confess.data.repo.StoryRepository;
import in.mohammad.ramiz.confess.databinding.ActivityStoryViewerBinding;
import in.mohammad.ramiz.confess.ui.base.BaseActivity;
import in.mohammad.ramiz.confess.ui.nav.Nav;
import in.mohammad.ramiz.confess.ui.util.EdgeInsets;

/**
 * Full-screen stories, one person after another (the order of the feed tray).
 *  - 5 s per story; tap right → next, tap left → back, hold → pause, swipe down → close
 *  - opening someone starts at their first unseen story; each one shown is marked seen
 *  - mine: "Seen by N" (who viewed) and Delete; others: Report (hides it for me)
 */
public class StoryViewerActivity extends BaseActivity {

    public static final String EXTRA_GROUP = "group";
    /** Only that person (opened from an avatar ring), not the whole tray. */
    public static final String EXTRA_SINGLE = "single";
    private static final String STATE_GROUP = "g";
    private static final String STATE_STORY = "s";

    private static final long STORY_MS = 5_000;
    private static final long HOLD_MS = 180;

    private ActivityStoryViewerBinding b;
    private final StoryRepository repo = new StoryRepository();
    private final Handler handler = new Handler(Looper.getMainLooper());

    /** The cache's own list (or just one of its groups) — seen flags / removals here show up in the tray. */
    private List<StoryGroup> groups;
    private boolean single;
    private int groupIndex, storyIndex;
    private final List<View> fills = new ArrayList<>();
    @Nullable private ValueAnimator timer;

    // Pause reasons — playback runs only when none is set.
    private boolean held, overlay, backgrounded;

    // Touch
    private float downX, downY;
    private boolean dragging;
    private int touchSlop;
    private final Runnable holdStart = () -> {
        held = true;
        chrome(false);
        updatePlayback();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityStoryViewerBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeInsets.padTop(b.top);
        EdgeInsets.padBottom(b.bottom, false);
        touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();

        groups = StoryTrayCache.get();
        single = getIntent().getBooleanExtra(EXTRA_SINGLE, false);
        int requested = getIntent().getIntExtra(EXTRA_GROUP, 0);
        if (single) {
            StoryGroup only = requested >= 0 && requested < groups.size() ? groups.get(requested) : null;
            groups = new ArrayList<>();
            if (only != null) groups.add(only);
            requested = 0;
        }
        dropEmptyGroups();
        if (groups.isEmpty()) {
            finish();
            return;
        }
        if (savedInstanceState != null) {
            groupIndex = single ? 0 : savedInstanceState.getInt(STATE_GROUP, 0);
            storyIndex = savedInstanceState.getInt(STATE_STORY, 0);
        } else {
            groupIndex = requested;
            storyIndex = -1;
        }
        groupIndex = Math.max(0, Math.min(groupIndex, groups.size() - 1));

        b.close.setOnClickListener(v -> close());
        b.more.setOnClickListener(this::showMenu);
        b.seenBy.setOnClickListener(v -> showViewers());
        buildReactions();
        setupTouch();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { close(); }
        });

        openGroup(groupIndex, storyIndex);
    }

    // ── Navigation ───────────────────────────────────────────────────────────

    private StoryGroup group() {
        return groups.get(groupIndex);
    }

    /** startAt < 0 → first unseen (or the first one if all are seen / it's mine). */
    private void openGroup(int index, int startAt) {
        groupIndex = index;
        StoryGroup g = group();
        int start = startAt;
        if (start < 0 || start >= g.stories.size()) {
            start = 0;
            if (!g.isMe && startAt < 0) {
                for (int i = 0; i < g.stories.size(); i++) {
                    if (!g.stories.get(i).seen) { start = i; break; }
                }
            }
        }
        buildSegments(g.stories.size());
        bindHeader(g);
        show(start);
    }

    private void show(int index) {
        StoryGroup g = group();
        storyIndex = index;
        Story s = g.stories.get(index);

        for (int i = 0; i < fills.size(); i++) fills.get(i).setScaleX(i < index ? 1f : 0f);

        b.card.setBackground(StoryStyles.card(this, s.style, 0f));
        b.text.setTextColor(StoryStyles.text(this, s.style));
        b.text.setText(s.text);
        b.text.setAlpha(0f);
        b.text.animate().alpha(1f).setDuration(160).start();
        b.time.setText(Formats.relative(s.createdAt));
        b.card.setContentDescription(s.text);

        b.bottom.setVisibility(View.VISIBLE);
        if (g.isMe) {
            int n = s.viewCount == null ? 0 : s.viewCount;
            int reacted = s.reactionCount == null ? 0 : s.reactionCount;
            b.seenBy.setText(n == 0 ? getString(R.string.story_seen_by_none)
                    : reacted > 0 ? getString(R.string.story_seen_by_reacted, n, reacted)
                    : getString(R.string.story_seen_by, n));
            b.seenBy.setVisibility(View.VISIBLE);
            b.reactions.setVisibility(View.GONE);
        } else {
            b.seenBy.setVisibility(View.GONE);
            b.reactions.setVisibility(View.VISIBLE);
            renderReactions(s);
            markSeen(g, s);
        }
        startTimer();
    }

    private void next() {
        StoryGroup g = group();
        if (storyIndex < g.stories.size() - 1) {
            show(storyIndex + 1);
        } else if (groupIndex < groups.size() - 1) {
            switchGroup(groupIndex + 1, true);
        } else {
            close();
        }
    }

    private void prev() {
        if (storyIndex > 0) {
            show(storyIndex - 1);
        } else if (groupIndex > 0) {
            switchGroup(groupIndex - 1, false);
        } else {
            show(0);                                   // first of all: replay it
        }
    }

    /** Between people: a short slide so it's clear someone else is speaking now. */
    private void switchGroup(int index, boolean forward) {
        stopTimer();
        float w = b.page.getWidth() * 0.12f;
        b.page.animate().cancel();
        b.page.setTranslationX(forward ? w : -w);
        b.page.setAlpha(0.4f);
        b.page.animate().translationX(0f).alpha(1f).setDuration(220).start();
        openGroup(index, forward ? -1 : 0);
    }

    private void bindHeader(@NonNull StoryGroup g) {
        b.more.setIconResource(g.isMe ? R.drawable.ic_trash : R.drawable.ic_flag);
        b.more.setContentDescription(getString(g.isMe ? R.string.story_delete : R.string.story_report));
        String alias = g.author != null ? g.author.alias : null;
        b.avatar.setAvatar(g.author != null ? g.author.avatar : null);
        b.alias.setText(g.isMe ? getString(R.string.story_yours) : "@" + (alias == null ? "" : alias));
        if (!g.isMe && alias != null) {
            b.who.setOnClickListener(v -> Nav.profile(this, alias));
            b.who.setClickable(true);
        } else {
            b.who.setOnClickListener(null);
            b.who.setClickable(false);
        }
    }

    private void close() {
        stopTimer();
        finish();
        overridePendingTransition(R.anim.stay, R.anim.story_close);
    }

    // ── Progress ─────────────────────────────────────────────────────────────

    private void buildSegments(int count) {
        b.progress.removeAllViews();
        fills.clear();
        int gap = Math.round(getResources().getDisplayMetrics().density * 2);
        for (int i = 0; i < count; i++) {
            FrameLayout track = new FrameLayout(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            if (i > 0) lp.setMarginStart(gap);
            track.setLayoutParams(lp);
            track.setBackgroundResource(R.drawable.bg_story_segment_track);
            View fill = new View(this);
            fill.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            fill.setBackgroundResource(R.drawable.bg_story_segment_fill);
            fill.setPivotX(0f);
            fill.setScaleX(0f);
            track.addView(fill);
            b.progress.addView(track);
            fills.add(fill);
        }
        // pivotX 0 must be measured from the real left edge in RTL too
        b.progress.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
    }

    private void startTimer() {
        stopTimer();
        View fill = fills.get(storyIndex);
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(STORY_MS);
        a.setInterpolator(new LinearInterpolator());
        a.addUpdateListener(an -> fill.setScaleX((float) an.getAnimatedValue()));
        a.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(Animator animation) { cancelled = true; }
            @Override public void onAnimationEnd(Animator animation) {
                if (!cancelled && timer == animation && !isFinishing()) next();
            }
        });
        timer = a;
        a.start();
        updatePlayback();
    }

    private void stopTimer() {
        if (timer != null) {
            ValueAnimator t = timer;
            timer = null;
            t.cancel();
        }
    }

    private void updatePlayback() {
        if (timer == null) return;
        if (held || overlay || backgrounded) timer.pause();
        else timer.resume();
    }

    private void chrome(boolean visible) {
        float a = visible ? 1f : 0f;
        b.top.animate().alpha(a).setDuration(150).start();
        b.bottom.animate().alpha(a).setDuration(150).start();
    }

    // ── Touch: tap sides, hold, swipe down ───────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private void setupTouch() {
        b.card.setOnClickListener(v -> next());          // accessibility "click" = next
        b.card.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX();
                    downY = e.getRawY();
                    dragging = false;
                    handler.postDelayed(holdStart, HOLD_MS);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                    if (!dragging && dy > touchSlop && dy > Math.abs(dx)) {
                        dragging = true;
                        handler.removeCallbacks(holdStart);
                        held = true;
                        updatePlayback();
                    }
                    if (dragging) {
                        float t = Math.max(0f, dy);
                        float f = Math.min(1f, t / Math.max(1, b.root.getHeight()));
                        b.page.setTranslationY(t);
                        b.page.setScaleX(1f - f * 0.12f);
                        b.page.setScaleY(1f - f * 0.12f);
                        b.page.setAlpha(1f - f * 0.5f);
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP: {
                    handler.removeCallbacks(holdStart);
                    float dy = e.getRawY() - downY;
                    if (dragging) {
                        dragging = false;
                        if (dy > b.root.getHeight() * 0.18f) {
                            close();
                        } else {
                            springBack();
                            held = false;
                            updatePlayback();
                        }
                    } else if (held) {
                        held = false;
                        chrome(true);
                        updatePlayback();
                    } else if (e.getX() < v.getWidth() * 0.3f) {
                        prev();
                    } else {
                        v.performClick();                    // → next()
                    }
                    return true;
                }
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(holdStart);
                    if (dragging) springBack();
                    dragging = false;
                    if (held) chrome(true);
                    held = false;
                    updatePlayback();
                    return true;
                default:
                    return false;
            }
        });
    }

    private void springBack() {
        b.page.animate().translationY(0f).scaleX(1f).scaleY(1f).alpha(1f).setDuration(180).start();
    }

    // ── Seen ─────────────────────────────────────────────────────────────────

    private void markSeen(@NonNull StoryGroup g, @NonNull Story s) {
        if (s.seen) return;
        s.seen = true;
        boolean any = false;
        for (Story x : g.stories) if (!x.seen) { any = true; break; }
        g.hasUnseen = any;
        StoryTrayCache.changed();
        repo.markSeen(s.id, new ApiCallback<JsonObject>() {
            @Override public void onSuccess(JsonObject data) { }
            @Override public void onError(ApiException e) { }   // best effort — the ring catches up on next load
        });
    }

    // ── Menu: delete (mine) / report (theirs) ────────────────────────────────

    /** Mine → bin (delete); theirs → flag (report). One tap, straight to the confirm dialog. */
    private void showMenu(@NonNull View anchor) {
        Haptics.tick(this);
        setOverlay(true);                                   // the dialogs resume playback on dismiss
        if (group().isMe) confirmDelete();
        else confirmReport();
    }

    private void confirmDelete() {
        Story s = group().stories.get(storyIndex);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.story_delete_title)
                .setMessage(R.string.story_delete_body)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.story_delete, (d, w) -> {
                    Haptics.click(this);
                    repo.delete(s.id, new ApiCallback<JsonObject>() {
                        @Override public void onSuccess(JsonObject data) {
                            Toast.makeText(StoryViewerActivity.this, R.string.story_deleted, Toast.LENGTH_SHORT).show();
                            removeStory(s);
                        }
                        @Override public void onError(ApiException e) {
                            Toast.makeText(StoryViewerActivity.this, e.userMessage(StoryViewerActivity.this), Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setOnDismissListener(d -> setOverlay(false))
                .show();
    }

    private void confirmReport() {
        Story s = group().stories.get(storyIndex);
        String[] codes = {"hate_or_harassment", "reveals_identity", "threat_or_self_harm", "spam", "other"};
        CharSequence[] labels = {
                getString(R.string.report_hate), getString(R.string.report_personal),
                getString(R.string.report_threat), getString(R.string.report_spam), getString(R.string.report_other)};
        int[] picked = {-1};
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.story_report_title)
                .setSingleChoiceItems(labels, -1, (d, which) -> {
                    picked[0] = which;
                    Haptics.tick(this);
                    ((androidx.appcompat.app.AlertDialog) d).getButton(android.content.DialogInterface.BUTTON_POSITIVE).setEnabled(true);
                })
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.story_report_send, (d, w) -> {
                    if (picked[0] < 0) return;
                    Haptics.click(this);
                    repo.report(s.id, codes[picked[0]], new ApiCallback<JsonObject>() {
                        @Override public void onSuccess(JsonObject data) {
                            Toast.makeText(StoryViewerActivity.this, R.string.story_reported, Toast.LENGTH_SHORT).show();
                            removeStory(s);
                        }
                        @Override public void onError(ApiException e) {
                            Toast.makeText(StoryViewerActivity.this, e.userMessage(StoryViewerActivity.this), Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setOnDismissListener(d -> setOverlay(false))
                .create();
        dialog.show();
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setEnabled(false);
    }

    /** Deleted / reported: take it out here and in the tray, then carry on. */
    private void removeStory(@NonNull Story s) {
        if (isFinishing() || isDestroyed()) return;
        StoryGroup g = null;
        int gi = -1;
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i).stories.remove(s)) { g = groups.get(i); gi = i; break; }
        }
        if (g == null) return;
        boolean any = false;
        for (Story x : g.stories) if (!x.seen) { any = true; break; }
        g.hasUnseen = any;

        if (g.stories.isEmpty() && single) StoryTrayCache.get().remove(g);
        StoryTrayCache.changed();

        if (!g.stories.isEmpty()) {
            if (gi == groupIndex) openGroup(groupIndex, Math.min(storyIndex, g.stories.size() - 1));
            return;
        }
        groups.remove(gi);
        if (gi < groupIndex) groupIndex--;
        if (groups.isEmpty()) {
            close();
        } else if (gi == groupIndex) {
            if (groupIndex < groups.size()) openGroup(groupIndex, -1);
            else close();
        }
    }

    // ── Viewers ──────────────────────────────────────────────────────────────

    // ── Reactions (others' stories) ──────────────────────────────────────────

    private final List<TextView> reactionViews = new ArrayList<>();

    private void buildReactions() {
        float d = getResources().getDisplayMetrics().density;
        int size = Math.round(46 * d);
        for (String key : Reactions.KEYS) {
            TextView e = new TextView(this);
            e.setText(Reactions.emoji(key));
            e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
            e.setGravity(Gravity.CENTER);
            e.setLayoutParams(new LinearLayout.LayoutParams(size, size));
            e.setContentDescription(getString(R.string.story_react_cd, getString(Reactions.label(key))));
            e.setOnClickListener(v -> react(key, v));
            b.reactions.addView(e);
            reactionViews.add(e);
        }
    }

    private void renderReactions(@NonNull Story s) {
        for (int i = 0; i < reactionViews.size(); i++) {
            boolean mine = Reactions.KEYS[i].equals(s.myReaction);
            TextView v = reactionViews.get(i);
            v.setBackgroundResource(mine ? R.drawable.bg_reaction_selected : 0);
            v.setAlpha(s.myReaction == null || mine ? 1f : 0.6f);
        }
    }

    /** Tap a reaction: send it (tap my current one again to take it back), with a burst. */
    private void react(@NonNull String key, @NonNull View from) {
        StoryGroup g = group();
        if (g.isMe) return;
        Story s = g.stories.get(storyIndex);
        String before = s.myReaction;
        String want = key.equals(before) ? null : key;
        s.myReaction = want;                                   // optimistic (the tray cache shares this object)
        renderReactions(s);
        Haptics.click(this);
        from.animate().scaleX(1.4f).scaleY(1.4f).setDuration(110)
                .withEndAction(() -> from.animate().scaleX(1f).scaleY(1f).setDuration(160).start()).start();
        if (want != null) {
            burst(want);
            startTimer();                                      // restart this story so they see it land
        }
        repo.react(s.id, want, new ApiCallback<JsonObject>() {
            @Override public void onSuccess(JsonObject data) { }
            @Override public void onError(ApiException e) {
                if (isFinishing() || isDestroyed()) return;
                s.myReaction = before;                          // undo
                if (group().stories.indexOf(s) == storyIndex) renderReactions(s);
                Toast.makeText(StoryViewerActivity.this, e.userMessage(StoryViewerActivity.this), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** The chosen emoji pops in the middle of the card and floats up. */
    private void burst(@NonNull String key) {
        float d = getResources().getDisplayMetrics().density;
        TextView v = b.burst;
        v.animate().cancel();
        v.setText(Reactions.emoji(key));
        v.setVisibility(View.VISIBLE);
        v.setAlpha(0f);
        v.setScaleX(0.2f);
        v.setScaleY(0.2f);
        v.setTranslationY(0f);
        v.animate().alpha(1f).scaleX(1.1f).scaleY(1.1f).setStartDelay(0).setDuration(260)
                .setInterpolator(new OvershootInterpolator(2.6f))
                .withEndAction(() -> v.animate().alpha(0f).scaleX(1.4f).scaleY(1.4f).translationY(-80 * d)
                        .setStartDelay(260).setDuration(380).setInterpolator(new AccelerateInterpolator())
                        .withEndAction(() -> {
                            v.animate().setStartDelay(0);
                            v.setVisibility(View.GONE);
                        }).start())
                .start();
    }

    private void showViewers() {
        StoryGroup g = group();
        if (!g.isMe) return;
        Story s = g.stories.get(storyIndex);
        setOverlay(true);
        StoryViewersSheet sheet = new StoryViewersSheet(this, repo, s.id, s.viewCount == null ? 0 : s.viewCount);
        sheet.setOnDismiss(() -> setOverlay(false));
        sheet.show();
    }

    private void setOverlay(boolean on) {
        overlay = on;
        updatePlayback();
    }

    private void dropEmptyGroups() {
        for (int i = groups.size() - 1; i >= 0; i--) {
            StoryGroup g = groups.get(i);
            if (g == null || g.stories == null || g.stories.isEmpty()) groups.remove(i);
        }
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    protected void onPause() {
        super.onPause();
        backgrounded = true;
        updatePlayback();
    }

    @Override
    protected void onResume() {
        super.onResume();
        backgrounded = false;
        updatePlayback();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(STATE_GROUP, groupIndex);
        out.putInt(STATE_STORY, storyIndex);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        stopTimer();
        super.onDestroy();
    }
}
