package in.mohammad.ramiz.confess;

import android.content.Intent;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

import in.mohammad.ramiz.confess.haptics.VibManager;

public class WelcomeUser extends AppCompatActivity {

    private ImageView welcomeBanner;
    private TextView funFact;
    private FrameLayout continueButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_welcome_user);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        welcomeBanner = findViewById(R.id.bannerImage);
        funFact = findViewById(R.id.funFact);
        continueButton = findViewById(R.id.collapseButton);

        Glide.with(this)
                .asGif()
                .load(R.raw.welcome)
                .into(welcomeBanner);

        ArrayList<String> healingQuotes = new ArrayList<>(Arrays.asList(
                "Every confession is a step toward peace.",
                "Let it out. Lighten your soul.",
                "Confess. Heal. Repeat.",
                "Some secrets deserve freedom.",
                "Speak your truth, silently or loudly.",
                "Unspoken feelings find peace here.",
                "It’s okay to let go. We’re listening.",
                "Your truth is safe with us.",
                "Confession: a quiet act of courage.",
                "Hearts heal one word at a time."
        ));

        String quoteToShow = healingQuotes.get(new Random().nextInt(healingQuotes.size()));
        funFact.setText(quoteToShow);

        continueButton.setOnClickListener(v -> {
            VibManager.vibrateTick(this);
            Intent homeIntent = new Intent(WelcomeUser.this, HomePage.class);
            startActivity(homeIntent);
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        });
    }
}