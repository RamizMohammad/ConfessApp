package in.mohammad.ramiz.confess.likemanager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import in.mohammad.ramiz.confess.BuildConfig;
import in.mohammad.ramiz.confess.server.Endpoints;
import in.mohammad.ramiz.confess.server.ServerConfigs;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LikeSyncQueue {

    private Endpoints endpoints;
    private final ExecutorService service = Executors.newFixedThreadPool(5);
    private static LikeSyncQueue instance;

    private LikeSyncQueue(){
        endpoints = ServerConfigs.getInstance().create(Endpoints.class);
    }

    public static synchronized LikeSyncQueue getInstance() {
        if(instance == null){
            instance = new LikeSyncQueue();
        }
        return instance;
    }

    public void enqueueLike(String postId, String userEmail) {
        service.execute(() -> {
            sendLikeToServer(postId, userEmail);
        });
    }

    public void enqueueUnlike(String postId, String userEmail) {
        service.execute(() -> {
            sendUnlikeToServer(postId, userEmail);
        });
    }

    private void sendLikeToServer(String postId, String UserEmail) {
        LikeRequest request = new LikeRequest(UserEmail, postId);

        Call<LikeResponse> call = endpoints.addLike(BuildConfig.CLIENT_API, request);
        call.enqueue(new Callback<LikeResponse>() {
            @Override
            public void onResponse(Call<LikeResponse> call, Response<LikeResponse> response) {

            }

            @Override
            public void onFailure(Call<LikeResponse> call, Throwable t) {

            }
        });
    }

    private void sendUnlikeToServer(String postId, String UserEmail) {
        LikeRequest request = new LikeRequest(UserEmail, postId);

        Call<LikeResponse> call = endpoints.addUnlike(BuildConfig.CLIENT_API, request);
        call.enqueue(new Callback<LikeResponse>() {
            @Override
            public void onResponse(Call<LikeResponse> call, Response<LikeResponse> response) {

            }

            @Override
            public void onFailure(Call<LikeResponse> call, Throwable t) {

            }
        });
    }
}
