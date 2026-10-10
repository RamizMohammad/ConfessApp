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
 * Builds the notification while the app is open (when it's closed Android shows the server's
 * notification block itself). {title, body, type, postId?, alias?}; type = like · milestone ·
 * comment · follow · story_reaction · poll_result (activity) · following_post · new_post
 * (new confessions) · nudge · digest (reminders). Tapping opens the confession / profile /
 * activity screen. Activity on my own words is hidden on a secure lock screen.
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
                || "story_reaction".equals(type) || "poll_result".equals(type) || "milestone".equals(type);
        boolean digest = "digest".equals(type);
        if (activity || digest) ActivityEvents.ping();             // bell dot refreshes if the app is open
        // About my own words → hidden on a secure lock screen (same as the server's PRIVATE visibility).
        boolean personal = activity && !"follow".equals(type);

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
        if (digest) open.putExtra(DeepLink.EXTRA_ACTIVITY, true);
        // Same id as the server's tag: a milestone replaces that letter's reaction notification.
        String tagType = "milestone".equals(type) ? "like" : type;
        int id = (tagType + ":" + (postId != null ? postId : alias != null ? alias : title)).hashCode();
        PendingIntent tap = PendingIntent.getActivity(this, id, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder n = new NotificationCompat.Builder(this, channel)
                .setSmallIcon(R.drawable.ic_stat_confess)
                .setColor(ContextCompat.getColor(this, R.color.amber))
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setShowWhen(true)
                .setContentIntent(tap)
                .setGroup(channel)                                   // activity · new posts · reminders stack separately
                .setCategory(activity ? NotificationCompat.CATEGORY_SOCIAL : NotificationCompat.CATEGORY_REMINDER)
                .setPriority(activity ? NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_DEFAULT);
        if (personal) {
            n.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setPublicVersion(new NotificationCompat.Builder(this, channel)
                            .setSmallIcon(R.drawable.ic_stat_confess)
                            .setColor(ContextCompat.getColor(this, R.color.amber))
                            .setContentTitle(getString(R.string.push_default_title))
                            .setContentText(getString(R.string.push_private_body))
                            .build());
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        NotificationManagerCompat nm = NotificationManagerCompat.from(this);
        nm.notify(id, n.build());
        // The group needs a summary so several stack into one expandable entry.
        nm.notify(channel.hashCode(), new NotificationCompat.Builder(this, channel)
                .setSmallIcon(R.drawable.ic_stat_confess)
                .setColor(ContextCompat.getColor(this, R.color.amber))
                .setStyle(new NotificationCompat.InboxStyle().setSummaryText(getString(R.string.push_default_title)))
                .setGroup(channel)
                .setGroupSummary(true)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                .setAutoCancel(true)
                .setVisibility(personal ? NotificationCompat.VISIBILITY_PRIVATE : NotificationCompat.VISIBILITY_PUBLIC)
                .build());
    }
}
