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
import in.mohammad.ramiz.confess.databinding.ActivitySettingsBinding;
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
 * S12 · Settings. Old: the 8-button grid in ProfileFragment + UpdatePassBio.
 *
 *  Security       password lock on/off · lock-again timer · fingerprint · change password
 *  Notifications  push on/off (server) · Android notification types
 *  Profile        aura & about
 *  Support        request a feature · guidelines
 *  Account        sign out · delete account (type your alias to confirm)
 *
 * Every switch reflects the server's state (users/me) and rolls back if the call fails.
 */
public class SettingsActivity extends BaseActivity {

    private ActivitySettingsBinding b;
    private final UserRepository users = new UserRepository();
    @Nullable private Me me;
    private boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeInsets.padTop(b.topBar);
        EdgeInsets.padBottom(b.scroll, false);
        b.back.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        setup(b.lock, R.drawable.ic_lock, R.string.settings_lock);
        setup(b.autolock, R.drawable.ic_refresh, R.string.settings_autolock);
        setup(b.bio, R.drawable.ic_fingerprint, R.string.settings_bio);
        setup(b.changePassword, R.drawable.ic_pencil, R.string.settings_change_password);
        setup(b.privacyScreen, R.drawable.ic_eye_off, R.string.settings_privacy_screen);
        setup(b.push, R.drawable.ic_bell, R.string.settings_push);
        setup(b.pushSystem, R.drawable.ic_settings, R.string.settings_notif_types);
        setup(b.editProfile, R.drawable.ic_user, R.string.settings_edit_profile);
        setup(b.feature, R.drawable.ic_mail_open, R.string.settings_feature);
        setup(b.guidelines, R.drawable.ic_shield, R.string.settings_guidelines);
        setup(b.signOut, R.drawable.ic_log_out, R.string.settings_sign_out);
        setup(b.design, R.drawable.ic_help, R.string.settings_design);

