package rkr.simplekeyboard.inputmethod.latin;

import android.content.Context;
import android.util.AttributeSet;

import java.util.List;

public final class InputView extends android.widget.FrameLayout {

    private SmartBarView smartBar;

    public InputView(
            final Context context,
            final AttributeSet attrs) {

        super(context, attrs, 0);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        smartBar = findViewById(
                rkr.simplekeyboard.inputmethod.R.id.smart_bar
        );
    }

    public void setSuggestionClickListener(
            SmartBarView.SuggestionClickListener listener) {

        if (smartBar != null) {
            smartBar.setSuggestionClickListener(listener);
        }
    }

    public void setSuggestions(List<String> suggestions) {
        if (smartBar != null) {
            smartBar.setSuggestions(suggestions);
        }
    }

    public void setAISuggestions(List<String> suggestions) {
        if (smartBar != null) {
            smartBar.setAISuggestions(suggestions);
        }
    }

    public void setAISuggestion(String suggestion) {
        if (smartBar != null) {
            smartBar.setAISuggestion(suggestion);
        }
    }

    public void clearSuggestions() {
        if (smartBar != null) {
            smartBar.clearSuggestions();
        }
    }
}