package in.mohammad.ramiz.confess.ui.search;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.ArrayList;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.model.UserSearchResult;
import in.mohammad.ramiz.confess.data.model.UserSummary;
import in.mohammad.ramiz.confess.data.repo.UserRepository;
import in.mohammad.ramiz.confess.databinding.ActivitySearchBinding;
import in.mohammad.ramiz.confess.ui.base.BaseActivity;
import in.mohammad.ramiz.confess.ui.nav.Nav;
import in.mohammad.ramiz.confess.ui.util.EdgeInsets;
import in.mohammad.ramiz.confess.ui.util.SimpleTextWatcher;
import retrofit2.Call;

/**
 * S11b · Find people by alias (prefix match). Old: User_LookUp.
 * Searches as you type (debounced); tap a person → their profile.
 */
public class SearchActivity extends BaseActivity implements PersonAdapter.Listener {

    private static final long DEBOUNCE_MS = 350;

    private ActivitySearchBinding b;
    private final UserRepository users = new UserRepository();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private PersonAdapter adapter;
    @Nullable private Call<UserSearchResult> pending;
    private String lastQuery = "";
    private final Runnable run = this::search;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivitySearchBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeInsets.padTop(b.topBar);
        EdgeInsets.padBottom(b.list, true);

        adapter = new PersonAdapter(this);
        b.list.setLayoutManager(new LinearLayoutManager(this));
        b.list.setAdapter(adapter);

        b.back.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        b.clear.setOnClickListener(v -> b.input.setText(""));
        b.input.addTextChangedListener(new SimpleTextWatcher(t -> {
            b.clear.setVisibility(query().isEmpty() ? View.GONE : View.VISIBLE);
            handler.removeCallbacks(run);
            handler.postDelayed(run, DEBOUNCE_MS);
        }));
        b.input.setOnEditorActionListener((v, action, e) -> {
            boolean go = action == EditorInfo.IME_ACTION_SEARCH
                    || (e != null && e.getKeyCode() == KeyEvent.KEYCODE_ENTER);
            if (go) { handler.removeCallbacks(run); search(); }
            return go;
        });

        showIntro();
        if (savedInstanceState == null) {
            b.input.postDelayed(() -> {
                b.input.requestFocus();
                WindowCompat.getInsetsController(getWindow(), b.input).show(WindowInsetsCompat.Type.ime());
            }, 250);
        }
    }

    /** "@moon" → "moon"; only alias characters are useful. */
    private String query() {
        String q = b.input.getText() == null ? "" : b.input.getText().toString().trim();
        while (q.startsWith("@")) q = q.substring(1);
        return q;
    }

    private void search() {
        String q = query();
        if (q.equals(lastQuery) && pending == null && !adapter.getCurrentList().isEmpty()) return;
        lastQuery = q;
        if (pending != null) { pending.cancel(); pending = null; }
        if (q.isEmpty()) {
            b.progress.setVisibility(View.INVISIBLE);
            adapter.submitList(new ArrayList<>());
            showIntro();
            return;
        }
        b.progress.setVisibility(View.VISIBLE);
        pending = users.search(q, new ApiCallback<UserSearchResult>() {
            @Override
            public void onSuccess(UserSearchResult r) {
                pending = null;
                if (!q.equals(query())) return;                  // typed on meanwhile
                b.progress.setVisibility(View.INVISIBLE);
                adapter.submitList(new ArrayList<>(r.users));
                if (r.users.isEmpty()) {
                    showState(R.drawable.ic_user, getString(R.string.search_none_title, "@" + q),
                            getString(R.string.search_none_body));
                } else {
                    b.state.setVisibility(View.GONE);
                }
            }

            @Override
            public void onError(ApiException e) {
                pending = null;
                b.progress.setVisibility(View.INVISIBLE);
                adapter.submitList(new ArrayList<>());
                showState(e.isOffline() ? R.drawable.ic_wifi_off : R.drawable.ic_alert,
                        getString(R.string.feed_error_title), e.userMessage(SearchActivity.this));
            }
        });
    }

    private void showIntro() {
        showState(R.drawable.ic_search, getString(R.string.search_intro_title), getString(R.string.search_intro_body));
    }

    private void showState(int icon, String title, String body) {
        b.state.setVisibility(View.VISIBLE);
        b.stateIcon.setImageResource(icon);
        b.stateTitle.setText(title);
        b.stateBody.setText(body);
    }

    @Override
    public void onPerson(@NonNull UserSummary user) {
        Nav.profile(this, user.alias);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(run);
        if (pending != null) pending.cancel();
        super.onDestroy();
    }
}
