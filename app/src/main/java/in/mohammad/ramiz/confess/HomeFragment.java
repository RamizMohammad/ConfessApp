package in.mohammad.ramiz.confess;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.List;

import in.mohammad.ramiz.confess.adapters.PostAdapter;
import in.mohammad.ramiz.confess.adapters.ShimmerAdapter;
import in.mohammad.ramiz.confess.popups.OkPopUp;
import in.mohammad.ramiz.confess.postdatabase.PostViewmodel;
import in.mohammad.ramiz.confess.postdatabase.PostsData;

public class HomeFragment extends Fragment {

    private PostViewmodel viewmodel;
    private PostAdapter postAdapter;
    private ShimmerAdapter shimmerAdapter;
    private SwipeRefreshLayout swipeRefreshLayout;
    private LinearLayoutManager layoutManager;
    private LinearLayout empty;
    private boolean isLoadingNextPage = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_home, container, false);

        swipeRefreshLayout = view.findViewById(R.id.swipeRefresh);
        RecyclerView recyclerView = view.findViewById(R.id.recycleView);
        empty = view.findViewById(R.id.empty);

        layoutManager = new LinearLayoutManager(getContext());
        recyclerView.setLayoutManager(layoutManager);

        postAdapter = new PostAdapter();
        shimmerAdapter = new ShimmerAdapter();

        recyclerView.setAdapter(shimmerAdapter); // Show shimmer on initial load

        viewmodel = new ViewModelProvider(this).get(PostViewmodel.class);

        // Observe cached posts
        viewmodel.getSavedPosts().observe(getViewLifecycleOwner(), postsData -> {
            if (postsData != null && !postsData.isEmpty()) {
                recyclerView.setAdapter(postAdapter);
                postAdapter.submitList(postsData); // DiffUtil handles updates
                empty.setVisibility(View.GONE);
            } else {
                empty.setVisibility(View.VISIBLE);
            }
        });

        // Swipe-to-refresh
        swipeRefreshLayout.setOnRefreshListener(() -> {
            recyclerView.setAdapter(shimmerAdapter);
            viewmodel.refreshTopPosts(new PostViewmodel.ViewmodelRefreshCallback() {
                @Override
                public void onSuccess() {
                    swipeRefreshLayout.setRefreshing(false);
                    recyclerView.setVisibility(View.VISIBLE);
                    empty.setVisibility(View.GONE);
                }

                @Override
                public void onFail() {
                    swipeRefreshLayout.setRefreshing(false);
                    new OkPopUp(getActivity(), R.raw.error_animation, "Fetching of posts failed");
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
                            postAdapter.showShimmer(true);
                            String lastDate = currentList.get(currentList.size() - 1).getDate();
                            viewmodel.fetchNextPosts(lastDate, new PostViewmodel.ViewmodelNextCallback() {
                                @Override
                                public void onSuccess() {
                                    isLoadingNextPage = false;
                                    postAdapter.showShimmer(false);
                                }

                                @Override
                                public void onFail() {
                                    isLoadingNextPage = false;
                                    new OkPopUp(getActivity(), R.raw.error_animation, "There is an error");
                                }

                                @Override
                                public void onEmpty() {
                                    isLoadingNextPage = false;
                                    postAdapter.showShimmer(false);
                                    postAdapter.showFooter(true);
                                }
                            });
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
        });

        // Initial fetch (with shimmer)
        viewmodel.refreshTopPosts(new PostViewmodel.ViewmodelRefreshCallback() {
            @Override
            public void onSuccess() {
                recyclerView.setAdapter(postAdapter);
                recyclerView.setVisibility(View.VISIBLE);
                empty.setVisibility(View.GONE);
            }

            @Override
            public void onFail() {
                new OkPopUp(getActivity(), R.raw.error_animation, "Fetching of post is failed");
            }

            @Override
            public void onEmpty() {
                viewmodel.eraseAll();
                recyclerView.setVisibility(View.GONE);
                empty.setVisibility(View.VISIBLE);
            }
        });
        return view;
    }
}