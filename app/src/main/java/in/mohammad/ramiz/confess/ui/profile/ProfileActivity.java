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

        screen = new ProfileScreen(this, b.profile, this, vm, getSupportFragmentManager(), this);
        this.vm = vm;
        this.alias = alias;
        if (!vm.isMe()) {
            // Someone else: the top-bar button is their flag menu (report · block).
            b.profile.action.setVisibility(View.VISIBLE);
            b.profile.action.setIconResource(R.drawable.ic_flag);
            b.profile.action.setContentDescription(getString(R.string.cd_profile_more));
        }
    }

    private ProfileScreen screen;
    private ProfileViewModel vm;
    private String alias;

    @Override
    public void onSettings(@NonNull View anchor) {
        if (vm != null && !vm.isMe()) {
            screen.personMenu(anchor, alias);
            return;
        }
        Nav.settings(this);
    }

    @Override
    public void onEditProfile() {
        Nav.editProfile(this);
    }
}
