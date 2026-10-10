package in.mohammad.ramiz.confess.data.api;

/** Error codes from the server envelope (API_V2.md) + two client-side ones. */
public final class ErrorCode {
    private ErrorCode() { }

    // Client-side
    public static final String OFFLINE = "OFFLINE";
    public static final String UNKNOWN = "UNKNOWN";
    /** Certificate pin or response signature didn't match — someone is in the middle. */
    public static final String INSECURE_CONNECTION = "INSECURE_CONNECTION";

    // Server
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    public static final String NOT_REGISTERED = "NOT_REGISTERED";
    public static final String ALIAS_TAKEN = "ALIAS_TAKEN";
    public static final String ALIAS_NOT_ALLOWED = "ALIAS_NOT_ALLOWED";
    public static final String ACCOUNT_EXISTS = "ACCOUNT_EXISTS";
    public static final String PASSWORD_NOT_SET = "PASSWORD_NOT_SET";
    public static final String PASSWORD_ALREADY_SET = "PASSWORD_ALREADY_SET";
    public static final String WRONG_PASSWORD = "WRONG_PASSWORD";
    public static final String COMMENTS_DISABLED = "COMMENTS_DISABLED";
    public static final String CONTENT_FLAGGED = "CONTENT_FLAGGED";
    public static final String MODERATION_UNAVAILABLE = "MODERATION_UNAVAILABLE";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String INVALID_CURSOR = "INVALID_CURSOR";
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String TOKEN_USED = "TOKEN_USED";
    public static final String TOKEN_EXPIRED = "TOKEN_EXPIRED";
    public static final String RATE_LIMITED = "RATE_LIMITED";
    public static final String SESSION_REVOKED = "SESSION_REVOKED";
    public static final String REFRESH_INVALID = "REFRESH_INVALID";
    public static final String BAD_SIGNATURE = "BAD_SIGNATURE";
    public static final String INTEGRITY_FAILED = "INTEGRITY_FAILED";
    public static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
    public static final String BLOCKED = "BLOCKED";
}
