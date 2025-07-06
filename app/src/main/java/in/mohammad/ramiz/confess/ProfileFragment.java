package in.mohammad.ramiz.confess;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

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
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;

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
    private TextView aliasName, about, bioText;
    private ImageView bioIcon;
    private ShimmerFrameLayout skelitonUi;
    private LinearLayout realUi, signOut;
    private GoogleSignInAccount account;
    private SwipeRefreshLayout refreshLayout;
    private Endpoints endpoints;
    private OnlyLoader loader;
    private boolean biometricInfo;
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
        signOut = view.findViewById(R.id.signOutButton);
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
        endpoints = ServerConfigs.getInstance().create(Endpoints.class);
        viewmodel = new ViewModelProvider(this).get(ProfileViewmodel.class);

        biometricInfo = BiometricPrefs.getInstance(requireContext()).isBiometricEnabled();
        changeButtonDesign();

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

        button1.setOnClickListener(v -> VibManager.vibrateTick(requireContext()));

        button2.setOnClickListener(v -> {
            VibManager.vibrateTick(requireContext());
            Intent updateIntent = new Intent(getContext(), UpdateAbout.class);
            startActivity(updateIntent);
        });

        button3.setOnClickListener(v ->{
            VibManager.vibrateTick(requireContext());
            if(biometricInfo){
                new ButtonLoader(getActivity(), R.drawable.biometric,
                        "Do you want to disable biometric login",
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
            else {
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
            }
        });

        button4.setOnClickListener(v -> {
            VibManager.vibrateTick(requireContext());
            ratePlaystore(requireContext());
        });

        button5.setOnClickListener(v -> {
            VibManager.vibrateTick(requireContext());
            developerProfile();
        });

        button6.setOnClickListener(v -> {
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
            bioIcon.setImageTintList(ContextCompat.getColorStateList(requireContext(), android.R.color.black));
            buttonDrawable.setColor(Color.WHITE);
        } else {
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
        skelitonUi.setVisibility(View.GONE);
        realUi.setVisibility(View.VISIBLE);
    }
}
