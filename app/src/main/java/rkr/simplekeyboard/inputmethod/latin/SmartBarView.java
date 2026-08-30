package rkr.simplekeyboard.inputmethod.latin;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import rkr.simplekeyboard.inputmethod.nexus.NexusOCRActivity;
import rkr.simplekeyboard.inputmethod.nexus.voice.VoiceActivity;

public class SmartBarView extends LinearLayout {

    public interface SuggestionClickListener {
        void onSuggestionClick(String suggestion);
    }

    private ImageView magicButton;
    private ImageView ocrButton;
    private ImageView aiButton;
    private ImageView voiceButton;
    private ImageView attachButton;

    private LinearLayout suggestionContainer;
    private HorizontalScrollView suggestionScroll;

    private GradientDrawable barBackground;

    private SuggestionClickListener suggestionClickListener;

    private final int NEON_BLUE =
            Color.rgb(70, 190, 255);

    private final int NEON_PURPLE =
            Color.rgb(155, 95, 255);

    private final int TEXT_WHITE =
            Color.rgb(235, 242, 250);

    public SmartBarView(
            Context context,
            AttributeSet attrs) {

        super(context, attrs);

        setOrientation(VERTICAL);

        setGravity(
                Gravity.CENTER_HORIZONTAL
        );

        setPadding(
                dp(5),
                dp(4),
                dp(5),
                dp(4)
        );

        createBarBackground();

        createToolbar(context);

        createSuggestionLayer(context);
    }

    // ====================================================
    // BACKGROUND
    // ====================================================

    private void createBarBackground() {

        barBackground =
                new GradientDrawable();

        barBackground.setColor(
                Color.rgb(9, 13, 20)
        );

        barBackground.setCornerRadius(
                dp(13)
        );

        barBackground.setStroke(
                dp(1),
                Color.rgb(38, 91, 135)
        );

        setBackground(
                barBackground
        );
    }

    // ====================================================
    // TOOLBAR
    // ====================================================

    private void createToolbar(
            Context context) {

        LinearLayout toolbar =
                new LinearLayout(context);

        toolbar.setOrientation(
                HORIZONTAL
        );

        toolbar.setGravity(
                Gravity.CENTER_VERTICAL
        );

        LayoutParams toolbarParams =
                new LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        dp(38)
                );

        toolbar.setLayoutParams(
                toolbarParams
        );

        magicButton =
                createIcon(context);

        ocrButton =
                createIcon(context);

        aiButton =
                createIcon(context);

        voiceButton =
                createIcon(context);

        attachButton =
                createIcon(context);

        /*
         * Temporary Android icons.
         * Replace with NexusIME vectors later.
         */

        magicButton.setImageResource(
                android.R.drawable.ic_menu_edit
        );

        ocrButton.setImageResource(
                android.R.drawable.ic_menu_camera
        );

        aiButton.setImageResource(
                android.R.drawable.ic_menu_search
        );

        voiceButton.setImageResource(
                android.R.drawable.ic_btn_speak_now
        );

        attachButton.setImageResource(
                android.R.drawable.ic_menu_add
        );

        /*
         * AI gets purple identity.
         */

        aiButton.setColorFilter(
                NEON_PURPLE
        );

        toolbar.addView(
                magicButton
        );

        toolbar.addView(
                ocrButton
        );

        toolbar.addView(
                aiButton
        );

        toolbar.addView(
                voiceButton
        );

        toolbar.addView(
                attachButton
        );

