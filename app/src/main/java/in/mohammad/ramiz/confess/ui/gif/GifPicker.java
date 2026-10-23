package in.mohammad.ramiz.confess.ui.gif;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.data.gif.GiphyClient;
import in.mohammad.ramiz.confess.data.model.Gif;
import in.mohammad.ramiz.confess.databinding.DialogGifPickerBinding;
import in.mohammad.ramiz.confess.databinding.ItemGifTileBinding;
import okhttp3.Call;

/**
 * Letty × GIPHY: a bottom sheet with a search bar and a grid of GIFs (trending until you
 * search). Tap one to use it. Used by the composer (GIF confessions) and the response bar.
 *
 * Searches GIPHY directly (GiphyClient) a moment after typing stops, or at once with the
 * keyboard's search key.
 *
 * Attribution follows GIPHY's rules: GIPHY's own mark in the header and "Powered by GIPHY" at the
 * bottom. Drop the official images from giphy-attribution-marks.zip in as
 * res/drawable-nodpi/giphy_logo.png and giphy_powered_by.png and they're used automatically;
 * until then their names are written out.
 */
public final class GifPicker {

    public interface Listener {
        void onGif(@NonNull Gif gif);
    }

    private static final long SEARCH_DELAY_MS = 400;

    private final Context ctx;
    private final Listener listener;
    private final DialogGifPickerBinding b;
    private final BottomSheetDialog dialog;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Gif> gifs = new ArrayList<>();
    private final Adapter adapter = new Adapter();
    private String query = "";
    private int nextOffset = 0;
    private boolean more = false, loading = false;
    @Nullable private Call call;

    public static void show(@NonNull Context ctx, @NonNull Listener listener) {
        show(ctx, listener, null);
    }

    /** {@code onClosed}: runs when the sheet goes away (after {@code listener} if a GIF was picked). */
    public static void show(@NonNull Context ctx, @NonNull Listener listener, @Nullable Runnable onClosed) {
        new GifPicker(ctx, listener, onClosed);
    }

    private GifPicker(@NonNull Context ctx, @NonNull Listener listener, @Nullable Runnable onClosed) {
        this.ctx = ctx;
        this.listener = listener;
        b = DialogGifPickerBinding.inflate(LayoutInflater.from(ctx));
        dialog = new BottomSheetDialog(ctx);
        int height = Math.round(ctx.getResources().getDisplayMetrics().heightPixels * 0.82f);
        dialog.setContentView(b.getRoot(), new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
        BottomSheetBehavior<?> behavior = dialog.getBehavior();
        behavior.setPeekHeight(height);
        behavior.setSkipCollapsed(true);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        brandMarks();

        b.close.setOnClickListener(v -> dialog.dismiss());
        StaggeredGridLayoutManager grid = new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        grid.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS);
        b.grid.setLayoutManager(grid);
        b.grid.setAdapter(adapter);
        b.grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0 || loading || !more) return;
                int[] last = grid.findLastVisibleItemPositions(null);
                int max = 0;
                for (int p : last) max = Math.max(max, p);
                if (max >= gifs.size() - 4) load(false);
            }
        });
        b.search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int e) { }
            @Override public void onTextChanged(CharSequence s, int a, int c, int e) { }
            @Override public void afterTextChanged(android.text.Editable s) {
                handler.removeCallbacksAndMessages(null);
                String t = GiphyClient.normalise(s.toString());
                handler.postDelayed(() -> setQuery(t), t.isEmpty() ? 0 : SEARCH_DELAY_MS);
            }
        });
        b.search.setOnEditorActionListener((v, id, ev) -> {
            if (id != EditorInfo.IME_ACTION_SEARCH) return false;
            handler.removeCallbacksAndMessages(null);
            setQuery(GiphyClient.normalise(b.search.getText() == null ? "" : b.search.getText().toString()));
            hideKeyboard();
            return true;
        });
        dialog.setOnDismissListener(x -> {
            handler.removeCallbacksAndMessages(null);
            if (call != null) call.cancel();
            if (onClosed != null) onClosed.run();
        });

        dialog.show();
        if (!GiphyClient.configured()) {
            state(ctx.getString(R.string.gif_not_set_up));
            b.search.setEnabled(false);
            return;
        }
        load(true);
    }

    /** GIPHY's own marks if the official images are in the app; their names otherwise. */
    private void brandMarks() {
        int logo = ctx.getResources().getIdentifier("giphy_logo", "drawable", ctx.getPackageName());
        if (logo != 0) {
            b.giphyLogo.setImageResource(logo);
            b.giphyLogo.setVisibility(View.VISIBLE);
            b.giphyName.setVisibility(View.GONE);
        }
        int powered = ctx.getResources().getIdentifier("giphy_powered_by", "drawable", ctx.getPackageName());
        if (powered != 0) {
            b.poweredMark.setImageResource(powered);
            b.poweredMark.setVisibility(View.VISIBLE);
            b.poweredText.setVisibility(View.GONE);
        }
    }

    private void setQuery(@NonNull String q) {
        if (q.equals(query)) return;
        query = q;
        b.heading.setText(q.isEmpty() ? ctx.getString(R.string.gif_trending) : ctx.getString(R.string.gif_results, q));
        load(true);
    }

    private void load(boolean fresh) {
        if (call != null) call.cancel();
        if (fresh) {
            int n = gifs.size();
            gifs.clear();
            adapter.notifyItemRangeRemoved(0, n);
            nextOffset = 0;
            more = false;
            b.loading.setVisibility(View.VISIBLE);
            b.state.setVisibility(View.GONE);
        }
        loading = true;
        call = GiphyClient.load(query, nextOffset, new GiphyClient.Callback() {
            @Override
            public void onResult(@NonNull List<Gif> page, int next, boolean hasMore) {
                loading = false;
                b.loading.setVisibility(View.GONE);
                int start = gifs.size();
                gifs.addAll(page);
                adapter.notifyItemRangeInserted(start, page.size());
                nextOffset = next;
                more = hasMore;
                if (gifs.isEmpty()) state(ctx.getString(R.string.gif_none));
                if (fresh) b.grid.scrollToPosition(0);
            }

            @Override
            public void onError() {
                loading = false;
                more = false;
                b.loading.setVisibility(View.GONE);
                if (gifs.isEmpty()) state(ctx.getString(R.string.gif_error));
            }
        });
    }

    private void state(@NonNull String text) {
        b.state.setText(text);
        b.state.setVisibility(View.VISIBLE);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(b.search.getWindowToken(), 0);
    }

    private final class Adapter extends RecyclerView.Adapter<Adapter.Holder> {

        final class Holder extends RecyclerView.ViewHolder {
            final ItemGifTileBinding t;

            Holder(ItemGifTileBinding t) {
                super(t.getRoot());
                this.t = t;
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemGifTileBinding t = ItemGifTileBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            t.tile.setMaxRatio(1.6f);
            return new Holder(t);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            Gif g = gifs.get(position);
            GifViews.show(h.t.tile, g, true);
            h.t.tile.setOnClickListener(v -> {
                Haptics.click(ctx);
                hideKeyboard();
                listener.onGif(g);
                dialog.dismiss();
            });
        }

        @Override
        public void onViewRecycled(@NonNull Holder h) {
            GifViews.show(h.t.tile, null, true);
        }

        @Override
        public int getItemCount() {
            return gifs.size();
        }
    }
}
