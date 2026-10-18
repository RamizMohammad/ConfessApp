package in.mohammad.ramiz.confess.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.JsonObject;

import in.mohammad.ramiz.confess.BuildConfig;
import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.core.LockPrefs;
import in.mohammad.ramiz.confess.core.push.Push;
import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.drafts.Drafts;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.api.ErrorCode;
import in.mohammad.ramiz.confess.data.model.Me;
import in.mohammad.ramiz.confess.data.repo.ActivityRepository;
import in.mohammad.ramiz.confess.data.repo.UserRepository;
import in.mohammad.ramiz.confess.data.session.SessionManager;
import in.mohammad.ramiz.confess.databinding.ActivitySettingsSecurityBinding;
import in.mohammad.ramiz.confess.databinding.DialogTextInputBinding;
import in.mohammad.ramiz.confess.databinding.ViewSettingRowBinding;
import in.mohammad.ramiz.confess.ui.auth.Biometrics;
import in.mohammad.ramiz.confess.ui.auth.GoogleAuth;
import in.mohammad.ramiz.confess.ui.base.BaseActivity;
import in.mohammad.ramiz.confess.ui.base.Router;
import in.mohammad.ramiz.confess.ui.dev.DesignCheckActivity;
import in.mohammad.ramiz.confess.ui.entry.GuidelinesActivity;
import in.mohammad.ramiz.confess.ui.util.EdgeInsets;

/**
 * Settings → Lock & privacy: password lock on/off (turning it off asks for the password) ·
 * lock-again timer · fingerprint unlock (proved once before it's turned on) · change password ·
 * private screen (no screenshots, hidden in recents).
 * Every switch reflects the server's state (users/me) and rolls back if the call fails.
 */
public class SecurityActivity extends BaseActivity {

    private ActivitySettingsSecurityBinding b;
    private final UserRepository users = new UserRepository();
    @Nullable private Me me;
    private boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivitySettingsSecurityBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeInsets.padTop(b.topBar);
        EdgeInsets.padBottom(b.scroll, false);
        b.back.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        SettingRows.setup(b.lock, R.drawable.ic_lock, R.string.settings_lock);
        SettingRows.setup(b.autolock, R.drawable.ic_refresh, R.string.settings_autolock);
        SettingRows.setup(b.bio, R.drawable.ic_fingerprint, R.string.settings_bio);
        SettingRows.setup(b.changePassword, R.drawable.ic_pencil, R.string.settings_change_password);
        SettingRows.setup(b.privacyScreen, R.drawable.ic_eye_off, R.string.settings_privacy_screen);
        SettingRows.toggle(b.lock);
        SettingRows.toggle(b.bio);
        SettingRows.toggle(b.privacyScreen);
        SettingRows.subtitle(b.privacyScreen, R.string.settings_privacy_screen_sub);
        SettingRows.chevron(b.autolock);
        SettingRows.chevron(b.changePassword);

        b.lock.row.setOnClickListener(v -> onLockTapped());
        b.autolock.row.setOnClickListener(v -> chooseAutolock());
        b.bio.row.setOnClickListener(v -> onBioTapped());
        b.changePassword.row.setOnClickListener(v ->
                startActivity(new Intent(this, PasswordActivity.class).putExtra(PasswordActivity.EXTRA_MODE, PasswordActivity.MODE_CHANGE)));
        b.privacyScreen.row.setOnClickListener(v -> {
            boolean on = !LockPrefs.secureScreen(this);
            LockPrefs.setSecureScreen(this, on);
            b.privacyScreen.toggle.setChecked(on);
            Haptics.tick(this);
            applySecureFlag();
        });

