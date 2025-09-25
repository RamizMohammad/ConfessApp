package in.mohammad.ramiz.confess;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.concurrent.Executors;

import in.mohammad.ramiz.confess.debugmonitor.TelegramLogs;
import in.mohammad.ramiz.confess.entities.CheckUserPassRequest;
import in.mohammad.ramiz.confess.entities.CheckUserPassResponse;
import in.mohammad.ramiz.confess.popups.OnlyLoader;
import in.mohammad.ramiz.confess.security.BiometricPrefs;
import in.mohammad.ramiz.confess.server.Endpoints;
import in.mohammad.ramiz.confess.server.ServerConfigs;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class Launcher extends AppCompatActivity {

    private Endpoints endpoints;
    private OnlyLoader loader;
    private Handler handler = new Handler(Looper.getMainLooper());

    private boolean navigationTriggered = false; // ✅ prevent double navigation

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        endpoints = ServerConfigs.getInstance().create(Endpoints.class);

        Executors.newSingleThreadExecutor().execute(this::fetchFcm);

        if (account != null) {
            String email = account.getEmail();
            loader = new OnlyLoader(this, R.raw.loading_animation);

            // Optional: hide loader if waiting too long, but DON'T navigate
            handler.postDelayed(() -> {
                if (loader != null && loader.isShowing() && !navigationTriggered) {
                    Log.w("Launcher", "⏳ Loader timeout reached (UI hidden, still waiting for API)");
                    loader.dismiss();
                    TelegramLogs.sendTelegramLog("Loader hidden due to timeout, still waiting for API in Launcher");
                }
            }, 10000); // 10s max loader, but keep waiting

            checkUser(email, (isUser, isPassword, isBiometric) -> {
                if (!navigationTriggered) {
                    navigationTriggered = true; // ✅ avoid double calls
                    if (loader != null && loader.isShowing()) {
                        loader.dismiss();
                    }

                    if (isUser && isPassword) {
                        BiometricPrefs.getInstance(this).setPasswordStatus(true);
                        if (isBiometric) {
                            BiometricPrefs.getInstance(this).setBiometricEnabled(true);
                            BiometricPrefs.getInstance(this).setBiometricLogin(true);
                        } else {
                            BiometricPrefs.getInstance(this).setBiometricEnabled(false);
                            BiometricPrefs.getInstance(this).setBiometricLogin(false);
                        }
                        startActivity(new Intent(this, Password_Page.class));
                    } else if (isUser) {
                        BiometricPrefs.getInstance(this).setPasswordStatus(false);
                        startActivity(new Intent(this, HomePage.class));
                    } else {
                        startActivity(new Intent(this, MainActivity.class));
                    }
                    finish(); // ✅ only finish AFTER we know backup also had a chance
                }
            });

        } else {
            if (loader != null && loader.isShowing()) {
                loader.dismiss();
            }
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }
    }

    private void checkUser(String email, UserCallback callback) {
        CheckUserPassRequest request = new CheckUserPassRequest(email);
        Call<CheckUserPassResponse> call = endpoints.checkUserPass(BuildConfig.CLIENT_API, request);

        call.enqueue(new Callback<CheckUserPassResponse>() {
            @Override
            public void onResponse(Call<CheckUserPassResponse> call, Response<CheckUserPassResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onResult(response.body().isUser(), response.body().isPassword(), response.body().isBiometric());
                } else {
                    TelegramLogs.sendTelegramLog("Null response body in LauncherActivity");
                    callback.onResult(false, false, false);
                }
            }

            @Override
            public void onFailure(Call<CheckUserPassResponse> call, Throwable t) {
                TelegramLogs.sendTelegramLog("LauncherActivity error: " + t.getMessage());
                callback.onResult(false, false, false);
            }
        });
    }

    public interface UserCallback {
        void onResult(boolean isUser, boolean isPassword, boolean isBiometric);
    }

    private void fetchFcm() {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        BiometricPrefs.getInstance(this).setFcmKey(true);
                        BiometricPrefs.getInstance(this).setFcmValue(task.getResult());
                    } else {
                        TelegramLogs.sendTelegramLog("FCM token fetch failed");
                    }
                });
    }
}
