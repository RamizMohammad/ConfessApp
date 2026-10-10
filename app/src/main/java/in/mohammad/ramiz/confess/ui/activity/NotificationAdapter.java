package in.mohammad.ramiz.confess.ui.activity;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Formats;
import in.mohammad.ramiz.confess.data.model.Notification;
import in.mohammad.ramiz.confess.databinding.ItemFeedFooterBinding;
import in.mohammad.ramiz.confess.databinding.ItemNotificationBinding;
import in.mohammad.ramiz.confess.databinding.ItemNotificationSectionBinding;

/** TODAY / THIS WEEK / EARLIER sections, activity cards, footer. */
public class NotificationAdapter extends ListAdapter<NotificationAdapter.Row, RecyclerView.ViewHolder> {

    public interface Listener {
        void onOpen(@NonNull Notification n);

        void onRetryMore();
    }

    private static final int T_SECTION = 1, T_ITEM = 2, T_FOOTER = 3;

    static final class Row {
        final int type;
        @Nullable final String section;
        final int newCount;
        @Nullable final Notification item;
        @Nullable final NotificationsViewModel.Footer footer;

        Row(int type, @Nullable String section, int newCount, @Nullable Notification item,
            @Nullable NotificationsViewModel.Footer footer) {
            this.type = type;
            this.section = section;
            this.newCount = newCount;
            this.item = item;
            this.footer = footer;
        }
    }

    private final Listener listener;

