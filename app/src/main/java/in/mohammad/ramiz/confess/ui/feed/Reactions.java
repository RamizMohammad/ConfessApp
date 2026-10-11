package in.mohammad.ramiz.confess.ui.feed;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.data.model.Post;

/**
 * The five ways to feel a confession (server keys in brackets):
 * ❤️ felt · 🫂 hug · 🤝 same ("me too") · 💪 strength · 🥺 tears.
 * Tap = felt; long-press the heart = pick one.
 */
public final class Reactions {

    public static final String FELT = "felt";
    public static final String[] KEYS = {"felt", "hug", "same", "strength", "tears"};
    private static final String[] EMOJI = {"❤️", "🫂", "🤝", "💪", "🥺"};
    @StringRes private static final int[] LABELS = {
            R.string.reaction_felt, R.string.reaction_hug, R.string.reaction_same,
            R.string.reaction_strength, R.string.reaction_tears};

    private Reactions() { }

    private static int index(@Nullable String key) {
        if (key == null) return -1;
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(key)) return i;
        return -1;
    }

    @NonNull
    public static String emoji(@Nullable String key) {
        int i = index(key);
        return i < 0 ? EMOJI[0] : EMOJI[i];
    }

    @StringRes
    public static int label(@Nullable String key) {
        int i = index(key);
        return i < 0 ? LABELS[0] : LABELS[i];
    }

    /** My reaction as the card shows it (older servers only say likedByMe). */
    @Nullable
    public static String mine(@NonNull Post p) {
        if (p.myReaction != null && index(p.myReaction) >= 0) return p.myReaction;
        return p.likedByMe ? FELT : null;
    }

    /** Most-used reactions first (ties keep the picker's order). */
    @NonNull
    public static List<String> top(@NonNull Post p) {
        List<String> out = new ArrayList<>();
        if (p.reactions == null) return out;
        for (String k : KEYS) {
            Integer n = p.reactions.get(k);
            if (n != null && n > 0) out.add(k);
        }
        out.sort((a, b) -> Integer.compare(p.reactions.get(b), p.reactions.get(a)));
        return out;
    }

    /**
     * Emoji shown before the count. Hearts are the button icon, so ❤️ isn't repeated;
     * my own (non-heart) reaction comes first.
     */
    @NonNull
    public static String summary(@NonNull Post p, int max) {
        StringBuilder sb = new StringBuilder();
        String mine = mine(p);
        int shown = 0;
        if (mine != null && !FELT.equals(mine)) {
            sb.append(emoji(mine));
            shown++;
        }
        for (String k : top(p)) {
            if (shown >= max) break;
            if (FELT.equals(k) || k.equals(mine)) continue;
            sb.append(emoji(k));
            shown++;
        }
        return sb.toString();
    }

    /** Apply "I now feel X (or nothing)" to a copy, keeping count + breakdown consistent. */
    public static void apply(@NonNull Post p, @Nullable String want) {
        String had = mine(p);
        if (p.reactions == null) {
            p.reactions = new HashMap<>();
            if (p.likeCount > 0) p.reactions.put(FELT, p.likeCount);   // older payload: all hearts
        }
        Map<String, Integer> r = p.reactions;
        if (had != null) {
            int n = (r.containsKey(had) ? r.get(had) : 0) - 1;
            if (n > 0) r.put(had, n); else r.remove(had);
        }
        if (want != null) r.put(want, (r.containsKey(want) ? r.get(want) : 0) + 1);
        if (had == null && want != null) p.likeCount++;
        if (had != null && want == null) p.likeCount = Math.max(0, p.likeCount - 1);
        p.myReaction = want;
        p.likedByMe = want != null;
    }
}
