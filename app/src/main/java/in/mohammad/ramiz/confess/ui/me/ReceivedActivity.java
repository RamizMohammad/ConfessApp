package in.mohammad.ramiz.confess.ui.me;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Formats;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.data.model.Me;
import in.mohammad.ramiz.confess.data.model.Post;
import in.mohammad.ramiz.confess.data.session.SessionManager;
import in.mohammad.ramiz.confess.databinding.ActivityReceivedBinding;
import in.mohammad.ramiz.confess.ui.base.BaseActivity;
import in.mohammad.ramiz.confess.ui.nav.Nav;
import in.mohammad.ramiz.confess.ui.util.EdgeInsets;

/**
 * Felt / Responses on my letters (from the Me page cards). Each letter is a
 * collapsible row: expand it to see who felt it, or the responses it got.
 */
public class ReceivedActivity extends BaseActivity implements ReceivedAdapter.Listener {

    public static final String EXTRA_MODE = "mode";
    public static final String MODE_FELT = "felt";
    public static final String MODE_RESPONSES = "responses";
    private static final int PREFETCH = 4;

    private ActivityReceivedBinding b;
    private ReceivedViewModel vm;
    private ReceivedAdapter adapter;
    private LinearLayoutManager layout;
    private boolean felt;
    @Nullable private ReceivedViewModel.State shown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityReceivedBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeInsets.padTop(b.topBar);
        EdgeInsets.padBottom(b.list, false);

        felt = !MODE_RESPONSES.equals(getIntent().getStringExtra(EXTRA_MODE));
        b.title.setText(felt ? R.string.felt_title : R.string.responses_title);
        Me me = SessionManager.get().cachedMe();
        if (me != null) {
            b.summary.setText(felt
                    ? getString(R.string.received_felt_summary, Formats.count(me.likesReceived), Formats.count(me.likesGiven))
                    : getString(R.string.received_responses_summary,
                    Formats.count(me.commentsReceived), Formats.count(me.commentsMade)));
        }

        vm = new ViewModelProvider(this).get(ReceivedViewModel.class);
        adapter = new ReceivedAdapter(this, felt);
        layout = new LinearLayoutManager(this);
        b.list.setLayoutManager(layout);
        b.list.setAdapter(adapter);
        b.list.setItemAnimator(null);                  // expand/collapse without cross-fades
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
        b.stateAction.setOnClickListener(v -> vm.refresh());

        vm.state().observe(this, this::render);
        vm.start(felt);
    }

    private void render(@Nullable ReceivedViewModel.State s) {
        if (s == null) return;
        shown = s;
        boolean loading = s.screen == ReceivedViewModel.Screen.LOADING;
        b.skeleton.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) b.skeleton.startShimmer(); else b.skeleton.stopShimmer();
        b.refresh.setRefreshing(s.refreshing);
        b.refresh.setEnabled(!loading);
        b.list.setVisibility(s.screen == ReceivedViewModel.Screen.CONTENT ? View.VISIBLE : View.INVISIBLE);

        if (s.screen == ReceivedViewModel.Screen.EMPTY) {
            b.state.setVisibility(View.VISIBLE);
            b.stateIcon.setImageResource(felt ? R.drawable.ic_heart : R.drawable.ic_message);
            b.stateTitle.setText(felt ? R.string.received_felt_empty : R.string.received_responses_empty);
            b.stateBody.setText(R.string.received_empty_body);
            b.stateAction.setVisibility(View.GONE);
        } else if (s.screen == ReceivedViewModel.Screen.ERROR) {
            b.state.setVisibility(View.VISIBLE);
            boolean offline = s.error != null && s.error.isOffline();
            b.stateIcon.setImageResource(offline ? R.drawable.ic_wifi_off : R.drawable.ic_alert);
            b.stateTitle.setText(R.string.feed_error_title);
            b.stateBody.setText(s.error != null ? s.error.userMessage(this) : getString(R.string.error_generic));
            b.stateAction.setVisibility(View.VISIBLE);
        } else {
            b.state.setVisibility(View.GONE);
        }
        adapter.submit(s, this::maybeLoadMore);
    }

    private void maybeLoadMore() {
        if (shown == null || shown.screen != ReceivedViewModel.Screen.CONTENT) return;
        int last = layout.findLastVisibleItemPosition();
        if (last == RecyclerView.NO_POSITION || last >= adapter.getItemCount() - PREFETCH) vm.loadMore();
    }

    @Override
    public void onToggle(@NonNull Post post) {
        Haptics.tick(this);
        vm.toggle(post.postId);
    }

    @Override
    public void onPerson(@NonNull String alias) {
        if (!alias.isEmpty()) Nav.profile(this, alias);
    }

    @Override
    public void onMorePeople(@NonNull String postId) {
        vm.morePeople(postId);
    }

    @Override
    public void onOpenLetter(@NonNull String postId) {
        Nav.post(this, postId, !felt);
    }

    @Override
    public void onRetryMore() {
        vm.retryMore();
    }
}
