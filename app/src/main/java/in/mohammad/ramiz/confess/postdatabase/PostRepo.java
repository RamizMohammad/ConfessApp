package in.mohammad.ramiz.confess.postdatabase;

import android.app.Application;

import androidx.lifecycle.LiveData;

import java.util.List;
import java.util.concurrent.Executors;

import in.mohammad.ramiz.confess.BuildConfig;
import in.mohammad.ramiz.confess.server.Endpoints;
import in.mohammad.ramiz.confess.server.ServerConfigs;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PostRepo {

    private final PostDao dao;
    private  final Endpoints endpoints;

    public PostRepo(Application application){
        PostDatabase db = PostDatabase.getInstance(application);
        dao = db.dao();
        endpoints = ServerConfigs.getInstance().create(Endpoints.class);
    }

    public LiveData<List<PostsData>> getSavedPosts(){
        return dao.getAllPosts();
    }

    public void refreshPosts(){
        PostRequest request = new PostRequest("empty", false, false);
        Call<PostResponse> call = endpoints.getAllPosts(BuildConfig.CLIENT_API, request);

        call.enqueue(new Callback<PostResponse>() {
            @Override
            public void onResponse(Call<PostResponse> call, Response<PostResponse> response) {
                if(response.isSuccessful() && (response.body().getStatus()).equals("done")){
                    Executors.newSingleThreadExecutor().execute(() -> {
                        dao.deleteAll();
                        dao.insertPostsDao(response.body().getPosts());
                    });
                }
            }

            @Override
            public void onFailure(Call<PostResponse> call, Throwable t) {

            }
        });
    }

    public void loadNextPosts(String lastDate, Callback<PostResponse> callback){
        PostRequest request = new PostRequest(lastDate, true, false);
        endpoints.getAllPosts(BuildConfig.CLIENT_API, request).enqueue(callback);
    }
}
