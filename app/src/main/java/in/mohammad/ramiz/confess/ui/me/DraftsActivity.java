package in.mohammad.ramiz.confess.ui.me;

import android.graphics.Canvas;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import java.util.List;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.data.drafts.Drafts;
import in.mohammad.ramiz.confess.data.model.Me;
import in.mohammad.ramiz.confess.data.session.SessionManager;
import in.mohammad.ramiz.confess.databinding.ActivityDraftsBinding;
import in.mohammad.ramiz.confess.ui.base.BaseActivity;
import in.mohammad.ramiz.confess.ui.nav.Nav;
import in.mohammad.ramiz.confess.ui.util.EdgeInsets;
import in.mohammad.ramiz.confess.ui.util.Motion;

/**
 * Me → Drafts. Unsent confessions on this phone (encrypted, never uploaded).
 * Tap to continue writing; swipe either way or tap the bin to delete (with undo).
 */
public class DraftsActivity extends BaseActivity implements DraftsAdapter.Listener {

    private ActivityDraftsBinding b;
    private DraftsAdapter adapter;
    private Drafts drafts;
    @Nullable private String userId;
    private boolean animatedIn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityDraftsBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeInsets.padTop(b.topBar);
        EdgeInsets.padBottom(b.list, false);

        drafts = Drafts.get(this);
        Me me = SessionManager.get().cachedMe();
        userId = me == null ? null : me.id;

        b.back.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        adapter = new DraftsAdapter(this);
        b.list.setLayoutManager(new LinearLayoutManager(this));
        b.list.setAdapter(adapter);

        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh, @NonNull RecyclerView.ViewHolder t) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
                int pos = vh.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) onDelete(adapter.itemAt(pos), pos);
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh,
                                    float dX, float dY, int state, boolean active) {
                // Fade as it slides away.
                vh.itemView.setAlpha(1f - Math.min(1f, Math.abs(dX) / vh.itemView.getWidth()));
                super.onChildDraw(c, rv, vh, dX, dY, state, active);
            }

            @Override
            public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                super.clearView(rv, vh);
                vh.itemView.setAlpha(1f);
            }
        }).attachToRecyclerView(b.list);
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        List<Drafts.Draft> list = drafts.list(userId);
        boolean first = !animatedIn && !list.isEmpty();
        adapter.submitList(list, () -> {
            if (first) {
                animatedIn = true;
                Motion.enterList(b.list);
            }
        });
        b.state.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onOpen(@NonNull Drafts.Draft draft) {
        Nav.composeDraft(this, draft.id);
    }

    @Override
    public void onDelete(@NonNull Drafts.Draft draft, int position) {
        Haptics.tick(this);
        drafts.delete(userId, draft.id);
        load();
        Snackbar.make(b.root, R.string.drafts_deleted, Snackbar.LENGTH_LONG)
                .setAction(R.string.drafts_undo, v -> {
                    drafts.restore(userId, draft);
                    load();
                })
                .show();
    }
}
