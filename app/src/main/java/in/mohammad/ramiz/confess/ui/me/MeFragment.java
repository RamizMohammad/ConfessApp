package in.mohammad.ramiz.confess.ui.me;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.FollowEvents;
import in.mohammad.ramiz.confess.core.Formats;
import in.mohammad.ramiz.confess.core.PostEvents;
import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.drafts.Drafts;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.model.ConfessionGraph;
import in.mohammad.ramiz.confess.data.model.Me;
import in.mohammad.ramiz.confess.data.repo.UserRepository;
import in.mohammad.ramiz.confess.data.session.SessionManager;
import in.mohammad.ramiz.confess.databinding.FragmentMeBinding;
import in.mohammad.ramiz.confess.ui.components.Banner;
import in.mohammad.ramiz.confess.ui.nav.Nav;
import in.mohammad.ramiz.confess.ui.profile.BannerSheet;
import in.mohammad.ramiz.confess.ui.util.EdgeInsets;
import in.mohammad.ramiz.confess.ui.util.Motion;

/**
 * S11 · Me tab. Old: ProfileFragment (profile + a grid of setting buttons).
 *
 * Profile header, then four cards stacked vertically:
 *   Graph      → my confessions per day (last 6 months) + my streak
 *   Letters    → all my letters
 *   Felt       → given · received   → who felt each letter (expand per letter)
 *   Responses  → made · received    → responses on each letter (expand per letter)
 *   Settings
 */
public class MeFragment extends Fragment {

    private FragmentMeBinding b;
    private final UserRepository users = new UserRepository();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentMeBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        EdgeInsets.padTop(b.topBar);
        setUpBanner();

        b.feltGiven.label.setText(R.string.me_given);
        b.feltReceived.label.setText(R.string.me_received);
        b.feltReceived.value.setTextColor(ContextCompat.getColor(requireContext(), R.color.amber));
        b.responsesMade.label.setText(R.string.me_made);
        b.responsesReceived.label.setText(R.string.me_received);
        b.responsesReceived.value.setTextColor(ContextCompat.getColor(requireContext(), R.color.amber));

