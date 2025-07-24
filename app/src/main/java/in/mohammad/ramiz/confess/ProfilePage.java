package in.mohammad.ramiz.confess;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;

import in.mohammad.ramiz.confess.debugmonitor.TelegramLogs;
import in.mohammad.ramiz.confess.entities.UpdateProfilePictureRequest;
import in.mohammad.ramiz.confess.entities.UpdateProfilePictureResponse;
import in.mohammad.ramiz.confess.haptics.VibManager;
import in.mohammad.ramiz.confess.popups.OkPopUp;
import in.mohammad.ramiz.confess.popups.OnlyLoader;
import in.mohammad.ramiz.confess.server.Endpoints;
import in.mohammad.ramiz.confess.server.ServerConfigs;
import in.mohammad.ramiz.confess.storage.CloudinaryStorageManager;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfilePage extends AppCompatActivity {

    private ImageView dp;
    private ImageView[] avatars;
    private Drawable[] originalBackgrounds;
    private int selectedAvatar;
    private FrameLayout profileButton;
    private OnlyLoader loader;
    private Endpoints endpoints;
    private int[] avatarRes = {
            R.raw.avatar1, R.raw.avatar2, R.raw.avatar3,
            R.raw.avatar4, R.raw.avatar5, R.raw.avatar6
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_profile_page);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        dp = findViewById(R.id.displayProfile);
        profileButton = findViewById(R.id.profileButton);

        Intent old = getIntent();
        String caller = old.getStringExtra("Calling");

        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        endpoints = ServerConfigs.getInstance().create(Endpoints.class);

        avatars = new ImageView[]{
                findViewById(R.id.profile1),
                findViewById(R.id.profile2),
                findViewById(R.id.profile3),
                findViewById(R.id.profile4),
                findViewById(R.id.profile5),
                findViewById(R.id.profile6)
        };

        originalBackgrounds = new Drawable[avatars.length];
        for (int i = 0; i < avatars.length; i++) {
            originalBackgrounds[i] = avatars[i].getBackground();
        }

        profileSetter(avatarRes[0], avatars[0]);

        for (int i = 0; i < avatars.length; i++) {
            final int index = i;
            avatars[i].setOnClickListener(v -> {
                VibManager.vibrateTick(this);
                profileSetter(avatarRes[index], avatars[index]);
            });
        }

        profileButton.setOnClickListener(v -> {
            VibManager.vibrateTick(this);
            loader = new OnlyLoader(this, R.raw.loading_animation);
            Uri uri = CloudinaryStorageManager.getUriFromResId(this, selectedAvatar);
            CloudinaryStorageManager.uploadImage(this, uri, new CloudinaryStorageManager.UploadResultCallback() {
                @Override
                public void onSuccess(String downloadUrl) {
                    if(account != null){
                        String email = account.getEmail();
                        updateProfile(email, downloadUrl, new ProfileCallback() {
                            @Override
                            public void onSuccess() {
                                loader.dismiss();
                                if(caller.equals("Home")){
                                    Intent welcomeIntent = new Intent(ProfilePage.this, HomePage.class);
                                    startActivity(welcomeIntent);
                                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                                    finish();
                                }
                                else{
                                    Intent welcomeIntent = new Intent(ProfilePage.this, WelcomeUser.class);
                                    startActivity(welcomeIntent);
                                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                                    finish();
                                }
                            }

                            @Override
                            public void onUpdateFailed() {
                                loader.dismiss();
                                new OkPopUp(ProfilePage.this, R.raw.error_animation, "Update of profile failed");
                            }

                            @Override
                            public void onFailure() {
                                loader.dismiss();
                                new OkPopUp(ProfilePage.this, R.raw.error_animation, "Server error");
                            }
                        });
                    }
                }

                @Override
                public void onFailure(String message) {
                    loader.dismiss();
                    TelegramLogs.sendTelegramLog(message);
                    new OkPopUp(ProfilePage.this, R.raw.error_animation, "Server Error");
                }
            });
        });
    }

    private void profileSetter(int profilePath, ImageView selectedImage) {
        // Fade-out, change image, then fade-in
        dp.animate()
                .alpha(0f)
                .setDuration(150)
                .withEndAction(() -> {
                    dp.setImageResource(profilePath);
                    dp.animate().alpha(1f).setDuration(150).start();
                })
                .start();

        // Reset backgrounds
        for (int i = 0; i < avatars.length; i++) {
            avatars[i].setBackground(originalBackgrounds[i]);
        }

        // Highlight selected avatar with quick pulse
        selectedImage.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction(() -> selectedImage.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start())
                .start();
        selectedAvatar = profilePath;
        selectedImage.setBackgroundResource(R.drawable.selection_design);
    }

    private void updateProfile(String email, String Url, ProfileCallback callback){

        UpdateProfilePictureRequest request = new UpdateProfilePictureRequest(email, Url);

        Call<UpdateProfilePictureResponse> call = endpoints.updateProfilePicture(BuildConfig.CLIENT_API, request);

        call.enqueue(new Callback<UpdateProfilePictureResponse>() {
            @Override
            public void onResponse(Call<UpdateProfilePictureResponse> call, Response<UpdateProfilePictureResponse> response) {
                if(response.isSuccessful() && response.body() != null){
                    if(response.body().isMessage()){
                        callback.onSuccess();
                    }
                    else{
                        callback.onUpdateFailed();
                    }
                }
                else {
                    callback.onFailure();
                }
            }

            @Override
            public void onFailure(Call<UpdateProfilePictureResponse> call, Throwable t) {
                TelegramLogs.sendTelegramLog("There is a error"+t);
                callback.onFailure();
            }
        });
    }

    public interface ProfileCallback{
        void onSuccess();
        void onUpdateFailed();
        void onFailure();
    }
}
