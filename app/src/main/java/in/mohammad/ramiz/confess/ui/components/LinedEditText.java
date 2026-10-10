package in.mohammad.ramiz.confess.ui.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.core.content.ContextCompat;

import in.mohammad.ramiz.confess.R;

/** A multi-line editor that looks like ruled letter paper: a faint line under every text line. */
public class LinedEditText extends AppCompatEditText {

    private final Paint rule = new Paint();
    private final Rect bounds = new Rect();
    private final float gap;

    public LinedEditText(@NonNull Context context) {
        this(context, null);
    }

    public LinedEditText(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, android.R.attr.editTextStyle);
    }

    public LinedEditText(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        float d = getResources().getDisplayMetrics().density;
        rule.setColor(ContextCompat.getColor(context, R.color.paper_rule));
        rule.setStrokeWidth(Math.max(1f, d));
        gap = 7 * d;                                     // line sits a little under the baseline
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int lineHeight = getLineHeight();
        if (lineHeight > 0) {
            int left = getPaddingLeft();
            int right = getWidth() - getPaddingRight();
            int firstBaseline = getLineBounds(0, bounds);
            int visible = (getHeight() - getPaddingTop() - getPaddingBottom()) / lineHeight;
            int count = Math.max(getLineCount(), visible);
            for (int i = 0; i < count; i++) {
                float y = firstBaseline + i * lineHeight + gap;
                if (y > getHeight() - getPaddingBottom() + gap) break;
                canvas.drawLine(left, y, right, y, rule);
            }
        }
        super.onDraw(canvas);
    }
}
