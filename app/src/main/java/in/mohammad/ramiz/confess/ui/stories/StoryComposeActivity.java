package in.mohammad.ramiz.confess.ui.stories;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.api.ErrorCode;
import in.mohammad.ramiz.confess.data.model.Story;
import in.mohammad.ramiz.confess.data.repo.StoryRepository;
import in.mohammad.ramiz.confess.databinding.ActivityStoryComposeBinding;
import in.mohammad.ramiz.confess.ui.base.BaseActivity;
import in.mohammad.ramiz.confess.ui.compose.Moderation;
import in.mohammad.ramiz.confess.ui.util.EdgeInsets;
import in.mohammad.ramiz.confess.ui.util.SimpleTextWatcher;
import retrofit2.Call;

/**
 * New story: a coloured card with up to 100 characters. Pick one of five
 * backgrounds; "Share for 24h" checks it (same moderation as letters) and posts.
 */
public class StoryComposeActivity extends BaseActivity {

    private static final String STATE_STYLE = "style";

    private ActivityStoryComposeBinding b;
    private final StoryRepository repo = new StoryRepository();
    private int style = 0;
    private boolean working = false;
    @Nullable private Call<Story> pending;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityStoryComposeBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeInsets.padTop(b.topBar);
        EdgeInsets.padBottom(b.bottom, true);
        if (savedInstanceState != null) style = savedInstanceState.getInt(STATE_STYLE, 0);

        buildSwatches();
        applyStyle();
        updateCounter();

        b.input.addTextChangedListener(new SimpleTextWatcher(t -> {
            b.error.setVisibility(View.GONE);
            updateCounter();
        }));
        b.share.setOnClickListener(v -> share());
        b.close.setOnClickListener(v -> leave());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { leave(); }
        });

        if (savedInstanceState == null) {
            b.input.postDelayed(() -> {
                if (isFinishing()) return;
                b.input.requestFocus();
                WindowCompat.getInsetsController(getWindow(), b.input).show(WindowInsetsCompat.Type.ime());
            }, 300);
        }
    }

    // ── Look ─────────────────────────────────────────────────────────────────

    private void buildSwatches() {
        int size = getResources().getDimensionPixelSize(R.dimen.space_xl) - 8;
        int gap = getResources().getDimensionPixelSize(R.dimen.space_sm);
        for (int i = 0; i < StoryStyles.COUNT; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            if (i > 0) lp.setMarginStart(gap);
            dot.setLayoutParams(lp);
            dot.setContentDescription(getString(R.string.story_style_cd, i + 1));
            int index = i;
            dot.setOnClickListener(v -> {
                if (style == index) return;
                Haptics.tick(this);
                style = index;
                applyStyle();
            });
            b.swatches.addView(dot);
        }
    }

    private void applyStyle() {
        float radius = getResources().getDimension(R.dimen.space_lg);
        b.card.setBackground(StoryStyles.card(this, style, radius));
        int fg = StoryStyles.text(this, style);
        b.input.setTextColor(fg);
        b.input.setHintTextColor((fg & 0x00FFFFFF) | 0x80000000);
        for (int i = 0; i < b.swatches.getChildCount(); i++) {
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(StoryStyles.background(this, i));
            d.setStroke(i == style ? 6 : 2, ContextCompat.getColor(this, i == style ? R.color.text_primary : R.color.hairline));
            b.swatches.getChildAt(i).setBackground(d);
            b.swatches.getChildAt(i).setSelected(i == style);
        }
    }

    private String text() {
        return b.input.getText() == null ? "" : b.input.getText().toString().trim();
    }

    private void updateCounter() {
        int n = text().length();
        b.counter.setText(getString(R.string.story_counter, n));
        b.counter.setTextColor(ContextCompat.getColor(this, n >= 90 ? R.color.amber : R.color.text_secondary));
        b.share.setEnabled(!working && n > 0 && n <= StoryRepository.MAX_CHARS);
    }

    // ── Share ────────────────────────────────────────────────────────────────

    private void share() {
        String t = text();
        if (working || t.isEmpty()) return;
        working = true;
        Haptics.click(this);
        b.share.setText(R.string.story_sharing);
        b.input.setEnabled(false);
        updateCounter();
        pending = repo.create(t, style, new ApiCallback<Story>() {
            @Override public void onSuccess(Story s) {
                Toast.makeText(StoryComposeActivity.this, R.string.story_shared, Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            }
            @Override public void onError(ApiException e) {
                working = false;
                b.share.setText(R.string.story_share);
                b.input.setEnabled(true);
                updateCounter();
                Haptics.reject(StoryComposeActivity.this);
                String msg;
                if (e.is(ErrorCode.CONTENT_FLAGGED)) msg = getString(Moderation.reason(e));
                else if (e.is(ErrorCode.VALIDATION_ERROR) && e.serverMessage.contains("up to")) msg = getString(R.string.story_too_many);
                else msg = e.userMessage(StoryComposeActivity.this);
                b.error.setText(msg);
                b.error.setVisibility(View.VISIBLE);
            }
        });
    }

    private void leave() {
        if (working) return;
        if (text().isEmpty()) {
            finish();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.story_discard_title)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.compose_leave_discard, (d, w) -> finish())
                .show();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.stay, R.anim.slide_down_out);
    }

    @Override
    protected void onSaveInstanceState(@androidx.annotation.NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(STATE_STYLE, style);
    }

    @Override
    protected void onDestroy() {
        if (pending != null && !isChangingConfigurations()) pending.cancel();
        super.onDestroy();
    }
}
