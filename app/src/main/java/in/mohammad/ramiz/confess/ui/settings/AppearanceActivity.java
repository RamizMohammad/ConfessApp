package in.mohammad.ramiz.confess.ui.settings;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.databinding.ActivitySettingsAppearanceBinding;
import in.mohammad.ramiz.confess.databinding.ItemMeSectionBinding;
import in.mohammad.ramiz.confess.databinding.ItemNavTabBinding;
import in.mohammad.ramiz.confess.ui.home.NavBar;
import in.mohammad.ramiz.confess.ui.me.MePage;
import in.mohammad.ramiz.confess.ui.base.BaseActivity;
import in.mohammad.ramiz.confess.ui.util.EdgeInsets;

/**
 * Settings → Appearance: the paper tone (bright → charcoal) and text tone (Auto, or dark ink →
 * light cream) used by confession cards, the composer and book pages — with a live preview —
 * how bursts look in the feed (slider · stack · one by one, with the animated chooser), and the
 * bottom bar (tab order around the Confess button, labels, animated icons — with the real bar as
 * a live preview; Home picks the changes up when it resumes), and the Me page (its sections'
 * order and which show; Settings always stays last). "Arrange this page" on Me opens this
 * screen at that section ({@link #EXTRA_SECTION}).
 */
public class AppearanceActivity extends BaseActivity {

    /** Scroll to a section on open: {@link #SECTION_ME}. */
    public static final String EXTRA_SECTION = "section";
    public static final String SECTION_ME = "me";

    private ActivitySettingsAppearanceBinding b;

    private final List<String> meOrder = new ArrayList<>();
    private MeAdapter meAdapter;
    private ItemTouchHelper meDrag;

