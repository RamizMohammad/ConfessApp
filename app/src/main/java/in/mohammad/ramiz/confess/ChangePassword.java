package in.mohammad.ramiz.confess;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;

import in.mohammad.ramiz.confess.debugmonitor.TelegramLogs;
import in.mohammad.ramiz.confess.encryptiondb.SaltRepo;
import in.mohammad.ramiz.confess.entities.ChangePasswordRequest;
import in.mohammad.ramiz.confess.entities.ChangePasswordResponse;
import in.mohammad.ramiz.confess.entities.ForgotPasswordRequest;
import in.mohammad.ramiz.confess.entities.ForgotPasswordResponse;
import in.mohammad.ramiz.confess.haptics.VibManager;
import in.mohammad.ramiz.confess.popups.OkPopUp;
import in.mohammad.ramiz.confess.popups.OnlyLoader;
import in.mohammad.ramiz.confess.server.Endpoints;
import in.mohammad.ramiz.confess.server.ServerConfigs;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChangePassword extends AppCompatActivity {

    private EditText oldPassword, newPassword;
    private ImageView oldIcon , newIcon;
    private FrameLayout updatePassword;
    private boolean isNewPasswordShowing = false;
    private boolean isOldPasswordShowing = false;
    private TextView forgotButton;
    private Endpoints endpoints;
    private OnlyLoader loader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_change_password);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        endpoints = ServerConfigs.getInstance().create(Endpoints.class);

        oldPassword = findViewById(R.id.oldPassword);
        newPassword = findViewById(R.id.password);
        newIcon = findViewById(R.id.showButton);
        oldIcon = findViewById(R.id.oldShowButton);
        forgotButton = findViewById(R.id.forgotPasswordButton);
        updatePassword = findViewById(R.id.updatePasswordButton);

        LinearLayout passwordfeild = findViewById(R.id.oldPasswordEntryPanel);

        Drawable orignal = ContextCompat.getDrawable(this, R.drawable.text_field);
        GradientDrawable copy = (GradientDrawable) orignal.mutate().getConstantState().newDrawable().mutate();

        passwordfeild.setBackground(copy);

        newIcon.setOnClickListener(v -> {
            VibManager.vibrateTick(this);
            if (!isNewPasswordShowing) {
                newPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                newIcon.setImageResource(R.drawable.hide);
            } else {
                newPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                newIcon.setImageResource(R.drawable.show);
            }
            newPassword.setSelection(newPassword.getText().length());
            isNewPasswordShowing = !isNewPasswordShowing;
        });

        oldIcon.setOnClickListener(v -> {
            VibManager.vibrateTick(this);
            if (!isOldPasswordShowing) {
                oldPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                oldIcon.setImageResource(R.drawable.hide);
            } else {
                oldPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                oldIcon.setImageResource(R.drawable.show);
            }
            oldPassword.setSelection(oldPassword.getText().length());
            isOldPasswordShowing = !isOldPasswordShowing;
        });

        updatePassword.setOnClickListener(v -> {
            VibManager.vibrateTick(this);
            if(account != null){
                if(!TextUtils.isEmpty(oldPassword.getText()) && !TextUtils.isEmpty(newPassword.getText())){
                    String email = account.getEmail();
                    String oldPass = oldPassword.getText().toString();
                    String newPass = newPassword.getText().toString();
                    loader = new OnlyLoader(ChangePassword.this, R.raw.loading_animation);
                    changePassword(email, oldPass, newPass, new ChangePasswordCallback() {
                        @Override
                        public void onSuccess() {
                            loader.dismiss();
                            Toast.makeText(ChangePassword.this, "Password updated", Toast.LENGTH_SHORT).show();
                            Intent change = new Intent(ChangePassword.this, HomePage.class);
                            startActivity(change);
                            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                            finish();
                        }

                        @Override
                        public void onWrongPassword() {
                            loader.dismiss();
                            oldPassword.setText("");
                            oldPassword.setHint("wrong password");
                            newPassword.setText("");
                            copy.setStroke(1, getColor(R.color.falseRed));
                        }

                        @Override
                        public void onFail() {
                            new OkPopUp(ChangePassword.this, R.raw.error_animation, "We got some server error");
                        }
                    });
                }
                else{
                    new OkPopUp(ChangePassword.this, R.raw.file_not_found, "We found empty fields");
                }
            }
        });

        forgotButton.setOnClickListener(v -> {
            VibManager.vibrateTick(this);
            if(account != null){
                String email = account.getEmail();
                loader = new OnlyLoader(ChangePassword.this, R.raw.loading_animation);
                forgotPassword(email, new ForgotPasswordCallback() {
                    @Override
                    public void onSuccess() {
                        loader.dismiss();
                        Toast.makeText(ChangePassword.this, "Reset link has been sent", Toast.LENGTH_SHORT).show();
                        Intent changeIntent = new Intent(ChangePassword.this, HomePage.class);
                        startActivity(changeIntent);
                        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                        finish();
                    }

                    @Override
                    public void onFailure() {
                        loader.dismiss();
                        new OkPopUp(ChangePassword.this, R.raw.error_animation,"We got some server error");
                    }
                });
            }
        });
    }

    public interface ForgotPasswordCallback{
        void onSuccess();
        void onFailure();
    }

    public interface ChangePasswordCallback{
        void onSuccess();
        void onWrongPassword();
        void onFail();
    }

    private void forgotPassword(String email, ForgotPasswordCallback callback){
        ForgotPasswordRequest request = new ForgotPasswordRequest(email);

        Call<ForgotPasswordResponse> call = endpoints.forgotUserPassword(BuildConfig.CLIENT_API, request);

        call.enqueue(new Callback<ForgotPasswordResponse>() {
            @Override
            public void onResponse(Call<ForgotPasswordResponse> call, Response<ForgotPasswordResponse> response) {
                if(response.isSuccessful() && response.body() != null){
                    callback.onSuccess();
                }
                else{
                    callback.onFailure();
                }
            }

            @Override
            public void onFailure(Call<ForgotPasswordResponse> call, Throwable t) {
                TelegramLogs.sendTelegramLog("There is an error");
                callback.onFailure();
            }
        });
    }

    private void changePassword(String email, String oldPassword, String newPassword, ChangePasswordCallback callback){

        ChangePasswordRequest request = new ChangePasswordRequest(email, oldPassword, newPassword);

        Call<ChangePasswordResponse> call = endpoints.changesPassword(BuildConfig.CLIENT_API, request);

        call.enqueue(new Callback<ChangePasswordResponse>() {
            @Override
            public void onResponse(Call<ChangePasswordResponse> call, Response<ChangePasswordResponse> response) {
                if(response.isSuccessful() && response.body() != null){
                    if(response.body().isMessage() && response.body().getLabel().equals("done")){
                        callback.onSuccess();
                    }
                    else if(response.body().isMessage() && response.body().getLabel().equals("wrong")){
                        callback.onWrongPassword();
                    }
                }
                else {
                    callback.onFail();
                }
            }

            @Override
            public void onFailure(Call<ChangePasswordResponse> call, Throwable t) {
                TelegramLogs.sendTelegramLog("There is and error found");
                callback.onFail();
            }
        });
    }
}