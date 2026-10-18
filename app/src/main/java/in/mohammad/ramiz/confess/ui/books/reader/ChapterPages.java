package in.mohammad.ramiz.confess.ui.books.reader;

import android.graphics.text.LineBreaker;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.style.LeadingMarginSpan;
import android.text.style.RelativeSizeSpan;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One chapter laid out into pages, the way an e-reader does it: the whole chapter is set once
 * as a StaticLayout (justified, hyphenated, first-line indents, the opening words in small
 * capitals) and then cut into pages of whole lines. The first page starts lower, under the
 * chapter heading. A page is identified by the character it starts with, so the reader keeps
 * its place when the text size changes.
 */
final class ChapterPages {

    final StaticLayout body;
    final StaticLayout title;
    /** Space the heading takes on the first page (from the top margin to the first line). */
    final int headingHeight;
    /** Where the heading starts below the top margin. */
    final int headingTop;
    private final int[] startLine;
    private final int[] endLine;               // exclusive

    private ChapterPages(StaticLayout body, StaticLayout title, int headingTop, int headingHeight,
                         int[] startLine, int[] endLine) {
        this.body = body;
        this.title = title;
        this.headingTop = headingTop;
        this.headingHeight = headingHeight;
        this.startLine = startLine;
        this.endLine = endLine;
    }

    int count() {
        return startLine.length;
    }

    int startLine(int page) {
        return startLine[page];
    }

    int endLine(int page) {
        return endLine[page];
    }

    /** The first character on a page. */
    int offsetOf(int page) {
        return body.getLineStart(startLine[Math.max(0, Math.min(page, count() - 1))]);
    }

    /** The page that shows this character. */
    int pageOf(int offset) {
        if (offset <= 0) return 0;
        int line = body.getLineForOffset(Math.min(offset, Math.max(0, body.getText().length() - 1)));
        for (int i = 0; i < count(); i++) if (line < endLine[i]) return i;
        return count() - 1;
    }

    @NonNull
    static ChapterPages build(@NonNull ReaderStyle s, @NonNull String text, @NonNull String chapterTitle,
                              int pageWidth, int pageHeight) {
        int width = Math.max(1, pageWidth - 2 * s.sidePx);
        int height = Math.max(1, pageHeight - s.topPx - s.bottomPx);

        CharSequence prepared = prepare(text, s.body.getTextSize());
        StaticLayout.Builder bb = StaticLayout.Builder.obtain(prepared, 0, prepared.length(), s.body, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0, s.lineSpacing)
                .setIncludePad(false)
                .setBreakStrategy(LineBreaker.BREAK_STRATEGY_HIGH_QUALITY)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL);
        if (s.justify) bb.setJustificationMode(LineBreaker.JUSTIFICATION_MODE_INTER_WORD);
        StaticLayout body = bb.build();

        StaticLayout title = StaticLayout.Builder.obtain(chapterTitle, 0, chapterTitle.length(), s.heading, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0, 1.1f)
                .setIncludePad(false)
                .build();

        float unit = s.body.getTextSize();
        int headingTop = Math.round(height * 0.10f);
        int labelH = Math.round(s.label.getFontSpacing());
        int headingHeight = headingTop + labelH + Math.round(unit * 0.9f) + title.getHeight()
                + Math.round(unit * 1.2f) + Math.round(unit * 1.6f);
        headingHeight = Math.min(headingHeight, Math.round(height * 0.6f));

        List<Integer> starts = new ArrayList<>();
        List<Integer> ends = new ArrayList<>();
        int lines = body.getLineCount();
        int line = 0;
        boolean first = true;
        while (line < lines || first) {
            int avail = first ? height - headingHeight : height;
            int top = line < lines ? body.getLineTop(line) : 0;
            int end = line;
            while (end < lines && body.getLineBottom(end) - top <= avail) end++;
            if (end == line && line < lines) end = line + 1;       // a line taller than the page: show it anyway
            // Like a printed book: don't strand a paragraph's last line at the top of the next page
            // (a widow) or its first line at the foot of this one (an orphan) — carry one more over.
            if (end < lines && end - line >= 4) {
                CharSequence txt = body.getText();
                if (paraEnd(body, txt, end) && !paraStart(body, txt, end)) end--;
                else if (paraStart(body, txt, end - 1) && !paraEnd(body, txt, end - 1)) end--;
            }
            starts.add(line);
            ends.add(end);
            line = end;
            first = false;
            if (lines == 0) break;
        }
        int[] s0 = new int[starts.size()], e0 = new int[ends.size()];
        for (int i = 0; i < s0.length; i++) {
            s0[i] = starts.get(i);
            e0[i] = ends.get(i);
        }
        return new ChapterPages(body, title, headingTop, headingHeight, s0, e0);
    }

    private static boolean paraStart(@NonNull StaticLayout l, @NonNull CharSequence t, int line) {
        int s = l.getLineStart(line);
        return s == 0 || t.charAt(s - 1) == '\n';
    }

    private static boolean paraEnd(@NonNull StaticLayout l, @NonNull CharSequence t, int line) {
        int e = l.getLineEnd(line);
        return e >= t.length() || t.charAt(e - 1) == '\n';
    }

    /** Words from a character to the end of the chapter (for "n min left"). */
    int wordsFrom(int offset) {
        CharSequence t = body.getText();
        int n = 0;
        boolean inWord = false;
        for (int i = Math.max(0, offset); i < t.length(); i++) {
            boolean space = Character.isWhitespace(t.charAt(i));
            if (!space && !inWord) n++;
            inWord = !space;
        }
        return n;
    }

    /**
     * Book typography for plain text: paragraphs after the first get a first-line indent
     * (blank lines are kept as the writer's breaks), and the first words are set in small caps.
     */
    @NonNull
    private static CharSequence prepare(@NonNull String raw, float textSize) {
        String text = raw.replace("\r\n", "\n").trim();
        SpannableStringBuilder out = new SpannableStringBuilder(text);
        int indent = Math.round(textSize * 1.4f);
        int start = 0;
        boolean firstPara = true;
        boolean prevBlank = false;
        while (start <= text.length()) {
            int end = text.indexOf('\n', start);
            if (end < 0) end = text.length();
            boolean blank = end == start;
            if (!blank) {
                if (!firstPara && !prevBlank) {
                    out.setSpan(new LeadingMarginSpan.Standard(indent, 0), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                firstPara = false;
            }
            prevBlank = blank;
            if (end == text.length()) break;
            start = end + 1;
        }
        // Opening words in small capitals (up to 3 words or the first line break).
        int words = 0, i = 0;
        while (i < text.length() && words < 3) {
            char c = text.charAt(i);
            if (c == '\n') break;
            if (c == ' ' && i > 0 && text.charAt(i - 1) != ' ') words++;
            i++;
        }
        if (i > 0) {
            out.replace(0, i, text.substring(0, i).toUpperCase(Locale.getDefault()));
            out.setSpan(new RelativeSizeSpan(0.82f), 0, Math.min(i, out.length()), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return out;
    }
}
