package in.mohammad.ramiz.confess.ui.dev;

import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

import in.mohammad.ramiz.confess.BuildConfig;
import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Formats;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiClient;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.api.Calls;
import in.mohammad.ramiz.confess.data.model.AliasAvailability;
import in.mohammad.ramiz.confess.data.model.Me;
import in.mohammad.ramiz.confess.data.repo.AuthRepository;
import in.mohammad.ramiz.confess.data.repo.UserRepository;
import in.mohammad.ramiz.confess.data.session.SessionManager;
import in.mohammad.ramiz.confess.databinding.ActivityDesignCheckBinding;
import in.mohammad.ramiz.confess.databinding.ItemConfessionCardBinding;
import in.mohammad.ramiz.confess.ui.components.Aura;
import in.mohammad.ramiz.confess.ui.components.AuraAvatarView;
import in.mohammad.ramiz.confess.ui.util.EdgeInsets;

/**
 * Section 0 — every token and component on one screen.
 * Build, open, scroll top to bottom, and compare against new_ui/.
 * Temporary: replaced by the real launcher in Section 2.
 */
public class DesignCheckActivity extends AppCompatActivity {

    private ActivityDesignCheckBinding b;
    private final List<AuraAvatarView> auraViews = new ArrayList<>();
    private boolean felt = false;
    private int feltCount = 1402;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeInsets.edgeToEdge(this);
        b = ActivityDesignCheckBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeInsets.padSystemBars(b.root, true);

        b.passwordLayout.setError("That's not it. Try again.");
        buildSwatches();
        bindSampleCard();
        buildAuraGrid();
        buildIconGrid();

