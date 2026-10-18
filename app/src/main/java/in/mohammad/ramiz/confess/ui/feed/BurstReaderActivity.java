package in.mohammad.ramiz.confess.ui.feed;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.JsonObject;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.core.PostEvents;
import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.api.ErrorCode;
import in.mohammad.ramiz.confess.data.model.Post;
import in.mohammad.ramiz.confess.data.repo.PostRepository;
import in.mohammad.ramiz.confess.databinding.ActivityBurstReaderBinding;
import in.mohammad.ramiz.confess.databinding.ItemConfessionCardBinding;
import in.mohammad.ramiz.confess.ui.base.BaseActivity;
import in.mohammad.ramiz.confess.ui.nav.Nav;
import in.mohammad.ramiz.confess.ui.util.EdgeInsets;

/**
 * A burst read full screen: one writer's confessions, oldest → newest, one per page, each in
 * full. Pages come from the server a set at a time as you go ({@link BurstStore}) — a burst of
 * 217 opens as fast as one of 5. Swipe, or Previous / Next; "Done" on the very last.
 * Reactions, polls, responses and the ⋯ menu work as on the feed.
 */
public class BurstReaderActivity extends BaseActivity
        implements PostCardBinder.Listener, ReportSheet.Outcome, BurstStore.Listener {

    private static final String EXTRA_ID = "burst_id";
    private static final String EXTRA_SCOPE = "scope";
    private static final String EXTRA_TOTAL = "total";
    private static final String EXTRA_ALIAS = "alias";
    /** More than this many: one progress bar instead of a segment per confession. */
    private static final int MAX_SEGMENTS = 12;

    public static void open(@NonNull Context ctx, @NonNull Bursts.Group group) {
        ctx.startActivity(new Intent(ctx, BurstReaderActivity.class)
                .putExtra(EXTRA_ID, group.id)
                .putExtra(EXTRA_SCOPE, group.scope)
                .putExtra(EXTRA_TOTAL, group.total)
                .putExtra(EXTRA_ALIAS, group.alias()));
    }

    private ActivityBurstReaderBinding b;
    private BurstStore.Entry entry;
    private String alias = "";
    private final PostRepository repo = new PostRepository();
    private final FeltSync felt = new FeltSync(this::find, this::sayError);
    private final PollSync polls = new PollSync(this::sayError);
    private Pages adapter;
    private LinearLayoutManager layout;
    private int page = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String id = getIntent().getStringExtra(EXTRA_ID);
        if (id == null) {
            finish();
            return;
        }
        String scope = getIntent().getStringExtra(EXTRA_SCOPE);
        entry = BurstStore.get(id, scope == null ? PostRepository.SCOPE_ALL : scope, getIntent().getIntExtra(EXTRA_TOTAL, 0));
        String a = getIntent().getStringExtra(EXTRA_ALIAS);
        alias = a == null ? "" : a;

        b = ActivityBurstReaderBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeInsets.padTop(b.topBar);
        EdgeInsets.padBottom(b.bottomBar, false);

        adapter = new Pages();
        layout = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        b.pager.setLayoutManager(layout);
        b.pager.setAdapter(adapter);
        b.pager.setItemAnimator(null);
        new PagerSnapHelper().attachToRecyclerView(b.pager);
        b.pager.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dx > 0 && layout.findLastVisibleItemPosition() >= entry.posts.size() - 2) {
                    BurstStore.want(entry, entry.posts.size() + 1);
                }
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int state) {
                if (state != RecyclerView.SCROLL_STATE_IDLE) return;
                int p = layout.findFirstCompletelyVisibleItemPosition();
                if (p == RecyclerView.NO_POSITION) p = layout.findFirstVisibleItemPosition();
                if (p != RecyclerView.NO_POSITION && p != page) {
                    page = p;
                    Haptics.tick(BurstReaderActivity.this);
                    chrome();
                }
            }
        });
        if (savedInstanceState != null) page = savedInstanceState.getInt("page", 0);

        b.back.setOnClickListener(v -> finish());
        b.prev.setOnClickListener(v -> go(page - 1));
        b.next.setOnClickListener(v -> {
            if (atVeryEnd()) finish();
            else go(page + 1);
        });

        PostEvents.changes().observe(this, c -> {
            if (c != null && c.kind == PostEvents.Kind.BLOCKED) finish();
        });
        getSupportFragmentManager().setFragmentResultListener(ReportSheet.RESULT, this,
                (key, result) -> ReportSheet.deliver(this, result, this));

        BurstStore.listen(this);
        adapter.notifyDataSetChanged();
        if (page > 0) layout.scrollToPosition(Math.min(page, Math.max(0, entry.posts.size() - 1)));
        BurstStore.want(entry, Math.max(BurstStore.SET, page + 2));
        chrome();
    }

    @Override
    public void onBurstChanged(@NonNull String key) {
        if (entry == null || !entry.key.equals(key) || b == null) return;
        if (entry.posts.isEmpty() && entry.done) {
            finish();                                    // all taken down / hidden for me
            return;
        }
        adapter.notifyDataSetChanged();                  // a set came in, or one changed / went
        page = Math.min(page, Math.max(0, entry.posts.size() - 1));
        chrome();
    }

    private boolean atVeryEnd() {
        return entry.done && page >= entry.posts.size() - 1;
    }

    private void go(int p) {
        if (p < 0) return;
        if (p >= entry.posts.size()) {                   // not loaded yet: fetch, the swipe follows
            BurstStore.want(entry, p + 1);
            return;
        }
        page = p;
        b.pager.smoothScrollToPosition(p);
        if (p >= entry.posts.size() - 2) BurstStore.want(entry, entry.posts.size() + 1);
        chrome();
    }

    /** "@alias", "73 of 217 · oldest first", progress and the buttons. */
    private void chrome() {
        int total = Math.max(entry.total, entry.posts.size());
        b.title.setText("@" + alias);
        b.loading.setVisibility(entry.posts.isEmpty() ? View.VISIBLE : View.GONE);
        b.sub.setText(total > 0 ? getString(R.string.burst_reader_position, Math.min(page + 1, total), total) : "");
        b.prev.setEnabled(page > 0);
        b.next.setText(atVeryEnd() ? R.string.burst_done : R.string.burst_next);
        b.next.setEnabled(!entry.posts.isEmpty());

        int on = ContextCompat.getColor(this, R.color.amber);
        int off = ColorUtils.setAlphaComponent(ContextCompat.getColor(this, R.color.text_secondary), 70);
        int segments = total <= MAX_SEGMENTS ? total : 2;
        if (b.segments.getChildCount() != segments) {
            b.segments.removeAllViews();
            int gap = Math.round(getResources().getDisplayMetrics().density * 3);
            for (int i = 0; i < segments; i++) {
                View seg = new View(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                if (i > 0 && total <= MAX_SEGMENTS) lp.setMarginStart(gap);
                seg.setLayoutParams(lp);
                b.segments.addView(seg);
            }
        }
        if (total > MAX_SEGMENTS) {
            // One bar: read · still to read.
            ((LinearLayout.LayoutParams) b.segments.getChildAt(0).getLayoutParams()).weight = page + 1;
            ((LinearLayout.LayoutParams) b.segments.getChildAt(1).getLayoutParams()).weight = Math.max(0, total - page - 1);
            b.segments.requestLayout();
        }
        for (int i = 0; i < b.segments.getChildCount(); i++) {
            GradientDrawable d = new GradientDrawable();
            d.setCornerRadius(99f);
            d.setColor(total > MAX_SEGMENTS ? (i == 0 ? on : off) : (i <= page ? on : off));
            b.segments.getChildAt(i).setBackground(d);
        }
    }

    @Nullable
    private Post find(@NonNull String postId) {
        for (Post p : entry.posts) if (postId.equals(p.postId)) return p;
        return null;
    }

    // ── Card actions ──────────────────────────────────────────────────────

    @Override
    public void onOpen(@NonNull Post post, boolean toResponses) {
        Nav.post(this, post.postId, toResponses);
    }

    @Override
    public void onAuthor(@NonNull Post post) {
        if (post.author != null && post.author.alias != null) Nav.profile(this, post.author.alias);
    }

    @Override
    public void onFelt(@NonNull Post post) {
        Haptics.tick(this);
        felt.toggle(post);
    }

    @Override
    public void onReact(@NonNull Post post, @NonNull String reaction) {
        felt.react(post, reaction);
    }

    @Override
    public void onVote(@NonNull Post post, int option) {
        Post now = find(post.postId);
        polls.vote(now != null ? now : post, option);
    }

    @Override
    public void onMore(@NonNull Post post, @NonNull View anchor) {
        PostMenu.show(anchor, post, new PostMenu.Listener() {
            @Override public void onDelete(@NonNull Post p) { confirmDelete(p); }
            @Override public void onReportPost(@NonNull Post p) { ReportSheet.showIn(getSupportFragmentManager(), p.postId); }
            @Override public void onReportUser(@NonNull Post p, @NonNull String a) {
                ReportSheet.showUser(getSupportFragmentManager(), a);
            }
        });
    }

    private void confirmDelete(@NonNull Post post) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_title)
                .setMessage(R.string.delete_body)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete, (d, w) -> repo.delete(post.postId, new ApiCallback<JsonObject>() {
                    @Override public void onSuccess(JsonObject data) { PostEvents.removed(post.postId); }
                    @Override public void onError(ApiException e) {
                        if (e.is(ErrorCode.NOT_FOUND)) PostEvents.removed(post.postId);
                        else sayError(e);
                    }
                }))
                .show();
    }

    // ── ReportSheet.Outcome ───────────────────────────────────────────────

    @Override
    public void reportPost(@NonNull String postId, @NonNull String reason) {
        PostEvents.removed(postId);                       // hidden for me at once, like the feed
        repo.report(postId, reason, new ApiCallback<JsonObject>() {
            @Override public void onSuccess(JsonObject data) { }
            @Override public void onError(ApiException e) { }
        });
    }

    @Override
    public void say(@NonNull String text) {
        if (b != null) Snackbar.make(b.root, text, Snackbar.LENGTH_SHORT).show();
    }

    private void sayError(@NonNull ApiException e) {
        say(e.userMessage(this));
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("page", page);
    }

    @Override
    protected void onDestroy() {
        BurstStore.unlisten(this);
        super.onDestroy();
    }

    // ── Pages ─────────────────────────────────────────────────────────────

    /** One confession per page, in full, scrolling when it's long. */
    private final class Pages extends RecyclerView.Adapter<Pages.Holder> {

        final class Holder extends RecyclerView.ViewHolder {
            final ItemConfessionCardBinding card;

            Holder(@NonNull NestedScrollView scroll, @NonNull ItemConfessionCardBinding card) {
                super(scroll);
                this.card = card;
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            NestedScrollView scroll = new NestedScrollView(parent.getContext());
            scroll.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            scroll.setClipToPadding(false);
            int pad = Math.round(getResources().getDisplayMetrics().density * 8);
            scroll.setPadding(0, pad, 0, pad * 3);
            ItemConfessionCardBinding card = ItemConfessionCardBinding.inflate(LayoutInflater.from(parent.getContext()), scroll, false);
            scroll.addView(card.getRoot());
            return new Holder(scroll, card);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            PostCardBinder.bind(h.card, entry.posts.get(position), BurstReaderActivity.this);
            h.card.text.setMaxLines(Integer.MAX_VALUE);             // the whole confession here
            h.card.text.setEllipsize(null);
        }

        @Override
        public int getItemCount() {
            return entry == null ? 0 : entry.posts.size();
        }
    }
}
