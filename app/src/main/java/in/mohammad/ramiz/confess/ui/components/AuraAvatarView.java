package in.mohammad.ramiz.confess.ui.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import java.util.Random;

import in.mohammad.ramiz.confess.R;

/**
 * Circular generative avatar ("aura"). Usage:
 *
 * <pre>
 * &lt;in.mohammad.ramiz.confess.ui.components.AuraAvatarView
 *     android:layout_width="@dimen/avatar_sm"
 *     android:layout_height="@dimen/avatar_sm"
 *     app:aura="aura_04"
 *     app:auraSelected="false" /&gt;
 * </pre>
 *
 * In code: {@code avatarView.setAvatar(post.author.avatar)}.
 * An https value is an uploaded photo ({server}/media/avatars/<id>): it is
 * loaded with Glide (thumb or medium, by size) and drawn in the same circle;
 * a stable aura from the URL's hash shows while it loads or if it fails.
 */
public class AuraAvatarView extends View {

    private static final float RING_GAP_DP = 3f;
    private static final float RING_WIDTH_DP = 2f;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlight = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grain = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();

    private Aura aura = Aura.AURA_04;
    private boolean selectedRing = false;
    private float[] grainPoints = new float[0];
    private int[] grainAlphas = new int[0];

    private float cx, cy, r;
    private final float density;

    // Uploaded photo
    private final Paint photoPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Matrix photoMatrix = new Matrix();
    @Nullable private String photoUrl;
    @Nullable private Bitmap photo;
    @Nullable private CustomTarget<Bitmap> photoTarget;

    public AuraAvatarView(Context context) {
        this(context, null);
    }

