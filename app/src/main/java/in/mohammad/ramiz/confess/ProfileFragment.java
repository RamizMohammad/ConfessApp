package in.mohammad.ramiz.confess;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;

import in.mohammad.ramiz.confess.popups.OkPopUp;
import in.mohammad.ramiz.confess.profiledatabase.ProfileRepo;
import in.mohammad.ramiz.confess.profiledatabase.ProfileViewmodel;

public class ProfileFragment extends Fragment {

    private ProfileViewmodel viewmodel;
    private TextView aliasName;
    private TextView about;
    private ShimmerFrameLayout skelitonUi;
    private LinearLayout realUi;
    private GoogleSignInAccount account;
    private SwipeRefreshLayout refreshLayout;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        aliasName = view.findViewById(R.id.aliasName);
        about = view.findViewById(R.id.about);
        skelitonUi = view.findViewById(R.id.shimmer_layout);
        realUi = view.findViewById(R.id.real_text_container);
        refreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        refreshLayout.setProgressViewOffset(true, 100, 150);

        account = GoogleSignIn.getLastSignedInAccount(requireContext());
        viewmodel = new ViewModelProvider(this).get(ProfileViewmodel.class);

        if (account == null) {
            refreshLayout.setEnabled(false);
            return;
        }

        // Start loading animation
        skelitonUi.startShimmer();
        skelitonUi.setVisibility(View.VISIBLE);
        realUi.setVisibility(View.GONE);

        String email = account.getEmail();

        // Set existing data from DB
        setExistingData(email);

        // Pull-to-refresh listener
        refreshLayout.setOnRefreshListener(() -> {
            skelitonUi.startShimmer();
            skelitonUi.setVisibility(View.VISIBLE);
            realUi.setVisibility(View.GONE);

            viewmodel.serverSync(email, new ProfileRepo.onAnyError() {
                @Override
                public void userError() {
                    stopLoadingAndShowData();
                    new OkPopUp(requireActivity(), R.raw.error_animation, "We are unable to find user");
                }

                @Override
                public void onResponseError() {
                    stopLoadingAndShowData();
                    new OkPopUp(requireActivity(), R.raw.error_animation, "Ahh, we got a response error");
                }

                @Override
                public void onServerError() {
                    stopLoadingAndShowData();
                    new OkPopUp(requireActivity(), R.raw.error_animation, "Server didn't respond");
                }
            });
        });
    }

    private void setExistingData(String email) {
        viewmodel.getProfile(email).observe(getViewLifecycleOwner(), profileEntity -> {
            if (profileEntity != null) {
                // Avoid unnecessary redraws
                if (!aliasName.getText().toString().equals(profileEntity.getAliasName())) {
                    aliasName.setText(profileEntity.getAliasName());
                    about.setText(profileEntity.getAbout());
                }
                stopLoadingAndShowData();
            }
        });

        // Sync once silently
        viewmodel.serverSync(email, new ProfileRepo.onAnyError() {
            @Override public void userError() {}
            @Override public void onResponseError() {}
            @Override public void onServerError() {}
        });
    }

    private void stopLoadingAndShowData() {
        refreshLayout.setRefreshing(false);
        skelitonUi.stopShimmer();
        skelitonUi.setVisibility(View.GONE);
        realUi.setVisibility(View.VISIBLE);
    }
}
