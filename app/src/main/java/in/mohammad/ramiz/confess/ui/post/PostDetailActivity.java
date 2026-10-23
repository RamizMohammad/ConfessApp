package in.mohammad.ramiz.confess.ui.post;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.api.ErrorCode;
import in.mohammad.ramiz.confess.data.model.Comment;
import in.mohammad.ramiz.confess.data.model.Me;
import in.mohammad.ramiz.confess.data.model.Post;
import in.mohammad.ramiz.confess.data.session.SessionManager;
import in.mohammad.ramiz.confess.databinding.ActivityPostDetailBinding;
import in.mohammad.ramiz.confess.ui.base.BaseActivity;
import in.mohammad.ramiz.confess.ui.compose.Moderation;
import in.mohammad.ramiz.confess.ui.feed.PostMenu;
import in.mohammad.ramiz.confess.ui.feed.ReportSheet;
import in.mohammad.ramiz.confess.ui.nav.Nav;
import in.mohammad.ramiz.confess.ui.util.EdgeInsets;
import in.mohammad.ramiz.confess.ui.util.SimpleTextWatcher;

/**
 * S9 · A confession and its responses. Old: PostDetails (+ comment bottom sheet).
 * Opened with Nav.post(ctx, postId, toResponses) — from the feed, and later from
 * notifications and profiles.
 */
public class PostDetailActivity extends BaseActivity implements DetailAdapter.Listener {

    public static final int MAX_RESPONSE = 500;           // server COMMENT_MAX
    private static final int COUNTER_FROM = 400;
    private static final int PREFETCH = 3;

    private ActivityPostDetailBinding b;
    private PostDetailViewModel vm;
    private DetailAdapter adapter;
    private LinearLayoutManager layout;
    private boolean toResponses;
    private boolean firstContent = true;
    @Nullable private PostDetailViewModel.State shown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityPostDetailBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeInsets.padTop(b.topBar);
        EdgeInsets.padBottom(b.bottom, true);

        String postId = getIntent().getStringExtra(Nav.EXTRA_POST_ID);
        if (postId == null) { finish(); return; }
        toResponses = savedInstanceState == null && getIntent().getBooleanExtra(Nav.EXTRA_TO_RESPONSES, false);

        vm = new ViewModelProvider(this).get(PostDetailViewModel.class);

