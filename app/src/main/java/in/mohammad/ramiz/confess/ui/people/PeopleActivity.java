package in.mohammad.ramiz.confess.ui.people;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.FollowEvents;
import in.mohammad.ramiz.confess.core.Formats;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.model.FollowState;
import in.mohammad.ramiz.confess.data.model.Me;
import in.mohammad.ramiz.confess.data.model.Person;
import in.mohammad.ramiz.confess.data.model.PersonPage;
import in.mohammad.ramiz.confess.data.repo.UserRepository;
import in.mohammad.ramiz.confess.data.session.SessionManager;
import in.mohammad.ramiz.confess.databinding.ActivityPeopleBinding;
import in.mohammad.ramiz.confess.ui.base.BaseActivity;
import in.mohammad.ramiz.confess.ui.nav.Nav;
import in.mohammad.ramiz.confess.ui.util.EdgeInsets;
import in.mohammad.ramiz.confess.ui.util.Motion;
import retrofit2.Call;

/**
 * Me → Followers / Following. Newest first, paged; Follow / Following on each row
 * (no confirmation here — tapping again undoes it). Tap a row for their profile.
 */
public class PeopleActivity extends BaseActivity implements PeopleAdapter.Listener {

    public static final String EXTRA_FOLLOWERS = "followers";
    private static final int PREFETCH = 5;

    private ActivityPeopleBinding b;
    private final UserRepository users = new UserRepository();
    private PeopleAdapter adapter;
    private LinearLayoutManager layout;
    private boolean followers;

    private final List<Person> people = new ArrayList<>();
    @Nullable private String cursor;
    private boolean loaded, ended, entered;
    @Nullable private Call<PersonPage> call;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityPeopleBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeInsets.padTop(b.topBar);
        EdgeInsets.padBottom(b.list, false);
        followers = getIntent().getBooleanExtra(EXTRA_FOLLOWERS, false);

        b.back.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        b.title.setText(followers ? R.string.people_followers_title : R.string.people_following_title);
        b.stateTitle.setText(followers ? R.string.people_followers_empty : R.string.people_following_empty);
        b.stateBody.setText(followers ? R.string.people_followers_empty_body : R.string.people_following_empty_body);
        showSummary(SessionManager.get().cachedMe());

        adapter = new PeopleAdapter(this);
        layout = new LinearLayoutManager(this);
        b.list.setLayoutManager(layout);
        b.list.setAdapter(adapter);
        b.list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy > 0 && layout.findLastVisibleItemPosition() >= people.size() - PREFETCH) loadMore();
            }
        });
        b.refresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.amber));
        b.refresh.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(this, R.color.surface_high));
        b.refresh.setOnRefreshListener(this::reload);

        b.loading.setVisibility(View.VISIBLE);
        reload();
    }

    private void showSummary(@Nullable Me me) {
        if (me == null) return;
        int n = followers ? me.followers : me.following;
        b.summary.setText(Formats.count(n) + " " + getString(followers ? R.string.followers_label : R.string.following_label));
    }

    private void reload() {
        if (call != null) call.cancel();
        cursor = null;
        ended = false;
        call = users.people(followers, null, new ApiCallback<PersonPage>() {
            @Override public void onSuccess(PersonPage page) {
                call = null;
                people.clear();
                people.addAll(page.users);
                cursor = page.nextCursor;
                ended = cursor == null;
                loaded = true;
                publish();
            }
            @Override public void onError(ApiException e) {
                call = null;
                b.refresh.setRefreshing(false);
                b.loading.setVisibility(View.GONE);
                Snackbar.make(b.root, e.userMessage(PeopleActivity.this), Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void loadMore() {
        if (!loaded || ended || call != null || cursor == null) return;
        call = users.people(followers, cursor, new ApiCallback<PersonPage>() {
            @Override public void onSuccess(PersonPage page) {
                call = null;
                for (Person p : page.users) if (indexOf(p.alias) < 0) people.add(p);
                cursor = page.nextCursor;
                ended = cursor == null;
                publish();
            }
            @Override public void onError(ApiException e) {
                call = null;
            }
        });
    }

    private void publish() {
        b.refresh.setRefreshing(false);
        b.loading.setVisibility(View.GONE);
        b.state.setVisibility(people.isEmpty() ? View.VISIBLE : View.GONE);
        boolean first = !entered && !people.isEmpty();
        adapter.submitList(copy(people), () -> {
            if (first) {
                entered = true;
                Motion.enterList(b.list);
            }
        });
    }

    @Override
    public void onPerson(@NonNull Person p) {
        Nav.profile(this, p.alias);
    }

    @Override
    public void onFollow(@NonNull Person p) {
        int i = indexOf(p.alias);
        if (i < 0) return;
        Haptics.tick(this);
        boolean next = !people.get(i).followedByMe;
        set(i, next);
        users.setFollowing(p.alias, next, new ApiCallback<FollowState>() {
            @Override public void onSuccess(FollowState s) {
                FollowEvents.changed(p.alias, s.following, s.followers);
            }
            @Override public void onError(ApiException e) {
                int j = indexOf(p.alias);
                if (j >= 0) set(j, !next);
                Snackbar.make(b.root, e.userMessage(PeopleActivity.this), Snackbar.LENGTH_SHORT).show();
            }
        });
    }

    private void set(int i, boolean followed) {
        Person old = people.get(i);
        Person n = new Person();
        n.alias = old.alias;
        n.about = old.about;
        n.avatar = old.avatar;
        n.followedAt = old.followedAt;
        n.followedByMe = followed;
        people.set(i, n);
        adapter.submitList(copy(people));
    }

    private int indexOf(String alias) {
        for (int i = 0; i < people.size(); i++) if (people.get(i).alias.equalsIgnoreCase(alias)) return i;
        return -1;
    }

    private static List<Person> copy(List<Person> l) {
        return new ArrayList<>(l);
    }

    @Override
    protected void onDestroy() {
        if (call != null) call.cancel();
        super.onDestroy();
    }
}
