package in.mohammad.ramiz.confess;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat.Type;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import in.mohammad.ramiz.confess.haptics.VibManager;

public class HomePage extends AppCompatActivity {

    private ImageView homeLogo, addLogo, profileLogo;
    private TextView homeText, profileText;
    private LinearLayout homeButton, addButton, profileButton;
    private FrameLayout screenHide;

    // Persistent selection even when returning from other activities
    public static int selectedTabIndex = 0; // 0 = Home, 1 = Add, 2 = Profile

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home_page);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);

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
        screenHide = findViewById(R.id.secureOverlay);

        // Set click listeners
        homeButton.setOnClickListener(v -> {
            VibManager.vibrateTick(this);
            selectedTabIndex = 0;
            applyTabSelection();
            fragmentOpener(new HomeFragment(), "HomeFragment");
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
            fragmentOpener(new ProfileFragment(), "ProfileFragment");
        });

        // Load selected fragment only once when activity is created
        if (savedInstanceState == null) {
            applyTabSelection();
            switch (selectedTabIndex) {
                case 0:
                    fragmentOpener(new HomeFragment(), "HomeFragment");
                    break;
                case 2:
                    fragmentOpener(new ProfileFragment(), "ProfileFragment");
                    break;
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyTabSelection();
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

    private void fragmentOpener(Fragment fragment, String tag) {
        FragmentManager fm = getSupportFragmentManager();
        Fragment currentFragment = fm.findFragmentById(R.id.fragment);
        Fragment existingFragment = fm.findFragmentByTag(tag);

        if (existingFragment != null && existingFragment == currentFragment) {
            return;
        }

        FragmentTransaction ft = fm.beginTransaction();

        if (existingFragment == null) {
            ft.replace(R.id.fragment, fragment, tag);
        } else {
            ft.replace(R.id.fragment, existingFragment, tag);
        }

        ft.commit();
    }

    @Override
    protected void onPause() {
        super.onPause();
        screenHide.setAlpha(0f);
        screenHide.setVisibility(View.VISIBLE);
        screenHide.animate().alpha(1f).setDuration(200).start();
    }


    @Override
    protected void onRestart() {
        super.onRestart();
        Intent reLogin = new Intent(this, Password_Page.class);
        startActivity(reLogin);
        finish();
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
