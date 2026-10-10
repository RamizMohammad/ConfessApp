package in.mohammad.ramiz.confess.data.model;

import androidx.annotation.Nullable;

/**
 * POST auth/register. password == null → no password lock (biometrics are then ignored).
 * alias: 3–15 of A–Z a–z 0–9 _ · avatar: aura_01…aura_12 · about: ≤ 50 chars.
 */
public class RegisterRequest {
    public final String idToken;
    public final String alias;
    public final String avatar;
    public final String about;
    @Nullable public final String password;
    public final boolean biometricEnabled;
    /** Filled in by AuthRepository just before sending (integrity verdict + session kind). */
    @Nullable public ClientInfo client;

    public RegisterRequest(String idToken, String alias, String avatar, String about,
                           @Nullable String password, boolean biometricEnabled) {
        this.idToken = idToken;
        this.alias = alias;
        this.avatar = avatar;
        this.about = about;
        this.password = password;
        this.biometricEnabled = biometricEnabled;
    }
}
