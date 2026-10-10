package in.mohammad.ramiz.confess.ui.auth;

import androidx.annotation.Nullable;

/**
 * What a new user has chosen so far during onboarding
 * (Google token → guidelines → alias → avatar). Kept in memory only: the
 * Google ID token is short-lived and must never be written to disk.
 */
public final class OnboardingDraft {

    private static OnboardingDraft current;

    @Nullable public String googleIdToken;
    @Nullable public String alias;
    @Nullable public String avatar;
    public String about = "";
    @Nullable public String password;
    public boolean biometricEnabled;
    public boolean guidelinesAccepted;

    private OnboardingDraft() { }

    public static synchronized OnboardingDraft start(String googleIdToken) {
        current = new OnboardingDraft();
        current.googleIdToken = googleIdToken;
        return current;
    }

    /** The draft in progress, or null if onboarding wasn't started (e.g. process was killed). */
    @Nullable
    public static synchronized OnboardingDraft get() {
        return current;
    }

    public static synchronized void clear() {
        current = null;
    }
}
