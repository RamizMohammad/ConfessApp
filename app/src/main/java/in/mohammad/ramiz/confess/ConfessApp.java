package in.mohammad.ramiz.confess;

import android.app.Application;
import android.content.Context;

import in.mohammad.ramiz.confess.core.AppLock;
import in.mohammad.ramiz.confess.data.session.SessionManager;

/**
 * Application class — initialises the session layer before any Activity runs.
 */
public class ConfessApp extends Application {

    private static Context appContext;

    @Override
    public void onCreate() {
        super.onCreate();
        appContext = getApplicationContext();
        SessionManager.init(this);
        AppLock.install(this);
    }

    public static Context getAppContext() {
        return appContext;
    }
}
