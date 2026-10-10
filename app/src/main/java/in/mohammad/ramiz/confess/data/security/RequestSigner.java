package in.mohammad.ramiz.confess.data.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Request / response signatures — byte-for-byte the same as the server's
 * SecurityManager.request_signature / response_signature (API_V2.md, "Request signing").
 *
 *   request  = b64url(HMAC-SHA256(key, "v1\n" METHOD "\n" path?query "\n" ts "\n" nonce "\n" sha256hex(body) "\n" sid))
 *   response = b64url(HMAC-SHA256(key, "v1r\n" nonce "\n" status "\n" sha256hex(body)))
 *
 * Pure Java (no Android types) so it can be unit-tested against the server's vectors.
 */
public final class RequestSigner {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private RequestSigner() { }

    /** "/api/…path[?query]" — everything before "/api/" (a proxy base path) is dropped, like the server does. */
    public static String canonicalPath(String encodedPath, String encodedQuery) {
        int i = encodedPath.indexOf("/api/");
        String path = i >= 0 ? encodedPath.substring(i) : encodedPath;
        return encodedQuery == null || encodedQuery.isEmpty() ? path : path + "?" + encodedQuery;
    }

    public static String requestSignature(byte[] key, String method, String canonicalPath, long ts,
                                          String nonce, String bodySha256Hex, String sessionId) {
        String msg = "v1\n" + method.toUpperCase(java.util.Locale.ROOT) + "\n" + canonicalPath + "\n" + ts + "\n"
                + nonce + "\n" + bodySha256Hex + "\n" + sessionId;
        return b64url(hmac(key, msg));
    }

    public static String responseSignature(byte[] key, String nonce, int status, String bodySha256Hex) {
        return b64url(hmac(key, "v1r\n" + nonce + "\n" + status + "\n" + bodySha256Hex));
    }

    /** 24 random bytes → 32 url-safe chars (server accepts 16–64 of [A-Za-z0-9_-]). */
    public static String newNonce() {
        byte[] b = new byte[24];
        RANDOM.nextBytes(b);
        return b64url(b);
    }

    public static String sha256Hex(byte[] data) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(data);
            char[] out = new char[d.length * 2];
            for (int i = 0; i < d.length; i++) {
                out[i * 2] = HEX[(d[i] >> 4) & 0xF];
                out[i * 2 + 1] = HEX[d[i] & 0xF];
            }
            return new String(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Constant-time compare of two signatures. */
    public static boolean same(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII), b.getBytes(StandardCharsets.US_ASCII));
    }

    public static String b64url(byte[] raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    public static byte[] b64urlDecode(String s) {
        return Base64.getUrlDecoder().decode(s);
    }

    private static byte[] hmac(byte[] key, String msg) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(msg.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
