package in.mohammad.ramiz.confess.data.security;

import androidx.annotation.Nullable;

import java.io.IOException;

import in.mohammad.ramiz.confess.data.api.ApiClient;
import in.mohammad.ramiz.confess.data.model.AuthResponse;
import in.mohammad.ramiz.confess.data.model.RefreshRequest;
import in.mohammad.ramiz.confess.data.session.SessionManager;
import retrofit2.Response;

/**
 * Rotates the refresh token (blocking — runs on an OkHttp thread). One refresh
 * at a time: callers that queue behind a successful refresh just use its result.
 *
 * The server revokes the whole session if an old refresh token is ever used
 * again, so the new pair is saved before anything else happens.
 */
public final class TokenRefresher {

    private TokenRefresher() { }

    /**
     * @param staleAccess the access token the caller used (null = "make sure it's fresh")
     * @return true when a usable access token is now stored
     */
    public static synchronized boolean refresh(@Nullable String staleAccess) {
        SessionManager s = SessionManager.get();
        if (!s.isRefreshable()) return false;
        String current = s.token();
        if (current != null && !SessionManager.isExpired(current, 60)
                && (staleAccess == null || !current.equals(staleAccess))) {
            return true;                                   // someone else already refreshed
        }
        String rt = s.refreshToken();
        if (rt == null) return false;
        try {
            Response<AuthResponse> r = ApiClient.authApi().refresh(new RefreshRequest(rt)).execute();
            AuthResponse a = r.body();
            if (r.isSuccessful() && a != null && a.token != null && a.refreshToken != null) {
                s.rotate(a.token, a.refreshToken);
                return true;
            }
            if (r.code() == 401) s.clear();                // REFRESH_INVALID / revoked: sign in again
            return false;                                  // 429 / 5xx: keep the session, try later
        } catch (TamperedResponseException e) {
            return false;
        } catch (IOException e) {
            return false;                                  // offline: keep the session
        }
    }
}
