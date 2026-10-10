package in.mohammad.ramiz.confess.ui.util;

import androidx.annotation.Nullable;

/** Password checks shared by onboarding and Settings (server: 8–64 chars). */
public final class PasswordStrength {

    public static final int MIN = 8, MAX = 64;

    private PasswordStrength() { }

    /** 0 = empty, 1 weak, 2 fair, 3 strong. */
    public static int score(@Nullable String pw) {
        if (pw == null || pw.isEmpty()) return 0;
        if (pw.length() < MIN) return 1;
        int score = 1;
        if (pw.length() >= 12) score++;
        if (hasMix(pw)) score++;
        if (hasSymbol(pw)) score++;
        return Math.min(3, score);
    }

    public static boolean longEnough(@Nullable String pw) {
        return pw != null && pw.length() >= MIN;
    }

    /** Letters and numbers, or upper and lower case. */
    public static boolean hasMix(@Nullable String pw) {
        if (pw == null) return false;
        boolean lower = false, upper = false, digit = false;
        for (char c : pw.toCharArray()) {
            if (Character.isLowerCase(c)) lower = true;
            else if (Character.isUpperCase(c)) upper = true;
            else if (Character.isDigit(c)) digit = true;
        }
        return (lower && upper) || (digit && (lower || upper));
    }

    public static boolean hasSymbol(@Nullable String pw) {
        if (pw == null) return false;
        for (char c : pw.toCharArray()) if (!Character.isLetterOrDigit(c) && !Character.isWhitespace(c)) return true;
        return false;
    }
}