        b.refresh.setColorSchemeColors(ContextCompat.getColor(requireContext(), R.color.amber));
        b.refresh.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(requireContext(), R.color.surface_high));
        b.refresh.setOnRefreshListener(this::refresh);

        b.cardLetters.setOnClickListener(v -> Nav.myLetters(requireContext()));
        b.cardDrafts.setOnClickListener(v -> Nav.drafts(requireContext()));
        b.cardFollowing.setOnClickListener(v -> Nav.people(requireContext(), false));
        b.cardFollowers.setOnClickListener(v -> Nav.people(requireContext(), true));
        // Followed / unfollowed someone anywhere → my "following" number changes.
        FollowEvents.changes().observe(getViewLifecycleOwner(), c -> { if (c != null && !isHidden()) refresh(); });
        b.cardFelt.setOnClickListener(v -> Nav.received(requireContext(), ReceivedActivity.MODE_FELT));
        b.cardResponses.setOnClickListener(v -> Nav.received(requireContext(), ReceivedActivity.MODE_RESPONSES));
        b.cardSettings.setOnClickListener(v -> Nav.settings(requireContext()));
        b.graph.setOnDayTap(this::onGraphDay);
        b.graphCustomize.setOnClickListener(v -> openGraphStyle());
        b.meArrange.setOnClickListener(v -> startActivity(
                new android.content.Intent(requireContext(), in.mohammad.ramiz.confess.ui.settings.AppearanceActivity.class)
                        .putExtra(in.mohammad.ramiz.confess.ui.settings.AppearanceActivity.EXTRA_SECTION,
                                in.mohammad.ramiz.confess.ui.settings.AppearanceActivity.SECTION_ME)));
        MePage.apply(requireContext(), b);
        b.editAura.setOnClickListener(v -> Nav.editProfile(requireContext()));
        b.editAbout.setOnClickListener(v -> Nav.editProfile(requireContext()));
        b.about.setOnClickListener(v -> Nav.editProfile(requireContext()));

        // Posting / deleting / felt anywhere changes my counters.
        PostEvents.changes().observe(getViewLifecycleOwner(), c -> {
            if (c != null && c.kind != PostEvents.Kind.UPDATED && !isHidden()) refresh();
        });

        render(SessionManager.get().cachedMe());       // fresh numbers come in onResume / when shown
        if (!isHidden()) enterCards();
    }

    /** The cards ease in the first time Me is actually on screen. */
    private boolean cardsEntered = false;

    private void enterCards() {
        if (cardsEntered || b == null) return;
        cardsEntered = true;
        Motion.enterStack(b.content);
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && b != null) {
            enterCards();
            refresh();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (b != null) MePage.apply(requireContext(), b);   // back from Appearance → Me page
        Me cached = SessionManager.get().cachedMe();
        if (cached != null) renderDrafts(cached.id);
        if (!isHidden()) refresh();
    }

    private void refresh() {
        loadGraph();
        users.me(new ApiCallback<Me>() {
            @Override public void onSuccess(Me me) {
                if (b == null) return;
                b.refresh.setRefreshing(false);
                render(me);
            }
            @Override public void onError(ApiException e) {
                if (b != null) b.refresh.setRefreshing(false);
            }
        });
    }

    private void render(@Nullable Me me) {
        if (me == null || b == null) return;
        b.banner.setBanner(Banner.resolve(me.banner, me.avatar, me.alias));
        b.avatar.setAvatar(me.avatar);
        b.avatar.setStoryAlias(me.alias);
        b.alias.setText("@" + me.alias);

        boolean hasAbout = me.about != null && !me.about.trim().isEmpty();
        b.about.setText(hasAbout ? "“" + me.about.trim() + "”" : getString(R.string.profile_add_about));
        b.about.setTextColor(ContextCompat.getColor(requireContext(), hasAbout ? R.color.text_secondary : R.color.amber));

        String since = Formats.monthYear(me.createdAt);
        b.since.setVisibility(since.isEmpty() ? View.GONE : View.VISIBLE);
        b.since.setText(getString(R.string.me_since, since.toUpperCase(Locale.getDefault())));

        b.letterCount.setText(Formats.count(me.postCount));
        b.followingCount.setText(Formats.count(me.following));
        b.followersCount.setText(Formats.count(me.followers));
        renderDrafts(me.id);
        b.feltGiven.value.setText(Formats.count(me.likesGiven));
        b.feltReceived.value.setText(Formats.count(me.likesReceived));
        b.responsesMade.value.setText(Formats.count(me.commentsMade));
        b.responsesReceived.value.setText(Formats.count(me.commentsReceived));
        if (me.streak != null) {                        // the chip right away; the graph follows
            b.graphStreak.setText(me.streak.days > 0 ? getResources().getQuantityString(R.plurals.graph_streak, me.streak.days, me.streak.days)
                    : getString(R.string.graph_streak_start));
        }
    }

    // ── Confession graph ─────────────────────────────────────────────────────

    private static final int GRAPH_WEEKS = 26;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault());

    @Nullable private ConfessionGraph lastGraph;

    private void openGraphStyle() {
        Me me = SessionManager.get().cachedMe();
        GraphStyle style = GraphStyle.parse(lastGraph != null ? lastGraph.style : me != null ? me.graphStyle : null);
        boolean isPublic = lastGraph != null ? lastGraph.isPublic : me == null || me.graphPublic;
        GraphStyleSheet.show(requireContext(), lastGraph, style, isPublic, (chosen, pub) -> {
            if (b == null) return;
            if (lastGraph != null) {
                lastGraph.style = chosen.spec();
                lastGraph.isPublic = pub;
            }
            applyGraphStyle(chosen);
        });
    }

    private void applyGraphStyle(@NonNull GraphStyle style) {
        b.graph.setGraphStyle(style);
        style.paintLegend(b.graphLegend);
    }

    private void loadGraph() {
        if (!MePage.shown(requireContext(), MePage.GRAPH)) return;
        users.confessionGraph(GRAPH_WEEKS, new ApiCallback<ConfessionGraph>() {
            @Override public void onSuccess(ConfessionGraph g) {
                if (b != null) renderGraph(g);
            }
            @Override public void onError(ApiException e) { }
        });
    }

    private void renderGraph(@NonNull ConfessionGraph g) {
        if (g.start == null || g.days == null) return;
        lastGraph = g;
        applyGraphStyle(GraphStyle.parse(g.style));
        try {
            b.graph.setData(LocalDate.parse(g.start), g.days);
        } catch (Exception e) {
            return;
        }
        String total = getResources().getQuantityString(R.plurals.graph_total, g.total, g.total);
        int best = g.streak != null ? g.streak.best : 0;
        b.graphSub.setText(total);
        int streak = g.streak != null ? g.streak.days : 0;
        b.graphStreak.setText(streak > 0 ? getResources().getQuantityString(R.plurals.graph_streak, streak, streak) : getString(R.string.graph_streak_start));
        b.graphDay.setText(best > 1 ? getString(R.string.graph_hint_best, best) : getString(R.string.graph_hint));
        b.graph.setContentDescription(getString(R.string.graph_cd, total, b.graphStreak.getText()));
    }

    private void onGraphDay(@NonNull LocalDate day, int count) {
        if (b == null) return;
        String d = day.format(DAY);
        b.graphDay.setText(count > 0 ? getResources().getQuantityString(R.plurals.graph_day, count, count, d)
                : getString(R.string.graph_day_none, d));
    }

    // ── Banner ───────────────────────────────────────────────────────────────

    /**
     * The banner runs edge to edge under the status bar and the (clear) top bar, behind the whole
     * header (avatar · @alias · about · since). Scrolling moves it at half speed and fills the top bar in with ink.
     */
    private void setUpBanner() {
        float dp = getResources().getDisplayMetrics().density;
        b.banner.setAnimated(true);
        b.banner.setFadeStart(0.72f);
        b.topBar.setBackgroundColor(0);
        b.topBar.addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, or, ob) -> {
            if (b == null || bt - t == ob - ot) return;
            int top = bt - t;                                // the header starts below the top bar
            b.headerContent.setPadding(b.headerContent.getPaddingLeft(), top + Math.round(12 * dp),
                    b.headerContent.getPaddingRight(), b.headerContent.getPaddingBottom());
            b.refresh.setProgressViewOffset(false, top - Math.round(24 * dp), top + Math.round(40 * dp));
        });
        b.scroll.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener)
                (v, x, y, ox, oy) -> onScrolled(y));
        View.OnClickListener edit = v -> BannerSheet.show(requireContext(), SessionManager.get().cachedMe(),
                spec -> {
                    Me cached = SessionManager.get().cachedMe();
                    if (cached != null) render(cached);
                    refresh();
                });
        b.bannerBox.setOnClickListener(edit);
        b.bannerEdit.setOnClickListener(edit);
    }

    private void onScrolled(int y) {
        if (b == null) return;
        float range = Math.max(1, b.bannerBox.getHeight() - b.topBar.getHeight());
        float f = Math.max(0f, Math.min(1f, y / range));
        int ink = ContextCompat.getColor(requireContext(), R.color.ink);
        b.topBar.setBackgroundColor(((int) (f * 255) << 24) | (ink & 0x00FFFFFF));
        b.banner.setTranslationY(Math.min(y, b.bannerBox.getHeight()) * 0.45f);   // parallax
    }

    /** Drafts live on the phone, so their count is read locally (also when coming back from the composer). */
    private void renderDrafts(@Nullable String userId) {
        if (b == null) return;
        b.draftCount.setText(Formats.count(Drafts.get(requireContext()).count(userId)));
    }

    /** Re-selecting the Me tab. */
    public void scrollToTop() {
        if (b != null) b.scroll.smoothScrollTo(0, 0);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
