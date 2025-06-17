package in.mohammad.ramiz.confess;

import android.app.Activity;
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
import in.mohammad.ramiz.confess.entities.CheckPasswordRequest;
import in.mohammad.ramiz.confess.entities.CheckPasswordResponse;
import in.mohammad.ramiz.confess.entities.CheckUserPassRequest;
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

public class Password_Page extends AppCompatActivity {

    private FrameLayout buttonFrame;
    private EditText passwordField;
    private ImageView showButton;
    private TextView forgotButton;
    private boolean isPasswordShowing = false;
    private String email;
    private Endpoints endpoints;
    private OkPopUp popUp;
    private GradientDrawable drawable;
    private LinearLayout backgroundDetector;
    private OnlyLoader loader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_password_page);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        buttonFrame = findViewById(R.id.checkPasswordButton);
        passwordField = findViewById(R.id.password);
        showButton = findViewById(R.id.showButton);
        forgotButton = findViewById(R.id.forgotPasswordButton);
        backgroundDetector = findViewById(R.id.passwordEntryPanel);

        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        endpoints = ServerConfigs.getInstance().create(Endpoints.class);

        Drawable background = backgroundDetector.getBackground();
        if(background instanceof GradientDrawable){
            drawable = (GradientDrawable) background;
        }

        int color = ContextCompat.getColor(this, R.color.falseRed);

        passwordField.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                VibManager.vibrateTick(v.getContext());
            }
        });

        showButton.setOnClickListener(v -> {
            VibManager.vibrateTick(this);
            if (!isPasswordShowing) {
                passwordField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            } else {
                passwordField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            }
            passwordField.setSelection(passwordField.getText().length());
            isPasswordShowing = !isPasswordShowing;
        });

        buttonFrame.setOnClickListener(v -> {
            VibManager.vibrateTick(this);
            loader = new OnlyLoader(this, R.raw.loading_animation);
            String password = passwordField.getText().toString();
            if(account != null && !TextUtils.isEmpty(password)){
                email = account.getEmail();
                checkUserPassword(email, password,this, isCorrect -> {
                    if(loader!=null){
                        loader.dismiss();
                    }
                    if(isCorrect){
                        Intent homePage = new Intent(this, HomePage.class);
                        startActivity(homePage);
                        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                        finish();
                    }
                    else {
                        drawable.setStroke(2, color);
                        passwordField.setText("");
                        passwordField.setHint("wrong password");
                    }
                });
            }
        });

        forgotButton.setOnClickListener(v -> {
            VibManager.vibrateTick(this);
            loader = new OnlyLoader(this, R.raw.loading_animation);
            if(account != null){
                String email = account.getEmail();
                forgotUserPassword(email, this, isCorrect -> {
                    if(loader!=null){
                        loader.dismiss();
                    }
                    if (isCorrect){
                        Toast.makeText(this, "Reset link sent to email", Toast.LENGTH_SHORT).show();
                    }
                    else {
                        Toast.makeText(this, "Error in genrating the link", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

    }

    protected void checkUserPassword(String email, String password, Activity activity, UserCallback callback){

        CheckPasswordRequest checkPasswordRequest = new CheckPasswordRequest(email, password);
        Call<CheckPasswordResponse> call = endpoints.checkUserPassword(checkPasswordRequest);

        call.enqueue(new Callback<CheckPasswordResponse>() {
            @Override
            public void onResponse(Call<CheckPasswordResponse> call, Response<CheckPasswordResponse> response) {
                if(response.isSuccessful() && response.body() != null){

                    boolean passResponse = response.body().isMessage();

                    callback.onResult(passResponse);
                }
                else {
                    TelegramLogs.sendTelegramLog("Response body not came in call");
                    callback.onResult(false);
                    popUp = new OkPopUp(activity, R.raw.error_animation, "Got a server error.");
                }
            }

            @Override
            public void onFailure(Call<CheckPasswordResponse> call, Throwable throwable) {
                TelegramLogs.sendTelegramLog("Caught up an error\n"+throwable);
                popUp = new OkPopUp(activity, R.raw.error_animation, "Error in fetching detail");
            }
        });
    }
    protected void forgotUserPassword(String email, Activity activity, UserCallback callback){

        ForgotPasswordRequest forgotPasswordRequest = new ForgotPasswordRequest(email);
        Call<ForgotPasswordResponse> call = endpoints.forgotUserPassword(forgotPasswordRequest);

        call.enqueue(new Callback<ForgotPasswordResponse>() {
            @Override
            public void onResponse(Call<ForgotPasswordResponse> call, Response<ForgotPasswordResponse> response) {
                if(response.isSuccessful() && response.body() != null){

                    boolean resBody = response.body().isMessage();
                    callback.onResult(resBody);
                }
                else {
                    TelegramLogs.sendTelegramLog("Server side response error");
                    callback.onResult(false);
                    popUp = new OkPopUp(activity, R.raw.error_animation, "Got a server error.");
                }
            }

            @Override
            public void onFailure(Call<ForgotPasswordResponse> call, Throwable throwable) {
                TelegramLogs.sendTelegramLog("Caught up an error\n"+throwable);
                popUp = new OkPopUp(activity, R.raw.error_animation, "Error in fetching detail");
            }
        });
    }

    public interface UserCallback {
        void onResult(boolean isCorrect);
    }
}