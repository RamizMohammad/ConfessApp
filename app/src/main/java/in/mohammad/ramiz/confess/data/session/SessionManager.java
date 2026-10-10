package in.mohammad.ramiz.confess.data.session;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;

import in.mohammad.ramiz.confess.data.api.ApiClient;
import in.mohammad.ramiz.confess.data.model.AuthResponse;
import in.mohammad.ramiz.confess.data.model.Me;
import in.mohammad.ramiz.confess.data.security.RequestSigner;
import in.mohammad.ramiz.confess.data.security.SecureStore;

/**
 * The signed-in session on this device (API_V2.md, "Security protocol"):
 *   - access token (15 min), rotating refresh token, session id, request-signing key
 *   - a cached copy of {@link Me} so the app can draw instantly on cold start
 *   - the in-memory "unlocked" flag for the password lock (resets on process death)
 *
 * Everything is stored through {@link SecureStore} (AES-GCM, Android Keystore key),
 * in a prefs file excluded from all backups. Sessions from older app versions
 * (one long plain-text token) are moved into the encrypted store once; the
 * launcher then upgrades them to a refreshable session with a quiet re-sign-in.
 */
public final class SessionManager {

    private static final String SECURE_FILE = "confess_session_v3";
    private static final String LEGACY_FILE = "confess_session_v2";
    private static final String K_ACCESS = "access";
    private static final String K_REFRESH = "refresh";
    private static final String K_SID = "sid";
    private static final String K_SIGKEY = "sigkey";
    private static final String K_ME = "me";
    private static final String K_RESP_SIG = "resp_sig_seen";

    private static volatile SessionManager instance;

    private final SecureStore store;
    private volatile boolean unlocked = false;

    // Decrypted copies — the network layer reads these on every request.
    @Nullable private volatile String access;
    @Nullable private volatile String refresh;
    @Nullable private volatile String sid;
    @Nullable private volatile byte[] signingKey;
    private volatile boolean respSigSeen;

    private SessionManager(Context context) {
        store = new SecureStore(context, SECURE_FILE);
        migrateLegacy(context);
        access = store.get(K_ACCESS);
        refresh = store.get(K_REFRESH);
        sid = store.get(K_SID);
        String k = store.get(K_SIGKEY);
        signingKey = k == null ? null : decodeKey(k);
        respSigSeen = "1".equals(store.get(K_RESP_SIG));
        if (refresh != null && (sid == null || signingKey == null)) clear();   // half-written session
    }

    public static void init(Context context) {
        if (instance == null) {
            synchronized (SessionManager.class) {
                if (instance == null) instance = new SessionManager(context);
            }
        }
    }

    public static SessionManager get() {
        if (instance == null) throw new IllegalStateException("SessionManager.init() not called");
        return instance;
    }

    // ── Tokens ───────────────────────────────────────────────────────────────

    @Nullable public String token() { return access; }
    @Nullable public String refreshToken() { return refresh; }
    @Nullable public String sessionId() { return sid; }
    @Nullable public byte[] signingKey() { return signingKey; }

    /** A refreshable (v2) session — short access tokens, signed requests. */
    public boolean isRefreshable() {
        return refresh != null && sid != null && signingKey != null;
    }

    /** True while the user has a usable session (a refresh token, or an unexpired old-style token). */
    public boolean isSignedIn() {
        if (isRefreshable()) return true;
        String t = access;
        return t != null && !isExpired(t, 0);
    }

    /** Signed in with an old long-lived token — the launcher swaps it for a refreshable session. */
    public boolean needsUpgrade() {
        return access != null && !isRefreshable();
    }

    /** True when the access token is missing or expires within {@code withinSeconds}. */
    public boolean accessExpiresWithin(long withinSeconds) {
        String t = access;
        return t == null || isExpired(t, withinSeconds);
    }

