package in.mohammad.ramiz.confess.ui.books.write;

import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.chip.Chip;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.CompositeDateValidator;
import com.google.android.material.datepicker.DateValidatorPointBackward;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Formats;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.model.BookDetail;
import in.mohammad.ramiz.confess.data.model.ChapterEntry;
import in.mohammad.ramiz.confess.data.model.ChapterRead;
import in.mohammad.ramiz.confess.data.model.ChapterRequest;
import in.mohammad.ramiz.confess.data.model.Post;
import in.mohammad.ramiz.confess.data.repo.BookRepository;
import in.mohammad.ramiz.confess.databinding.ActivityChapterEditorBinding;
import in.mohammad.ramiz.confess.ui.base.BaseActivity;
import in.mohammad.ramiz.confess.ui.books.BookUi;
import in.mohammad.ramiz.confess.ui.feed.Moods;
import in.mohammad.ramiz.confess.ui.util.EdgeInsets;
import in.mohammad.ramiz.confess.ui.util.SimpleTextWatcher;

/**
 * Write a new chapter or edit one, in two pages.
 *
 * Page 1 — write: the chapter's name (asked for when it's created, tap to change), the text on
 * ruled paper and the mood. While the keyboard is up the buttons step aside and the mood chips
 * move up under the top bar so they stay in reach. Buttons, side by side:
 *   new chapter       Instant publish (out now, responses on) | Publish →
 *   not out yet       Save (keeps its schedule)               | Publish →
 *   already out       Save                                    | Settings →
 *
 * Page 2 — before it goes out: responses, and when it comes out (now · a date and time up to 90
 * days ahead · draft). For a new chapter "Save & next" queues it and starts the next one — a
 * scheduled run moves on a day each time, so a whole run can be written in one sitting.
 */
public class ChapterEditorActivity extends BaseActivity {

    public static final String EXTRA_BOOK_ID = "book_id";
    /** 0 = a new chapter. */
    public static final String EXTRA_CHAPTER = "chapter_no";

    private ActivityChapterEditorBinding b;
    private final BookRepository books = new BookRepository();
    private String bookId;
    private int chapterNo;                     // the one being edited, or the number a new one gets
    private boolean isNew;
    private boolean released;
    @Nullable private String title;           // null / empty = "Chapter n"
    @Nullable private String originalTitle;
    @Nullable private String mood;
    @Nullable private ZonedDateTime when;
    private boolean busy = false;
    private boolean dirty = false;
    private boolean onOptions = false;
    private boolean imeUp = false;
    private int actionsPad;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityChapterEditorBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeInsets.padTop(b.topBar);
        actionsPad = b.actions.getPaddingBottom();
        android.content.res.ColorStateList paper = android.content.res.ColorStateList.valueOf(
                in.mohammad.ramiz.confess.ui.components.PaperTone.paper(this));
        b.paperFrame.setBackgroundTintList(paper);
        b.previewCard.setBackgroundTintList(paper);
        in.mohammad.ramiz.confess.ui.components.PaperTone.apply(b.paperFrame);
        in.mohammad.ramiz.confess.ui.components.PaperTone.apply(b.previewCard);
        b.body.setRuleColor(in.mohammad.ramiz.confess.ui.components.PaperTone.rule(this));
        watchKeyboard();

        bookId = getIntent().getStringExtra(EXTRA_BOOK_ID);
        chapterNo = getIntent().getIntExtra(EXTRA_CHAPTER, 0);
        isNew = chapterNo <= 0;
        if (bookId == null) {
            finish();
            return;
        }
        if (savedInstanceState != null) title = savedInstanceState.getString("title");

