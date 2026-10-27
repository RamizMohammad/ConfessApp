package in.mohammad.ramiz.confess.ui.me;

import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import in.mohammad.ramiz.confess.R;

/**
 * How someone's confession graph looks: a palette and a cell shape. Saved on the server as
 * "g1:<palette>.<shape>" ("" = default: amber, round) and shown to everyone who sees the graph.
 * Keep the names in sync with the server's streak_service.PALETTES / SHAPES.
 */
public final class GraphStyle {

    public enum Palette {
        AMBER("amber", R.string.graph_palette_amber, 0x59E8A04C, 0x8CE8A04C, 0xC7E8A04C, 0xFFF6C76A),
        EMBER("ember", R.string.graph_palette_ember, 0xFF4D2416, 0xFF8A3A1E, 0xFFD9733A, 0xFFFFB067),
        OCEAN("ocean", R.string.graph_palette_ocean, 0xFF1C3554, 0xFF2A5C8C, 0xFF3E8EC2, 0xFF86CDF5),
        MINT("mint", R.string.graph_palette_mint, 0xFF1C4436, 0xFF2B7458, 0xFF43B285, 0xFF95E6C3),
        ROSE("rose", R.string.graph_palette_rose, 0xFF45192E, 0xFF7C2B51, 0xFFC4497F, 0xFFF495BB),
        VIOLET("violet", R.string.graph_palette_violet, 0xFF2B2150, 0xFF473388, 0xFF7457C9, 0xFFB7A2F5),
        MONO("mono", R.string.graph_palette_mono, 0xFF34333A, 0xFF5F5D67, 0xFF9E9BA6, 0xFFECE8E1);

        public final String key;
        @StringRes public final int name;
        /** Levels 1–4 (level 0, an empty day, is the same faint square for every palette). */
        public final int[] levels;

        Palette(String key, int name, int... levels) {
            this.key = key;
            this.name = name;
            this.levels = levels;
        }
    }

    public enum Shape {
        ROUND("round", R.string.graph_shape_round),
        SQUARE("square", R.string.graph_shape_square),
        CIRCLE("circle", R.string.graph_shape_circle),
        HEART("heart", R.string.graph_shape_heart),
        DIAMOND("diamond", R.string.graph_shape_diamond);

        public final String key;
        @StringRes public final int name;

        Shape(String key, int name) {
            this.key = key;
            this.name = name;
        }
    }

    public static final int EMPTY = 0x14ECE8E1;
    public static final GraphStyle DEFAULT = new GraphStyle(Palette.AMBER, Shape.ROUND);

    @NonNull public final Palette palette;
    @NonNull public final Shape shape;

    public GraphStyle(@NonNull Palette palette, @NonNull Shape shape) {
        this.palette = palette;
        this.shape = shape;
    }

    @NonNull
    public static GraphStyle parse(@Nullable String spec) {
        if (spec == null || !spec.startsWith("g1:") || spec.indexOf('.') < 0) return DEFAULT;
        String[] parts = spec.substring(3).split("\\.");
        Palette p = Palette.AMBER;
        Shape s = Shape.ROUND;
        for (Palette x : Palette.values()) if (x.key.equals(parts[0])) p = x;
        if (parts.length > 1) for (Shape x : Shape.values()) if (x.key.equals(parts[1])) s = x;
        return new GraphStyle(p, s);
    }

    /** "" for the default look, so the server keeps it as "automatic". */
    @NonNull
    public String spec() {
        return equals(DEFAULT) ? "" : "g1:" + palette.key + "." + shape.key;
    }

    /** Colour for a level 0–4. */
    public int color(int level) {
        return level <= 0 ? EMPTY : palette.levels[Math.min(4, level) - 1];
    }

    @NonNull public GraphStyle with(@NonNull Palette p) { return new GraphStyle(p, shape); }
    @NonNull public GraphStyle with(@NonNull Shape s) { return new GraphStyle(palette, s); }

    /** Paint a Less→More legend: the first five children of {@code legend} become level 0–4 swatches. */
    public void paintLegend(@NonNull ViewGroup legend) {
        float dp = legend.getResources().getDisplayMetrics().density;
        int n = 0;
        for (int i = 0; i < legend.getChildCount() && n < 5; i++) {
            View v = legend.getChildAt(i);
            if (!"swatch".equals(v.getTag())) continue;
            GradientDrawable g = new GradientDrawable();
            g.setColor(color(n));
            if (shape == Shape.CIRCLE || shape == Shape.HEART) g.setShape(GradientDrawable.OVAL);
            else g.setCornerRadius(shape == Shape.SQUARE ? dp : 3 * dp);
            v.setBackground(g);
            n++;
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof GraphStyle && ((GraphStyle) o).palette == palette && ((GraphStyle) o).shape == shape;
    }

    @Override
    public int hashCode() {
        return palette.hashCode() * 31 + shape.hashCode();
    }
}
