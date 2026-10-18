package in.mohammad.ramiz.confess.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.material.snackbar.Snackbar;
import com.google.gson.JsonObject;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.core.push.Push;
import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.model.Me;
import in.mohammad.ramiz.confess.data.model.NotificationPrefs;
import in.mohammad.ramiz.confess.data.repo.ActivityRepository;
import in.mohammad.ramiz.confess.data.repo.UserRepository;
import in.mohammad.ramiz.confess.data.session.SessionManager;
import in.mohammad.ramiz.confess.databinding.ActivityNotificationSettingsBinding;
import in.mohammad.ramiz.confess.databinding.ViewSettingRowBinding;
import in.mohammad.ramiz.confess.ui.base.BaseActivity;
import in.mohammad.ramiz.confess.ui.util.EdgeInsets;

/**
 * Which pushes Confess sends this account — saved on the server, so they apply on
 * every phone. "Push notifications" is the master switch; the rest are per type.
 * The in-app Activity list keeps everything either way.
 *
 * Sound (this phone): the phone's default, one of its sounds, your own file, or silent
 * ({@link in.mohammad.ramiz.confess.core.push.NotificationSound}); "Send a test notification"
 * rings it for real. If the phone has Confess's sound switched off, a banner says so and opens
 * the right system page.
 */
public class NotificationSettingsActivity extends BaseActivity {

    private ActivityNotificationSettingsBinding b;
    private final UserRepository users = new UserRepository();
    @Nullable private Me me;
    private boolean busy = false;

    /** One row per switch: binding, the API field, icon, title, subtitle. */
    private interface Field {
        @Nullable Boolean get(NotificationPrefs p);

        void set(NotificationPrefs p, Boolean v);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityNotificationSettingsBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeInsets.padTop(b.topBar);
        EdgeInsets.padBottom(b.scroll, false);
        b.back.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        row(b.master, R.drawable.ic_bell, R.string.nprefs_master, R.string.nprefs_master_sub, true);
        b.master.row.setOnClickListener(v -> toggleMaster());

        pref(b.likes, R.drawable.ic_heart, R.string.nprefs_likes, R.string.nprefs_likes_sub,
                new Field() {
                    public Boolean get(NotificationPrefs p) { return p.likes; }
                    public void set(NotificationPrefs p, Boolean v) { p.likes = v; }
                });
        pref(b.comments, R.drawable.ic_message, R.string.nprefs_comments, R.string.nprefs_comments_sub,
                new Field() {
                    public Boolean get(NotificationPrefs p) { return p.comments; }
                    public void set(NotificationPrefs p, Boolean v) { p.comments = v; }
                });
        pref(b.follows, R.drawable.ic_user, R.string.nprefs_follows, R.string.nprefs_follows_sub,
                new Field() {
                    public Boolean get(NotificationPrefs p) { return p.follows; }
                    public void set(NotificationPrefs p, Boolean v) { p.follows = v; }
                });
        pref(b.followingPosts, R.drawable.ic_letters, R.string.nprefs_following_posts, R.string.nprefs_following_posts_sub,
                new Field() {
                    public Boolean get(NotificationPrefs p) { return p.followingPosts; }
                    public void set(NotificationPrefs p, Boolean v) { p.followingPosts = v; }
                });
        pref(b.newPosts, R.drawable.ic_mail_open, R.string.nprefs_new_posts, R.string.nprefs_new_posts_sub,
                new Field() {
                    public Boolean get(NotificationPrefs p) { return p.newPosts; }
                    public void set(NotificationPrefs p, Boolean v) { p.newPosts = v; }
                });
        pref(b.bookChapters, R.drawable.ic_book, R.string.nprefs_book_chapters, R.string.nprefs_book_chapters_sub,
                new Field() {
                    public Boolean get(NotificationPrefs p) { return p.bookChapters; }
                    public void set(NotificationPrefs p, Boolean v) { p.bookChapters = v; }
                });
        pref(b.nudges, R.drawable.ic_moon, R.string.nprefs_nudges, R.string.nprefs_nudges_sub,
                new Field() {
                    public Boolean get(NotificationPrefs p) { return p.nudges; }
                    public void set(NotificationPrefs p, Boolean v) { p.nudges = v; }
                });

        setUpSound();

