package in.mohammad.ramiz.confess.likemanager;

import android.os.Handler;
import android.os.Looper;

import java.util.HashMap;
import java.util.Map;
import in.mohammad.ramiz.confess.likemanager.LikeSyncQueue;

public class LikeStageManager {

    private static final long DEBOUNCE_DELAY = 3000;
    private final Map<String, Runnable> debounceMap = new HashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private static LikeStageManager instance;

    public static synchronized LikeStageManager getInstance() {
        if(instance == null){
            instance = new LikeStageManager();
        }
        return instance;
    }

    public void onLikeAction(String postId, String userEmail, boolean isLiked) {
        if(debounceMap.containsKey(postId)) {
            handler.removeCallbacks(debounceMap.get(postId));
            debounceMap.remove(postId);
        }

        Runnable task = () -> {
            if(isLiked){
                LikeSyncQueue.getInstance().enqueueLike(postId, userEmail);
            } else {
                LikeSyncQueue.getInstance().enqueueUnlike(postId, userEmail);
            }
            debounceMap.remove(postId);
        };

        debounceMap.put(postId, task);
        handler.postDelayed(task, DEBOUNCE_DELAY);
    }
}
