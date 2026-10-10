package in.mohammad.ramiz.confess.data.security;

import androidx.annotation.NonNull;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.IOException;

import in.mohammad.ramiz.confess.data.api.ErrorCode;
import in.mohammad.ramiz.confess.data.session.SessionManager;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Adds "Authorization: Bearer <access token>". For refreshable sessions it
 * refreshes a token that is about to expire before sending, and once more if
 * the server still says UNAUTHORIZED (expired token) — then retries the call.
 * Any other 401 (revoked session, wrong password, bad signature) passes through.
 * Calls that set their own Authorization header (sign-out) are left alone.
 */
public final class AuthInterceptor implements Interceptor {

    private static final long EARLY_REFRESH_SECONDS = 60;

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request req = chain.request();
        if (req.header("Authorization") != null) return chain.proceed(req);

        SessionManager s = SessionManager.get();
        if (s.isRefreshable() && s.accessExpiresWithin(EARLY_REFRESH_SECONDS)) {
            TokenRefresher.refresh(null);
        }
        String token = s.token();
        if (token == null) return chain.proceed(req);

        Response response = chain.proceed(withBearer(req, token));
        if (response.code() != 401 || !s.isRefreshable()) return response;
        if (!ErrorCode.UNAUTHORIZED.equals(errorCode(response))) return response;
        if (!TokenRefresher.refresh(token)) return response;
        String fresh = s.token();
        if (fresh == null || fresh.equals(token)) return response;
        response.close();
        return chain.proceed(withBearer(req, fresh));
    }

    private static Request withBearer(Request r, String token) {
        return r.newBuilder().header("Authorization", "Bearer " + token).build();
    }

    private static String errorCode(Response response) {
        try {
            JsonElement root = JsonParser.parseString(response.peekBody(8192).string());
            return root.getAsJsonObject().getAsJsonObject("error").get("code").getAsString();
        } catch (Exception e) {
            return "";
        }
    }
}
