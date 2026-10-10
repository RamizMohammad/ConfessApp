package in.mohammad.ramiz.confess.ui.onboarding;

import android.view.View;
import android.widget.LinearLayout;

import androidx.activity.ComponentActivity;
import androidx.core.content.ContextCompat;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.databinding.ViewStepBarBinding;

/** Fills the onboarding top bar: back button + "STEP n OF m" + progress dots (current = amber pill). */
final class StepBar {

    private StepBar() { }

    static void bind(ViewStepBarBinding bar, ComponentActivity activity, int step, int total) {
        bar.stepText.setText(activity.getString(R.string.onb_step, step, total));
        bar.back.setOnClickListener(v -> activity.getOnBackPressedDispatcher().onBackPressed());
        bar.dots.removeAllViews();
        float d = activity.getResources().getDisplayMetrics().density;
        for (int i = 1; i <= total; i++) {
            View dot = new View(activity);
            boolean current = i == step;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    Math.round((current ? 16 : 6) * d), Math.round(6 * d));
            if (i > 1) lp.setMarginStart(Math.round(4 * d));
            dot.setLayoutParams(lp);
            dot.setBackgroundResource(R.drawable.step_dot);
            dot.setBackgroundTintList(ContextCompat.getColorStateList(activity,
                    i <= step ? R.color.amber : R.color.hairline));
            bar.dots.addView(dot);
        }
    }
}
