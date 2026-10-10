package in.mohammad.ramiz.confess.ui.util;

import android.text.Editable;
import android.text.TextWatcher;

/** TextWatcher that only cares about the text after each change. */
public final class SimpleTextWatcher implements TextWatcher {

    public interface OnText {
        void changed(String text);
    }

    private final OnText onText;

    public SimpleTextWatcher(OnText onText) {
        this.onText = onText;
    }

    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

    @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }

    @Override public void afterTextChanged(Editable s) {
        onText.changed(s == null ? "" : s.toString());
    }
}