        b.back.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (onOptions) {
                    showOptions(false);
                } else if (dirty && hasText() && !busy) {
                    confirmDiscard();
                } else {
                    finish();
                }
            }
        });
        b.delete.setOnClickListener(v -> confirmDelete());
        b.titleRow.setOnClickListener(v -> askTitle(false));
        b.body.addTextChangedListener(new SimpleTextWatcher(t -> {
            dirty = true;
            b.error.setVisibility(View.GONE);
            renderCounter();
            renderActions();
        }));
        b.release.setOnCheckedChangeListener((g, id) -> {
            dirty = true;
            b.optionsError.setVisibility(View.GONE);
            if (id == R.id.releaseSchedule && when == null) pickTime();
            renderRelease();
        });
        b.allow.setOnCheckedChangeListener((v, on) -> {
            dirty = true;
            renderAllow();
        });
        b.when.setOnClickListener(v -> pickTime());
        b.secondary.setOnClickListener(v -> onSecondary());
        b.primary.setOnClickListener(v -> onPrimary());
        buildMoodChips();

        setEnabled(false);
        if (isNew) loadBook(); else loadChapter();
    }

    // ── Loading ───────────────────────────────────────────────────────────

    private void loadBook() {
        books.book(bookId, new ApiCallback<BookDetail>() {
            @Override
            public void onSuccess(BookDetail d) {
                if (isFinishing()) return;
                int max = 0;
                ZonedDateTime lastSlot = null;
                for (ChapterEntry c : d.chapters) {
                    max = Math.max(max, c.chapterNo);
                    OffsetDateTime t = Formats.parse(c.publishAt);
                    if (t != null) lastSlot = t.atZoneSameInstant(ZoneId.systemDefault());
                }
                chapterNo = max + 1;
                b.bookTitle.setText(d.book.title);
                // Writing the next of a scheduled run: suggest the day after the last slot.
                if (lastSlot != null) {
                    when = lastSlot.plusDays(1);
                    b.releaseSchedule.setChecked(true);
                }
                dirty = false;
                render();
                setEnabled(true);
                if (title == null || title.isEmpty()) askTitle(true);
            }

            @Override
            public void onError(ApiException e) {
                if (!isFinishing()) showError(BookUi.error(ChapterEditorActivity.this, e));
            }
        });
    }

    private void loadChapter() {
        books.chapter(bookId, chapterNo, new ApiCallback<ChapterRead>() {
            @Override
            public void onSuccess(ChapterRead cr) {
                if (isFinishing()) return;
                b.bookTitle.setText(cr.book.title);
                Post p = cr.chapter;
                b.body.setText(p.text);
                if (p.chapter != null) {
                    title = p.chapter.title;
                    originalTitle = p.chapter.title;
                    released = ChapterEntry.RELEASED.equals(p.chapter.state);
                    OffsetDateTime t = Formats.parse(p.chapter.publishAt);
                    if (t != null) when = t.atZoneSameInstant(ZoneId.systemDefault());
                    if (ChapterEntry.SCHEDULED.equals(p.chapter.state)) b.releaseSchedule.setChecked(true);
                    else if (ChapterEntry.DRAFT.equals(p.chapter.state)) b.releaseDraft.setChecked(true);
                }
                b.allow.setChecked(p.commentsEnabled);
                if (p.mood != null) selectMood(p.mood);
                dirty = false;
                render();
                setEnabled(true);
            }

            @Override
            public void onError(ApiException e) {
                if (!isFinishing()) showError(BookUi.error(ChapterEditorActivity.this, e));
            }
        });
    }

    // ── The chapter's name ────────────────────────────────────────────────

    /** Name the chapter. Required when creating one (Back leaves the screen), changeable any time. */
    private void askTitle(boolean required) {
        float d = getResources().getDisplayMetrics().density;
        TextInputLayout box = new TextInputLayout(this);
        box.setCounterEnabled(true);
        box.setCounterMaxLength(BookRepository.CHAPTER_TITLE_MAX);
        box.setHint(getString(R.string.chapter_name_hint));
        TextInputEditText input = new TextInputEditText(box.getContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(BookRepository.CHAPTER_TITLE_MAX)});
        input.setSingleLine(true);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        input.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(this, R.font.newsreader));
        if (title != null) input.setText(title);
        box.addView(input);
        android.widget.FrameLayout wrap = new android.widget.FrameLayout(this);
        int pad = Math.round(20 * d);
        wrap.setPadding(pad, Math.round(8 * d), pad, 0);
        wrap.addView(box, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dlg = new MaterialAlertDialogBuilder(this)
                .setTitle(required ? R.string.chapter_name_title : R.string.chapter_rename_title)
                .setMessage(required ? getString(R.string.chapter_name_body, chapterNo) : null)
                .setView(wrap)
                .setCancelable(!required)
                .setPositiveButton(required ? R.string.chapter_name_ok : R.string.action_save, (di, w) -> {
                    String t = input.getText() == null ? "" : input.getText().toString().trim();
                    if (!t.equals(title == null ? "" : title)) dirty = true;
                    title = t;
                    renderTitle();
                    if (required) focusBody();
                })
                .setNegativeButton(R.string.action_cancel, (di, w) -> {
                    if (required) finish();               // no name, no chapter
                })
                .create();
        dlg.setOnShowListener(x -> {
            Button ok = dlg.getButton(AlertDialog.BUTTON_POSITIVE);
            Runnable check = () -> ok.setEnabled(!required
                    || (input.getText() != null && input.getText().toString().trim().length() > 0));
            check.run();
            input.addTextChangedListener(new SimpleTextWatcher(t -> check.run()));
            input.requestFocus();
            input.setSelection(input.getText() == null ? 0 : input.getText().length());
            WindowCompat.getInsetsController(dlg.getWindow(), input).show(WindowInsetsCompat.Type.ime());
        });
        dlg.show();
    }

    private void focusBody() {
        b.body.requestFocus();
        b.body.postDelayed(() -> WindowCompat.getInsetsController(getWindow(), b.body)
                .show(WindowInsetsCompat.Type.ime()), 150);
    }

    // ── Pages + keyboard ──────────────────────────────────────────────────

    /** Keyboard up: buttons step aside, mood chips move under the top bar, page lifts above it. */
    private void watchKeyboard() {
        ViewCompat.setOnApplyWindowInsetsListener(b.root, (v, insets) -> {
            boolean up = insets.isVisible(WindowInsetsCompat.Type.ime());
            int ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            int nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            b.actions.setPadding(b.actions.getPaddingLeft(), b.actions.getPaddingTop(), b.actions.getPaddingRight(), actionsPad + nav);
            b.pages.setPadding(0, 0, 0, up ? ime : 0);
            if (up != imeUp) {
                imeUp = up;
                b.actions.setVisibility(up ? View.GONE : View.VISIBLE);
                placeMoods(up && !onOptions && !released);
            }
            return insets;
        });
    }

    private void placeMoods(boolean top) {
        ViewGroup parent = (ViewGroup) b.moodScroll.getParent();
        if (top && parent != b.moodTop) {
            parent.removeView(b.moodScroll);
            b.moodTop.addView(b.moodScroll);
            b.moodTop.setVisibility(View.VISIBLE);
            b.moodLabel.setVisibility(View.GONE);
        } else if (!top && parent == b.moodTop) {
            b.moodTop.removeView(b.moodScroll);
            b.writeColumn.addView(b.moodScroll, b.writeColumn.indexOfChild(b.moodLabel) + 1);
            b.moodTop.setVisibility(View.GONE);
            b.moodLabel.setVisibility(released ? View.GONE : View.VISIBLE);
        }
    }

    private void showOptions(boolean show) {
        if (show == onOptions || (show && busy)) return;
        if (show) {
            if (!hasText()) return;
            Haptics.click(this);
            WindowCompat.getInsetsController(getWindow(), b.body).hide(WindowInsetsCompat.Type.ime());
            b.previewLabel.setText(getString(R.string.reader_chapter_label, chapterNo));
            b.previewTitle.setText(BookUi.chapterTitle(this, chapterNo, title));
            b.previewText.setText(text());
        }
        onOptions = show;
        float w = b.pages.getWidth();
        if (show) {
            b.options.setVisibility(View.VISIBLE);
            b.options.setTranslationX(w);
            b.options.animate().translationX(0).setDuration(220).start();
        } else {
            b.options.animate().translationX(w).setDuration(200)
                    .withEndAction(() -> b.options.setVisibility(View.GONE)).start();
        }
        renderActions();
    }

    // ── Rendering ─────────────────────────────────────────────────────────

    private void render() {
        b.label.setText(isNew ? getString(R.string.chapter_new_label, chapterNo) : getString(R.string.chapter_edit_label, chapterNo));
        b.delete.setVisibility(isNew ? View.GONE : View.VISIBLE);
        b.releasedNote.setVisibility(released ? View.VISIBLE : View.GONE);
        b.body.setEnabled(!released);
        b.body.setAlpha(released ? 0.75f : 1f);
        b.releaseBox.setVisibility(released ? View.GONE : View.VISIBLE);
        b.optionsTitle.setText(released ? R.string.chapter_settings_title : R.string.chapter_release);
        b.moodLabel.setVisibility(released ? View.GONE : View.VISIBLE);
        b.moodScroll.setVisibility(released ? View.GONE : View.VISIBLE);
        renderTitle();
        renderCounter();
        renderAllow();
        renderRelease();
    }

    private void renderTitle() {
        boolean named = title != null && !title.trim().isEmpty();
        b.titleView.setText(named ? title : getString(R.string.chapter_default, Math.max(1, chapterNo)));
        b.titleView.setTextColor(getColor(named ? R.color.text_primary : R.color.text_muted));
    }

    private void renderCounter() {
        b.counter.setText(text().length() + " / " + BookRepository.CHAPTER_MAX);
    }

    private void renderAllow() {
        boolean listen = Moods.LISTEN.equals(mood);
        b.allowSub.setText(listen ? R.string.compose_allow_listen
                : b.allow.isChecked() ? R.string.compose_allow_on : R.string.compose_allow_off);
    }

    @NonNull
    private String release() {
        int id = b.release.getCheckedRadioButtonId();
        if (id == R.id.releaseSchedule) return ChapterRequest.SCHEDULE;
        if (id == R.id.releaseDraft) return ChapterRequest.DRAFT;
        return ChapterRequest.NOW;
    }

    private void renderRelease() {
        boolean schedule = ChapterRequest.SCHEDULE.equals(release());
        b.when.setVisibility(schedule ? View.VISIBLE : View.GONE);
        b.when.setText(when != null ? getString(R.string.chapter_scheduled_for, BookUi.when(iso(when)))
                : getString(R.string.chapter_pick_time));
        renderActions();
    }

    /** Side-by-side buttons for whichever page is showing. */
    private void renderActions() {
        if (onOptions) {
            if (isNew) {
                b.secondary.setText(R.string.chapter_save_next_short);
                b.secondary.setIcon(null);
            } else {
                b.secondary.setText(R.string.compose_options_back);
                b.secondary.setIconResource(R.drawable.ic_arrow_left);
            }
            String r = release();
            b.primary.setText(released ? R.string.chapter_save
                    : ChapterRequest.SCHEDULE.equals(r) ? R.string.chapter_schedule_btn
                    : ChapterRequest.DRAFT.equals(r) ? R.string.chapter_save_draft : R.string.chapter_publish);
            b.primary.setIcon(null);
        } else {
            if (isNew) {
                b.secondary.setText(R.string.compose_instant);
                b.secondary.setIcon(null);
            } else {
                b.secondary.setText(R.string.chapter_save);
                b.secondary.setIcon(null);
            }
            b.primary.setText(released ? R.string.chapter_settings : R.string.compose_publish_next);
            b.primary.setIconResource(R.drawable.ic_arrow_right);
        }
        boolean ok = !busy && hasText();
        b.primary.setEnabled(ok);
        b.secondary.setEnabled(ok || (onOptions && !isNew && !busy));
    }

    private void setEnabled(boolean on) {
        b.primary.setEnabled(on);
        b.secondary.setEnabled(on);
        if (on) renderActions();
    }

    private boolean hasText() {
        return !text().trim().isEmpty();
    }

    @NonNull
    private String text() {
        return b.body.getText() == null ? "" : b.body.getText().toString();
    }

    private void buildMoodChips() {
        for (String key : Moods.KEYS) {
            Chip chip = new Chip(this, null, com.google.android.material.R.attr.chipStyle);
            chip.setId(View.generateViewId());
            chip.setTag(key);
            chip.setText(Moods.emoji(key) + "  " + getString(Moods.label(key)));
            chip.setCheckable(true);
            chip.setCheckedIconVisible(false);
            chip.setOnClickListener(v -> {
                Haptics.tick(this);
                dirty = true;
                applyMood(chip.isChecked() ? key : null);
            });
            b.moods.addView(chip);
        }
    }

    private void selectMood(@NonNull String key) {
        for (int i = 0; i < b.moods.getChildCount(); i++) {
            View c = b.moods.getChildAt(i);
            if (c instanceof Chip && key.equals(c.getTag())) ((Chip) c).setChecked(true);
        }
        applyMood(key);
    }

    /** "Just listen" = reactions only, so responses switch off. */
    private void applyMood(@Nullable String key) {
        mood = key;
        boolean listen = Moods.LISTEN.equals(key);
        if (listen) b.allow.setChecked(false);
        b.allow.setEnabled(!listen);
        renderAllow();
    }

    // ── Picking a time ────────────────────────────────────────────────────

    private void pickTime() {
        long today = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        long last = LocalDate.now().plusDays(BookRepository.SCHEDULE_DAYS).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        List<CalendarConstraints.DateValidator> rules = new ArrayList<>();
        rules.add(DateValidatorPointForward.from(today));
        rules.add(DateValidatorPointBackward.before(last));
        ZonedDateTime start = when != null ? when : ZonedDateTime.now().plusDays(1).withHour(21).withMinute(0);
        MaterialDatePicker<Long> date = MaterialDatePicker.Builder.datePicker()
                .setSelection(start.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
                .setCalendarConstraints(new CalendarConstraints.Builder()
                        .setStart(today).setEnd(last).setValidator(CompositeDateValidator.allOf(rules)).build())
                .build();
        date.addOnPositiveButtonClickListener(sel -> {
            LocalDate day = Instant.ofEpochMilli(sel).atZone(ZoneOffset.UTC).toLocalDate();
            MaterialTimePicker time = new MaterialTimePicker.Builder()
                    .setTimeFormat(DateFormat.is24HourFormat(this) ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                    .setHour(start.getHour())
                    .setMinute(start.getMinute())
                    .build();
            time.addOnPositiveButtonClickListener(v -> {
                ZonedDateTime picked = day.atTime(time.getHour(), time.getMinute()).atZone(ZoneId.systemDefault());
                if (!picked.isAfter(ZonedDateTime.now().plusMinutes(1))) {
                    Toast.makeText(this, R.string.chapter_time_past, Toast.LENGTH_SHORT).show();
                    return;
                }
                when = picked.truncatedTo(ChronoUnit.MINUTES);
                dirty = true;
                renderRelease();
            });
            time.show(getSupportFragmentManager(), "time");
        });
        date.addOnNegativeButtonClickListener(v -> {
            if (when == null) b.releaseNow.setChecked(true);
        });
        date.show(getSupportFragmentManager(), "date");
    }

    @NonNull
    private static String iso(@NonNull ZonedDateTime t) {
        return t.toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    // ── Buttons ───────────────────────────────────────────────────────────

    private void onSecondary() {
        if (onOptions) {
            if (isNew) save(release(), true);           // Save & next
            else showOptions(false);                    // Edit
        } else if (isNew) {
            save(ChapterRequest.NOW, false);            // Instant publish
        } else {
            save(null, false);                          // Save, keep its schedule
        }
    }

    private void onPrimary() {
        if (onOptions) save(released ? null : release(), false);
        else showOptions(true);
    }

    // ── Saving ────────────────────────────────────────────────────────────

    /** rel = now / schedule / draft, or null to leave when it comes out unchanged. */
    private void save(@Nullable String rel, boolean thenNext) {
        if (busy) return;
        String body = text().trim();
        String name = title == null ? "" : title.trim();
        if (isNew && name.isEmpty()) {
            askTitle(true);
            return;
        }
        if (rel != null && ChapterRequest.SCHEDULE.equals(rel)) {
            if (when == null) {
                showError(getString(R.string.chapter_need_time));
                return;
            }
            if (!when.isAfter(ZonedDateTime.now().plusMinutes(1))) {
                showError(getString(R.string.chapter_time_past));
                return;
            }
        }

        ChapterRequest r = new ChapterRequest();
        if (!name.isEmpty()) {
            if (isNew || !name.equals(originalTitle)) r.title = name;
        } else if (!isNew && originalTitle != null) {
            r.clearTitle = true;
        }
        r.commentsEnabled = b.allow.isChecked();
        if (!released) {
            r.text = body;
            r.mood = mood;
            r.release = rel;
            if (ChapterRequest.SCHEDULE.equals(rel) && when != null) r.publishAt = iso(when);
        }

        busy = true;
        renderActions();
        b.error.setVisibility(View.GONE);
        b.optionsError.setVisibility(View.GONE);
        Haptics.click(this);
        final int no = chapterNo;
        final String sent = rel;
        ApiCallback<BookDetail> done = new ApiCallback<BookDetail>() {
            @Override
            public void onSuccess(BookDetail d) {
                busy = false;
                dirty = false;
                if (isFinishing()) return;
                int msg = sent == null ? R.string.chapter_saved
                        : ChapterRequest.SCHEDULE.equals(sent) ? R.string.chapter_scheduled_done
                        : ChapterRequest.DRAFT.equals(sent) ? R.string.chapter_drafted : R.string.chapter_published;
                Toast.makeText(ChapterEditorActivity.this, getString(msg, no), Toast.LENGTH_SHORT).show();
                if (thenNext && isNew) startNext(sent);
                else finish();
            }

            @Override
            public void onError(ApiException e) {
                busy = false;
                if (isFinishing()) return;
                renderActions();
                Haptics.reject(ChapterEditorActivity.this);
                showError(BookUi.error(ChapterEditorActivity.this, e));
            }
        };
        if (isNew) books.addChapter(bookId, r, done);
        else books.updateChapter(bookId, chapterNo, r, done);
    }

    /** Clear the page for the next chapter (and ask its name); a scheduled run moves on a day. */
    private void startNext(@Nullable String rel) {
        chapterNo++;
        b.body.setText("");
        title = null;
        if (ChapterRequest.SCHEDULE.equals(rel) && when != null) when = when.plusDays(1);
        dirty = false;
        showOptions(false);
        render();
        b.scroll.smoothScrollTo(0, 0);
        askTitle(true);
    }

    /** Errors show on the page that's open. */
    private void showError(@NonNull String text) {
        if (onOptions) {
            b.optionsError.setText(text);
            b.optionsError.setVisibility(View.VISIBLE);
            b.options.post(() -> b.options.smoothScrollTo(0, b.optionsError.getBottom()));
        } else {
            b.error.setText(text);
            b.error.setVisibility(View.VISIBLE);
            b.scroll.post(() -> b.scroll.smoothScrollTo(0, b.error.getBottom()));
        }
    }

    private void confirmDiscard() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.chapter_discard_title)
                .setMessage(R.string.chapter_discard_body)
                .setNegativeButton(R.string.chapter_keep, null)
                .setPositiveButton(R.string.chapter_discard, (d, w) -> finish())
                .show();
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.chapter_delete_title)
                .setMessage(R.string.chapter_delete_body)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete, (d, w) -> books.deleteChapter(bookId, chapterNo, new ApiCallback<BookDetail>() {
                    @Override
                    public void onSuccess(BookDetail detail) {
                        if (isFinishing()) return;
                        Toast.makeText(ChapterEditorActivity.this, R.string.chapter_deleted, Toast.LENGTH_SHORT).show();
                        finish();
                    }

                    @Override
                    public void onError(ApiException e) {
                        if (!isFinishing()) showError(BookUi.error(ChapterEditorActivity.this, e));
                    }
                }))
                .show();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("title", title);
    }
}
