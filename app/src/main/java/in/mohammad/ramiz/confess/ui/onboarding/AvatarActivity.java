package in.mohammad.ramiz.confess.ui.onboarding;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.api.Pending;
import in.mohammad.ramiz.confess.data.api.ErrorCode;
import in.mohammad.ramiz.confess.data.model.Me;
import in.mohammad.ramiz.confess.data.model.RegisterRequest;
import in.mohammad.ramiz.confess.data.repo.AuthRepository;
import in.mohammad.ramiz.confess.databinding.ActivityAvatarBinding;
import in.mohammad.ramiz.confess.databinding.ItemAuraTileBinding;
import in.mohammad.ramiz.confess.ui.auth.OnboardingDraft;
import in.mohammad.ramiz.confess.ui.base.BaseActivity;
import in.mohammad.ramiz.confess.ui.base.Router;
import in.mohammad.ramiz.confess.ui.avatar.AvatarStudioActivity;
import in.mohammad.ramiz.confess.ui.components.Aura;
import in.mohammad.ramiz.confess.ui.components.AvatarSpec;
import in.mohammad.ramiz.confess.ui.util.EdgeInsets;

/**
 * S5 · Pick a face that isn't yours. Old: ProfilePage (6 preset PNGs) — now 12
 * generative auras. Last onboarding step: creates the account (POST auth/register).
 *
 *   201            → session saved, unlocked → home
 *   ALIAS_TAKEN    → someone took it meanwhile → back to the alias step
 *   ALIAS_NOT_ALLOWED → the wording check said no → back to the alias step (it shows why)
 *   ACCOUNT_EXISTS → this Google account already registered → just log in
 *   CONTENT_FLAGGED (about) → inline error on the about field
 *   INVALID_CREDENTIALS → Google token expired (≈1h) → start again
 */
public class AvatarActivity extends BaseActivity {

    private ActivityAvatarBinding b;
    private final List<ItemAuraTileBinding> tiles = new ArrayList<>();
    private final AuthRepository auth = new AuthRepository();
    /** The aura picked in the grid, or null while a designed avatar is chosen. */
    @Nullable private Aura selected;
    /** What will be sent: an aura id or a designed avatar ("av1:…"). */
    private String chosen = "aura_04";

