package in.mohammad.ramiz.confess.ui.profile;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.data.model.Post;
import in.mohammad.ramiz.confess.databinding.ViewProfileBinding;
import in.mohammad.ramiz.confess.ui.feed.PostMenu;
import in.mohammad.ramiz.confess.ui.feed.ReportSheet;
import in.mohammad.ramiz.confess.ui.nav.Nav;
import in.mohammad.ramiz.confess.ui.util.Motion;

/**
 * Wires view_profile to a {@link ProfileViewModel}. Used by the Me tab and by
 * {@link ProfileActivity}, so both behave the same.
 */
public final class ProfileScreen implements ProfileAdapter.Listener {

    /** What the host screen decides. */
    public interface Host {
        /** Me tab: the gear button. */
        void onSettings(@NonNull View anchor);

        /** Edit aura / about (Section 10). */
        void onEditProfile();
    }

    private static final int PREFETCH = 3;

    private final Context ctx;
    private final ViewProfileBinding b;
    private final ProfileViewModel vm;
    private final FragmentManager fm;
    private final Host host;
    private final ProfileAdapter adapter;
    private final LinearLayoutManager layout;
    @Nullable private ProfileViewModel.State shown;

    public ProfileScreen(@NonNull Context ctx, @NonNull ViewProfileBinding b, @NonNull LifecycleOwner owner,
                         @NonNull ProfileViewModel vm, @NonNull FragmentManager fm, @NonNull Host host) {
        this(ctx, b, owner, vm, fm, host, true);
    }

