package in.mohammad.ramiz.confess;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;

import in.mohammad.ramiz.confess.debugmonitor.TelegramLogs;
import in.mohammad.ramiz.confess.entities.UpdateAboutRequest;
import in.mohammad.ramiz.confess.entities.UpdateAboutResponse;
import in.mohammad.ramiz.confess.haptics.VibManager;
import in.mohammad.ramiz.confess.popups.OkPopUp;
import in.mohammad.ramiz.confess.popups.OnlyLoader;
import in.mohammad.ramiz.confess.server.Endpoints;
import in.mohammad.ramiz.confess.server.ServerConfigs;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UpdateAbout extends AppCompatActivity {

    private EditText aboutField;
    private FrameLayout updateButton;
    private TextView wordCount;
    private Endpoints endpoints;
    private int currentLength;
    private OnlyLoader loader;
    private OkPopUp popUp;
    private String email;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_update_about);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        aboutField = findViewById(R.id.about);
        updateButton = findViewById(R.id.updateAboutButton);
        wordCount = findViewById(R.id.wordCount);

        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        endpoints = ServerConfigs.getInstance().create(Endpoints.class);

        aboutField.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {}
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}
            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                currentLength = charSequence.length();
                wordCount.setText(currentLength+"/50");
            }
        });

        updateButton.setOnClickListener(v -> {
            VibManager.vibrateTick(this);
            loader = new OnlyLoader(this, R.raw.loading_animation);
            if(account != null){
                email = account.getEmail();
            }
            String about = aboutField.getText().toString();
            updateAbout(email, about, ((isUpdated, label) -> {
                if(isUpdated && label.equals("safe")){
                    Toast.makeText(UpdateAbout.this, "About Updated", Toast.LENGTH_SHORT).show();
                    Intent homeIntent = new Intent(UpdateAbout.this, HomePage.class);
                    startActivity(homeIntent);
                    finish();
                } else if(label == null){
                    new OkPopUp(UpdateAbout.this, R.raw.error_animation, "We got some error");
                }
                else {
                    new OkPopUp(UpdateAbout.this, R.raw.error_animation, "We found "+label+" in your post");
                }
            }));
        });
    }

    private void updateAbout(String email, String about, UserCallback callback){
        UpdateAboutRequest request = new UpdateAboutRequest(email, about);

        Call<UpdateAboutResponse> call = endpoints.updateAbout(BuildConfig.CLIENT_API, request);

        call.enqueue(new Callback<UpdateAboutResponse>() {
            @Override
            public void onResponse(Call<UpdateAboutResponse> call, Response<UpdateAboutResponse> response) {
                if(response.isSuccessful() && response.body() != null){
                    boolean callResponse = response.body().isMessage();
                    String label = response.body().getLabel();
                    callback.onResult(callResponse,label);
                }
                else{
                    TelegramLogs.sendTelegramLog("There is error in fetching the response.");
                    callback.onResult(false, null);
                }
            }

            @Override
            public void onFailure(Call<UpdateAboutResponse> call, Throwable t) {
                TelegramLogs.sendTelegramLog("Caught internal error");
                callback.onResult(false, null);
            }
        });
    }

    private interface UserCallback{
        void onResult(boolean isUpdated, String label);
    }
}