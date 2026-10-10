package in.mohammad.ramiz.confess.ui.entry;

import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.databinding.ActivityWelcomeBinding;
import in.mohammad.ramiz.confess.databinding.ViewPaperNoteBinding;
import in.mohammad.ramiz.confess.ui.base.BaseActivity;
import in.mohammad.ramiz.confess.ui.util.EdgeInsets;

/**
 * S1 · Welcome. Old: MainActivity ("Share your thoughts without guilt" →
 * "Join the world"). Both actions open the sign-in sheet; the server decides
 * whether this Google account is new (→ onboarding) or returning (→ app).
 */
public class WelcomeActivity extends BaseActivity {

    private ActivityWelcomeBinding b;

    @Override
    protected boolean requiresSession() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityWelcomeBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeInsets.padSystemBars(b.root, false);

        buildNotes();
        b.join.setOnClickListener(v -> SignInSheet.show(getSupportFragmentManager()));
        b.haveAccount.setOnClickListener(v -> SignInSheet.show(getSupportFragmentManager()));

        if (savedInstanceState == null) animateIn();
    }

    /** Three paper notes, fanned like letters left on a table (back → front). */
    private void buildNotes() {
        addNote(R.string.note_1_text, R.string.note_1_alias, R.drawable.ic_eye_off, -7f, -dp(18), dp(4));
        addNote(R.string.note_2_text, R.string.note_2_alias, R.drawable.ic_heart, 5f, dp(22), dp(58));
        addNote(R.string.note_3_text, R.string.note_3_alias, R.drawable.ic_lock, -2f, 0, dp(116));
    }

    private void addNote(@StringRes int text, @StringRes int alias, @DrawableRes int icon,
                         float rotation, int dx, int dy) {
        ViewPaperNoteBinding note = ViewPaperNoteBinding.inflate(LayoutInflater.from(this), b.noteStack, false);
        note.noteText.setText(text);
        note.noteAlias.setText("· " + getString(alias));
        note.noteIcon.setImageResource(icon);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(280), FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        lp.topMargin = dp(32) + dy;
        View card = note.getRoot();
        card.setLayoutParams(lp);
        card.setTranslationX(dx);
        card.setRotation(rotation);
        b.noteStack.addView(card);
    }

    private void animateIn() {
        View[] seq = {b.headline1, b.headline2, b.sub, b.noteStack, b.join, b.footer, b.haveAccount};
        for (int i = 0; i < seq.length; i++) {
            View v = seq[i];
            v.setAlpha(0f);
            v.setTranslationY(dp(14));
            v.animate().alpha(1f).translationY(0f).setStartDelay(80L * i).setDuration(520).start();
        }
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }
}
