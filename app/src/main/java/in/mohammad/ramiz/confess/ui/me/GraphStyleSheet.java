package in.mohammad.ramiz.confess.ui.me;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.gson.JsonObject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.model.ConfessionGraph;
import in.mohammad.ramiz.confess.data.repo.UserRepository;
import in.mohammad.ramiz.confess.databinding.ItemGraphOptionBinding;
import in.mohammad.ramiz.confess.databinding.SheetGraphStyleBinding;
import in.mohammad.ramiz.confess.core.Haptics;

/**
 * Customise my confession graph, like the banner sheet: a live preview (my real graph), the
 * colours and shapes as little swatches, and "Show on my profile". Saves with POST users/me/graph.
 */
public final class GraphStyleSheet {

    public interface Saved {
        void onSaved(@NonNull GraphStyle style, boolean isPublic);
    }

    private final Context ctx;
    private final SheetGraphStyleBinding s;
    private final BottomSheetDialog d;
    private final List<ItemGraphOptionBinding> palettes = new ArrayList<>();
    private final List<ItemGraphOptionBinding> shapes = new ArrayList<>();
    private final GraphStyle original;
    private final boolean originalPublic;
    @NonNull private GraphStyle chosen;
    private boolean saving;

    private GraphStyleSheet(@NonNull Context ctx, @NonNull GraphStyle style, boolean isPublic) {
        this.ctx = ctx;
        original = style;
        originalPublic = isPublic;
        chosen = style;
        d = new BottomSheetDialog(ctx);
        s = SheetGraphStyleBinding.inflate(LayoutInflater.from(ctx));
    }

    /** {@code graph} = my graph as last loaded (for the preview); may be null before it arrives. */
    public static void show(@NonNull Context ctx, @Nullable ConfessionGraph graph, @NonNull GraphStyle style,
                            boolean isPublic, @Nullable Saved saved) {
        new GraphStyleSheet(ctx, style, isPublic).open(graph, saved);
    }

    private void open(@Nullable ConfessionGraph graph, @Nullable Saved saved) {
        if (graph != null && graph.start != null && graph.days != null) {
            try {
                s.preview.setData(LocalDate.parse(graph.start), graph.days);
            } catch (Exception ignored) { }
        }
        LayoutInflater inf = LayoutInflater.from(ctx);
        for (GraphStyle.Palette p : GraphStyle.Palette.values()) {
            ItemGraphOptionBinding t = ItemGraphOptionBinding.inflate(inf, s.palettes, false);
            t.name.setText(p.name);
            t.getRoot().setContentDescription(ctx.getString(p.name));
            t.getRoot().setOnClickListener(v -> pick(chosen.with(p)));
            palettes.add(t);
            s.palettes.addView(t.getRoot());
        }
        for (GraphStyle.Shape sh : GraphStyle.Shape.values()) {
            ItemGraphOptionBinding t = ItemGraphOptionBinding.inflate(inf, s.shapes, false);
            t.name.setText(sh.name);
            t.getRoot().setContentDescription(ctx.getString(sh.name));
            t.getRoot().setOnClickListener(v -> pick(chosen.with(sh)));
            shapes.add(t);
            s.shapes.addView(t.getRoot());
        }
        s.publicSwitch.setChecked(originalPublic);
        s.publicSwitch.setOnCheckedChangeListener((b, on) -> render());
        s.publicRow.setOnClickListener(v -> s.publicSwitch.toggle());
        s.cancel.setOnClickListener(v -> d.dismiss());
        s.save.setOnClickListener(v -> save(saved));
        render();

        d.setContentView(s.getRoot());
        d.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        d.getBehavior().setSkipCollapsed(true);
        d.show();
        s.getRoot().post(() -> {
            View p = palettes.get(chosen.palette.ordinal()).getRoot();
            s.paletteScroll.smoothScrollTo(Math.max(0, p.getLeft() - p.getWidth()), 0);
        });
    }

    private void pick(@NonNull GraphStyle style) {
        if (saving || style.equals(chosen)) return;
        Haptics.tick(ctx);
        chosen = style;
        render();
    }

    private void render() {
        s.preview.setGraphStyle(chosen);
        chosen.paintLegend(s.legend);
        int amber = ContextCompat.getColor(ctx, R.color.amber), plain = ContextCompat.getColor(ctx, R.color.text_secondary);
        for (GraphStyle.Palette p : GraphStyle.Palette.values()) {
            ItemGraphOptionBinding t = palettes.get(p.ordinal());
            boolean on = p == chosen.palette;
            t.swatch.setGraphStyle(new GraphStyle(p, chosen.shape));      // every colour in the current shape
            t.frame.setSelected(on);
            t.name.setTextColor(on ? amber : plain);
        }
        for (GraphStyle.Shape sh : GraphStyle.Shape.values()) {
            ItemGraphOptionBinding t = shapes.get(sh.ordinal());
            boolean on = sh == chosen.shape;
            t.swatch.setGraphStyle(new GraphStyle(chosen.palette, sh));   // every shape in the current colour
            t.frame.setSelected(on);
            t.name.setTextColor(on ? amber : plain);
        }
        boolean changed = !chosen.equals(original) || s.publicSwitch.isChecked() != originalPublic;
        s.save.setEnabled(!saving && changed);
    }

    private void save(@Nullable Saved saved) {
        if (saving) return;
        saving = true;
        boolean isPublic = s.publicSwitch.isChecked();
        s.save.setText(R.string.edit_saving);
        s.save.setEnabled(false);
        new UserRepository().updateGraph(chosen.spec(), isPublic, new ApiCallback<JsonObject>() {
            @Override public void onSuccess(JsonObject data) {
                Haptics.click(ctx);
                Toast.makeText(ctx, R.string.graph_style_saved, Toast.LENGTH_SHORT).show();
                d.dismiss();
                if (saved != null) saved.onSaved(chosen, isPublic);
            }

            @Override public void onError(ApiException e) {
                saving = false;
                Haptics.reject(ctx);
                s.save.setText(R.string.graph_style_save);
                render();
                Toast.makeText(ctx, e.userMessage(ctx), Toast.LENGTH_LONG).show();
            }
        });
    }
}
