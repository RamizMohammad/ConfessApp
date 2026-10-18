package in.mohammad.ramiz.confess.ui.home;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.JsonObject;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.DeepLink;
import in.mohammad.ramiz.confess.core.ResetNotice;
import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.model.Me;
import in.mohammad.ramiz.confess.data.repo.UserRepository;
import in.mohammad.ramiz.confess.ui.auth.Biometrics;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.core.push.Push;
import in.mohammad.ramiz.confess.data.session.SessionManager;
import in.mohammad.ramiz.confess.databinding.ActivityHomeBinding;
import in.mohammad.ramiz.confess.ui.base.BaseActivity;
import in.mohammad.ramiz.confess.ui.books.BooksFragment;
import in.mohammad.ramiz.confess.ui.feed.FeedFragment;
import in.mohammad.ramiz.confess.ui.me.MeFragment;
import in.mohammad.ramiz.confess.ui.nav.Nav;
import in.mohammad.ramiz.confess.ui.util.EdgeInsets;

/**
 * Home shell. Old: MainActivity + BottomNavigationView (Home / Add / Profile).
 * Feed · Search · (Confess) · Books · Me — Confess (centre) opens the composer and Search opens
 * people search; neither is a tab. All tabs stay alive (show/hide) so scroll positions survive.
 */
public class HomeActivity extends BaseActivity {

    private static final String TAG_FEED = "feed";
    private static final String TAG_BOOKS = "books";
    private static final String TAG_ME = "me";
    private static final String STATE_TAB = "tab";

