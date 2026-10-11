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
        pref(b.nudges, R.drawable.ic_moon, R.string.nprefs_nudges, R.string.nprefs_nudges_sub,
                new Field() {
                    public Boolean get(NotificationPrefs p) { return p.nudges; }
                    public void set(NotificationPrefs p, Boolean v) { p.nudges = v; }
                });

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
