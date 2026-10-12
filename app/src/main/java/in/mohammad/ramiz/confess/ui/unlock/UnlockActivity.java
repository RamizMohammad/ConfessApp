package in.mohammad.ramiz.confess.ui.unlock;

import android.animation.ValueAnimator;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.EditorInfo;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.JsonObject;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.core.ResetNotice;
import in.mohammad.ramiz.confess.core.push.Push;
import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.api.ErrorCode;
import in.mohammad.ramiz.confess.data.model.MatchResult;
import in.mohammad.ramiz.confess.data.model.Me;
import in.mohammad.ramiz.confess.data.repo.AuthRepository;
import in.mohammad.ramiz.confess.data.repo.UserRepository;
import in.mohammad.ramiz.confess.data.session.SessionManager;
import in.mohammad.ramiz.confess.databinding.ActivityUnlockBinding;
import in.mohammad.ramiz.confess.ui.auth.Biometrics;
import in.mohammad.ramiz.confess.ui.base.BaseActivity;
import in.mohammad.ramiz.confess.ui.base.Router;
import in.mohammad.ramiz.confess.ui.util.EdgeInsets;
import in.mohammad.ramiz.confess.ui.util.SimpleTextWatcher;
import retrofit2.Call;

/**
 * S6 · Unlock. Old: Password_Page (+ BiometricLogin).
 *
 *  - password → POST users/me/password/verify; on match the session is unlocked
 *  - biometric (if turned on for the account AND available on this phone) opens
 *    automatically, like the old app; it unlocks locally. Android draws the sheet;
 *    around it the avatar pulses amber, a check pops in on success, and the sheet's
 *    "Use password" button drops straight into the password field
 *  - 5 wrong passwords → 30 s pause (the server also rate-limits)
 *  - "Forgot password?" → emails a reset link to the signed-in Google account
 *  - "Not you? Sign out"
 *
 * Two entry modes:
 *  - from the launcher: success → home
 *  - EXTRA_RELOCK (app came back after 5 min away): success → just close, revealing
 *    the screen the user was on
 */
public class UnlockActivity extends BaseActivity {

    public static final String EXTRA_RELOCK = "relock";

    private static final int MAX_TRIES = 5;
    private static final long COOLDOWN_MS = 30_000;

    private ActivityUnlockBinding b;
    private final UserRepository users = new UserRepository();
    private boolean relock;
    private boolean bioAllowed;
    private int wrongTries = 0;
    private boolean working = false;
    @Nullable private CountDownTimer cooldown;
    @Nullable private Call<MatchResult> pending;
    @Nullable private ValueAnimator pulse;
    private boolean unlocking = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityUnlockBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeInsets.padSystemBars(b.root, true);
        relock = getIntent().getBooleanExtra(EXTRA_RELOCK, false);

        // Nothing to unlock (lock removed elsewhere, or already unlocked) → move on.
        if (!SessionManager.get().needsUnlock()) {
            done();
            return;
        }

        // Cached profile for the face/alias; the fingerprint shortcut waits for the
        // server's answer (a password reset may have switched it off).
        boolean justReset = ResetNotice.justReset(this);
        render(SessionManager.get().cachedMe(), false);
        users.me(new ApiCallback<Me>() {          // fresh alias/avatar/biometric flag
            @Override public void onSuccess(Me me) {
                if (!me.hasPassword) { SessionManager.get().markUnlocked(); done(); return; }
                render(me, true);
                if (savedInstanceState == null && bioAllowed) b.getRoot().postDelayed(UnlockActivity.this::promptBiometric, 350);
            }
            @Override public void onError(ApiException e) {
                // Offline: fall back to the cached flag, unless we know a reset just happened.
                if (justReset) return;
                render(SessionManager.get().cachedMe(), true);
                if (savedInstanceState == null && bioAllowed) promptBiometric();
            }
        });

