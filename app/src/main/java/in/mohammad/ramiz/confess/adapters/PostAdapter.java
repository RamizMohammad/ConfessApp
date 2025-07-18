package in.mohammad.ramiz.confess.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.postdatabase.PostsData;

public class PostAdapter extends ListAdapter<PostsData, PostAdapter.PostViewholder> {

    public PostAdapter() {
        super(DIFF_CALLBACK);
    }

    private static final DiffUtil.ItemCallback<PostsData> DIFF_CALLBACK = new DiffUtil.ItemCallback<PostsData>() {
        @Override
        public boolean areItemsTheSame(@NonNull PostsData oldItem, @NonNull PostsData newItem) {
            return oldItem.getPostId().equals(newItem.getPostId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull PostsData oldItem, @NonNull PostsData newItem) {
            return oldItem.getPost().equals(newItem.getPost());
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
    }

    public void addMorePosts(List<PostsData> morePost) {
        List<PostsData> newList = new ArrayList<>(getCurrentList());
        newList.addAll(morePost);
        submitList(newList);
    }

    public static class PostViewholder extends RecyclerView.ViewHolder {
        TextView aliasName, date, post;

        PostViewholder(View itemView) {
            super(itemView);
            aliasName = itemView.findViewById(R.id.aliasName);
            date = itemView.findViewById(R.id.date);
            post = itemView.findViewById(R.id.postData);
        }
    }
}
