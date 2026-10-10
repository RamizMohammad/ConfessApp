package in.mohammad.ramiz.confess.ui.onboarding;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.google.android.material.chip.Chip;

import java.util.regex.Pattern;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.model.AliasAvailability;
import in.mohammad.ramiz.confess.data.repo.AuthRepository;
import in.mohammad.ramiz.confess.databinding.ActivityAliasBinding;
import in.mohammad.ramiz.confess.ui.auth.Biometrics;
import in.mohammad.ramiz.confess.ui.auth.OnboardingDraft;
import in.mohammad.ramiz.confess.ui.base.BaseActivity;
import in.mohammad.ramiz.confess.ui.base.Router;
import in.mohammad.ramiz.confess.ui.util.EdgeInsets;
import retrofit2.Call;

/**
 * S4 · Choose your alias. Old: AlaisPage (alias + optional password + biometric).
 *
 *  - alias: 3–15 of [A-Za-z0-9_], checked live against users/alias-available (debounced)
 *  - optional password lock (8–64) with a strength meter
 *  - optional biometric unlock, confirmed with a real BiometricPrompt before it's enabled
 *
 * Nothing is sent to the server here except the availability check; the
 * account is created at the end of the avatar step (S5).
 */
public class AliasActivity extends BaseActivity {

    static final int TOTAL_STEPS = 2;
    private static final Pattern ALIAS = Pattern.compile("^[A-Za-z0-9_]{3,15}$");
    private static final long DEBOUNCE_MS = 450;

    private enum Status { RULES, CHECKING, AVAILABLE, TAKEN, OFFLINE }

    private ActivityAliasBinding b;
    private final AuthRepository auth = new AuthRepository();
    private final Handler handler = new Handler(Looper.getMainLooper());
    @Nullable private Call<AliasAvailability> pending;
    private Status status = Status.RULES;
    private String checkedAlias = "";
    /** True while we flip the biometric switch ourselves (so the listener doesn't re-prompt). */
    private boolean settingBio = false;

    @Override
    protected boolean requiresSession() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityAliasBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        OnboardingDraft draft = OnboardingDraft.get();
        if (draft == null || draft.googleIdToken == null) {
            Toast.makeText(this, R.string.register_expired, Toast.LENGTH_LONG).show();
            Router.toWelcome(this);
            return;
        }

        EdgeInsets.padTop(b.stepBar.getRoot());
        EdgeInsets.padBottom(b.bottomBar, true);
        StepBar.bind(b.stepBar, this, 1, TOTAL_STEPS);

        setupAliasInput();
        setupSuggestions();
        setupLock();
        b.next.setOnClickListener(v -> next());

