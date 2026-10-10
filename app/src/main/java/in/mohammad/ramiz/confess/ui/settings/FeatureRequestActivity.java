package in.mohammad.ramiz.confess.ui.settings;

import android.os.Bundle;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;
import com.google.gson.JsonObject;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.repo.ActivityRepository;
import in.mohammad.ramiz.confess.databinding.ActivityFeatureRequestBinding;
import in.mohammad.ramiz.confess.ui.base.BaseActivity;
import in.mohammad.ramiz.confess.ui.util.EdgeInsets;
import in.mohammad.ramiz.confess.ui.util.SimpleTextWatcher;

/** Request a feature (category + up to 1000 chars). Old: FeatureDemand. */
public class FeatureRequestActivity extends BaseActivity {

    private ActivityFeatureRequestBinding b;
    private boolean sending;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityFeatureRequestBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeInsets.padTop(b.topBar);
        EdgeInsets.padBottom(b.bottom, true);

        b.back.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        b.textInput.addTextChangedListener(new SimpleTextWatcher(t -> update()));
        b.categories.setOnCheckedStateChangeListener((g, ids) -> Haptics.tick(this));
        b.send.setOnClickListener(v -> send());
        update();
    }

    private String text() {
        return b.textInput.getText() == null ? "" : b.textInput.getText().toString().trim();
    }

    private String category() {
        int id = b.categories.getCheckedChipId();
        if (id == R.id.catFeed) return "feed";
        if (id == R.id.catPrivacy) return "privacy";
        if (id == R.id.catComments) return "comments";
        return "other";
    }

    private void update() {
        b.send.setEnabled(!sending && !text().isEmpty());
    }

    private void send() {
        if (sending || text().isEmpty()) return;
        sending = true;
        b.send.setText(R.string.feature_sending);
        update();
        new ActivityRepository().requestFeature(text(), category(), new ApiCallback<JsonObject>() {
            @Override public void onSuccess(JsonObject data) {
                Haptics.click(FeatureRequestActivity.this);
                Toast.makeText(FeatureRequestActivity.this, R.string.feature_sent, Toast.LENGTH_LONG).show();
                finish();
            }
            @Override public void onError(ApiException e) {
                sending = false;
                b.send.setText(R.string.feature_send);
                update();
                Snackbar.make(b.getRoot(), e.userMessage(FeatureRequestActivity.this), Snackbar.LENGTH_LONG)
                        .setAnchorView(b.bottom).show();
            }
        });
    }
}
