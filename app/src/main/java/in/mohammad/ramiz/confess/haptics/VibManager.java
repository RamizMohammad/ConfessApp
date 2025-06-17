package in.mohammad.ramiz.confess.haptics;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

public class VibManager {

    /**
     * Vibrate subtly using predefined EFFECT_TICK if available, otherwise default light vibration.
     * @param context Any context (e.g., activity)
     */
    public static void vibrateTick(Context context) {
        if (context == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
            VibratorManager vibratorManager = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            if (vibratorManager != null) {
                Vibrator vibrator = vibratorManager.getDefaultVibrator();
                if (vibrator.hasVibrator()) {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));
                }
            }
        } else {
            // For older versions, fallback to custom subtle buzz
            vibrateCustom(context, 20);  // 20ms subtle buzz
        }
    }

    /**
     * Vibrate for custom duration (ms) with default amplitude.
     * @param context Any context (e.g., activity)
     * @param milliseconds Duration to vibrate
     */
    public static void vibrateCustom(Context context, long milliseconds) {
        if (context == null || milliseconds <= 0) return;

        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            VibrationEffect effect = VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE);
            vibrator.vibrate(effect);
        } else {
            vibrator.vibrate(milliseconds);
        }
    }
}
