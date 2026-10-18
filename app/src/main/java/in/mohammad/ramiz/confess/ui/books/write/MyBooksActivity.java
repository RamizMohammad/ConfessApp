package in.mohammad.ramiz.confess.ui.books.write;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.model.Book;
import in.mohammad.ramiz.confess.data.model.BookPage;
import in.mohammad.ramiz.confess.data.repo.BookRepository;
import in.mohammad.ramiz.confess.databinding.ActivityMyBooksBinding;
import in.mohammad.ramiz.confess.databinding.ItemMyBookBinding;
import in.mohammad.ramiz.confess.ui.base.BaseActivity;
import in.mohammad.ramiz.confess.ui.books.BookUi;
import in.mohammad.ramiz.confess.ui.nav.Nav;
import in.mohammad.ramiz.confess.ui.util.EdgeInsets;
import in.mohammad.ramiz.confess.ui.util.Motion;

/** Books → Your books: drafts, ongoing and complete books, each opening its writer's desk. */
public class MyBooksActivity extends BaseActivity {

    private ActivityMyBooksBinding b;
    private final BookRepository books = new BookRepository();
    private final List<Book> items = new ArrayList<>();
    private boolean animatedIn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityMyBooksBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeInsets.padTop(b.topBar);
        EdgeInsets.padBottom(b.list, false);
        b.back.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        b.create.setOnClickListener(v -> Nav.bookSetup(this, null));
        b.createEmpty.setOnClickListener(v -> Nav.bookSetup(this, null));
        b.list.setLayoutManager(new LinearLayoutManager(this));
        b.list.setAdapter(new Adapter());
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        books.mine(new ApiCallback<BookPage>() {
            @Override
            public void onSuccess(BookPage page) {
                if (isFinishing()) return;
                b.progress.setVisibility(View.GONE);
                items.clear();
                items.addAll(page.books);
                //noinspection ConstantConditions
                b.list.getAdapter().notifyDataSetChanged();
                b.state.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                if (!animatedIn && !items.isEmpty()) {
                    animatedIn = true;
                    Motion.enterList(b.list);
                }
            }

            @Override
            public void onError(ApiException e) {
                if (isFinishing()) return;
                b.progress.setVisibility(View.GONE);
                Snackbar.make(b.root, BookUi.error(MyBooksActivity.this, e), Snackbar.LENGTH_LONG)
                        .setAction(R.string.action_retry, v -> load()).show();
            }
        });
    }

    private final class Adapter extends RecyclerView.Adapter<Adapter.Holder> {

        final class Holder extends RecyclerView.ViewHolder {
            final ItemMyBookBinding b;

            Holder(ItemMyBookBinding b) {
                super(b.getRoot());
                this.b = b;
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemMyBookBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            Book book = items.get(position);
            h.b.cover.bind(book);
            h.b.title.setText(book.title);
            h.b.status.setText(book.isCompleted() ? R.string.mybooks_status_complete
                    : book.isDraft() ? R.string.mybooks_status_draft : R.string.mybooks_status_ongoing);
            h.b.meta.setText(summary(book));
            h.b.card.setOnClickListener(v -> Nav.bookEditor(MyBooksActivity.this, book.bookId));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    /** "5 out · 2 scheduled · 1 draft · next Sat 18 Oct · 9:00 pm" */
    @NonNull
    private String summary(@NonNull Book book) {
        List<String> parts = new ArrayList<>();
        if (Boolean.TRUE.equals(book.hidden)) parts.add(getString(R.string.mybooks_hidden));
        if (book.chapterCount > 0) parts.add(getString(R.string.mybooks_out, book.chapterCount));
        if (book.scheduledCount != null && book.scheduledCount > 0) parts.add(getString(R.string.mybooks_scheduled, book.scheduledCount));
        if (book.draftCount != null && book.draftCount > 0) parts.add(getString(R.string.mybooks_drafts, book.draftCount));
        if (book.nextReleaseAt != null) parts.add(getString(R.string.mybooks_next, BookUi.when(book.nextReleaseAt)));
        if (parts.isEmpty()) return getString(R.string.desk_empty).split("\\.")[0];
        return android.text.TextUtils.join(" · ", parts);
    }
}
