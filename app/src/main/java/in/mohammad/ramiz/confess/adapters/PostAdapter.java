package in.mohammad.ramiz.confess.adapters;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import in.mohammad.ramiz.confess.CommentSection;
import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.haptics.VibManager;
import in.mohammad.ramiz.confess.likemanager.LikeStageManager;
import in.mohammad.ramiz.confess.postdatabase.PostsData;

public class PostAdapter extends ListAdapter<PostsData, RecyclerView.ViewHolder> {

    private static final int TYPE_ITEM = 0;
    private static final int TYPE_SHIMMER = 1;
    private static final int TYPE_FOOTER = 2;
    private String email;
    private boolean showShimmer = false;
    private boolean showFooter = false;

    public PostAdapter() {
        super(DIFF_CALLBACK);
    }

    private static final DiffUtil.ItemCallback<PostsData> DIFF_CALLBACK = new DiffUtil.ItemCallback<PostsData>() {
        @Override
        public boolean areItemsTheSame(@NonNull PostsData oldItem, @NonNull PostsData newItem) {
            return Objects.equals(oldItem.getPostId(), newItem.getPostId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull PostsData oldItem, @NonNull PostsData newItem) {
            return Objects.equals(oldItem.getPost(), newItem.getPost()) &&
                    Objects.equals(oldItem.getAliasName(), newItem.getAliasName()) &&
                    Objects.equals(oldItem.getProfileLink(), newItem.getProfileLink()) &&
                    Objects.equals(oldItem.getDate(), newItem.getDate());
        }
    };

    @Override
    public int getItemViewType(int position) {
        if (showShimmer && position == getCurrentList().size()) {
            return TYPE_SHIMMER;
        } else if (showFooter && position == getCurrentList().size()) {
            return TYPE_FOOTER;
        }
        return TYPE_ITEM;
    }

    @Override
    public int getItemCount() {
        return getCurrentList().size() + ((showShimmer || showFooter) ? 1 : 0);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(parent.getContext());
        if(account != null){
            email = account.getEmail();
        }
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_SHIMMER) {
            View view = inflater.inflate(R.layout.next_page_frame, parent, false);
            return new ShimmerViewHolder(view);
        } else if (viewType == TYPE_FOOTER) {
            View view = inflater.inflate(R.layout.post_item_footer, parent, false);
            return new FooterViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.post_frame, parent, false);
            return new PostViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof PostViewHolder) {
            PostsData post = getItem(position);
            PostViewHolder postHolder = (PostViewHolder) holder;

            postHolder.aliasName.setText(post.getAliasName() != null ? post.getAliasName() : "Anonymous");
            postHolder.date.setText(post.getFormatDate());
            postHolder.post.setText(post.getPost());
            postHolder.likeCount.setText(post.getTotalLikes());

            if (post.isComment()) {
                postHolder.commentButton.setVisibility(View.VISIBLE);
            } else {
                postHolder.commentButton.setVisibility(View.GONE);
            }

            if(post.isUserLiked()){
                postHolder.like.setImageResource(R.drawable.like);
            }
            else{
                postHolder.like.setImageResource(R.drawable.unlike);
            }

            Glide.with(postHolder.itemView.getContext())
                    .load(post.getProfileLink())
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(postHolder.profilePhoto);

            postHolder.commentButton.setOnClickListener(v -> {
                Context context = holder.itemView.getContext();
                VibManager.vibrateTick(context);
                Intent commentIntent = new Intent(context, CommentSection.class);
                commentIntent.putExtra("postId", post.getPostId());
                context.startActivity(commentIntent);
            });

            postHolder.likeButton.setOnClickListener(v -> {
                Context context = holder.itemView.getContext();
                VibManager.vibrateTick(context);
                boolean currentLikeStatus = post.isUserLiked();
                post.setUserLiked(!currentLikeStatus);
                postHolder.like.setImageResource(!currentLikeStatus ? R.drawable.like : R.drawable.unlike);

                int count = Integer.parseInt(post.getTotalLikes());
                count = !currentLikeStatus ? count + 1 : count - 1;
                post.setTotalLikes(String.valueOf(count));
                postHolder.likeCount.setText(post.getTotalLikes());

                LikeStageManager.getInstance().onLikeAction(post.getPostId(), email, !currentLikeStatus);
            });
        }
    }

    public void addMorePosts(List<PostsData> morePost) {
        List<PostsData> newList = new ArrayList<>(getCurrentList());
        newList.addAll(morePost);
        submitList(newList);
    }

    public void showShimmer(boolean show) {
        this.showShimmer = show;
        this.showFooter = false;
        notifyDataSetChanged();
    }

    public void showFooter(boolean show) {
        this.showFooter = show;
        this.showShimmer = false;
        notifyDataSetChanged();
    }

    static class PostViewHolder extends RecyclerView.ViewHolder {
        TextView aliasName, date, post, likeCount;
        ImageView profilePhoto, like;
        LinearLayout commentButton, likeButton;

        PostViewHolder(View itemView) {
            super(itemView);
            aliasName = itemView.findViewById(R.id.aliasName);
            date = itemView.findViewById(R.id.date);
            post = itemView.findViewById(R.id.postData);
            profilePhoto = itemView.findViewById(R.id.profilePhoto);
            commentButton = itemView.findViewById(R.id.commentPanel);
            like = itemView.findViewById(R.id.like);
            likeCount = itemView.findViewById(R.id.likeCount);
            likeButton = itemView.findViewById(R.id.likePanel);
        }
    }

    static class ShimmerViewHolder extends RecyclerView.ViewHolder {
        ShimmerViewHolder(View itemView) {
            super(itemView);
        }
    }

    static class FooterViewHolder extends RecyclerView.ViewHolder {
        FooterViewHolder(View itemView) {
            super(itemView);
        }
    }
}
