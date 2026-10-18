package in.mohammad.ramiz.confess.ui.books;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.ColorUtils;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.data.model.Book;
import in.mohammad.ramiz.confess.ui.stories.StoryStyles;

/**
 * A book cover drawn in code, like a printed paperback: the story palette (cover 0–4) as a
 * soft wash, a darker spine on the left, an inset frame, the title set in Newsreader and the
 * writer's alias at the foot. Always 2:3 (height follows width unless both are exact).
 * Text sizes scale with the width, so the same view works from a 56dp picker to the book page.
 */
public class BookCoverView extends View {

    private int cover = 0;
    @NonNull private String title = "";
    @NonNull private String alias = "";

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint metaPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    @Nullable private StaticLayout titleLayout;
    private float radius;

    public BookCoverView(Context context) {
        this(context, null);
    }

    public BookCoverView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        Typeface serif = ResourcesCompat.getFont(context, R.font.newsreader_headline);
        Typeface mono = ResourcesCompat.getFont(context, R.font.jetbrainsmono);
        titlePaint.setTypeface(serif);
        metaPaint.setTypeface(mono);
        metaPaint.setLetterSpacing(0.12f);
        line.setStyle(Paint.Style.STROKE);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    public void bind(@NonNull Book book) {
        set(book.cover, book.title, book.author != null ? book.author.alias : "");
    }

    public void set(int cover, @Nullable String title, @Nullable String alias) {
        this.cover = cover;
        this.title = title == null ? "" : title.trim();
        this.alias = alias == null ? "" : alias;
        setContentDescription(this.title);
        titleLayout = null;
        invalidate();
    }

    public void setCover(int cover) {
        this.cover = cover;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        if (MeasureSpec.getMode(heightSpec) == MeasureSpec.EXACTLY && MeasureSpec.getMode(widthSpec) != MeasureSpec.EXACTLY) {
            int h = MeasureSpec.getSize(heightSpec);
            setMeasuredDimension(Math.round(h / 1.5f), h);
            return;
        }
        setMeasuredDimension(w, Math.round(w * 1.5f));
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        radius = w * 0.035f;
        titleLayout = null;
        invalidateOutline();
    }

    @Override
    protected void onDraw(@NonNull Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        int base = StoryStyles.background(getContext(), cover);
        int fg = StoryStyles.text(getContext(), cover);

        // Paper wash, lighter top-left → deeper bottom-right.
        fill.setShader(new LinearGradient(0, 0, w, h,
                new int[]{ColorUtils.blendARGB(base, 0xFFFFFFFF, 0.12f), base, ColorUtils.blendARGB(base, 0xFF000000, 0.25f)},
                null, Shader.TileMode.CLAMP));
        rect.set(0, 0, w, h);
        c.drawRoundRect(rect, radius, radius, fill);

        // Spine: a darker band with a thin light crease, like the hinge of a paperback.
        float sw = w * 0.075f;
        spine.setShader(new LinearGradient(0, 0, sw, 0,
                ColorUtils.setAlphaComponent(0xFF000000, 70), ColorUtils.setAlphaComponent(0xFF000000, 20),
                Shader.TileMode.CLAMP));
        c.drawRect(0, 0, sw, h, spine);
        line.setStrokeWidth(Math.max(1f, w * 0.006f));
        line.setColor(ColorUtils.setAlphaComponent(0xFFFFFFFF, 45));
        c.drawLine(sw, 0, sw, h, line);

        // Inset frame.
        float l = w * 0.15f, r = w * 0.92f, t = h * 0.055f, b = h * 0.945f;
        line.setColor(ColorUtils.setAlphaComponent(fg, 64));
        line.setStrokeWidth(Math.max(1f, w * 0.005f));
        rect.set(l, t, r, b);
        c.drawRoundRect(rect, w * 0.012f, w * 0.012f, line);

        float cx = (l + r) / 2f;
        // Imprint at the head.
        metaPaint.setColor(ColorUtils.setAlphaComponent(fg, 150));
        metaPaint.setTextSize(Math.max(6f, w * 0.045f));
        metaPaint.setTextAlign(Paint.Align.CENTER);
        c.drawText("CONFESS", cx, t + h * 0.075f, metaPaint);

        // Title, auto-sized to fit the middle of the cover.
        int textWidth = Math.max(1, Math.round((r - l) * 0.84f));
        if (titleLayout == null || titleLayout.getWidth() != textWidth) titleLayout = layoutTitle(textWidth, h * 0.46f, fg);
        float ty = h * 0.43f - titleLayout.getHeight() / 2f;
        c.save();
        c.translate(cx - textWidth / 2f, ty);
        titleLayout.draw(c);
        c.restore();

        // A short rule under the title.
        float ry = ty + titleLayout.getHeight() + h * 0.04f;
        line.setColor(ColorUtils.setAlphaComponent(fg, 120));
        c.drawLine(cx - w * 0.07f, ry, cx + w * 0.07f, ry, line);

        // Writer at the foot.
        if (!alias.isEmpty()) {
            metaPaint.setColor(ColorUtils.setAlphaComponent(fg, 200));
            metaPaint.setTextSize(Math.max(6f, w * 0.052f));
            String a = TextUtils.ellipsize("@" + alias, metaPaint, (r - l) * 0.9f, TextUtils.TruncateAt.END).toString();
            c.drawText(a, cx, b - h * 0.05f, metaPaint);
        }
    }

    @NonNull
    private StaticLayout layoutTitle(int width, float maxHeight, int color) {
        titlePaint.setColor(color);
        CharSequence text = title.isEmpty() ? " " : title;
        float size = width * 0.19f;
        StaticLayout layout;
        while (true) {
            titlePaint.setTextSize(size);
            layout = StaticLayout.Builder.obtain(text, 0, text.length(), titlePaint, width)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setLineSpacing(0, 1.05f)
                    .setIncludePad(false)
                    .setMaxLines(6)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .build();
            if ((layout.getHeight() <= maxHeight && layout.getLineCount() <= 5) || size <= width * 0.08f) break;
            size *= 0.9f;
        }
        return layout;
    }
}
