package in.mohammad.ramiz.confess;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import in.mohammad.ramiz.confess.entities.FeatureDemandRequest;
import in.mohammad.ramiz.confess.entities.FeatureDemandResponse;
import in.mohammad.ramiz.confess.haptics.VibManager;
import in.mohammad.ramiz.confess.popups.OnlyLoader;
import in.mohammad.ramiz.confess.server.Endpoints;
import in.mohammad.ramiz.confess.server.ServerConfigs;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FeatureDemand extends AppCompatActivity {

    private EditText name, feature;
    private FrameLayout submitButton;
    private Endpoints endpoints;
    private OnlyLoader loader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_feature_demand);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        name = findViewById(R.id.aliasName);
        feature = findViewById(R.id.feature);
        submitButton = findViewById(R.id.createAccountButton);

        endpoints = ServerConfigs.getInstance().create(Endpoints.class);

        submitButton.setOnClickListener(v -> {
            VibManager.vibrateTick(this);
            String nameData = name.getText().toString();
            String featureData = feature.getText().toString();
            loader = new OnlyLoader(this, R.raw.loading_animation);
            if(!TextUtils.isEmpty(nameData) && !TextUtils.isEmpty(featureData)){
                serverCaller(nameData, featureData, new serverCallback() {
                    @Override
                    public void onSuccess() {
                        loader.dismiss();
                        Toast.makeText(FeatureDemand.this, "Thanks for request 😊", Toast.LENGTH_SHORT).show();
                        Intent homeIntent = new Intent(FeatureDemand.this, HomePage.class);
                        startActivity(homeIntent);
                        finish();
                    }

                    @Override
                    public void onFail() {
                        loader.dismiss();
                        Toast.makeText(FeatureDemand.this, "Please try again", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void serverCaller(String name, String feature, serverCallback callback){

        FeatureDemandRequest request = new FeatureDemandRequest(name, feature);

        Call<FeatureDemandResponse> call = endpoints.featureDemand(BuildConfig.CLIENT_API, request);

        call.enqueue(new Callback<FeatureDemandResponse>() {
            @Override
            public void onResponse(Call<FeatureDemandResponse> call, Response<FeatureDemandResponse> response) {
                if(response.isSuccessful() && response.body() != null){
                    if(response.body().isMessage()){
                        callback.onSuccess();
                    }
                    else {
                        callback.onFail();
                    }
                }
                else {
                    callback.onFail();
                }
            }

            @Override
            public void onFailure(Call<FeatureDemandResponse> call, Throwable t) {
                callback.onFail();
            }
        });

    }

    public interface serverCallback{
        void onSuccess();
        void onFail();
    }
}