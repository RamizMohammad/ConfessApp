package in.mohammad.ramiz.confess.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.api.ErrorCode;
import in.mohammad.ramiz.confess.data.model.Me;
import in.mohammad.ramiz.confess.data.repo.UserRepository;
import in.mohammad.ramiz.confess.data.session.SessionManager;
import in.mohammad.ramiz.confess.databinding.ActivityEditProfileBinding;
import in.mohammad.ramiz.confess.databinding.ItemAuraTileBinding;
import in.mohammad.ramiz.confess.ui.base.BaseActivity;
import in.mohammad.ramiz.confess.ui.avatar.AvatarStudioActivity;
import in.mohammad.ramiz.confess.ui.components.Aura;
import in.mohammad.ramiz.confess.ui.components.AvatarSpec;
import in.mohammad.ramiz.confess.ui.util.EdgeInsets;
import in.mohammad.ramiz.confess.ui.util.PhotoPrep;
import in.mohammad.ramiz.confess.ui.util.SimpleTextWatcher;

/**
 * S13 · Avatar, aura & about. Old: ProfilePage (6 avatar PNGs) + UpdateAbout.
 *
 *  - avatar: designed in AvatarStudioActivity (illustrated, never a photo) → "av1:…" → POST users/me/avatar.
 *    Photo uploads are gone from the app; an old uploaded photo still shows until replaced.
 *  - aura: the 12 presets → POST users/me/avatar
 *  - about: moderated, 451 shows inline
 * Saves only what changed. The alias is fixed (no endpoint to change it).
 */
public class EditProfileActivity extends BaseActivity {

    private ActivityEditProfileBinding b;
    private final UserRepository users = new UserRepository();
    private final List<ItemAuraTileBinding> tiles = new ArrayList<>();
    private String originalAvatar = "";
    private String originalAbout = "";
    /** Chosen aura, or null while a designed avatar (or an old photo) is the avatar. */
    @Nullable private Aura selected;
    /** A designed avatar ("av1:…"), or null. */
    @Nullable private String designed;
    private boolean saving;

