package in.mohammad.ramiz.confess.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A GIPHY GIF — the only media on Confess. The server stores just the id (and size) and sends
 * the links back; the picker builds the same object from GIPHY's search results.
 */
public class Gif {
    public String id;
    public int width;
    public int height;
    /** Full size (giphy.webp) — cards and the confession screen. */
    @Nullable public String url;
    /** 200 px wide (200w.webp) — picker grid, responses. */
    @Nullable public String previewUrl;
    /** First frame — when the phone's animations are off. */
    @Nullable public String stillUrl;

    public Gif() { }

    public Gif(@NonNull String id, int width, int height, @Nullable String url, @Nullable String previewUrl,
               @Nullable String stillUrl) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.url = url;
        this.previewUrl = previewUrl;
        this.stillUrl = stillUrl;
    }

    /** Height ÷ width (1 when unknown). */
    public float ratio() {
        return width > 0 && height > 0 ? (float) height / width : 1f;
    }

    /** What POST /posts and POST …/comments take. */
    public static class In {
        public final String id;
        public final int width;
        public final int height;

        public In(@NonNull Gif g) {
            id = g.id;
            width = Math.max(1, g.width);
            height = Math.max(1, g.height);
        }
    }
}
