package in.mohammad.ramiz.confess;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
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
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.vectordrawable.graphics.drawable.Animatable2Compat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import in.mohammad.ramiz.confess.popups.OkPopUp;

public class AlaisPage extends AppCompatActivity {

    private ImageView lockIcon;
    private boolean isAnimating = false;
    private boolean playNext = true;
    private LinearLayout passwordButton, passwordPanel;
    private int gif = R.raw.lock_animation;
    private boolean isPassword = false;
    private FrameLayout joinButton;
    private EditText aliasName, password;
    private OkPopUp popUp;

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

        joinButton.setOnClickListener(v -> {
            String userAliasName = aliasName.getText().toString();
            if(isPassword){
                String userPassword = password.getText().toString();

                if(!TextUtils.isEmpty(userAliasName) && !TextUtils.isEmpty(userPassword)){
                    Log.d("name", ""+userAliasName);
                    Log.d("pass",""+userPassword);
                }
                else{
                    popUp = new OkPopUp(this, R.raw.file_not_found, "Some fields are found empty");
                    }
                }
            else{
                if(!TextUtils.isEmpty(userAliasName)){
                    Log.d("name",""+userAliasName);
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
}
