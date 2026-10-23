package in.mohammad.ramiz.confess.ui.gif;

import android.animation.ValueAnimator;
import android.graphics.drawable.ColorDrawable;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.data.model.Gif;
import in.mohammad.ramiz.confess.ui.components.AspectImageView;

/** Showing a GIPHY GIF (Glide plays animated WebP / GIF). Phone animations off → the still frame. */
public final class GifViews {

    private GifViews() { }

    /** {@code small}: the 200 px rendition (grid, responses); otherwise the full one (cards). */
    public static void show(@NonNull ImageView view, @Nullable Gif gif, boolean small) {
        if (gif == null) {
            Glide.with(view).clear(view);
            view.setImageDrawable(null);
            return;
        }
        if (view instanceof AspectImageView) ((AspectImageView) view).setRatio(gif.ratio());
        String url = !ValueAnimator.areAnimatorsEnabled() && gif.stillUrl != null ? gif.stillUrl
                : small && gif.previewUrl != null ? gif.previewUrl
                : gif.url != null ? gif.url : gif.previewUrl;
        Glide.with(view)
                .load(url)
                .placeholder(new ColorDrawable(ContextCompat.getColor(view.getContext(), R.color.surface_high)))
                .diskCacheStrategy(DiskCacheStrategy.DATA)
                .into(view);
        view.setContentDescription(view.getContext().getString(R.string.cd_gif));
    }
}
