package in.mohammad.ramiz.confess.core.push;

import android.app.Activity;
import android.app.NotificationChannel;
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
        String pair = (me != null ? me.id : "session") + ":" + token.hashCode();
        if (!force && pair.equals(p.getString(KEY_REGISTERED_FOR, null))) return;

        new ActivityRepository().registerPushToken(token, new ApiCallback<JsonObject>() {
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

    /** Android notification channels — the user can mute each kind separately in system settings. */
    public static void ensureChannels(@NonNull Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;
        nm.createNotificationChannel(channel(context, CHANNEL_ACTIVITY, R.string.channel_activity,
                R.string.channel_activity_desc, NotificationManager.IMPORTANCE_HIGH));
        nm.createNotificationChannel(channel(context, CHANNEL_NEW_POSTS, R.string.channel_new_posts,
                R.string.channel_new_posts_desc, NotificationManager.IMPORTANCE_DEFAULT));
        nm.createNotificationChannel(channel(context, CHANNEL_REMINDERS, R.string.channel_reminders,
                R.string.channel_reminders_desc, NotificationManager.IMPORTANCE_LOW));
        nm.deleteNotificationChannel("confess_channel");     // the old app's single channel
    }

    private static NotificationChannel channel(Context c, String id, int name, int desc, int importance) {
        NotificationChannel ch = new NotificationChannel(id, c.getString(name), importance);
        ch.setDescription(c.getString(desc));
        return ch;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
