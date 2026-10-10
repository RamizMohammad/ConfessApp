package in.mohammad.ramiz.confess.data.security;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;

import androidx.annotation.NonNull;

import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import in.mohammad.ramiz.confess.BuildConfig;

/**
 * Is this APK signed with our certificate? A re-signed (repackaged) copy isn't.
 *
 * Expected SHA-256 fingerprints come from secrets.properties SIGNING_CERT_SHA256
 * (comma-separated, "AA:BB:…" or plain hex). With Play App Signing use the
 * *app signing key* fingerprint from Play Console, plus your upload key if you
 * install release builds directly. Empty = check off.
 *
 * This is a first, cheap line: someone patching the APK can remove it. The
 * check that can't be patched out is the server's — Play Integrity reports the
 * real signing certificate and the server compares it with ANDROID_CERT_SHA256.
 */
public final class AppSignature {

    private AppSignature() { }

    public static boolean isGenuine(@NonNull Context context) {
        Set<String> expected = expected();
        if (expected.isEmpty()) return true;
        try {
            PackageInfo info = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
            SigningInfo si = info.signingInfo;
            if (si == null) return false;
            Signature[] sigs = si.hasMultipleSigners() ? si.getApkContentsSigners() : si.getSigningCertificateHistory();
            if (sigs == null) return false;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (Signature s : sigs) {
                if (expected.contains(hex(md.digest(s.toByteArray())))) return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static Set<String> expected() {
        Set<String> out = new HashSet<>();
        String raw = BuildConfig.SIGNING_CERT_SHA256 == null ? "" : BuildConfig.SIGNING_CERT_SHA256;
        for (String part : raw.split(",")) {
            String h = part.replace(":", "").trim().toUpperCase(Locale.ROOT);
            if (h.length() == 64) out.add(h);
        }
        return out;
    }

    private static String hex(byte[] d) {
        StringBuilder sb = new StringBuilder(d.length * 2);
        for (byte b : d) sb.append(String.format(Locale.ROOT, "%02X", b));
        return sb.toString();
    }
}
