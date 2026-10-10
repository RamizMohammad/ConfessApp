package in.mohammad.ramiz.confess.core;

import android.content.Context;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/**
 * Subtle haptic feedback (minSdk 31, so VibratorManager is always available).
 * Ported from the old haptics/VibManager.
 */
public final class Haptics {

    private Haptics() { }

    /** A light tick — selections, toggles, "felt". */
    public static void tick(Context context) {
        play(context, VibrationEffect.EFFECT_TICK);
    }

    /** A slightly firmer click — confirmations (post released, account created). */
    public static void click(Context context) {
        play(context, VibrationEffect.EFFECT_CLICK);
    }

    /** Double buzz — errors (wrong password, flagged content). */
    public static void reject(Context context) {
        play(context, VibrationEffect.EFFECT_DOUBLE_CLICK);
    }

    private static void play(Context context, int effect) {
        if (context == null) return;
        VibratorManager vm = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
        if (vm == null) return;
        Vibrator vibrator = vm.getDefaultVibrator();
        if (vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createPredefined(effect));
        }
    }
}
