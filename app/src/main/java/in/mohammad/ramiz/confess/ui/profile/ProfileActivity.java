package in.mohammad.ramiz.confess.ui.profile;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.databinding.ActivityProfileBinding;
import in.mohammad.ramiz.confess.ui.base.BaseActivity;
import in.mohammad.ramiz.confess.ui.nav.Nav;
import in.mohammad.ramiz.confess.ui.util.EdgeInsets;

/**
 * Someone's profile: aura, alias, about, member since, letter count and their
 * letters. Old: User_LookUp. Opened by tapping a writer (feed, confession, responses)
 * or a search result. Opening my own alias shows my profile.
 */
public class ProfileActivity extends BaseActivity implements ProfileScreen.Host {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityProfileBinding b = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeInsets.padTop(b.profile.topBar);
        EdgeInsets.padBottom(b.profile.list, false);

        String alias = getIntent().getStringExtra(Nav.EXTRA_ALIAS);
        if (alias == null) { finish(); return; }

        ProfileViewModel vm = new ViewModelProvider(this).get(ProfileViewModel.class);
        vm.start(alias);

        b.profile.back.setVisibility(View.VISIBLE);
        b.profile.back.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        b.profile.tag.setText(vm.isMe() ? R.string.profile_mine_tag : R.string.profile_tag);

        new ProfileScreen(this, b.profile, this, vm, getSupportFragmentManager(), this);
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