        b.btnPrimary.setOnClickListener(v -> Haptics.click(this));
        b.btnServerCheck.setOnClickListener(v -> runServerCheck());
        b.btnSnackbar.setOnClickListener(v ->
                Snackbar.make(b.getRoot(), "Your letter was released into the dark.", Snackbar.LENGTH_LONG)
                        .setAction("Undo", x -> { })
                        .show());
        b.btnSheet.setOnClickListener(v -> {
            BottomSheetDialog sheet = new BottomSheetDialog(this);
            sheet.setContentView(LayoutInflater.from(this).inflate(R.layout.sheet_design_sample, null));
            sheet.show();
        });
        b.btnDialog.setOnClickListener(v ->
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Delete this confession?")
                        .setMessage("It will disappear for everyone, along with its responses. This can't be undone.")
                        .setNegativeButton(R.string.action_cancel, null)
                        .setPositiveButton(R.string.action_delete, null)
                        .show());
    }

    // ── Colors ────────────────────────────────────────────────────────────────

    private void buildSwatches() {
        int[][] swatches = {
                {R.color.ink}, {R.color.surface_raised}, {R.color.hairline}, {R.color.paper},
                {R.color.paper_ink}, {R.color.text_primary}, {R.color.text_secondary}, {R.color.text_muted},
                {R.color.amber}, {R.color.brick}, {R.color.sage},
        };
        String[] names = {"ink", "raised", "hairline", "paper", "paper ink", "text", "text 2", "muted",
                "amber", "brick", "sage"};
        for (int i = 0; i < swatches.length; i++) {
            b.swatches.addView(swatch(swatches[i][0], names[i]));
        }
    }

    private View swatch(@ColorRes int color, String name) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(64), LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(8));
        col.setLayoutParams(lp);

        View chip = new View(this);
        chip.setLayoutParams(new LinearLayout.LayoutParams(dp(56), dp(56)));
        chip.setBackgroundResource(R.drawable.bg_plate);
        chip.setBackgroundTintList(ContextCompat.getColorStateList(this, color));
        col.addView(chip);

        TextView label = new TextView(this);
        label.setTextAppearance(R.style.TextAppearance_Confess_MetaSmall);
        label.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        label.setText(name);
        label.setPadding(0, dp(6), 0, 0);
        col.addView(label);
        return col;
    }

    // ── Paper card ────────────────────────────────────────────────────────────

    private void bindSampleCard() {
        ItemConfessionCardBinding card = b.sampleCard;
        card.avatar.setAvatar("aura_04");
        card.alias.setText("@solitary_star");
        card.time.setText("2h ago");
        card.text.setText("“I still look for your car at every red light, even though I moved three states "
                + "away two years ago. I don't miss you, I just miss who I was before you made me quiet.”");
        card.responses.setText("86");
        card.commentsClosed.setVisibility(View.GONE);
        renderFelt(card);
        card.felt.setOnClickListener(v -> {
            felt = !felt;
            feltCount += felt ? 1 : -1;
            Haptics.tick(this);
            renderFelt(card);
        });
    }

    private void renderFelt(ItemConfessionCardBinding card) {
        card.felt.setIconResource(felt ? R.drawable.ic_heart_filled : R.drawable.ic_heart);
        card.felt.setIconTint(felt ? null : ContextCompat.getColorStateList(this, R.color.paper_meta));
        card.felt.setText(feltCount >= 1000 ? String.format("%.1fk", feltCount / 1000f) : String.valueOf(feltCount));
    }

    // ── Auras ─────────────────────────────────────────────────────────────────

    private void buildAuraGrid() {
        int cell = (getResources().getDisplayMetrics().widthPixels - dp(40)) / 4;
        for (Aura aura : Aura.values()) {
            AuraAvatarView v = new AuraAvatarView(this);
            v.setAura(aura);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = cell;
            lp.height = cell;
            int pad = dp(10);
            v.setPadding(pad, pad, pad, pad);
            v.setLayoutParams(lp);
            v.setOnClickListener(x -> selectAura(aura));
            auraViews.add(v);
            b.auraGrid.addView(v);
        }
        selectAura(Aura.AURA_04);
    }

    private void selectAura(Aura aura) {
        for (AuraAvatarView v : auraViews) v.setAuraSelected(v.getAura() == aura);
        b.auraName.setText(String.format("Aura %02d · %s", aura.number(), aura.displayName));
        b.sampleCard.avatar.setAura(aura);
        Haptics.tick(this);
    }

    // ── Icons ─────────────────────────────────────────────────────────────────

    private void buildIconGrid() {
        @DrawableRes int[] icons = {
                R.drawable.ic_arrow_left, R.drawable.ic_arrow_right, R.drawable.ic_arrow_up, R.drawable.ic_close,
                R.drawable.ic_heart, R.drawable.ic_heart_filled, R.drawable.ic_message, R.drawable.ic_bell,
                R.drawable.ic_search, R.drawable.ic_more, R.drawable.ic_lock, R.drawable.ic_fingerprint,
                R.drawable.ic_eye, R.drawable.ic_eye_off, R.drawable.ic_pencil, R.drawable.ic_compose,
                R.drawable.ic_trash, R.drawable.ic_settings, R.drawable.ic_flag, R.drawable.ic_letters,
                R.drawable.ic_user, R.drawable.ic_check, R.drawable.ic_shuffle, R.drawable.ic_help,
                R.drawable.ic_chevron_right, R.drawable.ic_chevron_down, R.drawable.ic_alert, R.drawable.ic_shield,
                R.drawable.ic_moon, R.drawable.ic_refresh, R.drawable.ic_log_out, R.drawable.ic_mail_open,
                R.drawable.ic_wifi_off,
        };
        int cell = (getResources().getDisplayMetrics().widthPixels - dp(40)) / 8;
        for (int icon : icons) {
            ImageView iv = new ImageView(this);
            iv.setImageResource(icon);
            if (icon != R.drawable.ic_heart_filled) {
                iv.setImageTintList(ContextCompat.getColorStateList(this, R.color.text_primary));
            }
            iv.setScaleType(ImageView.ScaleType.CENTER);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = cell;
            lp.height = cell;
            iv.setLayoutParams(lp);
            b.iconGrid.addView(iv);
        }
    }

    // ── Section 1: server check ───────────────────────────────────────────────

    private final StringBuilder log = new StringBuilder();

    private void line(boolean ok, String text) {
        log.append(ok ? "✓ " : "✗ ").append(text).append('\n');
        b.serverResult.setText(log.toString().trim());
        b.serverResult.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
    }

    private void runServerCheck() {
        log.setLength(0);
        String host = android.net.Uri.parse(BuildConfig.SERVER_URL).getHost();
        line(true, "server: " + host + (BuildConfig.BACKUP_URL.isEmpty() ? " (no backup)" : " (+ backup)"));
        line(Formats.count(1402).equals("1.4k") && Formats.count(950).equals("950"),
                "formats: 1402 → " + Formats.count(1402) + ", 950 → " + Formats.count(950));

        // 1) /ready — Postgres + Redis
        Calls.enqueue(ApiClient.api().ready(), new ApiCallback<JsonObject>() {
            @Override
            public void onSuccess(JsonObject data) {
                boolean pg = data.has("postgres") && data.get("postgres").getAsBoolean();
                boolean rd = data.has("redis") && data.get("redis").getAsBoolean();
                line(pg && rd, "ready: postgres=" + pg + ", redis=" + rd);
                checkApiKey();
            }

            @Override
            public void onError(ApiException e) {
                line(false, "ready: " + e.code + " (" + e.httpStatus + ") " + e.serverMessage);
                checkApiKey();
            }
        });
    }

    private void checkApiKey() {
        // 2) alias check — needs a valid x-api-key, no session
        new AuthRepository().aliasAvailable("design_check", new ApiCallback<AliasAvailability>() {
            @Override
            public void onSuccess(AliasAvailability a) {
                line(a.valid, "api key ok · alias 'design_check' valid=" + a.valid + ", available=" + a.available);
                checkSession();
            }

            @Override
            public void onError(ApiException e) {
                line(false, "api key: " + e.code + " (" + e.httpStatus + ") — check CLIENT_API vs API_KEY_HASH");
                checkSession();
            }
        });
    }

    private void checkSession() {
        // 3) signed-in call — only once Section 2 can sign in
        if (!SessionManager.get().isSignedIn()) {
            line(true, "session: not signed in yet (sign-in arrives in Section 2)");
            return;
        }
        new UserRepository().me(new ApiCallback<Me>() {
            @Override
            public void onSuccess(Me me) {
                line(true, "me: @" + me.alias + " · " + me.postCount + " posts");
            }

            @Override
            public void onError(ApiException e) {
                line(false, "me: " + e.userMessage(DesignCheckActivity.this));
            }
        });
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }
}
