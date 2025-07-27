package in.mohammad.ramiz.confess.postdatabase;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PostViewmodel extends AndroidViewModel {

    private final PostRepo repo;
    private final LiveData<List<PostsData>> savedPosts;
    public MutableLiveData<List<PostsData>> nextPosts = new MutableLiveData<>();
    public MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);

    public PostViewmodel(@NonNull Application application) {
        super(application);
        repo = new PostRepo(application);
        savedPosts = repo.getSavedPosts();
    }

    public LiveData<List<PostsData>> getSavedPosts() {
        return savedPosts;
    }

    public void eraseAll(){
        repo.eraseAll();
    }

    public void refreshTopPosts(String email, ViewmodelRefreshCallback callback) {
        isLoading.setValue(true);
        repo.refreshPosts(email, new PostRepo.RepoCallback() {
            @Override
            public void onRepoSuccess() {
                callback.onSuccess();
                isLoading.setValue(false);
            }

            @Override
            public void onRepoFailure() {
                callback.onFail();
                isLoading.setValue(false);
            }

            @Override
            public void onEmptyPostResponse() {
                callback.onEmpty();
                isLoading.setValue(false);
            }
        });
    }

    public void fetchNextPosts(String email, String lastDate, ViewmodelNextCallback callback) {
        isLoading.setValue(true);
        repo.loadNextPosts(email, lastDate, new Callback<PostResponse>() {
            @Override
            public void onResponse(Call<PostResponse> call, Response<PostResponse> response) {
                Log.d("resposne", ""+response.body());
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
            public void onFailure(Call<PostResponse> call, Throwable t) {
                Log.d("checks", ""+t);
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