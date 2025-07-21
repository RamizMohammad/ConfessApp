package in.mohammad.ramiz.confess.yourconfessiondatabase;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;

import java.security.Policy;
import java.util.List;
import java.util.concurrent.Executors;

import in.mohammad.ramiz.confess.BuildConfig;
import in.mohammad.ramiz.confess.server.Endpoints;
import in.mohammad.ramiz.confess.server.ServerConfigs;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MyPostRepo {

    private final MyPostDao myPostDao;
    private Endpoints endpoints;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public MyPostRepo(Application application){
        MyPostDatabase db = MyPostDatabase.getInstance(application);
        myPostDao = db.myPostDao();
        endpoints = ServerConfigs.getInstance().create(Endpoints.class);
    }

    public LiveData<List<MyPostsData>> getSavedData(){
        return myPostDao.getAllPosts();
    }

    public void eraseAll(){
        Executors.newSingleThreadExecutor().execute(() -> {
            myPostDao.deleteAll();
        });
    }

    public void refreshPosts(String email, MyRepoCallback callback){
        MyPostsRequest request = new MyPostsRequest("empty", email);
        Call<MyPostsResponse> call = endpoints.getMyPosts(BuildConfig.CLIENT_API, request);

        call.enqueue(new Callback<MyPostsResponse>() {
            @Override
            public void onResponse(Call<MyPostsResponse> call, Response<MyPostsResponse> response) {
                if(response.isSuccessful() && (response.body().getStatus()).equals("done")){
                    Executors.newSingleThreadExecutor().execute(() -> {
                        myPostDao.deleteAll();
                        myPostDao.insertPostsDao(response.body().getPosts());

                        mainHandler.post(callback::onMyRepoSuccess);
                    });
                }
                else if (response.isSuccessful() && (response.body().getStatus()).equals("empty")) {
                    mainHandler.post(callback::onEmptyData);
                }
                else {
                    mainHandler.post(callback::onMyRepoFail);
                }
            }

            @Override
            public void onFailure(Call<MyPostsResponse> call, Throwable t) {
                mainHandler.post(callback::onMyRepoFail);
            }
        });
    }

    public void loadNextPosts(String email, String lastDate, Callback<MyPostsResponse> callback){
        MyPostsRequest request = new MyPostsRequest(lastDate, email);
        endpoints.getMyPosts(BuildConfig.CLIENT_API, request).enqueue(callback);
    }

    public interface MyRepoCallback{
        void onMyRepoSuccess();
        void onMyRepoFail();
        void onEmptyData();
    }
}
