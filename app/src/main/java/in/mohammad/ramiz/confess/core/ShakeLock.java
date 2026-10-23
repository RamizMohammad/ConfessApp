package in.mohammad.ramiz.confess.core;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import in.mohammad.ramiz.confess.data.session.SessionManager;
import in.mohammad.ramiz.confess.ui.unlock.UnlockActivity;

/**
 * Shake to lock (Settings → Security): a firm shake while Confess is open locks it at once and
 * covers the screen with the unlock screen — for when someone walks up.
 *
 * Only with the app lock on (a password); otherwise there's nothing to lock to. Listens to the
 * accelerometer only while Confess is on screen; nothing runs in the background.
 * A shake = two jolts over ~2.6 g within 600 ms (a single bump or putting the phone down
 * doesn't count), then a short pause before it can fire again.
 */
public final class ShakeLock implements Application.ActivityLifecycleCallbacks, SensorEventListener {

    private static final float THRESHOLD_G = 2.6f;
    private static final long WINDOW_MS = 600;
    private static final long COOLDOWN_MS = 1500;

    private final Application app;
    @Nullable private final SensorManager sensors;
    @Nullable private final Sensor accel;
    @Nullable private Activity resumed;
    private boolean listening;
    private long firstJolt, lastFired;

    @Nullable private static ShakeLock instance;

    public static void install(@NonNull Application app) {
        instance = new ShakeLock(app);
        app.registerActivityLifecycleCallbacks(instance);
    }

    /** The setting was just switched (Security screen): start / stop listening right away. */
    public static void settingChanged(@NonNull Activity current) {
        if (instance != null) instance.onActivityResumed(current);
    }

    private ShakeLock(Application app) {
        this.app = app;
        sensors = (SensorManager) app.getSystemService(Context.SENSOR_SERVICE);
        accel = sensors == null ? null : sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    /** The phone has an accelerometer (the setting is hidden without one). */
    public static boolean available(@NonNull Context c) {
        SensorManager sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        return sm != null && sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null;
    }

    // ── Listening only while a screen of ours is in front ─────────────────

    @Override
    public void onActivityResumed(@NonNull Activity a) {
        resumed = a;
        boolean want = LockPrefs.shakeToLock(app) && !(a instanceof UnlockActivity);
        if (want && !listening && sensors != null && accel != null) {
            listening = sensors.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI);
        } else if (!want) {
            stop();
        }
    }

    @Override
    public void onActivityPaused(@NonNull Activity a) {
        if (resumed == a) resumed = null;
        stop();
    }

    private void stop() {
        if (listening && sensors != null) sensors.unregisterListener(this);
        listening = false;
        firstJolt = 0;
    }

    // ── Detecting a shake ─────────────────────────────────────────────────

    @Override
    public void onSensorChanged(SensorEvent e) {
        float gx = e.values[0] / SensorManager.GRAVITY_EARTH;
        float gy = e.values[1] / SensorManager.GRAVITY_EARTH;
        float gz = e.values[2] / SensorManager.GRAVITY_EARTH;
        double g = Math.sqrt(gx * gx + gy * gy + gz * gz);
        if (g < THRESHOLD_G) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastFired < COOLDOWN_MS) return;
        if (firstJolt == 0 || now - firstJolt > WINDOW_MS) {
            firstJolt = now;                              // first jolt: wait for a second one
            return;
        }
        firstJolt = 0;
        lastFired = now;
        lockNow();
    }

    private void lockNow() {
        Activity a = resumed;
        SessionManager session = SessionManager.get();
        if (a == null || a instanceof UnlockActivity || a.isFinishing()) return;
        session.lock();
        if (!session.needsUnlock()) return;               // no password on this account: nothing to lock
        Haptics.reject(a);                                // a firm buzz: "locked"
        a.startActivity(new Intent(a, UnlockActivity.class)
                .putExtra(UnlockActivity.EXTRA_RELOCK, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        a.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
    @Override public void onActivityCreated(@NonNull Activity a, @Nullable Bundle b) { }
    @Override public void onActivityStarted(@NonNull Activity a) { }
    @Override public void onActivityStopped(@NonNull Activity a) { }
    @Override public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull Bundle b) { }
    @Override public void onActivityDestroyed(@NonNull Activity a) { if (resumed == a) resumed = null; }
}
