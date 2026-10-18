package in.mohammad.ramiz.confess.ui.feed;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Formats;
import in.mohammad.ramiz.confess.data.model.Post;
import in.mohammad.ramiz.confess.data.repo.PostRepository;
import in.mohammad.ramiz.confess.databinding.ItemBurstSliderBinding;
import in.mohammad.ramiz.confess.databinding.ItemBurstStackBinding;
import in.mohammad.ramiz.confess.databinding.ItemConfessionCardBinding;
import in.mohammad.ramiz.confess.databinding.ItemFeedFooterBinding;
import in.mohammad.ramiz.confess.ui.components.PaperTone;

/**
 * Paper cards + one footer row. Old: PostAdapter (notifyDataSetChanged on every page).
 * Uses DiffUtil, and a "felt" payload so a heart tap updates only the button.
 *
 * Bursts ({@link Bursts}): the feed sends one card per burst; it shows as a slider (swipe
 * through them, oldest first — fetched from the server a set at a time as you near the end,
 * see {@link BurstStore}) or, for more than three when the reader chose it, a stack card that
 * opens them full screen. The same adapter, in "pages" mode, fills a slider: plain cards at their
 * own height, no footer. The slider takes the height of the card on screen and grows / shrinks
 * smoothly while you swipe between a short one and a long one (like a photo carousel).
 */
public class PostAdapter extends ListAdapter<PostAdapter.Row, RecyclerView.ViewHolder> {

    private static final int TYPE_POST = 1;
    private static final int TYPE_FOOTER = 2;
    private static final int TYPE_SLIDER = 3;
    private static final int TYPE_STACK = 4;
    private static final String PAYLOAD_FELT = "felt";

    /** What a burst row needs from the screen. */
    public interface BurstListener {
        /** The stack card was tapped: read them full screen, oldest first. */
        void onOpenBurst(@NonNull Bursts.Group group);

        /** A burst is on screen (the first time: ask how bursts should look). */
        void onBurstShown();
    }

    /** A list row: a confession, a burst, or the footer. */
    public static final class Row {
        @Nullable final Post post;
        @Nullable final Bursts.Group group;
        @Nullable final FeedState.Footer footer;

        private Row(@Nullable Post post, @Nullable Bursts.Group group, @Nullable FeedState.Footer footer) {
            this.post = post;
            this.group = group;
            this.footer = footer;
        }
    }

    private final PostCardBinder.Listener listener;
    private final Runnable onRetryMore;
    /** Pages of a slider: no bursts, no footer, cards fill the slider's height. */
    private final boolean pages;
    private int burstMode = Bursts.SLIDER;
    private String scope = PostRepository.SCOPE_ALL;
    @Nullable private BurstListener bursts;
    /** Which page each slider was left on (by burst), so a rebind doesn't jump back. */
    private final Map<String, Integer> sliderPage = new HashMap<>();
    @Nullable private List<Post> lastPosts;
    private FeedState.Footer lastFooter = FeedState.Footer.NONE;

    public PostAdapter(@NonNull PostCardBinder.Listener listener, @NonNull Runnable onRetryMore) {
        this(listener, onRetryMore, false);
    }

    private PostAdapter(@NonNull PostCardBinder.Listener listener, @NonNull Runnable onRetryMore, boolean pages) {
        super(DIFF);
        this.listener = listener;
        this.onRetryMore = onRetryMore;
        this.pages = pages;
    }

    /** Show bursts (feed only). {@code mode}: {@link Bursts#SLIDER} · STACK · SEPARATE. */
    public void setBursts(int mode, @Nullable BurstListener l) {
        bursts = l;
        setBurstMode(mode);
    }

    /** The reader changed how bursts look: redraw what's shown. */
    public void setBurstMode(int mode) {
        if (mode == burstMode) return;
        burstMode = mode;
        if (lastPosts != null) submit(lastPosts, lastFooter, null);
    }

    /** Everyone / Following: bursts are fetched with the same filter as the feed. */
    public void setScope(@NonNull String s) {
        scope = s;
    }