        addView(toolbar);
    }

    // ====================================================
    // ICON CREATION
    // ====================================================

    private ImageView createIcon(
            Context context) {

        ImageView icon =
                new ImageView(context);

        LayoutParams params =
                new LayoutParams(
                        0,
                        dp(34),
                        1f
                );

        params.setMargins(
                dp(2),
                dp(1),
                dp(2),
                dp(1)
        );

        icon.setLayoutParams(
                params
        );

        icon.setPadding(
                dp(7),
                dp(7),
                dp(7),
                dp(7)
        );

        icon.setColorFilter(
                NEON_BLUE
        );

        icon.setScaleType(
                ImageView.ScaleType.CENTER_INSIDE
        );

        icon.setClickable(true);

        icon.setFocusable(true);

        /*
         * =================================================
         * ICON BACKGROUND
         * =================================================
         *
         * Normally transparent.
         * On press, a rounded neon background appears
         * behind the icon.
         */

        GradientDrawable normalBackground =
                new GradientDrawable();

        normalBackground.setColor(
                Color.TRANSPARENT
        );

        normalBackground.setCornerRadius(
                dp(10)
        );

        icon.setBackground(
                normalBackground
        );

        /*
         * =================================================
         * PRESS / CLICK ANIMATION
         * =================================================
         *
         * The icon slightly shrinks while pressed.
         * A rounded colored background appears behind it.
         *
         * AI -> Purple
         * Other icons -> Blue
         */

        icon.setOnTouchListener(
                (v, event) -> {

                    switch (
                            event.getAction()
                    ) {

                        case MotionEvent.ACTION_DOWN:

                            v.animate()
                                    .cancel();

                            int backgroundColor =
                                    (v == aiButton)
                                            ? Color.rgb(
                                            55,
                                            30,
                                            85
                                    )
                                            : Color.rgb(
                                            25,
                                            65,
                                            90
                                    );

                            int strokeColor =
                                    (v == aiButton)
                                            ? NEON_PURPLE
                                            : NEON_BLUE;

                            GradientDrawable pressedBackground =
                                    new GradientDrawable();

                            pressedBackground.setColor(
                                    backgroundColor
                            );

                            pressedBackground.setCornerRadius(
                                    dp(10)
                            );

                            pressedBackground.setStroke(
                                    dp(1),
                                    strokeColor
                            );

                            v.setBackground(
                                    pressedBackground
                            );

                            v.animate()
                                    .scaleX(0.94f)
                                    .scaleY(0.94f)
                                    .setDuration(70)
                                    .start();

                            break;

                        case MotionEvent.ACTION_UP:

                            v.animate()
                                    .cancel();

                            GradientDrawable releasedBackground =
                                    new GradientDrawable();

                            releasedBackground.setColor(
                                    Color.TRANSPARENT
                            );

                            releasedBackground.setCornerRadius(
                                    dp(10)
                            );

                            v.setBackground(
                                    releasedBackground
                            );

                            v.animate()
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .setDuration(110)
                                    .start();

                            break;

                        case MotionEvent.ACTION_CANCEL:

                            v.animate()
                                    .cancel();

                            GradientDrawable cancelBackground =
                                    new GradientDrawable();

                            cancelBackground.setColor(
                                    Color.TRANSPARENT
                            );

                            cancelBackground.setCornerRadius(
                                    dp(10)
                            );

                            v.setBackground(
                                    cancelBackground
                            );

                            v.animate()
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .setDuration(100)
                                    .start();

                            break;
                    }

                    return false;
                }
        );

        /*
         * =================================================
         * BUTTON ACTIONS
         * =================================================
         *
         * EXISTING FUNCTIONALITY — UNCHANGED
         */

        icon.setOnClickListener(v -> {

            // =================================================
            // MAGIC / DRAFT
            // =================================================

            if (v == magicButton) {

                showLoading();

            }

            // =================================================
            // OCR
            // =================================================

            else if (v == ocrButton) {

                showOCRState();

                openOCR();

            }

            // =================================================
            // AI
            // =================================================

            else if (v == aiButton) {

                showAIState();

            }

            // =================================================
            // VOICE
            // =================================================

            else if (v == voiceButton) {

                showVoiceState();

                openVoice();

            }

            // =================================================
            // OFFICE / ATTACH
            // =================================================

            else if (v == attachButton) {

                showAttachState();

            }
        });

        return icon;
    }

    // ====================================================
    // OCR LAUNCH
    // ====================================================

    private void openOCR() {

        try {

            Context context = getContext();

            Intent intent = new Intent();

            intent.setClassName(
                    context,
                    "rkr.simplekeyboard.inputmethod.nexus.NexusOCRActivity"
            );

            intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
            );

            context.startActivity(intent);

        } catch (Exception e) {

            e.printStackTrace();

            resetState();
        }
    }

    // ====================================================
    // VOICE LAUNCH
    // ====================================================

    private void openVoice() {

        try {

            Context context = getContext();

            Intent intent =
                    new Intent(
                            context,
                            VoiceActivity.class
                    );

            intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
            );

            context.startActivity(
                    intent
            );

        } catch (Exception e) {

            e.printStackTrace();

            resetState();
        }
    }

    // ====================================================
    // SUGGESTION LAYER
    // ====================================================

    private void createSuggestionLayer(
            Context context) {

        suggestionScroll =
                new HorizontalScrollView(
                        context
                );

        suggestionScroll
                .setHorizontalScrollBarEnabled(
                        false
                );

        suggestionScroll
                .setOverScrollMode(
                        View.OVER_SCROLL_NEVER
                );

        LayoutParams scrollParams =
                new LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        0
                );

        scrollParams.topMargin =
                dp(3);

        suggestionScroll.setLayoutParams(
                scrollParams
        );

        suggestionContainer =
                new LinearLayout(
                        context
                );

        suggestionContainer.setOrientation(
                HORIZONTAL
        );

        suggestionContainer.setGravity(
                Gravity.CENTER_VERTICAL
        );

        suggestionContainer.setPadding(
                dp(2),
                dp(1),
                dp(2),
                dp(2)
        );

        suggestionScroll.addView(
                suggestionContainer
        );

        addView(
                suggestionScroll
        );

        suggestionScroll.setVisibility(
                GONE
        );
    }

    // ====================================================
    // SUGGESTION API
    // ====================================================

    public void setSuggestionClickListener(
            SuggestionClickListener listener) {

        suggestionClickListener =
                listener;
    }

    public void setSuggestions(
            List<String> suggestions) {

        if (
                suggestions == null ||
                        suggestions.isEmpty()
        ) {

            clearSuggestions();

            return;
        }

        suggestionContainer
                .removeAllViews();

        for (
                String suggestion :
                suggestions
        ) {

            if (
                    suggestion == null ||
                            suggestion.trim().isEmpty()
            ) {

                continue;
            }

            TextView chip =
                    createSuggestionChip(
                            getContext(),
                            suggestion
                    );

            suggestionContainer.addView(
                    chip
            );
        }

        if (
                suggestionContainer
                        .getChildCount() == 0
        ) {

            clearSuggestions();

            return;
        }

        showSuggestionLayer();
    }

    public void setAISuggestions(
            List<String> suggestions) {

        setSuggestions(
                suggestions
        );
    }

    public void setAISuggestion(
            String suggestion) {

        if (
                suggestion == null ||
                        suggestion.trim().isEmpty()
        ) {

            clearSuggestions();

            return;
        }

        List<String> list =
                new ArrayList<>();

        list.add(
                suggestion
        );

        setAISuggestions(
                list
        );
    }

    public void clearSuggestions() {

        if (
                suggestionContainer != null
        ) {

            suggestionContainer
                    .removeAllViews();
        }

        if (
                suggestionScroll != null &&
                        suggestionScroll.getVisibility()
                                != GONE
        ) {

            hideSuggestionLayer();
        }
    }

    // ====================================================
    // SUGGESTION CHIP
    // ====================================================

    private TextView createSuggestionChip(
            Context context,
            String text) {

        TextView chip =
                new TextView(context);

        chip.setText(text);

        chip.setTextColor(
                TEXT_WHITE
        );

        chip.setTextSize(
                13
        );

        chip.setGravity(
                Gravity.CENTER
        );

        chip.setSingleLine(
                true
        );

        chip.setEllipsize(
                android.text.TextUtils
                        .TruncateAt.END
        );

        chip.setMaxWidth(
                dp(260)
        );

        chip.setPadding(
                dp(13),
                dp(7),
                dp(13),
                dp(7)
        );

        GradientDrawable background =
                new GradientDrawable();

        background.setColor(
                Color.rgb(19, 27, 38)
        );

        background.setCornerRadius(
                dp(17)
        );

        background.setStroke(
                dp(1),
                Color.rgb(46, 102, 148)
        );

        chip.setBackground(
                background
        );

        LayoutParams params =
                new LayoutParams(
                        LayoutParams.WRAP_CONTENT,
                        LayoutParams.WRAP_CONTENT
                );

        params.setMargins(
                dp(3),
                dp(2),
                dp(3),
                dp(2)
        );

        chip.setLayoutParams(
                params
        );

        chip.setClickable(true);

        chip.setFocusable(true);

        chip.setOnClickListener(v -> {

            if (
                    suggestionClickListener != null
            ) {

                suggestionClickListener
                        .onSuggestionClick(
                                text
                        );
            }
        });

        chip.setOnTouchListener(
                (v, event) -> {

                    switch (
                            event.getAction()
                    ) {

                        case MotionEvent.ACTION_DOWN:

                            v.animate()
                                    .scaleX(0.94f)
                                    .scaleY(0.94f)
                                    .setDuration(60)
                                    .start();

                            break;

                        case MotionEvent.ACTION_UP:

                        case MotionEvent.ACTION_CANCEL:

                            v.animate()
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .setDuration(90)
                                    .start();

                            break;
                    }

                    return false;
                }
        );

        return chip;
    }

    // ====================================================
    // SUGGESTION ANIMATION
    // ====================================================

    private void showSuggestionLayer() {

        suggestionScroll.setVisibility(
                VISIBLE
        );

        suggestionScroll.setAlpha(
                1f
        );

        suggestionScroll.post(() -> {

            int targetHeight =
                    suggestionContainer
                            .getMeasuredHeight();

            targetHeight =
                    Math.min(
                            Math.max(
                                    targetHeight,
                                    dp(36)
                            ),
                            dp(82)
                    );

            animateToHeight(
                    targetHeight
            );
        });
    }

    private void hideSuggestionLayer() {

        suggestionScroll.animate()
                .alpha(0f)
                .setDuration(120)
                .withEndAction(() -> {

                    suggestionScroll
                            .setVisibility(
                                    GONE
                            );

                    suggestionScroll
                            .setAlpha(1f);

                    LayoutParams params =
                            (LayoutParams)
                                    suggestionScroll
                                            .getLayoutParams();

                    params.height = 0;

                    suggestionScroll
                            .setLayoutParams(
                                    params
                            );
                })
                .start();
    }

    private void animateToHeight(
            int targetHeight) {

        LayoutParams params =
                (LayoutParams)
                        suggestionScroll
                                .getLayoutParams();

        int startHeight =
                params.height;

        ValueAnimator animator =
                ValueAnimator.ofInt(
                        startHeight,
                        targetHeight
                );

        animator.setDuration(
                180
        );

        animator.addUpdateListener(
                animation -> {

                    int height =
                            (Integer)
                                    animation
                                            .getAnimatedValue();

                    LayoutParams lp =
                            (LayoutParams)
                                    suggestionScroll
                                            .getLayoutParams();

                    lp.height =
                            height;

                    suggestionScroll
                            .setLayoutParams(
                                    lp
                            );
                }
        );

        animator.start();
    }

    // ====================================================
    // SMART BAR STATES
    // ====================================================

    public void showLoading() {

        updateBarColor(
                Color.rgb(9, 24, 38),
                Color.rgb(65, 160, 225)
        );
    }

    public void showOCRState() {

        updateBarColor(
                Color.rgb(8, 21, 34),
                Color.rgb(45, 115, 170)
        );
    }

    public void showAIState() {

        updateBarColor(
                Color.rgb(22, 12, 38),
                Color.rgb(130, 78, 210)
        );
    }

    public void showVoiceState() {

        updateBarColor(
                Color.rgb(8, 21, 30),
                Color.rgb(50, 140, 175)
        );
    }

    public void showAttachState() {

        updateBarColor(
                Color.rgb(9, 23, 33),
                Color.rgb(55, 145, 185)
        );
    }

    public void resetState() {

        updateBarColor(
                Color.rgb(9, 13, 20),
                Color.rgb(38, 91, 135)
        );
    }

    private void updateBarColor(
            int backgroundColor,
            int strokeColor) {

        if (barBackground == null) {

            createBarBackground();
        }

        barBackground.setColor(
                backgroundColor
        );

        barBackground.setStroke(
                dp(1),
                strokeColor
        );

        setBackground(
                barBackground
        );
    }

    // ====================================================
    // USER TYPING
    // ====================================================

    public void onUserTyping() {

        if (
                getVisibility()
                        != View.VISIBLE
        ) {

            setVisibility(
                    View.VISIBLE
            );

            animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(180)
                    .start();
        }
    }

    // ====================================================
    // UTILITY
    // ====================================================

    private int dp(int value) {

        return (int) (
                value *
                        getResources()
                                .getDisplayMetrics()
                                .density
                        + 0.5f
        );
    }
}