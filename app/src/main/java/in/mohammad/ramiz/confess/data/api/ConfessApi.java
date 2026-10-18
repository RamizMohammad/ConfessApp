package in.mohammad.ramiz.confess.data.api;

import androidx.annotation.Nullable;

import com.google.gson.JsonObject;

import in.mohammad.ramiz.confess.data.model.AboutRequest;
import in.mohammad.ramiz.confess.data.model.AliasAvailability;
import in.mohammad.ramiz.confess.data.model.AuthResponse;
import in.mohammad.ramiz.confess.data.model.AvatarRequest;
import in.mohammad.ramiz.confess.data.model.BookDetail;
import in.mohammad.ramiz.confess.data.model.BurstPage;
import in.mohammad.ramiz.confess.data.model.BookDownload;
import in.mohammad.ramiz.confess.data.model.BookPage;
import in.mohammad.ramiz.confess.data.model.BookRequest;
import in.mohammad.ramiz.confess.data.model.Book;
import in.mohammad.ramiz.confess.data.model.ChapterRead;
import in.mohammad.ramiz.confess.data.model.ChapterRequest;
import in.mohammad.ramiz.confess.data.model.ChangePasswordRequest;
import in.mohammad.ramiz.confess.data.model.Comment;
import in.mohammad.ramiz.confess.data.model.CommentPage;
import in.mohammad.ramiz.confess.data.model.CreatePostRequest;
import in.mohammad.ramiz.confess.data.model.CreateStoryRequest;
import in.mohammad.ramiz.confess.data.model.EnabledRequest;
import in.mohammad.ramiz.confess.data.model.FeatureRequest;
import in.mohammad.ramiz.confess.data.model.FollowState;
import in.mohammad.ramiz.confess.data.model.GoogleTokenRequest;
import in.mohammad.ramiz.confess.data.model.IntegrityNonce;
import in.mohammad.ramiz.confess.data.model.LikeRequest;
import in.mohammad.ramiz.confess.data.model.VoteRequest;
import in.mohammad.ramiz.confess.data.model.LikeState;
import in.mohammad.ramiz.confess.data.model.LikerPage;
import in.mohammad.ramiz.confess.data.model.MarkReadRequest;
import in.mohammad.ramiz.confess.data.model.MatchResult;
import in.mohammad.ramiz.confess.data.model.Me;
import in.mohammad.ramiz.confess.data.model.NotificationPage;
import in.mohammad.ramiz.confess.data.model.NotificationPrefs;
import in.mohammad.ramiz.confess.data.model.PasswordRequest;
import in.mohammad.ramiz.confess.data.model.PersonPage;
import in.mohammad.ramiz.confess.data.model.Post;
import in.mohammad.ramiz.confess.data.model.PostPage;
import in.mohammad.ramiz.confess.data.model.PublicProfile;
import in.mohammad.ramiz.confess.data.model.PushTokenRequest;
import in.mohammad.ramiz.confess.data.model.RefreshRequest;
import in.mohammad.ramiz.confess.data.model.RegisterRequest;
import in.mohammad.ramiz.confess.data.model.ReportRequest;
import in.mohammad.ramiz.confess.data.model.Story;
import in.mohammad.ramiz.confess.data.model.StoryTray;
import in.mohammad.ramiz.confess.data.model.StoryViewers;
import in.mohammad.ramiz.confess.data.model.TextRequest;
import in.mohammad.ramiz.confess.data.model.UnreadCount;
import in.mohammad.ramiz.confess.data.model.UserSearchResult;
import in.mohammad.ramiz.confess.data.session.SessionSnapshot;
import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Multipart;
import retrofit2.http.PATCH;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.Tag;

/**
 * Confess API v2 (server 3.x). Mirrors API_V2.md in the server repo one-to-one.
 * Response types are the INNER "data" object — EnvelopeConverterFactory unwraps it.
 * The x-api-key and Bearer headers are added by ApiClient's interceptors.
 * Sensitive actions take X-Integrity-* headers (a Play Integrity step-up; null = omitted).
 */
public interface ConfessApi {

    // ── System ────────────────────────────────────────────────────────────
    /** Postgres + Redis reachable. No auth. */
    @GET("ready")
    Call<JsonObject> ready();

