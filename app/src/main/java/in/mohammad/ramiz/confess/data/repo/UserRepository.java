package in.mohammad.ramiz.confess.data.repo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonObject;

import in.mohammad.ramiz.confess.ConfessApp;
import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiClient;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.api.Calls;
import in.mohammad.ramiz.confess.data.api.Pending;
import in.mohammad.ramiz.confess.data.model.AboutRequest;
import in.mohammad.ramiz.confess.data.model.AvatarRequest;
import in.mohammad.ramiz.confess.data.model.ChangePasswordRequest;
import in.mohammad.ramiz.confess.data.model.EnabledRequest;
import in.mohammad.ramiz.confess.data.model.FollowState;
import in.mohammad.ramiz.confess.data.model.MatchResult;
import in.mohammad.ramiz.confess.data.model.Me;
import in.mohammad.ramiz.confess.data.model.NotificationPrefs;
import in.mohammad.ramiz.confess.data.model.PasswordRequest;
import in.mohammad.ramiz.confess.data.model.PersonPage;
import in.mohammad.ramiz.confess.data.model.PostPage;
import in.mohammad.ramiz.confess.data.model.PublicProfile;
import in.mohammad.ramiz.confess.data.model.ReportRequest;
import in.mohammad.ramiz.confess.data.model.UserSearchResult;
import in.mohammad.ramiz.confess.data.security.PlayIntegrity;
import in.mohammad.ramiz.confess.data.session.SessionManager;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;

/** My profile + settings, and other people's public profiles. */
public final class UserRepository {

    /** GET users/me — also refreshes the cached copy used on cold start. */
    public Call<Me> me(@NonNull ApiCallback<Me> cb) {
        return Calls.enqueue(ApiClient.api().me(), new ApiCallback<Me>() {
            @Override
            public void onSuccess(Me data) {
                SessionManager.get().cacheMe(data);
                cb.onSuccess(data);
            }

            @Override
            public void onError(ApiException error) {
                cb.onError(error);
            }
        });
    }

    /** GET users/me/graph — my confessions per day for the last {@code weeks} weeks + my streak. */
    public Call<in.mohammad.ramiz.confess.data.model.ConfessionGraph> confessionGraph(
            int weeks, @NonNull ApiCallback<in.mohammad.ramiz.confess.data.model.ConfessionGraph> cb) {
        return Calls.enqueue(ApiClient.api().confessionGraph(weeks), cb);
    }

    /** POST users/me/graph — my graph's look ("" = default) and whether others can see it. */
    public Call<JsonObject> updateGraph(@NonNull String style, boolean isPublic, @NonNull ApiCallback<JsonObject> cb) {
        return Calls.enqueue(ApiClient.api().updateGraph(
                new in.mohammad.ramiz.confess.data.model.GraphStyleRequest(style, isPublic)), new ApiCallback<JsonObject>() {
            @Override public void onSuccess(JsonObject data) {
                Me cached = SessionManager.get().cachedMe();
                if (cached != null) {
                    cached.graphStyle = style;
                    cached.graphPublic = isPublic;
                }
                cb.onSuccess(data);
            }
            @Override public void onError(ApiException e) { cb.onError(e); }
        });
    }

    /** GET users/{alias}/graph — someone's confession graph (hidden = they keep it to themselves). */
    public Call<in.mohammad.ramiz.confess.data.model.ConfessionGraph> userGraph(
            @NonNull String alias, int weeks, @NonNull ApiCallback<in.mohammad.ramiz.confess.data.model.ConfessionGraph> cb) {
        return Calls.enqueue(ApiClient.api().userGraph(alias, weeks), cb);
    }

    public Call<JsonObject> updateAbout(@NonNull String about, @NonNull ApiCallback<JsonObject> cb) {
        return Calls.enqueue(ApiClient.api().updateAbout(new AboutRequest(about)), cb);
    }

    /** Upload a profile photo (JPEG). 451 CONTENT_FLAGGED / 503 when it can't be checked or stored. */
    public Call<JsonObject> uploadAvatar(@NonNull byte[] jpeg, @NonNull ApiCallback<JsonObject> cb) {
        RequestBody body = RequestBody.create(jpeg, MediaType.get("image/jpeg"));
        MultipartBody.Part part = MultipartBody.Part.createFormData("file", "avatar.jpg", body);
        return Calls.enqueue(ApiClient.uploads().uploadAvatar(part), cb);
    }

    public Call<JsonObject> updateAvatar(@NonNull String avatar, @NonNull ApiCallback<JsonObject> cb) {
        return Calls.enqueue(ApiClient.api().updateAvatar(new AvatarRequest(avatar)), cb);
    }

    /** Profile banner: "bn1:p3.c5", or "" to follow the avatar. */
    public Call<JsonObject> updateBanner(@NonNull String banner, @NonNull ApiCallback<JsonObject> cb) {
        return Calls.enqueue(ApiClient.api().updateBanner(new in.mohammad.ramiz.confess.data.model.BannerRequest(banner)), cb);
    }

    // ── Password lock ────────────────────────────────────────────────────────

