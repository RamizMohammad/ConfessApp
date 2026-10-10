package in.mohammad.ramiz.confess.core.push;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.ActivityEvents;
import in.mohammad.ramiz.confess.core.DeepLink;
import in.mohammad.ramiz.confess.data.session.SessionManager;
import in.mohammad.ramiz.confess.ui.entry.LauncherActivity;

/**
 * Receives the server's data-only FCM messages: {title, body, type, postId?}
 * (type = like | comment | new_post | nudge). Shows a notification on the matching
 * channel; tapping it opens the app at that confession.
 */
public class PushService extends FirebaseMessagingService {

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Push.onNewToken(this, token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        super.onMessageReceived(message);
        if (!SessionManager.get().isSignedIn()) return;          // signed out on this phone

        Map<String, String> data = message.getData();
        String title = data.get("title");
        String body = data.get("body");
        if (title == null && message.getNotification() != null) {
            title = message.getNotification().getTitle();
            body = message.getNotification().getBody();
        }
        if (title == null) title = getString(R.string.push_default_title);
        if (body == null) body = getString(R.string.push_default_body);

        String type = data.get("type");
        String postId = data.get("postId");
        String alias = data.get("alias");
        boolean activity = "like".equals(type) || "comment".equals(type) || "follow".equals(type)
                || "story_reaction".equals(type);
        if (activity) ActivityEvents.ping();                       // bell dot refreshes if the app is open

        String channel = activity ? Push.CHANNEL_ACTIVITY
                : "new_post".equals(type) || "following_post".equals(type) ? Push.CHANNEL_NEW_POSTS
                : Push.CHANNEL_REMINDERS;
        Push.ensureChannels(this);

        Intent open = new Intent(this, LauncherActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        if (postId != null) {
            open.putExtra(DeepLink.EXTRA_POST_ID, postId)
                    .putExtra(DeepLink.EXTRA_TO_RESPONSES, "comment".equals(type));
        }
        if ("follow".equals(type) && alias != null) open.putExtra(DeepLink.EXTRA_ALIAS, alias);
        int id = (type + ":" + (postId != null ? postId : alias != null ? alias : title)).hashCode();
        PendingIntent tap = PendingIntent.getActivity(this, id, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder n = new NotificationCompat.Builder(this, channel)
                .setSmallIcon(R.drawable.ic_stat_confess)
                .setColor(ContextCompat.getColor(this, R.color.amber))
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(tap)
                .setCategory(activity ? NotificationCompat.CATEGORY_SOCIAL : NotificationCompat.CATEGORY_REMINDER)
                .setPriority(activity ? NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_DEFAULT);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        NotificationManagerCompat.from(this).notify(id, n.build());
    }
}
