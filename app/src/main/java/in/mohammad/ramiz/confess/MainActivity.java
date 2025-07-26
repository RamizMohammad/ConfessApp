package in.mohammad.ramiz.confess;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import in.mohammad.ramiz.confess.haptics.VibManager;
import in.mohammad.ramiz.confess.permissions.NotificationHandler;

public class MainActivity extends AppCompatActivity {

    private FrameLayout frameLayout;
    private TextView buttonText;
    private NotificationHandler handler;
    private Runnable onPermissionGrantedRunnable;

    private ActivityResultLauncher<String> permissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        frameLayout = findViewById(R.id.collapseButton);
        buttonText = findViewById(R.id.buttonText);

        // Register the permission launcher
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        Toast.makeText(MainActivity.this, "Notification permission granted", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, "Notification permission denied, continuing...", Toast.LENGTH_SHORT).show();
                    }
                    if (onPermissionGrantedRunnable != null) {
                        onPermissionGrantedRunnable.run();
                    }
                }
        );

        frameLayout.setOnClickListener(v -> {
            VibManager.vibrateTick(this);
            askForNotification(() -> {
                Intent term = new Intent(getApplicationContext(), TermsAndCondition.class);
                startActivity(term);
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            });
        });
    }

    private void askForNotification(Runnable onProceed) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onPermissionGrantedRunnable = onProceed;
            handler = new NotificationHandler(this, permissionLauncher);
            handler.requestPermission();
        } else {
            onProceed.run();
        }
    }
}
