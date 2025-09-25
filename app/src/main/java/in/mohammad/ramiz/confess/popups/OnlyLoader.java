package in.mohammad.ramiz.confess.popups;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;
import android.widget.ImageView;

import com.bumptech.glide.Glide;

import in.mohammad.ramiz.confess.R;

public class OnlyLoader {

    private Dialog dialog;

    public OnlyLoader(Activity activity, int gifResId) {
        if (activity == null || activity.isFinishing()) {
            return; // don't show loader on dead activity
        }

        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.only_loader);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        dialog.setCancelable(false); // prevent accidental dismiss
        dialog.setCanceledOnTouchOutside(false);

        ImageView gifImageView = dialog.findViewById(R.id.gifImage);
        Glide.with(activity).asGif().load(gifResId).into(gifImageView);

        if (!dialog.isShowing()) {
            dialog.show();
        }
    }

    public void dismiss() {
        if (dialog != null && dialog.isShowing()) {
            try {
                dialog.dismiss(); // ✅ safe dismiss
            } catch (Exception ignored) {
                // Ignore rare WindowManager errors if activity is already finishing
            } finally {
                dialog = null; // free reference
            }
        }
    }

    public boolean isShowing() {
        return dialog != null && dialog.isShowing();
    }
}
