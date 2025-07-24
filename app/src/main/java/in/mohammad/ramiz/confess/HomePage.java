package in.mohammad.ramiz.confess;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
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

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;

import java.util.concurrent.Executors;

import in.mohammad.ramiz.confess.entities.FcmTokenRequest;
import in.mohammad.ramiz.confess.entities.FcmTokenResponse;
import in.mohammad.ramiz.confess.haptics.VibManager;
import in.mohammad.ramiz.confess.security.BiometricPrefs;
import in.mohammad.ramiz.confess.server.Endpoints;
import in.mohammad.ramiz.confess.server.ServerConfigs;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomePage extends AppCompatActivity {

    private ImageView homeLogo, addLogo, profileLogo;
    private TextView homeText, profileText;
    private LinearLayout homeButton, addButton, profileButton;
    private FrameLayout screenHide;
    private Endpoints endpoints;
    private static final long SESSION_TIME = 60 * 1000;
    private static long CURRENT_SESSION = 0;

    // Persistent selection even when returning from other activities
    public static int selectedTabIndex = 0; // 0 = Home, 2 = Profile
    private int previousTabIndex = 0; // Used for Add button restore

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
        screenHide = findViewById(R.id.secureOverlay);

        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        endpoints = ServerConfigs.getInstance().create(Endpoints.class);
        if(account != null){
            String email = account.getEmail();
            Executors.newSingleThreadExecutor().execute(() -> {
                sendFcmToServer(email);
            });
        }

        // Click listeners for navigation
        homeButton.setOnClickListener(v -> {
            VibManager.vibrateTick(this);
            selectedTabIndex = 0;
            applyTabSelection();
            fragmentOpener(new HomeFragment(), "HomeFragment");
        });

        addButton.setOnClickListener(v -> {
            VibManager.vibrateTick(this);
            previousTabIndex = selectedTabIndex; // Save previous tab
            selectedTabIndex = 1; // Temporarily highlight Add button
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

        // Load the correct fragment initially
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

        // If Add activity was opened, revert to previous tab
        if (selectedTabIndex == 1) {
            selectedTabIndex = previousTabIndex;
        }

        applyTabSelection();

        // Ensure fragment is visible after returning
        switch (selectedTabIndex) {
            case 0:
                fragmentOpener(new HomeFragment(), "HomeFragment");
                break;
            case 2:
                fragmentOpener(new ProfileFragment(), "ProfileFragment");
                break;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        CURRENT_SESSION = System.currentTimeMillis();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (CURRENT_SESSION > 0) {
            long timeOuts = System.currentTimeMillis() - CURRENT_SESSION;
            CURRENT_SESSION = 0;
            if (timeOuts >= SESSION_TIME) {
                if(BiometricPrefs.getInstance(this).getPasswordStatus()){
                    Intent reLogin = new Intent(this, Password_Page.class);
                    startActivity(reLogin);
                    finish();
                }
            }
        }
    }

    /**
     * Highlights selected tab and updates UI colors
     */
    private void applyTabSelection() {
        int selectedColor = getResources().getColor(R.color.yellow, getTheme());
        int unselectedColor = getResources().getColor(R.color.white, getTheme());

        switch (selectedTabIndex) {
            case 0: // Home
                setSelectedTab(homeLogo, homeText, selectedColor);
                setUnselectedTab(addLogo, null, unselectedColor);
                setUnselectedTab(profileLogo, profileText, unselectedColor);
                break;
            case 1: // Add (temporary)
                setUnselectedTab(homeLogo, homeText, unselectedColor);
                setSelectedTab(addLogo, null, selectedColor);
                setUnselectedTab(profileLogo, profileText, unselectedColor);
                break;
            case 2: // Profile
                setUnselectedTab(homeLogo, homeText, unselectedColor);
                setUnselectedTab(addLogo, null, unselectedColor);
                setSelectedTab(profileLogo, profileText, selectedColor);
                break;
        }
    }

    /**
     * Opens a fragment, keeps others in memory using add/show/hide
     */
    private void fragmentOpener(Fragment fragment, String tag) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();

        // Hide all current fragments
        for (Fragment frag : fm.getFragments()) {
            ft.hide(frag);
        }

        Fragment existingFragment = fm.findFragmentByTag(tag);

        if (existingFragment == null) {
            ft.add(R.id.fragment, fragment, tag);
        } else {
            ft.show(existingFragment);
        }

        ft.commit();
    }

    private void setSelectedTab(ImageView icon, TextView text, int color) {
        icon.setImageTintList(ColorStateList.valueOf(color));
        if (text != null) text.setTextColor(color);
    }

    private void setUnselectedTab(ImageView icon, TextView text, int color) {
        icon.setImageTintList(ColorStateList.valueOf(color));
        if (text != null) text.setTextColor(color);
    }

    private void sendFcmToServer(String email){
        BiometricPrefs pref = BiometricPrefs.getInstance(this);
        FcmTokenRequest request = new FcmTokenRequest(email, pref.getFcmValue(), "active");

        Call<FcmTokenResponse> call = endpoints.fcm(BuildConfig.CLIENT_API, request);

        call.enqueue(new Callback<FcmTokenResponse>() {
            @Override
            public void onResponse(Call<FcmTokenResponse> call, Response<FcmTokenResponse> response) {
            }

            @Override
            public void onFailure(Call<FcmTokenResponse> call, Throwable t) {

            }
        });
    }
}
