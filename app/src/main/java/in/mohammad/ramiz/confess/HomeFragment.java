package in.mohammad.ramiz.confess;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.List;

import in.mohammad.ramiz.confess.adapters.PostAdapter;
import in.mohammad.ramiz.confess.adapters.ShimmerAdapter;
import in.mohammad.ramiz.confess.postdatabase.PostViewmodel;
import in.mohammad.ramiz.confess.postdatabase.PostsData;

public class HomeFragment extends Fragment {

    private PostViewmodel viewmodel;
    private PostAdapter postAdapter;
    private ShimmerAdapter shimmerAdapter;
    private SwipeRefreshLayout swipeRefreshLayout;
    private LinearLayoutManager layoutManager;
    private boolean isLoadingNextPage = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_home, container, false);

        swipeRefreshLayout = view.findViewById(R.id.swipeRefresh);
        RecyclerView recyclerView = view.findViewById(R.id.recycleView);

        layoutManager = new LinearLayoutManager(getContext());
        recyclerView.setLayoutManager(layoutManager);

        postAdapter = new PostAdapter();
        shimmerAdapter = new ShimmerAdapter();

        recyclerView.setAdapter(shimmerAdapter);

        viewmodel = new ViewModelProvider(this).get(PostViewmodel.class);

        // Observe cached posts
        viewmodel.getSavedPosts().observe(getViewLifecycleOwner(), postsData -> {
            if (postsData != null) {
                recyclerView.setAdapter(postAdapter);
                postAdapter.submitList(postsData); // DiffUtil handles updates
            }
        });

        // Swipe-to-refresh
        swipeRefreshLayout.setOnRefreshListener(() -> {
            recyclerView.setAdapter(shimmerAdapter);
            viewmodel.refreshTopPosts();
            swipeRefreshLayout.setRefreshing(false);
        });

        // Pagination logic
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                if (dy > 0 && !isLoadingNextPage) {
                    List<PostsData> currentList = postAdapter.getCurrentList();
                    if (!currentList.isEmpty()) {
                        int totalItemCount = layoutManager.getItemCount();
                        int lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition();

                        if (lastVisibleItemPosition >= totalItemCount - 3) {
                            isLoadingNextPage = true;
                            String lastDate = currentList.get(currentList.size() - 1).getDate();
                            viewmodel.fetchNextPosts(lastDate);
                        }
                    }
                }
            }
        });

        // Observe next page posts
        viewmodel.nextPosts.observe(getViewLifecycleOwner(), nextPosts -> {
            if (nextPosts != null && !nextPosts.isEmpty()) {
                postAdapter.addMorePosts(nextPosts);
            }
            isLoadingNextPage = false; // Reset loading flag
        });

        viewmodel.refreshTopPosts();
        return view;
    }
}