        adapter = new DetailAdapter(this);
        layout = new LinearLayoutManager(this);
        b.list.setLayoutManager(layout);
        b.list.setAdapter(adapter);
        b.list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy > 0) maybeLoadMore();
            }
        });

        Me me = SessionManager.get().cachedMe();
        if (me != null) b.myAvatar.setAvatar(me.avatar);

        b.back.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        // Sharing is off for now (button hidden in the layout); share() stays for when it comes back.
        b.more.setOnClickListener(v -> postMenu(v));
        b.input.addTextChangedListener(new SimpleTextWatcher(t -> updateInput()));
        b.send.setOnClickListener(v -> send());
        b.gifButton.setOnClickListener(v -> {
            Haptics.tick(this);
            in.mohammad.ramiz.confess.ui.gif.GifPicker.show(this, this::attachGif);
        });
        b.gifThumb.setMaxRatio(1.4f);
        b.gifThumb.setOnClickListener(v -> in.mohammad.ramiz.confess.ui.gif.GifPicker.show(this, this::attachGif));
        b.gifRemove.setOnClickListener(v -> {
            Haptics.tick(this);
            attachGif(null);
        });
        b.noticeClose.setOnClickListener(v -> b.notice.setVisibility(View.GONE));
        updateInput();

        vm.state().observe(this, this::render);
        vm.sending().observe(this, on -> {
            boolean busy = Boolean.TRUE.equals(on);
            b.sending.setVisibility(busy ? View.VISIBLE : View.GONE);
            b.send.setImageAlpha(busy ? 0 : 255);
            b.input.setEnabled(!busy);
            updateInput();
        });
        vm.sent().observe(this, e -> {
            Comment c = e == null ? null : e.consume();
            if (c == null) return;
            Haptics.click(this);
            b.input.setText("");
            attachGif(null);
            b.notice.setVisibility(View.GONE);
            b.list.postDelayed(() -> {
                int pos = adapter.lastResponsePosition();
                if (pos >= 0) b.list.smoothScrollToPosition(pos);
            }, 250);
        });
        vm.sendErrors().observe(this, e -> {
            ApiException err = e == null ? null : e.consume();
            if (err != null) showSendError(err);
        });
        vm.messages().observe(this, e -> {
            PostDetailViewModel.Message m = e == null ? null : e.consume();
            if (m == null) return;
            String text = m.error != null ? m.error.userMessage(this) : getString(m.res);
            Snackbar.make(b.getRoot(), text, Snackbar.LENGTH_SHORT).setAnchorView(b.bottom).show();
        });
        vm.closed().observe(this, e -> {
            Integer res = e == null ? null : e.consume();
            if (res != null) {
                Toast.makeText(this, res, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
        getSupportFragmentManager().setFragmentResultListener(ReportSheet.RESULT, this, (key, result) ->
                ReportSheet.deliver(this, result, new ReportSheet.Outcome() {
                    @Override public void reportPost(@NonNull String postId, @NonNull String reason) { vm.report(reason); }
                    @Override public void commentReported(@NonNull String commentId) { vm.hideComment(commentId); }
                    @Override public void say(@NonNull String text) {
                        if (isFinishing() || isDestroyed()) return;
                        Snackbar.make(b.getRoot(), text, Snackbar.LENGTH_SHORT).setAnchorView(b.bottom).show();
                    }
                }));

        vm.start(postId);
    }

    // ── Render ───────────────────────────────────────────────────────────────

    private void render(@Nullable PostDetailViewModel.State s) {
        if (s == null) return;
        shown = s;
        boolean loading = s.screen == PostDetailViewModel.Screen.LOADING;
        boolean content = s.screen == PostDetailViewModel.Screen.CONTENT && s.post != null;

        b.skeleton.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) b.skeleton.startShimmer(); else b.skeleton.stopShimmer();
        b.list.setVisibility(content ? View.VISIBLE : View.INVISIBLE);
        b.more.setEnabled(content);
        if (content) {                                   // mine → bin (delete) · theirs → flag (report)
            b.more.setIconResource(s.post.isMine ? R.drawable.ic_trash : R.drawable.ic_flag);
            b.more.setContentDescription(getString(s.post.isMine ? R.string.cd_delete_menu : R.string.cd_report_menu));
        }

        if (s.screen == PostDetailViewModel.Screen.GONE) {
            showState(R.drawable.ic_mail_open, getString(R.string.detail_gone_title),
                    getString(R.string.detail_gone_body), R.string.detail_back_to_feed, v -> finish());
        } else if (s.screen == PostDetailViewModel.Screen.ERROR) {
            boolean offline = s.error != null && s.error.isOffline();
            showState(offline ? R.drawable.ic_wifi_off : R.drawable.ic_alert, getString(R.string.feed_error_title),
                    s.error != null ? s.error.userMessage(this) : getString(R.string.error_generic),
                    R.string.action_retry, v -> vm.retry());
        } else {
            b.state.setVisibility(View.GONE);
        }

        // Bottom: response bar, or "closed".
        b.bottom.setVisibility(content ? View.VISIBLE : View.GONE);
        if (content) {
            boolean open = s.post.commentsEnabled;
            b.inputBar.setVisibility(open ? View.VISIBLE : View.GONE);
            b.closedBar.setVisibility(open ? View.GONE : View.VISIBLE);
            if (!open) {
                b.counter.setVisibility(View.GONE);
                b.notice.setVisibility(View.GONE);
            }
        }

        adapter.submit(s, vm, () -> {
            if (content && firstContent) {
                firstContent = false;
                if (toResponses) {
                    toResponses = false;
                    jumpToResponses(s.post.commentsEnabled);
                }
            }
            maybeLoadMore();
        });
    }

    private void showState(int icon, String title, String body, int action, View.OnClickListener click) {
        b.state.setVisibility(View.VISIBLE);
        b.stateIcon.setImageResource(icon);
        b.stateTitle.setText(title);
        b.stateBody.setText(body);
        b.stateAction.setText(action);
        b.stateAction.setOnClickListener(click);
    }

    private void maybeLoadMore() {
        if (shown == null || shown.screen != PostDetailViewModel.Screen.CONTENT) return;
        int last = layout.findLastVisibleItemPosition();
        if (last != RecyclerView.NO_POSITION && last >= adapter.getItemCount() - PREFETCH) vm.loadMore();
    }

    private void jumpToResponses(boolean focusInput) {
        int header = adapter.headerPosition();
        if (header >= 0) layout.scrollToPositionWithOffset(header, 0);
        if (focusInput) {
            b.input.requestFocus();
            WindowCompat.getInsetsController(getWindow(), b.input).show(WindowInsetsCompat.Type.ime());
        }
    }

    // ── Response input ───────────────────────────────────────────────────────

    private String input() {
        return b.input.getText() == null ? "" : b.input.getText().toString();
    }

    /** The GIF going with the next response (null = none). */
    @Nullable private in.mohammad.ramiz.confess.data.model.Gif gif;

    private void attachGif(@Nullable in.mohammad.ramiz.confess.data.model.Gif g) {
        gif = g;
        b.gifAttach.setVisibility(g != null ? View.VISIBLE : View.GONE);
        in.mohammad.ramiz.confess.ui.gif.GifViews.show(b.gifThumb, g, true);
        b.gifButton.setColorFilter(ContextCompat.getColor(this, g != null ? R.color.amber : R.color.text_secondary));
        updateInput();
    }

    private void updateInput() {
        int n = input().trim().length();
        boolean busy = Boolean.TRUE.equals(vm == null ? null : vm.sending().getValue());
        b.send.setEnabled(!busy && (n > 0 || gif != null) && n <= MAX_RESPONSE);
        b.counter.setVisibility(n >= COUNTER_FROM ? View.VISIBLE : View.GONE);
        b.counter.setText(getString(R.string.response_counter, n, MAX_RESPONSE));
        b.counter.setTextColor(ContextCompat.getColor(this, n > MAX_RESPONSE ? R.color.brick : R.color.text_muted));
    }

    private void send() {
        String text = input().trim();
        if ((text.isEmpty() && gif == null) || text.length() > MAX_RESPONSE) return;
        b.notice.setVisibility(View.GONE);
        vm.send(text, gif);
    }

    private void showSendError(ApiException e) {
        Haptics.reject(this);
        String message;
        if (e.is(ErrorCode.CONTENT_FLAGGED)) {
            message = Moderation.isEmptyAfterCleaning(e)
                    ? getString(R.string.compose_empty_after_clean)
                    : getString(R.string.response_flagged, getString(Moderation.reason(e)));
        } else if (e.is(ErrorCode.MODERATION_UNAVAILABLE)) {
            message = getString(R.string.response_unavailable);
        } else if (e.is(ErrorCode.COMMENTS_DISABLED)) {
            Snackbar.make(b.getRoot(), e.userMessage(this), Snackbar.LENGTH_LONG).setAnchorView(b.bottom).show();
            return;
        } else {
            Snackbar.make(b.getRoot(), e.userMessage(this), Snackbar.LENGTH_LONG).setAnchorView(b.bottom).show();
            return;
        }
        boolean soft = e.is(ErrorCode.MODERATION_UNAVAILABLE);
        b.notice.setBackgroundResource(soft ? R.drawable.bg_plate_amber : R.drawable.bg_plate_error);
        b.noticeIcon.setImageResource(soft ? R.drawable.ic_shield : R.drawable.ic_alert);
        b.noticeIcon.setImageTintList(android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this, soft ? R.color.amber : R.color.brick)));
        b.noticeText.setText(message);
        b.notice.setVisibility(View.VISIBLE);
    }

    // ── Post actions ─────────────────────────────────────────────────────────

    private void postMenu(View anchor) {
        Post post = vm.post();
        if (post == null) return;
        PostMenu.show(anchor, post, new PostMenu.Listener() {
            @Override public void onDelete(@NonNull Post p) {
                new MaterialAlertDialogBuilder(PostDetailActivity.this)
                        .setTitle(R.string.delete_title)
                        .setMessage(R.string.delete_body)
                        .setNegativeButton(R.string.action_cancel, null)
                        .setPositiveButton(R.string.action_delete, (d, w) -> vm.deletePost())
                        .show();
            }
            @Override public void onReportPost(@NonNull Post p) {
                ReportSheet.showIn(getSupportFragmentManager(), p.postId);
            }
            @Override public void onReportUser(@NonNull Post p, @NonNull String alias) {
                ReportSheet.showUser(getSupportFragmentManager(), alias);
            }
            @Override public void onBlocked(@NonNull String alias) {
                if (!isFinishing()) finish();
            }
        });
    }

    private void share() {
        Post post = vm.post();
        if (post == null) return;
        String alias = post.author != null ? "@" + post.author.alias : "";
        String text = "“" + post.text.trim() + "”\n" + alias + " " + getString(R.string.detail_share_footer);
        Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(send, getString(R.string.cd_share)));
    }

    // ── Card + response callbacks ────────────────────────────────────────────

    @Override
    public void onOpen(@NonNull Post post, boolean toResponses) {
        if (toResponses) jumpToResponses(post.commentsEnabled);
    }

    @Override
    public void onAuthor(@NonNull Post post) {
        if (post.author != null && post.author.alias != null) Nav.profile(this, post.author.alias);
    }

    @Override
    public void onResponseAuthor(@NonNull Comment c) {
        if (c.author != null && c.author.alias != null) Nav.profile(this, c.author.alias);
    }

    @Override
    public void onFelt(@NonNull Post post) {
        Haptics.tick(this);
        vm.toggleFelt();
    }

    @Override
    public void onReact(@NonNull Post post, @NonNull String reaction) {
        vm.react(reaction);
    }

    @Override
    public void onVote(@NonNull Post post, int option) {
        vm.vote(option);
    }

    @Override
    public void onMore(@NonNull Post post, @NonNull View anchor) {
        postMenu(anchor);
    }

    @Override
    public void onResponseMore(@NonNull Comment c, @NonNull View anchor) {
        String alias = c.author != null && c.author.alias != null ? c.author.alias : "";
        PostMenu.showResponse(anchor, alias, c.isMine, vm.canDelete(c), new PostMenu.ResponseListener() {
            @Override public void onDelete() {
                new MaterialAlertDialogBuilder(PostDetailActivity.this)
                        .setTitle(R.string.response_delete_title)
                        .setMessage(R.string.response_delete_body)
                        .setNegativeButton(R.string.action_cancel, null)
                        .setPositiveButton(R.string.action_delete, (d, w) -> vm.deleteComment(c))
                        .show();
            }
            @Override public void onReport() {
                ReportSheet.showComment(getSupportFragmentManager(), c.commentId);
            }
            @Override public void onBlock(@NonNull String a) {
                in.mohammad.ramiz.confess.ui.people.BlockFlow.confirm(PostDetailActivity.this, a, null);
            }
        });
    }

    @Override
    public void onRetryMore() {
        vm.retryMore();
    }
}
