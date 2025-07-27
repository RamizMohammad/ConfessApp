package in.mohammad.ramiz.confess;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;

import java.util.List;

import in.mohammad.ramiz.confess.adapters.PostAdapter;
import in.mohammad.ramiz.confess.adapters.ShimmerAdapter;
import in.mohammad.ramiz.confess.haptics.VibManager;
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
    private ImageView notificationButton;
    private boolean isLoadingNextPage = false;
    private String email;
    private boolean isRefreshing = true;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_home, container, false);

        swipeRefreshLayout = view.findViewById(R.id.swipeRefresh);
        RecyclerView recyclerView = view.findViewById(R.id.recycleView);
        empty = view.findViewById(R.id.empty);
        notificationButton = view.findViewById(R.id.notification);

        layoutManager = new LinearLayoutManager(getContext());
        recyclerView.setLayoutManager(layoutManager);

        postAdapter = new PostAdapter();
        shimmerAdapter = new ShimmerAdapter();

        recyclerView.setAdapter(shimmerAdapter); // Show shimmer on initial load

        viewmodel = new ViewModelProvider(this).get(PostViewmodel.class);

        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(requireContext());

        if(account != null){
            Log.d("check", ""+account.getEmail());
            email = account.getEmail();
        }
        else{
            Log.d("account","account is null");
        }

        // Observe cached posts
        viewmodel.getSavedPosts().observe(getViewLifecycleOwner(), postsData -> {
            if (postsData != null && !postsData.isEmpty()) {
                recyclerView.setAdapter(postAdapter);
                postAdapter.submitList(postsData);
                recyclerView.smoothScrollToPosition(0);
                empty.setVisibility(View.GONE);
            } else {
                if(!isRefreshing){
                    empty.setVisibility(View.VISIBLE);
                }
            }
        });

        notificationButton.setOnClickListener(v -> {
            VibManager.vibrateTick(requireContext());
            Intent notificationIntent = new Intent(requireContext(), NotificationPage.class);
            startActivity(notificationIntent);
            requireActivity().overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        });

        // Swipe-to-refresh
        swipeRefreshLayout.setOnRefreshListener(() -> {
            recyclerView.setAdapter(shimmerAdapter);
            viewmodel.refreshTopPosts(email, new PostViewmodel.ViewmodelRefreshCallback() {
                @Override
                public void onSuccess() {
                    swipeRefreshLayout.setRefreshing(false);
                    isRefreshing = false;
                    recyclerView.setVisibility(View.VISIBLE);
                    empty.setVisibility(View.GONE);
                    recyclerView.smoothScrollToPosition(0);
                }

                @Override
                public void onFail() {
                    swipeRefreshLayout.setRefreshing(false);
                    recyclerView.setAdapter(postAdapter);
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
                            viewmodel.fetchNextPosts(email, lastDate, new PostViewmodel.ViewmodelNextCallback() {
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
        viewmodel.refreshTopPosts(email, new PostViewmodel.ViewmodelRefreshCallback() {
            @Override
            public void onSuccess() {
                recyclerView.setAdapter(postAdapter);
                recyclerView.setVisibility(View.VISIBLE);
                recyclerView.smoothScrollToPosition(0);
                isRefreshing = false;
                empty.setVisibility(View.GONE);
            }

            @Override
            public void onFail() {
                recyclerView.setAdapter(postAdapter);
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