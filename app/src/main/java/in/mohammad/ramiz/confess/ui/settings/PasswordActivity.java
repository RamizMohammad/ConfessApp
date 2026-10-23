package in.mohammad.ramiz.confess.ui.settings;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.JsonObject;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.core.push.Push;
import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.api.Pending;
import in.mohammad.ramiz.confess.data.api.ErrorCode;
import in.mohammad.ramiz.confess.data.model.Me;
import in.mohammad.ramiz.confess.data.repo.AuthRepository;
import in.mohammad.ramiz.confess.data.repo.UserRepository;
import in.mohammad.ramiz.confess.data.session.SessionManager;
import in.mohammad.ramiz.confess.databinding.ActivityPasswordBinding;
import in.mohammad.ramiz.confess.ui.base.BaseActivity;
import in.mohammad.ramiz.confess.ui.base.Router;
import in.mohammad.ramiz.confess.ui.util.EdgeInsets;
import in.mohammad.ramiz.confess.ui.util.PasswordStrength;
import in.mohammad.ramiz.confess.ui.util.SimpleTextWatcher;

/**
 * S14 · Turn on the password lock, or change it. Old: UpdatePassBio / ChangePassword.
 *   MODE_SET    new + confirm           → POST users/me/password/set
 *   MODE_CHANGE current + new + confirm → POST users/me/password/change (401 WRONG_PASSWORD)
 */
public class PasswordActivity extends BaseActivity {

    public static final String EXTRA_MODE = "mode";
    public static final String MODE_SET = "set";
    public static final String MODE_CHANGE = "change";

    private ActivityPasswordBinding b;
    private final UserRepository users = new UserRepository();
    private boolean change;
    private boolean working;
    @Nullable private Pending pending;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityPasswordBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeInsets.padTop(b.topBar);
        EdgeInsets.padBottom(b.bottom, true);

        change = MODE_CHANGE.equals(getIntent().getStringExtra(EXTRA_MODE));
        b.title.setText(change ? R.string.password_change_title : R.string.password_set_title);
        b.lead.setText(change ? R.string.password_change_lead : R.string.password_set_lead);
        b.save.setText(change ? R.string.password_save_change : R.string.password_save_set);
        int currentVis = change ? View.VISIBLE : View.GONE;
        b.currentLabel.setVisibility(currentVis);
        b.currentLayout.setVisibility(currentVis);
        b.forgot.setVisibility(currentVis);

        Me me = SessionManager.get().cachedMe();
        if (me != null) {
            b.avatar.setAvatar(me.avatar);
            b.alias.setText("@" + me.alias);
        }

