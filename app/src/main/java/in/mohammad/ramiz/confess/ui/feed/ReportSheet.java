package in.mohammad.ramiz.confess.ui.feed;

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

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.databinding.SheetReportBinding;

/**
 * Pick why a confession is being reported. Old: a plain "Report" menu item with no reason.
 * The answer goes back to the parent fragment through the Fragment Result API, so it
 * survives rotation / process death.
 */
public class ReportSheet extends BottomSheetDialogFragment {

    public static final String RESULT = "report_result";
    public static final String KEY_POST_ID = "post_id";
    public static final String KEY_REASON = "reason";
    private static final String TAG = "report_sheet";

    private SheetReportBinding b;

    /** From a fragment: listen with getChildFragmentManager().setFragmentResultListener(RESULT, …). */
    public static void show(@NonNull Fragment parent, @NonNull String postId) {
        showIn(parent.getChildFragmentManager(), postId);
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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = SheetReportBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        b.send.setAlpha(0.4f);
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