        render(SessionManager.get().cachedMe());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();                              // back from the password screen
    }

    private void refresh() {
        users.me(new ApiCallback<Me>() {
            @Override public void onSuccess(Me m) { render(m); }
            @Override public void onError(ApiException e) { }
        });
    }

    private void render(@Nullable Me m) {
        if (m == null) return;
        me = m;
        boolean lock = m.hasPassword;
        boolean bioHw = Biometrics.available(this);

        b.lock.toggle.setChecked(lock);
        SettingRows.subtitle(b.lock, lock ? R.string.settings_lock_on : R.string.settings_lock_off);

        SettingRows.value(b.autolock, getString(LockPrefs.label(LockPrefs.timeoutMs(this))));
        SettingRows.enabled(b.autolock, lock);

        b.bio.toggle.setChecked(lock && m.biometricEnabled);
        SettingRows.subtitle(b.bio, bioHw ? R.string.settings_bio_sub : R.string.settings_bio_unavailable);
        SettingRows.enabled(b.bio, lock && bioHw);

        SettingRows.enabled(b.changePassword, lock);
        b.privacyScreen.toggle.setChecked(LockPrefs.secureScreen(this));
    }

    // ── Security ─────────────────────────────────────────────────────────────

    private void onLockTapped() {
        if (busy || me == null) return;
        if (!me.hasPassword) {
            startActivity(new Intent(this, PasswordActivity.class)
                    .putExtra(PasswordActivity.EXTRA_MODE, PasswordActivity.MODE_SET));
            return;
        }
        // Turning off needs the current password.
        DialogTextInputBinding d = DialogTextInputBinding.inflate(LayoutInflater.from(this));
        d.input.setHint(R.string.password_current_hint);
        d.input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        d.layout.setEndIconMode(com.google.android.material.textfield.TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.lock_off_title)
                .setMessage(R.string.lock_off_body)
                .setView(d.getRoot())
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.lock_off_confirm, null)
                .show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String pw = d.input.getText() == null ? "" : d.input.getText().toString();
            if (pw.isEmpty()) return;
            v.setEnabled(false);
            users.removePassword(pw, new ApiCallback<JsonObject>() {
                @Override public void onSuccess(JsonObject data) {
                    dialog.dismiss();
                    Haptics.click(SecurityActivity.this);
                    say(R.string.lock_off_done);
                    refresh();
                }
                @Override public void onError(ApiException e) {
                    v.setEnabled(true);
                    Haptics.reject(SecurityActivity.this);
                    d.layout.setError(e.is(ErrorCode.WRONG_PASSWORD) ? getString(R.string.error_wrong_password)
                            : e.userMessage(SecurityActivity.this));
                }
            });
        });
    }

    private void chooseAutolock() {
        if (me == null || !me.hasPassword) return;
        long now = LockPrefs.timeoutMs(this);
        in.mohammad.ramiz.confess.ui.components.ChoiceSheet sheet = in.mohammad.ramiz.confess.ui.components.ChoiceSheet.with(this)
                .title(R.string.settings_autolock)
                .subtitle(R.string.settings_autolock_sub);
        for (int i = 0; i < LockPrefs.OPTIONS_MS.length; i++) {
            long ms = LockPrefs.OPTIONS_MS[i];
            sheet.option(R.drawable.ic_clock, getString(LockPrefs.LABELS[i]), null, ms == now, () -> {
                LockPrefs.setTimeoutMs(this, ms);
                Haptics.tick(this);
                render(me);
            });
        }
        sheet.show();
    }

    private void onBioTapped() {
        if (busy || me == null || !me.hasPassword || !Biometrics.available(this)) return;
        boolean turnOn = !me.biometricEnabled;
        if (turnOn) {
            // Prove the finger works before relying on it.
            Biometrics.prompt(this, getString(R.string.bio_prompt_title), getString(R.string.bio_prompt_subtitle),
                    new Biometrics.Callback() {
                        @Override public void onSuccess() { setBio(true); }
                        @Override public void onFailure(@androidx.annotation.NonNull CharSequence reason) { }
                    });
        } else {
            setBio(false);
        }
    }

    private void setBio(boolean on) {
        busy = true;
        b.bio.toggle.setChecked(on);
        users.setBiometric(on, new ApiCallback<JsonObject>() {
            @Override public void onSuccess(JsonObject data) {
                busy = false;
                say(on ? R.string.bio_on_done : R.string.bio_off_done);
                refresh();
            }
            @Override public void onError(ApiException e) {
                busy = false;
                b.bio.toggle.setChecked(!on);
                sayError(e);
            }
        });
    }

    private void say(@StringRes int res) {
        Snackbar.make(b.getRoot(), res, Snackbar.LENGTH_SHORT).show();
    }

    private void sayError(ApiException e) {
        Snackbar.make(b.getRoot(), e.userMessage(this), Snackbar.LENGTH_LONG).show();
    }
}
