package in.mohammad.ramiz.confess;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
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

import in.mohammad.ramiz.confess.debugmonitor.TelegramLogs;
import in.mohammad.ramiz.confess.entities.UpdatePasswordStateRequest;
import in.mohammad.ramiz.confess.entities.UpdatePasswordStateResponse;
import in.mohammad.ramiz.confess.haptics.VibManager;
import in.mohammad.ramiz.confess.popups.OkPopUp;
import in.mohammad.ramiz.confess.popups.OnlyLoader;
import in.mohammad.ramiz.confess.security.BiometricLogin;
import in.mohammad.ramiz.confess.security.BiometricPrefs;
import in.mohammad.ramiz.confess.server.Endpoints;
import in.mohammad.ramiz.confess.server.ServerConfigs;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UpdatePassBio extends AppCompatActivity {

    private ImageView showButton, biometric;
    private EditText passwordField;
    private LinearLayout biometricToggle;
    private FrameLayout updateButton;
    private Endpoints endpoints;
    private OnlyLoader loader;
    private int countLength, MAX_ATTEMPT = 3, CURRENT_COUNT = 0;
    private boolean isPasswordShowing = false;
    private boolean isBioAnimate = false, isBioPlay = true, isBiometric = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_update_pass_bio);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        showButton = findViewById(R.id.showButton);
        passwordField = findViewById(R.id.password);
        biometric = findViewById(R.id.biometricIcon);
        biometricToggle = findViewById(R.id.biometricToggle);
        updateButton = findViewById(R.id.updatePasswordButton);

        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        endpoints = ServerConfigs.getInstance().create(Endpoints.class);

        showButton.setOnClickListener(v -> {
            VibManager.vibrateTick(this);
            if (!isPasswordShowing) {
                passwordField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                showButton.setImageResource(R.drawable.hide);
            } else {
                passwordField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                showButton.setImageResource(R.drawable.show);
            }
            passwordField.setSelection(passwordField.getText().length());
            isPasswordShowing = !isPasswordShowing;
        });

        Glide.with(this)
                .asBitmap()
                .load(R.raw.biometric)
                .into(biometric);

        biometricToggle.setOnClickListener(v -> {
            handleGifOnlyToggle(this, R.raw.biometric, biometric);
        });

        updateButton.setOnClickListener(v -> {
            VibManager.vibrateTick(this);
            if(account != null && !TextUtils.isEmpty(passwordField.getText())){
                String email = account.getEmail();
                String password = passwordField.getText().toString();
                if(isBiometric){
                    addBiometricInfo(new Runnable() {
                        @Override
                        public void run() {
                            loader = new OnlyLoader(UpdatePassBio.this, R.raw.loading_animation);
                            onUpdateFunctionCall(UpdatePassBio.this, email, password, true, new Oncallback() {
                                @Override
                                public void onSuccess(boolean result) {
                                    loader.dismiss();
                                    BiometricPrefs.getInstance(UpdatePassBio.this).setPasswordStatus(true);
                                    BiometricPrefs.getInstance(UpdatePassBio.this).setBiometricEnabled(true);
                                    BiometricPrefs.getInstance(UpdatePassBio.this).setBiometricLogin(true);
                                    Toast.makeText(UpdatePassBio.this, "Biometric and password added", Toast.LENGTH_SHORT).show();
                                    Intent backIntent = new Intent(UpdatePassBio.this, Password_Page.class);
                                    startActivity(backIntent);
                                    finish();
                                }

                                @Override
                                public void onFailure() {
                                    TelegramLogs.sendTelegramLog("We got some server error");
                                    new OkPopUp(UpdatePassBio.this, R.raw.error_animation, "We got server error");
                                }
                            });
                        }
                    });
                }
                else{
                    loader = new OnlyLoader(this, R.raw.loading_animation);
                    onUpdateFunctionCall(this, email, password, false, new Oncallback() {
                        @Override
                        public void onSuccess(boolean result) {
                            loader.dismiss();
                            BiometricPrefs.getInstance(UpdatePassBio.this).setPasswordStatus(true);
                            BiometricPrefs.getInstance(UpdatePassBio.this).setBiometricLogin(false);
                            BiometricPrefs.getInstance(UpdatePassBio.this).setBiometricEnabled(false);
                            Toast.makeText(UpdatePassBio.this, "Password added successfully", Toast.LENGTH_SHORT).show();
                            Intent backIntent = new Intent(UpdatePassBio.this, Password_Page.class);
                            startActivity(backIntent);
                            finish();
                        }

                        @Override
                        public void onFailure() {
                            TelegramLogs.sendTelegramLog("We got some server error");
                            new OkPopUp(UpdatePassBio.this, R.raw.error_animation, "We got server error");
                        }
                    });
                }
            }
        });
    }

    public void handleGifOnlyToggle(Context context, int gifResId, ImageView targetImageView) {
        if (isBioAnimate) return;

        VibManager.vibrateTick(context);
        isBioAnimate = true;

        if (isBioPlay) {
            Glide.with(context)
                    .asGif()
                    .load(gifResId)
                    .into(new CustomTarget<GifDrawable>() {
                        @Override
                        public void onResourceReady(@NonNull GifDrawable resource, @Nullable Transition<? super GifDrawable> transition) {
                            resource.setLoopCount(1);
                            targetImageView.setImageDrawable(resource);
                            resource.start();

                            resource.registerAnimationCallback(new Animatable2Compat.AnimationCallback() {
                                @Override
                                public void onAnimationEnd(Drawable drawable) {
                                    isBioAnimate = false;
                                    isBioPlay = false;
                                    isBiometric = true;

                                    Glide.with(context)
                                            .asBitmap()
                                            .load(R.raw.last_frame)
                                            .into(targetImageView);
                                }
                            });
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {}
                    });

        } else {
            Glide.with(context)
                    .asBitmap()
                    .load(gifResId)
                    .into(new CustomTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                            targetImageView.setImageBitmap(resource);
                            isBioAnimate = false;
                            isBioPlay = true;
                            isBiometric = false;
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {}
                    });
        }
    }

    public void onUpdateFunctionCall(Activity activity, String email, String password, boolean biometricStatus, Oncallback oncallback){

        UpdatePasswordStateRequest request = new UpdatePasswordStateRequest(email, password, biometricStatus);

        Call<UpdatePasswordStateResponse> call = endpoints.addPassword(BuildConfig.CLIENT_API, request);

        call.enqueue(new Callback<UpdatePasswordStateResponse>() {
            @Override
            public void onResponse(Call<UpdatePasswordStateResponse> call, Response<UpdatePasswordStateResponse> response) {
                if(response.isSuccessful() && response.body() != null){
                    oncallback.onSuccess(response.body().isMessage());
                }
                else {
                    oncallback.onFailure();
                }
            }

            @Override
            public void onFailure(Call<UpdatePasswordStateResponse> call, Throwable t) {
                oncallback.onFailure();
            }
        });
    }

    private void addBiometricInfo(Runnable runnable){
        BiometricLogin.authenticte(this, new BiometricLogin.AuthCallback() {
            @Override
            public void onSuccess() {
                runnable.run();
            }

            @Override
            public void onError(String e) {
                TelegramLogs.sendTelegramLog("We got an error on biometric setup"+e);
                new OkPopUp(UpdatePassBio.this, R.raw.error_animation, "Unable to setup biometric");
            }

            @Override
            public void onFail() {
                CURRENT_COUNT++;
                if(CURRENT_COUNT <= MAX_ATTEMPT){
                    Toast.makeText(UpdatePassBio.this,
                            "Try setting up again",
                            Toast.LENGTH_SHORT).show();
                    addBiometricInfo(runnable);
                }
                else {
                    finish();
                }
            }

            @Override
            public void onUsePassword() {
                new OkPopUp(UpdatePassBio.this, R.raw.error_animation, "Unable to setup biometric");
            }
        });
    }

    public interface Oncallback{
        void onSuccess(boolean result);
        void onFailure();
    }
}