package in.mohammad.ramiz.confess;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;

import java.util.concurrent.Executors;

import in.mohammad.ramiz.confess.debugmonitor.TelegramLogs;
import in.mohammad.ramiz.confess.entities.BiometricRequest;
import in.mohammad.ramiz.confess.entities.BiometricResponse;
import in.mohammad.ramiz.confess.haptics.VibManager;
import in.mohammad.ramiz.confess.popups.ButtonLoader;
import in.mohammad.ramiz.confess.popups.OkPopUp;
import in.mohammad.ramiz.confess.popups.OnlyLoader;
import in.mohammad.ramiz.confess.profiledatabase.ProfileRepo;
import in.mohammad.ramiz.confess.profiledatabase.ProfileViewmodel;
import in.mohammad.ramiz.confess.security.BiometricLogin;
import in.mohammad.ramiz.confess.security.BiometricPrefs;
import in.mohammad.ramiz.confess.server.Endpoints;
import in.mohammad.ramiz.confess.server.ServerConfigs;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileFragment extends Fragment {

    private ProfileViewmodel viewmodel;
    private TextView aliasName, about, bioText, pinText;
    private ImageView bioIcon, pinIcon, profile;
    private ShimmerFrameLayout skelitonUi, shimmerProfile;
    private LinearLayout realUi, signOut;
    private GoogleSignInAccount account;
    private SwipeRefreshLayout refreshLayout;
    private Endpoints endpoints;
    private OnlyLoader loader;
    private boolean biometricInfo, isPassword;
    private ButtonLoader buttonLoader;
    private LinearLayout button1, button2, button3, button4, button5, button6, button7, button8;

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
        shimmerProfile = view.findViewById(R.id.shimmer_profile_image);
        realUi = view.findViewById(R.id.real_text_container);
        profile = view.findViewById(R.id.profilePhoto);
        refreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        signOut = view.findViewById(R.id.signOutButton);
        bioText = view.findViewById(R.id.bioText);
        bioIcon = view.findViewById(R.id.bioIcon);
        pinIcon = view.findViewById(R.id.pinButton);
        pinText = view.findViewById(R.id.pinText);

        // Button layouts
        button1 = view.findViewById(R.id.button1);
        button2 = view.findViewById(R.id.button2);
        button3 = view.findViewById(R.id.button3);
        button4 = view.findViewById(R.id.button4);
        button5 = view.findViewById(R.id.button5);
        button6 = view.findViewById(R.id.button6);
        button7 = view.findViewById(R.id.button7);
        button8 = view.findViewById(R.id.button8);

        refreshLayout.setProgressViewOffset(true, 100, 150);

        account = GoogleSignIn.getLastSignedInAccount(requireContext());
        endpoints = ServerConfigs.getInstance().create(Endpoints.class);
        viewmodel = new ViewModelProvider(this).get(ProfileViewmodel.class);

        biometricInfo = BiometricPrefs.getInstance(requireContext()).isBiometricEnabled();
        isPassword = BiometricPrefs.getInstance(requireContext()).getPasswordStatus();
        Log.d("bioInfo", ""+biometricInfo);

        if(BiometricLogin.isBiometricThere(requireContext())){
            if(biometricInfo && isPassword){
                changeButtonDesign();
            } else if (isPassword) {
                bioIcon.setImageResource(R.drawable.biometric);
                bioText.setText("Enable Biometric");
            } else {
                bioIcon.setImageResource(R.drawable.password);
                bioText.setText("Set Password");
            }
        } else{
            bioText.setText("No hardware");
            bioIcon.setImageResource(R.drawable.nobiometrics);
        }

        if(BiometricPrefs.getInstance(getContext()).getPasswordStatus()){
            pinIcon.setImageResource(R.drawable.change_pins);
            pinText.setText("Change password");
        }
        else{
            pinIcon.setImageResource(R.drawable.slash_pin);
            pinText.setText("No password");
        }

        signOut.setOnClickListener(v -> {
            new ButtonLoader(requireActivity(), R.drawable.logout,
                    "As we only support google login you have continue from start to protect privacy but your data is secured"
                    , new ButtonLoader.onUserOkClick() {
                @Override
                public void onOk() {
                    signOut();
                }
            });
        });

        button1.setOnClickListener(v -> {
            VibManager.vibrateTick(requireContext());
            Intent yourConfession = new Intent(getContext(), YourConfession.class);
            startActivity(yourConfession);
            requireActivity().overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        });

        button2.setOnClickListener(v -> {
            VibManager.vibrateTick(requireContext());
            Intent updateIntent = new Intent(getContext(), UpdateAbout.class);
            startActivity(updateIntent);
            requireActivity().overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        });

        button3.setOnClickListener(v ->{
            VibManager.vibrateTick(requireContext());
            if(BiometricLogin.isBiometricThere(requireContext())){
                if(biometricInfo && isPassword){
                    new ButtonLoader(getActivity(), R.drawable.biometric,
                            "Do you want to disable biometric login but your password will not be disabled",
                            new ButtonLoader.onUserOkClick() {
                                @Override
                                public void onOk() {
                                    if(account != null){
                                        String email = account.getEmail();
                                        loader = new OnlyLoader(getActivity(), R.raw.loading_animation);
                                        changeBiometricStatus(email, new ServerCallback() {
                                            @Override
                                            public void onSuccess(boolean isChanged) {
                                                if(isChanged){
                                                    loader.dismiss();
                                                    BiometricPrefs.getInstance(getContext()).setBiometricEnabled(false);
                                                    Toast.makeText(getContext(), "Biometric status updated", Toast.LENGTH_SHORT).show();
                                                    Intent loginIntent = new Intent(requireContext(), Password_Page.class);
                                                    startActivity(loginIntent);
                                                    requireActivity().finish();
                                                }
                                                else{
                                                    loader.dismiss();
                                                    new OkPopUp(requireActivity(), R.raw.error_animation, "Ahh, we got some error");
                                                }
                                            }

                                            @Override
                                            public void onFailure() {
                                                loader.dismiss();
                                                new OkPopUp(requireActivity(), R.raw.error_animation, "We got server error");
                                            }
                                        });
                                    }
                                }
                            });
                }
                else if(isPassword){
                    startBiometric(new Runnable() {
                        @Override
                        public void run() {
                            if(account != null){
                                loader = new OnlyLoader(requireActivity(), R.raw.loading_animation);
                                String email = account.getEmail();
                                changeBiometricStatus(email, new ServerCallback() {
                                    @Override
                                    public void onSuccess(boolean isChanged) {
                                        if(isChanged){
                                            loader.dismiss();
                                            BiometricPrefs.getInstance(requireContext()).setBiometricEnabled(true);
                                            biometricInfo = true;
                                            changeButtonDesign();
                                        }
                                        else{
                                            loader.dismiss();
                                            new OkPopUp(requireActivity(), R.raw.error_animation, "We got some error");
                                        }
                                    }

                                    @Override
                                    public void onFailure() {
                                        loader.dismiss();
                                        new OkPopUp(requireActivity(), R.raw.error_animation, "We got server error");
                                    }
                                });
                            }
                        }
                    });
                } else{
                    new ButtonLoader(requireActivity(), R.drawable.biometric,
                            "Set a password first to use biometric login",
                            new ButtonLoader.onUserOkClick() {
                                @Override
                                public void onOk() {
                                    Intent updatePassword = new Intent(requireContext(), UpdatePassBio.class);
                                    startActivity(updatePassword);
                                }
                            });
                }
            }
            else{
                BiometricPrefs prefs = BiometricPrefs.getInstance(requireContext());
                prefs.setCurrentCount(prefs.getCurrentCount() + 1);
                if(prefs.getCurrentCount() < prefs.getMaxAttempt()) {
                    Toast.makeText(requireContext(), "No hardware available", Toast.LENGTH_SHORT).show();
                } else {
                    buttonLoader = new ButtonLoader(requireActivity(), R.drawable.nobiometrics,
                            "Bro taping again and again do not add hardware in device",
                            new ButtonLoader.onUserOkClick() {
                                @Override
                                public void onOk() {
                                    new OnlyLoader(requireActivity(), R.raw.laugh);
                                    buttonLoader.dismisser();
                                }
                            });
                }
            }
        });

        button4.setOnClickListener(v -> {
            VibManager.vibrateTick(requireContext());
            if(BiometricPrefs.getInstance(requireContext()).getPasswordStatus()){
                Intent passwordUpdate = new Intent(getContext(), ChangePassword.class);
                startActivity(passwordUpdate);
            }
            else {
                new OkPopUp(requireActivity(), R.raw.lock_animation, "Bro, set a password first");
            }
        });

        button5.setOnClickListener(v -> {
            VibManager.vibrateTick(requireContext());
            Toast.makeText(requireContext(), "Feature available soon", Toast.LENGTH_SHORT).show();
        });

        button6.setOnClickListener(v -> {
            VibManager.vibrateTick(requireContext());
            ratePlaystore(requireContext());
        });

        button7.setOnClickListener(v -> {
            VibManager.vibrateTick(requireContext());
            developerProfile();
        });

        button8.setOnClickListener(v -> {
            VibManager.vibrateTick(requireContext());
            new ButtonLoader(requireActivity(), R.drawable.block_user,
                    "You are going to delete your account this process can not be reversed and all the data will be cleared",
                    new ButtonLoader.onUserOkClick() {
                        @Override
                        public void onOk() {
                            if(account != null){
                                String email = account.getEmail();
                                loader = new OnlyLoader(requireActivity(), R.raw.loading_animation);
                                deleteUser(email, new ServerCallback() {
                                    @Override
                                    public void onSuccess(boolean isChanged) {
                                        if(isChanged){
                                            signOut();
                                            Toast.makeText(requireContext(), "Account deleted", Toast.LENGTH_SHORT).show();
                                            Intent mainIntnet = new Intent(requireContext(), MainActivity.class);
                                            startActivity(mainIntnet);
                                            requireActivity().finish();
                                        }
                                        else {
                                            new OkPopUp(requireActivity(), R.raw.error_animation, "Error in deleting account");
                                        }
                                    }

                                    @Override
                                    public void onFailure() {
                                        new OkPopUp(requireActivity(), R.raw.error_animation, "Error in deleting account");
                                    }
                                });
                            }
                        }
                    });
        });

        profile.setOnClickListener(v -> {
            Intent profileChange = new Intent(requireContext(), ProfilePage.class);
            profileChange.putExtra("Calling", "Home");
            startActivity(profileChange);
        });

        if (account == null) {
            refreshLayout.setEnabled(false);
            return;
        }

        skelitonUi.startShimmer();
        shimmerProfile.startShimmer();
        skelitonUi.setVisibility(View.VISIBLE);
        shimmerProfile.setVisibility(View.VISIBLE);
        realUi.setVisibility(View.GONE);
        profile.setVisibility(View.GONE);

        String email = account.getEmail();
        setExistingData(email);

        refreshLayout.setOnRefreshListener(() -> {
            VibManager.vibrateTick(requireContext());
            skelitonUi.startShimmer();
            shimmerProfile.startShimmer();
            skelitonUi.setVisibility(View.VISIBLE);
            shimmerProfile.setVisibility(View.VISIBLE);
            realUi.setVisibility(View.GONE);
            profile.setVisibility(View.GONE);

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
                loadProfileData(requireContext(), profileEntity.getProfileLink());
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

    private void startBiometric(Runnable runnable){
        BiometricLogin.authenticte(requireContext(), new BiometricLogin.AuthCallback() {
            @Override
            public void onSuccess() {
                runnable.run();
            }

            @Override
            public void onError(String e) {
                new OkPopUp(requireActivity(), R.raw.error_animation, "We got device error");
            }

            @Override
            public void onFail() {
                new OkPopUp(requireActivity(), R.raw.error_animation, "We got device error");
            }

            @Override
            public void onUsePassword() {

            }
        });
    }

    private void changeBiometricStatus(String email, ServerCallback callback){

        BiometricRequest request = new BiometricRequest(email);

        Call<BiometricResponse> call = endpoints.updateBiometric(BuildConfig.CLIENT_API, request);

        call.enqueue(new Callback<BiometricResponse>() {
            @Override
            public void onResponse(Call<BiometricResponse> call, Response<BiometricResponse> response) {
                if(response.isSuccessful() && response.body() != null){
                    boolean res = response.body().isMessage();
                    callback.onSuccess(res);
                }
                else{
                    TelegramLogs.sendTelegramLog("We got the empty response");
                    callback.onSuccess(false);
                }
            }

            @Override
            public void onFailure(Call<BiometricResponse> call, Throwable t) {
                TelegramLogs.sendTelegramLog("We have server error"+t);
                callback.onFailure();
            }
        });
    }

    private void deleteUser(String email, ServerCallback callback){
        BiometricRequest request = new BiometricRequest(email);

        Call<BiometricResponse> call = endpoints.deleteUser(BuildConfig.CLIENT_API, request);
        call.enqueue(new Callback<BiometricResponse>() {
            @Override
            public void onResponse(Call<BiometricResponse> call, Response<BiometricResponse> response) {
                if(response.isSuccessful() && response.body() != null){
                    callback.onSuccess(response.body().isMessage());
                }
                else {
                    callback.onSuccess(false);
                }
            }

            @Override
            public void onFailure(Call<BiometricResponse> call, Throwable t) {
                TelegramLogs.sendTelegramLog("Error got in server"+t);
                callback.onFailure();
            }
        });
    }

    private void changeButtonDesign(){
        Drawable originalDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.profle_buttons);
        GradientDrawable buttonDrawable = (GradientDrawable) originalDrawable.mutate().getConstantState().newDrawable().mutate();
        button3.setBackground(buttonDrawable);

        if (biometricInfo) {
            bioText.setTextColor(Color.BLACK);
            bioText.setText("Disable Biometric");
            bioIcon.setImageTintList(ContextCompat.getColorStateList(requireContext(), android.R.color.black));
            buttonDrawable.setColor(Color.WHITE);
        } else {
            bioText.setText("Enable Biometric");
            bioText.setTextColor(Color.WHITE);
            bioIcon.setImageTintList(ContextCompat.getColorStateList(requireContext(), android.R.color.white));
            buttonDrawable.setColor(ContextCompat.getColor(requireContext(), R.color.extraColor));
        }
    }
    public interface ServerCallback{
        void onSuccess(boolean isChanged);
        void onFailure();
    }

    private void signOut(){
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();

        GoogleSignInClient signInClient = GoogleSignIn.getClient(requireContext(), gso);

        signInClient.signOut()
                .addOnCompleteListener(task -> {
                    Intent signout = new Intent(requireContext(), TermsAndCondition.class);
                    startActivity(signout);
                    requireActivity().finish();
                });
    }

    private void ratePlaystore(Context context){
        try{
            context.startActivity(
                    new Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("market://details?id=" + context.getPackageName())
                    )
            );
        } catch (Exception e){
            context.startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=" + context.getPackageName())));
        }
    }

    private void developerProfile(){
        String url = "https://www.mohammadramiz.in";
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        startActivity(intent);
    }

    private void stopLoadingAndShowData() {
        refreshLayout.setRefreshing(false);
        skelitonUi.stopShimmer();
        shimmerProfile.stopShimmer();
        skelitonUi.setVisibility(View.GONE);
        shimmerProfile.setVisibility(View.GONE);
        realUi.setVisibility(View.VISIBLE);
        profile.setVisibility(View.VISIBLE);
    }

    private void loadProfileData(Context context, String url){
        Glide.with(context)
                .load(url)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(profile);
    }
}