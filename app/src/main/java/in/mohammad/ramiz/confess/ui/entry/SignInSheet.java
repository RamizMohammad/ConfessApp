package in.mohammad.ramiz.confess.ui.entry;

import android.content.Intent;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import in.mohammad.ramiz.confess.BuildConfig;
import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.api.ErrorCode;
import in.mohammad.ramiz.confess.data.model.Me;
import in.mohammad.ramiz.confess.data.repo.AuthRepository;
import in.mohammad.ramiz.confess.databinding.SheetSignInBinding;
import in.mohammad.ramiz.confess.ui.auth.GoogleAuth;
import in.mohammad.ramiz.confess.ui.auth.OnboardingDraft;
import in.mohammad.ramiz.confess.ui.base.Router;

/**
 * S2 · Sign-in sheet ("Enter the room"). Google → server login:
 *   200            → returning user → unlock / home
 *   NOT_REGISTERED → new user → guidelines (S3) with the Google token kept in memory
 */
public class SignInSheet extends BottomSheetDialogFragment {

    private static final String TAG = "sign_in_sheet";

    private SheetSignInBinding b;
    @Nullable private CancellationSignal googleCancel;
    private boolean working = false;

    public static void show(FragmentManager fm) {
        if (fm.findFragmentByTag(TAG) == null) new SignInSheet().show(fm, TAG);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = SheetSignInBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        b.close.setOnClickListener(v -> dismiss());
        b.google.setOnClickListener(v -> startGoogle());
        b.terms.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), GuidelinesActivity.class)
                        .putExtra(GuidelinesActivity.EXTRA_READ_ONLY, true)));
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

    private void startGoogle() {
        if (working) return;
        setWorking(true);
        b.error.setVisibility(View.GONE);
        googleCancel = GoogleAuth.signIn(requireActivity(), new GoogleAuth.Callback() {
            @Override
            public void onIdToken(@NonNull String idToken) {
                loginWithServer(idToken);
            }

            @Override
            public void onCancelled() {
                setWorking(false);
            }

            @Override
            public void onFailed(@NonNull String reason) {
                setWorking(false);
                // Debug builds show the real cause (logcat tag: ConfessAuth).
                showError(getString(R.string.signin_failed) + (BuildConfig.DEBUG ? "\n" + reason : ""));
            }
        });
    }

    private void loginWithServer(String idToken) {
        new AuthRepository().login(idToken, new ApiCallback<Me>() {
            @Override
            public void onSuccess(Me me) {
                if (!isAdded()) return;
                Haptics.click(requireContext());
                Router.toApp(requireActivity());
            }

            @Override
            public void onError(ApiException error) {
                if (!isAdded()) return;
                if (error.is(ErrorCode.NOT_REGISTERED)) {
                    OnboardingDraft.start(idToken);
                    setWorking(false);
                    dismiss();
                    startActivity(new Intent(requireContext(), GuidelinesActivity.class));
                    return;
                }
                setWorking(false);
                Haptics.reject(requireContext());
                showError(error.userMessage(requireContext())
                        + (BuildConfig.DEBUG ? "\n" + error.code + " (" + error.httpStatus + ") " + error.serverMessage : ""));
            }
        });
    }

    private void setWorking(boolean on) {
        working = on;
        if (b == null) return;
        b.google.setEnabled(!on);
        b.google.setText(on ? R.string.signin_working : R.string.signin_google);
        b.progress.setVisibility(on ? View.VISIBLE : View.INVISIBLE);
        setCancelable(!on);
    }

    private void showError(String message) {
        if (b == null) return;
        b.error.setText(message);
        b.error.setVisibility(View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        if (googleCancel != null) googleCancel.cancel();
        b = null;
        super.onDestroyView();
    }
}
