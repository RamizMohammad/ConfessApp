package in.mohammad.ramiz.confess.ui.books;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Formats;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.api.ErrorCode;
import in.mohammad.ramiz.confess.data.model.ChapterEntry;
import in.mohammad.ramiz.confess.databinding.ItemContentsRowBinding;
import in.mohammad.ramiz.confess.ui.compose.Moderation;

/** Small shared bits for the Books screens. */
public final class BookUi {

    private BookUi() { }

    /** "The first night", or "Chapter 3" when the writer gave it no title. */
    @NonNull
    public static String chapterTitle(@NonNull Context ctx, int no, @Nullable String title) {
        return title != null && !title.trim().isEmpty() ? title : ctx.getString(R.string.chapter_default, no);
    }

    /**
     * Error text for Books calls: the specific moderation reason for 451, and the server's own
     * sentence for rule errors (e.g. "Chapter 3 has to come out before chapter 4 …").
     */
    @NonNull
    public static String error(@NonNull Context ctx, @NonNull ApiException e) {
        if (e.is(ErrorCode.CONTENT_FLAGGED)) return ctx.getString(Moderation.reason(e));
        if (e.is(ErrorCode.VALIDATION_ERROR) && e.fieldMessage == null && !e.serverMessage.isEmpty()
                && !"Invalid request body".equals(e.serverMessage)) {
            return e.serverMessage;
        }
        return e.userMessage(ctx);
    }

    /** "Sat 18 Oct · 9:00 pm" in the phone's time zone and locale. */
    @NonNull
    public static String when(@Nullable String iso) {
        OffsetDateTime t = Formats.parse(iso);
        if (t == null) return "";
        ZonedDateTime z = t.atZoneSameInstant(ZoneId.systemDefault());
        String day = z.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()));
        String time = z.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()));
        return day + " · " + time;
    }

    /** One contents row (book page, reader's contents sheet). read = I've opened it. */
    @NonNull
    public static View contentsRow(@NonNull LayoutInflater inf, @NonNull ViewGroup parent, @NonNull ChapterEntry c,
                                   boolean read, boolean current, @NonNull View.OnClickListener onClick) {
        Context ctx = parent.getContext();
        ItemContentsRowBinding r = ItemContentsRowBinding.inflate(inf, parent, false);
        r.number.setText(ctx.getString(R.string.chapter_number, c.chapterNo));
        r.title.setText(chapterTitle(ctx, c.chapterNo, c.title));
        StringBuilder meta = new StringBuilder();
        if (c.releasedAt != null) meta.append(Formats.dayTime(c.releasedAt).split(" · ")[0]);
        if (c.likeCount > 0) meta.append(meta.length() > 0 ? " · " : "").append(Formats.count(c.likeCount)).append(" felt");
        if (c.commentCount > 0) meta.append(meta.length() > 0 ? " · " : "")
                .append(Formats.count(c.commentCount)).append(c.commentCount == 1 ? " response" : " responses");
        r.meta.setText(meta);
        r.meta.setVisibility(meta.length() > 0 ? View.VISIBLE : View.GONE);
        r.mark.setVisibility(read ? View.VISIBLE : View.GONE);
        int muted = ContextCompat.getColor(ctx, R.color.text_muted);
        r.number.setTextColor(read && !current ? muted : ContextCompat.getColor(ctx, R.color.amber));
        r.title.setTextColor(ContextCompat.getColor(ctx, read && !current ? R.color.text_secondary : R.color.text_primary));
        if (current) r.row.setBackgroundColor(ContextCompat.getColor(ctx, R.color.surface_high));
        r.row.setOnClickListener(onClick);
        return r.getRoot();
    }
}
