package in.mohammad.ramiz.confess.ui.settings;

import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import in.mohammad.ramiz.confess.databinding.ViewSettingRowBinding;

/** Filling in a settings row (view_setting_row): icon · title / subtitle · value · switch or chevron. */
final class SettingRows {

    private SettingRows() { }

    static void setup(@NonNull ViewSettingRowBinding r, @DrawableRes int icon, @StringRes int title) {
        r.icon.setImageResource(icon);
        r.title.setText(title);
    }

    static void toggle(@NonNull ViewSettingRowBinding r) {
        r.toggle.setVisibility(View.VISIBLE);
    }

    static void chevron(@NonNull ViewSettingRowBinding r) {
        r.chevron.setVisibility(View.VISIBLE);
    }

    static void subtitle(@NonNull ViewSettingRowBinding r, @StringRes int text) {
        r.subtitle.setVisibility(View.VISIBLE);
        r.subtitle.setText(text);
    }

    static void value(@NonNull ViewSettingRowBinding r, @NonNull CharSequence text) {
        r.value.setVisibility(View.VISIBLE);
        r.value.setText(text);
    }

    static void enabled(@NonNull ViewSettingRowBinding r, boolean on) {
        r.row.setEnabled(on);
        r.row.setAlpha(on ? 1f : 0.4f);
    }

    static void danger(@NonNull ViewSettingRowBinding r, int color) {
        r.title.setTextColor(color);
        r.icon.setImageTintList(android.content.res.ColorStateList.valueOf(color));
    }
}
