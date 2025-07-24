package in.mohammad.ramiz.confess.notificationdatabase;

import android.app.Application;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NotificationRepo {

    private final NotificationDao notificationDao;
    private final ExecutorService service;
    public MutableLiveData<List<NotificationEntity>> liveData = new MutableLiveData<>();

    public NotificationRepo(Application application){
        NotificationDatabase db = NotificationDatabase.getInstance(application);
        notificationDao = db.notificationDao();
        service = Executors.newSingleThreadExecutor();
    }

    public void insertNotification(NotificationEntity notifications){
        service.execute(() -> notificationDao.insertNotification(notifications));
    }

    public LiveData<List<NotificationEntity>> getAllNotification(){
        service.execute(() -> {
            List<NotificationEntity> notificationEntities = notificationDao.fetchAll();
            liveData.postValue(notificationEntities);
        });
        return liveData;
    }

    public void deleteAllNoti(){
        service.execute(notificationDao::deleteAll);
    }
}