    private final ActivityResultLauncher<Intent> studio = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), res -> {
                if (res.getResultCode() != RESULT_OK || res.getData() == null) return;
                String spec = res.getData().getStringExtra(AvatarStudioActivity.EXTRA_SPEC);
                if (AvatarSpec.isSpec(spec)) showDesigned(spec, true);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityEditProfileBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeInsets.padTop(b.topBar);
        EdgeInsets.padBottom(b.bottom, true);

        Me me = SessionManager.get().cachedMe();
        if (me == null) { finish(); return; }
        originalAvatar = me.avatar == null ? "" : me.avatar;
        originalAbout = me.about == null ? "" : me.about.trim();
        b.alias.setText("@" + me.alias);
        b.previewAlias.setText("@" + me.alias);

        buildGrid();
        if (AvatarSpec.isSpec(originalAvatar)) {
            showDesigned(originalAvatar, false);
        } else if (PhotoPrep.isPhoto(originalAvatar)) {
            showCurrentPhoto();
        } else {
            select(Aura.resolve(originalAvatar), false);
        }
        if (savedInstanceState == null) b.aboutInput.setText(originalAbout);
        b.aboutInput.addTextChangedListener(new SimpleTextWatcher(t -> {
            b.aboutLayout.setError(null);
            renderPreviewAbout();
            updateSave();
        }));
        renderPreviewAbout();
        updateSave();

        b.pickPhoto.setOnClickListener(v -> studio.launch(AvatarStudioActivity.intent(this,
                designed != null ? designed : (AvatarSpec.isSpec(originalAvatar) ? originalAvatar : null), false)));
        b.removePhoto.setOnClickListener(v -> select(Aura.resolve(
                Aura.fromId(originalAvatar) != null ? originalAvatar : "aura_04"), true));
        b.back.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        b.save.setOnClickListener(v -> save());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { leave(); }
        });
    }

    // ── Designed avatar ──────────────────────────────────────────────────────

    private void showDesigned(@NonNull String spec, boolean fromUser) {
        designed = spec;
        selected = null;
        markTiles(null);
        b.preview.setAvatar(spec);
        b.auraName.setText(R.string.avs_name);
        b.removePhoto.setVisibility(View.VISIBLE);
        b.pickPhoto.setText(R.string.avs_edit);
        b.photoError.setVisibility(View.GONE);
        if (fromUser) pop();
        updateSave();
    }

    /** The account still uses an old uploaded photo: show it, nothing selected in the grid. */
    private void showCurrentPhoto() {
        selected = null;
        designed = null;
        markTiles(null);
        b.preview.setAvatar(originalAvatar);
        b.auraName.setText(R.string.edit_photo_name);
        b.removePhoto.setVisibility(View.VISIBLE);
        b.pickPhoto.setText(R.string.avs_design);
    }

    // ── Aura grid (same tiles as onboarding) ─────────────────────────────────

    private void buildGrid() {
        int m = Math.round(5 * getResources().getDisplayMetrics().density);
        for (Aura aura : Aura.values()) {
            ItemAuraTileBinding tile = ItemAuraTileBinding.inflate(LayoutInflater.from(this), b.grid, false);
            tile.aura.setAura(aura);
            tile.label.setText(getString(R.string.avatar_short, aura.number()));
            tile.getRoot().setContentDescription(getString(R.string.avatar_label, aura.number(), aura.displayName));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            lp.width = 0;
            lp.setMargins(m, m, m, m);
            tile.getRoot().setLayoutParams(lp);
            tile.getRoot().setOnClickListener(v -> select(aura, true));
            tiles.add(tile);
            b.grid.addView(tile.getRoot());
        }
    }

    private void markTiles(@Nullable Aura aura) {
        for (ItemAuraTileBinding t : tiles) {
            boolean on = t.aura.getAura() == aura;
            t.getRoot().setSelected(on);
            t.check.setVisibility(on ? View.VISIBLE : View.GONE);
            t.label.setTextColor(getColor(on ? R.color.amber : R.color.text_secondary));
        }
    }

    /** An aura replaces a designed avatar or an old photo. */
    private void select(Aura aura, boolean fromUser) {
        selected = aura;
        designed = null;
        markTiles(aura);
        b.preview.setAvatar(aura.id);
        b.auraName.setText(getString(R.string.avatar_label, aura.number(), aura.displayName));
        b.removePhoto.setVisibility(View.GONE);
        b.pickPhoto.setText(R.string.avs_design);
        b.photoError.setVisibility(View.GONE);
        if (fromUser) pop();
        updateSave();
    }

    private void pop() {
        Haptics.tick(this);
        b.preview.setScaleX(0.9f);
        b.preview.setScaleY(0.9f);
        b.preview.animate().scaleX(1f).scaleY(1f).setDuration(220).start();
    }

    // ── State ────────────────────────────────────────────────────────────────

    private String about() {
        return b.aboutInput.getText() == null ? "" : b.aboutInput.getText().toString().trim().replaceAll("\\s+", " ");
    }

    private void renderPreviewAbout() {
        String a = about();
        b.previewAbout.setVisibility(a.isEmpty() ? View.GONE : View.VISIBLE);
        b.previewAbout.setText("“" + a + "”");
    }

    /** The value to save ("aura_04" / "av1:…"), or null when the avatar didn't change. */
    @Nullable
    private String newAvatar() {
        String v = designed != null ? designed : selected != null ? selected.id : null;
        return v == null || v.equalsIgnoreCase(originalAvatar) ? null : v;
    }

    private boolean avatarChanged() {
        return newAvatar() != null;
    }

    private boolean aboutChanged() {
        return !about().equals(originalAbout);
    }

    private void updateSave() {
        b.save.setEnabled(!saving && (avatarChanged() || aboutChanged()));
    }

    // ── Save ─────────────────────────────────────────────────────────────────

    private void save() {
        if (saving) return;
        b.photoError.setVisibility(View.GONE);
        String id = newAvatar();
        if (id != null) {
            setSaving(true, R.string.edit_saving);
            users.updateAvatar(id, new ApiCallback<JsonObject>() {
                @Override public void onSuccess(JsonObject data) {
                    originalAvatar = id;
                    saveAbout();
                }
                @Override public void onError(ApiException e) { avatarFailed(e); }
            });
        } else {
            setSaving(true, R.string.edit_saving);
            saveAbout();
        }
    }

    private void saveAbout() {
        if (!aboutChanged()) { finished(); return; }
        String text = about();
        users.updateAbout(text, new ApiCallback<JsonObject>() {
            @Override public void onSuccess(JsonObject data) {
                originalAbout = text;
                finished();
            }
            @Override public void onError(ApiException e) { failed(e); }
        });
    }

    private void finished() {
        users.me(new ApiCallback<Me>() {                    // refresh the cached profile
            @Override public void onSuccess(Me m) { done(); }
            @Override public void onError(ApiException e) { done(); }
        });
    }

    private void done() {
        Haptics.click(this);
        Toast.makeText(this, R.string.edit_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    /** Saving the avatar failed — explain under the avatar section. */
    private void avatarFailed(ApiException e) {
        setSaving(false, 0);
        Haptics.reject(this);
        b.photoError.setText(e.userMessage(this));
        b.photoError.setVisibility(View.VISIBLE);
        b.scroll.post(() -> b.scroll.smoothScrollTo(0, b.photoError.getTop()));
    }

    private void failed(ApiException e) {
        setSaving(false, 0);
        Haptics.reject(this);
        if (e.is(ErrorCode.CONTENT_FLAGGED) || e.is(ErrorCode.VALIDATION_ERROR)) {
            b.aboutLayout.setError(e.userMessage(this));
            b.scroll.post(() -> b.scroll.smoothScrollTo(0, b.aboutLayout.getTop()));
        } else {
            Snackbar.make(b.getRoot(), e.userMessage(this), Snackbar.LENGTH_LONG).setAnchorView(b.bottom).show();
        }
    }

    private void setSaving(boolean on, int label) {
        saving = on;
        b.save.setText(on ? label : R.string.edit_save);
        b.aboutInput.setEnabled(!on);
        b.pickPhoto.setEnabled(!on);
        b.removePhoto.setEnabled(!on);
        b.grid.setEnabled(!on);
        updateSave();
    }

    private void leave() {
        if (saving) return;
        if (!avatarChanged() && !aboutChanged()) { finish(); return; }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.edit_discard_title)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.edit_discard_confirm, (d, w) -> finish())
                .show();
    }
}
