package in.mohammad.ramiz.confess.ui.util;

import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;

import androidx.annotation.NonNull;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import androidx.recyclerview.widget.RecyclerView;

import in.mohammad.ramiz.confess.R;

/**
 * Subtle, shared motion. Everything respects the system "remove animations" setting.
 *   enterList   — rows fade up a little, staggered, the first time a list fills
 *   enterStack  — the same for the children of a column (e.g. the Me cards)
 */
public final class Motion {

    private Motion() { }

    public static boolean enabled() {
        return ValueAnimator.areAnimatorsEnabled();
    }

    /** Run once, right after a list's first data is in. */
    public static void enterList(@NonNull RecyclerView list) {
        if (!enabled()) return;
        LayoutAnimationController c = AnimationUtils.loadLayoutAnimation(list.getContext(), R.anim.layout_enter);
        list.setLayoutAnimation(c);
        list.setLayoutAnimationListener(new Animation.AnimationListener() {
            @Override public void onAnimationStart(Animation a) { }
            @Override public void onAnimationRepeat(Animation a) { }
            @Override public void onAnimationEnd(Animation a) {
                list.setLayoutAnimation(null);               // only the first fill animates
                list.setLayoutAnimationListener(null);
            }
        });
        list.scheduleLayoutAnimation();
    }

    /** Fade-up the visible children of a column, 40 ms apart. */
    public static void enterStack(@NonNull ViewGroup column) {
        if (!enabled()) return;
        float dy = 12 * column.getResources().getDisplayMetrics().density;
        int n = 0;
        for (int i = 0; i < column.getChildCount(); i++) {
            View v = column.getChildAt(i);
            if (v.getVisibility() != View.VISIBLE) continue;
            v.setAlpha(0f);
            v.setTranslationY(dy);
            v.animate().alpha(1f).translationY(0f)
                    .setStartDelay(40L * n++)
                    .setDuration(280)
                    .setInterpolator(new FastOutSlowInInterpolator())
                    .start();
        }
    }
}
