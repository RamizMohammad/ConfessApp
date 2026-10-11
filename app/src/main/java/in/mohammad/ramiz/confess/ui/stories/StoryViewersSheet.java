package in.mohammad.ramiz.confess.ui.stories;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Formats;
import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.api.ErrorCode;
import in.mohammad.ramiz.confess.data.model.StoryViewers;
import in.mohammad.ramiz.confess.data.repo.StoryRepository;
import in.mohammad.ramiz.confess.databinding.ItemFollowPersonBinding;
import in.mohammad.ramiz.confess.databinding.SheetStoryViewersBinding;
import in.mohammad.ramiz.confess.ui.nav.Nav;
import retrofit2.Call;

/** "Seen by" for one of my stories — newest first, pages in as you scroll. */
final class StoryViewersSheet {

    private final Context ctx;
    private final String storyId;
    private final StoryRepository repo;
    private final BottomSheetDialog dialog;
    private final SheetStoryViewersBinding b;
    private final Adapter adapter = new Adapter();
    @Nullable private String cursor;
    private boolean loading, done;
    @Nullable private Call<StoryViewers> call;

    StoryViewersSheet(@NonNull Context ctx, @NonNull StoryRepository repo, @NonNull String storyId, int knownCount) {
        this.ctx = ctx;
        this.repo = repo;
        this.storyId = storyId;
        dialog = new BottomSheetDialog(ctx);
        b = SheetStoryViewersBinding.inflate(LayoutInflater.from(ctx));
        dialog.setContentView(b.getRoot());
        setTitle(knownCount);

        LinearLayoutManager lm = new LinearLayoutManager(ctx);
        b.list.setLayoutManager(lm);
        b.list.setAdapter(adapter);
        b.list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy > 0 && lm.findLastVisibleItemPosition() >= adapter.getItemCount() - 5) load();
            }
        });
        dialog.setOnDismissListener(d -> {
            if (call != null) call.cancel();
        });
    }

    void setOnDismiss(@NonNull Runnable r) {
        dialog.setOnDismissListener(d -> {
            if (call != null) call.cancel();
            r.run();
        });
    }

    void show() {
        dialog.show();
        dialog.getBehavior().setSkipCollapsed(false);
        load();
    }

    private void setTitle(int count) {
        b.title.setText(count > 0 ? ctx.getString(R.string.story_seen_by, count) : ctx.getString(R.string.story_viewers_title));
    }

    private void load() {
        if (loading || done) return;
        loading = true;
        boolean first = cursor == null;
        call = repo.viewers(storyId, cursor, new ApiCallback<StoryViewers>() {
            @Override public void onSuccess(StoryViewers page) {
                loading = false;
                call = null;
                b.loading.setVisibility(View.GONE);
                if (first) setTitle(page.viewCount);
                adapter.add(page.users);
                cursor = page.nextCursor;
                done = cursor == null;
                boolean empty = adapter.getItemCount() == 0;
                b.empty.setText(R.string.story_seen_by_none);
                b.empty.setVisibility(empty ? View.VISIBLE : View.GONE);
            }
            @Override public void onError(ApiException e) {
                loading = false;
                call = null;
                b.loading.setVisibility(View.GONE);
                if (e.is(ErrorCode.NOT_FOUND)) {
                    done = true;
                    b.empty.setText(R.string.story_ended);
                    b.empty.setVisibility(View.VISIBLE);
                } else if (first) {
                    b.empty.setText(e.userMessage(ctx));
                    b.empty.setVisibility(View.VISIBLE);
                } else {
                    Toast.makeText(ctx, R.string.story_viewers_more_error, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private final class Adapter extends RecyclerView.Adapter<Holder> {
        private final List<StoryViewers.Viewer> rows = new ArrayList<>();

        void add(@Nullable List<StoryViewers.Viewer> more) {
            if (more == null || more.isEmpty()) return;
            int start = rows.size();
            rows.addAll(more);
            notifyItemRangeInserted(start, more.size());
        }

        @Override public int getItemCount() { return rows.size(); }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemFollowPersonBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            StoryViewers.Viewer v = rows.get(position);
            h.b.avatar.setAvatar(v.avatar);
            h.b.alias.setText("@" + v.alias);
            h.b.about.setText(v.reaction != null
                    ? in.mohammad.ramiz.confess.ui.feed.Reactions.emoji(v.reaction) + "  " + Formats.relative(v.viewedAt)
                    : Formats.relative(v.viewedAt));
            h.b.about.setVisibility(View.VISIBLE);
            h.b.followBtn.setVisibility(View.GONE);
            h.b.followingBtn.setVisibility(View.GONE);
            h.b.row.setOnClickListener(x -> {
                if (v.alias == null) return;
                dialog.dismiss();
                Nav.profile(ctx, v.alias);
            });
        }
    }

    private static final class Holder extends RecyclerView.ViewHolder {
        final ItemFollowPersonBinding b;

        Holder(ItemFollowPersonBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
