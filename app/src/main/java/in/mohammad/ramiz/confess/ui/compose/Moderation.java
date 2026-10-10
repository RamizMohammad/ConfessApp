package in.mohammad.ramiz.confess.ui.compose;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.util.Locale;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.data.api.ApiException;

/**
 * Turns a 451 CONTENT_FLAGGED reply into a specific, human reason.
 * The server's message ends with the moderation label in brackets, e.g.
 * "This can't be posted — it breaks our guidelines (personal info)".
 * Used by the composer and (Section 7) by responses.
 */
public final class Moderation {

    private Moderation() { }

    @StringRes
    public static int reason(@NonNull ApiException e) {
        String m = e.serverMessage.toLowerCase(Locale.ROOT);
        if (m.contains("personal info")) return R.string.mod_personal_info;
        if (m.contains("religious hate")) return R.string.mod_religious_hate;
        if (m.contains("sexual threat")) return R.string.mod_sexual_threat;
        if (m.contains("national offense")) return R.string.mod_national_offense;
        if (m.contains("child safety")) return R.string.mod_child_safety;
        if (m.contains("violent threat")) return R.string.mod_violent_threat;
        if (m.contains("self harm")) return R.string.mod_self_harm;
        return R.string.mod_generic;
    }

    /** "Your confession is empty after removing formatting" — not a guideline problem. */
    public static boolean isEmptyAfterCleaning(@NonNull ApiException e) {
        return e.serverMessage.toLowerCase(Locale.ROOT).contains("empty");
    }
}
