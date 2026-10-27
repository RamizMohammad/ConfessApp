package in.mohammad.ramiz.confess.core.push;

import android.app.PendingIntent;
import android.content.Context;
import android.graphics.Bitmap;
import android.text.TextUtils;
import android.widget.RemoteViews;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.util.Map;

import in.mohammad.ramiz.confess.R;

/**
 * Confess notification templates — the designed layouts for the marketing pushes the server's
 * Gemini push agent writes (nudges, new-confession teasers, digests, win-backs).
 *
 * The server sends, in the FCM data: template = spark · whisper · teaser · digest · winback · streak,
 * art = Letty's pose (wave · wink · sleepy · peek · hug · party · chai · shush), cta = button label,
 * and for digests count. This build tells the server it can draw them with
 * {@link #VERSION} (sent with the push token); the server then sends those pushes data-only so
 * {@link PushService} always builds them here, even when the app is closed.
 *
 * Collapsed: a compact card built to Android's 48dp cap (push_template_small.xml).
 * Pop-up: the full card fitted to the heads-up height, button inside (push_template_popup.xml, 112dp).
 * Expanded: the full themed card (background, colours, eyebrow) with a big Letty and a CTA pill.
 * Anything unknown falls back to the plain notification — a push is never lost to a template.
 */
public final class PushTemplates {

    /** Bump when a new template / pose is added, and teach the server about it. */
    public static final int VERSION = 1;

    private PushTemplates() { }

    private static final class Theme {
        @DrawableRes final int background;
        @StringRes final int eyebrow;
        final int title, body, eyebrowColor, ctaBg, ctaText;
        // Compact card (collapsed / pop-up): its own background, Letty's badge, muted body colour.
        @DrawableRes int smallBackground, badge;
        int smallBody;

        Theme(int background, int eyebrow, int title, int body, int eyebrowColor, int ctaBg, int ctaText) {
            this.background = background;
            this.eyebrow = eyebrow;
            this.title = title;
            this.body = body;
            this.eyebrowColor = eyebrowColor;
            this.ctaBg = ctaBg;
            this.ctaText = ctaText;
        }

        Theme compact(int smallBackground, int badge, int smallBody) {
            this.smallBackground = smallBackground;
            this.badge = badge;
            this.smallBody = smallBody;
            return this;
        }
    }

    // Colours are resource ids, resolved per call.
    @Nullable
    private static Theme theme(@Nullable String template) {
        if (template == null) return null;
        switch (template) {
            case "spark":
                return new Theme(R.drawable.push_bg_spark, R.string.push_eyebrow_spark,
                        R.color.paper_ink, R.color.paper_ink, R.color.paper_ink, R.drawable.push_cta_ink, R.color.paper)
                        .compact(R.drawable.push_bg_spark_sm, R.drawable.push_badge_glass, R.color.push_muted_amber);
            case "whisper":
                return new Theme(R.drawable.push_bg_whisper, R.string.push_eyebrow_whisper,
                        R.color.paper, R.color.paper, R.color.story_gold, R.drawable.push_cta_amber, R.color.paper_ink)
                        .compact(R.drawable.push_bg_whisper_sm, R.drawable.push_badge_night, R.color.push_muted_night);
            case "teaser":
                return new Theme(R.drawable.push_bg_teaser, R.string.push_eyebrow_teaser,
                        R.color.paper_ink, R.color.paper_ink, R.color.amber_pressed, R.drawable.push_cta_ink, R.color.paper)
                        .compact(R.drawable.push_bg_teaser_sm, R.drawable.push_badge_amber, R.color.push_muted_paper);
            case "digest":
                return new Theme(R.drawable.push_bg_digest, R.string.push_eyebrow_digest,
                        R.color.paper, R.color.paper, R.color.amber, R.drawable.push_cta_amber, R.color.paper_ink)
                        .compact(R.drawable.push_bg_digest_sm, R.drawable.push_badge_amber, R.color.push_muted_night);
            case "winback":
                return new Theme(R.drawable.push_bg_winback, R.string.push_eyebrow_winback,
                        R.color.paper_ink, R.color.paper_ink, R.color.amber_pressed, R.drawable.push_cta_ink, R.color.paper)
                        .compact(R.drawable.push_bg_winback_sm, R.drawable.push_badge_amber, R.color.push_muted_paper);
            case "streak":
                return new Theme(R.drawable.push_bg_streak, R.string.push_eyebrow_streak,
                        R.color.paper, R.color.paper, R.color.story_gold, R.drawable.push_cta_amber, R.color.paper_ink)
                        .compact(R.drawable.push_bg_streak_sm, R.drawable.push_badge_night, R.color.push_muted_night);
            default:
                return null;
        }
    }

