package in.mohammad.ramiz.confess.notificationdatabase;

import android.app.Application;

import androidx.lifecycle.LiveData;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NotificationRepo {

    private final NotificationDao notificationDao;
    private final ExecutorService service;

    public NotificationRepo(Application application) {
        NotificationDatabase db = NotificationDatabase.getInstance(application);
        notificationDao = db.notificationDao();
        service = Executors.newSingleThreadExecutor();
    }

    public void insertNotification(NotificationEntity notification) {
        service.execute(() -> notificationDao.insertNotification(notification));
    }

    public LiveData<List<NotificationEntity>> getAllNotification() {
        return notificationDao.fetchAll();
    }

    public void deleteAllNoti() {
        service.execute(notificationDao::deleteAll);
    }
}