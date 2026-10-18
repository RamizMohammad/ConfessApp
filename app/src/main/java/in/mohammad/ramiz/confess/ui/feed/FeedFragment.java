package in.mohammad.ramiz.confess.ui.feed;

import android.os.Bundle;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.HashMap;
import java.util.Map;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.ActivityEvents;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.data.model.Post;
import in.mohammad.ramiz.confess.data.model.Me;
import in.mohammad.ramiz.confess.data.repo.PostRepository;
import in.mohammad.ramiz.confess.data.session.SessionManager;
import in.mohammad.ramiz.confess.databinding.FragmentFeedBinding;
import in.mohammad.ramiz.confess.ui.home.HomeActivity;
import in.mohammad.ramiz.confess.ui.nav.Nav;
import in.mohammad.ramiz.confess.ui.util.EdgeInsets;
import in.mohammad.ramiz.confess.ui.util.Motion;
import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.model.StoryTray;
import in.mohammad.ramiz.confess.data.repo.StoryRepository;
import in.mohammad.ramiz.confess.ui.stories.StoryTrayAdapter;
import in.mohammad.ramiz.confess.ui.stories.StoryTrayCache;

/**
 * S7 · Feed tab. Old: HomeFragment.
 *
 *  - wordmark · bell (amber dot when there are unread notifications); search is in the bottom bar
 *  - Latest / Most felt (last 7 days) — each keeps its own list and scroll position
 *  - paper cards: tap → confession, heart → felt (optimistic), ⋯ → delete (mine) / report
 *  - pull to refresh, shimmer on first load, "You're all caught up." at the end
 *  - bursts (one writer's confessions close together) come from the server as one card — a
 *    slider, or a stack card for more than 3 — as the reader chose ({@link Bursts}); their
 *    confessions load a set at a time as you swipe ({@link BurstStore}); the chooser with its
 *    little animations opens the first time a burst shows up
 */
public class FeedFragment extends Fragment implements PostCardBinder.Listener {

    private static final int PREFETCH = 4;

    private FragmentFeedBinding b;
    private FeedViewModel vm;
    private PostAdapter adapter;
    /** Paper tone the cards were last drawn with (Settings can change it). */
    private int toneSeen = -1;
    private LinearLayoutManager layout;
    private String sort = PostRepository.SORT_LATEST;
    /** Everyone (all) or only people I follow — remembered across launches. */
    private String scope = PostRepository.SCOPE_ALL;
    private static final String STATE_SCOPE = "scope";
    private static final String PREF_SCOPE = "feed_scope";
    @Nullable private FeedState shown;
    private final Map<String, Parcelable> scrollBySort = new HashMap<>();
    private final Observer<FeedState> stateObserver = this::render;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentFeedBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        // The feed is always newest-first; Everyone / Following is the only switch.
        scope = state != null ? state.getString(STATE_SCOPE, PostRepository.SCOPE_ALL)
                : requireContext().getSharedPreferences("feed", android.content.Context.MODE_PRIVATE)
                        .getString(PREF_SCOPE, PostRepository.SCOPE_ALL);
        vm = new ViewModelProvider(this).get(FeedViewModel.class);
        EdgeInsets.padTop(b.topBar);

