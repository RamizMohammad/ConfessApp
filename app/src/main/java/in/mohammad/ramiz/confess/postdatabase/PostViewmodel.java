package in.mohammad.ramiz.confess.postdatabase;

import android.app.Application;

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

    public void refreshTopPosts(ViewmodelRefreshCallback callback) {
        isLoading.setValue(true);
        repo.refreshPosts(new PostRepo.RepoCallback() {
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

    public void fetchNextPosts(String lastDate, ViewmodelNextCallback callback) {
        isLoading.setValue(true);
        repo.loadNextPosts(lastDate, new Callback<PostResponse>() {
            @Override
            public void onResponse(Call<PostResponse> call, Response<PostResponse> response) {
                if (response.isSuccessful() && (response.body().getStatus()).equals("done")) {
                    nextPosts.postValue(response.body().getPosts());
                    callback.onSuccess();
                } else if (response.isSuccessful() && (response.body().getStatus()).equals("empty")) {
                    callback.onEmpty();
                }
                isLoading.setValue(false);
            }

            @Override
            public void onFailure(Call<PostResponse> call, Throwable t) {
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