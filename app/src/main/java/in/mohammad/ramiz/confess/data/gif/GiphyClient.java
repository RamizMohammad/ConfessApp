package in.mohammad.ramiz.confess.data.gif;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import in.mohammad.ramiz.confess.BuildConfig;
import in.mohammad.ramiz.confess.data.model.Gif;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * GIPHY search for the GIF picker — the app talks to GIPHY directly (GIPHY_API_KEY in
 * secrets.properties); our server only ever sees the chosen GIF's id (and re-checks its rating).
 *
 * Always rating=pg-13 (G, PG and PG-13 only).
 */
public final class GiphyClient {

    public interface Callback {
        /** Main thread. {@code more}: there's another page at {@code nextOffset}. */
        void onResult(@NonNull List<Gif> gifs, int nextOffset, boolean more);

        void onError();
    }

    public static final int PAGE = 24;
    private static final HttpUrl BASE = HttpUrl.get("https://api.giphy.com/v1/gifs/");
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile OkHttpClient http;

    private GiphyClient() { }

    /** A key is set in secrets.properties. */
    public static boolean configured() {
        return BuildConfig.GIPHY_API_KEY != null && !BuildConfig.GIPHY_API_KEY.trim().isEmpty();
    }

    /** Lower case, single spaces. */
    @NonNull
    public static String normalise(@Nullable String q) {
        return q == null ? "" : q.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** Trending GIFs (query empty) or a search. Cancel the returned call when it's no longer wanted. */
    @NonNull
    public static Call load(@Nullable String query, int offset, @NonNull Callback cb) {
        String q = normalise(query);
        HttpUrl.Builder url = BASE.newBuilder()
                .addPathSegment(q.isEmpty() ? "trending" : "search")
                .addQueryParameter("api_key", BuildConfig.GIPHY_API_KEY.trim())
                .addQueryParameter("limit", String.valueOf(PAGE))
                .addQueryParameter("offset", String.valueOf(offset))
                .addQueryParameter("rating", "pg-13")
                .addQueryParameter("bundle", "messaging_non_clips");
        if (!q.isEmpty()) url.addQueryParameter("q", q).addQueryParameter("lang", "en");
        Call call = client().newCall(new Request.Builder().url(url.build()).get().build());
        call.enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull Call c, @NonNull IOException e) {
                if (!c.isCanceled()) MAIN.post(cb::onError);
            }

            @Override
            public void onResponse(@NonNull Call c, @NonNull Response r) {
                try (Response res = r) {
                    if (!res.isSuccessful() || res.body() == null) {
                        if (!c.isCanceled()) MAIN.post(cb::onError);
                        return;
                    }
                    JsonObject root = JsonParser.parseString(res.body().string()).getAsJsonObject();
                    List<Gif> out = new ArrayList<>();
                    JsonArray data = root.has("data") && root.get("data").isJsonArray() ? root.getAsJsonArray("data") : new JsonArray();
                    for (JsonElement el : data) {
                        Gif g = parse(el.getAsJsonObject());
                        if (g != null) out.add(g);
                    }
                    JsonObject pg = root.has("pagination") ? root.getAsJsonObject("pagination") : new JsonObject();
                    int count = pg.has("count") ? pg.get("count").getAsInt() : data.size();
                    int total = pg.has("total_count") ? pg.get("total_count").getAsInt() : offset + count;
                    int next = offset + count;
                    boolean more = count > 0 && next < total;
                    if (!c.isCanceled()) MAIN.post(() -> cb.onResult(out, next, more));
                } catch (Exception e) {
                    if (!c.isCanceled()) MAIN.post(cb::onError);
                }
            }
        });
        return call;
    }

    @Nullable
    private static Gif parse(@NonNull JsonObject o) {
        try {
            String id = o.get("id").getAsString();
            JsonObject images = o.getAsJsonObject("images");
            JsonObject original = images.getAsJsonObject("original");
            JsonObject small = images.getAsJsonObject("fixed_width");
            JsonObject still = images.has("fixed_width_still") ? images.getAsJsonObject("fixed_width_still") : null;
            int w = original.get("width").getAsInt();
            int h = original.get("height").getAsInt();
            String full = original.has("webp") ? original.get("webp").getAsString() : original.get("url").getAsString();
            String preview = small.has("webp") ? small.get("webp").getAsString() : small.get("url").getAsString();
            String stillUrl = still != null && still.has("url") ? still.get("url").getAsString() : null;
            if (!id.matches("^[A-Za-z0-9]{5,40}$")) return null;       // the server takes nothing else
            return new Gif(id, w, h, full, preview, stillUrl);
        } catch (Exception e) {
            return null;
        }
    }

    private static OkHttpClient client() {
        if (http == null) {
            synchronized (GiphyClient.class) {
                if (http == null) {
                    http = new OkHttpClient.Builder()
                            .connectTimeout(8, TimeUnit.SECONDS)
                            .readTimeout(10, TimeUnit.SECONDS)
                            .callTimeout(15, TimeUnit.SECONDS)
                            .build();
                }
            }
        }
        return http;
    }
}