    /** Unlock screen. On match the session is marked unlocked for this process. */
    public Call<MatchResult> verifyPassword(@NonNull String password, @NonNull ApiCallback<MatchResult> cb) {
        return Calls.enqueue(ApiClient.api().verifyPassword(new PasswordRequest(password)), new ApiCallback<MatchResult>() {
            @Override
            public void onSuccess(MatchResult data) {
                if (data.match) SessionManager.get().markUnlocked();
                cb.onSuccess(data);
            }

            @Override
            public void onError(ApiException error) {
                cb.onError(error);
            }
        });
    }

    // Sensitive actions carry a fresh Play Integrity verdict (server: PLAY_INTEGRITY_SENSITIVE_MODE).

    public Pending setPassword(@NonNull String password, @NonNull ApiCallback<JsonObject> cb) {
        return withIntegrity(cb, (t, n) -> ApiClient.api().setPassword(new PasswordRequest(password), t, n));
    }

    public Pending changePassword(@NonNull String oldPw, @NonNull String newPw, @NonNull ApiCallback<JsonObject> cb) {
        return withIntegrity(cb, (t, n) -> ApiClient.api().changePassword(new ChangePasswordRequest(oldPw, newPw), t, n));
    }

    public Pending removePassword(@NonNull String password, @NonNull ApiCallback<JsonObject> cb) {
        return withIntegrity(cb, (t, n) -> ApiClient.api().removePassword(new PasswordRequest(password), t, n));
    }

    public Pending setBiometric(boolean enabled, @NonNull ApiCallback<JsonObject> cb) {
        return withIntegrity(cb, (t, n) -> ApiClient.api().setBiometric(new EnabledRequest(enabled), t, n));
    }

    /** Deletes the account and everything written with it. Clears the session on success. */
    public Pending deleteAccount(@NonNull ApiCallback<JsonObject> cb) {
        return withIntegrity(new ApiCallback<JsonObject>() {
            @Override
            public void onSuccess(JsonObject data) {
                SessionManager.get().clear();
                cb.onSuccess(data);
            }

            @Override
            public void onError(ApiException error) {
                cb.onError(error);
            }
        }, (t, n) -> ApiClient.api().deleteAccount(t, n));
    }

    private interface IntegrityCall {
        Call<JsonObject> make(@Nullable String integrityToken, @Nullable String integrityNonce);
    }

    private static Pending withIntegrity(ApiCallback<JsonObject> cb, IntegrityCall call) {
        Pending pending = new Pending();
        PlayIntegrity.attest(ConfessApp.getAppContext(), (token, nonce) -> {
            if (!pending.isCancelled()) pending.attach(Calls.enqueue(call.make(token, nonce), cb));
        });
        return pending;
    }

    // ── People ───────────────────────────────────────────────────────────────

    public Call<UserSearchResult> search(@NonNull String query, @NonNull ApiCallback<UserSearchResult> cb) {
        return Calls.enqueue(ApiClient.api().searchUsers(query, 20), cb);
    }

    public Call<PublicProfile> profile(@NonNull String alias, @NonNull ApiCallback<PublicProfile> cb) {
        return Calls.enqueue(ApiClient.api().profile(alias), cb);
    }

    // ── Following ────────────────────────────────────────────────────────────

    /** Idempotent on the server — safe to retry. */
    public Call<FollowState> setFollowing(@NonNull String alias, boolean follow, @NonNull ApiCallback<FollowState> cb) {
        return Calls.enqueue(follow ? ApiClient.api().follow(alias) : ApiClient.api().unfollow(alias), cb);
    }

    /** Report a person (one per reporter on the server, idempotent). */
    public Call<JsonObject> reportUser(@NonNull String alias, @NonNull String reason, @NonNull ApiCallback<JsonObject> cb) {
        return Calls.enqueue(ApiClient.api().reportUser(alias, new ReportRequest(reason)), cb);
    }

    // ── Blocking ─────────────────────────────────────────────────────────────

    /** Block / unblock (both idempotent on the server). */
    public Call<JsonObject> setBlocked(@NonNull String alias, boolean block, @NonNull ApiCallback<JsonObject> cb) {
        return Calls.enqueue(block ? ApiClient.api().block(alias) : ApiClient.api().unblock(alias), cb);
    }

    public Call<PersonPage> blocked(@Nullable String cursor, @NonNull ApiCallback<PersonPage> cb) {
        return Calls.enqueue(ApiClient.api().blocked(cursor), cb);
    }

    /** followers == true → people who follow me; false → people I follow. */
    public Call<PersonPage> people(boolean followers, @Nullable String cursor, @NonNull ApiCallback<PersonPage> cb) {
        return Calls.enqueue(followers ? ApiClient.api().followers(cursor) : ApiClient.api().following(cursor), cb);
    }

    /** Change some notification switches (null = unchanged). */
    public Call<NotificationPrefs> setNotificationPrefs(@NonNull NotificationPrefs change,
                                                        @NonNull ApiCallback<NotificationPrefs> cb) {
        return Calls.enqueue(ApiClient.api().setNotificationPrefs(change), cb);
    }

    public Call<PostPage> postsByAlias(@NonNull String alias, @Nullable String cursor, @NonNull ApiCallback<PostPage> cb) {
        return Calls.enqueue(ApiClient.api().postsByAlias(alias, cursor, null), cb);
    }
}
