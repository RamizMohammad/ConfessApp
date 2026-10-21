package in.mohammad.ramiz.confess.core.push;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.InputStream;
import java.io.OutputStream;

import in.mohammad.ramiz.confess.R;

/**
 * The reader's notification sound: the phone's default, one of Confess's own sounds
 * ({@link SoundLibrary}), one of the phone's sounds, their own audio file, or silent.
 *
 * Android fixes a channel's sound when the channel is created — the app can't change it later.
 * So every change makes a fresh set of channels ("activity.v4", …) with the new sound and drops
 * the old ones ({@link Push#ensureChannels}), and the new suffix goes to the server with the push
 * token, so even pushes Android shows while the app is closed ring with it.
 *
 * Their own file is copied into the phone's Notifications/Confess folder first: Android plays
 * channel sounds itself and can't open a file that only this app may read.
 */
public final class NotificationSound {

    public static final int DEFAULT = 0, PHONE = 1, FILE = 2, SILENT = 3, LIBRARY = 4;

    private static final String PREFS = "notif_sound";
    /** Bigger than this isn't a notification sound. */
    private static final long MAX_FILE_BYTES = 8L * 1024 * 1024;

    private NotificationSound() { }

    /** Starts at 1: this version's channels replace the first ones (some phones had made those silent). */
    static int version(@NonNull Context c) {
        return prefs(c).getInt("version", 1);
    }

    /** Suffix of the current channel ids, sent to the server with the push token. */
    @NonNull
    public static String tag(@NonNull Context c) {
        return ".v" + version(c);
    }

    public static int kind(@NonNull Context c) {
        int k = prefs(c).getInt("kind", DEFAULT);
        return k >= DEFAULT && k <= LIBRARY ? k : DEFAULT;
    }

    /** The sound for the channels (null = silent). */
    @Nullable
    public static Uri uri(@NonNull Context c) {
        int k = kind(c);
        if (k == SILENT) return null;
        String s = prefs(c).getString("uri", null);
        if (k == DEFAULT || s == null) return Settings.System.DEFAULT_NOTIFICATION_URI;
        return Uri.parse(s);
    }

    @NonNull
    public static String label(@NonNull Context c) {
        int k = kind(c);
        if (k == DEFAULT) return c.getString(R.string.nsound_default);
        if (k == SILENT) return c.getString(R.string.nsound_silent);
        String l = prefs(c).getString("label", null);
        return l == null || l.trim().isEmpty() ? c.getString(R.string.nsound_custom) : l;
    }

    /** Use this sound from now on: new channels with it, and the server is told. */
    public static void choose(@NonNull Context c, int kind, @Nullable Uri uri, @Nullable String label) {
        Context app = c.getApplicationContext();
        prefs(app).edit()
                .putInt("kind", kind)
                .putString("uri", uri == null ? null : uri.toString())
                .putString("label", label)
                .putInt("version", version(app) + 1)
                .commit();                                   // the channels below read it at once
        Push.ensureChannels(app);
        Push.sync(app);                                      // registers the token with the new channel tag
    }

    /**
     * A picked audio file → a copy in Notifications/Confess that Android can play for our channels.
     * Null if it can't be read, is too big, or isn't audio.
     */
    @WorkerThread
    @Nullable
    public static Uri importFile(@NonNull Context c, @NonNull Uri picked) {
        ContentResolver cr = c.getContentResolver();
        String mime = cr.getType(picked);
        if (mime == null || !mime.startsWith("audio/")) return null;
        String name = displayName(c, picked);
        ContentValues v = new ContentValues();
        v.put(MediaStore.Audio.Media.DISPLAY_NAME, "Confess - " + name);
        v.put(MediaStore.Audio.Media.MIME_TYPE, mime);
        v.put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_NOTIFICATIONS + "/Confess");
        v.put(MediaStore.Audio.Media.IS_NOTIFICATION, 1);
        v.put(MediaStore.Audio.Media.IS_PENDING, 1);
        Uri out = null;
        try {
            out = cr.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), v);
            if (out == null) return null;
            long total = 0;
            try (InputStream in = cr.openInputStream(picked); OutputStream os = cr.openOutputStream(out)) {
                if (in == null || os == null) throw new IllegalStateException("can't open");
                byte[] buf = new byte[16 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > MAX_FILE_BYTES) throw new IllegalStateException("too big");
                    os.write(buf, 0, n);
                }
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.Audio.Media.IS_PENDING, 0);
            cr.update(out, done, null, null);
            return out;
        } catch (Exception e) {
            if (out != null) {
                try {
                    cr.delete(out, null, null);
                } catch (Exception ignored) { }
            }
            return null;
        }
    }

    /** "rain.mp3" → "rain". */
    @NonNull
    public static String displayName(@NonNull Context c, @NonNull Uri uri) {
        String name = null;
        try (Cursor cur = c.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cur != null && cur.moveToFirst()) name = cur.getString(0);
        } catch (Exception ignored) { }
        if (name == null || name.trim().isEmpty()) name = "sound";
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static SharedPreferences prefs(@NonNull Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
