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

    public OnlyLoader(Activity activity, int gifResId){
        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.only_loader);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        ImageView gifImageView = dialog.findViewById(R.id.gifImage);
        Glide.with(activity).asGif().load(gifResId).into(gifImageView);

        dialog.show();
    }

    public void dismiss() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }
}