    // ── Auth & onboarding ─────────────────────────────────────────────────
    @POST("api/v2/auth/login")
    Call<AuthResponse> login(@Body GoogleTokenRequest body);

    @POST("api/v2/auth/register")
    Call<AuthResponse> register(@Body RegisterRequest body);

    /** Single-use nonce for a Play Integrity request (API key only). */
    @POST("api/v2/auth/integrity-nonce")
    Call<IntegrityNonce> integrityNonce();

    /** Rotate the refresh token. Use ApiClient.authApi() (no auth interceptor). */
    @POST("api/v2/auth/refresh")
    Call<AuthResponse> refresh(@Body RefreshRequest body);

    /** Revoke this device's session. Credentials passed explicitly: sign-out clears them first. */
    @POST("api/v2/auth/logout")
    Call<JsonObject> logout(@Header("Authorization") String bearer, @Tag SessionSnapshot session);

    @POST("api/v2/auth/forgot-password")
    Call<JsonObject> forgotPassword();

    @GET("api/v2/users/alias-available")
    Call<AliasAvailability> aliasAvailable(@Query("alias") String alias);

    // ── Me / settings ─────────────────────────────────────────────────────
    @GET("api/v2/users/me")
    Call<Me> me();

    @POST("api/v2/users/me/about")
    Call<JsonObject> updateAbout(@Body AboutRequest body);

    /** Profile photo (JPEG bytes, already shrunk on the phone). Use ApiClient.uploads(). */
    @Multipart
    @POST("api/v2/users/me/avatar/upload")
    Call<JsonObject> uploadAvatar(@Part MultipartBody.Part file);

    @POST("api/v2/users/me/avatar")
    Call<JsonObject> updateAvatar(@Body AvatarRequest body);

    @POST("api/v2/users/me/password/verify")
    Call<MatchResult> verifyPassword(@Body PasswordRequest body);

    @POST("api/v2/users/me/password/set")
    Call<JsonObject> setPassword(@Body PasswordRequest body,
                                 @Header("X-Integrity-Token") @Nullable String integrityToken, @Header("X-Integrity-Nonce") @Nullable String integrityNonce);

    @POST("api/v2/users/me/password/change")
    Call<JsonObject> changePassword(@Body ChangePasswordRequest body,
                                    @Header("X-Integrity-Token") @Nullable String integrityToken, @Header("X-Integrity-Nonce") @Nullable String integrityNonce);

    @POST("api/v2/users/me/password/remove")
    Call<JsonObject> removePassword(@Body PasswordRequest body,
                                    @Header("X-Integrity-Token") @Nullable String integrityToken, @Header("X-Integrity-Nonce") @Nullable String integrityNonce);

    @POST("api/v2/users/me/biometric")
    Call<JsonObject> setBiometric(@Body EnabledRequest body,
                                  @Header("X-Integrity-Token") @Nullable String integrityToken, @Header("X-Integrity-Nonce") @Nullable String integrityNonce);

    @DELETE("api/v2/users/me")
    Call<JsonObject> deleteAccount(@Header("X-Integrity-Token") @Nullable String integrityToken, @Header("X-Integrity-Nonce") @Nullable String integrityNonce);

    // ── People ────────────────────────────────────────────────────────────
    @GET("api/v2/users/search")
    Call<UserSearchResult> searchUsers(@Query("q") String query, @Query("limit") @Nullable Integer limit);

    @GET("api/v2/users/{alias}")
    Call<PublicProfile> profile(@Path("alias") String alias);

    @POST("api/v2/users/{alias}/follow")
    Call<FollowState> follow(@Path("alias") String alias);

    @DELETE("api/v2/users/{alias}/follow")
    Call<FollowState> unfollow(@Path("alias") String alias);

    @POST("api/v2/users/{alias}/report")
    Call<JsonObject> reportUser(@Path("alias") String alias, @Body ReportRequest body);

    @POST("api/v2/users/{alias}/block")
    Call<JsonObject> block(@Path("alias") String alias);

    @DELETE("api/v2/users/{alias}/block")
    Call<JsonObject> unblock(@Path("alias") String alias);

    /** People I blocked: users = [{alias, avatar, blockedAt}] (read as Person). */
    @GET("api/v2/users/me/blocked")
    Call<PersonPage> blocked(@Query("cursor") @Nullable String cursor);

