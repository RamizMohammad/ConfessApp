package in.mohammad.ramiz.confess.ui.activity;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.ActivityEvents;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.model.Notification;
import in.mohammad.ramiz.confess.data.repo.PostRepository;
import in.mohammad.ramiz.confess.databinding.ActivityNotificationsBinding;
import in.mohammad.ramiz.confess.ui.base.BaseActivity;
import in.mohammad.ramiz.confess.ui.nav.Nav;
import in.mohammad.ramiz.confess.ui.util.EdgeInsets;
import in.mohammad.ramiz.confess.ui.util.Motion;

/**
 * S10 · Whispers & Echoes. Old: NotificationPage (a local list of received pushes).
 * Now the real activity from the server: who felt or responded, with read state.
 *
 *  - Everyone / Following (only activity by people I follow) · All / Felt / Responses / Follows filter
 *  - tap → marks it read and opens the confession (at the responses for a response)
 *  - "Mark all read"; leaving the screen also marks everything seen as read,
 *    so the bell dot clears
 */
public class NotificationsActivity extends BaseActivity implements NotificationAdapter.Listener {

    private static final int PREFETCH = 4;

    private ActivityNotificationsBinding b;
    private NotificationsViewModel vm;
    private NotificationAdapter adapter;
    private LinearLayoutManager layout;
    @Nullable private NotificationsViewModel.State shown;
    private long lastPing = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityNotificationsBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeInsets.padTop(b.topBar);
        EdgeInsets.padBottom(b.list, false);

        vm = new ViewModelProvider(this).get(NotificationsViewModel.class);
        adapter = new NotificationAdapter(this);
        layout = new LinearLayoutManager(this);
        b.list.setLayoutManager(layout);
        b.list.setAdapter(adapter);
        b.list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy > 0) maybeLoadMore();
            }
        });

        b.refresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.amber));
        b.refresh.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(this, R.color.surface_high));
        b.refresh.setOnRefreshListener(vm::refresh);

        b.back.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        b.markAll.setOnClickListener(v -> {
            Haptics.tick(this);
            vm.markAllRead(true);
        });
        b.filters.setOnCheckedStateChangeListener((group, ids) -> {
            int id = ids.isEmpty() ? R.id.filterAll : ids.get(0);
            Haptics.tick(this);
            vm.setFilter(id == R.id.filterFelt ? NotificationsViewModel.Filter.FELT
                    : id == R.id.filterResponses ? NotificationsViewModel.Filter.RESPONSES
                    : id == R.id.filterFollows ? NotificationsViewModel.Filter.FOLLOWS
                    : NotificationsViewModel.Filter.ALL);
        });
        b.scope.scopeAll.setOnClickListener(v -> selectScope(PostRepository.SCOPE_ALL));
        b.scope.scopeFollowing.setOnClickListener(v -> selectScope(PostRepository.SCOPE_FOLLOWING));
        b.settings.setOnClickListener(v -> Nav.notificationSettings(this));
        showScope();
        b.stateAction.setOnClickListener(v -> vm.refresh());

        vm.state().observe(this, this::render);
        vm.errors().observe(this, e -> {
            ApiException err = e == null ? null : e.consume();
            if (err != null) Snackbar.make(b.getRoot(), err.userMessage(this), Snackbar.LENGTH_SHORT).show();
        });
        // A push arrived while we're open → pull the newest.
        ActivityEvents.pings().observe(this, t -> {
            if (t == null) return;
            if (lastPing >= 0 && t != lastPing) vm.refresh();
            lastPing = t;
        });

        vm.start();
    }

    private boolean listEntered = false;

    private void selectScope(String next) {
        if (next.equals(vm.scope())) return;
        Haptics.tick(this);
        vm.setScope(next);
        listEntered = false;
        showScope();
    }

    private void showScope() {
        boolean everyone = PostRepository.SCOPE_ALL.equals(vm.scope());
        b.scope.scopeAll.setSelected(everyone);
        b.scope.scopeFollowing.setSelected(!everyone);
    }

    private void render(@Nullable NotificationsViewModel.State s) {
        if (s == null) return;
        shown = s;
        boolean loading = s.screen == NotificationsViewModel.Screen.LOADING;
        b.skeleton.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) b.skeleton.startShimmer(); else b.skeleton.stopShimmer();
        b.refresh.setRefreshing(s.refreshing);
        b.refresh.setEnabled(!loading);

        b.titleDot.setVisibility(s.anyUnread ? View.VISIBLE : View.GONE);
        b.markAll.setVisibility(s.anyUnread ? View.VISIBLE : View.GONE);

        boolean filteredEmpty = s.screen == NotificationsViewModel.Screen.CONTENT && s.items.isEmpty()
                && s.footer == NotificationsViewModel.Footer.END;
        boolean following = PostRepository.SCOPE_FOLLOWING.equals(vm.scope());
        if (following && (s.screen == NotificationsViewModel.Screen.EMPTY || filteredEmpty)) {
            showState(R.drawable.ic_user, getString(R.string.notif_following_empty_title),
                    getString(R.string.notif_following_empty_body), false);
        } else if (s.screen == NotificationsViewModel.Screen.EMPTY || filteredEmpty) {
            showState(R.drawable.ic_mail_open, getString(R.string.notif_empty_title),
                    getString(filteredEmpty ? R.string.notif_empty_filtered : R.string.notif_empty_body), false);
        } else if (s.screen == NotificationsViewModel.Screen.ERROR) {
            boolean offline = s.error != null && s.error.isOffline();
            showState(offline ? R.drawable.ic_wifi_off : R.drawable.ic_alert, getString(R.string.feed_error_title),
                    s.error != null ? s.error.userMessage(this) : getString(R.string.error_generic), true);
        } else {
            b.state.setVisibility(View.GONE);
        }
        b.list.setVisibility(s.screen == NotificationsViewModel.Screen.CONTENT && !filteredEmpty
                ? View.VISIBLE : View.INVISIBLE);

        adapter.submit(this, s.items, s.footer, () -> {
            if (!listEntered && s.screen == NotificationsViewModel.Screen.CONTENT && !s.items.isEmpty()) {
                listEntered = true;
                Motion.enterList(b.list);
            }
            maybeLoadMore();
        });
    }

    private void showState(int icon, String title, String body, boolean retry) {
        b.state.setVisibility(View.VISIBLE);
        b.stateIcon.setImageResource(icon);
        b.stateTitle.setText(title);
        b.stateBody.setText(body);
        b.stateAction.setVisibility(retry ? View.VISIBLE : View.GONE);
    }

    private void maybeLoadMore() {
        if (shown == null || shown.screen != NotificationsViewModel.Screen.CONTENT) return;
        int last = layout.findLastVisibleItemPosition();
        if (last == RecyclerView.NO_POSITION || last >= adapter.getItemCount() - PREFETCH) vm.loadMore();
    }

    @Override
    public void onOpen(@NonNull Notification n) {
        vm.markRead(n);
        if (Notification.TYPE_FOLLOW.equals(n.type)) {
            if (n.actor != null && n.actor.alias != null) Nav.profile(this, n.actor.alias);
        } else if (n.postId != null) {
            Nav.post(this, n.postId, Notification.TYPE_COMMENT.equals(n.type));
        }
    }

    @Override
    public void onRetryMore() {
        vm.retryMore();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isFinishing()) vm.markAllRead(false);      // seen it all → bell clears
    }
}
