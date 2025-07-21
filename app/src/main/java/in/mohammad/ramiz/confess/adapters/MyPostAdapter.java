package in.mohammad.ramiz.confess.adapters;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.signature.ObjectKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.yourconfessiondatabase.MyPostsData;

public class MyPostAdapter extends ListAdapter<MyPostsData, RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_POST = 0;
    private static final int VIEW_TYPE_SHIMMER = 1;
    private static final int VIEW_TYPE_FOOTER = 2;

    private boolean showShimmer = false;
    private boolean showFooter = false;

    public MyPostAdapter() {
        super(DIFF_CALLBACK);
    }

    private static final DiffUtil.ItemCallback<MyPostsData> DIFF_CALLBACK = new DiffUtil.ItemCallback<MyPostsData>() {
        @Override
        public boolean areItemsTheSame(@NonNull MyPostsData oldItem, @NonNull MyPostsData newItem) {
            return Objects.equals(oldItem.getPostId(), newItem.getPostId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull MyPostsData oldItem, @NonNull MyPostsData newItem) {
            return Objects.equals(oldItem.getPost(), newItem.getPost()) &&
                    Objects.equals(oldItem.getAliasName(), newItem.getAliasName()) &&
                    Objects.equals(oldItem.getProfileLink(), newItem.getProfileLink()) &&
                    Objects.equals(oldItem.getDate(), newItem.getDate());
        }
    };

    @Override
    public int getItemViewType(int position) {
        if (showShimmer && position == getItemCount() - 1) return VIEW_TYPE_SHIMMER;
        if (showFooter && position == getItemCount() - 1) return VIEW_TYPE_FOOTER;
        return VIEW_TYPE_POST;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_SHIMMER) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.next_page_frame, parent, false);
            return new ShimmerViewHolder(view);
        } else if (viewType == VIEW_TYPE_FOOTER) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.post_item_footer, parent, false);
            return new FooterViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.post_frame, parent, false);
            return new PostViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof PostViewHolder) {
            MyPostsData post = getItem(position);
            PostViewHolder postHolder = (PostViewHolder) holder;

            postHolder.aliasName.setText(post.getAliasName() != null ? post.getAliasName() : "Anonymous");
            postHolder.date.setText(post.getFormatDate());
            postHolder.post.setText(post.getPost());

            String profileUrl = post.getProfileLink();
            Log.d("MyPostAdapter", "Loading profile URL: " + profileUrl);

            if (profileUrl == null || profileUrl.trim().isEmpty()) {
                postHolder.profilePhoto.setImageResource(R.drawable.post_profile);
            } else {
                Glide.with(holder.itemView.getContext())
                        .load(profileUrl)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .signature(new ObjectKey(profileUrl + "_" + System.currentTimeMillis()))
                        .into(postHolder.profilePhoto);
            }
        }
    }

    @Override
    public int getItemCount() {
        int baseCount = super.getItemCount();
        if (showShimmer || showFooter) return baseCount + 1;
        return baseCount;
    }

    // Public helpers
    public void showShimmer(boolean show) {
        this.showShimmer = show;
        notifyDataSetChanged();
    }

    public void showFooter(boolean show) {
        this.showFooter = show;
        notifyDataSetChanged();
    }

    public void addMorePosts(List<MyPostsData> morePosts) {
        List<MyPostsData> newList = new ArrayList<>(getCurrentList());
        newList.addAll(morePosts);
        submitList(newList);
    }

    // ViewHolders
    public static class PostViewHolder extends RecyclerView.ViewHolder {
        TextView aliasName, date, post;
        ImageView profilePhoto;

        PostViewHolder(View itemView) {
            super(itemView);
            aliasName = itemView.findViewById(R.id.aliasName);
            date = itemView.findViewById(R.id.date);
            post = itemView.findViewById(R.id.postData);
            profilePhoto = itemView.findViewById(R.id.profilePhoto);
        }
    }

    public static class ShimmerViewHolder extends RecyclerView.ViewHolder {
        ShimmerViewHolder(View itemView) {
            super(itemView);
        }
    }

    public static class FooterViewHolder extends RecyclerView.ViewHolder {
        FooterViewHolder(View itemView) {
            super(itemView);
        }
    }
}
