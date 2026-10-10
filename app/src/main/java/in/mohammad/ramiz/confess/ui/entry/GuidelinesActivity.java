package in.mohammad.ramiz.confess.ui.entry;

import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.gson.reflect.TypeToken;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.data.api.ApiClient;
import in.mohammad.ramiz.confess.data.model.TermsSection;
import in.mohammad.ramiz.confess.databinding.ActivityGuidelinesBinding;
import in.mohammad.ramiz.confess.databinding.ItemGuidelineSectionBinding;
import in.mohammad.ramiz.confess.ui.auth.OnboardingDraft;
import in.mohammad.ramiz.confess.ui.base.BaseActivity;
import in.mohammad.ramiz.confess.ui.base.Router;
import in.mohammad.ramiz.confess.ui.onboarding.AliasActivity;
import in.mohammad.ramiz.confess.ui.util.EdgeInsets;

/**
 * S3 · Community guidelines. Old: TermsAndCondition + TermsListAdapter.
 * Content is the real assets/terms.json (12 sections) shown as expandable cards.
 *
 * Modes:
 *   onboarding (default) — sticky "I accept" + "Continue to alias"
 *   EXTRA_READ_ONLY      — just reading (from the sign-in sheet / settings)
 */
public class GuidelinesActivity extends BaseActivity {

    public static final String EXTRA_READ_ONLY = "read_only";

    private ActivityGuidelinesBinding b;
    private boolean readOnly;

    @Override
    protected boolean requiresSession() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityGuidelinesBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        readOnly = getIntent().getBooleanExtra(EXTRA_READ_ONLY, false);

        EdgeInsets.padTop(b.topBar);
        if (readOnly) {
            b.acceptBar.setVisibility(View.GONE);
            EdgeInsets.padBottom(b.scroll, false);
        } else {
            EdgeInsets.padBottom(b.acceptBar, false);
        }

        b.back.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        b.scroll.setOnScrollChangeListener((androidx.core.widget.NestedScrollView.OnScrollChangeListener)
                (v, x, y, ox, oy) -> updateProgress());

        List<TermsSection> sections = loadTerms();
        for (int i = 0; i < sections.size(); i++) addSection(sections.get(i), i == 0);
        b.scroll.post(this::updateProgress);

        b.accept.setOnCheckedChangeListener((c, checked) -> {
            Haptics.tick(this);
            b.continueBtn.setEnabled(checked);
        });
        b.continueBtn.setOnClickListener(v -> continueToAlias());
    }

    // ── Content ──────────────────────────────────────────────────────────────

    private List<TermsSection> loadTerms() {
        try (Reader r = new InputStreamReader(getAssets().open("terms.json"), StandardCharsets.UTF_8)) {
            List<TermsSection> list = ApiClient.gson().fromJson(r, new TypeToken<List<TermsSection>>() { }.getType());
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void addSection(TermsSection s, boolean expanded) {
        ItemGuidelineSectionBinding item = ItemGuidelineSectionBinding.inflate(LayoutInflater.from(this), b.sections, false);
        String[] parts = splitEmoji(s.heading);
        item.emoji.setText(parts[0].isEmpty() ? "•" : parts[0]);
        item.title.setText(parts[1]);
        item.count.setText(getString(R.string.guidelines_points, s.terms.size()));

        for (String term : s.terms) item.body.addView(bullet(term));

        setExpanded(item, expanded);
        item.header.setOnClickListener(v -> {
            setExpanded(item, item.body.getVisibility() != View.VISIBLE);
            b.scroll.post(this::updateProgress);
        });
        b.sections.addView(item.getRoot());
    }

    private void setExpanded(ItemGuidelineSectionBinding item, boolean expanded) {
        item.body.setVisibility(expanded ? View.VISIBLE : View.GONE);
        item.chevron.animate().rotation(expanded ? 180f : 0f).setDuration(180).start();
        item.header.setContentDescription(item.title.getText() + (expanded ? ", expanded" : ", collapsed"));
    }

    /** A term line: its own leading emoji as the bullet, then the text. */
    private View bullet(String term) {
        String[] parts = splitEmoji(term);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(10), 0, 0);

        TextView mark = new TextView(this);
        mark.setText(parts[0].isEmpty() ? "•" : parts[0]);
        mark.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        mark.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        mark.setLayoutParams(new LinearLayout.LayoutParams(dp(28), LinearLayout.LayoutParams.WRAP_CONTENT));
        row.addView(mark);

        TextView text = new TextView(this);
        text.setTextAppearance(R.style.TextAppearance_Confess_BodyMedium);
        text.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        text.setText(parts[1]);
        text.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(text);
        return row;
    }

    /** "📖 Introduction" → ["📖", "Introduction"]; text without a leading emoji → ["", text]. */
    @NonNull
    static String[] splitEmoji(String s) {
        if (s == null) return new String[]{"", ""};
        String t = s.trim();
        int space = t.indexOf(' ');
        if (space > 0) {
            String first = t.substring(0, space);
            boolean hasLetterOrDigit = false;
            for (int i = 0; i < first.length(); i++) {
                if (Character.isLetterOrDigit(first.charAt(i))) { hasLetterOrDigit = true; break; }
            }
            if (!hasLetterOrDigit) return new String[]{first, t.substring(space + 1).trim()};
        }
        return new String[]{"", t};
    }

    private void updateProgress() {
        View child = b.scroll.getChildAt(0);
        int range = child == null ? 0 : child.getHeight() - b.scroll.getHeight();
        int p = range <= 0 ? 1000 : Math.min(1000, Math.round(1000f * b.scroll.getScrollY() / range));
        b.readProgress.setProgressCompat(p, true);
    }

    // ── Continue ─────────────────────────────────────────────────────────────

    private void continueToAlias() {
        OnboardingDraft draft = OnboardingDraft.get();
        if (draft == null || draft.googleIdToken == null) {
            // Process was killed and the in-memory Google token is gone — start again.
            Toast.makeText(this, R.string.guidelines_expired, Toast.LENGTH_LONG).show();
            Router.toWelcome(this);
            return;
        }
        draft.guidelinesAccepted = true;
        Haptics.click(this);
        startActivity(new Intent(this, AliasActivity.class));
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }
}
