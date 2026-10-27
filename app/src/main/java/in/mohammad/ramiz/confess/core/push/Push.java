package in.mohammad.ramiz.confess.core.push;

import android.app.Activity;
import android.app.NotificationChannel;
import android.media.AudioAttributes;
import android.net.Uri;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.gson.JsonObject;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.model.Me;
import in.mohammad.ramiz.confess.data.repo.ActivityRepository;
import in.mohammad.ramiz.confess.data.repo.AuthRepository;
import in.mohammad.ramiz.confess.data.session.SessionManager;
import in.mohammad.ramiz.confess.ui.auth.GoogleAuth;

/**
 * Push plumbing (Firebase is used ONLY for this). Old: FirebaseMessaging stored the
 * token in BiometricPrefs and sent it with login.
 *
 *  - {@link #sync} after reaching home: fetch the FCM token and register it with the
 *    server, once per (token, account) pair
 *  - {@link #onNewToken} from the service: re-register when Firebase rotates it
 *  - {@link #signOut}: unregister this phone, forget the session, sign out of Google
 */
public final class Push {

    private static final String TAG = "ConfessPush";
    private static final String PREFS = "push";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_REGISTERED_FOR = "registered_for";

    public static final String CHANNEL_ACTIVITY = "activity";
    public static final String CHANNEL_NEW_POSTS = "new_posts";
    public static final String CHANNEL_REMINDERS = "reminders";
    /** Letty's pushes (nudges, teasers, digests, win-backs) — high importance so they pop up. A new
     *  id because a channel's importance can't be raised once it exists. */
    public static final String CHANNEL_LETTY = "letty";

    private Push() { }

    /** Make sure this phone's token is registered for the signed-in account. */
    public static void sync(@NonNull Context context) {
        Context app = context.getApplicationContext();
        ensureChannels(app);
        if (!SessionManager.get().isSignedIn()) return;
        try {
            FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
                if (!task.isSuccessful() || task.getResult() == null) {
                    Log.w(TAG, "FCM token unavailable", task.getException());
                    return;
                }
                register(app, task.getResult(), false);
            });
        } catch (Exception e) {
            Log.w(TAG, "Firebase not available", e);
        }
    }

    /** Firebase handed us a new token (service callback). */
    public static void onNewToken(@NonNull Context context, @NonNull String token) {
        register(context.getApplicationContext(), token, true);
    }

    private static void register(Context app, String token, boolean force) {
        SharedPreferences p = prefs(app);
        p.edit().putString(KEY_TOKEN, token).apply();
        if (!SessionManager.get().isSignedIn()) return;
        // The server ties the token to whoever's session sends it, so we don't need the profile
        // loaded yet (waiting for it used to skip registration on a fresh sign-in → no pushes ever).
        Me me = SessionManager.get().cachedMe();
        String tag = NotificationSound.tag(app);
        // A new sound = new channel ids: the server must hear the tag, so it's part of the pair.
        // Same for the template version: an app update that draws new templates re-registers.
        String pair = (me != null ? me.id : "session") + ":" + token.hashCode() + tag + ":t" + PushTemplates.VERSION;
        if (!force && pair.equals(p.getString(KEY_REGISTERED_FOR, null))) return;

        new ActivityRepository().registerPushToken(token, tag, new ApiCallback<JsonObject>() {
            @Override public void onSuccess(JsonObject data) {
                p.edit().putString(KEY_REGISTERED_FOR, pair).apply();
            }
            @Override public void onError(ApiException e) {
                Log.w(TAG, "register token failed: " + e.code);
            }
        });
    }

    @Nullable
    public static String cachedToken(@NonNull Context context) {
        return prefs(context).getString(KEY_TOKEN, null);
    }

    /** Full sign-out on this phone. The caller routes to Welcome afterwards. */
    public static void signOut(@NonNull Activity activity) {
        String token = cachedToken(activity);
        prefs(activity).edit().remove(KEY_REGISTERED_FOR).apply();
        new AuthRepository().signOut(token);          // unregisters with the old session, then clears it
        GoogleAuth.signOut(activity);
    }

    /** The id of one kind's channel on this phone (base + the sound's version, e.g. "activity.v3"). */
    @NonNull
    public static String channelId(@NonNull Context context, @NonNull String base) {
        return base + NotificationSound.tag(context);
    }

    /**
     * Android notification channels — the user can mute each kind separately in system settings.
     * All three ring (with the reader's sound, {@link NotificationSound}); activity also pops up.
     * Channels of an earlier sound (or the first, sometimes silent ones) are removed.
     */
    public static void ensureChannels(@NonNull Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;
        Uri sound = NotificationSound.uri(context);
        java.util.Set<String> current = new java.util.HashSet<>();
        current.add(create(nm, context, CHANNEL_ACTIVITY, R.string.channel_activity, R.string.channel_activity_desc,
                NotificationManager.IMPORTANCE_HIGH, sound));
        current.add(create(nm, context, CHANNEL_NEW_POSTS, R.string.channel_new_posts, R.string.channel_new_posts_desc,
                NotificationManager.IMPORTANCE_DEFAULT, sound));
        current.add(create(nm, context, CHANNEL_REMINDERS, R.string.channel_reminders, R.string.channel_reminders_desc,
                NotificationManager.IMPORTANCE_DEFAULT, sound));
        current.add(create(nm, context, CHANNEL_LETTY, R.string.channel_letty, R.string.channel_letty_desc,
                NotificationManager.IMPORTANCE_HIGH, sound));
        for (NotificationChannel ch : nm.getNotificationChannels()) {
            String id = ch.getId();
            if (!current.contains(id) && ours(id)) nm.deleteNotificationChannel(id);
        }
        nm.deleteNotificationChannel("confess_channel");     // the old app's single channel
    }

    private static boolean ours(@NonNull String id) {
        for (String base : new String[]{CHANNEL_ACTIVITY, CHANNEL_NEW_POSTS, CHANNEL_REMINDERS, CHANNEL_LETTY}) {
            if (id.equals(base) || id.startsWith(base + ".v")) return true;
        }
        return false;
    }

    @NonNull
    private static String create(NotificationManager nm, Context c, String base, int name, int desc, int importance,
                                 @Nullable Uri sound) {
        String id = channelId(c, base);
        NotificationChannel ch = new NotificationChannel(id, c.getString(name), importance);
        ch.setDescription(c.getString(desc));
        ch.setSound(sound, new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build());
        ch.enableVibration(sound != null);
        ch.enableLights(true);
        ch.setLightColor(0xFFE8A04C);
        ch.setShowBadge(true);
        nm.createNotificationChannel(ch);
        return id;
    }

    /**
     * Notifications from Confess would arrive silently on this phone: notifications off, or the
     * activity channel turned down / its sound switched off (MIUI does this to new apps).
     */
    public static boolean soundOff(@NonNull Context context) {
        if (!androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) return true;
        if (NotificationSound.kind(context) == NotificationSound.SILENT) return false;   // chosen
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return false;
        NotificationChannel ch = nm.getNotificationChannel(channelId(context, CHANNEL_ACTIVITY));
        return ch != null && (ch.getImportance() < NotificationManager.IMPORTANCE_DEFAULT || ch.getSound() == null);
    }

    /** Where to switch it back on: the activity channel's own page, or the app's when notifications are off. */
    @NonNull
    public static android.content.Intent soundSettings(@NonNull Context context) {
        boolean appOff = !androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled();
        android.content.Intent i = new android.content.Intent(appOff
                ? android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                : android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.getPackageName());
        if (!appOff) i.putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, channelId(context, CHANNEL_ACTIVITY));
        return i;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