        toggle(b.lock);
        toggle(b.bio);
        toggle(b.push);
        toggle(b.privacyScreen);
        subtitle(b.privacyScreen, R.string.settings_privacy_screen_sub);
        chevron(b.autolock);
        chevron(b.changePassword);
        chevron(b.pushSystem);
        chevron(b.editProfile);
        chevron(b.feature);
        chevron(b.guidelines);
        subtitle(b.pushSystem, R.string.settings_notif_types_sub);
        subtitle(b.editProfile, R.string.settings_edit_profile_sub);
        subtitle(b.feature, R.string.settings_feature_sub);
        subtitle(b.push, R.string.settings_push_sub);
        b.signOut.title.setTextColor(getColor(R.color.brick));
        b.signOut.icon.setImageTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.brick)));

        b.lock.row.setOnClickListener(v -> onLockTapped());
        b.autolock.row.setOnClickListener(v -> chooseAutolock());
        b.bio.row.setOnClickListener(v -> onBioTapped());
        b.changePassword.row.setOnClickListener(v ->
                startActivity(new Intent(this, PasswordActivity.class).putExtra(PasswordActivity.EXTRA_MODE, PasswordActivity.MODE_CHANGE)));
        b.push.row.setOnClickListener(v -> onPushTapped());
        b.privacyScreen.row.setOnClickListener(v -> {
            boolean on = !LockPrefs.secureScreen(this);
            LockPrefs.setSecureScreen(this, on);
            b.privacyScreen.toggle.setChecked(on);
            Haptics.tick(this);
            applySecureFlag();
        });
        b.pushSystem.row.setOnClickListener(v -> startActivity(new Intent(this, NotificationSettingsActivity.class)));
        b.editProfile.row.setOnClickListener(v -> startActivity(new Intent(this, EditProfileActivity.class)));
        b.feature.row.setOnClickListener(v -> startActivity(new Intent(this, FeatureRequestActivity.class)));
        b.guidelines.row.setOnClickListener(v -> startActivity(new Intent(this, GuidelinesActivity.class)
                .putExtra(GuidelinesActivity.EXTRA_READ_ONLY, true)));
        b.signOut.row.setOnClickListener(v -> confirmSignOut());
        b.deleteAccount.setOnClickListener(v -> confirmDelete());

        b.designGroup.setVisibility(BuildConfig.DEBUG ? View.VISIBLE : View.GONE);
        b.design.row.setOnClickListener(v -> startActivity(new Intent(this, DesignCheckActivity.class)));
        b.version.setText(getString(R.string.settings_version, BuildConfig.VERSION_NAME));

        render(SessionManager.get().cachedMe());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();                              // back from the password screen etc.
    }

    private void refresh() {
        users.me(new ApiCallback<Me>() {
            @Override public void onSuccess(Me m) { render(m); }
            @Override public void onError(ApiException e) { }
        });
    }

    // ── Rows ─────────────────────────────────────────────────────────────────

    private void setup(ViewSettingRowBinding r, @DrawableRes int icon, @StringRes int title) {
        r.icon.setImageResource(icon);
        r.title.setText(title);
    }

    private void toggle(ViewSettingRowBinding r) {
        r.toggle.setVisibility(View.VISIBLE);
    }

    private void chevron(ViewSettingRowBinding r) {
        r.chevron.setVisibility(View.VISIBLE);
    }

    private void subtitle(ViewSettingRowBinding r, @StringRes int text) {
        r.subtitle.setVisibility(View.VISIBLE);
        r.subtitle.setText(text);
    }

    private void enabled(ViewSettingRowBinding r, boolean on) {
        r.row.setEnabled(on);
        r.row.setAlpha(on ? 1f : 0.4f);
    }

    private void render(@Nullable Me m) {
        if (m == null) return;
        me = m;
        boolean lock = m.hasPassword;
        boolean bioHw = Biometrics.available(this);

        b.lock.toggle.setChecked(lock);
        subtitle(b.lock, lock ? R.string.settings_lock_on : R.string.settings_lock_off);

        b.autolock.value.setVisibility(View.VISIBLE);
        b.autolock.value.setText(LockPrefs.label(LockPrefs.timeoutMs(this)));
        enabled(b.autolock, lock);

        b.bio.toggle.setChecked(lock && m.biometricEnabled);
        subtitle(b.bio, bioHw ? R.string.settings_bio_sub : R.string.settings_bio_unavailable);
        enabled(b.bio, lock && bioHw);

        enabled(b.changePassword, lock);
        b.push.toggle.setChecked(m.pushEnabled);
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
                    Haptics.click(SettingsActivity.this);
                    say(R.string.lock_off_done);
                    refresh();
                }
                @Override public void onError(ApiException e) {
                    v.setEnabled(true);
                    Haptics.reject(SettingsActivity.this);
                    d.layout.setError(e.is(ErrorCode.WRONG_PASSWORD) ? getString(R.string.error_wrong_password)
                            : e.userMessage(SettingsActivity.this));
                }
            });
        });
    }

    private void chooseAutolock() {
        if (me == null || !me.hasPassword) return;
        String[] labels = new String[LockPrefs.OPTIONS_MS.length];
        int current = 0;
        long now = LockPrefs.timeoutMs(this);
        for (int i = 0; i < labels.length; i++) {
            labels[i] = getString(LockPrefs.LABELS[i]);
            if (LockPrefs.OPTIONS_MS[i] == now) current = i;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_autolock)
                .setSingleChoiceItems(labels, current, (dialog, which) -> {
                    LockPrefs.setTimeoutMs(this, LockPrefs.OPTIONS_MS[which]);
                    Haptics.tick(this);
                    render(me);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
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

    // ── Notifications ────────────────────────────────────────────────────────

    private void onPushTapped() {
        if (busy || me == null) return;
        boolean on = !me.pushEnabled;
        busy = true;
        b.push.toggle.setChecked(on);
        Haptics.tick(this);
        new ActivityRepository().setPushEnabled(on, new ApiCallback<JsonObject>() {
            @Override public void onSuccess(JsonObject data) {
                busy = false;
                say(on ? R.string.push_on_done : R.string.push_off_done);
                if (on) Push.sync(SettingsActivity.this);
                refresh();
            }
            @Override public void onError(ApiException e) {
                busy = false;
                b.push.toggle.setChecked(!on);
                sayError(e);
            }
        });
    }

    // ── Account ──────────────────────────────────────────────────────────────

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

    /** Old app deleted with one tap. Now: type your alias to confirm. */
    private void confirmDelete() {
        if (me == null) return;
        String alias = me.alias;
        DialogTextInputBinding d = DialogTextInputBinding.inflate(LayoutInflater.from(this));
        d.input.setHint(R.string.delete_account_hint);
        d.input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_account_title)
                .setMessage(getString(R.string.delete_account_body, alias))
                .setView(d.getRoot())
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.delete_account_confirm, null)
                .show();
        View confirm = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        confirm.setEnabled(false);
        d.input.addTextChangedListener(new in.mohammad.ramiz.confess.ui.util.SimpleTextWatcher(t -> {
            String typed = t == null ? "" : t.toString().trim();
            if (typed.startsWith("@")) typed = typed.substring(1);
            confirm.setEnabled(typed.equalsIgnoreCase(alias));
        }));
        confirm.setOnClickListener(v -> {
            v.setEnabled(false);
            dialog.setCancelable(false);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(R.string.delete_account_working);
            String userId = me.id;
            users.deleteAccount(new ApiCallback<JsonObject>() {
                @Override public void onSuccess(JsonObject data) {
                    dialog.dismiss();
                    Drafts.get(SettingsActivity.this).clearAll(userId);
                    GoogleAuth.signOut(SettingsActivity.this);
                    android.widget.Toast.makeText(SettingsActivity.this, R.string.delete_account_done,
                            android.widget.Toast.LENGTH_LONG).show();
                    Router.toWelcome(SettingsActivity.this);
                }
                @Override public void onError(ApiException e) {
                    dialog.setCancelable(true);
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(R.string.delete_account_confirm);
                    v.setEnabled(true);
                    d.layout.setError(e.userMessage(SettingsActivity.this));
                }
            });
        });
    }

    // ── Messages ─────────────────────────────────────────────────────────────

    private void say(@StringRes int res) {
        Snackbar.make(b.getRoot(), res, Snackbar.LENGTH_SHORT).show();
    }

    private void sayError(ApiException e) {
        Snackbar.make(b.getRoot(), e.userMessage(this), Snackbar.LENGTH_LONG).show();
    }
}
