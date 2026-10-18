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
 *  (card)          my aura + alias → edit profile
 *  Appearance      → paper / text tone, bursts                     (AppearanceActivity)
 *  Notifications   push on/off (server) · which kinds
 *  Lock & privacy  → password, auto-lock, fingerprint, private screen (SecurityActivity)
 *  People          blocked people
 *  Support         request a feature · guidelines · privacy policy
 *  Account         sign out · delete account (type your alias to confirm)
 *
 * Rows that open a screen show where things stand ("On · fingerprint", "Dimmed · Slider").
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

        SettingRows.setup(b.appearance, R.drawable.ic_eye, R.string.settings_appearance_row);
        SettingRows.setup(b.push, R.drawable.ic_bell, R.string.settings_push);
        SettingRows.setup(b.pushSystem, R.drawable.ic_settings, R.string.settings_notif_types);
        SettingRows.setup(b.security, R.drawable.ic_lock, R.string.settings_security_row);
        SettingRows.setup(b.blocked, R.drawable.ic_ban, R.string.blocked_title);
        SettingRows.setup(b.feature, R.drawable.ic_mail_open, R.string.settings_feature);
        SettingRows.setup(b.guidelines, R.drawable.ic_shield, R.string.settings_guidelines);
        SettingRows.setup(b.privacy, R.drawable.ic_lock, R.string.settings_privacy_policy);
        SettingRows.setup(b.signOut, R.drawable.ic_log_out, R.string.settings_sign_out);
        SettingRows.setup(b.deleteAccount, R.drawable.ic_trash, R.string.settings_delete_title);

        SettingRows.toggle(b.push);
        for (in.mohammad.ramiz.confess.databinding.ViewSettingRowBinding r : new in.mohammad.ramiz.confess.databinding.ViewSettingRowBinding[]{
                b.appearance, b.pushSystem, b.security, b.blocked, b.feature, b.guidelines, b.privacy}) {
            SettingRows.chevron(r);
        }
        SettingRows.subtitle(b.appearance, R.string.settings_appearance_sub);
        SettingRows.subtitle(b.push, R.string.settings_push_sub);
        SettingRows.subtitle(b.pushSystem, R.string.settings_notif_types_sub);
        SettingRows.subtitle(b.security, R.string.settings_security_sub);
        SettingRows.subtitle(b.feature, R.string.settings_feature_sub);
        SettingRows.subtitle(b.deleteAccount, R.string.settings_delete_row_sub);
        SettingRows.danger(b.signOut, getColor(R.color.brick));
        SettingRows.danger(b.deleteAccount, getColor(R.color.brick));

        b.profileCard.setOnClickListener(v -> startActivity(new Intent(this, EditProfileActivity.class)));
        b.appearance.row.setOnClickListener(v -> startActivity(new Intent(this, AppearanceActivity.class)));
        b.security.row.setOnClickListener(v -> startActivity(new Intent(this, SecurityActivity.class)));
        b.push.row.setOnClickListener(v -> onPushTapped());
        b.pushSystem.row.setOnClickListener(v -> startActivity(new Intent(this, NotificationSettingsActivity.class)));
        b.blocked.row.setOnClickListener(v -> startActivity(
                new Intent(this, in.mohammad.ramiz.confess.ui.people.BlockedActivity.class)));
        b.feature.row.setOnClickListener(v -> startActivity(new Intent(this, FeatureRequestActivity.class)));
        b.guidelines.row.setOnClickListener(v -> startActivity(new Intent(this, GuidelinesActivity.class)
                .putExtra(GuidelinesActivity.EXTRA_READ_ONLY, true)));
        b.privacy.row.setOnClickListener(v -> openWeb("privacy"));
        b.signOut.row.setOnClickListener(v -> confirmSignOut());
        b.deleteAccount.row.setOnClickListener(v -> confirmDelete());

        b.version.setText(getString(R.string.settings_version, BuildConfig.VERSION_NAME));
        render(SessionManager.get().cachedMe());
    }

    @Override
    protected void onResume() {
        super.onResume();
        render(me != null ? me : SessionManager.get().cachedMe());   // back from a sub-screen: new summaries
        refresh();
    }

    private void refresh() {
        users.me(new ApiCallback<Me>() {
            @Override public void onSuccess(Me m) { render(m); }
            @Override public void onError(ApiException e) { }
        });
    }

    private void render(@Nullable Me m) {
        // Appearance: "Bright · Slider" / "Dimmed · Stack"
        int burst = in.mohammad.ramiz.confess.ui.feed.Bursts.mode(this);
        String paper = getString(in.mohammad.ramiz.confess.ui.components.PaperTone.get(this) > 0
                ? R.string.settings_appearance_dimmed : R.string.settings_appearance_bright);
        String bursts = getString(burst == in.mohammad.ramiz.confess.ui.feed.Bursts.STACK ? R.string.burst_mode_stack
                : burst == in.mohammad.ramiz.confess.ui.feed.Bursts.SEPARATE ? R.string.burst_mode_separate
                : R.string.burst_mode_slider);
        SettingRows.value(b.appearance, paper + " · " + bursts);
        if (m == null) return;
        me = m;
        b.avatar.setAvatar(m.avatar);
        b.alias.setText("@" + m.alias);
        b.push.toggle.setChecked(m.pushEnabled);
        // Lock & privacy: "Off" / "On" / "On · fingerprint"
        SettingRows.value(b.security, !m.hasPassword ? getString(R.string.settings_security_off)
                : m.biometricEnabled ? getString(R.string.settings_security_on_bio)
                : getString(R.string.settings_security_on));
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
                .setMessage(getString(R.string.settings_delete_body) + "\n\n" + getString(R.string.delete_account_body, alias))
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

    /** The Confess website — privacy policy, guidelines, child safety, delete account. */
    public static final String WEBSITE_URL = "https://confess.mohammadramiz.in/";

    /** A page on the Confess website in the browser. */
    private void openWeb(@androidx.annotation.NonNull String path) {
        String base = WEBSITE_URL;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(base + path)));
        } catch (android.content.ActivityNotFoundException e) {
            android.widget.Toast.makeText(this, base + path, android.widget.Toast.LENGTH_LONG).show();
        }
    }
}
