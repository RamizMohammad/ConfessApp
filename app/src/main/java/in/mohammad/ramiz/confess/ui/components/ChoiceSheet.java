package in.mohammad.ramiz.confess.ui.components;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.databinding.ItemChoiceOptionBinding;
import in.mohammad.ramiz.confess.databinding.SheetChoiceBinding;

/**
 * Confess's own "pick one" sheet, instead of the system's plain list dialogs: a title and a
 * line about it, options as paper-dark plates (icon tile · title / a line · amber check on the
 * current one), easing in one after another.
 *
 *   ChoiceSheet.with(ctx).title(R.string.x)
 *           .option(R.drawable.ic_bell, "Phone's default", "Whatever your phone uses", true, () -> …)
 *           .show();
 *
 * Tapping an option picks it and closes the sheet — or, with {@link #confirm}, marks it and the
 * confirm button does the rest (report reasons: you choose, then send).
 */
public final class ChoiceSheet {

    public interface Confirmed {
        void onConfirm(int index);
    }

    private static final class Option {
        @DrawableRes final int icon;
        @NonNull final CharSequence label;
        @Nullable final CharSequence sub;
        final boolean selected, danger;
        @Nullable final Runnable onPick;

        Option(int icon, @NonNull CharSequence label, @Nullable CharSequence sub, boolean selected, boolean danger,
               @Nullable Runnable onPick) {
            this.icon = icon;
            this.label = label;
            this.sub = sub;
            this.selected = selected;
            this.danger = danger;
            this.onPick = onPick;
        }
    }

    private final Context ctx;
    @Nullable private CharSequence title, subtitle;
    private final List<Option> options = new ArrayList<>();
    @Nullable private CharSequence confirmLabel;
    private boolean confirmDanger;
    @Nullable private Confirmed confirmed;
    @Nullable private Runnable onDismiss;

    private ChoiceSheet(@NonNull Context ctx) {
        this.ctx = ctx;
    }

    @NonNull
    public static ChoiceSheet with(@NonNull Context ctx) {
        return new ChoiceSheet(ctx);
    }

    @NonNull
    public ChoiceSheet title(@StringRes int res) {
        title = ctx.getString(res);
        return this;
    }

    @NonNull
    public ChoiceSheet title(@NonNull CharSequence text) {
        title = text;
        return this;
    }

    @NonNull
    public ChoiceSheet subtitle(@StringRes int res) {
        subtitle = ctx.getString(res);
        return this;
    }

    @NonNull
    public ChoiceSheet subtitle(@Nullable CharSequence text) {
        subtitle = text;
        return this;
    }

    /** An option (icon 0 = none). Picking it closes the sheet and runs {@code onPick}. */
    @NonNull
    public ChoiceSheet option(@DrawableRes int icon, @NonNull CharSequence label, @Nullable CharSequence sub,
                              boolean selected, @Nullable Runnable onPick) {
        options.add(new Option(icon, label, sub, selected, false, onPick));
        return this;
    }

    @NonNull
    public ChoiceSheet option(@DrawableRes int icon, @StringRes int label, @StringRes int sub, boolean selected,
                              @Nullable Runnable onPick) {
        return option(icon, ctx.getString(label), sub == 0 ? null : ctx.getString(sub), selected, onPick);
    }

    /** A destructive option (brick red). */
    @NonNull
    public ChoiceSheet danger(@DrawableRes int icon, @NonNull CharSequence label, @Nullable CharSequence sub,
                              @Nullable Runnable onPick) {
        options.add(new Option(icon, label, sub, false, true, onPick));
        return this;
    }

    /** Choose first, then press this (it's enabled once something is chosen). */
    @NonNull
    public ChoiceSheet confirm(@StringRes int label, boolean danger, @NonNull Confirmed c) {
        confirmLabel = ctx.getString(label);
        confirmDanger = danger;
        confirmed = c;
        return this;
    }

    @NonNull
    public ChoiceSheet onDismiss(@Nullable Runnable r) {
        onDismiss = r;
        return this;
    }

