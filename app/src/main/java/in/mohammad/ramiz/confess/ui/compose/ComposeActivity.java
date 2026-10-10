package in.mohammad.ramiz.confess.ui.compose;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.core.PostEvents;
import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.api.ErrorCode;
import in.mohammad.ramiz.confess.data.drafts.Drafts;
import in.mohammad.ramiz.confess.data.model.CreatePostRequest;
import in.mohammad.ramiz.confess.data.model.Me;
import in.mohammad.ramiz.confess.data.model.Post;
import in.mohammad.ramiz.confess.data.repo.PostRepository;
import in.mohammad.ramiz.confess.data.repo.UserRepository;
import in.mohammad.ramiz.confess.data.session.SessionManager;
import in.mohammad.ramiz.confess.databinding.ActivityComposeBinding;
import in.mohammad.ramiz.confess.ui.base.BaseActivity;
import in.mohammad.ramiz.confess.ui.entry.GuidelinesActivity;
import in.mohammad.ramiz.confess.ui.feed.Moods;
import in.mohammad.ramiz.confess.ui.util.EdgeInsets;
import in.mohammad.ramiz.confess.ui.util.SimpleTextWatcher;
import retrofit2.Call;

/**
 * S8 · Write a confession. Old: CreatePost (EditText + "Enable Comments" switch + Toasts).
 *
 *  - the note shows exactly how it will appear: your aura, @alias, the time
 *  - 2000-character limit (server's POST_MAX) with a live counter; over the limit → blocked
 *  - "Allow responses" = commentsEnabled
 *  - every confession is moderated on the server before it is saved:
 *      451 CONTENT_FLAGGED      → red notice with the specific reason + guidelines link
 *      503 MODERATION_UNAVAILABLE → amber notice + try again (nothing was posted)
 *  - the draft is kept on this phone while you write, and restored next time
 *  - success → RESULT_OK; the feed puts it on top (via PostEvents)
 */
public class ComposeActivity extends BaseActivity {

    /** Open this draft (from Me → Drafts) instead of a blank page. */
    public static final String EXTRA_DRAFT_ID = "draft_id";
    private static final String STATE_DRAFT_ID = "draft_id";
    private static final String STATE_LETTER = "letter_on";
    private static final String STATE_POLL = "poll_on";
    private static final String STATE_CHOICES = "poll_choices";
    private static final String STATE_MOOD = "mood";
    private static final int POLL_MIN = 2, POLL_MAX = 4, CHOICE_MAX = 60;

    public static final int MAX_CHARS = 2000;
    private static final int WARN_AT = 1800;
    private static final long DRAFT_DELAY_MS = 700;

    private ActivityComposeBinding b;
    private final PostRepository posts = new PostRepository();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable saveDraft = this::saveDraftNow;
    @Nullable private String userId;
    /** The draft being written (Drafts store); null until there's text. */
    @Nullable private String draftId;
    private Drafts drafts;
    @Nullable private Call<Post> pending;
    private boolean working = false;
    private boolean finished = false;
    @Nullable private String mood;
    /** "Allow responses" as the writer left it, before "just listen" turned it off. */
    private boolean allowBeforeListen = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityComposeBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        drafts = Drafts.get(this);
        if (savedInstanceState != null) draftId = savedInstanceState.getString(STATE_DRAFT_ID);
        EdgeInsets.padTop(b.topBar);
        EdgeInsets.padBottom(b.bottom, true);

        Me me = SessionManager.get().cachedMe();
        renderAuthor(me);
        if (me == null) {
            new UserRepository().me(new ApiCallback<Me>() {
                @Override public void onSuccess(Me m) { renderAuthor(m); }
                @Override public void onError(ApiException e) { }
            });
        }
        b.time.setText(timeLabel());
        b.countMax.setText(getString(R.string.compose_counter_max, MAX_CHARS));

        if (savedInstanceState == null) restoreDraft();
        setUpExtras(savedInstanceState);
        updateCounter();
        updateAllowText();

        b.input.addTextChangedListener(new SimpleTextWatcher(t -> {
            updateCounter();
            if (b.moderation.getVisibility() == View.VISIBLE && !working) hideNotice();
            b.draftStamp.setVisibility(View.INVISIBLE);
            handler.removeCallbacks(saveDraft);
            handler.postDelayed(saveDraft, DRAFT_DELAY_MS);
        }));
        b.allow.setOnCheckedChangeListener((v, on) -> {
            Haptics.tick(this);
            updateAllowText();
            handler.removeCallbacks(saveDraft);
            handler.postDelayed(saveDraft, DRAFT_DELAY_MS);
        });
        b.allowText.setOnClickListener(v -> b.allow.toggle());

