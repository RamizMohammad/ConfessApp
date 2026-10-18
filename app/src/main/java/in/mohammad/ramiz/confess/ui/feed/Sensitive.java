package in.mohammad.ramiz.confess.ui.feed;

import android.animation.ValueAnimator;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashSet;
import java.util.Set;

import in.mohammad.ramiz.confess.data.model.Post;

/**
 * Sensitive confessions (the server scored them at or above its threshold) are blurred until
 * the reader taps "tap to read" — on every card that shows them. What's been opened stays open
 * for the rest of the session (the feed, the slider and the confession screen agree). The writer
 * always sees their own clearly, with a note that others see it blurred.
 */
public final class Sensitive {

    private static final Set<String> revealed = new HashSet<>();
    static final float BLUR_DP = 9f;

    private Sensitive() { }

    /** Blurred for me right now? */
    public static boolean hidden(@NonNull Post p) {
        return p.sensitive && !p.isMine && p.postId != null && !revealed.contains(p.postId);
    }

    public static void reveal(@NonNull Post p) {
        if (p.postId != null) revealed.add(p.postId);
    }

    /** Blur (or clear) a view; radius in px, 0 = sharp. */
    static void blur(@Nullable View v, float radius) {
        if (v == null) return;
        v.setRenderEffect(radius <= 0.5f ? null : RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP));
    }

    /** The blur melts away. */
    static void unblur(@NonNull View[] views, float from) {
        ValueAnimator a = ValueAnimator.ofFloat(from, 0f);
        a.setDuration(320);
        a.addUpdateListener(x -> {
            float r = (float) x.getAnimatedValue();
            for (View v : views) blur(v, r);
        });
        a.start();
    }
}
