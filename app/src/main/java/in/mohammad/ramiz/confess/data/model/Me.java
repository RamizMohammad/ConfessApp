package in.mohammad.ramiz.confess.data.model;

/** The signed-in user's own profile + settings (GET users/me, login, register). */
public class Me {
    public String id;
    public String alias;
    public String about;
    public String avatar;
    public boolean hasPassword;
    public boolean biometricEnabled;
    /** A password reset switched biometrics off; offer to turn it back on. */
    public boolean biometricResetPending;
    public boolean pushEnabled;
    public boolean isPro;
    public String createdAt;
    public int postCount;
    public int likesReceived;
    public int likesGiven;
    public int commentsReceived;
    public int commentsMade;
    public int followers;
    public int following;
    /** Per-type push switches (null on an old cached copy → treat as all on). */
    @androidx.annotation.Nullable public NotificationPrefs notifications;
}
