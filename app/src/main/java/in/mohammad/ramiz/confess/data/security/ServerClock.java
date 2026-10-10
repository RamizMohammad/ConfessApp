package in.mohammad.ramiz.confess.data.security;

import java.util.Date;

import okhttp3.Response;

/**
 * Request signatures carry a timestamp the server accepts within ±5 min. Phones
 * with a wrong clock would fail every call, so we track the offset to the
 * server's own clock from the Date header of its answers.
 */
public final class ServerClock {

    private static volatile long offsetSeconds = 0;

    private ServerClock() { }

    public static long nowSeconds() {
        return System.currentTimeMillis() / 1000L + offsetSeconds;
    }

    /** Learn from a response. Returns true when the offset moved enough to matter. */
    public static boolean observe(Response response) {
        Date server = response.headers().getDate("Date");
        if (server == null) return false;
        long diff = server.getTime() / 1000L - System.currentTimeMillis() / 1000L;
        long next = Math.abs(diff) > 30 ? diff : 0;          // ignore latency-sized noise
        boolean moved = Math.abs(next - offsetSeconds) > 30;
        offsetSeconds = next;
        return moved;
    }
}