    public AuraAvatarView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AuraAvatarView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        density = getResources().getDisplayMetrics().density;

        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Math.max(1f, density * 0.75f));
        rim.setColor(Color.argb(28, 255, 255, 255));

        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(RING_WIDTH_DP * density);
        ring.setColor(ContextCompat.getColor(context, R.color.amber));

        grain.setStyle(Paint.Style.FILL);

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.AuraAvatarView, defStyleAttr, 0);
            try {
                String id = a.getString(R.styleable.AuraAvatarView_aura);
                if (id != null) aura = Aura.resolve(id);
                selectedRing = a.getBoolean(R.styleable.AuraAvatarView_auraSelected, false);
            } finally {
                a.recycle();
            }
        }
        setContentDescription(aura.displayName);
    }

    // ── Public API ──────────────────────────────────────────────────────────

    /** Server avatar value: "aura_01"…"aura_12" (or anything — falls back by hash). */
    public void setAvatar(@Nullable String value) {
        setAura(Aura.resolve(value));
        String url = value != null && value.startsWith("https://") ? value.trim() : null;
        setPhoto(url);
    }

    /** Show a photo picked on this phone (Edit profile preview, before upload). */
    public void setLocalPhoto(@Nullable Bitmap bitmap) {
        cancelPhoto();
        photoUrl = null;
        photo = bitmap;
        updatePhotoShader();
        invalidate();
    }

    public boolean hasPhoto() {
        return photo != null || photoUrl != null;
    }

    private void setPhoto(@Nullable String url) {
        if (url != null && url.equals(photoUrl)) return;     // recycled row, same image
        cancelPhoto();
        photoUrl = url;
        photo = null;
        updatePhotoShader();
        invalidate();
        if (url == null) return;
        // Small avatars use the thumb variant; profile-size ones use medium.
        float dp = Math.max(getWidth(), getLayoutParams() != null ? getLayoutParams().width : 0) / density;
        String variant = dp > 0 && dp <= 64 ? "thumb" : "medium";
        String full = url + (url.contains("?") ? "&" : "?") + "variant=" + variant;
        photoTarget = new CustomTarget<Bitmap>() {
            @Override
            public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> t) {
                if (!url.equals(photoUrl)) return;
                photo = resource;
                updatePhotoShader();
                invalidate();
            }

            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {
                photo = null;
                updatePhotoShader();
                invalidate();
            }
        };
        try {
            Glide.with(this).asBitmap().load(full).into(photoTarget);
        } catch (IllegalArgumentException ignored) {
            // Destroyed activity: keep the aura.
        }
    }

    private void cancelPhoto() {
        if (photoTarget != null) {
            try {
                Glide.with(this).clear(photoTarget);
            } catch (IllegalArgumentException ignored) {
                // Destroyed activity.
            }
            photoTarget = null;
        }
    }

    private void updatePhotoShader() {
        if (photo == null || r <= 0) {
            photoPaint.setShader(null);
            return;
        }
        BitmapShader shader = new BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        float d = r * 2f;
        float scale = Math.max(d / photo.getWidth(), d / photo.getHeight());      // centre-crop
        photoMatrix.reset();
        photoMatrix.setScale(scale, scale);
        photoMatrix.postTranslate(cx - photo.getWidth() * scale / 2f, cy - photo.getHeight() * scale / 2f);
        shader.setLocalMatrix(photoMatrix);
        photoPaint.setShader(shader);
    }

    public void setAura(@NonNull Aura aura) {
        if (this.aura == aura) return;
        this.aura = aura;
        setContentDescription(aura.displayName);
        rebuild();
        invalidate();
    }

    @NonNull
    public Aura getAura() {
        return aura;
    }

    /** Amber selection ring (avatar picker). */
    public void setAuraSelected(boolean selected) {
        if (selectedRing == selected) return;
        selectedRing = selected;
        rebuild();
        invalidate();
    }

    public boolean isAuraSelected() {
        return selectedRing;
    }

    // ── Layout + drawing ────────────────────────────────────────────────────

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Always square: the smaller of the two measured sides.
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int size = Math.min(getMeasuredWidth(), getMeasuredHeight());
        if (size == 0) size = Math.max(getMeasuredWidth(), getMeasuredHeight());
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rebuild();
    }

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        super.setPadding(left, top, right, bottom);
        rebuild();
        invalidate();
    }

    private void rebuild() {
        // View's constructor can trigger padding/size callbacks before our fields exist.
        if (fill == null || aura == null) return;
        int w = getWidth() - getPaddingLeft() - getPaddingRight();
        int h = getHeight() - getPaddingTop() - getPaddingBottom();
        if (w <= 0 || h <= 0) return;
        cx = getPaddingLeft() + w / 2f;
        cy = getPaddingTop() + h / 2f;
        float outer = Math.min(w, h) / 2f;
        r = selectedRing ? outer - (RING_GAP_DP + RING_WIDTH_DP) * density : outer;

        float fx = cx + aura.focusX * r;
        float fy = cy + aura.focusY * r;
        fill.setShader(new RadialGradient(fx, fy, r * 1.15f,
                new int[]{aura.glow, aura.mid, aura.base},
                new float[]{0f, 0.36f, 1f},
                Shader.TileMode.CLAMP));

        highlight.setShader(new RadialGradient(cx - r * 0.35f, cy - r * 0.42f, r * 0.7f,
                new int[]{Color.argb(40, 255, 255, 255), Color.argb(0, 255, 255, 255)},
                null, Shader.TileMode.CLAMP));

        clip.reset();
        clip.addCircle(cx, cy, r, Path.Direction.CW);
        updatePhotoShader();

        // Film grain: deterministic per aura so it never "shimmers" between frames.
        Random rnd = new Random(aura.id.hashCode() * 31L + (long) r);
        int count = Math.max(24, (int) (r * r / (9f * density)));
        grainPoints = new float[count * 2];
        grainAlphas = new int[count];
        for (int i = 0; i < count; i++) {
            double ang = rnd.nextDouble() * Math.PI * 2;
            double dist = Math.sqrt(rnd.nextDouble()) * r;
            grainPoints[i * 2] = cx + (float) (Math.cos(ang) * dist);
            grainPoints[i * 2 + 1] = cy + (float) (Math.sin(ang) * dist);
            grainAlphas[i] = 10 + rnd.nextInt(26);
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (r <= 0) return;

        if (photo != null && photoPaint.getShader() != null) {
            canvas.drawCircle(cx, cy, r, photoPaint);
            canvas.drawCircle(cx, cy, r - rim.getStrokeWidth() / 2f, rim);
            if (selectedRing) {
                float ringR = r + (RING_GAP_DP + RING_WIDTH_DP / 2f) * density;
                canvas.drawCircle(cx, cy, ringR, ring);
            }
            return;
        }

        canvas.drawCircle(cx, cy, r, fill);

        int save = canvas.save();
        canvas.clipPath(clip);
        canvas.drawCircle(cx, cy, r, highlight);
        float dot = Math.max(1f, density * 0.6f);
        for (int i = 0; i < grainAlphas.length; i++) {
            grain.setColor(i % 2 == 0
                    ? Color.argb(grainAlphas[i], 255, 255, 255)
                    : Color.argb(grainAlphas[i], 0, 0, 0));
            canvas.drawCircle(grainPoints[i * 2], grainPoints[i * 2 + 1], dot, grain);
        }
        canvas.restoreToCount(save);

        canvas.drawCircle(cx, cy, r - rim.getStrokeWidth() / 2f, rim);

        if (selectedRing) {
            float ringR = r + (RING_GAP_DP + RING_WIDTH_DP / 2f) * density;
            canvas.drawCircle(cx, cy, ringR, ring);
        }
    }
}
