package in.mohammad.ramiz.confess;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsCompat.Type;

import in.mohammad.ramiz.confess.haptics.VibManager;

public class HomePage extends AppCompatActivity {

    private ImageView homeLogo, addLogo, profileLogo;
    private TextView homeText, profileText;
    private LinearLayout homeButton, addButton, profileButton;

    // Persistent selection even when returning from other activities
    public static int selectedTabIndex = 0; // 0 = Home, 1 = Add, 2 = Profile

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home_page);

        // Apply bottom navigation padding for gesture nav bars
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bottomInsets = insets.getInsets(Type.navigationBars());
            findViewById(R.id.bottomNav).setPadding(0, 0, 0, bottomInsets.bottom);
            return insets;
        });

        // Find views
        homeLogo = findViewById(R.id.homeLogo);
        addLogo = findViewById(R.id.addLogo);
        profileLogo = findViewById(R.id.profileLogo);
        homeText = findViewById(R.id.homeText);
        profileText = findViewById(R.id.profileText);
        homeButton = findViewById(R.id.homeButton);
        addButton = findViewById(R.id.addButton);
        profileButton = findViewById(R.id.profileButton);

        // Initial tab selection
        applyTabSelection();

        // Set click listeners
        homeButton.setOnClickListener(v -> {
            VibManager.vibrateTick(this);
            selectedTabIndex = 0;
            applyTabSelection();
        });

        addButton.setOnClickListener(v -> {
            VibManager.vibrateTick(this);
            selectedTabIndex = 1;
            applyTabSelection();
            Intent addIntent = new Intent(this, CreatePost.class);
            startActivity(addIntent);
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        });

        profileButton.setOnClickListener(v -> {
            VibManager.vibrateTick(this);
            selectedTabIndex = 2;
            applyTabSelection();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyTabSelection(); // Reapply selection when coming back from another activity
    }

    private void applyTabSelection() {
        int selectedColor = getResources().getColor(R.color.yellow, getTheme());
        int unselectedColor = getResources().getColor(R.color.white, getTheme());

        switch (selectedTabIndex) {
            case 0:
                setSelectedTab(homeLogo, homeText, selectedColor);
                setUnselectedTab(addLogo, null, unselectedColor);
                setUnselectedTab(profileLogo, profileText, unselectedColor);
                break;
            case 1:
                setUnselectedTab(homeLogo, homeText, unselectedColor);
                setSelectedTab(addLogo, null, selectedColor);
                setUnselectedTab(profileLogo, profileText, unselectedColor);
                break;
            case 2:
                setUnselectedTab(homeLogo, homeText, unselectedColor);
                setUnselectedTab(addLogo, null, unselectedColor);
                setSelectedTab(profileLogo, profileText, selectedColor);
                break;
        }
    }

    private void setSelectedTab(ImageView icon, TextView text, int color) {
        icon.setImageTintList(ColorStateList.valueOf(color));
        if (text != null) text.setTextColor(color);
    }

    private void setUnselectedTab(ImageView icon, TextView text, int color) {
        icon.setImageTintList(ColorStateList.valueOf(color));
        if (text != null) text.setTextColor(color);
    }
}
