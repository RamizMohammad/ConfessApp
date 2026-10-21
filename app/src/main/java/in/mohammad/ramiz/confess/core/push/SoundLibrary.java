package in.mohammad.ramiz.confess.core.push;

import android.content.ContentResolver;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import in.mohammad.ramiz.confess.R;

/**
 * Confess's own notification sounds (res/raw/notif_*.ogg, made for the app — no licences).
 *
 * Channels get them as android.resource://<package>/raw/<name>: by name, not by R.raw id, so a
 * channel made by an older build still finds its sound after an update (ids can shift between
 * builds). res/raw/keep.xml stops resource shrinking from dropping them.
 */
public final class SoundLibrary {

    public static final class Sound {
        @NonNull public final String raw;
        @StringRes public final int name;
        @StringRes public final int about;

        Sound(@NonNull String raw, @StringRes int name, @StringRes int about) {
            this.raw = raw;
            this.name = name;
            this.about = about;
        }

        @NonNull
        public Uri uri(@NonNull Context c) {
            return new Uri.Builder()
                    .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                    .authority(c.getPackageName())
                    .appendPath("raw")
                    .appendPath(raw)
                    .build();
        }
    }

    /** In the order the sheet shows them; Letty (the signature sound) first. */
    public static final List<Sound> ALL = Collections.unmodifiableList(Arrays.asList(
            new Sound("notif_letty", R.string.slib_letty, R.string.slib_letty_sub),
            new Sound("notif_sealed", R.string.slib_sealed, R.string.slib_sealed_sub),
            new Sound("notif_ember", R.string.slib_ember, R.string.slib_ember_sub),
            new Sound("notif_whisper", R.string.slib_whisper, R.string.slib_whisper_sub),
            new Sound("notif_lantern", R.string.slib_lantern, R.string.slib_lantern_sub),
            new Sound("notif_paper_plane", R.string.slib_paper_plane, R.string.slib_paper_plane_sub),
            new Sound("notif_hush", R.string.slib_hush, R.string.slib_hush_sub),
            new Sound("notif_midnight", R.string.slib_midnight, R.string.slib_midnight_sub)));

    private SoundLibrary() { }

    /** The library sound in use right now, if the reader picked one. */
    @Nullable
    public static Sound current(@NonNull Context c) {
        if (NotificationSound.kind(c) != NotificationSound.LIBRARY) return null;
        Uri now = NotificationSound.uri(c);
        if (now == null) return null;
        for (Sound s : ALL) if (s.uri(c).equals(now)) return s;
        return null;
    }

    /** Plays sounds for the picker, one at a time. */
    public static final class Preview {
        @Nullable private Ringtone playing;

        public void play(@NonNull Context c, @NonNull Uri uri) {
            stop();
            try {
                Ringtone r = RingtoneManager.getRingtone(c, uri);
                if (r == null) return;
                r.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
                r.play();
                playing = r;
            } catch (Exception ignored) {
                // no audio service / muted by policy — the pick still works
            }
        }

        public void stop() {
            if (playing != null) {
                try {
                    playing.stop();
                } catch (Exception ignored) { }
                playing = null;
            }
        }
    }
}
