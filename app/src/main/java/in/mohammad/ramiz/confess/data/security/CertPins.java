package in.mohammad.ramiz.confess.data.security;

import androidx.annotation.Nullable;

import in.mohammad.ramiz.confess.BuildConfig;
import okhttp3.CertificatePinner;
import okhttp3.HttpUrl;

/**
 * Certificate pinning for the API hosts (SERVER_URL, BACKUP_URL).
 *
 * Pins come from secrets.properties: CERT_PINS=sha256/AAAA…=,sha256/BBBB…=
 * (SPKI hashes). Always include a backup pin — the CA's intermediate or root, or
 * the key of a spare certificate — so a certificate renewal can't lock every
 * installed app out. With no pins configured, pinning is off (release builds
 * print a warning at build time).
 *
 * DEBUG_ALLOW_PROXY=true turns pinning off in debug builds only, so you can
 * inspect your own traffic with a proxy while developing.
 */
public final class CertPins {

    private CertPins() { }

    @Nullable
    public static CertificatePinner pinner() {
        if (BuildConfig.DEBUG && BuildConfig.DEBUG_ALLOW_PROXY) return null;
        String raw = BuildConfig.CERT_PINS == null ? "" : BuildConfig.CERT_PINS.trim();
        if (raw.isEmpty()) return null;
        CertificatePinner.Builder b = new CertificatePinner.Builder();
        boolean any = false;
        for (String base : new String[]{BuildConfig.SERVER_URL, BuildConfig.BACKUP_URL}) {
            HttpUrl url = base == null || base.trim().isEmpty() ? null : HttpUrl.parse(base.trim());
            if (url == null) continue;
            for (String pin : raw.split(",")) {
                String p = pin.trim();
                if (p.startsWith("sha256/")) {
                    b.add(url.host(), p);
                    any = true;
                }
            }
        }
        return any ? b.build() : null;
    }
}
