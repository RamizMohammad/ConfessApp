package in.mohammad.ramiz.confess.ui.me;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** A tiny preview of a graph look in GraphStyleSheet: four cells, empty → most, in a style. */
public class GraphSwatchView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();
    @NonNull private GraphStyle style = GraphStyle.DEFAULT;

    public GraphSwatchView(Context c) { this(c, null); }

    public GraphSwatchView(Context c, @Nullable AttributeSet a) {
        super(c, a);
    }

    public void setGraphStyle(@NonNull GraphStyle s) {
        style = s;
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas c) {
        float dp = getResources().getDisplayMetrics().density;
        float gap = 4 * dp;
        float cell = Math.min(getHeight() - 12 * dp, (getWidth() - 12 * dp - 3 * gap) / 4f);
        float total = 4 * cell + 3 * gap;
        float x = (getWidth() - total) / 2f, y = (getHeight() - cell) / 2f;
        int[] levels = {1, 2, 3, 4};
        for (int i = 0; i < 4; i++) {
            r.set(x, y, x + cell, y + cell);
            paint.setColor(style.color(levels[i]));
            ConfessionGraphView.drawShape(c, r, paint, style.shape, false, path);
            x += cell + gap;
        }
    }
}
