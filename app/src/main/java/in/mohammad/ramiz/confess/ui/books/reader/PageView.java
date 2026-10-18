package in.mohammad.ramiz.confess.ui.books.reader;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import java.util.Locale;

import in.mohammad.ramiz.confess.R;

/**
 * One printed page: running head (book title), the chapter heading on a chapter's first page,
 * a slice of the chapter's lines, and the folio (chapter · page n / total) at the foot.
 * The opaque background gives the page an outline, so it casts a shadow while being turned.
 */
final class PageView extends View {

    @Nullable private ChapterPages pages;
    @Nullable private ReaderStyle style;
    private int page;
    private int chapterNo;
    @NonNull private String bookTitle = "";
    @NonNull private String chapterTitle = "";
    private final Paint rule = new Paint(Paint.ANTI_ALIAS_FLAG);

    PageView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    void bind(@NonNull ChapterPages pages, @NonNull ReaderStyle style, int page, int chapterNo,
              @NonNull String bookTitle, @NonNull String chapterTitle) {
        this.pages = pages;
        this.style = style;
        this.page = page;
        this.chapterNo = chapterNo;
        this.bookTitle = bookTitle;
        this.chapterTitle = chapterTitle;
        setBackground(new ColorDrawable(style.background));
        int from = pages.body.getLineStart(pages.startLine(page));
        int to = pages.body.getLineEnd(Math.max(pages.startLine(page), pages.endLine(page) - 1));
        CharSequence t = pages.body.getText();
        setContentDescription(t.subSequence(Math.min(from, t.length()), Math.min(to, t.length())));
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas c) {
        ChapterPages p = pages;
        ReaderStyle s = style;
        if (p == null || s == null) return;
        int w = getWidth(), h = getHeight();
        float cx = w / 2f;
        boolean first = page == 0;

        // Running head (not on a chapter's opening page, like a printed book).
        if (!first) {
            s.running.setTextAlign(Paint.Align.CENTER);
            String head = TextUtils.ellipsize(bookTitle.toUpperCase(Locale.getDefault()), s.running,
                    w - 2f * s.sidePx, TextUtils.TruncateAt.END).toString();
            c.drawText(head, cx, s.headBaselinePx, s.running);
        }

        int bodyTop = s.topPx;
        if (first) {
            float unit = s.body.getTextSize();
            float y = s.topPx + p.headingTop;
            s.label.setTextAlign(Paint.Align.CENTER);
            c.drawText(getContext().getString(R.string.reader_chapter_label, chapterNo), cx, y - s.label.ascent(), s.label);
            y += s.label.getFontSpacing() + unit * 0.9f;
            c.save();
            c.translate(s.sidePx, y);
            p.title.draw(c);
            c.restore();
            y += p.title.getHeight() + unit * 1.2f;
            rule.setColor(ColorUtils.setAlphaComponent(s.accent, 160));
            rule.setStrokeWidth(Math.max(1f, unit * 0.06f));
            c.drawLine(cx - unit * 1.2f, y, cx + unit * 1.2f, y, rule);
            bodyTop = s.topPx + p.headingHeight;
        }

        // The page's lines — on ruled paper: a faint rule under every line, continued down to the
        // foot of the page (even on a chapter's short last page), like a letter.
        int sl = p.startLine(page), el = p.endLine(page);
        if (s.rule != null && el > sl) {
            float gap = s.body.getTextSize() * 0.32f;              // just under the baseline, clear of descenders
            int top = p.body.getLineTop(sl);
            float left = s.sidePx, right = w - s.sidePx;
            float lastY = 0;
            for (int i = sl; i < el; i++) {
                lastY = bodyTop + (p.body.getLineBaseline(i) - top) + gap;
                c.drawLine(left, lastY, right, lastY, s.rule);
            }
            float step = el - sl > 1
                    ? (p.body.getLineBaseline(el - 1) - p.body.getLineBaseline(sl)) / (float) (el - 1 - sl)
                    : p.body.getLineBottom(sl) - p.body.getLineTop(sl);
            float floor = h - s.bottomPx;
            if (step > 0) for (float y = lastY + step; y <= floor; y += step) c.drawLine(left, y, right, y, s.rule);
        }
        if (el > sl) {
            int top = p.body.getLineTop(sl);
            int bottom = p.body.getLineBottom(el - 1);
            c.save();
            c.translate(s.sidePx, bodyTop - top);
            c.clipRect(0, top, w - 2 * s.sidePx, bottom);
            p.body.draw(c);
            c.restore();
        }

        // Folio: chapter title on the left, page n / total on the right.
        float fy = h - s.footBaselineFromBottomPx;
        s.running.setTextAlign(Paint.Align.LEFT);
        String left = TextUtils.ellipsize(chapterTitle, s.running, (w - 2f * s.sidePx) * 0.62f,
                TextUtils.TruncateAt.END).toString();
        c.drawText(left, s.sidePx, fy, s.running);
        s.running.setTextAlign(Paint.Align.RIGHT);
        c.drawText((page + 1) + " / " + p.count(), w - s.sidePx, fy, s.running);
    }
}
