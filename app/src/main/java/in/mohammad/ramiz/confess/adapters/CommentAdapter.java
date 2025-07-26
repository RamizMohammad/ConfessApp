package in.mohammad.ramiz.confess.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.facebook.shimmer.ShimmerFrameLayout;

import java.util.ArrayList;
import java.util.List;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.commentlogic.CommentData;

public class CommentAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_COMMENT = 0;
    private static final int TYPE_SHIMMER = 1;
    private static final int TYPE_FOOTER_SHIMMER = 2;

    private List<CommentData> comments = new ArrayList<>();
    private boolean showShimmer = true;
    private boolean showFooterShimmer = false;

    public void setComments(List<CommentData> comments) {
        this.comments = comments;
        this.showShimmer = false;
        notifyDataSetChanged();
    }

    public void showInitialShimmer() {
        this.showShimmer = true;
        notifyDataSetChanged();
    }

    public void showFooterShimmer(boolean show) {
        this.showFooterShimmer = show;
        notifyItemChanged(getItemCount() - 1);
    }

    @Override
    public int getItemViewType(int position) {
        if (showShimmer) {
            return TYPE_SHIMMER;
        } else if (showFooterShimmer && position == comments.size()) {
            return TYPE_FOOTER_SHIMMER;
        } else {
            return TYPE_COMMENT;
        }
    }

    @Override
    public int getItemCount() {
        if (showShimmer) {
            return 5; // number of shimmer items
        }
        return comments.size() + (showFooterShimmer ? 1 : 0);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        if (viewType == TYPE_COMMENT) {
            View view = inflater.inflate(R.layout.comment_frame, parent, false);
            return new CommentViewHolder(view);
        } else if (viewType == TYPE_FOOTER_SHIMMER) {
            View view = inflater.inflate(R.layout.comment_shimmer, parent, false);
            return new FooterShimmerViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.comment_shimmer, parent, false);
            return new ShimmerViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof CommentViewHolder) {
            CommentData comment = comments.get(position);
            ((CommentViewHolder) holder).bind(comment);
        }
        // ShimmerViewHolder and FooterShimmerViewHolder are static visuals, no binding needed
    }

    static class CommentViewHolder extends RecyclerView.ViewHolder {
        TextView aliasName, date, comment;
        ImageView profile;

        CommentViewHolder(View itemView) {
            super(itemView);
            profile = itemView.findViewById(R.id.profile);
            aliasName = itemView.findViewById(R.id.aliasName);
            date = itemView.findViewById(R.id.date);
            comment = itemView.findViewById(R.id.comment);
        }

        void bind(CommentData commentData) {
            aliasName.setText(commentData.getAliasName());
            date.setText(commentData.getFormatDate());
            comment.setText(commentData.getcomment());

            Glide.with(itemView.getContext())
                    .load(commentData.getProfileLink())
                    .into(profile);
        }
    }

    static class ShimmerViewHolder extends RecyclerView.ViewHolder {
        ShimmerFrameLayout shimmer;

        ShimmerViewHolder(View itemView) {
            super(itemView);
            shimmer = itemView.findViewById(R.id.shimmerLayout);
            shimmer.startShimmer();
        }
    }

    static class FooterShimmerViewHolder extends RecyclerView.ViewHolder {
        ShimmerFrameLayout shimmer;

        FooterShimmerViewHolder(View itemView) {
            super(itemView);
            shimmer = itemView.findViewById(R.id.shimmerLayout);
            shimmer.startShimmer();
        }
    }
}
