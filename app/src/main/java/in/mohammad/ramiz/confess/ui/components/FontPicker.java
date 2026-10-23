package in.mohammad.ramiz.confess.ui.components;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Haptics;

/**
 * A row of font chips you can swipe through, each written in its own font ("Aa" + its name);
 * the chosen one is amber. Used for confession text (Appearance) and books (reading sheet).
 */
public class FontPicker extends HorizontalScrollView {

    public interface Listener {
        void onFont(int id);
    }

    private final LinearLayout row;
    private int selected = ReadingFonts.DEFAULT;
    @Nullable private Listener listener;

    public FontPicker(@NonNull Context context) {
        this(context, null);
    }

    public FontPicker(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setClipToPadding(false);
        row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        addView(row, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        float d = getResources().getDisplayMetrics().density;
        for (int id : ReadingFonts.ORDER) {
            LinearLayout chip = new LinearLayout(context);
            chip.setOrientation(LinearLayout.VERTICAL);
            chip.setGravity(Gravity.CENTER);
            chip.setMinimumWidth(Math.round(84 * d));
            chip.setPadding(Math.round(14 * d), Math.round(10 * d), Math.round(14 * d), Math.round(10 * d));
            chip.setClickable(true);
            chip.setFocusable(true);
            chip.setTag(id);
            chip.setContentDescription(context.getString(ReadingFonts.name(id)) + ". " + context.getString(ReadingFonts.about(id)));

            TextView sample = new TextView(context);
            sample.setText("Aa");
            sample.setTypeface(ReadingFonts.typeface(context, id));
            sample.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22 * ReadingFonts.scale(id));
            sample.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            chip.addView(sample);

            TextView name = new TextView(context);
            name.setText(ReadingFonts.name(id));
            name.setTypeface(ReadingFonts.typeface(context, id));
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12 * ReadingFonts.scale(id));
            name.setMaxLines(1);
            name.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            chip.addView(name);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(Math.round(8 * d));
            chip.setOnClickListener(v -> {
                if (id == selected) return;
                Haptics.tick(context);
                select(id, true);
                if (listener != null) listener.onFont(id);
            });
            row.addView(chip, lp);
        }
        paint();
    }

    public void setListener(@Nullable Listener l) {
        listener = l;
    }

    public int selected() {
        return selected;
    }

    /** Mark this font (no listener call); scrolls it into view. */
    public void select(int id, boolean animate) {
        selected = ReadingFonts.valid(id);
        paint();
        View chip = row.findViewWithTag(selected);
        if (chip == null) return;
        post(() -> {
            int x = Math.max(0, chip.getLeft() - (getWidth() - chip.getWidth()) / 2);
            if (animate) smoothScrollTo(x, 0); else scrollTo(x, 0);
        });
        if (animate) {
            chip.setScaleX(0.94f);
            chip.setScaleY(0.94f);
            chip.animate().scaleX(1f).scaleY(1f).setDuration(220)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(2f)).start();
        }
    }

    private void paint() {
        int amber = ContextCompat.getColor(getContext(), R.color.amber);
        int text = ContextCompat.getColor(getContext(), R.color.text_primary);
        int muted = ContextCompat.getColor(getContext(), R.color.text_muted);
        for (int i = 0; i < row.getChildCount(); i++) {
            LinearLayout chip = (LinearLayout) row.getChildAt(i);
            boolean on = (int) chip.getTag() == selected;
            chip.setBackgroundResource(on ? R.drawable.bg_choice_option_on : R.drawable.bg_choice_option);
            chip.setSelected(on);
            ((TextView) chip.getChildAt(0)).setTextColor(on ? amber : text);
            ((TextView) chip.getChildAt(1)).setTextColor(on ? amber : muted);
        }
    }
}
