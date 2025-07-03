package in.mohammad.ramiz.confess;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;

import in.mohammad.ramiz.confess.debugmonitor.TelegramLogs;
import in.mohammad.ramiz.confess.entities.CheckUserPassRequest;
import in.mohammad.ramiz.confess.entities.CheckUserPassResponse;
import in.mohammad.ramiz.confess.server.Endpoints;
import in.mohammad.ramiz.confess.server.ServerConfigs;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class Launcher extends AppCompatActivity {

    private Endpoints endpoints;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        endpoints = ServerConfigs.getInstance().create(Endpoints.class);

        if (account != null) {
            String email = account.getEmail();

            checkUser(email, (isUser, isPassword, isBiometric) -> {
                if (isUser && isPassword) {
                    Intent passwordIntent = new Intent(this, Password_Page.class);
                    passwordIntent.putExtra("biometricStatus", isBiometric);
                    startActivity(passwordIntent);
                }
                else if(isUser) {
                    startActivity(new Intent(this, HomePage.class));
                } else {
                    startActivity(new Intent(this, MainActivity.class));
                }
                finish();
            });

        } else {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }
    }

    private void checkUser(String email, UserCallback callback) {
        CheckUserPassRequest request = new CheckUserPassRequest(email);
        Call<CheckUserPassResponse> call = endpoints.checkUserPass(BuildConfig.CLIENT_API,request);

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

    public interface UserCallback{
        void onResult (boolean isUser,boolean isPassword, boolean isBiometric);
    }
}