        b.release.setOnClickListener(v -> release());
        b.discard.setOnClickListener(v -> discard());
        b.help.setOnClickListener(v -> openGuidelines());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { leave(); }
        });

        if (savedInstanceState == null) {
            b.input.postDelayed(() -> {
                if (isFinishing()) return;
                b.input.requestFocus();
                b.input.setSelection(text().length());
                WindowCompat.getInsetsController(getWindow(), b.input).show(WindowInsetsCompat.Type.ime());
            }, 350);
        }
    }

    // ── Header ───────────────────────────────────────────────────────────────

    private void renderAuthor(@Nullable Me me) {
        if (me == null) return;
        userId = me.id;
        b.avatar.setAvatar(me.avatar);
        b.alias.setText("@" + me.alias);
    }

    /** "Tonight · 02:48" between 8 pm and 5 am, otherwise "Today · 14:20" — in the phone's clock format. */
    private String timeLabel() {
        LocalTime now = LocalTime.now();
        String pattern = DateFormat.is24HourFormat(this) ? "HH:mm" : "h:mm a";
        String clock = now.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()));
        boolean night = now.getHour() >= 20 || now.getHour() < 5;
        return getString(night ? R.string.compose_tonight : R.string.compose_today, clock);
    }

    // ── Text, counter, switch ────────────────────────────────────────────────

    private String text() {
        return b.input.getText() == null ? "" : b.input.getText().toString();
    }

    private void updateCounter() {
        int n = text().trim().length();
        int over = n - MAX_CHARS;
        int color = over > 0 ? R.color.brick : (n >= WARN_AT ? R.color.amber : R.color.text_secondary);
        b.count.setText(getString(R.string.compose_counter, n));
        b.count.setTextColor(ContextCompat.getColor(this, n == 0 ? R.color.text_muted : color));
        b.tooLong.setVisibility(over > 0 ? View.VISIBLE : View.GONE);
        if (over > 0) b.tooLong.setText(getString(R.string.compose_too_long, over));
        updateButton();
    }

    private void updateButton() {
        int n = text().trim().length();
        b.release.setEnabled(!working && n > 0 && n <= MAX_CHARS);
    }

    private void updateAllowText() {
        if (Moods.LISTEN.equals(mood)) {
            b.allowSub.setText(R.string.compose_allow_listen);
            return;
        }
        b.allowSub.setText(b.allow.isChecked() ? R.string.compose_allow_on : R.string.compose_allow_off);
    }

    // ── Extras: unsent letter · poll · mood ──────────────────────────────────

    private void setUpExtras(@Nullable Bundle state) {
        buildMoodChips();

        b.letterToggle.addOnCheckedChangeListener((btn, on) -> {
            b.letterRow.setVisibility(on ? View.VISIBLE : View.GONE);
            if (on && btn.isPressed()) {
                Haptics.tick(this);
                b.letterInput.requestFocus();
                WindowCompat.getInsetsController(getWindow(), b.letterInput).show(WindowInsetsCompat.Type.ime());
            }
        });
        b.letterInput.addTextChangedListener(new SimpleTextWatcher(t -> {
            if (b.moderation.getVisibility() == View.VISIBLE && !working) hideNotice();
        }));

        b.pollToggle.addOnCheckedChangeListener((btn, on) -> {
            b.pollEditor.setVisibility(on ? View.VISIBLE : View.GONE);
            if (on && b.pollOptions.getChildCount() == 0) {
                for (int i = 0; i < POLL_MIN; i++) addChoice(null);
            }
            if (on && btn.isPressed()) {
                Haptics.tick(this);
                b.scroll.post(() -> b.scroll.smoothScrollTo(0, b.pollEditor.getTop()));
                if (b.pollOptions.getChildCount() > 0) choiceInput(0).requestFocus();
            }
        });
        b.addOption.setOnClickListener(v -> {
            if (b.pollOptions.getChildCount() >= POLL_MAX) return;
            Haptics.tick(this);
            addChoice(null).requestFocus();
        });

        if (state != null) {
            ArrayList<String> choices = state.getStringArrayList(STATE_CHOICES);
            if (choices != null) for (String c : choices) addChoice(c);
            String m = state.getString(STATE_MOOD);
            if (m != null) selectMood(m);
            b.letterToggle.setChecked(state.getBoolean(STATE_LETTER));
            b.pollToggle.setChecked(state.getBoolean(STATE_POLL));
        }
        refreshChoices();
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

    /** "Just listen" means no replies — the switch turns off and locks until another mood is picked. */
    private void applyMood(@Nullable String key) {
        boolean wasListen = Moods.LISTEN.equals(mood);
        mood = key;
        boolean listen = Moods.LISTEN.equals(key);
        if (listen && !wasListen) {
            allowBeforeListen = b.allow.isChecked();
            b.allow.setChecked(false);
        } else if (!listen && wasListen) {
            b.allow.setChecked(allowBeforeListen);
        }
        b.allow.setEnabled(!working && !listen);
        b.allowText.setEnabled(!listen);
        updateAllowText();
    }

    @NonNull
    private EditText addChoice(@Nullable String value) {
        int index = b.pollOptions.getChildCount();
        float d = getResources().getDisplayMetrics().density;

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = Math.round(8 * d);
        row.setLayoutParams(rowLp);

        EditText input = new EditText(this);
        input.setBackgroundResource(R.drawable.bg_input_pill);
        input.setPadding(Math.round(16 * d), Math.round(12 * d), Math.round(16 * d), Math.round(12 * d));
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        input.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        input.setHintTextColor(ContextCompat.getColor(this, R.color.text_muted));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(CHOICE_MAX)});
        input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        if (value != null) input.setText(value);
        input.addTextChangedListener(new SimpleTextWatcher(t -> {
            if (b.moderation.getVisibility() == View.VISIBLE && !working) hideNotice();
        }));
        row.addView(input, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        ImageButton remove = new ImageButton(this);
        remove.setImageResource(R.drawable.ic_close);
        remove.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.text_secondary)));
        TypedValue ripple = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true);
        remove.setBackgroundResource(ripple.resourceId);
        remove.setContentDescription(getString(R.string.compose_poll_remove));
        int size = Math.round(40 * d);
        LinearLayout.LayoutParams removeLp = new LinearLayout.LayoutParams(size, size);
        removeLp.setMarginStart(Math.round(4 * d));
        remove.setOnClickListener(v -> {
            if (b.pollOptions.getChildCount() <= POLL_MIN) return;
            Haptics.tick(this);
            b.pollOptions.removeView(row);
            refreshChoices();
        });
        row.addView(remove, removeLp);

        b.pollOptions.addView(row, index);
        refreshChoices();
        return input;
    }

    @NonNull
    private EditText choiceInput(int i) {
        return (EditText) ((LinearLayout) b.pollOptions.getChildAt(i)).getChildAt(0);
    }

    /** Re-number hints, show ✕ only above two choices, hide "Add" at four. */
    private void refreshChoices() {
        int n = b.pollOptions.getChildCount();
        for (int i = 0; i < n; i++) {
            LinearLayout row = (LinearLayout) b.pollOptions.getChildAt(i);
            ((EditText) row.getChildAt(0)).setHint(getString(R.string.compose_poll_hint, i + 1));
            row.getChildAt(1).setVisibility(n > POLL_MIN ? View.VISIBLE : View.GONE);
        }
        b.addOption.setVisibility(n < POLL_MAX ? View.VISIBLE : View.GONE);
    }

    @NonNull
    private ArrayList<String> rawChoices() {
        ArrayList<String> out = new ArrayList<>();
        for (int i = 0; i < b.pollOptions.getChildCount(); i++) {
            CharSequence t = choiceInput(i).getText();
            out.add(t == null ? "" : t.toString());
        }
        return out;
    }

    /** The poll to send, or null when the choices aren't valid (fewer than two distinct, non-empty). */
    @Nullable
    private CreatePostRequest.PollIn pollOrNull() {
        Set<String> seen = new LinkedHashSet<>();
        List<String> choices = new ArrayList<>();
        for (String raw : rawChoices()) {
            String c = raw.trim().replaceAll("\\s+", " ");
            if (c.isEmpty()) continue;
            if (!seen.add(c.toLowerCase(Locale.ROOT))) return null;
            choices.add(c);
        }
        if (choices.size() < POLL_MIN) return null;
        int checked = b.pollDuration.getCheckedChipId();
        int hours = checked == R.id.dur7 ? 168 : checked == R.id.dur3 ? 72 : 24;
        return new CreatePostRequest.PollIn(choices, hours);
    }

    @Nullable
    private String letterTo() {
        if (!b.letterToggle.isChecked()) return null;
        CharSequence t = b.letterInput.getText();
        String to = t == null ? "" : t.toString().trim();
        return to;
    }

    // ── Draft ────────────────────────────────────────────────────────────────

    /**
     * Which text to start with:
     *   opened from Drafts (EXTRA_DRAFT_ID)       → that draft
     *   left the composer less than a minute ago  → continue that draft
     *   otherwise                                 → a blank page (drafts wait in Me → Drafts)
     */
    private void restoreDraft() {
        String asked = getIntent().getStringExtra(EXTRA_DRAFT_ID);
        Drafts.Draft draft = asked != null ? drafts.find(userId, asked) : drafts.takeResumable(userId);
        if (draft == null) return;
        draftId = draft.id;
        b.input.setText(draft.text);
        b.allow.setChecked(draft.allowResponses);
        showStamp(R.string.compose_draft_restored);
    }

    private void saveDraftNow() {
        if (finished || b == null) return;
        draftId = drafts.save(userId, draftId, text(), b.allow.isChecked());
        if (draftId != null) showStamp(R.string.compose_draft_kept);
    }

    private void showStamp(int res) {
        b.draftStamp.setText(res);
        if (b.draftStamp.getVisibility() != View.VISIBLE) {
            b.draftStamp.setAlpha(0f);
            b.draftStamp.setVisibility(View.VISIBLE);
            b.draftStamp.animate().alpha(1f).setDuration(200).start();
        }
    }

    // ── Release ──────────────────────────────────────────────────────────────

    private void release() {
        String body = text().trim();
        if (working || body.isEmpty() || body.length() > MAX_CHARS) return;

        String to = letterTo();
        if (to != null && to.isEmpty()) {
            Haptics.reject(this);
            b.letterInput.setError(getString(R.string.compose_letter_invalid));
            b.letterInput.requestFocus();
            return;
        }
        CreatePostRequest.PollIn poll = null;
        if (b.pollToggle.isChecked()) {
            poll = pollOrNull();
            if (poll == null) {
                Haptics.reject(this);
                Snackbar.make(b.getRoot(), R.string.compose_poll_invalid, Snackbar.LENGTH_LONG)
                        .setAnchorView(b.bottom)
                        .show();
                return;
            }
        }
        boolean allow = b.allow.isChecked() && !Moods.LISTEN.equals(mood);
        CreatePostRequest request = new CreatePostRequest(body, allow, mood, to, poll);

        handler.removeCallbacks(saveDraft);
        saveDraftNow();                         // safe even if the app dies mid-request
        hideNotice();
        setWorking(true);
        Haptics.click(this);

        pending = posts.create(request, new ApiCallback<Post>() {
            @Override
            public void onSuccess(Post post) {
                finished = true;
                drafts.delete(userId, draftId);
                PostEvents.created(post);
                setResult(RESULT_OK);
                close();
            }

            @Override
            public void onError(ApiException e) {
                setWorking(false);
                handleError(e);
            }
        });
    }

    private void handleError(ApiException e) {
        if (e.is(ErrorCode.CONTENT_FLAGGED)) {
            Haptics.reject(this);
            if (Moderation.isEmptyAfterCleaning(e)) {
                showNotice(false, R.string.mod_flagged_title, getString(R.string.compose_empty_after_clean), false);
            } else {
                showNotice(false, R.string.mod_flagged_title, getString(Moderation.reason(e)), true);
            }
        } else if (e.is(ErrorCode.MODERATION_UNAVAILABLE)) {
            showNotice(true, R.string.mod_unavailable_title, getString(R.string.mod_unavailable_body), false);
        } else {
            Snackbar.make(b.getRoot(), e.userMessage(this), Snackbar.LENGTH_LONG)
                    .setAnchorView(b.bottom)
                    .show();
        }
    }

    /** Red (rejected) or amber (couldn't check) notice under the note. */
    private void showNotice(boolean soft, int title, String body, boolean guidelinesLink) {
        b.moderation.setBackgroundResource(soft ? R.drawable.bg_plate_amber : R.drawable.bg_plate_error);
        int tint = ContextCompat.getColor(this, soft ? R.color.amber : R.color.brick);
        b.modIcon.setImageResource(soft ? R.drawable.ic_shield : R.drawable.ic_alert);
        b.modIcon.setImageTintList(ColorStateList.valueOf(tint));
        b.modTitle.setText(title);
        b.modBody.setText(body);

        if (soft) {
            b.modAction.setVisibility(View.VISIBLE);
            b.modAction.setText(R.string.action_retry);
            b.modAction.setIconResource(R.drawable.ic_refresh);
            b.modAction.setOnClickListener(v -> release());
        } else if (guidelinesLink) {
            b.modAction.setVisibility(View.VISIBLE);
            b.modAction.setText(R.string.mod_review);
            b.modAction.setIconResource(R.drawable.ic_arrow_right);
            b.modAction.setOnClickListener(v -> openGuidelines());
        } else {
            b.modAction.setVisibility(View.GONE);
        }

        b.moderation.setAlpha(0f);
        b.moderation.setVisibility(View.VISIBLE);
        b.moderation.animate().alpha(1f).setDuration(200).start();
        b.scroll.post(() -> b.scroll.smoothScrollTo(0, b.moderation.getBottom()));
    }

    private void hideNotice() {
        b.moderation.setVisibility(View.GONE);
    }

    private void setWorking(boolean on) {
        working = on;
        b.release.setText(on ? R.string.compose_checking : R.string.compose_release);
        b.release.setIconResource(on ? 0 : R.drawable.ic_send);
        b.input.setEnabled(!on);
        b.allow.setEnabled(!on && !Moods.LISTEN.equals(mood));
        b.discard.setEnabled(!on);
        b.letterInput.setEnabled(!on);
        b.letterToggle.setEnabled(!on);
        b.pollToggle.setEnabled(!on);
        b.addOption.setEnabled(!on);
        for (int i = 0; i < b.pollOptions.getChildCount(); i++) {
            LinearLayout row = (LinearLayout) b.pollOptions.getChildAt(i);
            row.getChildAt(0).setEnabled(!on);
            row.getChildAt(1).setEnabled(!on);
        }
        for (int i = 0; i < b.moods.getChildCount(); i++) b.moods.getChildAt(i).setEnabled(!on);
        for (int i = 0; i < b.pollDuration.getChildCount(); i++) b.pollDuration.getChildAt(i).setEnabled(!on);
        b.paper.animate().alpha(on ? 0.7f : 1f).setDuration(200).start();
        updateButton();
    }

    // ── Leaving ──────────────────────────────────────────────────────────────

    /** Back: whatever was written is kept in Drafts (and resumes if they come straight back). */
    private void leave() {
        if (working) return;
        handler.removeCallbacks(saveDraft);
        finished = true;
        draftId = drafts.save(userId, draftId, text(), b.allow.isChecked());
        if (draftId != null) {
            drafts.markLeft(userId, draftId);
            Toast.makeText(this, R.string.compose_saved_to_drafts, Toast.LENGTH_SHORT).show();
        }
        close();
    }

    /** The Discard button: throw this one away (asks first when there's text). */
    private void discard() {
        if (working) return;
        if (text().trim().isEmpty()) {
            drafts.delete(userId, draftId);
            finished = true;
            close();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.compose_discard_title)
                .setMessage(R.string.compose_discard_body)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.compose_leave_discard, (d, w) -> {
                    handler.removeCallbacks(saveDraft);
                    finished = true;
                    drafts.delete(userId, draftId);
                    close();
                })
                .show();
    }

    private void openGuidelines() {
        startActivity(new Intent(this, GuidelinesActivity.class).putExtra(GuidelinesActivity.EXTRA_READ_ONLY, true));
    }

    private void close() {
        finish();
        overridePendingTransition(R.anim.stay, R.anim.slide_down_out);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(STATE_DRAFT_ID, draftId);
        out.putBoolean(STATE_LETTER, b.letterToggle.isChecked());
        out.putBoolean(STATE_POLL, b.pollToggle.isChecked());
        out.putStringArrayList(STATE_CHOICES, rawChoices());
        out.putString(STATE_MOOD, mood);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!finished) {
            handler.removeCallbacks(saveDraft);
            draftId = drafts.save(userId, draftId, text(), b.allow.isChecked());
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(saveDraft);
        if (pending != null && !finished) pending.cancel();
        super.onDestroy();
    }
}
