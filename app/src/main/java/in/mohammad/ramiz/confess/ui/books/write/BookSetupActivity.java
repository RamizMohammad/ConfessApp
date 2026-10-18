package in.mohammad.ramiz.confess.ui.books.write;

import android.os.Bundle;
import android.text.Editable;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.model.BookDetail;
import in.mohammad.ramiz.confess.data.model.BookRequest;
import in.mohammad.ramiz.confess.data.model.Me;
import in.mohammad.ramiz.confess.data.repo.BookRepository;
import in.mohammad.ramiz.confess.data.session.SessionManager;
import in.mohammad.ramiz.confess.databinding.ActivityBookSetupBinding;
import in.mohammad.ramiz.confess.ui.base.BaseActivity;
import in.mohammad.ramiz.confess.ui.books.BookCoverView;
import in.mohammad.ramiz.confess.ui.books.BookUi;
import in.mohammad.ramiz.confess.ui.nav.Nav;
import in.mohammad.ramiz.confess.ui.stories.StoryStyles;
import in.mohammad.ramiz.confess.ui.util.EdgeInsets;
import in.mohammad.ramiz.confess.ui.util.SimpleTextWatcher;

/**
 * Start a book (title, optional blurb, one of five covers — the story palette) or edit one.
 * The cover previews live as you type. A new book opens straight onto its writer's desk.
 */
public class BookSetupActivity extends BaseActivity {

    /** Set to edit that book; absent for a new one. */
    public static final String EXTRA_BOOK_ID = "book_id";

    private ActivityBookSetupBinding b;
    private final BookRepository books = new BookRepository();
    @Nullable private String bookId;
    private int cover = 0;
    private String alias = "";
    private boolean busy = false;
    private final BookCoverView[] options = new BookCoverView[StoryStyles.COUNT];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityBookSetupBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeInsets.padTop(b.topBar);
        EdgeInsets.padBottom(b.actions, true);

        bookId = getIntent().getStringExtra(EXTRA_BOOK_ID);
        Me me = SessionManager.get().cachedMe();
        alias = me != null ? me.alias : "";
        if (savedInstanceState != null) cover = savedInstanceState.getInt("cover", 0);

        b.screenTitle.setText(bookId == null ? R.string.setup_title_new : R.string.setup_title_edit);
        b.screenSummary.setVisibility(View.GONE);
        b.save.setText(bookId == null ? R.string.setup_create : R.string.setup_save);
        b.back.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        b.save.setOnClickListener(v -> save());
        b.titleInput.addTextChangedListener(new SimpleTextWatcher(t -> {
            preview();
            b.error.setVisibility(View.GONE);
            updateSave();
        }));
        buildCovers();
        preview();
        updateSave();

        if (bookId != null) {
            b.save.setEnabled(false);
            books.book(bookId, new ApiCallback<BookDetail>() {
                @Override
                public void onSuccess(BookDetail d) {
                    if (isFinishing()) return;
                    b.titleInput.setText(d.book.title);
                    b.blurbInput.setText(d.book.blurb);
                    cover = d.book.cover;
                    selectCover(cover);
                    updateSave();
                }

                @Override
                public void onError(ApiException e) {
                    if (!isFinishing()) showError(BookUi.error(BookSetupActivity.this, e));
                }
            });
        }
    }

    private void buildCovers() {
        int gap = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics()));
        for (int i = 0; i < StoryStyles.COUNT; i++) {
            BookCoverView v = new BookCoverView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) lp.setMarginStart(gap);
            v.setLayoutParams(lp);
            v.setPadding(gap / 2, gap / 2, gap / 2, gap / 2);
            v.setForeground(getDrawable(R.drawable.bg_cover_option));
            v.setElevation(gap / 2f);
            final int style = i;
            v.setOnClickListener(x -> {
                Haptics.tick(this);
                cover = style;
                selectCover(style);
            });
            options[i] = v;
            b.covers.addView(v);
        }
        selectCover(cover);
    }

    private void selectCover(int style) {
        for (int i = 0; i < options.length; i++) options[i].setSelected(i == style);
        preview();
    }

    private void preview() {
        String t = text(b.titleInput.getText());
        String shown = t.isEmpty() ? getString(R.string.setup_title_preview) : t;
        b.preview.set(cover, shown, alias);
        for (int i = 0; i < options.length; i++) if (options[i] != null) options[i].set(i, shown, alias);
    }

    private void updateSave() {
        b.save.setEnabled(!busy && !text(b.titleInput.getText()).isEmpty());
    }

    @NonNull
    private static String text(@Nullable Editable e) {
        return e == null ? "" : e.toString().trim();
    }

    private void save() {
        String title = text(b.titleInput.getText());
        String blurb = text(b.blurbInput.getText());
        if (title.isEmpty() || busy) return;
        busy = true;
        updateSave();
        b.error.setVisibility(View.GONE);
        Haptics.click(this);
        ApiCallback<BookDetail> done = new ApiCallback<BookDetail>() {
            @Override
            public void onSuccess(BookDetail d) {
                busy = false;
                if (isFinishing()) return;
                if (bookId == null) Nav.bookEditor(BookSetupActivity.this, d.book.bookId);
                setResult(RESULT_OK);
                finish();
            }

            @Override
            public void onError(ApiException e) {
                busy = false;
                if (isFinishing()) return;
                updateSave();
                Haptics.reject(BookSetupActivity.this);
                showError(BookUi.error(BookSetupActivity.this, e));
            }
        };
        if (bookId == null) {
            books.create(title, blurb, cover, done);
        } else {
            BookRequest r = new BookRequest();
            r.title = title;
            r.blurb = blurb;
            r.cover = cover;
            books.update(bookId, r, done);
        }
    }

    private void showError(@NonNull String text) {
        b.error.setText(text);
        b.error.setVisibility(View.VISIBLE);
        b.scroll.post(() -> b.scroll.smoothScrollTo(0, b.error.getBottom()));
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("cover", cover);
    }
}
