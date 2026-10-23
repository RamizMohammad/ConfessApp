package in.mohammad.ramiz.confess.ui.books.reader;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.databinding.SheetReaderSettingsBinding;

/**
 * The reading settings sheet (text size, spacing, font, page turn, theme, paper / text tone,
 * brightness, ruled lines, justify, volume keys). Opened from the reader's "Aa" button and from
 * Settings → Reading; both change the same {@link ReaderPrefs}. Each change is saved here; the
 * {@link Listener} lets the reader re-typeset / recolour live (Settings doesn't need to).
 */
public final class ReaderSettingsSheet {

    public interface Listener {
        /** Size, spacing, font, theme, lines or justify changed (already saved). */
        default void onLayout() { }

        /** Paper or text tone changed. */
        default void onColours() { }

        /** Page turn style changed (already saved). */
        default void onTurn(int turn) { }

        /** Reading brightness changed (already saved). */
        default void onBrightness() { }
    }

    private ReaderSettingsSheet() { }

    @NonNull
    public static BottomSheetDialog show(@NonNull Activity a, @NonNull ReaderPrefs prefs, @NonNull Listener l) {
        BottomSheetDialog d = new BottomSheetDialog(a);
        SheetReaderSettingsBinding s = SheetReaderSettingsBinding.inflate(LayoutInflater.from(a));
        s.preview.update(prefs);
        Runnable changed = () -> {
            Haptics.tick(a);
            prefs.save(a);
            s.preview.update(prefs);                      // the sample page follows every change
            l.onLayout();
        };
        Runnable render = () -> {
            s.sizeValue.setText(String.valueOf(prefs.sizeSp));
            s.smaller.setEnabled(prefs.sizeSp > ReaderPrefs.MIN_SIZE);
            s.bigger.setEnabled(prefs.sizeSp < ReaderPrefs.MAX_SIZE);
            s.spacing.check(prefs.spacing == 0 ? R.id.spacingCompact : prefs.spacing == 2 ? R.id.spacingAiry : R.id.spacingNormal);
            themeTile(a, s.themePaper, R.color.reader_paper_bg, prefs.theme == ReaderPrefs.THEME_PAPER);
            themeTile(a, s.themeSepia, R.color.reader_sepia_bg, prefs.theme == ReaderPrefs.THEME_SEPIA);
            themeTile(a, s.themeNight, R.color.reader_night_bg, prefs.theme == ReaderPrefs.THEME_NIGHT);
            s.lines.setChecked(prefs.lines);
            s.turnStyle.check(prefs.turn == ReaderPrefs.TURN_SLIDE ? R.id.turnSlide
                    : prefs.turn == ReaderPrefs.TURN_INSTANT ? R.id.turnInstant : R.id.turnCurl);
            s.brightnessSystem.setChecked(prefs.brightness < 0);
            s.brightness.setEnabled(prefs.brightness >= 0);
            s.brightness.setValue(Math.round((prefs.brightness < 0 ? systemBrightness(a) : prefs.brightness) / 5f) * 5f);
            boolean lightTheme = prefs.theme != ReaderPrefs.THEME_NIGHT;
            int textTone = in.mohammad.ramiz.confess.ui.components.PaperTone.text(a);
            s.tone.setEnabled(lightTheme);
            s.tone.setAlpha(lightTheme ? 1f : 0.4f);
            s.textAuto.setEnabled(lightTheme);
            s.textAuto.setChecked(textTone == in.mohammad.ramiz.confess.ui.components.PaperTone.AUTO);
            s.textTone.setEnabled(lightTheme && textTone != in.mohammad.ramiz.confess.ui.components.PaperTone.AUTO);
            s.textTone.setAlpha(lightTheme ? 1f : 0.4f);
            if (textTone != in.mohammad.ramiz.confess.ui.components.PaperTone.AUTO) s.textTone.setValue(Math.round(textTone / 5f) * 5f);
            s.justify.setChecked(prefs.justify);
            s.volumeKeys.setChecked(prefs.volumeKeys);
        };
        render.run();
        s.smaller.setOnClickListener(v -> { prefs.sizeSp = Math.max(ReaderPrefs.MIN_SIZE, prefs.sizeSp - 1); changed.run(); render.run(); });
        s.bigger.setOnClickListener(v -> { prefs.sizeSp = Math.min(ReaderPrefs.MAX_SIZE, prefs.sizeSp + 1); changed.run(); render.run(); });
        s.spacing.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked) return;
            int sp = id == R.id.spacingCompact ? 0 : id == R.id.spacingAiry ? 2 : 1;
            if (sp != prefs.spacing) { prefs.spacing = sp; changed.run(); }
        });
        s.font.select(prefs.font, false);
        s.font.setListener(f -> { prefs.font = f; changed.run(); });
        s.themePaper.setOnClickListener(v -> { prefs.theme = ReaderPrefs.THEME_PAPER; changed.run(); render.run(); });
        s.themeSepia.setOnClickListener(v -> { prefs.theme = ReaderPrefs.THEME_SEPIA; changed.run(); render.run(); });
        s.themeNight.setOnClickListener(v -> { prefs.theme = ReaderPrefs.THEME_NIGHT; changed.run(); render.run(); });
        s.tone.setValue(Math.round(in.mohammad.ramiz.confess.ui.components.PaperTone.get(a) / 5f) * 5f);
        s.tone.addOnChangeListener((sl, value, fromUser) -> {
            if (!fromUser) return;
            in.mohammad.ramiz.confess.ui.components.PaperTone.set(a, Math.round(value));
            Haptics.tick(a);
            l.onColours();
            s.preview.update(prefs);                                   // pages change colour as you slide
        });
        s.textTone.addOnChangeListener((sl, value, fromUser) -> {
            if (!fromUser) return;
            in.mohammad.ramiz.confess.ui.components.PaperTone.setText(a, Math.round(value));
            Haptics.tick(a);
            l.onColours();
            s.preview.update(prefs);
        });
        s.textAuto.setOnCheckedChangeListener((v, auto) -> {
            boolean wasAuto = in.mohammad.ramiz.confess.ui.components.PaperTone.text(a)
                    == in.mohammad.ramiz.confess.ui.components.PaperTone.AUTO;
            if (auto == wasAuto) return;                  // render() re-stating the saved value
            if (auto) {
                in.mohammad.ramiz.confess.ui.components.PaperTone.setText(a, in.mohammad.ramiz.confess.ui.components.PaperTone.AUTO);
            } else {
                int now = androidx.core.graphics.ColorUtils.calculateLuminance(in.mohammad.ramiz.confess.ui.components.PaperTone.ink(a)) > 0.5 ? 100 : 0;
                s.textTone.setValue(now);
                in.mohammad.ramiz.confess.ui.components.PaperTone.setText(a, now);
            }
            s.textTone.setEnabled(!auto);
            l.onColours();
            s.preview.update(prefs);
        });
        s.turnStyle.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked) return;
            int t = id == R.id.turnSlide ? ReaderPrefs.TURN_SLIDE : id == R.id.turnInstant ? ReaderPrefs.TURN_INSTANT : ReaderPrefs.TURN_CURL;
            if (t == prefs.turn) return;
            prefs.turn = t;
            prefs.save(a);
            l.onTurn(t);
            s.preview.update(prefs);
            s.preview.turn();                             // show the new turn straight away
        });
        s.brightness.addOnChangeListener((sl, value, fromUser) -> {
            if (!fromUser) return;
            prefs.brightness = Math.round(value);
            Haptics.tick(a);
            l.onBrightness();
            prefs.save(a);
        });
        s.brightnessSystem.setOnCheckedChangeListener((v, system) -> {
            if (system == (prefs.brightness < 0)) return;           // render() re-stating it
            prefs.brightness = system ? -1 : Math.round(s.brightness.getValue());
            s.brightness.setEnabled(!system);
            l.onBrightness();
            prefs.save(a);
        });
        s.lines.setOnCheckedChangeListener((v, on) -> { if (on != prefs.lines) { prefs.lines = on; changed.run(); } });
        s.justify.setOnCheckedChangeListener((v, on) -> { if (on != prefs.justify) { prefs.justify = on; changed.run(); } });
        s.volumeKeys.setOnCheckedChangeListener((v, on) -> { prefs.volumeKeys = on; prefs.save(a); });
        // Opens at about 70% of the screen and stays that size: the sample page stays on top,
        // the settings scroll underneath it.
        int height = Math.round(a.getResources().getDisplayMetrics().heightPixels * 0.7f);
        d.setContentView(s.getRoot(), new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, height));
        d.getBehavior().setPeekHeight(height);
        d.getBehavior().setSkipCollapsed(true);
        d.getBehavior().setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
        d.show();
        return d;
    }

    private static void themeTile(@NonNull Context c, @NonNull TextView tile, int color, boolean selected) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, c.getResources().getDisplayMetrics()));
        g.setColor(c.getColor(color));
        int px = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, selected ? 2 : 1, c.getResources().getDisplayMetrics()));
        g.setStroke(px, c.getColor(selected ? R.color.amber : R.color.hairline));
        tile.setBackground(g);
        tile.setSelected(selected);
    }

    /** The phone's brightness, 0–100 (where the slider starts when "use phone brightness" is turned off). */
    static int systemBrightness(@NonNull Context c) {
        try {
            return Math.round(android.provider.Settings.System.getInt(c.getContentResolver(),
                    android.provider.Settings.System.SCREEN_BRIGHTNESS) * 100f / 255f);
        } catch (Exception e) {
            return 50;
        }
    }
}
