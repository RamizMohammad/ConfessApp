package in.mohammad.ramiz.confess.ui.components;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.databinding.ItemPopupRowBinding;
import in.mohammad.ramiz.confess.databinding.PopupPostMenuBinding;

/**
 * Our ⋯ popup for any screen (the card menu's look, {@code PostMenu}), instead of the system
 * PopupMenu: raised surface, icon · label rows, brick red for destructive ones; opens beside
 * the anchor, right-aligned, above it when there's no room below.
 *
 *   PopMenu.at(anchor).item(R.drawable.ic_eye, R.string.desk_preview, this::preview)
 *           .danger(R.drawable.ic_trash, R.string.desk_delete, this::confirmDelete).show();
 */
public final class PopMenu {

    private static final class Item {
        @DrawableRes final int icon;
        @NonNull final CharSequence label;
        final boolean danger;
        @NonNull final Runnable run;

        Item(int icon, @NonNull CharSequence label, boolean danger, @NonNull Runnable run) {
            this.icon = icon;
            this.label = label;
            this.danger = danger;
            this.run = run;
        }
    }

    private final View anchor;
    private final List<Item> items = new ArrayList<>();

    private PopMenu(@NonNull View anchor) {
        this.anchor = anchor;
    }

    @NonNull
    public static PopMenu at(@NonNull View anchor) {
        return new PopMenu(anchor);
    }

    @NonNull
    public PopMenu item(@DrawableRes int icon, @StringRes int label, @NonNull Runnable run) {
        items.add(new Item(icon, anchor.getContext().getString(label), false, run));
        return this;
    }

    @NonNull
    public PopMenu danger(@DrawableRes int icon, @StringRes int label, @NonNull Runnable run) {
        items.add(new Item(icon, anchor.getContext().getString(label), true, run));
        return this;
    }

    public void show() {
        Context ctx = anchor.getContext();
        PopupPostMenuBinding b = PopupPostMenuBinding.inflate(LayoutInflater.from(ctx));
        PopupWindow window = new PopupWindow(b.getRoot(),
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        window.setOutsideTouchable(true);
        window.setElevation(ctx.getResources().getDisplayMetrics().density * 12);
        window.setAnimationStyle(R.style.Animation_Confess_Popup);
        window.setBackgroundDrawable(ContextCompat.getDrawable(ctx, R.drawable.bg_popup_menu));
        for (Item it : items) {
            ItemPopupRowBinding r = ItemPopupRowBinding.inflate(LayoutInflater.from(ctx), b.rows, false);
            int c = ContextCompat.getColor(ctx, it.danger ? R.color.brick : R.color.text_primary);
            r.icon.setImageResource(it.icon);
            r.icon.setImageTintList(ColorStateList.valueOf(c));
            r.label.setText(it.label);
            r.label.setTextColor(c);
            r.getRoot().setContentDescription(it.label);
            r.getRoot().setOnClickListener(v -> {
                window.dismiss();
                it.run.run();
            });
            b.rows.addView(r.getRoot());
        }
        b.getRoot().measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int w = b.getRoot().getMeasuredWidth(), h = b.getRoot().getMeasuredHeight();
        Rect visible = new Rect();
        anchor.getWindowVisibleDisplayFrame(visible);
        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        int gap = Math.round(ctx.getResources().getDisplayMetrics().density * 4);
        int x = anchor.getWidth() - w;
        boolean below = loc[1] + anchor.getHeight() + gap + h <= visible.bottom;
        int y = below ? gap : -(anchor.getHeight() + h + gap);
        Haptics.tick(ctx);
        window.showAsDropDown(anchor, x, y);
    }
}