    public void submit(@NonNull List<Post> posts, @NonNull FeedState.Footer footer, @Nullable Runnable done) {
        lastPosts = posts;
        lastFooter = footer;
        boolean group = !pages && bursts != null && burstMode != Bursts.SEPARATE;
        List<Row> rows = new ArrayList<>(posts.size() + 1);
        boolean anyBurst = false;
        for (Post p : posts) {
            Bursts.Group g = group ? Bursts.of(p, scope) : null;
            if (g != null) {
                rows.add(new Row(null, g, null));
                anyBurst = true;
            } else {
                rows.add(new Row(p, null, null));
            }
        }
        if (!pages && (footer != FeedState.Footer.NONE || !posts.isEmpty())) rows.add(new Row(null, null, footer));
        submitList(rows, done);
        if (anyBurst && bursts != null) bursts.onBurstShown();
    }

    @Override
    public int getItemViewType(int position) {
        Row r = getItem(position);
        if (r.post != null) return TYPE_POST;
        if (r.group != null) return Bursts.asStack(r.group, burstMode) ? TYPE_STACK : TYPE_SLIDER;
        return TYPE_FOOTER;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_POST) {
            ItemConfessionCardBinding b = ItemConfessionCardBinding.inflate(inf, parent, false);
            if (pages) {
                ViewGroup.LayoutParams lp = b.getRoot().getLayoutParams();
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;    // its own height, never stretched
            }
            return new PostHolder(b);
        }
        if (viewType == TYPE_SLIDER) return new SliderHolder(ItemBurstSliderBinding.inflate(inf, parent, false), parent);
        if (viewType == TYPE_STACK) return new StackHolder(ItemBurstStackBinding.inflate(inf, parent, false));
        return new FooterHolder(ItemFeedFooterBinding.inflate(inf, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = getItem(position);
        if (holder instanceof PostHolder && row.post != null) {
            PostCardBinder.bind(((PostHolder) holder).b, row.post, listener);
        } else if (holder instanceof SliderHolder && row.group != null) {
            ((SliderHolder) holder).bind(row.group);
        } else if (holder instanceof StackHolder && row.group != null) {
            ((StackHolder) holder).bind(row.group);
        } else if (holder instanceof FooterHolder) {
            bindFooter(((FooterHolder) holder).b, row.footer);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
        Row row = getItem(position);
        if (holder instanceof PostHolder && row.post != null && payloads.contains(PAYLOAD_FELT)) {
            PostHolder h = (PostHolder) holder;
            PostCardBinder.bind(h.b, row.post, listener);         // rebinds click targets to the new Post
            PostCardBinder.bindFelt(h.b, row.post, true);
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    // Burst rows follow their burst only while on screen (no leaks, no work off screen).
    @Override
    public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        if (holder instanceof BurstStore.Listener) {
            BurstStore.listen((BurstStore.Listener) holder);
            if (holder instanceof SliderHolder) ((SliderHolder) holder).refresh();
            if (holder instanceof StackHolder) ((StackHolder) holder).refresh();
        }
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof BurstStore.Listener) BurstStore.unlisten((BurstStore.Listener) holder);
        super.onViewDetachedFromWindow(holder);
    }

    private void bindFooter(ItemFeedFooterBinding b, @Nullable FeedState.Footer footer) {
        FeedState.Footer f = footer == null ? FeedState.Footer.NONE : footer;
        b.loading.setVisibility(f == FeedState.Footer.LOADING || f == FeedState.Footer.NONE ? View.VISIBLE : View.GONE);
        b.error.setVisibility(f == FeedState.Footer.ERROR ? View.VISIBLE : View.GONE);
        b.end.setVisibility(f == FeedState.Footer.END ? View.VISIBLE : View.GONE);
        b.retry.setOnClickListener(v -> onRetryMore.run());
    }

    // ── Slider ────────────────────────────────────────────────────────────

    /**
     * A burst you swipe through, oldest first. Until its first set arrives it shows the card the
     * feed sent; then the loaded ones, and the next set is fetched as you near the end.
     */
    final class SliderHolder extends RecyclerView.ViewHolder implements BurstStore.Listener {
        final ItemBurstSliderBinding b;
        final PostAdapter pagesAdapter;
        final LinearLayoutManager layout;
        @Nullable Bursts.Group group;
        @Nullable BurstStore.Entry entry;
        int page = 0;
        private int shownCount = 0;
        /** Each loaded card's height, in slider order. */
        private final List<Integer> pageHeights = new ArrayList<>();
        private final ViewGroup outer;
        @Nullable private ItemConfessionCardBinding probe;
        /** Measured card heights (by confession + what it shows), so each card is measured once. */
        private final Map<String, Integer> heights = new HashMap<>();
        private int measuredWidth = -1;

        SliderHolder(@NonNull ItemBurstSliderBinding b, @NonNull ViewGroup outer) {
            super(b.getRoot());
            this.b = b;
            this.outer = outer;
            pagesAdapter = new PostAdapter(listener, () -> { }, true);
            layout = new LinearLayoutManager(b.getRoot().getContext(), LinearLayoutManager.HORIZONTAL, false) {
                /** Cards are measured at their full height, not squeezed into the slider's current one
                 *  (mid-swipe the slider is between two heights; the taller card is clipped, not cut). */
                @Override
                public void measureChildWithMargins(@NonNull View child, int widthUsed, int heightUsed) {
                    RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) child.getLayoutParams();
                    int ws = getChildMeasureSpec(getWidth(), getWidthMode(),
                            getPaddingLeft() + getPaddingRight() + lp.leftMargin + lp.rightMargin + widthUsed,
                            lp.width, canScrollHorizontally());
                    int hs = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
                    child.measure(ws, hs);
                }
            };
            b.pages.setLayoutManager(layout);
            b.pages.setAdapter(pagesAdapter);
            b.pages.setItemAnimator(null);
            b.pages.setNestedScrollingEnabled(false);
            new PagerSnapHelper().attachToRecyclerView(b.pages);
            b.pages.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                    if (dx > 0 && entry != null && layout.findLastVisibleItemPosition() >= shownCount - 2) {
                        BurstStore.want(entry, shownCount + 1);          // the next set, before you get there
                    }
                    followHeight();
                }

                @Override
                public void onScrollStateChanged(@NonNull RecyclerView rv, int state) {
                    if (state != RecyclerView.SCROLL_STATE_IDLE) return;
                    int p = layout.findFirstCompletelyVisibleItemPosition();
                    if (p == RecyclerView.NO_POSITION) p = layout.findFirstVisibleItemPosition();
                    if (p == RecyclerView.NO_POSITION) return;
                    page = p;
                    if (group != null) sliderPage.put(group.id, p);
                    setHeight(heightAt(p));
                    showPage();
                }
            });
        }

        /** Mid-swipe: between the two cards' heights, in step with the finger. */
        void followHeight() {
            int w = b.pages.getWidth();
            int p = layout.findFirstVisibleItemPosition();
            if (w <= 0 || p == RecyclerView.NO_POSITION) return;
            View v = layout.findViewByPosition(p);
            if (v == null) return;
            float f = Math.max(0f, Math.min(1f, -v.getLeft() / (float) w));
            int a = heightAt(p), z = heightAt(Math.min(p + 1, pageHeights.size() - 1));
            setHeight(Math.round(a + (z - a) * f));
        }

        int heightAt(int p) {
            if (pageHeights.isEmpty()) return 0;
            return pageHeights.get(Math.max(0, Math.min(p, pageHeights.size() - 1)));
        }

        void setHeight(int height) {
            if (height <= 0) return;
            ViewGroup.LayoutParams lp = b.pages.getLayoutParams();
            if (lp.height != height) {
                lp.height = height;
                b.pages.setLayoutParams(lp);
            }
        }

        void bind(@NonNull Bursts.Group g) {
            boolean fresh = group == null || !group.id.equals(g.id);
            group = g;
            entry = g.entry();
            b.label.setText(itemView.getContext().getString(R.string.burst_slider_label, g.alias(), g.total));
            if (fresh) {
                Integer saved = sliderPage.get(g.id);
                page = saved == null ? 0 : saved;                       // oldest first
            }
            refresh();
            if (fresh) layout.scrollToPositionWithOffset(Math.min(page, Math.max(0, shownCount - 1)), 0);
            BurstStore.want(entry, BurstStore.SET);
        }

        @Override
        public void onBurstChanged(@NonNull String key) {
            if (entry != null && entry.key.equals(key)) refresh();
        }

        /** What's loaded (or the feed's card until the first set comes), the height, the counter. */
        void refresh() {
            if (group == null || entry == null) return;
            List<Post> list = entry.posts.isEmpty() ? Collections.singletonList(group.head) : new ArrayList<>(entry.posts);
            shownCount = list.size();
            pagesAdapter.submit(list, FeedState.Footer.NONE, null);
            measure(list);
            setHeight(heightAt(page));
            showPage();
        }

        /** Dots (up to 10) and "73 / 217" (· loading at the edge of what's loaded). */
        void showPage() {
            if (entry == null || group == null) return;
            Context ctx = itemView.getContext();
            int total = Math.max(entry.total, shownCount);
            String counter = ctx.getString(R.string.burst_counter, Math.min(page + 1, total), total);
            if (entry.loading && page >= shownCount - 2) counter = ctx.getString(R.string.burst_counter_loading, counter);
            b.counter.setText(counter);
            b.dots.setVisibility(total <= 10 ? View.VISIBLE : View.GONE);
            if (total > 10) return;
            if (b.dots.getChildCount() != total) {
                b.dots.removeAllViews();
                int size = dp(ctx, 6), gap = dp(ctx, 3);
                for (int i = 0; i < total; i++) {
                    View dot = new View(ctx);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
                    lp.setMarginStart(gap);
                    lp.setMarginEnd(gap);
                    dot.setLayoutParams(lp);
                    b.dots.addView(dot);
                }
            }
            int on = ContextCompat.getColor(ctx, R.color.amber);
            int off = ColorUtils.setAlphaComponent(ContextCompat.getColor(ctx, R.color.text_secondary), 90);
            for (int i = 0; i < b.dots.getChildCount(); i++) {
                GradientDrawable d = new GradientDrawable();
                d.setShape(GradientDrawable.OVAL);
                d.setColor(i == page ? on : off);
                b.dots.getChildAt(i).setBackground(d);
            }
        }

        /** Each loaded card's natural height (measured once per card, kept in {@link #pageHeights}). */
        void measure(@NonNull List<Post> posts) {
            int width = b.pages.getWidth() > 0 ? b.pages.getWidth()
                    : outer.getWidth() > 0 ? outer.getWidth()
                    : itemView.getResources().getDisplayMetrics().widthPixels;
            if (width != measuredWidth) {
                heights.clear();
                measuredWidth = width;
            }
            if (probe == null) probe = ItemConfessionCardBinding.inflate(LayoutInflater.from(itemView.getContext()), b.pages, false);
            View card = probe.getRoot();
            ViewGroup.MarginLayoutParams m = card.getLayoutParams() instanceof ViewGroup.MarginLayoutParams
                    ? (ViewGroup.MarginLayoutParams) card.getLayoutParams() : null;
            int mh = m == null ? 0 : m.leftMargin + m.rightMargin;
            int mv = m == null ? 0 : m.topMargin + m.bottomMargin;
            int ws = View.MeasureSpec.makeMeasureSpec(Math.max(1, width - mh), View.MeasureSpec.EXACTLY);
            int hs = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
            pageHeights.clear();
            for (Post p : posts) {
                String key = p.postId + "|" + (p.text == null ? 0 : p.text.length()) + "|" + PostCardBinder.extrasSignature(p);
                Integer known = heights.get(key);
                if (known == null) {
                    PostCardBinder.bind(probe, p, listener);
                    card.measure(ws, hs);
                    known = card.getMeasuredHeight() + mv;
                    heights.put(key, known);
                }
                pageHeights.add(known);
            }
        }
    }

    // ── Stack ─────────────────────────────────────────────────────────────

    /** "@u1 made 217 confessions" + the first lines of the oldest → the burst reader. */
    final class StackHolder extends RecyclerView.ViewHolder implements BurstStore.Listener {
        final ItemBurstStackBinding b;
        @Nullable Bursts.Group group;
        @Nullable BurstStore.Entry entry;

        StackHolder(ItemBurstStackBinding b) {
            super(b.getRoot());
            this.b = b;
        }

        void bind(@NonNull Bursts.Group g) {
            group = g;
            entry = g.entry();
            Context ctx = itemView.getContext();
            Post newest = g.head;
            int paper = PaperTone.paper(ctx);
            b.card.setCardBackgroundColor(paper);
            b.sheet1.setCardBackgroundColor(ColorUtils.blendARGB(paper, 0xFF000000, 0.07f));
            b.sheet2.setCardBackgroundColor(ColorUtils.blendARGB(paper, 0xFF000000, 0.14f));
            String alias = g.alias();
            b.avatar.setAvatar(newest.author != null ? newest.author.avatar : null);
            b.avatar.setStoryAlias(alias);
            b.alias.setText("@" + alias);
            b.time.setText(Formats.relative(newest.createdAt));
            b.span.setText(R.string.burst_stack_sub);
            View.OnClickListener open = v -> {
                if (bursts != null && group != null) bursts.onOpenBurst(group);
            };
            b.card.setOnClickListener(open);
            b.read.setOnClickListener(open);
            View.OnClickListener author = v -> listener.onAuthor(newest);
            b.avatar.setOnClickListener(author);
            b.alias.setOnClickListener(author);
            refresh();
            BurstStore.want(entry, BurstStore.SET);                 // the oldest, for the preview (and a quick open)
        }

        @Override
        public void onBurstChanged(@NonNull String key) {
            if (entry != null && entry.key.equals(key)) refresh();
        }

        void refresh() {
            if (group == null || entry == null) return;
            Context ctx = itemView.getContext();
            int total = Math.max(entry.total, entry.posts.size());
            if (total <= 0) total = group.total;
            b.count.setText(String.valueOf(total));
            b.title.setText(ctx.getString(R.string.burst_stack_title, group.alias(), total));
            Post first = entry.posts.isEmpty() ? group.head : entry.posts.get(0);
            b.preview.setText(first.text == null ? "" : first.text.trim());
            b.card.setContentDescription(ctx.getString(R.string.cd_burst_stack, group.alias(), total));
            PaperTone.apply(b.card);
        }
    }

    static int dp(@NonNull Context ctx, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, ctx.getResources().getDisplayMetrics()));
    }

    // ── Holders ───────────────────────────────────────────────────────────

    static final class PostHolder extends RecyclerView.ViewHolder {
        final ItemConfessionCardBinding b;

        PostHolder(ItemConfessionCardBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }

    static final class FooterHolder extends RecyclerView.ViewHolder {
        final ItemFeedFooterBinding b;

        FooterHolder(ItemFeedFooterBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }

    // ── Diff ──────────────────────────────────────────────────────────────

    private static final DiffUtil.ItemCallback<Row> DIFF = new DiffUtil.ItemCallback<Row>() {
        @Override
        public boolean areItemsTheSame(@NonNull Row a, @NonNull Row b) {
            if (a.post != null && b.post != null) return Objects.equals(a.post.postId, b.post.postId);
            if (a.group != null && b.group != null) return a.group.id.equals(b.group.id);
            return a.post == null && b.post == null && a.group == null && b.group == null;   // the one footer
        }

        @Override
        public boolean areContentsTheSame(@NonNull Row a, @NonNull Row b) {
            if (a.post != null && b.post != null) return same(a.post, b.post);
            if (a.group != null && b.group != null) {
                // What's inside comes from BurstStore; the row only changes with its head or size.
                return a.group.total == b.group.total && Objects.equals(a.group.head.postId, b.group.head.postId)
                        && a.group.scope.equals(b.group.scope);
            }
            return a.footer == b.footer;
        }

        @Nullable
        @Override
        public Object getChangePayload(@NonNull Row a, @NonNull Row b) {
            if (a.post != null && b.post != null && sameExceptFelt(a.post, b.post)) return PAYLOAD_FELT;
            return null;
        }

        private boolean same(Post x, Post y) {
            return sameExceptFelt(x, y)
                    && x.likedByMe == y.likedByMe
                    && x.likeCount == y.likeCount
                    && Objects.equals(x.myReaction, y.myReaction)
                    && Objects.equals(x.reactions, y.reactions);
        }

        private boolean sameExceptFelt(Post x, Post y) {
            return Objects.equals(x.text, y.text)
                    && PostCardBinder.extrasSignature(x).equals(PostCardBinder.extrasSignature(y))
                    && x.commentsEnabled == y.commentsEnabled
                    && x.commentCount == y.commentCount
                    && x.isMine == y.isMine
                    && Objects.equals(x.createdAt, y.createdAt)
                    && Objects.equals(x.author == null ? null : x.author.alias, y.author == null ? null : y.author.alias)
                    && Objects.equals(x.author == null ? null : x.author.avatar, y.author == null ? null : y.author.avatar);
        }
    };
}
