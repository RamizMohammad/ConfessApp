package in.mohammad.ramiz.confess.profilebuttons;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import in.mohammad.ramiz.confess.R;

public class ButtonAdapter extends RecyclerView.Adapter<ButtonAdapter.ViewHolder>{

    public interface clickListener {
        void onClick(int id, ButtonEntity button);
    }

    private List<ButtonEntity> buttons;
    private Context context;
    private clickListener listener;

    public ButtonAdapter(List<ButtonEntity> buttons, Context context, clickListener listener){
        this.buttons = buttons;
        this.context = context;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.button_frame_profile, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ButtonEntity button = buttons.get(position);

        // Bind data
        holder.icon.setImageResource(button.getIconId());
        holder.label.setText(button.getButtonName());

        // Click callback
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(button.getId(), button);
        });
    }

    @Override
    public int getItemCount() {
        return buttons.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView label;

        ViewHolder(View itemView){
            super(itemView);
            icon = itemView.findViewById(R.id.buttonIcon);
            label = itemView.findViewById(R.id.buttonText);
        }
    }
}