    @GET("api/v2/users/me/followers")
    Call<PersonPage> followers(@Query("cursor") @Nullable String cursor);

    @GET("api/v2/users/me/following")
    Call<PersonPage> following(@Query("cursor") @Nullable String cursor);

    /** Partial: only non-null switches change. Returns all six. */
    @POST("api/v2/users/me/notifications")
    Call<NotificationPrefs> setNotificationPrefs(@Body NotificationPrefs body);

    @GET("api/v2/users/{alias}/posts")
    Call<PostPage> postsByAlias(@Path("alias") String alias,
                                @Query("cursor") @Nullable String cursor,
                                @Query("limit") @Nullable Integer limit);

    // ── Confessions ───────────────────────────────────────────────────────
    @POST("api/v2/posts")
    Call<Post> createPost(@Body CreatePostRequest body);

    @POST("api/v2/posts/{postId}/vote")
    Call<Post> vote(@Path("postId") String postId, @Body VoteRequest body);

    /** sort: "latest" | "top" (most felt, last 7 days) · scope: "all" | "following". */
    /** bursts=true: one card per burst (its newest, with {@code burst: {id, count}}). */
    @GET("api/v2/posts/feed")
    Call<PostPage> feed(@Query("sort") String sort,
                        @Query("scope") String scope,
                        @Query("cursor") @Nullable String cursor,
                        @Query("limit") @Nullable Integer limit,
                        @Query("bursts") @Nullable Boolean bursts);

    /** One burst, oldest first, a set at a time (the slider / the burst reader). */
    @GET("api/v2/posts/bursts/{id}")
    Call<BurstPage> burst(@Path("id") String burstId,
                          @Query("scope") String scope,
                          @Query("cursor") @Nullable String cursor,
                          @Query("limit") int limit);

    @GET("api/v2/posts/mine")
    Call<PostPage> myPosts(@Query("cursor") @Nullable String cursor,
                           @Query("limit") @Nullable Integer limit);

    @GET("api/v2/posts/{postId}")
    Call<Post> post(@Path("postId") String postId);

    @DELETE("api/v2/posts/{postId}")
    Call<JsonObject> deletePost(@Path("postId") String postId);

    @POST("api/v2/posts/{postId}/like")
    Call<LikeState> like(@Path("postId") String postId, @Body LikeRequest body);

    @DELETE("api/v2/posts/{postId}/like")
    Call<LikeState> unlike(@Path("postId") String postId);

    @GET("api/v2/posts/{postId}/likes")
    Call<LikerPage> likers(@Path("postId") String postId, @Query("cursor") @Nullable String cursor);

    @POST("api/v2/posts/{postId}/report")
    Call<JsonObject> report(@Path("postId") String postId, @Body ReportRequest body);

    // ── Responses ─────────────────────────────────────────────────────────
    @GET("api/v2/posts/{postId}/comments")
    Call<CommentPage> comments(@Path("postId") String postId,
                               @Query("cursor") @Nullable String cursor,
                               @Query("limit") @Nullable Integer limit);

    @POST("api/v2/posts/{postId}/comments")
    Call<Comment> addComment(@Path("postId") String postId, @Body TextRequest body);

    @POST("api/v2/comments/{commentId}/report")
    Call<JsonObject> reportComment(@Path("commentId") String commentId, @Body ReportRequest body);

    @DELETE("api/v2/comments/{commentId}")
    Call<JsonObject> deleteComment(@Path("commentId") String commentId);

    // ── Stories ───────────────────────────────────────────────────────────
    @GET("api/v2/stories")
    Call<StoryTray> storyTray();

    @POST("api/v2/stories")
    Call<Story> createStory(@Body CreateStoryRequest body);

    @POST("api/v2/stories/{id}/view")
    Call<JsonObject> viewStory(@Path("id") String id);

    @DELETE("api/v2/stories/{id}")
    Call<JsonObject> deleteStory(@Path("id") String id);

    @GET("api/v2/stories/{id}/viewers")
    Call<StoryViewers> storyViewers(@Path("id") String id, @Query("cursor") @Nullable String cursor);

