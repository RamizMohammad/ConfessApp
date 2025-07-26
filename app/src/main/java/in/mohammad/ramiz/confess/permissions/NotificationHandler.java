package in.mohammad.ramiz.confess.permissions;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.activity.result.ActivityResultLauncher;
import androidx.core.app.ActivityCompat;

public class NotificationHandler {

    private final Context context;
    private final ActivityResultLauncher<String> permissionLauncher;

    public NotificationHandler(Context context, ActivityResultLauncher<String> launcher) {
        this.context = context;
        this.permissionLauncher = launcher;
    }

    public void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    public interface PermissionResultCallback {
        void onPermissionGranted();
        void onPermissionDenied();
    }
}
