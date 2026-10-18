package in.mohammad.ramiz.confess.data.books;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import in.mohammad.ramiz.confess.data.model.Book;

/**
 * Where I am in each book, on this phone — like an e-reader's "last read" position.
 * Kept per account; the most recent first; only the last {@link #MAX} books.
 * The server separately remembers my furthest chapter (for "new chapter" badges).
 */
public final class ReadingShelf {

    private static final String PREFS = "reading_shelf";
    private static final int MAX = 20;
    private static final Gson GSON = new Gson();

    /** One book I've opened. offset = character in the chapter where my page starts. */
    public static class Entry {
        public String bookId;
        public String title;
        public int cover;
        public String alias;
        public int chapterNo;
        @Nullable public String chapterTitle;
        public int offset;
        /** 0–1 through the current chapter. */
        public float chapterProgress;
        /** Released chapters when last seen. */
        public int chapters;
        /** Index (1-based) of the current chapter among the released ones. */
        public int chapterIndex;
        public boolean completed;
        public long at;

        /** 0–1 through the whole book. */
        public float bookProgress() {
            if (chapters <= 0) return 0f;
            return Math.max(0f, Math.min(1f, ((chapterIndex - 1) + chapterProgress) / chapters));
        }
    }

    private final SharedPreferences prefs;
    private final String key;

    private ReadingShelf(@NonNull Context ctx, @Nullable String userId) {
        prefs = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        key = "u_" + (userId == null ? "anon" : userId);
    }

    public static ReadingShelf get(@NonNull Context ctx, @Nullable String userId) {
        return new ReadingShelf(ctx, userId);
    }

    @NonNull
    public List<Entry> recent() {
        String raw = prefs.getString(key, null);
        if (raw == null) return new ArrayList<>();
        try {
            List<Entry> list = GSON.fromJson(raw, new TypeToken<List<Entry>>() { }.getType());
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    @Nullable
    public Entry find(@NonNull String bookId) {
        for (Entry e : recent()) if (bookId.equals(e.bookId)) return e;
        return null;
    }

    /** Save my place (moves the book to the front). */
    public void save(@NonNull Book book, int chapterNo, @Nullable String chapterTitle, int chapterIndex,
                     int offset, float chapterProgress) {
        List<Entry> list = recent();
        Entry e = null;
        for (Iterator<Entry> it = list.iterator(); it.hasNext(); ) {
            Entry x = it.next();
            if (book.bookId.equals(x.bookId)) {
                e = x;
                it.remove();
            }
        }
        if (e == null) e = new Entry();
        e.bookId = book.bookId;
        e.title = book.title;
        e.cover = book.cover;
        e.alias = book.author != null ? book.author.alias : "";
        e.chapterNo = chapterNo;
        e.chapterTitle = chapterTitle;
        e.chapterIndex = Math.max(1, chapterIndex);
        e.offset = Math.max(0, offset);
        e.chapterProgress = Math.max(0f, Math.min(1f, chapterProgress));
        e.chapters = book.chapterCount;
        e.completed = book.isCompleted();
        e.at = System.currentTimeMillis();
        list.add(0, e);
        while (list.size() > MAX) list.remove(list.size() - 1);
        prefs.edit().putString(key, GSON.toJson(list)).apply();
    }

    /** The book is gone (deleted / hidden / blocked) — forget it. */
    public void remove(@NonNull String bookId) {
        List<Entry> list = recent();
        boolean changed = false;
        for (Iterator<Entry> it = list.iterator(); it.hasNext(); ) {
            if (bookId.equals(it.next().bookId)) {
                it.remove();
                changed = true;
            }
        }
        if (changed) prefs.edit().putString(key, GSON.toJson(list)).apply();
    }
}
