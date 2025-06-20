package in.mohammad.ramiz.confess;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.vectordrawable.graphics.drawable.Animatable2Compat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;

import java.text.SimpleDateFormat;
import java.util.Date;

import in.mohammad.ramiz.confess.debugmonitor.TelegramLogs;
import in.mohammad.ramiz.confess.entities.AddUserRequest;
import in.mohammad.ramiz.confess.entities.AddUserResponse;
import in.mohammad.ramiz.confess.haptics.VibManager;
import in.mohammad.ramiz.confess.popups.OkPopUp;
import in.mohammad.ramiz.confess.popups.OnlyLoader;
import in.mohammad.ramiz.confess.server.Endpoints;
import in.mohammad.ramiz.confess.server.ServerConfigs;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AlaisPage extends AppCompatActivity {

    private ImageView lockIcon, showPassowrd;
    private boolean isAnimating = false;
    private boolean playNext = true;
    private LinearLayout passwordButton, passwordPanel;
    private int gif = R.raw.lock_animation;
    private boolean isPassword = false, isShowPassword = false;
    private FrameLayout joinButton;
    private EditText aliasName, password;
    private OkPopUp popUp;
    private Endpoints endpoints;
    private String email = null, token = null;
    private OnlyLoader loader;
    private GradientDrawable drawable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_alais_page);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        lockIcon = findViewById(R.id.lockIcon);
        passwordButton = findViewById(R.id.passwordToggle);
        passwordPanel = findViewById(R.id.passwordPanel);
        joinButton = findViewById(R.id.createAccountButton);
        aliasName = findViewById(R.id.aliasName);
        password = findViewById(R.id.password);
        showPassowrd = findViewById(R.id.showButton);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String date = sdf.format(new Date());

        Drawable background = aliasName.getBackground();
        if(background instanceof GradientDrawable){
            drawable = (GradientDrawable) background;
        }

        int color = ContextCompat.getColor(this, R.color.falseRed);

        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        endpoints = ServerConfigs.getInstance().create(Endpoints.class);

        aliasName.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                VibManager.vibrateTick(v.getContext());
            }
        });

        password.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                VibManager.vibrateTick(v.getContext());
            }
        });

        showPassowrd.setOnClickListener(v -> {
            VibManager.vibrateTick(this);
            if (!isShowPassword) {
                password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            } else {
                password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            }
            password.setSelection(password.getText().length());
            isShowPassword = !isShowPassword;
        });

        joinButton.setOnClickListener(v -> {
            VibManager.vibrateTick(this);
            if(account != null){
                email = account.getEmail();
                token = account.getIdToken();
            }
            String userAliasName = aliasName.getText().toString();

            if(isPassword){
                String userPassword = password.getText().toString();

                if(!TextUtils.isEmpty(userAliasName) && !TextUtils.isEmpty(userPassword)){
                    loader = new OnlyLoader(this, R.raw.loading_animation);
                    createNewUser(token, email,userAliasName,
                            date,isPassword,
                            userPassword,this,
                            ((isAliasName, isUserCreated) -> {

                                if(loader != null){
                                    loader.dismiss();
                                }

                                if(isAliasName){
                                    drawable.setStroke(2, color);
                                    aliasName.setText("");
                                    aliasName.setHint("alias already taken");
                                }
                                else if(isUserCreated){
                                    Intent welcomePageIntent = new Intent(this, WelcomeUser.class);
                                    startActivity(welcomePageIntent);
                                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                                    finish();
                                }
                            }));
                }
                else{
                    popUp = new OkPopUp(this, R.raw.file_not_found, "Some fields are found empty");
                    }
                }
            else{
                if(!TextUtils.isEmpty(userAliasName)){
                    loader = new OnlyLoader(this, R.raw.loading_animation);
                    createNewUser(token, email,userAliasName,
                            date,false,
                            null,this,
                            ((isAliasName, isUserCreated) -> {

                                if(loader != null){
                                    loader.dismiss();
                                }

                                if(isAliasName){
                                    drawable.setStroke(2, color);
                                    aliasName.setText("");
                                    aliasName.setHint("Alias already taken");
                                }
                                else if(isUserCreated){
                                    Intent welcomePageIntent = new Intent(this, WelcomeUser.class);
                                    startActivity(welcomePageIntent);
                                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                                    finish();
                                }
                            }));
                }
                else {
                    popUp = new OkPopUp(this, R.raw.file_not_found, "Some fields are found empty");
                }
            }
        });

        // Load initial still image
        Glide.with(this)
                .asBitmap()
                .load(gif)
                .into(lockIcon);

        passwordButton.setOnClickListener(v -> {
            VibManager.vibrateTick(this);
            if (isAnimating) return;
            isAnimating = true;

            if (playNext) {
                passwordPanel.setVisibility(View.VISIBLE);
                // Play animation once
                Glide.with(this)
                        .asGif()
                        .load(gif)
                        .into(new CustomTarget<GifDrawable>() {
                            @Override
                            public void onResourceReady(GifDrawable resource, Transition<? super GifDrawable> transition) {
                                resource.setLoopCount(1);
                                lockIcon.setImageDrawable(resource);
                                resource.start();

                                resource.registerAnimationCallback(new Animatable2Compat.AnimationCallback() {
                                    @Override
                                    public void onAnimationEnd(Drawable drawable) {
                                        isAnimating = false;
                                        playNext = false;
                                        isPassword = true;
                                    }
                                });
                            }

                            @Override
                            public void onLoadCleared(Drawable placeholder) {}
                        });

            } else {
                // Reset to first frame
                Glide.with(this)
                        .asBitmap()
                        .load(gif)
                        .into(new CustomTarget<Bitmap>() {
                            @Override
                            public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                                lockIcon.setImageBitmap(resource);
                                isAnimating = false;
                                playNext = true;
                                isPassword = false;
                                passwordPanel.setVisibility(View.GONE);
                            }

                            @Override
                            public void onLoadCleared(Drawable placeholder) {}
                        });
            }
        });
    }

    protected void createNewUser(String token, String email,
                                 String aliasName,String date,
                                 boolean isPassword, String passwordData, Activity activity,
                                 UserCheckCallback callback){

        AddUserRequest addUserRequestBody = new AddUserRequest(token, email, aliasName, date, isPassword, passwordData);
        Call<AddUserResponse> call = endpoints.createNewUser(BuildConfig.CLIENT_API,addUserRequestBody);

        call.enqueue(new Callback<AddUserResponse>() {
            @Override
            public void onResponse(Call<AddUserResponse> call, Response<AddUserResponse> response) {
                if(response.isSuccessful() && response.body() != null){

                    boolean aliasExistance = response.body().isAliasName();
                    boolean userCreation = response.body().isUserCreated();

                    callback.onResult(aliasExistance, userCreation);
                }
                else{
                    callback.onResult(false, false);
                    TelegramLogs.sendTelegramLog("We got an empty response");
                    popUp = new OkPopUp(activity, R.raw.error_animation, "Got a server error.");
                }
            }

            @Override
            public void onFailure(Call<AddUserResponse> call, Throwable throwable) {
                popUp = new OkPopUp(activity, R.raw.error_animation, "We Encounter the server error");
                TelegramLogs.sendTelegramLog("Server error encountered\n"+throwable);
            }
        });
    }

    public interface UserCheckCallback {
        void onResult(boolean isAliasName, boolean isUserCreated);
    }
}
