package in.mohammad.ramiz.confess.data.api;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import in.mohammad.ramiz.confess.R;
import okhttp3.ResponseBody;

/**
 * Every failure the UI can see: an error envelope from the server, no network,
 * or something unexpected. Screens switch on {@link #code} (see {@link ErrorCode}).
 */
public class ApiException extends Exception {

    /** HTTP status, or 0 for network / client-side failures. */
    public final int httpStatus;
    /** Server error code (ErrorCode.*), or OFFLINE / UNKNOWN. */
    @NonNull public final String code;
    /** Server message — fine for logs; use {@link #userMessage} for UI. */
    @NonNull public final String serverMessage;
    /** First field-level message for 422 VALIDATION_ERROR, if any. */
    @Nullable public final String fieldMessage;

    public ApiException(int httpStatus, @NonNull String code, @NonNull String serverMessage, @Nullable String fieldMessage) {
        super(code + " (" + httpStatus + "): " + serverMessage);
        this.httpStatus = httpStatus;
        this.code = code;
        this.serverMessage = serverMessage;
        this.fieldMessage = fieldMessage;
    }

    public static ApiException offline(Throwable cause) {
        ApiException e = new ApiException(0, ErrorCode.OFFLINE, "Network unavailable", null);
        e.initCause(cause);
        return e;
    }

    public static ApiException insecure(Throwable cause) {
        ApiException e = new ApiException(0, ErrorCode.INSECURE_CONNECTION, "Connection could not be verified", null);
        e.initCause(cause);
        return e;
    }

    public static ApiException unknown(Throwable cause) {
        ApiException e = new ApiException(0, ErrorCode.UNKNOWN, String.valueOf(cause), null);
        e.initCause(cause);
        return e;
    }

    /** Parse a non-2xx reply. Never returns null. */
    public static ApiException fromErrorBody(int httpStatus, @Nullable ResponseBody body) {
        String code = httpStatus == 429 ? ErrorCode.RATE_LIMITED : ErrorCode.UNKNOWN;
        String message = "";
        String field = null;
        try {
            if (body != null) {
                JsonElement root = JsonParser.parseString(body.string());
                if (root.isJsonObject() && root.getAsJsonObject().has("error")
                        && root.getAsJsonObject().get("error").isJsonObject()) {
                    JsonObject err = root.getAsJsonObject().getAsJsonObject("error");
                    if (err.has("code")) code = err.get("code").getAsString();
                    if (err.has("message")) message = err.get("message").getAsString();
                    if (err.has("fields") && err.get("fields").isJsonArray()
                            && err.getAsJsonArray("fields").size() > 0) {
                        JsonObject first = err.getAsJsonArray("fields").get(0).getAsJsonObject();
                        if (first.has("msg")) field = first.get("msg").getAsString().replace("Value error, ", "");
                    }
                }
            }
        } catch (Exception ignored) {
            // keep defaults
        }
        return new ApiException(httpStatus, code, message, field);
    }

    public boolean is(String errorCode) {
        return code.equals(errorCode);
    }

    public boolean isOffline() {
        return ErrorCode.OFFLINE.equals(code);
    }

    /** Friendly, on-brand text for snackbars / inline errors. */
    @NonNull
    public String userMessage(@NonNull Context ctx) {
        switch (code) {
            case ErrorCode.OFFLINE:                return ctx.getString(R.string.error_offline);
            case ErrorCode.RATE_LIMITED:           return ctx.getString(R.string.error_rate_limited);
            case ErrorCode.CONTENT_FLAGGED:        return ctx.getString(R.string.error_content_flagged);
            case ErrorCode.MODERATION_UNAVAILABLE: return ctx.getString(R.string.error_moderation_unavailable);
            case ErrorCode.ALIAS_TAKEN:            return ctx.getString(R.string.error_alias_taken);
            case ErrorCode.ALIAS_NOT_ALLOWED:      return ctx.getString(R.string.register_alias_not_allowed);
            case ErrorCode.ACCOUNT_EXISTS:         return ctx.getString(R.string.error_account_exists);
            case ErrorCode.WRONG_PASSWORD:         return ctx.getString(R.string.error_wrong_password);
            case ErrorCode.COMMENTS_DISABLED:      return ctx.getString(R.string.error_comments_disabled);
            case ErrorCode.NOT_FOUND:              return ctx.getString(R.string.error_not_found);
            case ErrorCode.UNAUTHORIZED:
            case ErrorCode.SESSION_REVOKED:
            case ErrorCode.REFRESH_INVALID:
            case ErrorCode.INVALID_CREDENTIALS:    return ctx.getString(R.string.error_session);
            case ErrorCode.INSECURE_CONNECTION:    return ctx.getString(R.string.error_insecure_connection);
            case ErrorCode.INTEGRITY_FAILED:       return ctx.getString(R.string.error_integrity_failed);
            case ErrorCode.BLOCKED:                return ctx.getString(R.string.error_blocked);
            case ErrorCode.ACCOUNT_LOCKED:
                return serverMessage.isEmpty() ? ctx.getString(R.string.error_rate_limited) : serverMessage;
            case ErrorCode.VALIDATION_ERROR:
                return fieldMessage != null ? capitalize(fieldMessage) : ctx.getString(R.string.error_generic);
            default:
                return ctx.getString(R.string.error_generic);
        }
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1) + (s.endsWith(".") ? "" : ".");
    }
}
