package in.mohammad.ramiz.confess.profiledatabase;

import android.app.Application;

import androidx.lifecycle.LiveData;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import in.mohammad.ramiz.confess.BuildConfig;
import in.mohammad.ramiz.confess.HomePage;
import in.mohammad.ramiz.confess.ProfileFragment;
import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.debugmonitor.TelegramLogs;
import in.mohammad.ramiz.confess.entities.AddUserResponse;
import in.mohammad.ramiz.confess.entities.GetProfileRequest;
import in.mohammad.ramiz.confess.entities.GetProfileResponse;
import in.mohammad.ramiz.confess.popups.OkPopUp;
import in.mohammad.ramiz.confess.popups.OnlyLoader;
import in.mohammad.ramiz.confess.server.Endpoints;
import in.mohammad.ramiz.confess.server.ServerConfigs;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileRepo {

    private ProfileDao profileDao;
    private Executor executors = Executors.newSingleThreadExecutor();

    public ProfileRepo(Application application){
        profileDao = ProfileDatabase.getInstance(application).profileDao();
    }

    public LiveData<ProfileEntity> getProfile(String email){
        return profileDao.getProfileData(email);
    }

    public void syncWithServer(String email, onAnyError errorCallback){
        GetProfileRequest request = new GetProfileRequest(email);

        Call<GetProfileResponse> call = ServerConfigs.getInstance()
                .create(Endpoints.class).getProfile(BuildConfig.CLIENT_API, request);

        call.enqueue(new Callback<GetProfileResponse>() {
            @Override
            public void onResponse(Call<GetProfileResponse> call, Response<GetProfileResponse> response) {
                if(response.isSuccessful() && response.body() != null){
                    if(response.body().isMessage()){
                        ProfileEntity profileEntity = new ProfileEntity();
                        profileEntity.setEmail(response.body().getEmail());
                        profileEntity.setAliasName(response.body().getAliasName());
                        profileEntity.setAbout(response.body().getAbout());
                        profileEntity.setProfileLink(response.body().getProfileLink());

                        try{
                            Executors.newSingleThreadExecutor().execute(() -> {
                                profileDao.InsertProfileData(profileEntity);
                            });
                        } catch (Exception e){
                            TelegramLogs.sendTelegramLog("We have profile data insertion error"+e);
                        }
                    }
                    else {
                        errorCallback.userError();
                    }
                }
                else {
                    TelegramLogs.sendTelegramLog("There is an empty response for profile");
                    errorCallback.onResponseError();
                }
            }

            @Override
            public void onFailure(Call<GetProfileResponse> call, Throwable t) {
                TelegramLogs.sendTelegramLog("We got server side error"+t);
                errorCallback.onServerError();
            }
        });
    }

    public interface onAnyError{
        void userError();
        void onResponseError();
        void onServerError();
    }
}
