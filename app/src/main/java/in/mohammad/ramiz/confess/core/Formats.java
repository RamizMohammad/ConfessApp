package in.mohammad.ramiz.confess.core;

import androidx.annotation.Nullable;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Display formatting the server used to do (old FormatMaker) — now on device,
 * so it follows the phone's clock and time zone.
 */
public final class Formats {

    private Formats() { }

    /** 950 → "950", 1402 → "1.4k", 12_500 → "12.5k", 2_300_000 → "2.3M". */
    public static String count(long n) {
        if (n >= 1_000_000) return trim(n / 1_000_000d) + "M";
        if (n >= 1_000) return trim(n / 1_000d) + "k";
        return String.valueOf(n);
    }

    private static String trim(double v) {
        String s = String.format(Locale.US, "%.1f", Math.floor(v * 10) / 10);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    /** ISO-8601 from the server → "Just now", "5m ago", "3h ago", "Yesterday", "Tuesday", "2w ago", "3mo ago", "1y ago". */
    public static String relative(@Nullable String iso) {
        OffsetDateTime t = parse(iso);
        if (t == null) return "";
        OffsetDateTime now = OffsetDateTime.now(t.getOffset());
        long seconds = Math.max(0, Duration.between(t, now).getSeconds());
        long minutes = seconds / 60, hours = minutes / 60, days = hours / 24;
        if (seconds < 60) return "Just now";
        if (minutes < 60) return minutes + "m ago";
        if (hours < 24) return hours + "h ago";
        if (days == 1) return "Yesterday";
        if (days < 7) return t.atZoneSameInstant(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()));
        if (days < 30) return (days / 7) + "w ago";
        if (days < 365) return (days / 30) + "mo ago";
        return (days / 365) + "y ago";
    }

    /** "Tonight, 02:48 AM"-style clock for the composer / detail header: "Oct 9 · 02:48". */
    public static String dayTime(@Nullable String iso) {
        OffsetDateTime t = parse(iso);
        if (t == null) return "";
        return t.atZoneSameInstant(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("MMM d · HH:mm", Locale.getDefault()));
    }

    /** "Member since Oct 2026". */
    public static String monthYear(@Nullable String iso) {
        OffsetDateTime t = parse(iso);
        if (t == null) return "";
        return t.atZoneSameInstant(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault()));
    }

    @Nullable
    public static OffsetDateTime parse(@Nullable String iso) {
        if (iso == null || iso.isEmpty()) return null;
        try {
            return OffsetDateTime.parse(iso);
        } catch (Exception e) {
            return null;
        }
    }
}
