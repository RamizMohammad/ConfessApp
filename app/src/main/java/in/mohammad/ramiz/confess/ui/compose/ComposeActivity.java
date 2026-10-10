package in.mohammad.ramiz.confess.ui.compose;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.core.PostEvents;
import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.api.ErrorCode;
import in.mohammad.ramiz.confess.data.model.Me;
import in.mohammad.ramiz.confess.data.model.Post;
import in.mohammad.ramiz.confess.data.repo.PostRepository;
import in.mohammad.ramiz.confess.data.repo.UserRepository;
import in.mohammad.ramiz.confess.data.session.SessionManager;
import in.mohammad.ramiz.confess.databinding.ActivityComposeBinding;
import in.mohammad.ramiz.confess.ui.base.BaseActivity;
import in.mohammad.ramiz.confess.ui.entry.GuidelinesActivity;
import in.mohammad.ramiz.confess.ui.util.EdgeInsets;
import in.mohammad.ramiz.confess.ui.util.SimpleTextWatcher;
import retrofit2.Call;

/**
 * S8 · Write a confession. Old: CreatePost (EditText + "Enable Comments" switch + Toasts).
 *
 *  - the note shows exactly how it will appear: your aura, @alias, the time
 *  - 2000-character limit (server's POST_MAX) with a live counter; over the limit → blocked
 *  - "Allow responses" = commentsEnabled
 *  - every confession is moderated on the server before it is saved:
 *      451 CONTENT_FLAGGED      → red notice with the specific reason + guidelines link
 *      503 MODERATION_UNAVAILABLE → amber notice + try again (nothing was posted)
 *  - the draft is kept on this phone while you write, and restored next time
 *  - success → RESULT_OK; the feed puts it on top (via PostEvents)
 */
public class ComposeActivity extends BaseActivity {

    public static final int MAX_CHARS = 2000;
    private static final int WARN_AT = 1800;
    private static final long DRAFT_DELAY_MS = 700;

    private ActivityComposeBinding b;
    private final PostRepository posts = new PostRepository();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable saveDraft = this::saveDraftNow;
    @Nullable private String userId;
    @Nullable private Call<Post> pending;
    private boolean working = false;
    private boolean finished = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityComposeBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeInsets.padTop(b.topBar);
        EdgeInsets.padBottom(b.bottom, true);

        Me me = SessionManager.get().cachedMe();
        renderAuthor(me);
        if (me == null) {
            new UserRepository().me(new ApiCallback<Me>() {
                @Override public void onSuccess(Me m) { renderAuthor(m); }
                @Override public void onError(ApiException e) { }
            });
        }
        b.time.setText(timeLabel());
        b.countMax.setText(getString(R.string.compose_counter_max, MAX_CHARS));

        if (savedInstanceState == null) restoreDraft();
        updateCounter();
        updateAllowText();

        b.input.addTextChangedListener(new SimpleTextWatcher(t -> {
            updateCounter();
            if (b.moderation.getVisibility() == View.VISIBLE && !working) hideNotice();
            b.draftStamp.setVisibility(View.INVISIBLE);
            handler.removeCallbacks(saveDraft);
            handler.postDelayed(saveDraft, DRAFT_DELAY_MS);
        }));
        b.allow.setOnCheckedChangeListener((v, on) -> {
            Haptics.tick(this);
            updateAllowText();
            handler.removeCallbacks(saveDraft);
            handler.postDelayed(saveDraft, DRAFT_DELAY_MS);
        });
        b.allowText.setOnClickListener(v -> b.allow.toggle());