        row(b.system, R.drawable.ic_settings, R.string.nprefs_system, R.string.nprefs_system_sub, false);
        b.system.chevron.setVisibility(View.VISIBLE);
        b.system.row.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())));

        render(SessionManager.get().cachedMe());
        users.me(new ApiCallback<Me>() {
            @Override public void onSuccess(Me m) { render(m); }
            @Override public void onError(ApiException e) { }
        });
    }

    // ── Sound ────────────────────────────────────────────────────────────────

    private final androidx.activity.result.ActivityResultLauncher<Intent> pickPhoneSound =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(), r -> {
                if (r.getResultCode() != RESULT_OK || r.getData() == null) return;
                @SuppressWarnings("deprecation")             // the typed overload is Android 13+ only
                android.net.Uri uri = r.getData().getParcelableExtra(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                if (uri == null) {
                    useSound(in.mohammad.ramiz.confess.core.push.NotificationSound.SILENT, null, null);
                } else if (uri.equals(Settings.System.DEFAULT_NOTIFICATION_URI)) {
                    useSound(in.mohammad.ramiz.confess.core.push.NotificationSound.DEFAULT, null, null);
                } else {
                    android.media.Ringtone tone = android.media.RingtoneManager.getRingtone(this, uri);
                    useSound(in.mohammad.ramiz.confess.core.push.NotificationSound.PHONE, uri,
                            tone != null ? tone.getTitle(this) : null);
                }
            });

    private final androidx.activity.result.ActivityResultLauncher<String[]> pickFile =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.OpenDocument(), picked -> {
                if (picked == null) return;
                b.sound.value.setText(R.string.nsound_importing);
                java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
                    android.net.Uri copy = in.mohammad.ramiz.confess.core.push.NotificationSound.importFile(this, picked);
                    String name = in.mohammad.ramiz.confess.core.push.NotificationSound.displayName(this, picked);
                    runOnUiThread(() -> {
                        if (isFinishing()) return;
                        if (copy == null) {
                            renderSound();
                            Snackbar.make(b.getRoot(), R.string.nsound_import_failed, Snackbar.LENGTH_LONG).show();
                        } else {
                            useSound(in.mohammad.ramiz.confess.core.push.NotificationSound.FILE, copy, name);
                        }
                    });
                });
            });

    private void setUpSound() {
        row(b.sound, R.drawable.ic_bell, R.string.nsound_title, R.string.nsound_title_sub, false);
        b.sound.chevron.setVisibility(View.VISIBLE);
        b.sound.value.setVisibility(View.VISIBLE);
        b.sound.row.setOnClickListener(v -> chooseSound());
        row(b.soundTest, R.drawable.ic_mail_open, R.string.nsound_test, R.string.nsound_test_sub, false);
        b.soundTest.row.setOnClickListener(v -> sendTest());
        b.soundFix.setOnClickListener(v -> {
            try {
                startActivity(Push.soundSettings(this));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
            }
        });
        renderSound();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (b != null) renderSound();                // back from system settings
    }

    private void renderSound() {
        b.sound.value.setText(in.mohammad.ramiz.confess.core.push.NotificationSound.label(this));
        b.soundWarning.setVisibility(Push.soundOff(this) ? View.VISIBLE : View.GONE);
    }

    private void chooseSound() {
        int kind = in.mohammad.ramiz.confess.core.push.NotificationSound.kind(this);
        in.mohammad.ramiz.confess.ui.components.ChoiceSheet.with(this)
                .title(R.string.nsound_title)
                .subtitle(R.string.nsound_title_sub)
                .option(R.drawable.ic_bell, R.string.nsound_default, R.string.nsound_default_sub,
                        kind == in.mohammad.ramiz.confess.core.push.NotificationSound.DEFAULT, () -> useSound(in.mohammad.ramiz.confess.core.push.NotificationSound.DEFAULT, null, null))
                .option(R.drawable.ic_settings, R.string.nsound_pick_phone, R.string.nsound_pick_phone_sub,
                        kind == in.mohammad.ramiz.confess.core.push.NotificationSound.PHONE, this::pickPhone)
                .option(R.drawable.ic_heart, R.string.nsound_pick_file, R.string.nsound_pick_file_sub,
                        kind == in.mohammad.ramiz.confess.core.push.NotificationSound.FILE, () -> pickFile.launch(new String[]{"audio/*"}))
                .option(R.drawable.ic_moon, R.string.nsound_silent, R.string.nsound_silent_sub,
                        kind == in.mohammad.ramiz.confess.core.push.NotificationSound.SILENT, () -> useSound(in.mohammad.ramiz.confess.core.push.NotificationSound.SILENT, null, null))
                .show();
    }

    /** Android's own sound list (notification sounds), opened at the current one. */
    private void pickPhone() {
        android.net.Uri now = in.mohammad.ramiz.confess.core.push.NotificationSound.uri(this);
        pickPhoneSound.launch(new Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE, android.media.RingtoneManager.TYPE_NOTIFICATION)
                .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.nsound_title))
                .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_NOTIFICATION_URI)
                .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, now));
    }

    /** Save it (new channels, server told), show it, and let them hear it. */
    private void useSound(int kind, @Nullable android.net.Uri uri, @Nullable String label) {
        in.mohammad.ramiz.confess.core.push.NotificationSound.choose(this, kind, uri, label);
        Haptics.tick(this);
        renderSound();
        android.net.Uri play = in.mohammad.ramiz.confess.core.push.NotificationSound.uri(this);
        if (play != null) {
            try {
                android.media.Ringtone tone = android.media.RingtoneManager.getRingtone(this, play);
                if (tone != null) tone.play();
            } catch (Exception ignored) { }
        }
        Snackbar.make(b.getRoot(), getString(R.string.nsound_saved,
                in.mohammad.ramiz.confess.core.push.NotificationSound.label(this)), Snackbar.LENGTH_SHORT).show();
    }

    /** A real notification on the activity channel — sound, vibration, pop-up, exactly as pushes come. */
    private void sendTest() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED || Push.soundOff(this)) {
            renderSound();
            Snackbar.make(b.getRoot(), R.string.nsound_off_warning, Snackbar.LENGTH_LONG)
                    .setAction(R.string.nsound_fix, v -> startActivity(Push.soundSettings(this))).show();
            return;
        }
        Push.ensureChannels(this);
        String channel = Push.channelId(this, Push.CHANNEL_ACTIVITY);
        androidx.core.app.NotificationManagerCompat.from(this).notify(0x5EED,
                new androidx.core.app.NotificationCompat.Builder(this, channel)
                        .setSmallIcon(R.drawable.ic_stat_confess)
                        .setColor(androidx.core.content.ContextCompat.getColor(this, R.color.amber))
                        .setContentTitle(getString(R.string.nsound_test_title))
                        .setContentText(getString(R.string.nsound_test_body))
                        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .build());
    }

    private final java.util.Map<ViewSettingRowBinding, Field> fields = new java.util.LinkedHashMap<>();

    private void row(ViewSettingRowBinding r, @DrawableRes int icon, @StringRes int title, @StringRes int sub,
                     boolean toggle) {
        r.icon.setImageResource(icon);
        r.title.setText(title);
        r.subtitle.setVisibility(View.VISIBLE);
        r.subtitle.setText(sub);
        if (toggle) r.toggle.setVisibility(View.VISIBLE);
    }

    private void pref(ViewSettingRowBinding r, @DrawableRes int icon, @StringRes int title, @StringRes int sub,
                      Field f) {
        row(r, icon, title, sub, true);
        fields.put(r, f);
        r.row.setOnClickListener(v -> togglePref(r, f));
    }

    private void render(@Nullable Me m) {
        if (m == null || b == null) return;
        me = m;
        NotificationPrefs p = m.notifications != null ? m.notifications : new NotificationPrefs();
        b.master.toggle.setChecked(m.pushEnabled);
        for (java.util.Map.Entry<ViewSettingRowBinding, Field> e : fields.entrySet()) {
            e.getKey().toggle.setChecked(NotificationPrefs.on(e.getValue().get(p)));
            enabled(e.getKey(), m.pushEnabled);
        }
    }

    private void enabled(ViewSettingRowBinding r, boolean on) {
        r.row.setEnabled(on);
        r.row.setAlpha(on ? 1f : 0.4f);
    }

    private void toggleMaster() {
        if (busy || me == null) return;
        busy = true;
        boolean on = !me.pushEnabled;
        Haptics.tick(this);
        b.master.toggle.setChecked(on);
        new ActivityRepository().setPushEnabled(on, new ApiCallback<JsonObject>() {
            @Override public void onSuccess(JsonObject data) {
                busy = false;
                me.pushEnabled = on;
                SessionManager.get().cacheMe(me);
                render(me);
                if (on) Push.sync(NotificationSettingsActivity.this);
            }
            @Override public void onError(ApiException e) {
                busy = false;
                b.master.toggle.setChecked(!on);
                say(e);
            }
        });
    }

    private void togglePref(ViewSettingRowBinding r, Field f) {
        if (busy || me == null || !me.pushEnabled) return;
        busy = true;
        NotificationPrefs current = me.notifications != null ? me.notifications : new NotificationPrefs();
        boolean on = !NotificationPrefs.on(f.get(current));
        Haptics.tick(this);
        r.toggle.setChecked(on);
        NotificationPrefs change = new NotificationPrefs();
        f.set(change, on);
        users.setNotificationPrefs(change, new ApiCallback<NotificationPrefs>() {
            @Override public void onSuccess(NotificationPrefs saved) {
                busy = false;
                me.notifications = saved;
                SessionManager.get().cacheMe(me);
                render(me);
            }
            @Override public void onError(ApiException e) {
                busy = false;
                r.toggle.setChecked(!on);
                say(e);
            }
        });
    }

    private void say(@NonNull ApiException e) {
        Snackbar.make(b.root, e.userMessage(this), Snackbar.LENGTH_SHORT).show();
    }
}
