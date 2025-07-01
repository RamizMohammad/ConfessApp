package in.mohammad.ramiz.confess;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
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
import in.mohammad.ramiz.confess.entities.CreatePostRequest;
import in.mohammad.ramiz.confess.entities.CreatePostResponse;
import in.mohammad.ramiz.confess.haptics.VibManager;
import in.mohammad.ramiz.confess.popups.OkPopUp;
import in.mohammad.ramiz.confess.popups.OnlyLoader;
import in.mohammad.ramiz.confess.server.Endpoints;
import in.mohammad.ramiz.confess.server.ServerConfigs;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CreatePost extends AppCompatActivity {

    private TextView wordCount;
    private EditText postData;
    private ImageView backButton;
    private FrameLayout postButton;
    private Endpoints endpoints;
    private String email;
    private OnlyLoader popUp;
    private OkPopUp popUp2;
    private int currentLength;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_create_post);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        wordCount = findViewById(R.id.wordCount);
        postData = findViewById(R.id.postText);
        backButton = findViewById(R.id.backButton);
        postButton = findViewById(R.id.postButton);

        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        endpoints = ServerConfigs.getInstance().create(Endpoints.class);

        postData.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {

            }

            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                currentLength = charSequence.length();
                wordCount.setText(currentLength+"/2000");
            }
        });

        backButton.setOnClickListener(v -> {
            Intent backIntent = new Intent(this, HomePage.class);
            startActivity(backIntent);
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        });

        postButton.setOnClickListener(v -> {
            if(currentLength>2000){
                Toast.makeText(this, "Exceed the text limit", Toast.LENGTH_SHORT).show();
                return;
            }
            VibManager.vibrateTick(this);
            if(account != null){
                email = account.getEmail();
            }
            String postText = postData.getText().toString();
            popUp = new OnlyLoader(this, R.raw.loading_animation);
            createPost(email, postText, (isPosted, label) -> {
                popUp.dismiss();
                if(isPosted && label.equals("safe")){
                    Toast.makeText(this, "Post Added", Toast.LENGTH_SHORT).show();
                    Intent postingIntent = new Intent(this, HomePage.class);
                    startActivity(postingIntent);
                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                }
                else if(label.equals("safe")){
                    popUp2 = new OkPopUp(this, R.raw.error_animation, "Unable to make your post");
                }
                else if(label == null){
                    popUp2 = new OkPopUp(this, R.raw.error_animation, "We have internal server error");
                }
                else {
                    popUp2 = new OkPopUp(this, R.raw.error_animation, "We found "+label+" in your post");
                }
            });
        });
    }

    private void createPost(String email, String post, UserCallback callback){

        CreatePostRequest postData = new CreatePostRequest(email,post);

        Call<CreatePostResponse> call = endpoints.createPost(BuildConfig.CLIENT_API, postData);

        call.enqueue(new Callback<CreatePostResponse>() {
            @Override
            public void onResponse(Call<CreatePostResponse> call, Response<CreatePostResponse> response) {
                if(response.isSuccessful() && response.body() != null){
                    boolean res = response.body().isMessage();
                    String lab = response.body().getLabel();
                    callback.onResult(res, lab);
                }
                else{
                    callback.onResult(false, null);
                }
            }

            @Override
            public void onFailure(Call<CreatePostResponse> call, Throwable t) {
                TelegramLogs.sendTelegramLog("Error in fetching the posts:\n"+t);
                callback.onResult(false, null);
            }
        });
    }

    public interface UserCallback{
        void onResult(boolean isPosted, String label);
    }
}