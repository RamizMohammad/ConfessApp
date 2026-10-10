package in.mohammad.ramiz.confess.data.model;

import androidx.annotation.Nullable;

/** POST auth/login, auth/register and auth/refresh. */
public class AuthResponse {
    public String token;
    @Nullable public Me user;                 // not on refresh
    // Present when the request carried `client` (refreshable sessions):
    @Nullable public String refreshToken;
    public long expiresIn;
    @Nullable public String sessionId;
    @Nullable public String signingKey;       // base64url, login/register only
}
