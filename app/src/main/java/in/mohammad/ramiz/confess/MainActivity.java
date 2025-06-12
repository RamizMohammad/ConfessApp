package in.mohammad.ramiz.confess;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.lang.annotation.Annotation;

public class MainActivity extends AppCompatActivity {

    private FrameLayout frameLayout;
    private TextView buttonText;
    private Animation collapseAnimation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        frameLayout = findViewById(R.id.collapseButton);
        buttonText = findViewById(R.id.buttonText);

        frameLayout.setOnClickListener(v -> {
            Intent term = new Intent(getApplicationContext()
            , TermsAndCondition.class);
            startActivity(term);
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        });
    }
}