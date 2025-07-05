package in.mohammad.ramiz.confess.notifications;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import in.mohammad.ramiz.confess.R;

public class FirebaseMessaging extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        super.onMessageReceived(message);

        if(message.getNotification() != null){
            String title = message.getNotification().getTitle();
            String body = message.getNotification().getBody();
        }
    }

    private void sendNotification(String title, String body){
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "confess_chanel")
                .setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setAutoCancel(true)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManagerCompat managerCompat = NotificationManagerCompat.from(this);
    }
}
