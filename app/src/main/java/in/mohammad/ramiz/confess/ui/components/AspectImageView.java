package in.mohammad.ramiz.confess.ui.components;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.imageview.ShapeableImageView;

/**
 * An image that keeps a shape (height ÷ width) before it has loaded, so a GIF's space is
 * reserved and nothing jumps when it arrives. Very tall GIFs are capped ({@link #setMaxRatio}).
 */
public class AspectImageView extends ShapeableImageView {

    private float ratio = 1f;
    private float maxRatio = 1.25f;

    public AspectImageView(@NonNull Context context) {
        super(context);
    }

    public AspectImageView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public AspectImageView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setRatio(float heightOverWidth) {
        float r = heightOverWidth > 0 ? heightOverWidth : 1f;
        if (r != ratio) {
            ratio = r;
            requestLayout();
        }
    }

    public void setMaxRatio(float max) {
        maxRatio = max;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        int h = Math.round(w * Math.min(ratio, maxRatio));
        setMeasuredDimension(w, h);
    }
}
