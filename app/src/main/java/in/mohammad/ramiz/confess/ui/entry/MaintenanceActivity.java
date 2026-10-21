package in.mohammad.ramiz.confess.ui.entry;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.data.api.ServerStatus;
import in.mohammad.ramiz.confess.databinding.ActivityMaintenanceBinding;
import in.mohammad.ramiz.confess.ui.base.BaseActivity;
import in.mohammad.ramiz.confess.ui.util.EdgeInsets;

/**
 * Shown by the splash when the server doesn't answer GET /ready with a 200.
 * Re-checks on "Try again" and quietly every {@link #AUTO_RETRY_MS} while visible;
 * once the server is back, goes through the launcher again (skipping its own check).
 */
public class MaintenanceActivity extends BaseActivity {

    private static final long AUTO_RETRY_MS = 30_000;

    private ActivityMaintenanceBinding b;
    private final Handler handler = new Handler(Looper.getMainLooper());
    @Nullable private ServerStatus.Check check;
    private boolean leaving = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityMaintenanceBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeInsets.padSystemBars(b.getRoot(), false);
        b.retry.setOnClickListener(v -> recheck(true));
    }

    @Override
    protected boolean requiresSession() {
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        scheduleAutoRetry();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacksAndMessages(null);
        super.onPause();
    }

    private void scheduleAutoRetry() {
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(() -> recheck(false), AUTO_RETRY_MS);
    }

    private void recheck(boolean byUser) {
        if (leaving || check != null) return;
        if (byUser) {
            b.retry.setEnabled(false);
            b.retry.setText(R.string.maintenance_checking);
            b.status.setText("");
        }
        check = ServerStatus.check(up -> {
            check = null;
            if (up) {
                backIn();
                return;
            }
            b.retry.setEnabled(true);
            b.retry.setText(R.string.maintenance_retry);
            if (byUser) b.status.setText(R.string.maintenance_still_down);
            scheduleAutoRetry();
        });
    }

    private void backIn() {
        leaving = true;
        Intent i = new Intent(this, LauncherActivity.class)
                .putExtra(LauncherActivity.EXTRA_SERVER_OK, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (check != null) check.cancel();
        super.onDestroy();
    }
}
