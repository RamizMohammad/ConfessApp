package in.mohammad.ramiz.confess.ui.settings;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
import in.mohammad.ramiz.confess.ui.components.Aura;
import in.mohammad.ramiz.confess.ui.util.EdgeInsets;
import in.mohammad.ramiz.confess.ui.util.PhotoPrep;
import in.mohammad.ramiz.confess.ui.util.SimpleTextWatcher;

/**
 * S13 · Photo, aura & about. Old: ProfilePage (6 avatar PNGs) + UpdateAbout.
 *
 *  - photo: system photo picker (no storage permission) → square ≤768 px JPEG on the
 *    phone → POST users/me/avatar/upload (server strips metadata, moderates, stores in
 *    PixelVault). Choosing an aura afterwards removes the photo.
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
    /** Chosen aura, or null while a photo (current or new) is the avatar. */
    @Nullable private Aura selected;
    /** A new photo picked on this phone, not yet uploaded. */
    @Nullable private byte[] pendingPhoto;
    private boolean saving;

    private final ActivityResultLauncher<PickVisualMediaRequest> picker = registerForActivityResult(
            new ActivityResultContracts.PickVisualMedia(), this::onPicked);

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
        if (PhotoPrep.isPhoto(originalAvatar)) {
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

        b.pickPhoto.setOnClickListener(v -> picker.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build()));
        b.removePhoto.setOnClickListener(v -> select(Aura.resolve(originalAvatar.isEmpty() ? "aura_04" : originalAvatar), true));
        b.back.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        b.save.setOnClickListener(v -> save());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { leave(); }
        });
    }

    // ── Photo ────────────────────────────────────────────────────────────────

    private void onPicked(@Nullable Uri uri) {
        if (uri == null) return;
        b.photoError.setVisibility(View.GONE);
        b.pickPhoto.setEnabled(false);
        PhotoPrep.prepare(getContentResolver(), uri, new PhotoPrep.Callback() {
            @Override public void onReady(@NonNull Bitmap preview, @NonNull byte[] jpeg) {
                b.pickPhoto.setEnabled(true);
                pendingPhoto = jpeg;
                selected = null;
                markTiles(null);
                b.preview.setLocalPhoto(preview);
                b.auraName.setText(R.string.edit_photo_name);
                b.removePhoto.setVisibility(View.VISIBLE);
                b.pickPhoto.setText(R.string.edit_photo_change);
                pop();
                updateSave();
            }

            @Override public void onFailed() {
                b.pickPhoto.setEnabled(true);
                showPhotoError(getString(R.string.edit_photo_failed));
            }
        });
    }

    /** The account already uses a photo: show it, nothing selected in the grid. */
    private void showCurrentPhoto() {
        selected = null;
        markTiles(null);
        b.preview.setAvatar(originalAvatar);
        b.auraName.setText(R.string.edit_photo_name);
        b.removePhoto.setVisibility(View.VISIBLE);
        b.pickPhoto.setText(R.string.edit_photo_change);
    }

    private void showPhotoError(String message) {
        b.photoError.setText(message);
        b.photoError.setVisibility(View.VISIBLE);
        b.scroll.post(() -> b.scroll.smoothScrollTo(0, b.photoError.getTop()));
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

    /** An aura replaces any photo (current or freshly picked). */
    private void select(Aura aura, boolean fromUser) {
        selected = aura;
        pendingPhoto = null;
        markTiles(aura);
        b.preview.setAvatar(aura.id);
        b.auraName.setText(getString(R.string.avatar_label, aura.number(), aura.displayName));
        b.removePhoto.setVisibility(View.GONE);
        b.pickPhoto.setText(R.string.edit_photo_pick);
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

    private boolean avatarChanged() {
        if (pendingPhoto != null) return true;
        return selected != null && !selected.id.equalsIgnoreCase(originalAvatar);
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
        if (pendingPhoto != null) {
            setSaving(true, R.string.edit_uploading);
            users.uploadAvatar(pendingPhoto, new ApiCallback<JsonObject>() {
                @Override public void onSuccess(JsonObject data) {
                    pendingPhoto = null;
                    if (data.has("avatar")) originalAvatar = data.get("avatar").getAsString();
                    saveAbout();
                }
                @Override public void onError(ApiException e) { photoFailed(e); }
            });
        } else if (avatarChanged() && selected != null) {
            setSaving(true, R.string.edit_saving);
            String id = selected.id;
            users.updateAvatar(id, new ApiCallback<JsonObject>() {
                @Override public void onSuccess(JsonObject data) {
                    originalAvatar = id;
                    saveAbout();
                }
                @Override public void onError(ApiException e) { failed(e); }
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

    /** Upload refused or failed — explain under the photo section. */
    private void photoFailed(ApiException e) {
        setSaving(false, 0);
        Haptics.reject(this);
        if (e.is(ErrorCode.CONTENT_FLAGGED)) {
            String m = e.serverMessage.toLowerCase(Locale.ROOT);
            int res = m.contains("sexual") ? R.string.photo_flag_sexual
                    : m.contains("violent") ? R.string.photo_flag_violent
                    : m.contains("hateful") ? R.string.photo_flag_hateful
                    : m.contains("personal") ? R.string.photo_flag_personal
                    : R.string.photo_flag_generic;
            showPhotoError(getString(res));
        } else if (e.is(ErrorCode.MODERATION_UNAVAILABLE) || e.httpStatus == 503) {
            showPhotoError(getString(R.string.photo_unavailable));
        } else if (e.is(ErrorCode.VALIDATION_ERROR) || e.httpStatus == 413) {
            showPhotoError(e.serverMessage.isEmpty() ? getString(R.string.edit_photo_failed) : e.serverMessage);
        } else {
            showPhotoError(e.userMessage(this));
        }
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
