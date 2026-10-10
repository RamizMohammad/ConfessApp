package in.mohammad.ramiz.confess.core;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import in.mohammad.ramiz.confess.data.session.SessionManager;

/**
 * Re-locks a password-protected account after the app has been in the
 * background longer than the chosen time ({@link LockPrefs}, 5 min by default). Counts started activities; when the
 * count drops to 0 the app is "in background".
 * BaseActivity.onStart() then routes to the unlock screen when needed.
 */
public final class AppLock implements Application.ActivityLifecycleCallbacks {

    private final Application app;
    private int started = 0;
    private long backgroundSince = -1;

    public static void install(Application app) {
        app.registerActivityLifecycleCallbacks(new AppLock(app));
    }

    private AppLock(Application app) {
        this.app = app;
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        if (started++ == 0 && backgroundSince > 0
                && SystemClock.elapsedRealtime() - backgroundSince >= LockPrefs.timeoutMs(app)) {
            SessionManager.get().lock();
        }
        backgroundSince = -1;
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        if (--started == 0 && !activity.isChangingConfigurations()) {
            backgroundSince = SystemClock.elapsedRealtime();
        }
    }

    @Override public void onActivityCreated(@NonNull Activity a, @Nullable Bundle b) { }
    @Override public void onActivityResumed(@NonNull Activity a) { }
    @Override public void onActivityPaused(@NonNull Activity a) { }
    @Override public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull Bundle b) { }
    @Override public void onActivityDestroyed(@NonNull Activity a) { }
}