    public NotificationAdapter(@NonNull Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    public void submit(@NonNull Context ctx, @NonNull List<Notification> items,
                       @NonNull NotificationsViewModel.Footer footer, @Nullable Runnable done) {
        List<Row> rows = new ArrayList<>();
        LocalDate today = LocalDate.now();
        String current = null;
        int sectionIndex = -1;
        for (Notification n : items) {
            String label = bucket(ctx, n.createdAt, today);
            if (!label.equals(current)) {
                current = label;
                rows.add(new Row(T_SECTION, label, 0, null, null));
                sectionIndex = rows.size() - 1;
            }
            rows.add(new Row(T_ITEM, null, 0, n, null));
            if (!n.read && sectionIndex >= 0) {
                Row s = rows.get(sectionIndex);
                rows.set(sectionIndex, new Row(T_SECTION, s.section, s.newCount + 1, null, null));
            }
        }
        if (!items.isEmpty() || footer != NotificationsViewModel.Footer.NONE) {
            rows.add(new Row(T_FOOTER, null, 0, null, footer));
        }
        submitList(rows, done);
    }

    private static String bucket(Context ctx, @Nullable String iso, LocalDate today) {
        OffsetDateTime t = Formats.parse(iso);
        if (t == null) return ctx.getString(R.string.notif_earlier);
        LocalDate d = t.atZoneSameInstant(ZoneId.systemDefault()).toLocalDate();
        if (d.equals(today)) return ctx.getString(R.string.notif_today);
        if (d.isAfter(today.minusDays(7))) return ctx.getString(R.string.notif_week);
        return ctx.getString(R.string.notif_earlier);
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (type == T_SECTION) return new Holder<>(ItemNotificationSectionBinding.inflate(inf, parent, false));
        if (type == T_ITEM) return new Holder<>(ItemNotificationBinding.inflate(inf, parent, false));
        return new Holder<>(ItemFeedFooterBinding.inflate(inf, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = getItem(position);
        Object b = ((Holder<?>) holder).b;
        if (row.type == T_SECTION) {
            ItemNotificationSectionBinding s = (ItemNotificationSectionBinding) b;
            s.label.setText(row.section);
            s.count.setVisibility(row.newCount > 0 ? View.VISIBLE : View.GONE);
            s.count.setText(s.getRoot().getContext().getString(R.string.notif_new_count, row.newCount));
        } else if (row.type == T_ITEM && row.item != null) {
            bindItem((ItemNotificationBinding) b, row.item);
        } else if (row.type == T_FOOTER) {
            ItemFeedFooterBinding f = (ItemFeedFooterBinding) b;
            NotificationsViewModel.Footer ft = row.footer == null ? NotificationsViewModel.Footer.NONE : row.footer;
            f.loading.setVisibility(ft == NotificationsViewModel.Footer.LOADING
                    || ft == NotificationsViewModel.Footer.NONE ? View.VISIBLE : View.GONE);
            f.error.setVisibility(ft == NotificationsViewModel.Footer.ERROR ? View.VISIBLE : View.GONE);
            f.end.setVisibility(ft == NotificationsViewModel.Footer.END ? View.VISIBLE : View.GONE);
            f.retry.setOnClickListener(v -> listener.onRetryMore());
        }
    }

    private void bindItem(ItemNotificationBinding b, Notification n) {
        Context ctx = b.getRoot().getContext();
        boolean like = Notification.TYPE_LIKE.equals(n.type);
        boolean follow = Notification.TYPE_FOLLOW.equals(n.type);
        boolean letter = Notification.TYPE_POST.equals(n.type);
        String alias = "@" + (n.actor != null && n.actor.alias != null ? n.actor.alias : "someone");
        String when = Formats.relative(n.createdAt);

        b.avatar.setAvatar(n.actor != null ? n.actor.avatar : null);
        b.avatar.setStoryAlias(n.actor != null ? n.actor.alias : null);
        b.badge.setImageResource(like ? R.drawable.ic_heart_filled : follow ? R.drawable.ic_user
                : letter ? R.drawable.ic_letters : R.drawable.ic_message);
        b.badge.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(ctx,
                like || follow ? R.color.amber : letter ? R.color.sage : R.color.text_secondary)));

        SpannableStringBuilder line = new SpannableStringBuilder(alias);
        line.setSpan(new ForegroundColorSpan(ContextCompat.getColor(ctx, R.color.amber)), 0, alias.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        line.append(ctx.getString(like ? R.string.notif_felt : follow ? R.string.notif_followed_you
                : letter ? R.string.notif_new_letter : R.string.notif_responded));
        b.line.setText(line);
        b.time.setText(when);

        // Excerpt: their response, or (for felt / a new letter) the confession. Nothing for a follow.
        String quote = follow ? null
                : (like || letter) ? n.postExcerpt : (n.commentExcerpt != null ? n.commentExcerpt : n.postExcerpt);
        b.quote.setVisibility(quote == null || quote.trim().isEmpty() ? View.GONE : View.VISIBLE);
        b.quote.setText(quote == null ? "" : "“" + quote.trim() + "”");
        boolean showContext = Notification.TYPE_COMMENT.equals(n.type) && n.commentExcerpt != null && n.postExcerpt != null;
        b.context.setVisibility(showContext ? View.VISIBLE : View.GONE);
        if (showContext) b.context.setText(ctx.getString(R.string.notif_on, n.postExcerpt.trim()));

        // Unread: amber edge, raised card, brighter line.
        b.unreadEdge.setVisibility(n.read ? View.INVISIBLE : View.VISIBLE);
        b.card.setCardBackgroundColor(ContextCompat.getColor(ctx, n.read ? R.color.ink : R.color.surface_raised));
        b.line.setTextColor(ContextCompat.getColor(ctx, n.read ? R.color.text_secondary : R.color.text_primary));

        b.card.setContentDescription(ctx.getString(like ? R.string.notif_cd_felt
                : follow ? R.string.notif_cd_followed : letter ? R.string.notif_cd_new_letter
                : R.string.notif_cd_responded, alias, when));
        b.card.setOnClickListener(v -> listener.onOpen(n));
    }

    static final class Holder<B extends ViewBinding> extends RecyclerView.ViewHolder {
        final B b;

        Holder(B b) {
            super(b.getRoot());
            this.b = b;
        }
    }

    private static final DiffUtil.ItemCallback<Row> DIFF = new DiffUtil.ItemCallback<Row>() {
        @Override
        public boolean areItemsTheSame(@NonNull Row a, @NonNull Row b) {
            if (a.type != b.type) return false;
            if (a.type == T_ITEM && a.item != null && b.item != null) return Objects.equals(a.item.id, b.item.id);
            if (a.type == T_SECTION) return Objects.equals(a.section, b.section);
            return true;
        }

        @Override
        public boolean areContentsTheSame(@NonNull Row a, @NonNull Row b) {
            if (a.type == T_ITEM) return a.item != null && b.item != null && a.item.read == b.item.read;
            if (a.type == T_SECTION) return a.newCount == b.newCount;
            return a.footer == b.footer;
        }
    };
}