    @NonNull
    public BottomSheetDialog show() {
        BottomSheetDialog d = new BottomSheetDialog(ctx);
        SheetChoiceBinding s = SheetChoiceBinding.inflate(LayoutInflater.from(ctx));
        s.title.setText(title);
        s.title.setVisibility(title == null ? View.GONE : View.VISIBLE);
        if (subtitle != null) {
            s.subtitle.setVisibility(View.VISIBLE);
            s.subtitle.setText(subtitle);
        }
        final int[] chosen = {-1};
        List<ItemChoiceOptionBinding> rows = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            Option o = options.get(i);
            ItemChoiceOptionBinding r = ItemChoiceOptionBinding.inflate(LayoutInflater.from(ctx), s.options, false);
            r.label.setText(o.label);
            if (o.sub != null) {
                r.sub.setVisibility(View.VISIBLE);
                r.sub.setText(o.sub);
            }
            if (o.icon != 0) {
                r.icon.setImageResource(o.icon);
            } else {
                r.tile.setVisibility(View.GONE);
            }
            if (o.danger) {
                int brick = ContextCompat.getColor(ctx, R.color.brick);
                r.label.setTextColor(brick);
                r.icon.setImageTintList(ColorStateList.valueOf(brick));
            }
            r.row.setContentDescription(o.sub == null ? o.label : o.label + ". " + o.sub);
            final int index = i;
            r.row.setOnClickListener(v -> {
                if (confirmed != null) {                     // choose, then confirm
                    Haptics.tick(ctx);
                    chosen[0] = index;
                    mark(rows, index);
                    s.confirm.setEnabled(true);
                    return;
                }
                Haptics.click(ctx);
                mark(rows, index);
                v.postDelayed(() -> {                        // let the check land before it closes
                    d.dismiss();
                    if (o.onPick != null) o.onPick.run();
                }, 140);
            });
            rows.add(r);
            s.options.addView(r.getRoot());
            if (o.selected) {
                chosen[0] = i;
            }
        }
        mark(rows, chosen[0]);
        if (confirmed != null) {
            s.confirm.setVisibility(View.VISIBLE);
            s.confirm.setText(confirmLabel);
            s.confirm.setEnabled(chosen[0] >= 0);
            if (confirmDanger) {
                s.confirm.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.brick)));
            }
            s.confirm.setOnClickListener(v -> {
                if (chosen[0] < 0) return;
                Haptics.click(ctx);
                d.dismiss();
                confirmed.onConfirm(chosen[0]);
            });
        }
        s.cancel.setOnClickListener(v -> d.dismiss());
        if (onDismiss != null) d.setOnDismissListener(x -> onDismiss.run());

        // Options ease in one after another.
        for (int i = 0; i < rows.size(); i++) {
            View v = rows.get(i).getRoot();
            v.setAlpha(0f);
            v.setTranslationY(ctx.getResources().getDisplayMetrics().density * 12);
            v.animate().alpha(1f).translationY(0f).setStartDelay(60L + 40L * i).setDuration(260).start();
        }

        d.setContentView(s.getRoot());
        d.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        d.getBehavior().setSkipCollapsed(true);
        d.show();
        return d;
    }

    private void mark(@NonNull List<ItemChoiceOptionBinding> rows, int index) {
        int amber = ContextCompat.getColor(ctx, R.color.amber);
        int plain = ContextCompat.getColor(ctx, R.color.text_secondary);
        for (int i = 0; i < rows.size(); i++) {
            ItemChoiceOptionBinding r = rows.get(i);
            boolean on = i == index;
            r.row.setBackgroundResource(on ? R.drawable.bg_choice_option_on : R.drawable.bg_choice_option);
            r.check.setVisibility(on ? View.VISIBLE : View.INVISIBLE);
            r.row.setSelected(on);
            if (!options.get(i).danger) r.icon.setImageTintList(ColorStateList.valueOf(on ? amber : plain));
            if (on) {
                r.check.setScaleX(0.4f);
                r.check.setScaleY(0.4f);
                r.check.animate().scaleX(1f).scaleY(1f).setDuration(180)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(2f)).start();
            }
        }
    }
}
