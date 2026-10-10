package in.mohammad.ramiz.confess.ui.onboarding;

import java.security.SecureRandom;
import java.util.LinkedHashSet;
import java.util.Set;

/** Night-themed alias ideas (≤ 15 chars, letters + underscore) for the Shuffle chips. */
final class AliasSuggestions {

    private static final String[] FIRST = {
            "quiet", "silent", "midnight", "velvet", "hollow", "amber", "paper", "lunar", "misty",
            "faded", "gentle", "ember", "dusk", "still", "folded", "drifting", "candle", "night", "soft", "wistful",
    };
    private static final String[] SECOND = {
            "owl", "echo", "ash", "star", "tide", "moth", "letter", "ink", "wander", "lamp", "fern",
            "rain", "frost", "willow", "harbor", "sparrow", "ember", "quill", "lantern", "moon",
    };

    private static final SecureRandom RANDOM = new SecureRandom();

    private AliasSuggestions() { }

    static String[] next(int count) {
        Set<String> out = new LinkedHashSet<>();
        int guard = 0;
        while (out.size() < count && guard++ < 200) {
            String a = FIRST[RANDOM.nextInt(FIRST.length)];
            String b = SECOND[RANDOM.nextInt(SECOND.length)];
            if (a.equals(b)) continue;
            String alias = a + "_" + b;
            if (alias.length() > 15) continue;
            if (alias.length() <= 12 && RANDOM.nextInt(3) == 0) alias += RANDOM.nextInt(90) + 10;
            out.add(alias);
        }
        return out.toArray(new String[0]);
    }
}
