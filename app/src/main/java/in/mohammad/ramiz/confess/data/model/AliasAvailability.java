package in.mohammad.ramiz.confess.data.model;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/** GET users/alias-available — live check on the alias screen. */
public class AliasAvailability {
    public static final String REASON_OFFENSIVE = "offensive";
    public static final String REASON_HATEFUL = "hateful";
    public static final String REASON_IDENTITY = "identity";

    public String alias;
    /** Format ok (3–15 of A–Z a–z 0–9 _). */
    public boolean valid;
    /** Nobody has it yet. */
    public boolean available;
    /** Wording ok. Null from an older server = allowed. */
    @Nullable public Boolean allowed;
    /** offensive · hateful · identity — when not allowed. */
    @Nullable public String reason;
    @Nullable public String message;
    /** Up to 3 free variations, only when taken. */
    public List<String> suggestions = new ArrayList<>();

    public boolean isAllowed() {
        return allowed == null || allowed;
    }
}