    private ActivityHomeBinding b;
    private String tab = TAG_FEED;
    private final ActivityResultLauncher<Intent> composer = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            r -> { if (r.getResultCode() == RESULT_OK) onReleased(); });
    private final ActivityResultLauncher<String> notificationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> { });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeInsets.padBottom(b.bottomBar.getRoot(), false);

        FragmentManager fm = getSupportFragmentManager();
        if (savedInstanceState == null) {
            fm.beginTransaction()
                    .setReorderingAllowed(true)
                    .add(R.id.container, new FeedFragment(), TAG_FEED)
                    .add(R.id.container, new BooksFragment(), TAG_BOOKS)
                    .add(R.id.container, new MeFragment(), TAG_ME)
                    .commitNow();
        } else {
            tab = savedInstanceState.getString(STATE_TAB, TAG_FEED);
        }
        show(tab, false);

        b.bottomBar.tabFeed.setOnClickListener(v -> select(TAG_FEED));
        b.bottomBar.tabSearch.setOnClickListener(v -> {
            Haptics.tick(this);
            Nav.search(this);
        });
        b.bottomBar.tabBooks.setOnClickListener(v -> select(TAG_BOOKS));
        b.bottomBar.tabMe.setOnClickListener(v -> select(TAG_ME));
        b.bottomBar.compose.setOnClickListener(v -> {
            Haptics.click(this);
            openComposer();
        });

        Push.sync(this);
        if (savedInstanceState == null) b.getRoot().postDelayed(this::maybeAskNotifications, 1500);

        // Back on "Books" / "Me" returns to the feed first, like the old app.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!TAG_FEED.equals(tab)) {
                    select(TAG_FEED);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Tapped a push: open that confession (only once unlocked).
        if (!SessionManager.get().needsUnlock()) {
            String[] link = DeepLink.consume();
            if (link != null) Nav.post(this, link[0], "1".equals(link[1]));
            String who = DeepLink.consumeAlias();
            if (who != null) Nav.profile(this, who);
            if (DeepLink.consumeActivity()) Nav.notifications(this);
            DeepLink.Chapter chapter = DeepLink.consumeChapter();
            if (chapter != null) {
                if (chapter.edit) Nav.chapterEditor(this, chapter.bookId, chapter.chapterNo);
                else Nav.reader(this, chapter.bookId, chapter.chapterNo);
            }
            checkBiometricAfterReset();
            in.mohammad.ramiz.confess.core.GuidelinesReminder.maybeShow(this);
        }
    }

    /** A password reset switched fingerprint unlock off — offer to set it up again (once per run). */
    private void checkBiometricAfterReset() {
        Me cached = SessionManager.get().cachedMe();          // refreshed by the unlock screen
        if (cached == null || !cached.biometricResetPending) return;
        new UserRepository().me(new ApiCallback<Me>() {
            @Override public void onSuccess(Me me) {
                if (isFinishing() || !me.biometricResetPending || !me.hasPassword) return;
                if (!Biometrics.available(HomeActivity.this)) {
                    clearBiometricNotice(false);             // nothing to set up on this phone
                    return;
                }
                if (!ResetNotice.shouldAskBiometricAgain()) return;
                new MaterialAlertDialogBuilder(HomeActivity.this)
                        .setIcon(R.drawable.ic_fingerprint)
                        .setTitle(R.string.bio_again_title)
                        .setMessage(R.string.bio_again_body)
                        .setCancelable(false)
                        .setNegativeButton(R.string.bio_again_no, (d, w) -> clearBiometricNotice(false))
                        .setPositiveButton(R.string.bio_again_yes, (d, w) -> Biometrics.prompt(HomeActivity.this,
                                getString(R.string.bio_prompt_title), getString(R.string.bio_prompt_subtitle),
                                new Biometrics.Callback() {
                                    @Override public void onSuccess() { clearBiometricNotice(true); }
                                    @Override public void onFailure(@NonNull CharSequence reason) { }
                                }))
                        .show();
            }
            @Override public void onError(ApiException e) { }
        });
    }

    private void clearBiometricNotice(boolean enable) {
        new UserRepository().setBiometric(enable, new ApiCallback<JsonObject>() {
            @Override public void onSuccess(JsonObject data) {
                if (enable) Snackbar.make(b.container, R.string.bio_on_done, Snackbar.LENGTH_SHORT)
                        .setAnchorView(b.bottomBar.getRoot()).show();
                new UserRepository().me(new ApiCallback<Me>() {   // refresh the cached flags
                    @Override public void onSuccess(Me me) { }
                    @Override public void onError(ApiException e) { }
                });
            }
            @Override public void onError(ApiException e) { }
        });
    }

    /** Android 13+: ask once for notification permission (the old app asked on first launch). */
    private void maybeAskNotifications() {
        if (isFinishing()) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) return;
        SharedPreferences p = getSharedPreferences("home", MODE_PRIVATE);
        if (p.getBoolean("asked_notifications", false)) return;
        p.edit().putBoolean("asked_notifications", true).apply();
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    /** Composer → on release: back to the feed, Latest, scrolled to the new confession. */
    public void openComposer() {
        composer.launch(Nav.composeIntent(this), Nav.composeAnimation(this));
    }

    private void onReleased() {
        show(TAG_FEED, false);
        Fragment f = getSupportFragmentManager().findFragmentByTag(TAG_FEED);
        if (f instanceof FeedFragment) ((FeedFragment) f).showNewConfession();
        Snackbar.make(b.container, R.string.compose_released, Snackbar.LENGTH_LONG)
                .setAnchorView(b.bottomBar.getRoot())
                .show();
    }

    private void select(String next) {
        if (next.equals(tab)) {
            Fragment f = getSupportFragmentManager().findFragmentByTag(next);
            if (f instanceof FeedFragment) ((FeedFragment) f).scrollToTop();
            else if (f instanceof BooksFragment) ((BooksFragment) f).scrollToTop();
            else if (f instanceof MeFragment) ((MeFragment) f).scrollToTop();
            return;
        }
        Haptics.tick(this);
        show(next, true);
    }

    private void show(String which, boolean animate) {
        tab = which;
        FragmentManager fm = getSupportFragmentManager();
        Fragment feed = fm.findFragmentByTag(TAG_FEED);
        Fragment books = fm.findFragmentByTag(TAG_BOOKS);
        Fragment me = fm.findFragmentByTag(TAG_ME);
        if (feed == null || me == null) return;
        if (books == null) {                       // restored from a version without the Books tab
            books = new BooksFragment();
            fm.beginTransaction().setReorderingAllowed(true).add(R.id.container, books, TAG_BOOKS).hide(books).commitNow();
        }

        androidx.fragment.app.FragmentTransaction tx = fm.beginTransaction().setReorderingAllowed(true);
        if (animate) tx.setCustomAnimations(R.anim.tab_in, R.anim.tab_out);
        for (Fragment f : new Fragment[]{feed, books, me}) {
            if (f == fm.findFragmentByTag(which)) tx.show(f); else tx.hide(f);
        }
        tx.commit();

        int on = ContextCompat.getColor(this, R.color.amber);
        int off = ContextCompat.getColor(this, R.color.text_muted);
        boolean onFeed = TAG_FEED.equals(which), onBooks = TAG_BOOKS.equals(which), onMe = TAG_ME.equals(which);
        b.bottomBar.iconFeed.setColorFilter(onFeed ? on : off);
        b.bottomBar.labelFeed.setTextColor(onFeed ? on : off);
        b.bottomBar.iconBooks.setColorFilter(onBooks ? on : off);
        b.bottomBar.labelBooks.setTextColor(onBooks ? on : off);
        b.bottomBar.iconSearch.setColorFilter(off);
        b.bottomBar.labelSearch.setTextColor(off);
        b.bottomBar.iconMe.setColorFilter(onMe ? on : off);
        b.bottomBar.labelMe.setTextColor(onMe ? on : off);
        b.bottomBar.tabFeed.setSelected(onFeed);
        b.bottomBar.tabBooks.setSelected(onBooks);
        b.bottomBar.tabMe.setSelected(onMe);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(STATE_TAB, tab);
    }
}