        b.release.setOnClickListener(v -> release());
        b.discard.setOnClickListener(v -> leave());
        b.help.setOnClickListener(v -> openGuidelines());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { leave(); }
        });

        if (savedInstanceState == null) {
            b.input.postDelayed(() -> {
                if (isFinishing()) return;
                b.input.requestFocus();
                b.input.setSelection(text().length());
                WindowCompat.getInsetsController(getWindow(), b.input).show(WindowInsetsCompat.Type.ime());
            }, 350);
        }
    }

    // ── Header ───────────────────────────────────────────────────────────────

    private void renderAuthor(@Nullable Me me) {
        if (me == null) return;
        userId = me.id;
        b.avatar.setAvatar(me.avatar);
        b.alias.setText("@" + me.alias);
    }

    /** "Tonight · 02:48" between 8 pm and 5 am, otherwise "Today · 14:20" — in the phone's clock format. */
    private String timeLabel() {
        LocalTime now = LocalTime.now();
        String pattern = DateFormat.is24HourFormat(this) ? "HH:mm" : "h:mm a";
        String clock = now.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()));
        boolean night = now.getHour() >= 20 || now.getHour() < 5;
        return getString(night ? R.string.compose_tonight : R.string.compose_today, clock);
    }

    // ── Text, counter, switch ────────────────────────────────────────────────

    private String text() {
        return b.input.getText() == null ? "" : b.input.getText().toString();
    }

    private void updateCounter() {
        int n = text().trim().length();
        int over = n - MAX_CHARS;
        int color = over > 0 ? R.color.brick : (n >= WARN_AT ? R.color.amber : R.color.text_secondary);
        b.count.setText(getString(R.string.compose_counter, n));
        b.count.setTextColor(ContextCompat.getColor(this, n == 0 ? R.color.text_muted : color));
        b.tooLong.setVisibility(over > 0 ? View.VISIBLE : View.GONE);
        if (over > 0) b.tooLong.setText(getString(R.string.compose_too_long, over));
        updateButton();
    }

    private void updateButton() {
        int n = text().trim().length();
        b.release.setEnabled(!working && n > 0 && n <= MAX_CHARS);
    }

    private void updateAllowText() {
        b.allowSub.setText(b.allow.isChecked() ? R.string.compose_allow_on : R.string.compose_allow_off);
    }

    // ── Draft ────────────────────────────────────────────────────────────────

    private void restoreDraft() {
        ComposeDraft draft = ComposeDraft.load(this, userId);
        if (draft == null) return;
        b.input.setText(draft.text);
        b.allow.setChecked(draft.allowResponses);
        showStamp(R.string.compose_draft_restored);
    }

    private void saveDraftNow() {
        if (finished || b == null) return;
        ComposeDraft.save(this, userId, text(), b.allow.isChecked());
        if (!text().trim().isEmpty()) showStamp(R.string.compose_draft_kept);
    }

    private void showStamp(int res) {
        b.draftStamp.setText(res);
        if (b.draftStamp.getVisibility() != View.VISIBLE) {
            b.draftStamp.setAlpha(0f);
            b.draftStamp.setVisibility(View.VISIBLE);
            b.draftStamp.animate().alpha(1f).setDuration(200).start();
        }
    }

    // ── Release ──────────────────────────────────────────────────────────────

    private void release() {
        String body = text().trim();
        if (working || body.isEmpty() || body.length() > MAX_CHARS) return;
        handler.removeCallbacks(saveDraft);
        saveDraftNow();                         // safe even if the app dies mid-request
        hideNotice();
        setWorking(true);
        Haptics.click(this);

        pending = posts.create(body, b.allow.isChecked(), new ApiCallback<Post>() {
            @Override
            public void onSuccess(Post post) {
                finished = true;
                ComposeDraft.clear(ComposeActivity.this, userId);
                PostEvents.created(post);
                setResult(RESULT_OK);
                close();
            }

            @Override
            public void onError(ApiException e) {
                setWorking(false);
                handleError(e);
            }
        });
    }

    private void handleError(ApiException e) {
        if (e.is(ErrorCode.CONTENT_FLAGGED)) {
            Haptics.reject(this);
            if (Moderation.isEmptyAfterCleaning(e)) {
                showNotice(false, R.string.mod_flagged_title, getString(R.string.compose_empty_after_clean), false);
            } else {
                showNotice(false, R.string.mod_flagged_title, getString(Moderation.reason(e)), true);
            }
        } else if (e.is(ErrorCode.MODERATION_UNAVAILABLE)) {
            showNotice(true, R.string.mod_unavailable_title, getString(R.string.mod_unavailable_body), false);
        } else {
            Snackbar.make(b.getRoot(), e.userMessage(this), Snackbar.LENGTH_LONG)
                    .setAnchorView(b.bottom)
                    .show();
        }
    }

    /** Red (rejected) or amber (couldn't check) notice under the note. */
    private void showNotice(boolean soft, int title, String body, boolean guidelinesLink) {
        b.moderation.setBackgroundResource(soft ? R.drawable.bg_plate_amber : R.drawable.bg_plate_error);
        int tint = ContextCompat.getColor(this, soft ? R.color.amber : R.color.brick);
        b.modIcon.setImageResource(soft ? R.drawable.ic_shield : R.drawable.ic_alert);
        b.modIcon.setImageTintList(ColorStateList.valueOf(tint));
        b.modTitle.setText(title);
        b.modBody.setText(body);

        if (soft) {
            b.modAction.setVisibility(View.VISIBLE);
            b.modAction.setText(R.string.action_retry);
            b.modAction.setIconResource(R.drawable.ic_refresh);
            b.modAction.setOnClickListener(v -> release());
        } else if (guidelinesLink) {
            b.modAction.setVisibility(View.VISIBLE);
            b.modAction.setText(R.string.mod_review);
            b.modAction.setIconResource(R.drawable.ic_arrow_right);
            b.modAction.setOnClickListener(v -> openGuidelines());
        } else {
            b.modAction.setVisibility(View.GONE);
        }

        b.moderation.setAlpha(0f);
        b.moderation.setVisibility(View.VISIBLE);
        b.moderation.animate().alpha(1f).setDuration(200).start();
        b.scroll.post(() -> b.scroll.smoothScrollTo(0, b.moderation.getBottom()));
    }

    private void hideNotice() {
        b.moderation.setVisibility(View.GONE);
    }

    private void setWorking(boolean on) {
        working = on;
        b.release.setText(on ? R.string.compose_checking : R.string.compose_release);
        b.release.setIconResource(on ? 0 : R.drawable.ic_send);
        b.input.setEnabled(!on);
        b.allow.setEnabled(!on);
        b.discard.setEnabled(!on);
        b.paper.animate().alpha(on ? 0.7f : 1f).setDuration(200).start();
        updateButton();
    }

    // ── Leaving ──────────────────────────────────────────────────────────────

    private void leave() {
        if (working) return;
        if (text().trim().isEmpty()) {
            ComposeDraft.clear(this, userId);
            finished = true;
            close();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.compose_leave_title)
                .setMessage(R.string.compose_leave_body)
                .setNeutralButton(R.string.action_cancel, null)
                .setNegativeButton(R.string.compose_leave_discard, (d, w) -> {
                    finished = true;
                    handler.removeCallbacks(saveDraft);
                    ComposeDraft.clear(this, userId);
                    close();
                })
                .setPositiveButton(R.string.compose_leave_keep, (d, w) -> {
                    handler.removeCallbacks(saveDraft);
                    saveDraftNow();
                    finished = true;
                    close();
                })
                .show();
    }

    private void openGuidelines() {
        startActivity(new Intent(this, GuidelinesActivity.class).putExtra(GuidelinesActivity.EXTRA_READ_ONLY, true));
    }

    private void close() {
        finish();
        overridePendingTransition(R.anim.stay, R.anim.slide_down_out);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!finished) {
            handler.removeCallbacks(saveDraft);
            ComposeDraft.save(this, userId, text(), b.allow.isChecked());
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(saveDraft);
        if (pending != null && !finished) pending.cancel();
        super.onDestroy();
    }
}