        // List
        adapter = new PostAdapter(this, () -> vm.retryMore(key()));
        adapter.setBursts(Bursts.mode(requireContext()), new PostAdapter.BurstListener() {
            @Override
            public void onOpenBurst(@NonNull Bursts.Group group) {
                BurstReaderActivity.open(requireContext(), group);
            }

            @Override
            public void onBurstShown() {
                askBurstsOnce();
            }
        });
        layout = new LinearLayoutManager(requireContext());
        b.list.setLayoutManager(layout);
        b.list.setAdapter(adapter);
        b.list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy > 0) maybeLoadMore();
            }
        });

        b.refresh.setColorSchemeColors(ContextCompat.getColor(requireContext(), R.color.amber));
        b.refresh.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(requireContext(), R.color.surface_high));
        b.refresh.setOnRefreshListener(() -> {
            vm.refresh(key());
            loadStories();
        });

        // Top bar
        b.wordmark.setOnClickListener(v -> scrollToTop());
        b.bell.setOnClickListener(v -> Nav.notifications(requireContext()));
        ActivityEvents.pings().observe(getViewLifecycleOwner(), t -> vm.refreshUnread());
        vm.unread().observe(getViewLifecycleOwner(), n ->
                b.unreadDot.setVisibility(n != null && n > 0 ? View.VISIBLE : View.GONE));

        // Everyone / Following (pins under the top bar once the stories scroll away)
        setupStories();
        b.scope.scopeAll.setOnClickListener(v -> selectScope(PostRepository.SCOPE_ALL));
        b.scope.scopeFollowing.setOnClickListener(v -> selectScope(PostRepository.SCOPE_FOLLOWING));

        // Messages + report results
        vm.messages().observe(getViewLifecycleOwner(), e -> {
            FeedViewModel.Message m = e == null ? null : e.consume();
            if (m == null) return;
            String text = m.error != null ? m.error.userMessage(requireContext()) : getString(m.res);
            Snackbar.make(b.getRoot(), text, Snackbar.LENGTH_SHORT).show();
        });
        getChildFragmentManager().setFragmentResultListener(ReportSheet.RESULT, getViewLifecycleOwner(), (key, result) ->
                ReportSheet.deliver(requireContext(), result, new ReportSheet.Outcome() {
                    @Override public void reportPost(@NonNull String postId, @NonNull String reason) { vm.report(postId, reason); }
                    @Override public void say(@NonNull String text) {
                        if (b != null) Snackbar.make(b.getRoot(), text, Snackbar.LENGTH_SHORT).show();
                    }
                }));

        showSortTabs();
        adapter.setScope(scope);
        vm.setBursts(Bursts.mode(requireContext()) != Bursts.SEPARATE);
        vm.state(key()).observe(getViewLifecycleOwner(), stateObserver);
        vm.start(key());
    }

    @Override
    public void onResume() {
        super.onResume();
        int tone = in.mohammad.ramiz.confess.ui.components.PaperTone.stamp(requireContext());
        if (toneSeen >= 0 && tone != toneSeen) adapter.notifyDataSetChanged();
        toneSeen = tone;
        int burstMode = Bursts.mode(requireContext());              // Settings may have changed it
        adapter.setBurstMode(burstMode);
        vm.setBursts(burstMode != Bursts.SEPARATE);
        vm.refreshUnread();
        loadStories();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && vm != null) {
            vm.refreshUnread();
            loadStories();
        }
    }

    // ── Bursts ───────────────────────────────────────────────────────────────

    private boolean burstAskPending = false;

    /** The first burst on screen: let the reader pick how bursts look (once). */
    private void askBurstsOnce() {
        if (burstAskPending || b == null || Bursts.asked(requireContext())) return;
        burstAskPending = true;
        b.list.postDelayed(() -> {                               // let the feed settle first
            if (b == null || !isResumed() || Bursts.asked(requireContext())) {
                burstAskPending = false;
                return;
            }
            Bursts.markAsked(requireContext());
            BurstChooser.show(requireContext(), mode -> {
                if (adapter != null) adapter.setBurstMode(mode);
                if (vm != null) vm.setBursts(mode != Bursts.SEPARATE);
            });
        }, 900);
    }

    // ── Sort ─────────────────────────────────────────────────────────────────

    private void selectScope(String next) {
        if (next.equals(scope)) {
            scrollToTop();
            return;
        }
        requireContext().getSharedPreferences("feed", android.content.Context.MODE_PRIVATE)
                .edit().putString(PREF_SCOPE, next).apply();
        switchTo(sort, next);
    }

    private void switchTo(String nextSort, String nextScope) {
        Haptics.tick(requireContext());
        Parcelable pos = layout.onSaveInstanceState();
        if (pos != null) scrollBySort.put(key(), pos);
        vm.state(key()).removeObserver(stateObserver);

        sort = nextSort;
        scope = nextScope;
        adapter.setScope(nextScope);
        shown = null;
        listEntered = false;
        showSortTabs();
        vm.state(key()).observe(getViewLifecycleOwner(), stateObserver);
        vm.start(key());
    }

    // ── Stories ──────────────────────────────────────────────────────────────

    private final StoryRepository stories = new StoryRepository();
    @Nullable private StoryTrayAdapter storyAdapter;
    @Nullable private retrofit2.Call<StoryTray> storyCall;

    private void setupStories() {
        storyAdapter = new StoryTrayAdapter(new StoryTrayAdapter.Listener() {
            @Override public void onOpenStories(int groupIndex) {
                Nav.stories(requireContext(), groupIndex);
            }
            @Override public void onAddStory() {
                Nav.composeStory(requireContext());
            }
        });
        b.stories.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        b.stories.setAdapter(storyAdapter);
        b.stories.setItemAnimator(null);
        // Show what we had (e.g. after the viewer marked some seen) while the fresh tray loads.
        storyAdapter.submit(SessionManager.get().cachedMe(), StoryTrayCache.get());
    }

    /** Reload the tray (on resume, on pull-to-refresh). */
    private void loadStories() {
        if (b == null || storyAdapter == null) return;
        storyAdapter.submit(SessionManager.get().cachedMe(), StoryTrayCache.get());
        if (storyCall != null) storyCall.cancel();
        storyCall = stories.tray(new ApiCallback<StoryTray>() {
            @Override public void onSuccess(StoryTray tray) {
                storyCall = null;
                StoryTrayCache.set(tray.people);
                if (b != null && storyAdapter != null) {
                    storyAdapter.submit(SessionManager.get().cachedMe(), tray.people);
                }
            }
            @Override public void onError(ApiException e) {
                storyCall = null;                     // the feed itself shows connection problems
            }
        });
    }

    /** ViewModel tab for the current sort + scope. */
    private String key() {
        return FeedViewModel.key(sort, scope);
    }

    private void showSortTabs() {
        boolean everyone = PostRepository.SCOPE_ALL.equals(scope);
        b.scope.scopeAll.setSelected(everyone);
        b.scope.scopeFollowing.setSelected(!everyone);
    }

    // ── Render ───────────────────────────────────────────────────────────────

    /** The first page eases in once; later updates don't animate. */
    private boolean listEntered = false;

    private void render(@Nullable FeedState s) {
        if (s == null || b == null) return;
        boolean firstForSort = shown == null;
        shown = s;

        boolean loading = s.screen == FeedState.Screen.LOADING;
        b.skeleton.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) b.skeleton.startShimmer(); else b.skeleton.stopShimmer();

        b.refresh.setRefreshing(s.refreshing);
        b.refresh.setEnabled(s.screen != FeedState.Screen.LOADING);
        b.list.setVisibility(s.screen == FeedState.Screen.CONTENT ? View.VISIBLE : View.INVISIBLE);

        switch (s.screen) {
            case EMPTY: showEmpty(); break;
            case ERROR: showError(s); break;
            default: b.state.setVisibility(View.GONE);
        }

        adapter.submit(s.posts, s.footer, () -> {
            if (b == null) return;
            if (firstForSort) {
                Parcelable saved = scrollBySort.remove(key());
                if (saved != null) layout.onRestoreInstanceState(saved);
                else layout.scrollToPosition(0);
            }
            if (!listEntered && s.screen == FeedState.Screen.CONTENT && !s.posts.isEmpty()) {
                listEntered = true;
                Motion.enterList(b.list);
            }
            maybeLoadMore();              // short page that doesn't fill the screen
        });
    }

    private void showEmpty() {
        boolean latest = PostRepository.SORT_LATEST.equals(sort);
        b.state.setVisibility(View.VISIBLE);
        if (PostRepository.SCOPE_FOLLOWING.equals(scope)) {
            Me me = SessionManager.get().cachedMe();
            boolean followsNobody = me == null || me.following == 0;
            b.stateIcon.setImageResource(R.drawable.ic_user);
            b.stateTitle.setText(followsNobody ? R.string.feed_following_none_title : R.string.feed_following_empty_title);
            b.stateBody.setText(followsNobody ? R.string.feed_following_none_body : R.string.feed_following_empty_body);
            b.stateAction.setText(R.string.feed_following_find);
            b.stateAction.setIconResource(R.drawable.ic_search);
            b.stateAction.setOnClickListener(v -> Nav.search(requireContext()));
            return;
        }
        b.stateIcon.setImageResource(R.drawable.ic_mail_open);
        b.stateTitle.setText(latest ? R.string.feed_empty_title : R.string.feed_empty_top);
        b.stateBody.setText(R.string.feed_empty_body);
        b.stateAction.setText(R.string.feed_empty_action);
        b.stateAction.setIconResource(R.drawable.ic_compose);
        b.stateAction.setOnClickListener(v -> {
            if (requireActivity() instanceof HomeActivity) ((HomeActivity) requireActivity()).openComposer();
            else Nav.compose(requireContext());
        });
    }

    private void showError(FeedState s) {
        b.state.setVisibility(View.VISIBLE);
        boolean offline = s.error != null && s.error.isOffline();
        b.stateIcon.setImageResource(offline ? R.drawable.ic_wifi_off : R.drawable.ic_alert);
        b.stateTitle.setText(R.string.feed_error_title);
        b.stateBody.setText(s.error != null ? s.error.userMessage(requireContext()) : getString(R.string.error_generic));
        b.stateAction.setText(R.string.action_retry);
        b.stateAction.setIconResource(R.drawable.ic_refresh);
        b.stateAction.setOnClickListener(v -> vm.refresh(key()));
    }

    private void maybeLoadMore() {
        if (shown == null || shown.screen != FeedState.Screen.CONTENT) return;
        int last = layout.findLastVisibleItemPosition();
        if (last != RecyclerView.NO_POSITION && last >= adapter.getItemCount() - PREFETCH) vm.loadMore(key());
    }

    /** Just released from the composer: show Latest from the top (the new one is first). */
    public void showNewConfession() {
        if (b == null) return;
        if (!PostRepository.SORT_LATEST.equals(sort) || !PostRepository.SCOPE_ALL.equals(scope)) {
            scrollBySort.remove(PostRepository.SORT_LATEST);
            switchTo(PostRepository.SORT_LATEST, PostRepository.SCOPE_ALL);
        }
        b.appBar.setExpanded(true, false);
        b.list.scrollToPosition(0);
        // The new card is inserted by an async diff; follow it up once it lands.
        b.list.postDelayed(() -> { if (b != null) b.list.smoothScrollToPosition(0); }, 350);
    }

    /** Wordmark tap / re-selecting the Feed tab: back to the newest. */
    public void scrollToTop() {
        if (b == null) return;
        b.appBar.setExpanded(true, true);          // bring the stories back too
        if (layout.findFirstVisibleItemPosition() > 8) b.list.scrollToPosition(8);
        b.list.smoothScrollToPosition(0);
    }

    // ── Card actions ─────────────────────────────────────────────────────────

    @Override
    public void onOpen(@NonNull Post post, boolean toResponses) {
        Nav.post(requireContext(), post.postId, toResponses);
    }

    @Override
    public void onAuthor(@NonNull Post post) {
        if (post.author != null && post.author.alias != null) Nav.profile(requireContext(), post.author.alias);
    }

    @Override
    public void onFelt(@NonNull Post post) {
        Haptics.tick(requireContext());
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
            @Override public void onDelete(@NonNull Post p) { confirmDelete(p); }
            @Override public void onReportPost(@NonNull Post p) { ReportSheet.show(FeedFragment.this, p.postId); }
            @Override public void onReportUser(@NonNull Post p, @NonNull String alias) {
                ReportSheet.showUser(getChildFragmentManager(), alias);
            }
            @Override public void onBlocked(@NonNull String alias) { loadStories(); }
        });
    }

    private void confirmDelete(Post post) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete_title)
                .setMessage(R.string.delete_body)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete, (d, w) -> vm.delete(post))
                .show();
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(STATE_SCOPE, scope);
    }

    @Override
    public void onDestroyView() {
        if (storyCall != null) storyCall.cancel();
        storyCall = null;
        storyAdapter = null;
        super.onDestroyView();
        b = null;
        shown = null;
    }
}
