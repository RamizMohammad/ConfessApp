package in.mohammad.ramiz.confess.yourconfessiondatabase;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MyPostViewmodel extends AndroidViewModel {

    private final MyPostRepo repo;
    private final LiveData<List<MyPostsData>> savedPosts;
    public MutableLiveData<List<MyPostsData>> nextPosts = new MutableLiveData<>();
    public MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);

    public MyPostViewmodel(@NonNull Application application){
        super(application);
        repo = new MyPostRepo(application);
        savedPosts = repo.getSavedData();
    }

    public LiveData<List<MyPostsData>> getSavedPosts(){
        return savedPosts;
    }

    public void eraseAll(){
        repo.eraseAll();
    }

    public void refreshTopPosts(String email, ViewmodelRefreshCallback callback){
        isLoading.setValue(true);
        repo.refreshPosts(email, new MyPostRepo.MyRepoCallback() {
            @Override
            public void onMyRepoSuccess() {
                callback.onSuccess();
                isLoading.setValue(false);
            }

            @Override
            public void onMyRepoFail() {
                callback.onFail();
                isLoading.setValue(false);
            }

            @Override
            public void onEmptyData() {
                callback.onEmpty();
                isLoading.setValue(false);
            }
        });
    }

    public void fetchNextPosts(String email, String lastDate, ViewmodelNextCallback callback){
        isLoading.setValue(true);
        repo.loadNextPosts(email, lastDate, new Callback<MyPostsResponse>() {
            @Override
            public void onResponse(Call<MyPostsResponse> call, Response<MyPostsResponse> response) {
                if (response.isSuccessful() && (response.body().getStatus()).equals("done")) {
                    nextPosts.postValue(response.body().getPosts());
                    callback.onSuccess();
                } else if (response.isSuccessful() && (response.body().getStatus()).equals("empty")) {
                    callback.onEmpty();
                }
                else {
                    callback.onFail();
                }
                isLoading.setValue(false);
            }

            @Override
            public void onFailure(Call<MyPostsResponse> call, Throwable t) {
                isLoading.setValue(false);
                callback.onFail();
            }
        });
    }

    public interface ViewmodelRefreshCallback {
        void onSuccess();
        void onFail();
        void onEmpty();
    }

    public interface ViewmodelNextCallback {
        void onSuccess();
        void onFail();
        void onEmpty();
    }
}
