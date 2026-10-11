package in.mohammad.ramiz.confess.ui.feed;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.databinding.PopupReactionsBinding;

/**
 * Tap-and-hold the heart → a floating bar of five reactions above it.
 *  - keep holding and slide: the emoji under your finger grows (with a tick); let go to pick it
 *  - let go anywhere else: the bar stays, tap one (or outside to close)
 *  - picking my current reaction again takes it back (handled by FeltSync)
 * Use {@link #attach} once per bound heart button.
 */
public final class ReactionPicker {

    public interface OnPick {
        void picked(@NonNull String reaction);
    }

    /** The open bar for a hold that's still in progress (one finger, one bar). */
    @Nullable private static ReactionPicker active;

    private final PopupWindow window;
    private final PopupReactionsBinding b;
    private final List<TextView> items = new ArrayList<>();
    private final OnPick onPick;
    private final Context ctx;
    private final float d;
    private int hovered = -1;

    /**
     * Wire the heart: long-press opens the bar, and the same finger can slide onto
     * a reaction and lift to pick it. Normal taps still go to the button's click listener.
     */
    public static void attach(@NonNull View heart, @NonNull CurrentReaction current, @NonNull OnPick onPick) {
        heart.setOnLongClickListener(v -> {
            if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);   // no list scroll mid-hold
            active = new ReactionPicker(v, current.get(), onPick);
            return true;
        });
        heart.setOnTouchListener((v, e) -> {
            ReactionPicker p = active;
            if (p == null || !p.window.isShowing()) return false;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    p.hover(e.getRawX(), e.getRawY());
                    return true;
                case MotionEvent.ACTION_UP:
                    p.release(e.getRawX(), e.getRawY());
                    active = null;
                    return false;                                   // let the button finish its press state
                case MotionEvent.ACTION_CANCEL:
                    p.hover(-1, -1);
                    active = null;
                    return false;
                default:
                    return false;
            }
        });
    }

    public interface CurrentReaction {
        @Nullable String get();
    }

    private ReactionPicker(@NonNull View anchor, @Nullable String current, @NonNull OnPick onPick) {
        this.ctx = anchor.getContext();
        this.onPick = onPick;
        this.d = ctx.getResources().getDisplayMetrics().density;
        b = PopupReactionsBinding.inflate(LayoutInflater.from(ctx));
        window = new PopupWindow(b.getRoot(),
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        window.setBackgroundDrawable(new ColorDrawable(0));          // transparent; the bar draws itself
        window.setOutsideTouchable(true);
        window.setElevation(10 * d);
        window.setAnimationStyle(R.style.Animation_Confess_Popup);

        int size = Math.round(44 * d);
        for (int i = 0; i < Reactions.KEYS.length; i++) {
            String key = Reactions.KEYS[i];
            TextView e = new TextView(ctx);
            e.setText(Reactions.emoji(key));
            e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
            e.setGravity(Gravity.CENTER);
            e.setLayoutParams(new LinearLayout.LayoutParams(size, size));
            e.setContentDescription(ctx.getString(Reactions.label(key)));
            if (key.equals(current)) e.setBackgroundResource(R.drawable.bg_reaction_selected);
            int index = i;
            e.setOnTouchListener((v, ev) -> {                       // tapping inside the bar
                if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) setHovered(index);
                else if (ev.getActionMasked() == MotionEvent.ACTION_CANCEL) setHovered(-1);
                return false;
            });
            e.setOnClickListener(v -> pick(index));
            e.setScaleX(0.3f);                                       // pop in one after another
            e.setScaleY(0.3f);
            e.setAlpha(0f);
            e.animate().scaleX(1f).scaleY(1f).alpha(1f).setStartDelay(30L * i).setDuration(220)
                    .setInterpolator(new OvershootInterpolator(2.2f)).start();
            b.row.addView(e);
            items.add(e);
        }

        // Above the heart (the first emoji sits over it); below when there's no room on top.
        b.getRoot().measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int h = b.getRoot().getMeasuredHeight();
        Rect visible = new Rect();
        anchor.getWindowVisibleDisplayFrame(visible);
        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        int gap = Math.round(4 * d);
        boolean above = loc[1] - h - gap >= visible.top;
        int y = above ? -(anchor.getHeight() + h + gap) : gap;
        Haptics.click(ctx);
        window.showAsDropDown(anchor, -Math.round(4 * d), y);
    }

    /**
     * Index of the emoji under a screen point, or -1.
     * Uses the row's position + each emoji's *layout* bounds — not their on-screen bounds,
     * which move while the hovered one is enlarged and lifted. (Measuring the moving bounds made
     * the finger fall off and back on the same emoji every frame: endless tick + pop.)
     * The current one also keeps a few dp of grace so the edge between two doesn't flicker.
     */
    private int indexAt(float rawX, float rawY) {
        int[] row = new int[2];
        b.row.getLocationOnScreen(row);
        float top = row[1] - 72 * d, bottom = row[1] + b.row.getHeight() + 28 * d;   // forgiving vertically
        if (rawY < top || rawY > bottom) return -1;
        float x = rawX - row[0];
        if (hovered >= 0) {
            View h = items.get(hovered);
            float grace = 6 * d;
            if (x >= h.getLeft() - grace && x < h.getRight() + grace) return hovered;
        }
        for (int i = 0; i < items.size(); i++) {
            View v = items.get(i);
            if (x >= v.getLeft() && x < v.getRight()) return i;
        }
        return -1;
    }

    private void hover(float rawX, float rawY) {
        setHovered(rawX < 0 ? -1 : indexAt(rawX, rawY));
    }

    private void setHovered(int i) {
        if (i == hovered) return;
        if (hovered >= 0) items.get(hovered).animate().scaleX(1f).scaleY(1f).translationY(0f).setDuration(120).start();
        hovered = i;
        if (i >= 0) {
            items.get(i).animate().scaleX(1.35f).scaleY(1.35f).translationY(-8 * d).setDuration(120).start();
            b.label.setText(Reactions.label(Reactions.KEYS[i]));
            b.label.setVisibility(View.VISIBLE);
            Haptics.tick(ctx);
        } else {
            b.label.setVisibility(View.INVISIBLE);
        }
    }

    private void release(float rawX, float rawY) {
        int i = indexAt(rawX, rawY);
        if (i >= 0) pick(i);
        else setHovered(-1);                                         // stays open for a tap
    }

    private void pick(int i) {
        Haptics.click(ctx);
        window.dismiss();
        onPick.picked(Reactions.KEYS[i]);
    }
}
