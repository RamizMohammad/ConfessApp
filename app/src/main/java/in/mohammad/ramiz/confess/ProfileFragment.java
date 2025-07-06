package in.mohammad.ramiz.confess;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;

import in.mohammad.ramiz.confess.haptics.VibManager;
import in.mohammad.ramiz.confess.popups.ButtonLoader;
import in.mohammad.ramiz.confess.popups.OkPopUp;
import in.mohammad.ramiz.confess.profiledatabase.ProfileRepo;
import in.mohammad.ramiz.confess.profiledatabase.ProfileViewmodel;
import in.mohammad.ramiz.confess.security.BiometricPrefs;

public class ProfileFragment extends Fragment {

    private ProfileViewmodel viewmodel;
    private TextView aliasName, about, bioText;
    private ImageView bioIcon;
    private ShimmerFrameLayout skelitonUi;
    private LinearLayout realUi;
    private GoogleSignInAccount account;
    private SwipeRefreshLayout refreshLayout;
    private LinearLayout button1, button2, button3, button4, button5, button6;

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
        bioText = view.findViewById(R.id.bioText);
        bioIcon = view.findViewById(R.id.bioIcon);
        button1 = view.findViewById(R.id.button1);
        button2 = view.findViewById(R.id.button2);
        button3 = view.findViewById(R.id.button3);
        button4 = view.findViewById(R.id.button4);
        button5 = view.findViewById(R.id.button5);
        button6 = view.findViewById(R.id.button6);
        refreshLayout.setProgressViewOffset(true, 100, 150);

        account = GoogleSignIn.getLastSignedInAccount(requireContext());
        viewmodel = new ViewModelProvider(this).get(ProfileViewmodel.class);

        boolean biometricInfo = BiometricPrefs.getInstance(requireContext()).isBiometricEnabled();

        Drawable originalDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.profle_buttons);
        GradientDrawable buttonDrawable = (GradientDrawable) originalDrawable.mutate().getConstantState().newDrawable().mutate();
        button3.setBackground(buttonDrawable);

        if (biometricInfo) {
            bioText.setTextColor(Color.BLACK);
            bioIcon.setImageTintList(ContextCompat.getColorStateList(requireContext(), android.R.color.black));
            buttonDrawable.setColor(Color.WHITE);
        } else {
            bioText.setTextColor(Color.WHITE);
            bioIcon.setImageTintList(ContextCompat.getColorStateList(requireContext(), android.R.color.white));
            buttonDrawable.setColor(Color.BLACK);
        }

        button1.setOnClickListener(v -> VibManager.vibrateTick(requireContext()));

        button2.setOnClickListener(v -> {
            VibManager.vibrateTick(requireContext());
            Intent updateIntent = new Intent(getContext(), UpdateAbout.class);
            startActivity(updateIntent);
        });

        button3.setOnClickListener(v ->{
            VibManager.vibrateTick(requireContext());
            new ButtonLoader(getActivity(), R.drawable.biometric,
                    "Do you want to disable biometric login",
                    new ButtonLoader.onUserOkClick() {
                @Override
                public void onOk() {

                }
            });
        });

        if (account == null) {
            refreshLayout.setEnabled(false);
            return;
        }

        skelitonUi.startShimmer();
        skelitonUi.setVisibility(View.VISIBLE);
        realUi.setVisibility(View.GONE);

        String email = account.getEmail();
        setExistingData(email);

        refreshLayout.setOnRefreshListener(() -> {
            VibManager.vibrateTick(requireContext());
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
                if (!about.getText().toString().equals(profileEntity.getAbout())) {
                    aliasName.setText(profileEntity.getAliasName());
                    about.setText(profileEntity.getAbout());
                }
                stopLoadingAndShowData();
            }
        });

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
