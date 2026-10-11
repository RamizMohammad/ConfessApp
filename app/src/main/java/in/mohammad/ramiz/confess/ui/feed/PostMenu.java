package in.mohammad.ramiz.confess.ui.feed;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.data.model.Post;
import in.mohammad.ramiz.confess.databinding.ItemPopupRowBinding;
import in.mohammad.ramiz.confess.databinding.PopupPostMenuBinding;

/**
 * The card's ⋯ menu — our own popup instead of the system PopupMenu.
 *   mine   → Delete confession
 *   theirs → Report confession · Report @alias · Block @alias
 * plus {@link #showPerson} for a profile's flag: Report @alias · Block / Unblock @alias.
 * Opens beside the ⋯ (above it when there's no room below) and right-aligned to it.
 */
public final class PostMenu {

    public interface Listener {
        void onDelete(@NonNull Post post);

        void onReportPost(@NonNull Post post);

        void onReportUser(@NonNull Post post, @NonNull String alias);

        /** After a successful block (the lists have already dropped their confessions). */
        default void onBlocked(@NonNull String alias) { }
    }

    /** A response's menu. */
    public interface ResponseListener {
        void onDelete();

        void onReport();

        void onBlock(@NonNull String alias);
    }

    /** A profile's flag menu. */
    public interface PersonListener {
        void onReportUser(@NonNull String alias);

        void onBlock(@NonNull String alias);

        void onUnblock(@NonNull String alias);
    }

    private PostMenu() { }

    public static void show(@NonNull View anchor, @NonNull Post post, @NonNull Listener l) {
        Context ctx = anchor.getContext();
        PopupPostMenuBinding b = PopupPostMenuBinding.inflate(LayoutInflater.from(ctx));
        PopupWindow window = new PopupWindow(b.getRoot(),
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        window.setOutsideTouchable(true);
        window.setElevation(ctx.getResources().getDisplayMetrics().density * 12);
        window.setAnimationStyle(R.style.Animation_Confess_Popup);
        window.setBackgroundDrawable(ContextCompat.getDrawable(ctx, R.drawable.bg_popup_menu));   // shape → shadow outline

        String alias = post.author != null ? post.author.alias : null;
        if (post.isMine) {
            row(b, ctx, R.drawable.ic_trash, ctx.getString(R.string.menu_delete), R.color.brick, () -> {
                window.dismiss();
                l.onDelete(post);
            });
        } else {
            row(b, ctx, R.drawable.ic_flag, ctx.getString(R.string.menu_report_post), R.color.text_primary, () -> {
                window.dismiss();
                l.onReportPost(post);
            });
            if (alias != null && !alias.isEmpty()) {
                row(b, ctx, R.drawable.ic_user, ctx.getString(R.string.menu_report_user, alias), R.color.text_primary, () -> {
                    window.dismiss();
                    l.onReportUser(post, alias);
                });
                row(b, ctx, R.drawable.ic_ban, ctx.getString(R.string.menu_block_user, alias), R.color.brick, () -> {
                    window.dismiss();
                    in.mohammad.ramiz.confess.ui.people.BlockFlow.confirm(ctx, alias, () -> l.onBlocked(alias));
                });
            }
        }
        place(anchor, b, window);
    }

    public static void showPerson(@NonNull View anchor, @NonNull String alias, boolean blocked,
                                  @NonNull PersonListener l) {
        Context ctx = anchor.getContext();
        PopupPostMenuBinding b = PopupPostMenuBinding.inflate(LayoutInflater.from(ctx));
        PopupWindow window = new PopupWindow(b.getRoot(),
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        window.setOutsideTouchable(true);
        window.setElevation(ctx.getResources().getDisplayMetrics().density * 12);
        window.setAnimationStyle(R.style.Animation_Confess_Popup);
        window.setBackgroundDrawable(ContextCompat.getDrawable(ctx, R.drawable.bg_popup_menu));
        row(b, ctx, R.drawable.ic_flag, ctx.getString(R.string.menu_report_user, alias), R.color.text_primary, () -> {
            window.dismiss();
            l.onReportUser(alias);
        });
        if (blocked) {
            row(b, ctx, R.drawable.ic_ban, ctx.getString(R.string.menu_unblock_user, alias), R.color.text_primary, () -> {
                window.dismiss();
                l.onUnblock(alias);
            });
        } else {
            row(b, ctx, R.drawable.ic_ban, ctx.getString(R.string.menu_block_user, alias), R.color.brick, () -> {
                window.dismiss();
                l.onBlock(alias);
            });
        }
        place(anchor, b, window);
    }

    /** mine → Delete · on my confession → Delete + Report + Block · otherwise → Report + Block. */
    public static void showResponse(@NonNull View anchor, @NonNull String alias, boolean mine, boolean canDelete,
                                    @NonNull ResponseListener l) {
        Context ctx = anchor.getContext();
        PopupPostMenuBinding b = PopupPostMenuBinding.inflate(LayoutInflater.from(ctx));
        PopupWindow window = new PopupWindow(b.getRoot(),
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        window.setOutsideTouchable(true);
        window.setElevation(ctx.getResources().getDisplayMetrics().density * 12);
        window.setAnimationStyle(R.style.Animation_Confess_Popup);
        window.setBackgroundDrawable(ContextCompat.getDrawable(ctx, R.drawable.bg_popup_menu));
        if (canDelete) {
            row(b, ctx, R.drawable.ic_trash, ctx.getString(R.string.response_delete), R.color.brick, () -> {
                window.dismiss();
                l.onDelete();
            });
        }
        if (!mine) {
            row(b, ctx, R.drawable.ic_flag, ctx.getString(R.string.menu_report_response), R.color.text_primary, () -> {
                window.dismiss();
                l.onReport();
            });
            if (!alias.isEmpty()) {
                row(b, ctx, R.drawable.ic_ban, ctx.getString(R.string.menu_block_user, alias), R.color.brick, () -> {
                    window.dismiss();
                    l.onBlock(alias);
                });
            }
        }
        place(anchor, b, window);
    }

    private static void place(@NonNull View anchor, @NonNull PopupPostMenuBinding b, @NonNull PopupWindow window) {
        Context ctx = anchor.getContext();
        // Right-align to the ⋯; flip above it when the bottom of the screen is too close.
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

    private static void row(@NonNull PopupPostMenuBinding parent, @NonNull Context ctx, @DrawableRes int icon,
                            @NonNull String label, @ColorRes int color, @NonNull Runnable onClick) {
        ItemPopupRowBinding r = ItemPopupRowBinding.inflate(LayoutInflater.from(ctx), parent.rows, false);
        int c = ContextCompat.getColor(ctx, color);
        r.icon.setImageResource(icon);
        r.icon.setImageTintList(ColorStateList.valueOf(c));
        r.label.setText(label);
        r.label.setTextColor(c);
        r.getRoot().setContentDescription(label);
        r.getRoot().setOnClickListener(v -> onClick.run());
        parent.rows.addView(r.getRoot());
    }
}
