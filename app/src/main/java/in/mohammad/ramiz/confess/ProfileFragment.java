package in.mohammad.ramiz.confess;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

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

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_profile,
                container,false);

        aliasName = view.findViewById(R.id.aliasName);
        about = view.findViewById(R.id.about);
        skelitonUi = view.findViewById(R.id.shimmer_layout);
        realUi = view.findViewById(R.id.real_text_container);

        account = GoogleSignIn.getLastSignedInAccount(requireContext());
        viewmodel = new ViewModelProvider(this).get(ProfileViewmodel.class);

        skelitonUi.startShimmer();
        setExistingData();
        observeServer();

        return view;
    }

    private void setExistingData(){
        if(account != null){
            String email = account.getEmail();
            viewmodel.getProfile(email).observe(getViewLifecycleOwner(), profileEntity -> {
                if(profileEntity != null){
                    aliasName.setText(profileEntity.getAliasName());
                    about.setText(profileEntity.getAbout());

                    skelitonUi.stopShimmer();
                    skelitonUi.setVisibility(View.GONE);
                    realUi.setVisibility(View.VISIBLE);
                }
            });
        }
    }

    private void observeServer(){
        if(account != null){
            String email = account.getEmail();

            viewmodel.serverSync(email, new ProfileRepo.onAnyError() {
                @Override
                public void userError() {
                    new OkPopUp(requireActivity(), R.raw.error_animation, "We are unable to find user");
                }

                @Override
                public void onResponseError() {
                    new OkPopUp(requireActivity(), R.raw.error_animation, "Ahh, we got a response error");
                }

                @Override
                public void onServerError() {
                    new OkPopUp(requireActivity(), R.raw.error_animation, "server didn't respond");
                }
            });
        }
    }
}