    @POST("api/v2/stories/{id}/react")
    Call<JsonObject> reactStory(@Path("id") String id, @Body LikeRequest body);

    @DELETE("api/v2/stories/{id}/react")
    Call<JsonObject> unreactStory(@Path("id") String id);

    @POST("api/v2/stories/{id}/report")
    Call<JsonObject> reportStory(@Path("id") String id, @Body ReportRequest body);

    // ── Notifications ─────────────────────────────────────────────────────
    @GET("api/v2/notifications")
    Call<NotificationPage> notifications(@Query("scope") String scope,
                                         @Query("cursor") @Nullable String cursor,
                                         @Query("limit") @Nullable Integer limit);

    @GET("api/v2/notifications/unread-count")
    Call<UnreadCount> unreadCount();

    @POST("api/v2/notifications/read")
    Call<JsonObject> markRead(@Body MarkReadRequest body);

    // ── Push ──────────────────────────────────────────────────────────────
    @POST("api/v2/push/token")
    Call<JsonObject> registerPushToken(@Body PushTokenRequest body);

    /** Auth passed explicitly: sign-out clears the session before this request runs. */
    @POST("api/v2/push/token/remove")
    Call<JsonObject> removePushToken(@Header("Authorization") String bearer, @Body PushTokenRequest body,
                                     @Tag SessionSnapshot session);

    @POST("api/v2/push/enabled")
    Call<JsonObject> setPushEnabled(@Body EnabledRequest body);

    // ── Feedback ──────────────────────────────────────────────────────────
    @POST("api/v2/feedback/feature")
    Call<JsonObject> requestFeature(@Body FeatureRequest body);

    // ── Books (server 3.6) ────────────────────────────────────────────────
    /** tab: updated · new · following; alias = one writer's books. */
    @GET("api/v2/books")
    Call<BookPage> books(@Query("tab") String tab, @Query("alias") @Nullable String alias,
                         @Query("cursor") @Nullable String cursor, @Query("limit") @Nullable Integer limit);

    @GET("api/v2/books/mine")
    Call<BookPage> myBooks();

    @POST("api/v2/books")
    Call<BookDetail> createBook(@Body BookRequest body);

    @GET("api/v2/books/{id}")
    Call<BookDetail> book(@Path("id") String bookId);

    @PATCH("api/v2/books/{id}")
    Call<BookDetail> updateBook(@Path("id") String bookId, @Body BookRequest body);

    @DELETE("api/v2/books/{id}")
    Call<JsonObject> deleteBook(@Path("id") String bookId);

    @POST("api/v2/books/{id}/chapters")
    Call<BookDetail> addChapters(@Path("id") String bookId, @Body ChapterRequest.Batch body);

    @PATCH("api/v2/books/{id}/chapters/{no}")
    Call<BookDetail> updateChapter(@Path("id") String bookId, @Path("no") int chapterNo, @Body ChapterRequest body);

    @DELETE("api/v2/books/{id}/chapters/{no}")
    Call<BookDetail> deleteChapter(@Path("id") String bookId, @Path("no") int chapterNo);

    @PUT("api/v2/books/{id}/order")
    Call<BookDetail> reorderChapters(@Path("id") String bookId, @Body ChapterRequest.Order body);

    /** Also remembers my furthest chapter on the server. */
    @GET("api/v2/books/{id}/chapters/{no}")
    Call<ChapterRead> chapter(@Path("id") String bookId, @Path("no") int chapterNo);

    /** Every released chapter in full, in one answer: the book is kept on the phone once I start reading it. */
    @GET("api/v2/books/{id}/download")
    Call<BookDownload> downloadBook(@Path("id") String bookId);

    /** Reading ahead: the same chapter, but the server doesn't count it as reached. */
    @GET("api/v2/books/{id}/chapters/{no}?peek=true")
    Call<ChapterRead> peekChapter(@Path("id") String bookId, @Path("no") int chapterNo);

    @POST("api/v2/books/{id}/follow")
    Call<Book> followBook(@Path("id") String bookId);

    @DELETE("api/v2/books/{id}/follow")
    Call<Book> unfollowBook(@Path("id") String bookId);

    @POST("api/v2/books/{id}/report")
    Call<JsonObject> reportBook(@Path("id") String bookId, @Body ReportRequest body);
}