        b.passwordInput.addTextChangedListener(new SimpleTextWatcher(t -> {
            b.passwordLayout.setError(null);
            updateButton();
        }));
        b.passwordInput.setOnEditorActionListener((v, action, event) -> {
            boolean go = action == EditorInfo.IME_ACTION_GO
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER);
            if (go && b.unlock.isEnabled()) verify();
            return go;
        });
        b.unlock.setOnClickListener(v -> verify());
        b.biometric.setOnClickListener(v -> promptBiometric());
        b.forgot.setOnClickListener(v -> confirmForgot());
        b.signOut.setOnClickListener(v -> confirmSignOut());

        // Never let "back" bypass the lock: leave the app instead.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { moveTaskToBack(true); }
        });

    }

    private void render(@Nullable Me me, boolean fresh) {
        boolean justReset = ResetNotice.justReset(this);
        if (me != null) {
            b.avatar.setAvatar(me.avatar);
            b.alias.setText(me.alias);
            bioAllowed = fresh && me.biometricEnabled && !justReset && Biometrics.available(this);
        }
        b.bioGroup.setVisibility(bioAllowed ? View.VISIBLE : View.GONE);

        // "Your password was just reset" — and why the fingerprint button is gone.
        boolean bioOff = me != null && me.biometricResetPending;
        b.resetNotice.setVisibility(justReset || bioOff ? View.VISIBLE : View.GONE);
        b.resetNoticeBody.setText(bioOff ? R.string.reset_notice_bio : R.string.reset_notice_plain);
    }

    private String password() {
        return b.passwordInput.getText() == null ? "" : b.passwordInput.getText().toString();
    }

    private void updateButton() {
        b.unlock.setEnabled(!working && cooldown == null && !password().isEmpty());
    }

    // ── Password ─────────────────────────────────────────────────────────────

    private void verify() {
        if (working || cooldown != null) return;
        setWorking(true);
        pending = users.verifyPassword(password(), new ApiCallback<MatchResult>() {
            @Override
            public void onSuccess(MatchResult r) {
                setWorking(false);
                if (r.match) {
                    Haptics.click(UnlockActivity.this);
                    showUnlocked();
                } else {
                    wrong();
                }
            }

            @Override
            public void onError(ApiException e) {
                setWorking(false);
                if (e.is(ErrorCode.PASSWORD_NOT_SET)) {          // lock was removed elsewhere
                    SessionManager.get().markUnlocked();
                    done();
                } else if (e.is(ErrorCode.RATE_LIMITED)) {
                    startCooldown();
                } else {
                    Haptics.reject(UnlockActivity.this);
                    b.passwordLayout.setError(e.userMessage(UnlockActivity.this));
                }
            }
        });
    }

    private void wrong() {
        wrongTries++;
        Haptics.reject(this);
        shake(b.passwordLayout);
        b.passwordInput.setText("");
        int left = MAX_TRIES - wrongTries;
        if (left <= 0) {
            startCooldown();
        } else {
            b.passwordLayout.setError(left <= 2
                    ? getString(R.string.unlock_wrong_left, left)
                    : getString(R.string.unlock_wrong));
        }
    }

    private void startCooldown() {
        wrongTries = 0;
        if (cooldown != null) cooldown.cancel();
        cooldown = new CountDownTimer(COOLDOWN_MS, 1000) {
            @Override public void onTick(long ms) {
                b.passwordLayout.setError(getString(R.string.unlock_cooldown, (int) Math.ceil(ms / 1000.0)));
            }
            @Override public void onFinish() {
                cooldown = null;
                b.passwordLayout.setError(null);
                updateButton();
            }
        }.start();
        updateButton();
    }

    private void setWorking(boolean on) {
        working = on;
        b.unlock.setText(on ? R.string.unlock_checking : R.string.unlock_button);
        b.passwordInput.setEnabled(!on);
        updateButton();
    }

    private void shake(View v) {
        v.animate().translationX(18f).setDuration(50).withEndAction(() ->
                v.animate().translationX(-14f).setDuration(50).withEndAction(() ->
                        v.animate().translationX(8f).setDuration(50).withEndAction(() ->
                                v.animate().translationX(0f).setDuration(50).start()).start()).start()).start();
    }

    // ── Biometric ────────────────────────────────────────────────────────────

    private void promptBiometric() {
        if (!bioAllowed || unlocking || isFinishing()) return;
        startPulse();
        Biometrics.prompt(this, getString(R.string.unlock_bio_title), getString(R.string.unlock_bio_subtitle),
                getString(R.string.unlock_bio_use_password),
                new Biometrics.Callback() {
                    @Override public void onSuccess() {
                        Haptics.click(UnlockActivity.this);
                        SessionManager.get().markUnlocked();
                        showUnlocked();
                    }
                    @Override public void onFailure(@NonNull CharSequence reason) {
                        stopPulse();
                        b.passwordInput.requestFocus();    // fall back to the password
                    }
                    @Override public void onNegative() {   // "Use password"
                        stopPulse();
                        b.passwordInput.requestFocus();
                        WindowCompat.getInsetsController(getWindow(), b.passwordInput)
                                .show(WindowInsetsCompat.Type.ime());
                    }
                });
    }

    /** Amber ring breathing out of the avatar while the system sheet is up. */
    private void startPulse() {
        if (pulse != null) return;
        pulse = ValueAnimator.ofFloat(0f, 1f);
        pulse.setDuration(1400);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setInterpolator(new DecelerateInterpolator());
        pulse.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            b.bioPulse.setScaleX(1f + 0.38f * f);
            b.bioPulse.setScaleY(1f + 0.38f * f);
            b.bioPulse.setAlpha(0.9f * (1f - f));
        });
        pulse.start();
    }

    private void stopPulse() {
        if (pulse != null) { pulse.cancel(); pulse = null; }
        b.bioPulse.animate().alpha(0f).setDuration(150).start();
    }

    /** Check pops onto the avatar, then the app opens. */
    private void showUnlocked() {
        if (unlocking) return;
        unlocking = true;
        stopPulse();
        b.unlockedBadge.setScaleX(0f);
        b.unlockedBadge.setScaleY(0f);
        b.unlockedBadge.setVisibility(View.VISIBLE);
        b.unlockedBadge.animate().scaleX(1f).scaleY(1f).setDuration(260)
                .setInterpolator(new OvershootInterpolator(2.2f)).start();
        b.avatar.animate().scaleX(1.06f).scaleY(1.06f).setDuration(150).withEndAction(() ->
                b.avatar.animate().scaleX(1f).scaleY(1f).setDuration(170).start()).start();
        b.getRoot().postDelayed(() -> { if (!isFinishing()) done(); }, 520);
    }

    // ── Forgot / sign out ────────────────────────────────────────────────────

    private void confirmForgot() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.forgot_title)
                .setMessage(R.string.forgot_body)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.forgot_send, (d, w) -> sendReset())
                .show();
    }

    private void sendReset() {
        new AuthRepository().forgotPassword(new ApiCallback<JsonObject>() {
            @Override public void onSuccess(JsonObject data) {
                Snackbar.make(b.getRoot(), R.string.forgot_sent, Snackbar.LENGTH_LONG).show();
            }
            @Override public void onError(ApiException e) {
                Snackbar.make(b.getRoot(), e.userMessage(UnlockActivity.this), Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void confirmSignOut() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.signout_title)
                .setMessage(R.string.signout_body)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.signout_confirm, (d, w) -> {
                    Push.signOut(this);
                    Router.toWelcome(this);
                })
                .show();
    }

    // ── Exit ─────────────────────────────────────────────────────────────────

    private void done() {
        if (!SessionManager.get().needsUnlock()) ResetNotice.clear(this);
        if (relock) {
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        } else {
            Router.toApp(this);
        }
    }

    @Override
    protected void onDestroy() {
        if (cooldown != null) cooldown.cancel();
        if (pending != null) pending.cancel();
        if (pulse != null) pulse.cancel();
        super.onDestroy();
    }
}
