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
import in.mohammad.ramiz.confess.postdatabase.PostsData;

public class PostAdapter extends ListAdapter<PostsData, PostAdapter.PostViewholder> {

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

    @NonNull
    @Override
    public PostViewholder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.post_frame, parent, false);
        return new PostViewholder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PostViewholder holder, int position) {
        PostsData post = getItem(position);

        holder.aliasName.setText(post.getAliasName() != null ? post.getAliasName() : "Anonymous");
        holder.date.setText(post.getFormatDate());
        holder.post.setText(post.getPost());

        String profileUrl = post.getProfileLink();
        Log.d("PostAdapter", "Loading profile URL: " + profileUrl);

        // Ensure a default image if profileLink is null or empty
        if (profileUrl == null || profileUrl.trim().isEmpty()) {
            holder.profilePhoto.setImageResource(R.drawable.post_profile);
        } else {
            Glide.with(holder.itemView.getContext())
                    .load(profileUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    // Signature ensures reload if link changes
                    .signature(new ObjectKey(profileUrl + "_" + System.currentTimeMillis()))
                    .into(holder.profilePhoto);
        }
    }

    public void addMorePosts(List<PostsData> morePost) {
        List<PostsData> newList = new ArrayList<>(getCurrentList());
        newList.addAll(morePost);
        submitList(newList);
    }

    public static class PostViewholder extends RecyclerView.ViewHolder {
        TextView aliasName, date, post;
        ImageView profilePhoto;

        PostViewholder(View itemView) {
            super(itemView);
            aliasName = itemView.findViewById(R.id.aliasName);
            date = itemView.findViewById(R.id.date);
            post = itemView.findViewById(R.id.postData);
            profilePhoto = itemView.findViewById(R.id.profilePhoto);
        }
    }
}