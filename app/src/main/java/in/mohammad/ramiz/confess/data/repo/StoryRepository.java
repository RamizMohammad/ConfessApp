package in.mohammad.ramiz.confess.data.repo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonObject;

import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiClient;
import in.mohammad.ramiz.confess.data.api.Calls;
import in.mohammad.ramiz.confess.data.model.CreateStoryRequest;
import in.mohammad.ramiz.confess.data.model.LikeRequest;
import in.mohammad.ramiz.confess.data.model.ReportRequest;
import in.mohammad.ramiz.confess.data.model.Story;
import in.mohammad.ramiz.confess.data.model.StoryTray;
import in.mohammad.ramiz.confess.data.model.StoryViewers;
import retrofit2.Call;

/** 24-hour text stories. */
public final class StoryRepository {

    public static final int MAX_CHARS = 100;

    public Call<StoryTray> tray(@NonNull ApiCallback<StoryTray> cb) {
        return Calls.enqueue(ApiClient.api().storyTray(), cb);
    }

    /** 451 CONTENT_FLAGGED / 503 MODERATION_UNAVAILABLE like letters; 422 when 10 are live. */
    public Call<Story> create(@NonNull String text, int style, @NonNull ApiCallback<Story> cb) {
        return Calls.enqueue(ApiClient.api().createStory(new CreateStoryRequest(text, style)), cb);
    }

    public Call<JsonObject> markSeen(@NonNull String id, @NonNull ApiCallback<JsonObject> cb) {
        return Calls.enqueue(ApiClient.api().viewStory(id), cb);
    }

    public Call<JsonObject> delete(@NonNull String id, @NonNull ApiCallback<JsonObject> cb) {
        return Calls.enqueue(ApiClient.api().deleteStory(id), cb);
    }

    public Call<StoryViewers> viewers(@NonNull String id, @Nullable String cursor, @NonNull ApiCallback<StoryViewers> cb) {
        return Calls.enqueue(ApiClient.api().storyViewers(id, cursor), cb);
    }

    /** reaction null → take mine back. Reacting also marks the story seen on the server. */
    public Call<JsonObject> react(@NonNull String id, @Nullable String reaction, @NonNull ApiCallback<JsonObject> cb) {
        return Calls.enqueue(reaction != null
                ? ApiClient.api().reactStory(id, new LikeRequest(reaction))
                : ApiClient.api().unreactStory(id), cb);
    }

    public Call<JsonObject> report(@NonNull String id, @NonNull String reason, @NonNull ApiCallback<JsonObject> cb) {
        return Calls.enqueue(ApiClient.api().reportStory(id, new ReportRequest(reason)), cb);
    }
}
