package in.mohammad.ramiz.confess.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import in.mohammad.ramiz.confess.R;

public abstract class ProfileAdapter extends RecyclerView.Adapter<ProfileAdapter.ProfileViewHolder> {

    private List<Integer> profileList;
    private int selectedPos = RecyclerView.NO_POSITION;

    public ProfileAdapter(List<Integer> profileList) {
        this.profileList = profileList;
    }

    public void setSelectedPosition(int position) {
        int prev = selectedPos;
        selectedPos = position;
        notifyItemChanged(prev);
        notifyItemChanged(position);
    }

    @NonNull
    @Override
    public ProfileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.profile_cycle, parent, false);
        return new ProfileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProfileViewHolder holder, int position) {
        holder.image.setImageResource(profileList.get(position));

        if (position == selectedPos) {
            holder.ring.setVisibility(View.VISIBLE);
            holder.image.setAlpha(1.0f);
        } else {
            holder.ring.setVisibility(View.GONE);
            holder.image.setAlpha(0.5f);
        }
    }

    @Override
    public int getItemCount() {
        return profileList.size();
    }

    static class ProfileViewHolder extends RecyclerView.ViewHolder {
        ImageView image, ring;

        ProfileViewHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.profileImage);
            ring = itemView.findViewById(R.id.ringOverlay);
        }
    }
}
