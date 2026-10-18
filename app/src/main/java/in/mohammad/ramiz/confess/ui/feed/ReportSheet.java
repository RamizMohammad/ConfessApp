package in.mohammad.ramiz.confess.ui.feed;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.gson.JsonObject;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.repo.UserRepository;
import in.mohammad.ramiz.confess.databinding.SheetReportBinding;

/**
 * Pick why a confession — a person ({@link #showUser}) or a response ({@link #showComment}) — is being reported.
 * The answer goes back to the parent fragment through the Fragment Result API, so it
 * survives rotation / process death.
 */
public class ReportSheet extends BottomSheetDialogFragment {

    public static final String RESULT = "report_result";
    public static final String KEY_POST_ID = "post_id";
    /** Set (instead of a post id) when a person is being reported. */
    public static final String KEY_ALIAS = "alias";
    /** Set (instead of a post id) when a response is being reported. */
    public static final String KEY_COMMENT_ID = "comment_id";
    /** Set (instead of a post id) when a book is being reported. */
    public static final String KEY_BOOK_ID = "book_id";
    public static final String KEY_REASON = "reason";
    private static final String TAG = "report_sheet";

    private SheetReportBinding b;

    /** From a fragment: listen with getChildFragmentManager().setFragmentResultListener(RESULT, …). */
    public static void show(@NonNull Fragment parent, @NonNull String postId) {
        showIn(parent.getChildFragmentManager(), postId);
    }

    /** Report a person (same reasons, different title). Listen exactly like a post report. */
    public static void showUser(@NonNull FragmentManager fm, @NonNull String alias) {
        if (fm.findFragmentByTag(TAG) != null) return;
        ReportSheet sheet = new ReportSheet();
        Bundle args = new Bundle();
        args.putString(KEY_ALIAS, alias);
        sheet.setArguments(args);
        sheet.show(fm, TAG);
    }

    /** Report a response. Listen exactly like a post report; the screen hears {@link Outcome#commentReported}. */
    public static void showComment(@NonNull FragmentManager fm, @NonNull String commentId) {
        if (fm.findFragmentByTag(TAG) != null) return;
        ReportSheet sheet = new ReportSheet();
        Bundle args = new Bundle();
        args.putString(KEY_COMMENT_ID, commentId);
        sheet.setArguments(args);
        sheet.show(fm, TAG);
    }

    /** Report a book (its title / blurb). Listen like a post report; the screen hears {@link Outcome#bookReported}. */
    public static void showBook(@NonNull FragmentManager fm, @NonNull String bookId) {
        if (fm.findFragmentByTag(TAG) != null) return;
        ReportSheet sheet = new ReportSheet();
        Bundle args = new Bundle();
        args.putString(KEY_BOOK_ID, bookId);
        sheet.setArguments(args);
        sheet.show(fm, TAG);
    }

    /** From an activity: listen with getSupportFragmentManager().setFragmentResultListener(RESULT, …). */
    public static void showIn(@NonNull FragmentManager fm, @NonNull String postId) {
        if (fm.findFragmentByTag(TAG) != null) return;
        ReportSheet sheet = new ReportSheet();
        Bundle args = new Bundle();
        args.putString(KEY_POST_ID, postId);
        sheet.setArguments(args);
        sheet.show(fm, TAG);
    }

    /** What a screen does with a result: post reports go to its ViewModel, person reports are sent here. */
    public interface Outcome {
        void reportPost(@NonNull String postId, @NonNull String reason);

        /** A short message for the screen's snackbar. */
        void say(@NonNull String text);

        /** A response was reported — drop it from the list (it's hidden for me now). */
        default void commentReported(@NonNull String commentId) { }

        /** A book was reported — it's hidden for me now. */
        default void bookReported(@NonNull String bookId) { }
    }

