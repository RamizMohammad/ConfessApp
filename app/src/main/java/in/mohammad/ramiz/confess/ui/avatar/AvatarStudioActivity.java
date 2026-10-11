package in.mohammad.ramiz.confess.ui.avatar;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.security.SecureRandom;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.databinding.ActivityAvatarStudioBinding;
import in.mohammad.ramiz.confess.databinding.ItemAvatarOptionBinding;
import in.mohammad.ramiz.confess.ui.base.BaseActivity;
import in.mohammad.ramiz.confess.ui.components.Aura;
import in.mohammad.ramiz.confess.ui.components.AvatarRenderer;
import in.mohammad.ramiz.confess.ui.components.AvatarSpec;
import in.mohammad.ramiz.confess.ui.components.AvatarSpec.Part;
import in.mohammad.ramiz.confess.ui.util.EdgeInsets;

/**
 * Build an illustrated avatar (Bitmoji-lite): pick a part, tap a choice, see it live.
 * Shuffle for a random look. Returns the spec string ("av1:…") in {@link #EXTRA_SPEC};
 * nothing is saved here — the caller saves it with the rest of the profile.
 */
public class AvatarStudioActivity extends BaseActivity {

    public static final String EXTRA_SPEC = "avatar_spec";
    /** Onboarding has no session yet. */
    public static final String EXTRA_NO_SESSION = "no_session";
    private static final String STATE_SPEC = "spec";
    private static final String STATE_PART = "part";

    private ActivityAvatarStudioBinding b;
    private AvatarSpec spec;
    private String startedFrom;
    private Part part = Part.HAIR;
    private final OptionAdapter adapter = new OptionAdapter();
    private final SecureRandom random = new SecureRandom();

    public static Intent intent(@NonNull Context ctx, @Nullable String current, boolean noSession) {
        return new Intent(ctx, AvatarStudioActivity.class)
                .putExtra(EXTRA_SPEC, current)
                .putExtra(EXTRA_NO_SESSION, noSession);
    }

    @Override
    protected boolean requiresSession() {
        return !getIntent().getBooleanExtra(EXTRA_NO_SESSION, false);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityAvatarStudioBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeInsets.padTop(b.topBar);
        EdgeInsets.padBottom(b.bottom, false);

        String given = getIntent().getStringExtra(EXTRA_SPEC);
        AvatarSpec initial = AvatarSpec.isSpec(given) ? AvatarSpec.parse(given) : AvatarSpec.random(random);
        startedFrom = initial.encode();
        spec = initial;
        if (savedInstanceState != null) {
            String s = savedInstanceState.getString(STATE_SPEC);
            if (AvatarSpec.isSpec(s)) spec = AvatarSpec.parse(s);
            part = Part.values()[savedInstanceState.getInt(STATE_PART, Part.HAIR.ordinal())];
        }

        buildPartChips();
        b.options.setLayoutManager(new GridLayoutManager(this, 4));
        b.options.setAdapter(adapter);
        b.options.setItemAnimator(null);
        renderPreview(false);

        b.close.setOnClickListener(v -> leave());
        b.shuffle.setOnClickListener(v -> {
            spec = AvatarSpec.random(random);
            Haptics.tick(this);
            b.shuffle.animate().rotationBy(180f).setDuration(260).start();
            renderPreview(true);
            adapter.notifyDataSetChanged();
        });
        b.save.setOnClickListener(v -> {
            Haptics.click(this);
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_SPEC, spec.encode()));
            finish();
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { leave(); }
        });
    }

    private void buildPartChips() {
        for (Part p : Part.values()) {
            Chip chip = new Chip(this, null, com.google.android.material.R.attr.chipStyle);
            chip.setId(View.generateViewId());
            chip.setText(p.label);
            chip.setCheckable(true);
            chip.setCheckedIconVisible(false);
            chip.setChecked(p == part);
            chip.setOnClickListener(v -> {
                if (part == p) return;
                Haptics.tick(this);
                part = p;
                adapter.notifyDataSetChanged();
                b.options.scrollToPosition(0);
            });
            b.parts.addView(chip);
        }
    }

    private void choose(int value) {
        if (spec.get(part) == value) return;
        int old = spec.get(part);
        spec = spec.with(part, value);
        Haptics.tick(this);
        renderPreview(true);
        adapter.notifyItemChanged(old);
        adapter.notifyItemChanged(value);
    }

    private void renderPreview(boolean bounce) {
        b.preview.setAvatar(spec.encode());
        if (!bounce) return;
        b.preview.animate().cancel();
        b.preview.setScaleX(0.94f);
        b.preview.setScaleY(0.94f);
        b.preview.animate().scaleX(1f).scaleY(1f).setDuration(220)
                .setInterpolator(new android.view.animation.OvershootInterpolator(2.5f)).start();
    }

    private void leave() {
        if (spec.encode().equals(startedFrom)) { finish(); return; }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.avs_discard_title)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.edit_discard_confirm, (d, w) -> finish())
                .show();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(STATE_SPEC, spec.encode());
        out.putInt(STATE_PART, part.ordinal());
    }

    // ── Choices grid ─────────────────────────────────────────────────────────

    private final class OptionAdapter extends RecyclerView.Adapter<OptionAdapter.Holder> {

        @Override
        public int getItemCount() {
            return part.count;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemAvatarOptionBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int i) {
            boolean selected = spec.get(part) == i;
            h.b.tile.setSelected(selected);
            h.b.tile.setContentDescription(getString(R.string.avs_option_cd, getString(part.label), i + 1));
            h.b.tile.setOnClickListener(v -> choose(i));
            if (part == Part.BACKDROP) {
                h.b.swatch.setVisibility(View.GONE);
                h.b.face.setVisibility(View.VISIBLE);
                h.b.face.setAvatar(Aura.values()[i].id);
            } else if (part.swatch) {
                h.b.face.setVisibility(View.GONE);
                h.b.swatch.setVisibility(View.VISIBLE);
                GradientDrawable dot = new GradientDrawable();
                dot.setShape(GradientDrawable.OVAL);
                dot.setColor(swatchColor(part, i));
                dot.setStroke(Math.round(getResources().getDisplayMetrics().density), getColor(R.color.hairline));
                h.b.swatch.setBackground(dot);
            } else {
                h.b.swatch.setVisibility(View.GONE);
                h.b.face.setVisibility(View.VISIBLE);
                h.b.face.setAvatar(spec.with(part, i).encode());
            }
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ItemAvatarOptionBinding b;

            Holder(ItemAvatarOptionBinding b) {
                super(b.getRoot());
                this.b = b;
            }
        }
    }

    private static int swatchColor(Part p, int i) {
        switch (p) {
            case SKIN: return AvatarRenderer.SKIN[i];
            case HAIR_COLOR: return AvatarRenderer.HAIR[i];
            default: return AvatarRenderer.OUTFIT[i];
        }
    }
}
