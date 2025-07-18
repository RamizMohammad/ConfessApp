package in.mohammad.ramiz.confess.postdatabase;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.List;

import in.mohammad.ramiz.confess.HomePage;
import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.popups.OkPopUp;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PostViewmodel extends AndroidViewModel {

    private final PostRepo repo;
    private final LiveData<List<PostsData>> savedPosts;
    public MutableLiveData<List<PostsData>> nextPosts = new MutableLiveData<>();
    public MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);

    public PostViewmodel(@NonNull Application application){
        super(application);
        repo = new PostRepo(application);
        savedPosts = repo.getSavedPosts();
    }

    public LiveData<List<PostsData>> getSavedPosts(){
        return savedPosts;
    }

    public void refreshTopPosts(){
        isLoading.setValue(true);
        repo.refreshPosts();
        isLoading.setValue(false);
    }

    public void fetchNextPosts(String lastDate){
        isLoading.setValue(true);
        repo.loadNextPosts(lastDate, new Callback<PostResponse>() {
            @Override
            public void onResponse(Call<PostResponse> call, Response<PostResponse> response) {
                if(response.isSuccessful() && (response.body().getStatus()).equals("done")){
                    nextPosts.postValue(response.body().getPosts());
                }
                isLoading.setValue(false);
            }

            @Override
            public void onFailure(Call<PostResponse> call, Throwable t) {
                isLoading.setValue(false);
            }
        });
    }
}