        // Restore a previous visit (back from the avatar step).
        if (draft.alias != null) b.aliasInput.setText(draft.alias);
        if (draft.password != null) {
            b.lockSwitch.setChecked(true);
            b.passwordInput.setText(draft.password);
            setBio(draft.biometricEnabled);
        }
        renderStatus();
    }

    // ── Alias ────────────────────────────────────────────────────────────────

    private void setupAliasInput() {
        // Only allow characters the server accepts (spaces → underscores).
        InputFilter allowed = (CharSequence src, int start, int end, Spanned dest, int dStart, int dEnd) -> {
            StringBuilder out = new StringBuilder();
            boolean changed = false;
            for (int i = start; i < end; i++) {
                char c = src.charAt(i);
                if (Character.isLetterOrDigit(c) && c < 128 || c == '_') out.append(c);
                else if (c == ' ') { out.append('_'); changed = true; }
                else changed = true;
            }
            return changed ? out.toString() : null;
        };
        b.aliasInput.setFilters(new InputFilter[]{allowed, new InputFilter.LengthFilter(15)});
        b.aliasInput.addTextChangedListener(new SimpleWatcher(text -> onAliasChanged(text.trim())));
    }

    private void onAliasChanged(String alias) {
        handler.removeCallbacksAndMessages(null);
        if (pending != null) pending.cancel();
        checkedAlias = "";
        if (!ALIAS.matcher(alias).matches()) {
            setStatus(Status.RULES);
            return;
        }
        setStatus(Status.CHECKING);
        handler.postDelayed(() -> checkAvailability(alias), DEBOUNCE_MS);
    }

    private void checkAvailability(String alias) {
        pending = auth.aliasAvailable(alias, new ApiCallback<AliasAvailability>() {
            @Override
            public void onSuccess(AliasAvailability a) {
                if (!alias.equals(currentAlias())) return;      // user kept typing
                checkedAlias = alias;
                setStatus(a.valid && a.available ? Status.AVAILABLE : (a.valid ? Status.TAKEN : Status.RULES));
            }

            @Override
            public void onError(ApiException e) {
                if (!alias.equals(currentAlias())) return;
                setStatus(Status.OFFLINE);
            }
        });
    }

    private String currentAlias() {
        return b.aliasInput.getText() == null ? "" : b.aliasInput.getText().toString().trim();
    }

    private void setStatus(Status s) {
        status = s;
        renderStatus();
    }

    private void renderStatus() {
        switch (status) {
            case CHECKING:  styleStatus(R.string.alias_checking, R.color.text_secondary, R.color.surface_high, 0); break;
            case AVAILABLE: styleStatus(R.string.alias_available, R.color.sage, R.color.sage_tint, R.drawable.ic_check); break;
            case TAKEN:     styleStatus(R.string.alias_taken, R.color.brick, R.color.brick_tint, R.drawable.ic_close); break;
            case OFFLINE:   styleStatus(R.string.alias_offline, R.color.text_secondary, R.color.surface_high, R.drawable.ic_wifi_off); break;
            default:        styleStatus(R.string.alias_rules, R.color.text_muted, R.color.surface_high, 0); break;
        }
        updateNext();
    }

    private void styleStatus(@StringRes int text, @ColorRes int fg, @ColorRes int bg, @DrawableRes int icon) {
        b.aliasStatus.setText(text);
        int color = ContextCompat.getColor(this, fg);
        b.aliasStatus.setTextColor(color);
        b.aliasStatus.setBackgroundTintList(ContextCompat.getColorStateList(this, bg));
        b.aliasStatus.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
        b.aliasStatus.setCompoundDrawableTintList(ColorStateList.valueOf(color));
        b.aliasLayout.setBoxStrokeColorStateList(ContextCompat.getColorStateList(this,
                status == Status.TAKEN ? R.color.brick : R.color.sl_input_stroke));
    }

    // ── Suggestions ──────────────────────────────────────────────────────────

    private void setupSuggestions() {
        fillSuggestions();
        b.shuffle.setOnClickListener(v -> {
            Haptics.tick(this);
            fillSuggestions();
        });
    }

    private void fillSuggestions() {
        b.suggestions.removeAllViews();
        for (String s : AliasSuggestions.next(3)) {
            Chip chip = new Chip(this);
            chip.setText(s);
            chip.setCheckable(false);
            chip.setOnClickListener(v -> {
                Haptics.tick(this);
                b.aliasInput.setText(s);
                b.aliasInput.setSelection(s.length());
            });
            b.suggestions.addView(chip);
        }
    }

    // ── Password lock + biometrics ───────────────────────────────────────────

    private void setupLock() {
        boolean bioAvailable = Biometrics.available(this);
        b.lockSwitch.setOnCheckedChangeListener((v, on) -> {
            Haptics.tick(this);
            b.lockPanel.setVisibility(on ? View.VISIBLE : View.GONE);
            b.bioRow.setVisibility(on && bioAvailable ? View.VISIBLE : View.GONE);
            if (on) {
                b.passwordInput.requestFocus();
                b.scroll.post(() -> b.scroll.smoothScrollTo(0, b.lockPanel.getBottom()));
            } else {
                setBio(false);
            }
            updateNext();
        });
        b.passwordInput.addTextChangedListener(new SimpleWatcher(text -> {
            renderStrength(text);
            updateNext();
        }));
        renderStrength("");

        b.bioSwitch.setOnCheckedChangeListener((v, on) -> {
            if (!on || settingBio) return;                 // only confirm a user turning it ON
            setBio(false);
            Biometrics.prompt(this, getString(R.string.bio_prompt_title), getString(R.string.bio_prompt_subtitle),
                    new Biometrics.Callback() {
                        @Override
                        public void onSuccess() {
                            Haptics.click(AliasActivity.this);
                            setBio(true);
                        }

                        @Override
                        public void onFailure(@NonNull CharSequence reason) {
                            Toast.makeText(AliasActivity.this, R.string.bio_failed, Toast.LENGTH_SHORT).show();
                        }
                    });
        });
    }

    private void setBio(boolean on) {
        settingBio = true;
        b.bioSwitch.setChecked(on);
        settingBio = false;
    }

    /** 0 = empty, 1 weak, 2 fair, 3 strong. */
    static int strength(String pw) {
        if (pw == null || pw.isEmpty()) return 0;
        int score = 0;
        if (pw.length() >= 8) score++;
        if (pw.length() >= 12) score++;
        boolean lower = false, upper = false, digit = false, symbol = false;
        for (char c : pw.toCharArray()) {
            if (Character.isLowerCase(c)) lower = true;
            else if (Character.isUpperCase(c)) upper = true;
            else if (Character.isDigit(c)) digit = true;
            else symbol = true;
        }
        if ((lower && upper) || (digit && (lower || upper))) score++;
        if (symbol) score++;
        if (pw.length() < 8) return 1;
        return Math.max(1, Math.min(3, score));
    }

    private void renderStrength(String pw) {
        int s = strength(pw);
        @ColorRes int on = s <= 1 ? R.color.brick : (s == 2 ? R.color.amber : R.color.sage);
        View[] segs = {b.seg1, b.seg2, b.seg3};
        for (int i = 0; i < segs.length; i++) {
            segs[i].setBackgroundTintList(ContextCompat.getColorStateList(this, i < s ? on : R.color.hairline));
        }
        b.strengthText.setText(s == 0 ? "" : getString(s == 1 ? R.string.strength_weak
                : s == 2 ? R.string.strength_fair : R.string.strength_strong));
        b.strengthText.setTextColor(ContextCompat.getColor(this, s == 0 ? R.color.text_muted : on));
    }

    private String password() {
        return b.passwordInput.getText() == null ? "" : b.passwordInput.getText().toString();
    }

    // ── Next ─────────────────────────────────────────────────────────────────

    private void updateNext() {
        boolean aliasOk = status == Status.AVAILABLE && checkedAlias.equals(currentAlias());
        boolean lockOk = !b.lockSwitch.isChecked() || password().length() >= 8;
        b.next.setEnabled(aliasOk && lockOk);
    }

    private void next() {
        OnboardingDraft draft = OnboardingDraft.get();
        if (draft == null) {
            Router.toWelcome(this);
            return;
        }
        draft.alias = currentAlias();
        boolean lock = b.lockSwitch.isChecked();
        draft.password = lock ? password() : null;
        draft.biometricEnabled = lock && b.bioSwitch.isChecked();
        Haptics.click(this);
        startActivity(new Intent(this, AvatarActivity.class));
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (pending != null) pending.cancel();
        super.onDestroy();
    }

    /** TextWatcher that only cares about the final text. */
    static final class SimpleWatcher implements TextWatcher {
        interface OnText { void changed(String text); }
        private final OnText onText;
        SimpleWatcher(OnText onText) { this.onText = onText; }
        @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) { }
        @Override public void onTextChanged(CharSequence s, int st, int b, int c) { }
        @Override public void afterTextChanged(Editable s) { onText.changed(s == null ? "" : s.toString()); }
    }
}
