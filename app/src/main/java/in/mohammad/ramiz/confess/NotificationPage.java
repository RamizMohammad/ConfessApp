package in.mohammad.ramiz.confess;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import in.mohammad.ramiz.confess.adapters.NotificationAdapter;
import in.mohammad.ramiz.confess.haptics.VibManager;
import in.mohammad.ramiz.confess.notificationdatabase.NotificationViewmodel;
import in.mohammad.ramiz.confess.popups.OkPopUp;

public class NotificationPage extends AppCompatActivity {

    private LinearLayout clear, empty;
    private ImageView back;
    private RecyclerView recyclerView;
    private NotificationViewmodel viewmodel;
    private NotificationAdapter adapter;
    private boolean notiStatus = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_notification_page);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize views
        clear = findViewById(R.id.clearButton);
        back = findViewById(R.id.backButton);
        recyclerView = findViewById(R.id.recycleView);
        empty = findViewById(R.id.empty);

        // Set up adapter
        adapter = new NotificationAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        // ViewModel setup
        viewmodel = new ViewModelProvider(this).get(NotificationViewmodel.class);

        // Observe notification list
        viewmodel.getAllNotifications().observe(this, notificationEntities -> {
            if (notificationEntities != null && !notificationEntities.isEmpty()) {
                adapter.setNotifications(notificationEntities);
                recyclerView.setVisibility(View.VISIBLE);
                empty.setVisibility(View.GONE);
                notiStatus = true;
                recyclerView.smoothScrollToPosition(0);
            } else {
                notiStatus = false;
                recyclerView.setVisibility(View.GONE);
                empty.setVisibility(View.VISIBLE);
            }
        });

        // Back button
        back.setOnClickListener(v -> {
            VibManager.vibrateTick(this);
            startActivity(new Intent(this, HomePage.class));
            finish();
        });

        // Clear notifications
        clear.setOnClickListener(v -> {
            VibManager.vibrateTick(this);
            if(!notiStatus){
                new OkPopUp(this, R.raw.empty_box, "No notifications to clear");
            }
            else {
                viewmodel.deleteAllNotifications();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        finish();
    }
}
