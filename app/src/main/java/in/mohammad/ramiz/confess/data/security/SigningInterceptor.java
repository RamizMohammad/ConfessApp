package in.mohammad.ramiz.confess.data.security;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;

import in.mohammad.ramiz.confess.data.session.SessionManager;
import in.mohammad.ramiz.confess.data.session.SessionSnapshot;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

/**
 * Signs every call of a refreshable session and verifies the server's answer
 * (API_V2.md, "Request signing"):
 *
 *   X-Sig-Ts / X-Sig-Nonce / X-Sig  — HMAC over method, path+query, time, nonce,
 *                                     body hash and session id (fresh nonce per call;
 *                                     the server rejects reuse, so replays fail)
 *   X-Resp-Sig                      — HMAC over nonce, status and body hash. A wrong
 *                                     signature, or a missing one once the server has
 *                                     been signing, means the answer was altered on
 *                                     the way: it's dropped (TamperedResponseException).
 *
 * The routes the server answers without a session are not signed (UNSIGNED).
 * Sign-out calls carry a {@link SessionSnapshot} tag and are signed with it.
 */
public final class SigningInterceptor implements Interceptor {

    /** Server routes that run before / without a session: it never signs their answers. */
    private static final String[] UNSIGNED = {
            "/auth/login", "/auth/register", "/auth/integrity-nonce", "/auth/reset-password",
            "/users/alias-available",
    };

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request original = chain.request();
        SessionSnapshot snap = original.tag(SessionSnapshot.class);
        SessionManager session = SessionManager.get();
        byte[] key = snap != null ? snap.signingKey : session.signingKey();
        String sid = snap != null ? snap.sessionId : session.sessionId();
        boolean strict = snap == null && session.responseSignaturesSeen();

        if (key == null || sid == null || isUnsigned(original)) {
            Response r = chain.proceed(original);
            ServerClock.observe(r);
            return r;
        }

        byte[] body = bodyBytes(original.body());
        String bodySha = RequestSigner.sha256Hex(body);
        Request rebuilt = original.body() == null ? original
                : original.newBuilder().method(original.method(), RequestBody.create(body, contentType(original.body()))).build();

        String nonce = RequestSigner.newNonce();
        Response response = chain.proceed(sign(rebuilt, key, sid, nonce, bodySha));
        boolean clockMoved = ServerClock.observe(response);

        // Wrong phone clock: the first signed call fails as stale. Retry once on the server's time.
        if (clockMoved && response.code() == 401) {
            response.close();
            nonce = RequestSigner.newNonce();
            response = chain.proceed(sign(rebuilt, key, sid, nonce, bodySha));
            ServerClock.observe(response);
        }
        return verify(response, key, nonce, strict, snap == null ? session : null);
    }

    private static Request sign(Request r, byte[] key, String sid, String nonce, String bodySha) {
        long ts = ServerClock.nowSeconds();
        String path = RequestSigner.canonicalPath(r.url().encodedPath(), r.url().encodedQuery());
        String sig = RequestSigner.requestSignature(key, r.method(), path, ts, nonce, bodySha, sid);
        return r.newBuilder()
                .header("X-Sig-Ts", String.valueOf(ts))
                .header("X-Sig-Nonce", nonce)
                .header("X-Sig", sig)
                .build();
    }

    private static Response verify(Response response, byte[] key, String nonce, boolean strict,
                                   @Nullable SessionManager session) throws IOException {
        String sig = response.header("X-Resp-Sig");
        if (sig == null) {
            if (strict && response.isSuccessful()) {
                response.close();
                throw new TamperedResponseException("Unsigned response");
            }
            return response;
        }
        ResponseBody rb = response.body();
        MediaType type = rb == null ? null : rb.contentType();
        byte[] bytes = rb == null ? new byte[0] : rb.bytes();          // already un-gzipped here
        String expected = RequestSigner.responseSignature(key, nonce, response.code(), RequestSigner.sha256Hex(bytes));
        if (!RequestSigner.same(expected, sig)) {
            throw new TamperedResponseException("Response signature mismatch");
        }
        if (session != null) session.markResponseSignatureSeen();
        return response.newBuilder().body(ResponseBody.create(bytes, type)).build();
    }

    private static boolean isUnsigned(Request r) {
        String p = r.url().encodedPath();
        for (String s : UNSIGNED) if (p.endsWith(s)) return true;
        return false;
    }

    private static byte[] bodyBytes(@Nullable RequestBody body) throws IOException {
        if (body == null) return new byte[0];
        Buffer buf = new Buffer();
        body.writeTo(buf);
        return buf.readByteArray();
    }

    @Nullable
    private static MediaType contentType(@Nullable RequestBody body) {
        return body == null ? null : body.contentType();
    }
}
