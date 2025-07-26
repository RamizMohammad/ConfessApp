package in.mohammad.ramiz.confess;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;

import java.util.List;

import in.mohammad.ramiz.confess.adapters.MyPostAdapter;
import in.mohammad.ramiz.confess.adapters.ShimmerAdapter;
import in.mohammad.ramiz.confess.popups.OkPopUp;
import in.mohammad.ramiz.confess.yourconfessiondatabase.MyPostViewmodel;
import in.mohammad.ramiz.confess.yourconfessiondatabase.MyPostsData;

public class YourConfession extends AppCompatActivity {

    private MyPostViewmodel viewmodel;
    private MyPostAdapter adapter;
    private ShimmerAdapter shimmerAdapter;
    private SwipeRefreshLayout swipeRefreshLayout;
    private LinearLayoutManager layoutManager;
    private LinearLayout empty;
    private boolean isLoadingNextPage = false;
    private String email;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_your_confession);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        swipeRefreshLayout = findViewById(R.id.swipeRefresh);
        RecyclerView recyclerView = findViewById(R.id.recycleView);
        empty = findViewById(R.id.empty);

        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if(account != null){
            email = account.getEmail();
        }

        layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);

        adapter = new MyPostAdapter();
        shimmerAdapter = new ShimmerAdapter();

        recyclerView.setAdapter(shimmerAdapter);

        viewmodel = new ViewModelProvider(this).get(MyPostViewmodel.class);

        viewmodel.getSavedPosts().observe(this, myPostsData -> {
            if(myPostsData != null && !myPostsData.isEmpty()){
                recyclerView.setAdapter(adapter);
                adapter.submitList(myPostsData);
                recyclerView.smoothScrollToPosition(0);
                empty.setVisibility(View.GONE);
            }
            else {
                empty.setVisibility(View.VISIBLE);
            }
        });

        swipeRefreshLayout.setOnRefreshListener(() -> {
            recyclerView.setAdapter(shimmerAdapter);
            viewmodel.refreshTopPosts(email, new MyPostViewmodel.ViewmodelRefreshCallback() {
                @Override
                public void onSuccess() {
                    swipeRefreshLayout.setRefreshing(false);
                    recyclerView.setVisibility(View.VISIBLE);
                    recyclerView.smoothScrollToPosition(0);
                    empty.setVisibility(View.GONE);
                }

                @Override
                public void onFail() {
                    swipeRefreshLayout.setRefreshing(false);
                    new OkPopUp(YourConfession.this, R.raw.error_animation, "Fetching of posts failed");
                }

                @Override
                public void onEmpty() {
                    swipeRefreshLayout.setRefreshing(false);
                    viewmodel.eraseAll();
                    recyclerView.setVisibility(View.GONE);
                    empty.setVisibility(View.VISIBLE);
                }
            });
        });

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                if(dy > 0 && !isLoadingNextPage) {
                    List<MyPostsData> currentList = adapter.getCurrentList();
                    if(!currentList.isEmpty()){
                        int totalItemCount = layoutManager.getItemCount();
                        int lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition();

                        if(lastVisibleItemPosition >= totalItemCount - 3){
                            isLoadingNextPage = true;
                            adapter.showShimmer(true);
                            String lastDate = currentList.get(currentList.size() - 1).getDate();
                            viewmodel.fetchNextPosts(email, lastDate, new MyPostViewmodel.ViewmodelNextCallback() {
                                @Override
                                public void onSuccess() {
                                    isLoadingNextPage = false;
                                    adapter.showShimmer(false);
                                }

                                @Override
                                public void onFail() {
                                    isLoadingNextPage = false;
                                    new OkPopUp(YourConfession.this, R.raw.error_animation, "There is an error");
                                }

                                @Override
                                public void onEmpty() {
                                    isLoadingNextPage = false;
                                    adapter.showShimmer(false);
                                    adapter.showFooter(true);
                                }
                            });
                        }
                    }
                }
            }
        });

        viewmodel.nextPosts.observe(this, nextPosts -> {
            if(nextPosts != null && !nextPosts.isEmpty()){
                adapter.addMorePosts(nextPosts);
            }
        });

        viewmodel.refreshTopPosts(email, new MyPostViewmodel.ViewmodelRefreshCallback() {
            @Override
            public void onSuccess() {
                recyclerView.setAdapter(adapter);
                recyclerView.setVisibility(View.VISIBLE);
                empty.setVisibility(View.GONE);
                recyclerView.smoothScrollToPosition(0);
            }

            @Override
            public void onFail() {
                new OkPopUp(YourConfession.this, R.raw.error_animation, "Fetching of post is failed");
            }

            @Override
            public void onEmpty() {
                viewmodel.eraseAll();
                recyclerView.setVisibility(View.GONE);
                empty.setVisibility(View.VISIBLE);
            }
        });
    }
}