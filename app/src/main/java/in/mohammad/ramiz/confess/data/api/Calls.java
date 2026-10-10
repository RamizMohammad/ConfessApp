package in.mohammad.ramiz.confess.data.api;

import androidx.annotation.NonNull;

import java.io.IOException;

import javax.net.ssl.SSLPeerUnverifiedException;

import in.mohammad.ramiz.confess.data.security.TamperedResponseException;
import in.mohammad.ramiz.confess.data.session.SessionEvents;
import in.mohammad.ramiz.confess.data.session.SessionManager;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Runs a Retrofit call and maps every outcome to {@link ApiCallback}.
 * Callbacks arrive on the main thread. A 401 that ends the session (after the
 * network layer already tried a refresh) clears it and fires
 * {@link SessionEvents#expired()} so the app can route to sign-in. A pinning or
 * response-signature failure becomes INSECURE_CONNECTION. Returns the call so a ViewModel can cancel it in onCleared().
 */
public final class Calls {

    private Calls() { }

    public static <T> Call<T> enqueue(@NonNull Call<T> call, @NonNull ApiCallback<T> cb) {
        call.enqueue(new Callback<T>() {
            @Override
            public void onResponse(@NonNull Call<T> c, @NonNull Response<T> response) {
                if (c.isCanceled()) return;
                if (response.isSuccessful() && response.body() != null) {
                    cb.onSuccess(response.body());
                    return;
                }
                ApiException error = response.isSuccessful()
                        ? new ApiException(response.code(), ErrorCode.UNKNOWN, "Empty response", null)
                        : ApiException.fromErrorBody(response.code(), response.errorBody());
                // Only answers that mean "this session is over" sign the user out — not
                // WRONG_PASSWORD (also 401) or a bad Google token at sign-in.
                if (response.code() == 401 && endsSession(error) && !isAuthEndpoint(c)) {
                    SessionManager.get().clear();
                    SessionEvents.expired();
                }
                cb.onError(error);
            }

            @Override
            public void onFailure(@NonNull Call<T> c, @NonNull Throwable t) {
                if (c.isCanceled()) return;
                if (t instanceof TamperedResponseException || t instanceof SSLPeerUnverifiedException) {
                    cb.onError(ApiException.insecure(t));
                    return;
                }
                cb.onError(t instanceof IOException ? ApiException.offline(t) : ApiException.unknown(t));
            }
        });
        return call;
    }

    private static boolean endsSession(ApiException e) {
        return e.is(ErrorCode.UNAUTHORIZED) || e.is(ErrorCode.SESSION_REVOKED)
                || e.is(ErrorCode.REFRESH_INVALID) || e.is(ErrorCode.BAD_SIGNATURE);
    }

    private static boolean isAuthEndpoint(Call<?> call) {
        String path = call.request().url().encodedPath();
        return path.contains("/auth/login") || path.contains("/auth/register");
    }
}
