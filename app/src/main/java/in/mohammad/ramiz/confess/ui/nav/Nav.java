package in.mohammad.ramiz.confess.ui.nav;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityOptionsCompat;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.ui.activity.NotificationsActivity;
import in.mohammad.ramiz.confess.ui.books.BookActivity;
import in.mohammad.ramiz.confess.ui.books.reader.ReaderActivity;
import in.mohammad.ramiz.confess.ui.books.write.BookEditorActivity;
import in.mohammad.ramiz.confess.ui.books.write.BookSetupActivity;
import in.mohammad.ramiz.confess.ui.books.write.ChapterEditorActivity;
import in.mohammad.ramiz.confess.ui.books.write.MyBooksActivity;
import in.mohammad.ramiz.confess.ui.compose.ComposeActivity;
import in.mohammad.ramiz.confess.ui.me.DraftsActivity;
import in.mohammad.ramiz.confess.ui.people.PeopleActivity;
import in.mohammad.ramiz.confess.ui.settings.NotificationSettingsActivity;
import in.mohammad.ramiz.confess.ui.me.LettersActivity;
import in.mohammad.ramiz.confess.ui.me.ReceivedActivity;
import in.mohammad.ramiz.confess.ui.post.PostDetailActivity;
import in.mohammad.ramiz.confess.ui.profile.ProfileActivity;
import in.mohammad.ramiz.confess.ui.search.SearchActivity;
import in.mohammad.ramiz.confess.ui.settings.EditProfileActivity;
import in.mohammad.ramiz.confess.ui.settings.SettingsActivity;
import in.mohammad.ramiz.confess.ui.stories.StoryComposeActivity;
import in.mohammad.ramiz.confess.ui.stories.StoryViewerActivity;

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

    /** Continue a draft (Me → Drafts). */
    public static void composeDraft(@NonNull Context ctx, @NonNull String draftId) {
        ctx.startActivity(composeIntent(ctx).putExtra(ComposeActivity.EXTRA_DRAFT_ID, draftId),
                composeAnimation(ctx).toBundle());
    }

    public static void drafts(@NonNull Context ctx) {
        ctx.startActivity(new Intent(ctx, DraftsActivity.class));
    }

    /** Me → Followers (true) / Following (false). */
    public static void people(@NonNull Context ctx, boolean followers) {
        ctx.startActivity(new Intent(ctx, PeopleActivity.class).putExtra(PeopleActivity.EXTRA_FOLLOWERS, followers));
    }

    /** Stories viewer, starting at this person in the tray (StoryTrayCache). */
    public static void stories(@NonNull Context ctx, int groupIndex) {
        ctx.startActivity(new Intent(ctx, StoryViewerActivity.class).putExtra(StoryViewerActivity.EXTRA_GROUP, groupIndex),
                ActivityOptionsCompat.makeCustomAnimation(ctx, R.anim.story_open, R.anim.stay).toBundle());
    }

    /** Just one person's stories (tapping a ringed avatar anywhere). */
    public static void storiesOf(@NonNull Context ctx, int groupIndex) {
        ctx.startActivity(new Intent(ctx, StoryViewerActivity.class)
                        .putExtra(StoryViewerActivity.EXTRA_GROUP, groupIndex)
                        .putExtra(StoryViewerActivity.EXTRA_SINGLE, true),
                ActivityOptionsCompat.makeCustomAnimation(ctx, R.anim.story_open, R.anim.stay).toBundle());
    }

    /** New 24-hour story. */
    public static void composeStory(@NonNull Context ctx) {
        ctx.startActivity(new Intent(ctx, StoryComposeActivity.class), composeAnimation(ctx).toBundle());
    }

    public static void notificationSettings(@NonNull Context ctx) {
        ctx.startActivity(new Intent(ctx, NotificationSettingsActivity.class));
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

    // ── Books ─────────────────────────────────────────────────────────────

    /** A book's page (cover, contents, Add to library). */
    public static void book(@NonNull Context ctx, @NonNull String bookId) {
        ctx.startActivity(new Intent(ctx, BookActivity.class).putExtra(BookActivity.EXTRA_BOOK_ID, bookId));
    }

    /** The e-book reader at a chapter; chapterNo 0 = carry on where I left off. */
    public static void reader(@NonNull Context ctx, @NonNull String bookId, int chapterNo) {
        ctx.startActivity(new Intent(ctx, ReaderActivity.class)
                        .putExtra(ReaderActivity.EXTRA_BOOK_ID, bookId)
                        .putExtra(ReaderActivity.EXTRA_CHAPTER, chapterNo),
                ActivityOptionsCompat.makeCustomAnimation(ctx, R.anim.story_open, R.anim.stay).toBundle());
    }

    /** Books → Your books. */
    public static void myBooks(@NonNull Context ctx) {
        ctx.startActivity(new Intent(ctx, MyBooksActivity.class));
    }

    /** Start a book (bookId null) or edit its title / blurb / cover. */
    public static void bookSetup(@NonNull Context ctx, @Nullable String bookId) {
        Intent i = new Intent(ctx, BookSetupActivity.class);
        if (bookId != null) i.putExtra(BookSetupActivity.EXTRA_BOOK_ID, bookId);
        ctx.startActivity(i, composeAnimation(ctx).toBundle());
    }

    /** The writer's desk for one of my books. */
    public static void bookEditor(@NonNull Context ctx, @NonNull String bookId) {
        ctx.startActivity(new Intent(ctx, BookEditorActivity.class).putExtra(BookEditorActivity.EXTRA_BOOK_ID, bookId));
    }

    /** Write a new chapter (chapterNo 0) or edit one. */
    public static void chapterEditor(@NonNull Context ctx, @NonNull String bookId, int chapterNo) {
        ctx.startActivity(new Intent(ctx, ChapterEditorActivity.class)
                        .putExtra(ChapterEditorActivity.EXTRA_BOOK_ID, bookId)
                        .putExtra(ChapterEditorActivity.EXTRA_CHAPTER, chapterNo),
                composeAnimation(ctx).toBundle());
    }
}
