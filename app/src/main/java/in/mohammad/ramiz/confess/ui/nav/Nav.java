package in.mohammad.ramiz.confess.ui.nav;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityOptionsCompat;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.ui.activity.NotificationsActivity;
import in.mohammad.ramiz.confess.ui.compose.ComposeActivity;
import in.mohammad.ramiz.confess.ui.me.LettersActivity;
import in.mohammad.ramiz.confess.ui.me.ReceivedActivity;
import in.mohammad.ramiz.confess.ui.post.PostDetailActivity;
import in.mohammad.ramiz.confess.ui.profile.ProfileActivity;
import in.mohammad.ramiz.confess.ui.search.SearchActivity;
import in.mohammad.ramiz.confess.ui.settings.EditProfileActivity;
import in.mohammad.ramiz.confess.ui.settings.SettingsActivity;

/**
 * Every in-app jump goes through here, so screens never hard-code each other.
 */
public final class Nav {

    public static final String EXTRA_POST_ID = "post_id";
    public static final String EXTRA_TO_RESPONSES = "to_responses";
    public static final String EXTRA_ALIAS = "alias";

    private Nav() { }

    /** Composer. Home opens it through {@link #composeIntent} to hear back when a confession is released. */
    public static void compose(@NonNull Context ctx) {
        ctx.startActivity(composeIntent(ctx), composeAnimation(ctx).toBundle());
    }

    public static Intent composeIntent(@NonNull Context ctx) {
        return new Intent(ctx, ComposeActivity.class);
    }

    /** The composer slides up over the current screen. */
    public static ActivityOptionsCompat composeAnimation(@NonNull Context ctx) {
        return ActivityOptionsCompat.makeCustomAnimation(ctx, R.anim.slide_up_in, R.anim.stay);
    }

    /** A confession and its responses; toResponses scrolls to them (and opens the keyboard). */
    public static void post(@NonNull Context ctx, @NonNull String postId, boolean toResponses) {
        ctx.startActivity(new Intent(ctx, PostDetailActivity.class)
                .putExtra(EXTRA_POST_ID, postId)
                .putExtra(EXTRA_TO_RESPONSES, toResponses));
    }

    /** Whispers & Echoes (notifications). */
    public static void notifications(@NonNull Context ctx) {
        ctx.startActivity(new Intent(ctx, NotificationsActivity.class));
    }

    /** People search by alias. */
    public static void search(@NonNull Context ctx) {
        ctx.startActivity(new Intent(ctx, SearchActivity.class));
    }

    /** Someone's profile (or mine). */
    public static void profile(@NonNull Context ctx, @NonNull String alias) {
        ctx.startActivity(new Intent(ctx, ProfileActivity.class).putExtra(EXTRA_ALIAS, alias));
    }

    /** All my letters. */
    public static void myLetters(@NonNull Context ctx) {
        ctx.startActivity(new Intent(ctx, LettersActivity.class));
    }

    /** Felt / responses on my letters (mode = ReceivedActivity.MODE_*). */
    public static void received(@NonNull Context ctx, @NonNull String mode) {
        ctx.startActivity(new Intent(ctx, ReceivedActivity.class).putExtra(ReceivedActivity.EXTRA_MODE, mode));
    }

    /** Settings: lock, notifications, account. */
    public static void settings(@NonNull Context ctx) {
        ctx.startActivity(new Intent(ctx, SettingsActivity.class));
    }

    /** Edit aura / about. */
    public static void editProfile(@NonNull Context ctx) {
        ctx.startActivity(new Intent(ctx, EditProfileActivity.class));
    }
}
