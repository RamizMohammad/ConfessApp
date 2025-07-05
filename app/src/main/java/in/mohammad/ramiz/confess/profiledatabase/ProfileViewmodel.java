package in.mohammad.ramiz.confess.profiledatabase;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

public class ProfileViewmodel extends AndroidViewModel {

    private final ProfileRepo repo;

    public ProfileViewmodel(@NonNull Application application){
        super(application);
        repo = new ProfileRepo(application);
    }

    public LiveData<ProfileEntity> getProfile(String email){
        return repo.getProfile(email);
    }

    public void serverSync(String email, ProfileRepo.onAnyError errorCallback){
        repo.syncWithServer(email, errorCallback);
    }
}