    /** Call from the RESULT listener. */
    public static void deliver(@NonNull Context ctx, @NonNull Bundle result, @NonNull Outcome o) {
        String reason = result.getString(KEY_REASON);
        if (reason == null) return;
        String alias = result.getString(KEY_ALIAS);
        if (alias != null) {
            Context app = ctx.getApplicationContext();
            new UserRepository().reportUser(alias, reason, new ApiCallback<JsonObject>() {
                @Override public void onSuccess(JsonObject data) { o.say(app.getString(R.string.reported_user)); }
                @Override public void onError(ApiException e) { o.say(e.userMessage(app)); }
            });
            return;
        }
        String bookId = result.getString(KEY_BOOK_ID);
        if (bookId != null) {
            Context app = ctx.getApplicationContext();
            new in.mohammad.ramiz.confess.data.repo.BookRepository().report(bookId, reason, new ApiCallback<JsonObject>() {
                @Override public void onSuccess(JsonObject data) {
                    android.widget.Toast.makeText(app, R.string.reported_book, android.widget.Toast.LENGTH_LONG).show();
                    o.bookReported(bookId);
                }
                @Override public void onError(ApiException e) { o.say(e.userMessage(app)); }
            });
            return;
        }
        String commentId = result.getString(KEY_COMMENT_ID);
        if (commentId != null) {
            Context app = ctx.getApplicationContext();
            o.commentReported(commentId);
            new in.mohammad.ramiz.confess.data.repo.PostRepository().reportComment(commentId, reason,
                    new ApiCallback<JsonObject>() {
                        @Override public void onSuccess(JsonObject data) { o.say(app.getString(R.string.reported_response)); }
                        @Override public void onError(ApiException e) { o.say(e.userMessage(app)); }
                    });
            return;
        }
        String postId = result.getString(KEY_POST_ID);
        if (postId != null) o.reportPost(postId, reason);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = SheetReportBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        b.send.setAlpha(0.4f);
        if (requireArguments().getString(KEY_ALIAS) != null) {
            b.title.setText(R.string.report_user_title);
            b.subtitle.setText(R.string.report_user_subtitle);
            b.send.setText(R.string.report_user_send);
        } else if (requireArguments().getString(KEY_BOOK_ID) != null) {
            b.title.setText(R.string.report_book_title);
            b.subtitle.setText(R.string.report_book_subtitle);
        } else if (requireArguments().getString(KEY_COMMENT_ID) != null) {
            b.title.setText(R.string.report_response_title);
            b.subtitle.setText(R.string.report_response_subtitle);
        }
        b.reasons.setOnCheckedChangeListener((group, id) -> {
            Haptics.tick(requireContext());
            b.send.setEnabled(id != View.NO_ID);
            b.send.setAlpha(id != View.NO_ID ? 1f : 0.4f);
        });
        b.send.setOnClickListener(v -> {
            String reason = reason(b.reasons.getCheckedRadioButtonId());
            if (reason == null) return;
            Bundle result = new Bundle();
            result.putString(KEY_POST_ID, requireArguments().getString(KEY_POST_ID));
            result.putString(KEY_ALIAS, requireArguments().getString(KEY_ALIAS));
            result.putString(KEY_COMMENT_ID, requireArguments().getString(KEY_COMMENT_ID));
            result.putString(KEY_BOOK_ID, requireArguments().getString(KEY_BOOK_ID));
            result.putString(KEY_REASON, reason);
            getParentFragmentManager().setFragmentResult(RESULT, result);
            Haptics.click(requireContext());
            dismiss();
        });
    }

    /** Short machine-friendly reason codes for the moderators' queue. */
    @Nullable
    private static String reason(int checkedId) {
        if (checkedId == R.id.reasonHate) return "hate_or_harassment";
        if (checkedId == R.id.reasonPersonal) return "reveals_identity";
        if (checkedId == R.id.reasonThreat) return "threat_or_self_harm";
        if (checkedId == R.id.reasonSexual) return "sexual_or_minor";       // child safety — reviewed first
        if (checkedId == R.id.reasonSpam) return "spam";
        if (checkedId == R.id.reasonOther) return "other";
        return null;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() instanceof BottomSheetDialog) {
            BottomSheetBehavior<?> behavior = ((BottomSheetDialog) getDialog()).getBehavior();
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            behavior.setSkipCollapsed(true);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
