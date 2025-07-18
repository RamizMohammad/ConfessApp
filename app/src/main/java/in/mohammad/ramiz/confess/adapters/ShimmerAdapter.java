package in.mohammad.ramiz.confess.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import in.mohammad.ramiz.confess.R;

public class ShimmerAdapter extends RecyclerView.Adapter<ShimmerAdapter.ShimmerPlaceHolder> {

    private final int SHIMMER_PANEL = 10;

    @NonNull
    @Override
    public ShimmerPlaceHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.post_shimmer, parent, false);
        return new ShimmerPlaceHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ShimmerPlaceHolder holder, int position) {
        // Nothing to bind
    }

    @Override
    public int getItemCount() {
        return SHIMMER_PANEL;
    }

    static class ShimmerPlaceHolder extends RecyclerView.ViewHolder{
        ShimmerPlaceHolder(View itemView){
            super(itemView);
        }
    }
}
