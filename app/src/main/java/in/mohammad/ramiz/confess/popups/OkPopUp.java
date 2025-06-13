package in.mohammad.ramiz.confess.popups;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import in.mohammad.ramiz.confess.R;

public class OkPopUp {

    private Dialog dialog;

    public OkPopUp(Activity activity, int gifResId, String message) {
        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.gof_pop_layout);

        // ✅ Allow dismiss via back button
        dialog.setCancelable(true);

        // ✅ Allow dismiss by tapping outside the dialog box
        dialog.setCanceledOnTouchOutside(true);

        // ✅ Make background transparent
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // Views from layout
        ImageView gifImageView = dialog.findViewById(R.id.gifImage);
        TextView messageTextView = dialog.findViewById(R.id.messageText);

        // Set message and load GIF
        messageTextView.setText(message);
        Glide.with(activity).asGif().load(gifResId).into(gifImageView);


        dialog.show();
    }

    public void dismiss() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }
}
