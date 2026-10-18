package in.mohammad.ramiz.confess.ui.profile;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.model.Me;
import in.mohammad.ramiz.confess.data.repo.UserRepository;
import in.mohammad.ramiz.confess.databinding.ItemBannerPatternBinding;
import in.mohammad.ramiz.confess.databinding.SheetBannerBinding;
import in.mohammad.ramiz.confess.ui.components.Aura;
import in.mohammad.ramiz.confess.ui.components.AuraAvatarView;
import in.mohammad.ramiz.confess.ui.components.Banner;

/**
 * Pick my profile banner, like the aura: a live preview (with my avatar on it), the patterns as
 * little banners in the current colour, the 12 aura colours, and "Match my avatar" to go back to
 * the automatic one. Saves with POST users/me/banner and refreshes my cached profile.
 */
public final class BannerSheet {

    public interface Saved {
        void onSaved(@NonNull String spec);
    }

    private final Context ctx;
    private final Me me;
    private final SheetBannerBinding s;
    private final BottomSheetDialog d;
    private final List<ItemBannerPatternBinding> patterns = new ArrayList<>();
    private final List<AuraAvatarView> colours = new ArrayList<>();
    private final String original;
    @NonNull private Banner chosen;
    private boolean saving;

    private BannerSheet(@NonNull Context ctx, @NonNull Me me) {
        this.ctx = ctx;
        this.me = me;
        original = me.banner == null ? "" : me.banner;
        chosen = Banner.resolve(original, me.avatar, me.alias);
        d = new BottomSheetDialog(ctx);
        s = SheetBannerBinding.inflate(LayoutInflater.from(ctx));
    }

    public static void show(@NonNull Context ctx, @Nullable Me me, @Nullable Saved saved) {
        if (me == null) return;
        new BannerSheet(ctx, me).open(saved);
    }

    private void open(@Nullable Saved saved) {
        float dp = ctx.getResources().getDisplayMetrics().density;
        s.preview.setCornerRadius(20 * dp);
        s.preview.setFadeToInk(false);
        s.preview.setAnimated(true);
        s.avatar.setAvatar(me.avatar);

        String[] names = ctx.getResources().getStringArray(R.array.banner_patterns);
        for (int p = 0; p < Banner.PATTERNS; p++) {
            ItemBannerPatternBinding t = ItemBannerPatternBinding.inflate(LayoutInflater.from(ctx), s.patterns, false);
            t.thumb.setCornerRadius(12 * dp);
            t.thumb.setFadeToInk(false);
            t.name.setText(p < names.length ? names[p] : "");
            t.getRoot().setContentDescription(p < names.length ? names[p] : null);
            final int pattern = p;
            t.getRoot().setOnClickListener(v -> pick(chosen.withPattern(pattern)));
            patterns.add(t);
            s.patterns.addView(t.getRoot());
        }
        int size = Math.round(44 * dp), gap = Math.round(6 * dp);
        for (Aura a : Aura.values()) {
            AuraAvatarView dot = new AuraAvatarView(ctx);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(gap, gap, gap, gap);
            dot.setLayoutParams(lp);
            dot.setAura(a);
            dot.setContentDescription(a.displayName);
            dot.setOnClickListener(v -> pick(chosen.withColour(a)));
            colours.add(dot);
            s.colours.addView(dot);
        }

        s.auto.setOnClickListener(v -> pick(Banner.automatic(me.avatar, me.alias)));
        s.cancel.setOnClickListener(v -> d.dismiss());
        s.save.setOnClickListener(v -> save(saved));
        render(false);

        d.setContentView(s.getRoot());
        d.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        d.getBehavior().setSkipCollapsed(true);
        d.show();
        // Scroll the chosen pattern / colour into view once laid out.
        s.getRoot().post(() -> {
            View p = patterns.get(chosen.pattern).getRoot();
            s.patternScroll.smoothScrollTo(Math.max(0, p.getLeft() - p.getWidth()), 0);
            View c = colours.get(chosen.colour.ordinal());
            s.colourScroll.smoothScrollTo(Math.max(0, c.getLeft() - c.getWidth() * 2), 0);
        });
    }

    private void pick(@NonNull Banner b) {
        if (saving || b.equals(chosen)) return;
        Haptics.tick(ctx);
        chosen = b;
        render(true);
    }

    private void render(boolean pop) {
        s.preview.setBanner(chosen);
        String[] names = ctx.getResources().getStringArray(R.array.banner_patterns);
        String name = chosen.pattern < names.length ? names[chosen.pattern] : "";
        s.previewLabel.setText(chosen.automatic
                ? ctx.getString(R.string.banner_auto_on)
                : ctx.getString(R.string.banner_label, name, chosen.colour.displayName));
        int amber = ContextCompat.getColor(ctx, R.color.amber), plain = ContextCompat.getColor(ctx, R.color.text_secondary);
        for (int p = 0; p < patterns.size(); p++) {
            ItemBannerPatternBinding t = patterns.get(p);
            boolean on = p == chosen.pattern;
            t.thumb.setBanner(Banner.of(p, chosen.colour));   // every pattern in the current colour
            t.frame.setSelected(on);
            t.name.setTextColor(on ? amber : plain);
        }
        for (AuraAvatarView dot : colours) dot.setAuraSelected(dot.getAura() == chosen.colour);
        s.auto.setEnabled(!chosen.automatic);
        s.save.setEnabled(!saving && !chosen.spec().equals(original));
        if (pop) {
            s.preview.setAlpha(0.6f);
            s.preview.animate().alpha(1f).setDuration(220).start();
        }
    }

    private void save(@Nullable Saved saved) {
        if (saving) return;
        String spec = chosen.spec();
        saving = true;
        s.save.setText(R.string.edit_saving);
        s.save.setEnabled(false);
        UserRepository users = new UserRepository();
        users.updateBanner(spec, new ApiCallback<JsonObject>() {
            @Override public void onSuccess(JsonObject data) {
                me.banner = spec;                            // the cached copy shows it at once
                users.me(new ApiCallback<Me>() {
                    @Override public void onSuccess(Me m) { done(); }
                    @Override public void onError(ApiException e) { done(); }
                });
            }

            private void done() {
                Haptics.click(ctx);
                Toast.makeText(ctx, R.string.banner_saved, Toast.LENGTH_SHORT).show();
                d.dismiss();
                if (saved != null) saved.onSaved(spec);
            }

            @Override public void onError(ApiException e) {
                saving = false;
                Haptics.reject(ctx);
                s.save.setText(R.string.banner_save);
                render(false);
                Toast.makeText(ctx, e.userMessage(ctx), Toast.LENGTH_LONG).show();
            }
        });
    }
}
