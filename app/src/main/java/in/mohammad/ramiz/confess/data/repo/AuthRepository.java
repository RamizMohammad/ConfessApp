package in.mohammad.ramiz.confess.data.repo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonObject;

import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiClient;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.api.Calls;
import in.mohammad.ramiz.confess.data.model.AliasAvailability;
import in.mohammad.ramiz.confess.data.model.AuthResponse;
import in.mohammad.ramiz.confess.data.model.GoogleTokenRequest;
import in.mohammad.ramiz.confess.data.model.Me;
import in.mohammad.ramiz.confess.data.model.PushTokenRequest;
import in.mohammad.ramiz.confess.data.model.RegisterRequest;
import in.mohammad.ramiz.confess.data.session.SessionManager;
import in.mohammad.ramiz.confess.ConfessApp;
import in.mohammad.ramiz.confess.data.api.Pending;
import in.mohammad.ramiz.confess.data.model.ClientInfo;
import in.mohammad.ramiz.confess.data.security.PlayIntegrity;
import in.mohammad.ramiz.confess.data.session.SessionSnapshot;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Sign-in / onboarding. login() and register() store the session on success.
 * Both first fetch a Play Integrity verdict (when configured) and ask for a
 * refreshable, signed session ({@link ClientInfo}).
 *
 *   login → 404 NOT_REGISTERED  ⇒ start onboarding with the same Google idToken
 *   register → 409 ALIAS_TAKEN  ⇒ back to the alias step
 */
public final class AuthRepository {

    public Pending login(@NonNull String googleIdToken, @NonNull ApiCallback<Me> cb) {
        Pending pending = new Pending();
        PlayIntegrity.attest(ConfessApp.getAppContext(), (token, nonce) -> {
            if (pending.isCancelled()) return;
            GoogleTokenRequest body = new GoogleTokenRequest(googleIdToken, new ClientInfo(token, nonce));
            pending.attach(Calls.enqueue(ApiClient.api().login(body), storing(cb)));
        });
        return pending;
    }

    public Pending register(@NonNull RegisterRequest body, @NonNull ApiCallback<Me> cb) {
        Pending pending = new Pending();
        PlayIntegrity.attest(ConfessApp.getAppContext(), (token, nonce) -> {
            if (pending.isCancelled()) return;
            body.client = new ClientInfo(token, nonce);
            pending.attach(Calls.enqueue(ApiClient.api().register(body), storing(new ApiCallback<Me>() {
                @Override
                public void onSuccess(Me me) {
                    SessionManager.get().markUnlocked();   // they just chose the password
                    cb.onSuccess(me);
                }

                @Override
                public void onError(ApiException error) {
                    cb.onError(error);
                }
            })));
        });
        return pending;
    }

    public Call<AliasAvailability> aliasAvailable(@NonNull String alias, @NonNull ApiCallback<AliasAvailability> cb) {
        return Calls.enqueue(ApiClient.api().aliasAvailable(alias), cb);
    }

    /** Emails the account's own address a reset link for the password lock. */
    public Call<JsonObject> forgotPassword(@NonNull ApiCallback<JsonObject> cb) {
        return Calls.enqueue(ApiClient.api().forgotPassword(), cb);
    }

    /**
     * Sign out on this device: forget the push token and revoke the session on
     * the server (best effort, with the credentials captured first), then wipe
     * the local session. Google sign-out itself is done by the UI.
     */
    public void signOut(@Nullable String pushToken) {
        SessionSnapshot snap = SessionManager.get().snapshot();
        if (snap != null) {
            if (pushToken != null) {
                ApiClient.api().removePushToken(snap.bearer(), new PushTokenRequest(pushToken), snap).enqueue(QUIET);
            }
            ApiClient.api().logout(snap.bearer(), snap).enqueue(QUIET);
        }
        SessionManager.get().clear();
    }

    /** Sign-out calls: the user is already gone, so results don't route anywhere. */
    private static final Callback<JsonObject> QUIET = new Callback<JsonObject>() {
        @Override public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) { }
        @Override public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) { }
    };

    private static ApiCallback<AuthResponse> storing(ApiCallback<Me> cb) {
        return new ApiCallback<AuthResponse>() {
            @Override
            public void onSuccess(AuthResponse data) {
                SessionManager.get().start(data);
                cb.onSuccess(data.user);
            }

            @Override
            public void onError(ApiException error) {
                cb.onError(error);
            }
        };
    }
}