    @DrawableRes
    static int pose(@Nullable String art, @Nullable String template) {
        if (art != null) {
            switch (art) {
                case "wave": return R.drawable.letty_pose_wave;
                case "wink": return R.drawable.letty_pose_wink;
                case "sleepy": return R.drawable.letty_pose_sleepy;
                case "peek": return R.drawable.letty_pose_peek;
                case "hug": return R.drawable.letty_pose_hug;
                case "party": return R.drawable.letty_pose_party;
                case "chai": return R.drawable.letty_pose_chai;
                case "shush": return R.drawable.letty_pose_shush;
                default: break;
            }
        }
        if ("whisper".equals(template)) return R.drawable.letty_pose_sleepy;
        if ("teaser".equals(template)) return R.drawable.letty_pose_peek;
        return R.drawable.letty_pose_wave;
    }

    /**
     * Dress {@code n} in the template named in {@code data}. Returns false (and leaves {@code n}
     * untouched) when there is no template or it's one this build doesn't know.
     */
    public static boolean apply(@NonNull Context c, @NonNull NotificationCompat.Builder n,
                                @NonNull Map<String, String> data, @NonNull String title, @NonNull String body,
                                @NonNull PendingIntent tap) {
        String template = data.get("template");
        Theme t = theme(template);
        if (t == null) return false;

        String cta = data.get("cta");
        if (TextUtils.isEmpty(cta)) cta = c.getString(R.string.push_cta_default);
        int art = pose(data.get("art"), template);
        String eyebrow = c.getString(t.eyebrow);
        int count = parse(data.get("count"));
        if ("digest".equals(template) && count > 0) eyebrow = c.getString(R.string.push_eyebrow_digest_count, count);
        if ("streak".equals(template) && count > 0) eyebrow = c.getString(R.string.push_eyebrow_streak_count, count);

        // Collapsed + pop-up: the compact card, built to Android's 48dp cap (push_template_small.xml).
        RemoteViews small = new RemoteViews(c.getPackageName(), R.layout.push_template_small);
        small.setInt(R.id.push_card, "setBackgroundResource", t.smallBackground);
        small.setInt(R.id.push_badge, "setBackgroundResource", t.badge);
        small.setTextViewText(R.id.push_title, title);
        small.setTextColor(R.id.push_title, color(c, t.title));
        small.setTextViewText(R.id.push_body, TextUtils.isEmpty(body) ? eyebrow : body);
        small.setTextColor(R.id.push_body, color(c, t.smallBody));
        small.setImageViewResource(R.id.push_art, art);

        // Pop-up: the full card fitted to the heads-up height (push_template_popup.xml, 88dp).
        RemoteViews popup = new RemoteViews(c.getPackageName(), R.layout.push_template_popup);
        popup.setInt(R.id.push_card, "setBackgroundResource", t.background);
        popup.setTextViewText(R.id.push_eyebrow, eyebrow);
        popup.setTextColor(R.id.push_eyebrow, color(c, t.eyebrowColor));
        popup.setTextViewText(R.id.push_title, title);
        popup.setTextColor(R.id.push_title, color(c, t.title));
        popup.setTextViewText(R.id.push_body, body);
        popup.setTextColor(R.id.push_body, color(c, t.body));
        popup.setViewVisibility(R.id.push_body, TextUtils.isEmpty(body) ? android.view.View.GONE : android.view.View.VISIBLE);
        popup.setImageViewResource(R.id.push_art, art);
        popup.setTextViewText(R.id.push_cta, cta);
        popup.setTextColor(R.id.push_cta, color(c, t.ctaText));
        popup.setInt(R.id.push_cta, "setBackgroundResource", t.ctaBg);
        popup.setOnClickPendingIntent(R.id.push_cta, tap);

        RemoteViews big = new RemoteViews(c.getPackageName(), R.layout.push_template_big);
        big.setInt(R.id.push_card, "setBackgroundResource", t.background);
        big.setTextViewText(R.id.push_eyebrow, eyebrow);
        big.setTextColor(R.id.push_eyebrow, color(c, t.eyebrowColor));
        big.setTextViewText(R.id.push_title, title);
        big.setTextColor(R.id.push_title, color(c, t.title));
        big.setTextViewText(R.id.push_body, body);
        big.setTextColor(R.id.push_body, color(c, t.body));
        big.setViewVisibility(R.id.push_body, TextUtils.isEmpty(body) ? android.view.View.GONE : android.view.View.VISIBLE);
        big.setTextViewText(R.id.push_cta, cta);
        big.setTextColor(R.id.push_cta, color(c, t.ctaText));
        big.setInt(R.id.push_cta, "setBackgroundResource", t.ctaBg);
        big.setOnClickPendingIntent(R.id.push_cta, tap);
        big.setImageViewResource(R.id.push_art, art);

        n.setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(small)
                .setCustomHeadsUpContentView(popup)
                .setCustomBigContentView(big)
                .setLargeIcon((Bitmap) null)          // Letty is inside the card; no second picture in the header
                .setContentTitle(title)
                .setContentText(TextUtils.isEmpty(body) ? eyebrow : body);
        return true;
    }

    @ColorInt
    private static int color(Context c, int res) {
        return ContextCompat.getColor(c, res);
    }

    private static int parse(@Nullable String s) {
        try {
            return s == null ? 0 : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
