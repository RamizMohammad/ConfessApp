package in.mohammad.ramiz.confess.ui.settings;

import android.os.Bundle;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.databinding.ActivitySettingsAppearanceBinding;
import in.mohammad.ramiz.confess.ui.base.BaseActivity;
import in.mohammad.ramiz.confess.ui.util.EdgeInsets;

/**
 * Settings → Appearance: the paper tone (bright → charcoal) and text tone (Auto, or dark ink →
 * light cream) used by confession cards, the composer and book pages — with a live preview —
 * and how bursts look in the feed (slider · stack · one by one, with the animated chooser).
 */
public class AppearanceActivity extends BaseActivity {

    private ActivitySettingsAppearanceBinding b;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivitySettingsAppearanceBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeInsets.padTop(b.topBar);
        EdgeInsets.padBottom(b.scroll, false);
        b.back.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        setUpPaperTone();
        setUpBursts();
    }

    // ── Appearance: paper + text tone ─────────────────────────────────────

    /**
     * Paper: bright → charcoal. Text: Auto (dark or light, whichever reads best on that paper)
     * or a fixed tone dark ink → light cream. The little note previews both.
     */
    private void setUpPaperTone() {
        b.paperTone.setValue(Math.round(in.mohammad.ramiz.confess.ui.components.PaperTone.get(this) / 5f) * 5f);
        int text = in.mohammad.ramiz.confess.ui.components.PaperTone.text(this);
        b.textAuto.setChecked(text == in.mohammad.ramiz.confess.ui.components.PaperTone.AUTO);
        b.textTone.setValue(text == in.mohammad.ramiz.confess.ui.components.PaperTone.AUTO ? 0f : Math.round(text / 5f) * 5f);
        b.textTone.setEnabled(text != in.mohammad.ramiz.confess.ui.components.PaperTone.AUTO);
        previewTone();
        b.paperTone.addOnChangeListener((slider, value, fromUser) -> {
            if (!fromUser) return;
            in.mohammad.ramiz.confess.ui.components.PaperTone.set(this, Math.round(value));
            previewTone();
            in.mohammad.ramiz.confess.core.Haptics.tick(this);
        });
        b.textTone.addOnChangeListener((slider, value, fromUser) -> {
            if (!fromUser) return;
            in.mohammad.ramiz.confess.ui.components.PaperTone.setText(this, Math.round(value));
            previewTone();
            in.mohammad.ramiz.confess.core.Haptics.tick(this);
        });
        b.textAuto.setOnCheckedChangeListener((v, auto) -> {
            if (auto) {
                in.mohammad.ramiz.confess.ui.components.PaperTone.setText(this, in.mohammad.ramiz.confess.ui.components.PaperTone.AUTO);
            } else {
                // Start the manual slider where Auto was, so nothing jumps.
                int now = in.mohammad.ramiz.confess.ui.components.PaperTone.ink(this) == androidx.core.content.ContextCompat.getColor(this, R.color.paper_ink) ? 0 : 100;
                b.textTone.setValue(now);
                in.mohammad.ramiz.confess.ui.components.PaperTone.setText(this, now);
            }
            b.textTone.setEnabled(!auto);
            previewTone();
        });
    }

    // ── Appearance: bursts ────────────────────────────────────────────────

    /** Slider · Stack · One by one — and the animated chooser. */
    private void setUpBursts() {
        showBurstMode();
        b.burstMode.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked) return;
            int m = id == b.burstStack.getId() ? in.mohammad.ramiz.confess.ui.feed.Bursts.STACK
                    : id == b.burstSeparate.getId() ? in.mohammad.ramiz.confess.ui.feed.Bursts.SEPARATE
                    : in.mohammad.ramiz.confess.ui.feed.Bursts.SLIDER;
            if (m == in.mohammad.ramiz.confess.ui.feed.Bursts.mode(this) && in.mohammad.ramiz.confess.ui.feed.Bursts.asked(this)) return;
            in.mohammad.ramiz.confess.ui.feed.Bursts.setMode(this, m);
            in.mohammad.ramiz.confess.core.Haptics.tick(this);
        });
        b.burstHow.setOnClickListener(v -> in.mohammad.ramiz.confess.ui.feed.BurstChooser.show(this, m -> showBurstMode()));
    }

    private void showBurstMode() {
        int m = in.mohammad.ramiz.confess.ui.feed.Bursts.mode(this);
        b.burstMode.check(m == in.mohammad.ramiz.confess.ui.feed.Bursts.STACK ? b.burstStack.getId()
                : m == in.mohammad.ramiz.confess.ui.feed.Bursts.SEPARATE ? b.burstSeparate.getId() : b.burstSlider.getId());
    }

    private void previewTone() {
        b.tonePreview.setBackgroundTintList(android.content.res.ColorStateList.valueOf(in.mohammad.ramiz.confess.ui.components.PaperTone.paper(this)));
        b.tonePreview.setTextColor(in.mohammad.ramiz.confess.ui.components.PaperTone.ink(this));
    }
}
