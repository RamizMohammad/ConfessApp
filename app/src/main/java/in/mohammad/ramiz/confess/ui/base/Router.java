package in.mohammad.ramiz.confess.ui.base;

import android.app.Activity;
import android.content.Intent;

import in.mohammad.ramiz.confess.data.session.SessionManager;
import in.mohammad.ramiz.confess.ui.entry.MaintenanceActivity;
import in.mohammad.ramiz.confess.ui.entry.WelcomeActivity;
import in.mohammad.ramiz.confess.ui.home.HomeActivity;
import in.mohammad.ramiz.confess.ui.unlock.UnlockActivity;

/** Where to go once we know who the user is. One place, so every entry path agrees. */
public final class Router {

    private Router() { }

    /** Signed in → unlock screen (if the account has a password lock) or home. */
    public static void toApp(Activity from) {
        Class<?> target = SessionManager.get().needsUnlock() ? UnlockActivity.class : HomeActivity.class;
        go(from, target);
    }

    public static void toWelcome(Activity from) {
        go(from, WelcomeActivity.class);
    }

    /** Server didn't answer GET /ready with a 200. */
    public static void toMaintenance(Activity from) {
        go(from, MaintenanceActivity.class);
    }

    private static void go(Activity from, Class<?> target) {
        Intent i = new Intent(from, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        from.startActivity(i);
        from.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        from.finish();
    }
}
