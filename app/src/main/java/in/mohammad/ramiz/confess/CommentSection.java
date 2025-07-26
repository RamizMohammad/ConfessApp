package in.mohammad.ramiz.confess;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import in.mohammad.ramiz.confess.adapters.CommentAdapter;
import in.mohammad.ramiz.confess.commentlogic.CommentData;
import in.mohammad.ramiz.confess.commentlogic.GetCommentResponse;
import in.mohammad.ramiz.confess.commentlogic.GetCommentsRequest;
import in.mohammad.ramiz.confess.entities.CreateCommentRequest;
import in.mohammad.ramiz.confess.entities.CreateCommentResponse;
import in.mohammad.ramiz.confess.haptics.VibManager;
import in.mohammad.ramiz.confess.popups.OkPopUp;
import in.mohammad.ramiz.confess.popups.OnlyLoader;
import in.mohammad.ramiz.confess.server.Endpoints;
import in.mohammad.ramiz.confess.server.ServerConfigs;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CommentSection extends AppCompatActivity {

    private ImageView e1, e2, e3, e4, e5, e6, sendButton;
    private EditText comment;
    private RecyclerView recyclerView;
    private CommentAdapter adapter;
    private List<CommentData> comments = new ArrayList<>();

    private boolean isLoading = false;
    private boolean hasMore = true;
    private String lastCommentDate = "empty";

    private String postId;
    private OnlyLoader loader;
    private Endpoints endpoints;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        setContentView(R.layout.activity_comment_section);

        Intent intent = getIntent();
        postId = intent.getStringExtra("postId");

        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        endpoints = ServerConfigs.getInstance().create(Endpoints.class);

        sendButton = findViewById(R.id.sendButton);
        comment = findViewById(R.id.comment);
        recyclerView = findViewById(R.id.recycleView);

        e1 = findViewById(R.id.e1);
        e2 = findViewById(R.id.e2);
        e3 = findViewById(R.id.e3);
        e4 = findViewById(R.id.e4);
        e5 = findViewById(R.id.e5);
        e6 = findViewById(R.id.e6);

        setupEmojiInsertion(e1, "❤️");
        setupEmojiInsertion(e2, "🤗");
        setupEmojiInsertion(e3, "😇");
        setupEmojiInsertion(e4, "😂");
        setupEmojiInsertion(e5, "😔");
        setupEmojiInsertion(e6, "😏");

        adapter = new CommentAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        setupRecyclerScrollListener();

        adapter.showInitialShimmer(); // show shimmer on first load
        getComments(postId, lastCommentDate, true); // true = initial


        sendButton.setOnClickListener(v -> {
            VibManager.vibrateTick(this);
            loader = new OnlyLoader(CommentSection.this, R.raw.loading_animation);

            if (account != null) {
                String email = account.getEmail();
                String commentData = comment.getText().toString();
                if (!TextUtils.isEmpty(commentData)) {
                    addComment(email, commentData, (isPosted, label) -> {
                        comment.setText("");
                        loader.dismiss();
                        if (isPosted && "safe".equals(label)) {
                            Toast.makeText(CommentSection.this, "Comment added", Toast.LENGTH_SHORT).show();
                            comments.clear();
                            lastCommentDate = "empty";
                            hasMore = true;
                            adapter.showInitialShimmer();
                            getComments(postId, lastCommentDate, true);
                        } else if (!"safe".equals(label)) {
                            new OkPopUp(CommentSection.this, R.raw.error_animation, "We found " + label + " in your comment");
                        } else {
                            new OkPopUp(CommentSection.this, R.raw.error_animation, "We are unable to add comment for now");
                        }
                    });
                } else {
                    new OkPopUp(CommentSection.this, R.raw.file_not_found, "Field is empty");
                }
            }
        });
    }

    private void setupEmojiInsertion(ImageView emojiButton, String emoji) {
        emojiButton.setOnClickListener(v -> {
            int start = Math.max(comment.getSelectionStart(), 0);
            int end = Math.max(comment.getSelectionEnd(), 0);
            comment.getText().replace(Math.min(start, end), Math.max(start, end), emoji, 0, emoji.length());
        });
    }

    private void setupRecyclerScrollListener() {
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (!recyclerView.canScrollVertically(1) && !isLoading && hasMore) {
                    adapter.showFooterShimmer(true);
                    getComments(postId, lastCommentDate, false);
                }
            }
        });
    }

    private void getComments(String postId, String date, boolean isInitialLoad) {
        isLoading = true;

        GetCommentsRequest request = new GetCommentsRequest(postId, date);
        Call<GetCommentResponse> call = endpoints.getComment(BuildConfig.CLIENT_API, request);

        call.enqueue(new Callback<GetCommentResponse>() {
            @Override
            public void onResponse(Call<GetCommentResponse> call, Response<GetCommentResponse> response) {
                isLoading = false;
                adapter.showFooterShimmer(false);

                if (response.isSuccessful() && response.body() != null && "done".equals(response.body().getStatus())) {
                    List<CommentData> newComments = response.body().getComments();

                    if (newComments != null && !newComments.isEmpty()) {
                        comments.addAll(newComments);
                        lastCommentDate = newComments.get(newComments.size() - 1).getDate();
                        adapter.setComments(comments);

                        if (newComments.size() < 10) {
                            hasMore = false;
                        }
                    } else {
                        hasMore = false;
                        if (isInitialLoad) {
                            adapter.setComments(new ArrayList<>()); // empty state
                        }
                    }
                } else {
                    hasMore = false;
                }
            }

            @Override
            public void onFailure(Call<GetCommentResponse> call, Throwable t) {
                isLoading = false;
                adapter.showFooterShimmer(false);
            }
        });
    }

    private void addComment(String email, String comment, CommentCallback callback) {
        String isoDate = Instant.now().toString();
        CreateCommentRequest request = new CreateCommentRequest(postId, email, isoDate, comment);

        Call<CreateCommentResponse> call = endpoints.comment(BuildConfig.CLIENT_API, request);
        call.enqueue(new Callback<CreateCommentResponse>() {
            @Override
            public void onResponse(Call<CreateCommentResponse> call, Response<CreateCommentResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(
                            response.body().isMessage(),
                            response.body().getLabel()
                    );
                } else {
                    callback.onSuccess(false, null);
                }
            }

            @Override
            public void onFailure(Call<CreateCommentResponse> call, Throwable t) {
                callback.onSuccess(false, null);
            }
        });
    }

    public interface CommentCallback {
        void onSuccess(boolean isPosted, String label);
    }
}
