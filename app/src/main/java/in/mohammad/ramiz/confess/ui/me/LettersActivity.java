package in.mohammad.ramiz.confess.ui.me;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.databinding.ActivityProfileBinding;
import in.mohammad.ramiz.confess.ui.base.BaseActivity;
import in.mohammad.ramiz.confess.ui.nav.Nav;
import in.mohammad.ramiz.confess.ui.profile.ProfileScreen;
import in.mohammad.ramiz.confess.ui.profile.ProfileViewModel;
import in.mohammad.ramiz.confess.ui.util.EdgeInsets;

/** All my letters as paper cards (felt, open, delete). Old: YourConfession. */
public class LettersActivity extends BaseActivity implements ProfileScreen.Host {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityProfileBinding b = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeInsets.padTop(b.profile.topBar);
        EdgeInsets.padBottom(b.profile.list, false);

        b.profile.back.setVisibility(View.VISIBLE);
        b.profile.back.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        b.profile.tag.setText(R.string.letters_title);
        b.profile.list.setPadding(0, getResources().getDimensionPixelSize(R.dimen.space_sm), 0, 0);

        ProfileViewModel vm = new ViewModelProvider(this).get(ProfileViewModel.class);
        vm.start(null);
        new ProfileScreen(this, b.profile, this, vm, getSupportFragmentManager(), this, false);
    }

    @Override
    public void onSettings(@NonNull View anchor) {
        Nav.settings(this);
    }

    @Override
    public void onEditProfile() {
        Nav.editProfile(this);
    }
}
