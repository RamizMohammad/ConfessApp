package in.mohammad.ramiz.confess.notificationdatabase;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.List;

public class NotificationViewmodel extends AndroidViewModel {

    private final NotificationRepo repo;
    private final LiveData<List<NotificationEntity>> getAllNotification;

    public NotificationViewmodel(@NonNull Application application){
        super(application);
        repo = new NotificationRepo(application);
        getAllNotification = repo.getAllNotification();
    }

    public LiveData<List<NotificationEntity>> getGetAllNotification(){
        return getAllNotification;
    }

    public void deleteAllNotification(){
        repo.deleteAllNoti();
    }

    public void insertNotification(NotificationEntity notification){
        repo.insertNotification(notification);
    }
}
