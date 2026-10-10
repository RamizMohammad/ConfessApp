package in.mohammad.ramiz.confess.ui.util;

import android.graphics.Color;
import android.view.View;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Edge-to-edge helpers. targetSdk 36 always draws behind the system bars,
 * so every screen calls {@link #edgeToEdge} and pads its root with
 * {@link #padSystemBars} (or pads only the views that need it).
 */
public final class EdgeInsets {

    private EdgeInsets() { }

    /** Dark, transparent status + navigation bars with light icons. */
    public static void edgeToEdge(ComponentActivity activity) {
        EdgeToEdge.enable(activity,
                SystemBarStyle.dark(Color.TRANSPARENT),
                SystemBarStyle.dark(Color.TRANSPARENT));
    }

    /** Adds status/navigation bar (and optionally keyboard) insets on top of the view's own padding. */
    public static void padSystemBars(View view, boolean includeIme) {
        final int l = view.getPaddingLeft(), t = view.getPaddingTop(),
                r = view.getPaddingRight(), b = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            int types = WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout();
            if (includeIme) types |= WindowInsetsCompat.Type.ime();
            androidx.core.graphics.Insets i = insets.getInsets(types);
            v.setPadding(l + i.left, t + i.top, r + i.right, b + i.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(view);
    }

    /** Only the top inset (e.g. a toolbar container). */
    public static void padTop(View view) {
        final int t = view.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            androidx.core.graphics.Insets i = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), t + i.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
        ViewCompat.requestApplyInsets(view);
    }

    /** Only the bottom inset (+ keyboard): bottom bars, sticky action bars, input bars. */
    public static void padBottom(View view, boolean includeIme) {
        final int b = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            int types = WindowInsetsCompat.Type.navigationBars();
            if (includeIme) types |= WindowInsetsCompat.Type.ime();
            androidx.core.graphics.Insets i = insets.getInsets(types);
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), b + i.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(view);
    }
}