    /** Save a fresh login / registration. */
    public synchronized void start(@NonNull AuthResponse auth) {
        store.clear();
        respSigSeen = false;
        byte[] key = auth.signingKey == null ? null : decodeKey(auth.signingKey);
        boolean v2 = auth.refreshToken != null && auth.sessionId != null && key != null;
        access = auth.token;
        refresh = v2 ? auth.refreshToken : null;
        sid = v2 ? auth.sessionId : null;
        signingKey = v2 ? key : null;
        store.put(K_ACCESS, access);
        store.put(K_REFRESH, refresh);
        store.put(K_SID, sid);
        store.put(K_SIGKEY, v2 ? auth.signingKey : null);
        if (auth.user != null) store.put(K_ME, ApiClient.gson().toJson(auth.user));
        // A password-locked account still asks for its password after Google sign-in
        // (the Google account may be shared on this phone).
        unlocked = auth.user == null || !auth.user.hasPassword;
    }

    /** New access + refresh token after a rotation (the session id and key stay). */
    public synchronized void rotate(@NonNull String newAccess, @NonNull String newRefresh) {
        if (!isRefreshable()) return;                  // signed out meanwhile
        access = newAccess;
        refresh = newRefresh;
        store.put(K_ACCESS, newAccess);
        store.put(K_REFRESH, newRefresh);
    }

    public synchronized void clear() {
        store.clear();
        access = null;
        refresh = null;
        sid = null;
        signingKey = null;
        respSigSeen = false;
        unlocked = false;
    }

    /** Copy of the credentials, for calls that must still go out after {@link #clear()} (sign-out). */
    @Nullable
    public SessionSnapshot snapshot() {
        String t = access;
        if (t == null) return null;
        return new SessionSnapshot(t, sid, signingKey);
    }

    // ── Response signatures ──────────────────────────────────────────────────

    /** Once the server has signed a response in this session, unsigned answers are refused. */
    public boolean responseSignaturesSeen() { return respSigSeen; }

    public void markResponseSignatureSeen() {
        if (respSigSeen) return;
        respSigSeen = true;
        store.put(K_RESP_SIG, "1");
    }

    // ── Cached profile ───────────────────────────────────────────────────────

    @Nullable
    public Me cachedMe() {
        String json = store.get(K_ME);
        if (json == null) return null;
        try {
            return ApiClient.gson().fromJson(json, Me.class);
        } catch (Exception e) {
            return null;
        }
    }

    public void cacheMe(Me me) {
        if (access == null) return;                    // don't resurrect a signed-out profile
        store.put(K_ME, ApiClient.gson().toJson(me));
    }

    // ── Password lock ────────────────────────────────────────────────────────

    /** Does this account need the unlock screen before showing content? */
    public boolean needsUnlock() {
        Me me = cachedMe();
        return me != null && me.hasPassword && !unlocked;
    }

    public void markUnlocked() { unlocked = true; }

    public void lock() { unlocked = false; }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Older versions kept the token in plain prefs: move it into the encrypted store, wipe the old file. */
    private void migrateLegacy(Context context) {
        SharedPreferences old = context.getSharedPreferences(LEGACY_FILE, Context.MODE_PRIVATE);
        String jwt = old.getString("jwt", null);
        String me = old.getString("me_json", null);
        if (jwt != null && store.get(K_ACCESS) == null) {
            store.put(K_ACCESS, jwt);
            if (me != null) store.put(K_ME, me);
        }
        if (jwt != null || me != null) old.edit().clear().commit();
        context.deleteSharedPreferences(LEGACY_FILE);
    }

    @Nullable
    private static byte[] decodeKey(String b64url) {
        try {
            byte[] k = RequestSigner.b64urlDecode(b64url);
            return k.length >= 32 ? k : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Reads `exp` from the JWT payload (no signature check — the server does that). */
    public static boolean isExpired(String jwt, long skewSeconds) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) return true;
            byte[] decoded = Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            JsonObject payload = JsonParser.parseString(new String(decoded, StandardCharsets.UTF_8)).getAsJsonObject();
            long exp = payload.get("exp").getAsLong();
            return System.currentTimeMillis() / 1000L + skewSeconds >= exp;
        } catch (Exception e) {
            return true;
        }
    }
}