        SimpleTextWatcher watcher = new SimpleTextWatcher(t -> {
            b.currentLayout.setError(null);
            b.newLayout.setError(null);
            b.confirmLayout.setError(null);
            update();
        });
        b.currentInput.addTextChangedListener(watcher);
        b.newInput.addTextChangedListener(watcher);
        b.confirmInput.addTextChangedListener(watcher);
        b.confirmInput.setOnEditorActionListener((v, action, e) -> {
            if (action == EditorInfo.IME_ACTION_DONE && b.save.isEnabled()) { save(); return true; }
            return false;
        });
        b.back.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        b.save.setOnClickListener(v -> save());
        b.forgot.setOnClickListener(v -> confirmForgot());
        update();
    }

    private static String text(TextView v) {
        return v.getText() == null ? "" : v.getText().toString();
    }

    private void update() {
        String current = text(b.currentInput), pw = text(b.newInput), again = text(b.confirmInput);

        int s = PasswordStrength.score(pw);
        @ColorRes int on = s <= 1 ? R.color.brick : (s == 2 ? R.color.amber : R.color.sage);
        View[] segs = {b.seg1, b.seg2, b.seg3};
        for (int i = 0; i < segs.length; i++) {
            segs[i].setBackgroundTintList(ContextCompat.getColorStateList(this, i < s ? on : R.color.hairline));
        }
        b.strengthText.setText(s == 0 ? "" : getString(s == 1 ? R.string.strength_weak
                : s == 2 ? R.string.strength_fair : R.string.strength_strong));
        b.strengthText.setTextColor(ContextCompat.getColor(this, s == 0 ? R.color.text_muted : on));
        rule(b.ruleLength, PasswordStrength.longEnough(pw));
        rule(b.ruleMix, PasswordStrength.hasMix(pw));
        rule(b.ruleSymbol, PasswordStrength.hasSymbol(pw));

        boolean match = !again.isEmpty() && again.equals(pw);
        b.confirmLayout.setEndIconTintList(ColorStateList.valueOf(
                ContextCompat.getColor(this, match ? R.color.sage : R.color.text_muted)));
        boolean ok = PasswordStrength.longEnough(pw) && pw.length() <= PasswordStrength.MAX
                && match && (!change || !current.isEmpty());
        b.save.setEnabled(ok && !working);
    }

    private void rule(TextView pill, boolean met) {
        int color = ContextCompat.getColor(this, met ? R.color.sage : R.color.text_muted);
        pill.setTextColor(color);
        // A small 14dp tick (the 24dp intrinsic one made the pill taller and pushed the next one off-screen).
        android.graphics.drawable.Drawable tick = null;
        if (met) {
            tick = ContextCompat.getDrawable(this, R.drawable.ic_check);
            if (tick != null) {
                tick = tick.mutate();
                int size = Math.round(14 * getResources().getDisplayMetrics().density);
                tick.setBounds(0, 0, size, size);
            }
        }
        pill.setCompoundDrawablesRelative(tick, null, null, null);
        pill.setCompoundDrawableTintList(ColorStateList.valueOf(color));
    }

    private void save() {
        String current = text(b.currentInput), pw = text(b.newInput), again = text(b.confirmInput);
        if (!pw.equals(again)) { b.confirmLayout.setError(getString(R.string.password_mismatch)); return; }
        if (change && current.equals(pw)) { b.newLayout.setError(getString(R.string.password_same)); return; }
        setWorking(true);
        ApiCallback<JsonObject> cb = new ApiCallback<JsonObject>() {
            @Override public void onSuccess(JsonObject data) {
                Haptics.click(PasswordActivity.this);
                SessionManager.get().markUnlocked();      // don't lock the person who just set it
                refreshMeThenFinish();
            }
            @Override public void onError(ApiException e) {
                setWorking(false);
                Haptics.reject(PasswordActivity.this);
                if (e.is(ErrorCode.WRONG_PASSWORD)) {
                    b.currentLayout.setError(getString(R.string.error_wrong_password));
                } else if (e.is(ErrorCode.VALIDATION_ERROR)) {
                    b.newLayout.setError(e.userMessage(PasswordActivity.this));
                } else if (e.is(ErrorCode.PASSWORD_ALREADY_SET) || e.is(ErrorCode.PASSWORD_NOT_SET)) {
                    refreshMeThenFinish();                  // state changed elsewhere — go back to Settings
                } else {
                    Snackbar.make(b.getRoot(), e.userMessage(PasswordActivity.this), Snackbar.LENGTH_LONG)
                            .setAnchorView(b.bottom).show();
                }
            }
        };
        pending = change ? users.changePassword(current, pw, cb) : users.setPassword(pw, cb);
    }

    private void refreshMeThenFinish() {
        users.me(new ApiCallback<Me>() {
            @Override public void onSuccess(Me m) { done(); }
            @Override public void onError(ApiException e) { done(); }
        });
    }

    private void done() {
        android.widget.Toast.makeText(this, change ? R.string.password_changed : R.string.lock_on_done,
                android.widget.Toast.LENGTH_SHORT).show();
        finish();
    }

    private void setWorking(boolean on) {
        working = on;
        b.save.setText(on ? getString(R.string.password_saving)
                : getString(change ? R.string.password_save_change : R.string.password_save_set));
        b.currentInput.setEnabled(!on);
        b.newInput.setEnabled(!on);
        b.confirmInput.setEnabled(!on);
        update();
    }

    /**
     * Forgot the current password: with fingerprint unlock on, prove it's you first; then email a
     * reset link and sign out of this phone right away.
     */
    private void confirmForgot() {
        Me me = SessionManager.get().cachedMe();
        boolean bio = me != null && me.biometricEnabled && !me.biometricResetPending
                && in.mohammad.ramiz.confess.ui.auth.Biometrics.available(this);
        if (!bio) {
            showForgot();
            return;
        }
        in.mohammad.ramiz.confess.ui.auth.Biometrics.prompt(this, getString(R.string.forgot_verify_title),
                getString(R.string.forgot_verify_sub), new in.mohammad.ramiz.confess.ui.auth.Biometrics.Callback() {
                    @Override public void onSuccess() { showForgot(); }
                    @Override public void onFailure(@androidx.annotation.NonNull CharSequence reason) {
                        in.mohammad.ramiz.confess.core.Haptics.reject(PasswordActivity.this);
                        Snackbar.make(b.getRoot(), R.string.forgot_verify_failed, Snackbar.LENGTH_LONG)
                                .setAnchorView(b.bottom).show();
                    }
                    @Override public void onNegative() { }
                });
    }

    private void showForgot() {
        in.mohammad.ramiz.confess.ui.components.ConfessDialog.with(this)
                .icon(R.drawable.ic_mail_open)
                .title(R.string.forgot_title)
                .body(R.string.forgot_logout_body)
                .danger()
                .primary(R.string.forgot_logout_confirm, d -> {
                    d.busy(true);
                    new AuthRepository().forgotPassword(new ApiCallback<JsonObject>() {
                        @Override public void onSuccess(JsonObject data) {
                            d.dismiss();
                            android.widget.Toast.makeText(PasswordActivity.this, R.string.forgot_signed_out,
                                    android.widget.Toast.LENGTH_LONG).show();
                            Push.signOut(PasswordActivity.this);
                            Router.toWelcome(PasswordActivity.this);
                        }
                        @Override public void onError(ApiException e) {
                            d.error(e.userMessage(PasswordActivity.this));
                        }
                    });
                })
                .show();
    }

    @Override
    protected void onDestroy() {
        if (pending != null && !working) pending.cancel();
        super.onDestroy();
    }
}
