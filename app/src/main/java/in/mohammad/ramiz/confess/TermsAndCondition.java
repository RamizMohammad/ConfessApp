package in.mohammad.ramiz.confess;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import in.mohammad.ramiz.confess.adapters.TermsListAdapter;
import in.mohammad.ramiz.confess.auth.GoogleAuth;
import in.mohammad.ramiz.confess.debugmonitor.TelegramLogs;
import in.mohammad.ramiz.confess.entities.CheckUserPassRequest;
import in.mohammad.ramiz.confess.entities.CheckUserPassResponse;
import in.mohammad.ramiz.confess.entityfiles.ListEntites;
import in.mohammad.ramiz.confess.popups.OkPopUp;
import in.mohammad.ramiz.confess.popups.OnlyLoader;
import in.mohammad.ramiz.confess.server.Endpoints;
import in.mohammad.ramiz.confess.server.ServerConfigs;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TermsAndCondition extends AppCompatActivity {

    private ListView termsListView;
    private ArrayList<ListEntites> termsArrayList;
    private static TermsListAdapter listAdapter;
    private CheckBox termsCheckBox;
    private boolean isAccepted = false;
    private FrameLayout googleButton;
    private OkPopUp popUp;
    private static final int RC_SIGN_IN = 123;
    private GoogleAuth googleAuth;
    private Endpoints endpoints;
    private Intent intent;
    private OnlyLoader onlyLoader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_terms_and_condition);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ListView termsListView = findViewById(R.id.listView);
        termsCheckBox = findViewById(R.id.termAccept);
        googleButton = findViewById(R.id.GoogleButton);

        termsArrayList= new ArrayList<>();
        googleAuth = new GoogleAuth(this);
        endpoints = ServerConfigs.getInstance().create(Endpoints.class);

        termsCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(@NonNull CompoundButton compoundButton, boolean b) {
                isAccepted = !isAccepted;
            }
        });

        googleButton.setOnClickListener(v -> {
            if(isAccepted){
                try{
                    if(googleAuth == null || googleAuth.googleSignInClient() == null){
                        popUp = new OkPopUp(this, R.raw.error_animation, "Encountered error on our side.");
                        TelegramLogs.sendTelegramLog("Google Auth login encounter an error");
                    }
                    GoogleSignInClient signInClient = googleAuth.googleSignInClient();
                    Intent googleIntent = signInClient.getSignInIntent();
                    startActivityForResult(googleIntent, RC_SIGN_IN);
                } catch (Exception e){
                    popUp = new OkPopUp(this, R.raw.error_animation, "Got some errors");
                    TelegramLogs.sendTelegramLog("Got some error on android side\n"+e);
                }
            }
            else {
                popUp = new OkPopUp(this, R.raw.terms_animation, "Accept the Terms and Condition");
            }
        });

        try {
            String jsonString = loadJSONFromAsset();
            JSONArray jsonArray = new JSONArray(jsonString);

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject group = jsonArray.getJSONObject(i);
                String heading = group.getString("heading");
                JSONArray terms = group.getJSONArray("terms");

                termsArrayList.add(new ListEntites(heading, true));

                for (int j = 0; j < terms.length(); j++) {
                    termsArrayList.add(new ListEntites(terms.getString(j), false));
                }
            }

            listAdapter = new TermsListAdapter(termsArrayList, this);
            termsListView.setAdapter(listAdapter);

        } catch (JSONException e) {
            e.printStackTrace();
        }

    }
    private String loadJSONFromAsset() {
        try {
            InputStream is = getAssets().open("terms.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            return new String(buffer, "UTF-8");
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        super.onActivityResult(requestCode,resultCode,data);

        if(requestCode == RC_SIGN_IN){
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try{
                GoogleSignInAccount account = task.getResult(ApiException.class);
                Toast.makeText(this, "Google login successful", Toast.LENGTH_SHORT).show();

                if (account != null){
                    String email = account.getEmail();

                    runOnUiThread(() -> {
                        onlyLoader = new OnlyLoader(this, R.raw.loading_animation);
                    });

                    checkForUserAndPass(email, this, ((isUser, isPassword) -> {

                        runOnUiThread(() -> {
                            if(onlyLoader != null){
                                onlyLoader.dismiss();
                            }
                        });

                        if(isUser && isPassword){
                            intent = new Intent(this, Password_Page.class);
                        } else if (isUser) {
                            intent = new Intent(this, HomePage.class);
                        }
                        else{
                            intent = new Intent(this, AlaisPage.class);
                        }

                        startActivity(intent);
                        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                    }));
                }


            } catch (ApiException e) {
                popUp = new OkPopUp(this, R.raw.error_animation, "Error in google signing");
                TelegramLogs.sendTelegramLog("Got google signing error\n"+e);
            }
        }
    }

    protected void checkForUserAndPass(String email, Activity activity, UserCheckCallback callback){

        CheckUserPassRequest userPassRequest = new CheckUserPassRequest(email);
        Call<CheckUserPassResponse> call = endpoints.chechUserPass(userPassRequest);

        call.enqueue(new Callback<CheckUserPassResponse>() {
            @Override
            public void onResponse(Call<CheckUserPassResponse> call, Response<CheckUserPassResponse> response) {
                if(response.isSuccessful() && response.body() != null){

                    boolean isUser = response.body().isUser();
                    boolean isPassword = response.body().isPassword();

                    callback.onResult(isUser, isPassword);
                }
                else{
                    callback.onResult(false, false);
                }
            }

            @Override
            public void onFailure(Call<CheckUserPassResponse> call, Throwable throwable) {
                popUp = new OkPopUp(activity, R.raw.error_animation, "Error to connect with server");
                TelegramLogs.sendTelegramLog("Error in android client:\n"+throwable);
            }
        });
    }

    public interface UserCheckCallback {
        void onResult(boolean isUser, boolean isPassword);
    }
}