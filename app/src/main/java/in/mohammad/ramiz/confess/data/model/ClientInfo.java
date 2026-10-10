package in.mohammad.ramiz.confess.data.model;

import android.os.Build;

import androidx.annotation.Nullable;

import in.mohammad.ramiz.confess.BuildConfig;

/**
 * Sent with login / register: asks for a refreshable, signed session and
 * carries the Play Integrity verdict when there is one.
 */
public class ClientInfo {
    public final String platform = "android";
    public final String appVersion = BuildConfig.VERSION_NAME;
    public final String device;
    @Nullable public final String integrityToken;
    @Nullable public final String integrityNonce;

    public ClientInfo(@Nullable String integrityToken, @Nullable String integrityNonce) {
        String d = (Build.MANUFACTURER + " " + Build.MODEL).trim();
        this.device = d.length() > 80 ? d.substring(0, 80) : d;
        this.integrityToken = integrityToken;
        this.integrityNonce = integrityNonce;
    }
}
