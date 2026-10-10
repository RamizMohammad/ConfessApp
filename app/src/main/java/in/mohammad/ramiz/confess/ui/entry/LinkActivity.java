package in.mohammad.ramiz.confess.ui.entry;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabColorSchemeParams;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.content.ContextCompat;

import in.mohammad.ramiz.confess.BuildConfig;
import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.ResetNotice;
import in.mohammad.ramiz.confess.data.session.SessionManager;

/**
 * Links that land in the app (no UI of its own):
 *
 *   https://<server>/reset-password?token=…   (App Link from the reset email)
 *       → the reset page in an in-app Chrome tab, themed like Confess
 *   confess://reset-done                       (the reset page's "Open Confess")
 *       → lock again and go to sign-in / unlock with the new password
 *   confess://open                             → just open the app
 */
public class LinkActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Uri uri = getIntent() != null ? getIntent().getData() : null;

        if (uri != null && ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                && uri.getPath() != null && uri.getPath().startsWith("/reset-password")) {
            openInTab(uri);
        } else {
            if (uri != null && "confess".equals(uri.getScheme()) && "reset-done".equals(uri.getHost())) {
                ResetNotice.markJustReset(this);
                SessionManager.get().lock();         // unlock again, with the new password
            }
            startActivity(new Intent(this, LauncherActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        }
        finish();
        overridePendingTransition(0, 0);
    }

    /** Only our own server's page; anything else is ignored. */
    private void openInTab(Uri uri) {
        Uri server = Uri.parse(BuildConfig.SERVER_URL);
        if (server.getHost() == null || !server.getHost().equalsIgnoreCase(uri.getHost())) return;
        int ink = ContextCompat.getColor(this, R.color.ink);
        CustomTabsIntent tab = new CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(new CustomTabColorSchemeParams.Builder()
                        .setToolbarColor(ink)
                        .setNavigationBarColor(ink)
                        .build())
                .setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
                .setShowTitle(true)
                .setUrlBarHidingEnabled(true)
                .build();
        try {
            tab.launchUrl(this, uri);
        } catch (Exception e) {                       // no browser with Custom Tabs: plain view
            startActivity(new Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE));
        }
    }
}
