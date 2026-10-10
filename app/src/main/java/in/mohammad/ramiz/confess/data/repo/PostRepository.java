package in.mohammad.ramiz.confess.data.repo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonObject;

import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiClient;
import in.mohammad.ramiz.confess.data.api.Calls;
import in.mohammad.ramiz.confess.data.model.Comment;
import in.mohammad.ramiz.confess.data.model.CommentPage;
import in.mohammad.ramiz.confess.data.model.CreatePostRequest;
import in.mohammad.ramiz.confess.data.model.LikeRequest;
import in.mohammad.ramiz.confess.data.model.LikeState;
import in.mohammad.ramiz.confess.data.model.LikerPage;
import in.mohammad.ramiz.confess.data.model.Post;
import in.mohammad.ramiz.confess.data.model.PostPage;
import in.mohammad.ramiz.confess.data.model.ReportRequest;
import in.mohammad.ramiz.confess.data.model.TextRequest;
import retrofit2.Call;

/** Confessions, felt (likes), reports and responses (comments). */
public final class PostRepository {

    public static final String SORT_LATEST = "latest";
    public static final String SORT_TOP = "top";
    public static final String SCOPE_ALL = "all";
    public static final String SCOPE_FOLLOWING = "following";

    public Call<PostPage> feed(@NonNull String sort, @NonNull String scope, @Nullable String cursor,
                               @NonNull ApiCallback<PostPage> cb) {
        return Calls.enqueue(ApiClient.api().feed(sort, scope, cursor, null), cb);
    }

    public Call<PostPage> myPosts(@Nullable String cursor, @NonNull ApiCallback<PostPage> cb) {
        return Calls.enqueue(ApiClient.api().myPosts(cursor, null), cb);
    }

    public Call<Post> post(@NonNull String postId, @NonNull ApiCallback<Post> cb) {
        return Calls.enqueue(ApiClient.api().post(postId), cb);
    }

    /** 451 CONTENT_FLAGGED / 503 MODERATION_UNAVAILABLE are expected — show the moderation card. */
    public Call<Post> create(@NonNull String text, boolean commentsEnabled, @NonNull ApiCallback<Post> cb) {
        return Calls.enqueue(ApiClient.api().createPost(new CreatePostRequest(text, commentsEnabled)), cb);
    }

    public Call<JsonObject> delete(@NonNull String postId, @NonNull ApiCallback<JsonObject> cb) {
        return Calls.enqueue(ApiClient.api().deletePost(postId), cb);
    }

    /** Idempotent on the server: safe to call again after a flaky network. */
    public Call<LikeState> setFelt(@NonNull String postId, boolean felt, @NonNull ApiCallback<LikeState> cb) {
        return setReaction(postId, felt ? "felt" : null, cb);
    }

    /** reaction null → remove mine; otherwise set (or switch to) that reaction. */
    public Call<LikeState> setReaction(@NonNull String postId, @Nullable String reaction, @NonNull ApiCallback<LikeState> cb) {
        return Calls.enqueue(reaction != null
                ? ApiClient.api().like(postId, new LikeRequest(reaction))
                : ApiClient.api().unlike(postId), cb);
    }

    /** Who felt my confession, newest first (writer only). */
    public Call<LikerPage> likers(@NonNull String postId, @Nullable String cursor, @NonNull ApiCallback<LikerPage> cb) {
        return Calls.enqueue(ApiClient.api().likers(postId, cursor), cb);
    }

    public Call<JsonObject> report(@NonNull String postId, @NonNull String reason, @NonNull ApiCallback<JsonObject> cb) {
        return Calls.enqueue(ApiClient.api().report(postId, new ReportRequest(reason)), cb);
    }

    // ── Responses ────────────────────────────────────────────────────────────

    public Call<CommentPage> comments(@NonNull String postId, @Nullable String cursor, @NonNull ApiCallback<CommentPage> cb) {
        return Calls.enqueue(ApiClient.api().comments(postId, cursor, null), cb);
    }

    public Call<Comment> addComment(@NonNull String postId, @NonNull String text, @NonNull ApiCallback<Comment> cb) {
        return Calls.enqueue(ApiClient.api().addComment(postId, new TextRequest(text)), cb);
    }

    public Call<JsonObject> deleteComment(@NonNull String commentId, @NonNull ApiCallback<JsonObject> cb) {
        return Calls.enqueue(ApiClient.api().deleteComment(commentId), cb);
    }
}