    private final androidx.activity.result.ActivityResultLauncher<Intent> studio = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(), res -> {
                if (res.getResultCode() != RESULT_OK || res.getData() == null) return;
                String spec = res.getData().getStringExtra(AvatarStudioActivity.EXTRA_SPEC);
                if (AvatarSpec.isSpec(spec)) selectDesigned(spec, true);
            });
    @Nullable private Pending pending;
    private boolean working = false;

    @Override
    protected boolean requiresSession() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityAvatarBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        OnboardingDraft draft = OnboardingDraft.get();
        if (draft == null || draft.googleIdToken == null || draft.alias == null) {
            Toast.makeText(this, R.string.register_expired, Toast.LENGTH_LONG).show();
            Router.toWelcome(this);
            return;
        }

        EdgeInsets.padTop(b.stepBar.getRoot());
        EdgeInsets.padBottom(b.bottomBar, true);
        StepBar.bind(b.stepBar, this, 2, AliasActivity.TOTAL_STEPS);

        b.previewAlias.setText("@" + draft.alias);
        buildGrid();
        Aura initial = Aura.fromId(draft.avatar);
        if (AvatarSpec.isSpec(draft.avatar)) selectDesigned(draft.avatar, false);
        else select(initial != null ? initial : Aura.values()[new SecureRandom().nextInt(Aura.values().length)], false);
        b.design.setOnClickListener(v -> studio.launch(AvatarStudioActivity.intent(this,
                AvatarSpec.isSpec(chosen) ? chosen : null, true)));
        b.aboutInput.setText(draft.about);

        b.random.setOnClickListener(v -> {
            Aura[] all = Aura.values();
            Aura next;
            do { next = all[new SecureRandom().nextInt(all.length)]; } while (next == selected);
            select(next, true);
        });
        b.enter.setOnClickListener(v -> register());
    }

    // ── Aura grid ────────────────────────────────────────────────────────────

    private void buildGrid() {
        for (Aura aura : Aura.values()) {
            ItemAuraTileBinding tile = ItemAuraTileBinding.inflate(LayoutInflater.from(this), b.grid, false);
            tile.aura.setAura(aura);
            tile.label.setText(getString(R.string.avatar_short, aura.number()));
            tile.getRoot().setContentDescription(getString(R.string.avatar_label, aura.number(), aura.displayName));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            lp.width = 0;
            int m = Math.round(5 * getResources().getDisplayMetrics().density);
            lp.setMargins(m, m, m, m);
            tile.getRoot().setLayoutParams(lp);
            tile.getRoot().setOnClickListener(v -> select(aura, true));
            tiles.add(tile);
            b.grid.addView(tile.getRoot());
        }
    }

    /** A designed avatar from the studio: no aura tile is selected. */
    private void selectDesigned(String spec, boolean fromUser) {
        selected = null;
        chosen = spec;
        for (ItemAuraTileBinding t : tiles) {
            t.getRoot().setSelected(false);
            t.check.setVisibility(View.GONE);
            t.label.setTextColor(getColor(R.color.text_secondary));
        }
        b.preview.setAvatar(spec);
        b.previewName.setText(R.string.avs_name);
        b.design.setText(R.string.avs_edit);
        if (fromUser) {
            Haptics.click(this);
            b.preview.setScaleX(0.9f);
            b.preview.setScaleY(0.9f);
            b.preview.animate().scaleX(1f).scaleY(1f).setDuration(220).start();
        }
        OnboardingDraft draft = OnboardingDraft.get();
        if (draft != null) draft.avatar = spec;
    }

    private void select(Aura aura, boolean fromUser) {
        selected = aura;
        chosen = aura.id;
        b.design.setText(R.string.avs_design);
        for (ItemAuraTileBinding t : tiles) {
            boolean on = t.aura.getAura() == aura;
            t.getRoot().setSelected(on);
            t.check.setVisibility(on ? View.VISIBLE : View.GONE);
            t.label.setTextColor(getColor(on ? R.color.amber : R.color.text_secondary));
        }
        b.preview.setAvatar(aura.id);
        b.previewName.setText(getString(R.string.avatar_label, aura.number(), aura.displayName));
        if (fromUser) {
            Haptics.tick(this);
            b.preview.setScaleX(0.9f);
            b.preview.setScaleY(0.9f);
            b.preview.animate().scaleX(1f).scaleY(1f).setDuration(220).start();
        }
        OnboardingDraft draft = OnboardingDraft.get();
        if (draft != null) draft.avatar = aura.id;
    }

    // ── Register ─────────────────────────────────────────────────────────────

    private String about() {
        return b.aboutInput.getText() == null ? "" : b.aboutInput.getText().toString().trim().replaceAll("\\s+", " ");
    }

    private void register() {
        OnboardingDraft draft = OnboardingDraft.get();
        if (working || draft == null || draft.googleIdToken == null) return;
        draft.about = about();
        setWorking(true);
        b.error.setVisibility(View.GONE);
        b.aboutLayout.setError(null);

        RegisterRequest body = new RegisterRequest(draft.googleIdToken, draft.alias, chosen,
                draft.about, draft.password, draft.biometricEnabled);
        pending = auth.register(body, new ApiCallback<Me>() {
            @Override
            public void onSuccess(Me me) {
                Haptics.click(AvatarActivity.this);
                OnboardingDraft.clear();
                Router.toApp(AvatarActivity.this);
            }

            @Override
            public void onError(ApiException e) {
                setWorking(false);
                handleError(e, draft);
            }
        });
    }

    private void handleError(ApiException e, OnboardingDraft draft) {
        Haptics.reject(this);
        if (e.is(ErrorCode.ALIAS_TAKEN) || e.is(ErrorCode.ALIAS_NOT_ALLOWED)) {
            Toast.makeText(this, e.is(ErrorCode.ALIAS_TAKEN) ? R.string.register_alias_taken
                    : R.string.register_alias_not_allowed, Toast.LENGTH_LONG).show();
            draft.alias = null;
            startActivity(new Intent(this, AliasActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
        } else if (e.is(ErrorCode.ACCOUNT_EXISTS)) {
            // Registered from another phone/tab meanwhile — just sign in.
            setWorking(true);
            auth.login(draft.googleIdToken, new ApiCallback<Me>() {
                @Override
                public void onSuccess(Me me) {
                    OnboardingDraft.clear();
                    Router.toApp(AvatarActivity.this);
                }

                @Override
                public void onError(ApiException err) {
                    setWorking(false);
                    showError(err.userMessage(AvatarActivity.this));
                }
            });
        } else if (e.is(ErrorCode.CONTENT_FLAGGED)) {
            b.aboutLayout.setError(e.userMessage(this));
            b.scroll.post(() -> b.scroll.smoothScrollTo(0, b.aboutLayout.getTop()));
        } else if (e.is(ErrorCode.INVALID_CREDENTIALS) || e.is(ErrorCode.UNAUTHORIZED)) {
            Toast.makeText(this, R.string.register_expired, Toast.LENGTH_LONG).show();
            OnboardingDraft.clear();
            Router.toWelcome(this);
        } else {
            showError(e.userMessage(this));
        }
    }

    private void setWorking(boolean on) {
        working = on;
        b.enter.setEnabled(!on);
        b.enter.setText(on ? R.string.avatar_creating : R.string.avatar_enter);
        b.random.setEnabled(!on);
        b.design.setEnabled(!on);
        b.stepBar.back.setEnabled(!on);
    }

    private void showError(String message) {
        b.error.setText(message);
        b.error.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        if (pending != null) pending.cancel();
        super.onDestroy();
    }
}
