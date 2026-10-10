package in.mohammad.ramiz.confess.ui.base;

import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.LockPrefs;
import in.mohammad.ramiz.confess.data.session.SessionEvents;
import in.mohammad.ramiz.confess.data.session.SessionManager;
import in.mohammad.ramiz.confess.ui.entry.LauncherActivity;
import in.mohammad.ramiz.confess.ui.unlock.UnlockActivity;
import in.mohammad.ramiz.confess.ui.util.EdgeInsets;

/**
 * Every screen extends this: edge-to-edge dark bars, and — for screens that
 * need a session — a jump back to the launcher when the server says the
 * session has ended (401).
 */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeInsets.edgeToEdge(this);
        applySecureFlag();                          // before the first frame (recents thumbnail)
        if (requiresSession()) {
            SessionEvents.expiredEvents().observe(this, event -> {
                if (event != null && event.consume() != null) {
                    Toast.makeText(this, R.string.error_session, Toast.LENGTH_LONG).show();
                    Intent i = new Intent(this, LauncherActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        applySecureFlag();
    }

    /** Settings → Private screen. Re-applied on resume so a change reaches every open screen. */
    protected void applySecureFlag() {
        if (LockPrefs.secureScreen(this)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Re-lock after a long background (AppLock): cover this screen with the unlock screen.
        if (requiresSession() && !(this instanceof UnlockActivity) && SessionManager.get().needsUnlock()) {
            startActivity(new Intent(this, UnlockActivity.class)
                    .putExtra(UnlockActivity.EXTRA_RELOCK, true)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }
    }

    /** Screens shown before sign-in (welcome, guidelines, onboarding) return false. */
    protected boolean requiresSession() {
        return true;
    }
}
