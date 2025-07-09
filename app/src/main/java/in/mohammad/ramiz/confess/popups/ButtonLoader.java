package in.mohammad.ramiz.confess.popups;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.haptics.VibManager;

public class ButtonLoader {

    private Dialog dialog;

    public ButtonLoader(Activity activity, int imageId, String message, onUserOkClick listner){
        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.button_loader);

        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        ImageView image = dialog.findViewById(R.id.gifImage);
        TextView messageTextView = dialog.findViewById(R.id.messageText);
        TextView okButton = dialog.findViewById(R.id.ok);
        TextView cancelButton = dialog.findViewById(R.id.cancel);

        image.setImageResource(imageId);
        messageTextView.setText(message);

        cancelButton.setOnClickListener(v -> {
            VibManager.vibrateTick(activity);
            if(dialog != null && dialog.isShowing()){
                dialog.dismiss();
            }
        });

        okButton.setOnClickListener(v -> {
            VibManager.vibrateTick(activity);
            if(listner != null){
                listner.onOk();
            }
            if (dialog != null && dialog.isShowing()){
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    public void dismisser(){
        if(dialog != null && dialog.isShowing()){
            dialog.dismiss();
        }
    }

    public interface onUserOkClick{
        void onOk();
    }
}