    private final List<String> navOrder = new ArrayList<>();
    private NavAdapter navAdapter;
    private ItemTouchHelper navDrag;
    /** The tab lit up in the preview (Search only flashes; it never stays selected). */
    private String previewTab = NavBar.FEED;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivitySettingsAppearanceBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeInsets.padTop(b.topBar);
        EdgeInsets.padBottom(b.scroll, false);
        b.back.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        setUpPaperTone();
        setUpCardText();
        setUpBursts();
        setUpNavBar();
        setUpMePage();
        if (SECTION_ME.equals(getIntent().getStringExtra(EXTRA_SECTION))) {
            b.scroll.post(() -> b.scroll.smoothScrollTo(0, Math.max(0, b.meSectionLabel.getTop() - b.topBar.getHeight())));
        }
    }

    // ── Appearance: Me page ───────────────────────────────────────────────

    private void setUpMePage() {
        meOrder.clear();
        meOrder.addAll(MePage.order(this));
        meAdapter = new MeAdapter();
        b.meOrder.setLayoutManager(new LinearLayoutManager(this));
        b.meOrder.setAdapter(meAdapter);
        meDrag = new ItemTouchHelper(new MeDrag());
        meDrag.attachToRecyclerView(b.meOrder);
        b.meReset.setOnClickListener(v -> {
            MePage.reset(this);
            meOrder.clear();
            meOrder.addAll(MePage.order(this));
            meAdapter.notifyDataSetChanged();
            Haptics.tick(this);
        });
    }

    private final class MeDrag extends ItemTouchHelper.Callback {

        @Override
        public int getMovementFlags(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
            return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
        }

        @Override
        public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder from, @NonNull RecyclerView.ViewHolder to) {
            int a = from.getBindingAdapterPosition(), z = to.getBindingAdapterPosition();
            if (a < 0 || z < 0) return false;
            Collections.swap(meOrder, a, z);
            meAdapter.notifyItemMoved(a, z);
            Haptics.tick(AppearanceActivity.this);
            return true;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) { }

        @Override
        public void onSelectedChanged(@Nullable RecyclerView.ViewHolder vh, int state) {
            super.onSelectedChanged(vh, state);
            if (state == ItemTouchHelper.ACTION_STATE_DRAG && vh != null) {
                vh.itemView.setTranslationZ(12f);
            }
        }

        @Override
        public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
            super.clearView(rv, vh);
            vh.itemView.setTranslationZ(0f);
            MePage.setOrder(AppearanceActivity.this, meOrder);
        }
    }

    private final class MeAdapter extends RecyclerView.Adapter<MeAdapter.Holder> {

        final class Holder extends RecyclerView.ViewHolder {
            final ItemMeSectionBinding row;

            Holder(ItemMeSectionBinding row) {
                super(row.getRoot());
                this.row = row;
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemMeSectionBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            String key = meOrder.get(position);
            String name = getString(MePage.name(key));
            boolean shown = MePage.shown(AppearanceActivity.this, key);
            h.row.icon.setImageResource(MePage.icon(key));
            h.row.name.setText(name);
            h.row.shown.setOnCheckedChangeListener(null);
            h.row.shown.setChecked(shown);
            h.row.shown.setContentDescription(getString(R.string.cd_show_section, name));
            showState(h, shown, false);
            h.row.shown.setOnCheckedChangeListener((v, on) -> {
                MePage.setShown(AppearanceActivity.this, key, on);
                Haptics.tick(AppearanceActivity.this);
                showState(h, on, true);
            });
            h.row.grip.setContentDescription(getString(R.string.cd_drag_tab, name));
            h.row.grip.setOnTouchListener((v, ev) -> {
                if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) meDrag.startDrag(h);
                return false;
            });
        }

        /** A hidden section's row fades back, so what's on the page stands out. */
        private void showState(@NonNull Holder h, boolean shown, boolean animate) {
            h.row.state.setText(shown ? R.string.mepage_shown : R.string.mepage_hidden);
            float a = shown ? 1f : 0.45f;
            if (animate) {
                h.row.tile.animate().alpha(a).setDuration(180).start();
                h.row.texts.animate().alpha(a).setDuration(180).start();
            } else {
                h.row.tile.setAlpha(a);
                h.row.texts.setAlpha(a);
            }
        }

        @Override
        public int getItemCount() {
            return meOrder.size();
        }
    }

    // ── Appearance: bottom bar ────────────────────────────────────────────

    private void setUpNavBar() {
        navOrder.clear();
        navOrder.addAll(NavBar.order(this));
        navAdapter = new NavAdapter();
        b.navOrder.setLayoutManager(new LinearLayoutManager(this));
        b.navOrder.setAdapter(navAdapter);
        navDrag = new ItemTouchHelper(new NavDrag());
        navDrag.attachToRecyclerView(b.navOrder);

        // Preview: the real bar. Tapping a tab there shows how switching looks.
        for (String key : NavBar.DEFAULT_ORDER) {
            NavBar.tab(b.navPreview, key).setOnClickListener(v -> {
                Haptics.tick(this);
                if (NavBar.SEARCH.equals(key)) {
                    NavBar.bump(this, b.navPreview, key);
                } else {
                    previewTab = key;
                    NavBar.render(this, b.navPreview, previewTab, true);
                }
            });
        }
        b.navPreview.compose.setClickable(false);
        b.navPreview.compose.setFocusable(false);
        b.navPreview.compose.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        refreshNavPreview();

        b.navReset.setOnClickListener(v -> {
            navOrder.clear();
            navOrder.addAll(NavBar.DEFAULT_ORDER);
            NavBar.setOrder(this, navOrder);
            navAdapter.notifyDataSetChanged();
            refreshNavPreview();
            Haptics.tick(this);
        });

        int labels = NavBar.labels(this);
        b.navLabels.check(labels == NavBar.LABELS_SELECTED ? b.navLabelsSelected.getId()
                : labels == NavBar.LABELS_NEVER ? b.navLabelsNever.getId() : b.navLabelsAlways.getId());
        b.navLabels.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked) return;
            int m = id == b.navLabelsSelected.getId() ? NavBar.LABELS_SELECTED
                    : id == b.navLabelsNever.getId() ? NavBar.LABELS_NEVER : NavBar.LABELS_ALWAYS;
            if (m == NavBar.labels(this)) return;
            NavBar.setLabels(this, m);
            Haptics.tick(this);
            android.transition.TransitionManager.beginDelayedTransition((ViewGroup) b.navPreview.getRoot());
            NavBar.render(this, b.navPreview, previewTab, false);
        });

        b.navAnimate.setChecked(NavBar.animationsOn(this));
        b.navAnimate.setOnCheckedChangeListener((v, on) -> {
            NavBar.setAnimationsOn(this, on);
            Haptics.tick(this);
            NavBar.render(this, b.navPreview, previewTab, true);    // shows the change straight away
        });
    }

    private void refreshNavPreview() {
        NavBar.arrange(b.navPreview, navOrder);
        NavBar.render(this, b.navPreview, previewTab, false);
    }

    /** Drag up/down; the list is the order, two left of Confess then two right. */
    private final class NavDrag extends ItemTouchHelper.Callback {

        @Override
        public int getMovementFlags(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
            return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
        }

        @Override
        public boolean isLongPressDragEnabled() {
            return true;                                  // hold the row, or touch the grip
        }

        @Override
        public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder from, @NonNull RecyclerView.ViewHolder to) {
            int a = from.getBindingAdapterPosition(), z = to.getBindingAdapterPosition();
            if (a < 0 || z < 0) return false;
            Collections.swap(navOrder, a, z);
            navAdapter.notifyItemMoved(a, z);
            refreshNavPreview();                          // the preview follows the drag
            Haptics.tick(AppearanceActivity.this);
            return true;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) { }

        @Override
        public void onSelectedChanged(@Nullable RecyclerView.ViewHolder vh, int state) {
            super.onSelectedChanged(vh, state);
            if (state == ItemTouchHelper.ACTION_STATE_DRAG && vh != null) {
                vh.itemView.setAlpha(0.92f);
                vh.itemView.setTranslationZ(12f);
            }
        }

        @SuppressLint("NotifyDataSetChanged")
        @Override
        public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
            super.clearView(rv, vh);
            vh.itemView.setAlpha(1f);
            vh.itemView.setTranslationZ(0f);
            NavBar.setOrder(AppearanceActivity.this, navOrder);
            rv.post(navAdapter::notifyDataSetChanged);    // "Left / Right of Confess" lines
        }
    }

    private final class NavAdapter extends RecyclerView.Adapter<NavAdapter.Holder> {

        final class Holder extends RecyclerView.ViewHolder {
            final ItemNavTabBinding row;

            Holder(ItemNavTabBinding row) {
                super(row.getRoot());
                this.row = row;
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemNavTabBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            String key = navOrder.get(position);
            String name = getString(NavBar.name(key));
            h.row.icon.setImageResource(NavBar.icon(key));
            h.row.name.setText(name);
            h.row.side.setText(position < 2 ? R.string.navbar_left : R.string.navbar_right);
            h.row.grip.setContentDescription(getString(R.string.cd_drag_tab, name));
            h.row.grip.setOnTouchListener((v, ev) -> {
                if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) navDrag.startDrag(h);
                return false;
            });
        }

        @Override
        public int getItemCount() {
            return navOrder.size();
        }
    }

    // ── Appearance: paper + text tone ─────────────────────────────────────

    /**
     * Paper: bright → charcoal. Text: Auto (dark or light, whichever reads best on that paper)
     * or a fixed tone dark ink → light cream. The little note previews both.
     */
    private void setUpPaperTone() {
        b.paperTone.setValue(Math.round(in.mohammad.ramiz.confess.ui.components.PaperTone.get(this) / 5f) * 5f);
        int text = in.mohammad.ramiz.confess.ui.components.PaperTone.text(this);
        b.textAuto.setChecked(text == in.mohammad.ramiz.confess.ui.components.PaperTone.AUTO);
        b.textTone.setValue(text == in.mohammad.ramiz.confess.ui.components.PaperTone.AUTO ? 0f : Math.round(text / 5f) * 5f);
        b.textTone.setEnabled(text != in.mohammad.ramiz.confess.ui.components.PaperTone.AUTO);
        previewTone();
        b.paperTone.addOnChangeListener((slider, value, fromUser) -> {
            if (!fromUser) return;
            in.mohammad.ramiz.confess.ui.components.PaperTone.set(this, Math.round(value));
            previewTone();
            in.mohammad.ramiz.confess.core.Haptics.tick(this);
        });
        b.textTone.addOnChangeListener((slider, value, fromUser) -> {
            if (!fromUser) return;
            in.mohammad.ramiz.confess.ui.components.PaperTone.setText(this, Math.round(value));
            previewTone();
            in.mohammad.ramiz.confess.core.Haptics.tick(this);
        });
        b.textAuto.setOnCheckedChangeListener((v, auto) -> {
            if (auto) {
                in.mohammad.ramiz.confess.ui.components.PaperTone.setText(this, in.mohammad.ramiz.confess.ui.components.PaperTone.AUTO);
            } else {
                // Start the manual slider where Auto was, so nothing jumps.
                int now = in.mohammad.ramiz.confess.ui.components.PaperTone.ink(this) == androidx.core.content.ContextCompat.getColor(this, R.color.paper_ink) ? 0 : 100;
                b.textTone.setValue(now);
                in.mohammad.ramiz.confess.ui.components.PaperTone.setText(this, now);
            }
            b.textTone.setEnabled(!auto);
            previewTone();
        });
    }

    // ── Appearance: confession text ───────────────────────────────────────

    /** Size · spacing · font for confession cards (the reader's settings that fit a card). */
    private void setUpCardText() {
        b.cardSpacing.check(cardSpacingId(in.mohammad.ramiz.confess.ui.feed.CardText.spacing(this)));
        b.cardFont.select(in.mohammad.ramiz.confess.ui.feed.CardText.font(this), false);
        previewCardText();
        b.cardSmaller.setOnClickListener(v -> {
            in.mohammad.ramiz.confess.ui.feed.CardText.setSize(this, in.mohammad.ramiz.confess.ui.feed.CardText.size(this) - 1);
            Haptics.tick(this);
            previewCardText();
        });
        b.cardBigger.setOnClickListener(v -> {
            in.mohammad.ramiz.confess.ui.feed.CardText.setSize(this, in.mohammad.ramiz.confess.ui.feed.CardText.size(this) + 1);
            Haptics.tick(this);
            previewCardText();
        });
        b.cardSpacing.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked) return;
            int sp = id == b.cardSpacingCompact.getId() ? in.mohammad.ramiz.confess.ui.feed.CardText.COMPACT
                    : id == b.cardSpacingAiry.getId() ? in.mohammad.ramiz.confess.ui.feed.CardText.AIRY
                    : in.mohammad.ramiz.confess.ui.feed.CardText.NORMAL;
            if (sp == in.mohammad.ramiz.confess.ui.feed.CardText.spacing(this)) return;
            in.mohammad.ramiz.confess.ui.feed.CardText.setSpacing(this, sp);
            Haptics.tick(this);
            previewCardText();
        });
        b.cardFont.setListener(f -> {
            in.mohammad.ramiz.confess.ui.feed.CardText.setFont(this, f);
            previewCardText();
        });
    }

    private int cardSpacingId(int spacing) {
        return spacing == in.mohammad.ramiz.confess.ui.feed.CardText.COMPACT ? b.cardSpacingCompact.getId()
                : spacing == in.mohammad.ramiz.confess.ui.feed.CardText.AIRY ? b.cardSpacingAiry.getId()
                : b.cardSpacingNormal.getId();
    }

    /** The sample card: size, spacing and font as chosen, in the chosen paper and text tone. */
    private void previewCardText() {
        int size = in.mohammad.ramiz.confess.ui.feed.CardText.size(this);
        b.cardSize.setText(String.valueOf(size));
        b.cardSmaller.setEnabled(size > in.mohammad.ramiz.confess.ui.feed.CardText.MIN_SIZE);
        b.cardBigger.setEnabled(size < in.mohammad.ramiz.confess.ui.feed.CardText.MAX_SIZE);
        android.transition.TransitionManager.beginDelayedTransition((ViewGroup) b.cardPreview.getParent(),
                new android.transition.ChangeBounds().setDuration(160));
        in.mohammad.ramiz.confess.ui.feed.CardText.apply(b.cardPreview, false);
        b.cardPreview.setBackgroundTintList(android.content.res.ColorStateList.valueOf(in.mohammad.ramiz.confess.ui.components.PaperTone.paper(this)));
        b.cardPreview.setTextColor(in.mohammad.ramiz.confess.ui.components.PaperTone.ink(this));
    }

    // ── Appearance: bursts ────────────────────────────────────────────────

    /** Slider · Stack · One by one — and the animated chooser. */
    private void setUpBursts() {
        showBurstMode();
        b.burstMode.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked) return;
            int m = id == b.burstStack.getId() ? in.mohammad.ramiz.confess.ui.feed.Bursts.STACK
                    : id == b.burstSeparate.getId() ? in.mohammad.ramiz.confess.ui.feed.Bursts.SEPARATE
                    : in.mohammad.ramiz.confess.ui.feed.Bursts.SLIDER;
            if (m == in.mohammad.ramiz.confess.ui.feed.Bursts.mode(this) && in.mohammad.ramiz.confess.ui.feed.Bursts.asked(this)) return;
            in.mohammad.ramiz.confess.ui.feed.Bursts.setMode(this, m);
            in.mohammad.ramiz.confess.core.Haptics.tick(this);
        });
        b.burstHow.setOnClickListener(v -> in.mohammad.ramiz.confess.ui.feed.BurstChooser.show(this, m -> showBurstMode()));
    }

    private void showBurstMode() {
        int m = in.mohammad.ramiz.confess.ui.feed.Bursts.mode(this);
        b.burstMode.check(m == in.mohammad.ramiz.confess.ui.feed.Bursts.STACK ? b.burstStack.getId()
                : m == in.mohammad.ramiz.confess.ui.feed.Bursts.SEPARATE ? b.burstSeparate.getId() : b.burstSlider.getId());
    }

    private void previewTone() {
        if (b.cardPreview != null) {
            b.cardPreview.setBackgroundTintList(android.content.res.ColorStateList.valueOf(in.mohammad.ramiz.confess.ui.components.PaperTone.paper(this)));
            b.cardPreview.setTextColor(in.mohammad.ramiz.confess.ui.components.PaperTone.ink(this));
        }
        b.tonePreview.setBackgroundTintList(android.content.res.ColorStateList.valueOf(in.mohammad.ramiz.confess.ui.components.PaperTone.paper(this)));
        b.tonePreview.setTextColor(in.mohammad.ramiz.confess.ui.components.PaperTone.ink(this));
    }
}
