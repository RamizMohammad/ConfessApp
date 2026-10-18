package in.mohammad.ramiz.confess.ui.feed;

import android.content.Context;
import android.content.res.ColorStateList;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.databinding.SheetBurstChooserBinding;

/**
 * The "how should bursts look?" sheet: three looping previews (slider · stack card · one by one),
 * tap one, "Use this". The choice is saved ({@link Bursts#setMode}) and handed back.
 */
public final class BurstChooser {

    public interface Picked {
        void onPicked(int mode);
    }

    private BurstChooser() { }

    public static void show(@NonNull Context ctx, @NonNull Picked picked) {
        BottomSheetDialog d = new BottomSheetDialog(ctx);
        SheetBurstChooserBinding s = SheetBurstChooserBinding.inflate(d.getLayoutInflater());
        s.preview0.setMode(Bursts.SLIDER);
        s.preview1.setMode(Bursts.STACK);
        s.preview2.setMode(Bursts.SEPARATE);
        MaterialCardView[] opts = {s.opt0, s.opt1, s.opt2};
        final int[] choice = {Bursts.mode(ctx)};
        Runnable render = () -> {
            int on = ContextCompat.getColor(ctx, R.color.amber);
            int off = ContextCompat.getColor(ctx, R.color.hairline);
            float density = ctx.getResources().getDisplayMetrics().density;
            for (int i = 0; i < opts.length; i++) {
                boolean sel = i == choice[0];
                opts[i].setStrokeColor(ColorStateList.valueOf(sel ? on : off));
                opts[i].setStrokeWidth(Math.round(density * (sel ? 2 : 1)));
                opts[i].setChecked(sel);
                opts[i].setSelected(sel);
            }
        };
        render.run();
        for (int i = 0; i < opts.length; i++) {
            final int mode = i;                          // opt0 = SLIDER, opt1 = STACK, opt2 = SEPARATE
            opts[i].setOnClickListener(v -> {
                Haptics.tick(ctx);
                choice[0] = mode;
                render.run();
            });
        }
        s.use.setOnClickListener(v -> {
            Bursts.setMode(ctx, choice[0]);
            Haptics.click(ctx);
            d.dismiss();
            picked.onPicked(choice[0]);
        });
        d.setOnDismissListener(x -> Bursts.markAsked(ctx));   // closed without choosing: the default stays
        d.setContentView(s.getRoot());
        d.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        d.getBehavior().setSkipCollapsed(true);
        d.show();
    }
}
