package in.mohammad.ramiz.confess.ui.people;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.model.Person;
import in.mohammad.ramiz.confess.data.model.PersonPage;
import in.mohammad.ramiz.confess.data.repo.UserRepository;
import in.mohammad.ramiz.confess.databinding.ActivityPeopleBinding;
import in.mohammad.ramiz.confess.databinding.ItemFollowPersonBinding;
import in.mohammad.ramiz.confess.ui.base.BaseActivity;
import in.mohammad.ramiz.confess.ui.util.EdgeInsets;
import retrofit2.Call;

/** Settings → Blocked people. Newest first, paged; Unblock on each row. */
public class BlockedActivity extends BaseActivity {

    private static final int PREFETCH = 5;

    private ActivityPeopleBinding b;
    private final UserRepository users = new UserRepository();
    private final List<Person> people = new ArrayList<>();
    private final Adapter adapter = new Adapter();
    private LinearLayoutManager layout;
    @Nullable private String cursor;
    private boolean loaded, ended;
    @Nullable private Call<PersonPage> call;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityPeopleBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeInsets.padTop(b.topBar);
        EdgeInsets.padBottom(b.list, false);

        b.back.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        b.title.setText(R.string.blocked_title);
        b.summary.setText(R.string.blocked_summary);
        b.stateTitle.setText(R.string.blocked_empty);
        b.stateBody.setText(R.string.blocked_empty_body);

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

    private void reload() {
        if (call != null) call.cancel();
        cursor = null;
        ended = false;
        call = users.blocked(null, new ApiCallback<PersonPage>() {
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
                Snackbar.make(b.root, e.userMessage(BlockedActivity.this), Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void loadMore() {
        if (!loaded || ended || call != null || cursor == null) return;
        call = users.blocked(cursor, new ApiCallback<PersonPage>() {
            @Override public void onSuccess(PersonPage page) {
                call = null;
                for (Person p : page.users) if (indexOf(p.alias) < 0) people.add(p);
                cursor = page.nextCursor;
                ended = cursor == null;
                publish();
            }
            @Override public void onError(ApiException e) { call = null; }
        });
    }

    private void publish() {
        b.refresh.setRefreshing(false);
        b.loading.setVisibility(View.GONE);
        b.state.setVisibility(people.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
    }

    private void unblock(@NonNull Person p) {
        int i = indexOf(p.alias);
        if (i < 0) return;
        Haptics.tick(this);
        people.remove(i);
        publish();
        users.setBlocked(p.alias, false, new ApiCallback<JsonObject>() {
            @Override public void onSuccess(JsonObject r) {
                Snackbar.make(b.root, R.string.unblocked_toast, Snackbar.LENGTH_SHORT).show();
            }
            @Override public void onError(ApiException e) {
                if (indexOf(p.alias) < 0) people.add(Math.min(i, people.size()), p);
                publish();
                Snackbar.make(b.root, e.userMessage(BlockedActivity.this), Snackbar.LENGTH_SHORT).show();
            }
        });
    }

    private int indexOf(String alias) {
        for (int i = 0; i < people.size(); i++) if (people.get(i).alias.equalsIgnoreCase(alias)) return i;
        return -1;
    }

    @Override
    protected void onDestroy() {
        if (call != null) call.cancel();
        super.onDestroy();
    }

    private final class Adapter extends RecyclerView.Adapter<Holder> {
        @Override public int getItemCount() { return people.size(); }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemFollowPersonBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            Person p = people.get(position);
            h.b.avatar.setAvatar(p.avatar);
            h.b.alias.setText("@" + p.alias);
            h.b.about.setVisibility(View.GONE);
            h.b.followingBtn.setVisibility(View.GONE);
            h.b.followBtn.setVisibility(View.VISIBLE);
            h.b.followBtn.setText(R.string.unblock_btn);
            h.b.followBtn.setOnClickListener(v -> unblock(p));
            h.b.row.setOnClickListener(null);
            h.b.row.setClickable(false);
        }
    }

    private static final class Holder extends RecyclerView.ViewHolder {
        final ItemFollowPersonBinding b;

        Holder(ItemFollowPersonBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