    public ProfileScreen(@NonNull Context ctx, @NonNull ViewProfileBinding b, @NonNull LifecycleOwner owner,
                         @NonNull ProfileViewModel vm, @NonNull FragmentManager fm, @NonNull Host host,
                         boolean withHeader) {
        this.ctx = ctx;
        this.b = b;
        this.vm = vm;
        this.fm = fm;
        this.host = host;

        adapter = new ProfileAdapter(this, withHeader);
        layout = new LinearLayoutManager(ctx);
        b.list.setLayoutManager(layout);
        b.list.setAdapter(adapter);
        b.list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy > 0) maybeLoadMore();
            }
        });
        b.refresh.setColorSchemeColors(ContextCompat.getColor(ctx, R.color.amber));
        b.refresh.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(ctx, R.color.surface_high));
        b.refresh.setOnRefreshListener(vm::refresh);
        b.action.setOnClickListener(host::onSettings);
        b.stateAction.setOnClickListener(v -> vm.refresh());

        vm.state().observe(owner, this::render);
        vm.messages().observe(owner, e -> {
            ProfileViewModel.Message m = e == null ? null : e.consume();
            if (m == null) return;
            String text = m.error != null ? m.error.userMessage(ctx) : ctx.getString(m.res);
            Snackbar.make(b.getRoot(), text, Snackbar.LENGTH_SHORT).show();
        });
        fm.setFragmentResultListener(ReportSheet.RESULT, owner, (key, result) ->
                ReportSheet.deliver(ctx, result, new ReportSheet.Outcome() {
                    @Override public void reportPost(@NonNull String postId, @NonNull String reason) { vm.report(postId, reason); }
                    @Override public void say(@NonNull String text) {
                        Snackbar.make(b.getRoot(), text, Snackbar.LENGTH_SHORT).show();
                    }
                }));
    }

    private boolean listEntered = false;

    private void render(@Nullable ProfileViewModel.State s) {
        if (s == null) return;
        shown = s;
        boolean loading = s.screen == ProfileViewModel.Screen.LOADING;
        boolean content = s.screen == ProfileViewModel.Screen.CONTENT && s.header != null;
        b.skeleton.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) b.skeleton.startShimmer(); else b.skeleton.stopShimmer();
        b.refresh.setRefreshing(s.refreshing);
        b.refresh.setEnabled(content);
        b.list.setVisibility(content ? View.VISIBLE : View.INVISIBLE);

        if (s.screen == ProfileViewModel.Screen.GONE) {
            showState(R.drawable.ic_user, ctx.getString(R.string.profile_gone_title),
                    ctx.getString(R.string.profile_gone_body), false);
        } else if (s.screen == ProfileViewModel.Screen.ERROR) {
            boolean offline = s.error != null && s.error.isOffline();
            showState(offline ? R.drawable.ic_wifi_off : R.drawable.ic_alert, ctx.getString(R.string.feed_error_title),
                    s.error != null ? s.error.userMessage(ctx) : ctx.getString(R.string.error_generic), true);
        } else {
            b.state.setVisibility(View.GONE);
        }
        adapter.submit(s, () -> {
            if (!listEntered && s.screen == ProfileViewModel.Screen.CONTENT) {
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
        b.stateAction.setText(R.string.action_retry);
    }

    private void maybeLoadMore() {
        if (shown == null || shown.screen != ProfileViewModel.Screen.CONTENT) return;
        int last = layout.findLastVisibleItemPosition();
        if (last == RecyclerView.NO_POSITION || last >= adapter.getItemCount() - PREFETCH) vm.loadMore();
    }

    public void scrollToTop() {
        b.list.smoothScrollToPosition(0);
    }

    // ── Adapter callbacks ────────────────────────────────────────────────────

    @Override
    public void onOpen(@NonNull Post post, boolean toResponses) {
        Nav.post(ctx, post.postId, toResponses);
    }

    @Override
    public void onFelt(@NonNull Post post) {
        Haptics.tick(ctx);
        vm.toggleFelt(post);
    }

    @Override
    public void onReact(@NonNull Post post, @NonNull String reaction) {
        vm.react(post, reaction);
    }

    @Override
    public void onVote(@NonNull Post post, int option) {
        vm.vote(post, option);
    }

    @Override
    public void onMore(@NonNull Post post, @NonNull View anchor) {
        PostMenu.show(anchor, post, new PostMenu.Listener() {
            @Override public void onDelete(@NonNull Post p) {
                new MaterialAlertDialogBuilder(ctx)
                        .setTitle(R.string.delete_title)
                        .setMessage(R.string.delete_body)
                        .setNegativeButton(R.string.action_cancel, null)
                        .setPositiveButton(R.string.action_delete, (d, w) -> vm.delete(p))
                        .show();
            }
            @Override public void onReportPost(@NonNull Post p) { ReportSheet.showIn(fm, p.postId); }
            @Override public void onReportUser(@NonNull Post p, @NonNull String alias) { ReportSheet.showUser(fm, alias); }
        });
    }

    @Override
    public void onUnblock(@NonNull String alias) {
        Haptics.tick(ctx);
        vm.unblock();
    }

    /** Someone else's profile: the flag in the top bar → Report / Block / Unblock. */
    public void personMenu(@NonNull View anchor, @NonNull String alias) {
        PostMenu.showPerson(anchor, alias, vm.blocked(), new PostMenu.PersonListener() {
            @Override public void onReportUser(@NonNull String a) { ReportSheet.showUser(fm, a); }
            @Override public void onBlock(@NonNull String a) { in.mohammad.ramiz.confess.ui.people.BlockFlow.confirm(ctx, a, null); }
            @Override public void onUnblock(@NonNull String a) { vm.unblock(); }
        });
    }

    @Override
    public void onEditAura() {
        host.onEditProfile();
    }

    @Override
    public void onEditAbout() {
        host.onEditProfile();
    }

    @Override
    public void onEditBanner() {
        BannerSheet.show(ctx, in.mohammad.ramiz.confess.data.session.SessionManager.get().cachedMe(), spec -> vm.refresh());
    }

    @Override
    public void onRetryMore() {
        vm.retryMore();
    }

    /** Follow at once; unfollowing asks first (it's easy to tap by accident). */
    @Override
    public void onFollow(boolean currentlyFollowing, @NonNull String alias) {
        Haptics.tick(ctx);
        if (!currentlyFollowing) {
            vm.toggleFollow();
            return;
        }
        new MaterialAlertDialogBuilder(ctx)
                .setTitle(ctx.getString(R.string.unfollow_title, alias))
                .setMessage(R.string.unfollow_body)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.unfollow_confirm, (d, w) -> vm.toggleFollow())
                .show();
    }
}
