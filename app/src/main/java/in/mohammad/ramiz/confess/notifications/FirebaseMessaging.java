package in.mohammad.ramiz.confess.notifications;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.notificationdatabase.NotificationEntity;
import in.mohammad.ramiz.confess.notificationdatabase.NotificationRepo;
import in.mohammad.ramiz.confess.notificationdatabase.NotificationViewmodel;
import in.mohammad.ramiz.confess.security.BiometricPrefs;

public class FirebaseMessaging extends FirebaseMessagingService {

    private static final String TAG = "FCM-Service";
    private static final String CHANNEL_ID = "confess_channel";
    private NotificationRepo repo;

    @Override
    public void onCreate() {
        super.onCreate();
        repo = new NotificationRepo(getApplication());
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        BiometricPrefs.getInstance(this).setFcmKey(true);
        BiometricPrefs.getInstance(this).setFcmValue(token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        super.onMessageReceived(message);

        // Prefer data payload to ensure consistency
        String title = "Confess";
        String body = "You have a new notification.";

        if (message.getData().size() > 0) {
            // Read from data payload
            if (message.getData().get("title") != null) {
                title = message.getData().get("title");
            }
            if (message.getData().get("body") != null) {
                body = message.getData().get("body");
            }
        } else if (message.getNotification() != null) {
            // Fallback to notification payload
            title = message.getNotification().getTitle();
            body = message.getNotification().getBody();
        }

        sendNotificationToDb(title, body);
        sendNotification(title, body);
    }

    private void sendNotification(String title, String body) {
        createNotificationChannel();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.notification_icon) // Must be white vector
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManagerCompat managerCompat = NotificationManagerCompat.from(this);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        managerCompat.notify(1001, builder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Confess Notifications",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Confess App notifications");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void sendNotificationToDb(String title, String body){
        NotificationEntity notification = new NotificationEntity(title, body);
        repo.insertNotification(notification);
    }
}
