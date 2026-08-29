
        /*
         * Copyright (C) 2008 The Android Open Source Project
         * Copyright (C) 2025 Raimondas Rimkus
         * Copyright (C) 2021 wittmane
         * Copyright (C) 2021 Maarten Trompper
         * Copyright (C) 2019 Micha LaQua
         * Copyright (C) 2019 Emmanuel
         *
         * Licensed under the Apache License, Version 2.0 (the "License");
         * you may not use this file except in compliance with the License.
         * You may obtain a copy of the License at
         *
         *      http://www.apache.org/licenses/LICENSE-2.0
         *
         * Unless required by applicable law or agreed to in writing, software
         * distributed under the License is distributed on an "AS IS" BASIS,
         * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
         * See the License for the specific language governing permissions and
         * limitations under the License.
         */

        package rkr.simplekeyboard.inputmethod.latin;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.inputmethodservice.InputMethodService;
import android.media.AudioManager;
import android.os.Build;
import android.os.Debug;
import android.os.IBinder;
import android.os.Message;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.util.PrintWriterPrinter;
import android.util.Printer;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.inputmethod.EditorInfo;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.compat.EditorInfoCompatUtils;
import rkr.simplekeyboard.inputmethod.compat.PreferenceManagerCompat;
import rkr.simplekeyboard.inputmethod.event.Event;
import rkr.simplekeyboard.inputmethod.event.InputTransaction;
import rkr.simplekeyboard.inputmethod.keyboard.Keyboard;
import rkr.simplekeyboard.inputmethod.keyboard.KeyboardActionListener;
import rkr.simplekeyboard.inputmethod.keyboard.KeyboardId;
import rkr.simplekeyboard.inputmethod.keyboard.KeyboardSwitcher;
import rkr.simplekeyboard.inputmethod.keyboard.MainKeyboardView;
import rkr.simplekeyboard.inputmethod.latin.common.Constants;
import rkr.simplekeyboard.inputmethod.latin.define.DebugFlags;
import rkr.simplekeyboard.inputmethod.latin.inputlogic.InputLogic;
import rkr.simplekeyboard.inputmethod.latin.settings.Settings;
import rkr.simplekeyboard.inputmethod.latin.settings.SettingsActivity;
import rkr.simplekeyboard.inputmethod.latin.settings.SettingsValues;
import rkr.simplekeyboard.inputmethod.latin.utils.ApplicationUtils;
import rkr.simplekeyboard.inputmethod.latin.utils.LeakGuardHandlerWrapper;
import rkr.simplekeyboard.inputmethod.latin.utils.ResourceUtils;
import rkr.simplekeyboard.inputmethod.latin.utils.ViewLayoutUtils;

import rkr.simplekeyboard.inputmethod.nexus.NexusImeBridge;


/**
 * Input method implementation for Qwerty'ish keyboard.
 */
public class LatinIME extends InputMethodService implements
        KeyboardActionListener,
        RichInputMethodManager.SubtypeChangedListener {

    static final String TAG = LatinIME.class.getSimpleName();

    private static final boolean TRACE = false;

    private static final int EXTENDED_TOUCHABLE_REGION_HEIGHT = 100;

    private static final int
            PERIOD_FOR_AUDIO_AND_HAPTIC_FEEDBACK_IN_KEY_REPEAT = 2;

    private static final int
            PENDING_IMS_CALLBACK_DURATION_MILLIS = 800;

    static final long DELAY_DEALLOCATE_MEMORY_MILLIS =
            TimeUnit.SECONDS.toMillis(10);

    final Settings mSettings;

    private Locale mLocale;

    final InputLogic mInputLogic =
            new InputLogic(this /* LatinIME */);

    // TODO: Move these Views to KeyboardSwitcher.
    private View mInputView;

    private RichInputMethodManager mRichImm;

    final KeyboardSwitcher mKeyboardSwitcher;

    private AlertDialog mOptionsDialog;

    public final UIHandler mHandler =
            new UIHandler(this);


    /**
     * UI handler for LatinIME.
     */
    public static final class UIHandler
            extends LeakGuardHandlerWrapper<LatinIME> {

        private static final int MSG_UPDATE_SHIFT_STATE = 0;
        private static final int MSG_PENDING_IMS_CALLBACK = 1;
        private static final int MSG_DEALLOCATE_MEMORY = 9;

        public UIHandler(final LatinIME ownerInstance) {
            super(ownerInstance);
        }

        @Override
        public void handleMessage(final Message msg) {

            final LatinIME latinIme =
                    getOwnerInstance();

            if (latinIme == null) {
                return;
            }

            final KeyboardSwitcher switcher =
                    latinIme.mKeyboardSwitcher;

            switch (msg.what) {

                case MSG_UPDATE_SHIFT_STATE:

                    switcher.requestUpdatingShiftState(
                            latinIme.getCurrentAutoCapsState(),
                            latinIme.getCurrentRecapitalizeState()
                    );

                    break;

                case MSG_DEALLOCATE_MEMORY:

                    latinIme.deallocateMemory();

                    break;
            }
        }

        public void postUpdateShiftState() {

            removeMessages(MSG_UPDATE_SHIFT_STATE);

            sendMessage(
                    obtainMessage(MSG_UPDATE_SHIFT_STATE)
            );
        }

        public void postDeallocateMemory() {

            sendMessageDelayed(
                    obtainMessage(MSG_DEALLOCATE_MEMORY),
                    DELAY_DEALLOCATE_MEMORY_MILLIS
            );
        }

        public void cancelDeallocateMemory() {

            removeMessages(MSG_DEALLOCATE_MEMORY);
        }

        public boolean hasPendingDeallocateMemory() {

            return hasMessages(
                    MSG_DEALLOCATE_MEMORY
            );
        }

        // Working variables for IMS callback handling.

        private boolean mIsOrientationChanging;

        private boolean mPendingSuccessiveImsCallback;

        private boolean mHasPendingStartInput;

        private boolean mHasPendingFinishInputView;

        private boolean mHasPendingFinishInput;

        private EditorInfo mAppliedEditorInfo;


        private void resetPendingImsCallback() {

            mHasPendingFinishInputView = false;

            mHasPendingFinishInput = false;

            mHasPendingStartInput = false;
        }


        private void executePendingImsCallback(
                final LatinIME latinIme,
                final EditorInfo editorInfo,
                final boolean restarting) {

            if (mHasPendingFinishInputView) {

                latinIme.onFinishInputViewInternal(
                        mHasPendingFinishInput
                );
            }

            if (mHasPendingFinishInput) {

                latinIme.onFinishInputInternal();
            }

            if (mHasPendingStartInput) {

                latinIme.onStartInputInternal(
                        editorInfo,
                        restarting
                );
            }

            resetPendingImsCallback();
        }


        public void onStartInput(
                final EditorInfo editorInfo,
                final boolean restarting) {

            if (hasMessages(MSG_PENDING_IMS_CALLBACK)) {

                mHasPendingStartInput = true;

            } else {

                if (mIsOrientationChanging && restarting) {

                    mIsOrientationChanging = false;

                    mPendingSuccessiveImsCallback = true;
                }

                final LatinIME latinIme =
                        getOwnerInstance();

                if (latinIme != null) {

                    executePendingImsCallback(
                            latinIme,
                            editorInfo,
                            restarting
                    );

                    latinIme.onStartInputInternal(
                            editorInfo,
                            restarting
                    );
                }
            }
        }


        public void onStartInputView(
                final EditorInfo editorInfo,
                final boolean restarting) {

            if (hasMessages(MSG_PENDING_IMS_CALLBACK)
                    && KeyboardId.equivalentEditorInfoForKeyboard(
                    editorInfo,
                    mAppliedEditorInfo)) {

                resetPendingImsCallback();

            } else {

                if (mPendingSuccessiveImsCallback) {

                    mPendingSuccessiveImsCallback = false;

                    resetPendingImsCallback();

                    sendMessageDelayed(
                            obtainMessage(MSG_PENDING_IMS_CALLBACK),
                            PENDING_IMS_CALLBACK_DURATION_MILLIS
                    );
                }

                final LatinIME latinIme =
                        getOwnerInstance();

                if (latinIme != null) {

                    executePendingImsCallback(
                            latinIme,
                            editorInfo,
                            restarting
                    );

                    latinIme.onStartInputViewInternal(
                            editorInfo,
                            restarting
                    );

                    mAppliedEditorInfo = editorInfo;
                }

                cancelDeallocateMemory();
            }
        }


        public void onFinishInputView(
                final boolean finishingInput) {

            if (hasMessages(MSG_PENDING_IMS_CALLBACK)) {

                mHasPendingFinishInputView = true;

            } else {

                final LatinIME latinIme =
                        getOwnerInstance();

                if (latinIme != null) {

                    latinIme.onFinishInputViewInternal(
                            finishingInput
                    );

                    mAppliedEditorInfo = null;
                }

                if (!hasPendingDeallocateMemory()) {

                    postDeallocateMemory();
                }
            }
        }


        public void onFinishInput() {

            if (hasMessages(MSG_PENDING_IMS_CALLBACK)) {

                mHasPendingFinishInput = true;

            } else {

                final LatinIME latinIme =
                        getOwnerInstance();

                if (latinIme != null) {

                    executePendingImsCallback(
                            latinIme,
                            null,
                            false
                    );

                    latinIme.onFinishInputInternal();
                }
            }
        }
    }


    public LatinIME() {

        super();

        mSettings = Settings.getInstance();

        mKeyboardSwitcher =
                KeyboardSwitcher.getInstance();
    }


    /**
     * Finds the beginning of the word currently being typed.
     */
    private int findCurrentWordStart(final String text) {

        if (text == null || text.isEmpty()) {

            return 0;
        }

        for (int i = text.length() - 1; i >= 0; i--) {

            final char c = text.charAt(i);

            if (Character.isWhitespace(c)
                    || Character.isSpaceChar(c)) {

                return i + 1;
            }
        }

        return 0;
    }


    @Override
    public void onCreate() {

        Settings.init(this);

        DebugFlags.init(
                PreferenceManagerCompat
                        .getDeviceSharedPreferences(this)
        );

        RichInputMethodManager.init(this);

        mRichImm =
                RichInputMethodManager.getInstance();

        mRichImm.setSubtypeChangeHandler(this);

        KeyboardSwitcher.init(this);

        AudioAndHapticFeedbackManager.init(this);

        super.onCreate();

        loadSettings();

        final IntentFilter filter =
                new IntentFilter();

        filter.addAction(
                AudioManager.RINGER_MODE_CHANGED_ACTION
        );

        registerReceiver(
                mRingerModeChangeReceiver,
                filter
        );
    }


    private void loadSettings() {

        mLocale =
                mRichImm
                        .getCurrentSubtype()
                        .getLocaleObject();

        final EditorInfo editorInfo =
                getCurrentInputEditorInfo();

        final InputAttributes inputAttributes =
                new InputAttributes(
                        editorInfo,
                        isFullscreenMode()
                );

        mSettings.loadSettings(
                inputAttributes
        );

        final SettingsValues currentSettingsValues =
                mSettings.getCurrent();

        AudioAndHapticFeedbackManager
                .getInstance()
                .onSettingsChanged(
                        currentSettingsValues
                );
    }


    @Override
    public void onDestroy() {

        mSettings.onDestroy();

        unregisterReceiver(
                mRingerModeChangeReceiver
        );

        super.onDestroy();
    }


    private boolean isImeSuppressedByHardwareKeyboard() {

        final KeyboardSwitcher switcher =
                KeyboardSwitcher.getInstance();

        return !onEvaluateInputViewShown()
                && switcher.isImeSuppressedByHardwareKeyboard(
                mSettings.getCurrent(),
                switcher.getKeyboardSwitchState()
        );
    }


    @Override
    public boolean onEvaluateInputViewShown() {

        final boolean useOnScreen =
                super.onEvaluateInputViewShown();

        if (Build.VERSION.SDK_INT
                < Build.VERSION_CODES.BAKLAVA) {

            return useOnScreen;

        } else {

            return useOnScreen
                    || mSettings.getCurrent().mUseOnScreen;
        }
    }


    @Override
    public void onConfigurationChanged(
            final Configuration conf) {

        SettingsValues settingsValues =
                mSettings.getCurrent();

        if (settingsValues.mHasHardwareKeyboard
                != Settings.readHasHardwareKeyboard(conf)) {

            loadSettings();
        }

        mKeyboardSwitcher.onConfigurationChanged();

        super.onConfigurationChanged(conf);
    }


    /**
     * Creates the keyboard input view and connects
     * SmartBar suggestion clicks.
     */
    @Override
    public View onCreateInputView() {

        final View view =
                mKeyboardSwitcher.onCreateInputView();

        if (view instanceof InputView) {

            final InputView inputView =
                    (InputView) view;

            /*
             * SmartBar suggestion click.
             */
            inputView.setSuggestionClickListener(
                    suggestion -> {

                        final android.view.inputmethod.InputConnection ic =
                                getCurrentInputConnection();

                        if (ic == null || suggestion == null) {

                            return;
                        }

                        final String suggestionText =
                                suggestion.toString().trim();

                        if (suggestionText.isEmpty()) {

                            return;
                        }

                        final CharSequence textBeforeCursor =
                                ic.getTextBeforeCursor(
                                        100,
                                        0
                                );

                        if (textBeforeCursor == null
                                || textBeforeCursor.length() == 0) {

                            ic.commitText(
                                    suggestionText,
                                    1
                            );

                            return;
                        }

                        final String beforeCursor =
                                textBeforeCursor.toString();

                        final int wordStart =
                                findCurrentWordStart(
                                        beforeCursor
                                );

                        final int charactersToReplace =
                                beforeCursor.length()
                                        - wordStart;

                        if (charactersToReplace > 0) {

                            ic.deleteSurroundingText(
                                    charactersToReplace,
                                    0
                            );
                        }

                        ic.commitText(
                                suggestionText,
                                1
                        );
                    }
            );
        }

        return view;
    }


    @Override
    public void setInputView(final View view) {

        super.setInputView(view);

        mInputView = view;

        updateSoftInputWindowLayoutParameters();

        view.requestApplyInsets();
    }


    @Override
    public void setCandidatesView(final View view) {

        // CandidatesView will never be set.
    }


    @Override
    public void onStartInput(
            final EditorInfo editorInfo,
            final boolean restarting) {

        mHandler.onStartInput(
                editorInfo,
                restarting
        );
    }


    @Override
    public void onStartInputView(
            final EditorInfo editorInfo,
            final boolean restarting) {

        mHandler.onStartInputView(
                editorInfo,
                restarting
        );
    }


    @Override
    public void onFinishInputView(
            final boolean finishingInput) {

        mInputLogic.clearCaches();

        mRichImm.resetSubtypeCycleOrder();

        mHandler.onFinishInputView(
                finishingInput
        );
    }


    @Override
    public void onFinishInput() {

        mHandler.onFinishInput();
    }


    @Override
    public void onCurrentSubtypeChanged() {

        mInputLogic.onSubtypeChanged();

        loadKeyboard();
    }


    void onStartInputInternal(
            final EditorInfo editorInfo,
            final boolean restarting) {

        super.onStartInput(
                editorInfo,
                restarting
        );
        NexusImeBridge.setImeService(
                this
        );

        final Locale primaryHintLocale =
                EditorInfoCompatUtils
                        .getPrimaryHintLocale(editorInfo);

        if (primaryHintLocale == null) {

            return;
        }

        mRichImm.setCurrentSubtype(
                primaryHintLocale
        );
    }


    void onStartInputViewInternal(
            final EditorInfo editorInfo,
            final boolean restarting) {

        super.onStartInputView(
                editorInfo,
                restarting
        );

        final KeyboardSwitcher switcher =
                mKeyboardSwitcher;

        switcher.updateKeyboardTheme();

        final MainKeyboardView mainKeyboardView =
                switcher.getMainKeyboardView();

        SettingsValues currentSettingsValues =
                mSettings.getCurrent();

        if (editorInfo == null) {

            Log.e(
                    TAG,
                    "Null EditorInfo in onStartInputView()"
            );

            if (DebugFlags.DEBUG_ENABLED) {

                throw new NullPointerException(
                        "Null EditorInfo in onStartInputView()"
                );
            }

            return;
        }


        if (DebugFlags.DEBUG_ENABLED) {

            Log.d(
                    TAG,
                    "onStartInputView: editorInfo:"
                            + String.format(
                            "inputType=0x%08x imeOptions=0x%08x",
                            editorInfo.inputType,
                            editorInfo.imeOptions
                    )
            );

            Log.d(
                    TAG,
                    "All caps = "
                            + ((editorInfo.inputType
                            & InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS)
                            != 0)
                            + ", sentence caps = "
                            + ((editorInfo.inputType
                            & InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
                            != 0)
                            + ", word caps = "
                            + ((editorInfo.inputType
                            & InputType.TYPE_TEXT_FLAG_CAP_WORDS)
                            != 0)
            );
        }


        Log.i(
                TAG,
                "Starting input. Cursor position = "
                        + editorInfo.initialSelStart
                        + ","
                        + editorInfo.initialSelEnd
                        + " Restarting = "
                        + restarting
        );


        if (mainKeyboardView == null) {

            return;
        }


        final boolean inputTypeChanged =
                !currentSettingsValues.isSameInputType(
                        editorInfo
                );

        final boolean isDifferentTextField =
                !restarting || inputTypeChanged;


        updateFullscreenMode();


        if (!isImeSuppressedByHardwareKeyboard()) {

            mInputLogic.startInput();

            mInputLogic.mConnection.reloadTextCache(
                    editorInfo,
                    restarting
            );
        }


        if (isDifferentTextField
                || !currentSettingsValues.hasSameOrientation(
                getResources().getConfiguration())) {

            loadSettings();
        }


        if (isDifferentTextField) {

            mainKeyboardView.closing();

            currentSettingsValues =
                    mSettings.getCurrent();

            switcher.loadKeyboard(
                    editorInfo,
                    currentSettingsValues,
                    getCurrentAutoCapsState(),
                    getCurrentRecapitalizeState()
            );

        } else {

            switcher.resetKeyboardStateToAlphabet(
                    getCurrentAutoCapsState(),
                    getCurrentRecapitalizeState()
            );
        }


        if (TRACE) {

            Debug.startMethodTracing(
                    "/data/trace/latinime"
            );
        }
    }


    @Override
    public void onWindowShown() {

        super.onWindowShown();

        if (isInputViewShown()) {

            setNavigationBarColor();
        }

        insertPendingNexusText();
    }


    @Override
    public void onWindowHidden() {

        super.onWindowHidden();

        final MainKeyboardView mainKeyboardView =
                mKeyboardSwitcher.getMainKeyboardView();

        if (mainKeyboardView != null) {

            mainKeyboardView.closing();
        }
    }

    private void insertPendingNexusText() {

        if (
                NexusImeBridge.getPendingText()
                        == null
        ) {
            return;
        }

        /*
         * The keyboard window may need a moment to become
         * active after Voice/OCR closes.
         */
        mHandler.postDelayed(
                new Runnable() {

                    @Override
                    public void run() {

                        NexusImeBridge.setImeService(
                                LatinIME.this
                        );

                        NexusImeBridge.commitPendingText();
                    }
                },
                150
        );
    }


    void onFinishInputInternal() {

        NexusImeBridge.clearImeService();

        super.onFinishInput();

        final MainKeyboardView mainKeyboardView =
                mKeyboardSwitcher.getMainKeyboardView();

        if (mainKeyboardView != null) {

            mainKeyboardView.closing();
        }
    }


    void onFinishInputViewInternal(
            final boolean finishingInput) {

        super.onFinishInputView(
                finishingInput
        );
    }


    protected void deallocateMemory() {

        mKeyboardSwitcher.deallocateMemory();
    }


    @Override
    public void onUpdateSelection(
            final int oldSelStart,
            final int oldSelEnd,
            final int newSelStart,
            final int newSelEnd,
            final int composingSpanStart,
            final int composingSpanEnd) {

        super.onUpdateSelection(
                oldSelStart,
                oldSelEnd,
                newSelStart,
                newSelEnd,
                composingSpanStart,
                composingSpanEnd
        );


        final MainKeyboardView keyboardView =
                mKeyboardSwitcher.getMainKeyboardView();

        if (keyboardView != null
                && keyboardView.isInCursorMove()) {

            return;
        }


        Log.i(
                TAG,
                "Update Selection. Cursor position = "
                        + newSelStart
                        + ","
                        + newSelEnd
        );


        mInputLogic.onUpdateSelection(
                newSelStart,
                newSelEnd
        );


        if (isInputViewShown()) {

            mInputLogic.reloadTextCache();

            mKeyboardSwitcher.requestUpdatingShiftState(
                    getCurrentAutoCapsState(),
                    getCurrentRecapitalizeState()
            );
        }
    }


    @Override
    public void hideWindow() {

        mKeyboardSwitcher.onHideWindow();

        if (TRACE) {

            Debug.stopMethodTracing();
        }

        if (isShowingOptionDialog()) {

            mOptionsDialog.dismiss();

            mOptionsDialog = null;
        }

        super.hideWindow();
    }


    @Override
    public void onComputeInsets(
            final InputMethodService.Insets outInsets) {

        super.onComputeInsets(outInsets);

        if (mInputView == null) {

            return;
        }


        final View visibleKeyboardView =
                mKeyboardSwitcher.getVisibleKeyboardView();

        if (visibleKeyboardView == null) {

            return;
        }


        final int inputHeight =
                mInputView.getHeight();


        final View keyboardContainer =
                mInputView.findViewById(
                        R.id.keyboard_container
                );


        final int imeHeight;

        if (keyboardContainer != null
                && keyboardContainer.getHeight() > 0) {

            imeHeight =
                    keyboardContainer.getHeight();

        } else {

            imeHeight =
                    visibleKeyboardView.getHeight();
        }


        final int visibleTopY =
                inputHeight - imeHeight;


        if (isImeSuppressedByHardwareKeyboard()
                && !visibleKeyboardView.isShown()) {

            outInsets.contentTopInsets =
                    inputHeight;

            outInsets.visibleTopInsets =
                    inputHeight;

            return;
        }


        if (visibleKeyboardView.isShown()) {

            final int touchLeft = 0;

            final int touchTop =
                    mKeyboardSwitcher
                            .isShowingMoreKeysPanel()
                            ? 0
                            : visibleTopY;

            final int touchRight =
                    visibleKeyboardView.getWidth();

            final int touchBottom =
                    inputHeight
                            + EXTENDED_TOUCHABLE_REGION_HEIGHT;


            outInsets.touchableInsets =
                    InputMethodService.Insets
                            .TOUCHABLE_INSETS_REGION;


            outInsets.touchableRegion.set(
                    touchLeft,
                    touchTop,
                    touchRight,
                    touchBottom
            );
        }


        outInsets.contentTopInsets =
                visibleTopY;

        outInsets.visibleTopInsets =
                visibleTopY;
    }


    @Override
    public boolean onShowInputRequested(
            final int flags,
            final boolean configChange) {

        if (isImeSuppressedByHardwareKeyboard()) {

            return true;
        }

        return super.onShowInputRequested(
                flags,
                configChange
        );
    }


    @Override
    public boolean onEvaluateFullscreenMode() {

        if (isImeSuppressedByHardwareKeyboard()) {

            return false;
        }


        final boolean isFullscreenModeAllowed =
                Settings.readUseFullscreenMode(
                        getResources()
                );


        if (super.onEvaluateFullscreenMode()
                && isFullscreenModeAllowed) {

            final EditorInfo ei =
                    getCurrentInputEditorInfo();

            return !(ei != null
                    && ((ei.imeOptions
                    & EditorInfo.IME_FLAG_NO_EXTRACT_UI)
                    != 0));
        }

        return false;
    }


    @Override
    public void updateFullscreenMode() {

        super.updateFullscreenMode();

        updateSoftInputWindowLayoutParameters();
    }


    private void updateSoftInputWindowLayoutParameters() {

        final Window window =
                getWindow().getWindow();


        ViewLayoutUtils.updateLayoutHeightOf(
                window,
                LayoutParams.MATCH_PARENT
        );


        if (mInputView != null) {

            final int layoutHeight =
                    isFullscreenMode()
                            ? LayoutParams.WRAP_CONTENT
                            : LayoutParams.MATCH_PARENT;


            final View inputArea =
                    window.findViewById(
                            android.R.id.inputArea
                    );


            ViewLayoutUtils.updateLayoutHeightOf(
                    inputArea,
                    layoutHeight
            );


            ViewLayoutUtils.updateLayoutGravityOf(
                    inputArea,
                    Gravity.BOTTOM
            );


            ViewLayoutUtils.updateLayoutHeightOf(
                    mInputView,
                    layoutHeight
            );
        }
    }


    int getCurrentAutoCapsState() {

        return mInputLogic.getCurrentAutoCapsState(
                mSettings.getCurrent(),
                mRichImm
                        .getCurrentSubtype()
                        .getKeyboardLayoutSet()
        );
    }


    int getCurrentRecapitalizeState() {

        return mInputLogic
                .getCurrentRecapitalizeState();
    }


    @Override
    public boolean onCustomRequest(
            final int requestCode) {

        switch (requestCode) {

            case Constants.CUSTOM_CODE_SHOW_INPUT_METHOD_PICKER:

                return showInputMethodPicker();
        }

        return false;
    }


    private boolean showInputMethodPicker() {

        if (isShowingOptionDialog()) {

            return false;
        }


        mOptionsDialog =
                mRichImm.showSubtypePicker(
                        this,
                        mKeyboardSwitcher
                                .getMainKeyboardView()
                                .getWindowToken(),
                        this
                );


        return mOptionsDialog != null;
    }


    public Locale getCurrentLayoutLocale() {

        return mLocale;
    }


    @Override
    public void onMoveCursorPointer(
            int steps) {

        if (mInputLogic.mConnection
                .hasCursorPosition()) {

            if (TextUtils.getLayoutDirectionFromLocale(
                    getCurrentLayoutLocale())
                    == View.LAYOUT_DIRECTION_RTL) {

                steps = -steps;
            }


            steps =
                    mInputLogic.mConnection
                            .getUnicodeSteps(
                                    steps,
                                    true
                            );


            if (steps == 0) {

                return;
            }


            final int end =
                    mInputLogic.mConnection
                            .getExpectedSelectionEnd()
                            + steps;


            final int start =
                    mInputLogic.mConnection.hasSelection()
                            ? mInputLogic.mConnection
                            .getExpectedSelectionStart()
                            : end;


            mInputLogic.mConnection.setSelection(
                    start,
                    end
            );


            hapticTickFeedback();

        } else {

            for (; steps < 0; steps--) {

                mInputLogic.sendDownUpKeyEvent(
                        KeyEvent.KEYCODE_DPAD_LEFT
                );
            }


            for (; steps > 0; steps--) {

                mInputLogic.sendDownUpKeyEvent(
                        KeyEvent.KEYCODE_DPAD_RIGHT
                );
            }


            hapticTickFeedback();
        }
    }


    @Override
    public void onMoveDeletePointer(
            int steps) {

        if (mInputLogic.mConnection
                .hasCursorPosition()) {

            steps =
                    mInputLogic.mConnection
                            .getUnicodeSteps(
                                    steps,
                                    false
                            );


            if (steps == 0) {

                return;
            }


            final int end =
                    mInputLogic.mConnection
                            .getExpectedSelectionEnd();


            final int start =
                    mInputLogic.mConnection
                            .getExpectedSelectionStart()
                            + steps;


            mInputLogic.mConnection.setSelection(
                    start,
                    end
            );


            hapticTickFeedback();

        } else {

            for (; steps < 0; steps++) {

                mInputLogic.sendDownUpKeyEvent(
                        KeyEvent.KEYCODE_DEL
                );
            }


            hapticTickFeedback();
        }
    }


    @Override
    public void onUpWithDeletePointerActive() {

        if (mInputLogic.mConnection.hasSelection()) {

            mInputLogic.mConnection
                    .deleteSelectedText();
        }
    }


    @Override
    public void onUpWithSpacePointerActive() {

        mInputLogic.reloadTextCache();
    }


    private boolean isShowingOptionDialog() {

        return mOptionsDialog != null
                && mOptionsDialog.isShowing();
    }


    public void switchToNextSubtype() {

        final IBinder token =
                getWindow()
                        .getWindow()
                        .getAttributes()
                        .token;


        mRichImm.switchToNextInputMethod(
                token,
                !shouldSwitchToOtherInputMethods(token)
        );
    }


    /*
     * Get correct shift code depending on
     * current keyboard layout.
     */
    private int getCodePointForKeyboard(
            final int codePoint) {

        if (Constants.CODE_SHIFT == codePoint) {

            final Keyboard currentKeyboard =
                    mKeyboardSwitcher.getKeyboard();


            if (currentKeyboard != null
                    && currentKeyboard.mId
                    .isAlphabetKeyboard()) {

                return codePoint;
            }


            return Constants.CODE_SYMBOL_SHIFT;
        }


        return codePoint;
    }


    // ----------------------------------------------------
    // KeyboardActionListener
    // ----------------------------------------------------

    @Override
    public void onCodeInput(
            final int codePoint,
            final int x,
            final int y,
            final boolean isKeyRepeat) {

        final Event event =
                createSoftwareKeypressEvent(
                        getCodePointForKeyboard(codePoint),
                        isKeyRepeat
                );


        onEvent(event);
    }


    public void onEvent(
            final Event event) {

        final InputTransaction completeInputTransaction =
                mInputLogic.onCodeInput(
                        mSettings.getCurrent(),
                        event
                );


        updateStateAfterInputTransaction(
                completeInputTransaction
        );


        mKeyboardSwitcher.onEvent(
                event,
                getCurrentAutoCapsState(),
                getCurrentRecapitalizeState()
        );
    }


    public static Event createSoftwareKeypressEvent(
            final int keyCodeOrCodePoint,
            final boolean isKeyRepeat) {

        final int keyCode;

        final int codePoint;


        if (keyCodeOrCodePoint <= 0) {

            keyCode =
                    keyCodeOrCodePoint;

            codePoint =
                    Event.NOT_A_CODE_POINT;

        } else {

            keyCode =
                    Event.NOT_A_KEY_CODE;

            codePoint =
                    keyCodeOrCodePoint;
        }


        return Event.createSoftwareKeypressEvent(
                codePoint,
                keyCode,
                isKeyRepeat
        );
    }


    @Override
    public void onTextInput(
            final String rawText) {

        final Event event =
                Event.createSoftwareTextEvent(
                        rawText,
                        Constants.CODE_OUTPUT_TEXT
                );


        final InputTransaction completeInputTransaction =
                mInputLogic.onTextInput(
                        mSettings.getCurrent(),
                        event
                );


        updateStateAfterInputTransaction(
                completeInputTransaction
        );


        mKeyboardSwitcher.onEvent(
                event,
                getCurrentAutoCapsState(),
                getCurrentRecapitalizeState()
        );
    }


    @Override
    public void onFinishSlidingInput() {

        mKeyboardSwitcher.onFinishSlidingInput(
                getCurrentAutoCapsState(),
                getCurrentRecapitalizeState()
        );
    }


    private void loadKeyboard() {

        loadSettings();


        if (mKeyboardSwitcher.getMainKeyboardView()
                != null) {

            mKeyboardSwitcher.loadKeyboard(
                    getCurrentInputEditorInfo(),
                    mSettings.getCurrent(),
                    getCurrentAutoCapsState(),
                    getCurrentRecapitalizeState()
            );
        }
    }


    /**
     * Updates keyboard state after input transaction.
     */
    private void updateStateAfterInputTransaction(
            final InputTransaction inputTransaction) {

        switch (
                inputTransaction.getRequiredShiftUpdate()
        ) {

            case InputTransaction.SHIFT_UPDATE_LATER:

                mHandler.postUpdateShiftState();

                break;


            case InputTransaction.SHIFT_UPDATE_NOW:

                mKeyboardSwitcher
                        .requestUpdatingShiftState(
                                getCurrentAutoCapsState(),
                                getCurrentRecapitalizeState()
                        );

                break;


            default:

                // SHIFT_NO_UPDATE

                break;
        }
    }


    private void hapticAndAudioFeedback(
            final int code,
            final int repeatCount) {

        final MainKeyboardView keyboardView =
                mKeyboardSwitcher.getMainKeyboardView();


        if (keyboardView != null
                && keyboardView.isInDraggingFinger()) {

            return;
        }


        if (repeatCount > 0) {

            if (code == Constants.CODE_DELETE
                    && !mInputLogic.mConnection
                    .canDeleteCharacters()) {

                return;
            }


            if (repeatCount
                    % PERIOD_FOR_AUDIO_AND_HAPTIC_FEEDBACK_IN_KEY_REPEAT
                    == 0) {

                return;
            }
        }


        final AudioAndHapticFeedbackManager feedbackManager =
                AudioAndHapticFeedbackManager.getInstance();


        if (repeatCount == 0) {

            feedbackManager.performHapticFeedback(
                    keyboardView
            );
        }


        feedbackManager.performAudioFeedback(
                code
        );
    }


    private void hapticTickFeedback() {

        final AudioAndHapticFeedbackManager feedbackManager =
                AudioAndHapticFeedbackManager.getInstance();

        feedbackManager.performTickFeedback();
    }


    @Override
    public void onPressKey(
            final int primaryCode,
            final int repeatCount,
            final boolean isSinglePointer) {

        mKeyboardSwitcher.onPressKey(
                primaryCode,
                isSinglePointer,
                getCurrentAutoCapsState(),
                getCurrentRecapitalizeState()
        );


        /*
         * Original keyboard audio + haptic feedback.
         *
         * Custom NexusIME key sound is NOT used here.
         */
        hapticAndAudioFeedback(
                primaryCode,
                repeatCount
        );
    }


    @Override
    public void onReleaseKey(
            final int primaryCode,
            final boolean withSliding) {

        mKeyboardSwitcher.onReleaseKey(
                primaryCode,
                withSliding,
                getCurrentAutoCapsState(),
                getCurrentRecapitalizeState()
        );
    }


    /*
     * Receive ringer mode changes.
     */
    private final BroadcastReceiver mRingerModeChangeReceiver =
            new BroadcastReceiver() {

                @Override
                public void onReceive(
                        final Context context,
                        final Intent intent) {

                    final String action =
                            intent.getAction();


                    if (AudioManager
                            .RINGER_MODE_CHANGED_ACTION
                            .equals(action)) {

                        AudioAndHapticFeedbackManager
                                .getInstance()
                                .onRingerModeChanged();
                    }
                }
            };


    public void launchSettings() {

        requestHideSelf(0);


        final MainKeyboardView mainKeyboardView =
                mKeyboardSwitcher.getMainKeyboardView();


        if (mainKeyboardView != null) {

            mainKeyboardView.closing();
        }


        final Intent intent =
                new Intent();


        intent.setClass(
                LatinIME.this,
                SettingsActivity.class
        );


        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
        );


        startActivity(intent);
    }


    @Override
    protected void dump(
            final FileDescriptor fd,
            final PrintWriter fout,
            final String[] args) {

        super.dump(
                fd,
                fout,
                args
        );


        final Printer p =
                new PrintWriterPrinter(fout);


        p.println("LatinIME state :");


        p.println(
                "  VersionCode = "
                        + ApplicationUtils
                        .getVersionCode(this)
        );


        p.println(
                "  VersionName = "
                        + ApplicationUtils
                        .getVersionName(this)
        );


        final Keyboard keyboard =
                mKeyboardSwitcher.getKeyboard();


        final int keyboardMode =
                keyboard != null
                        ? keyboard.mId.mMode
                        : -1;


        p.println(
                "  Keyboard mode = "
                        + keyboardMode
        );
    }


    public boolean shouldSwitchToOtherInputMethods(
            final IBinder token) {

        if (!mSettings.getCurrent()
                .mImeSwitchEnabled) {

            return false;
        }


        return mRichImm
                .shouldOfferSwitchingToOtherInputMethods(
                        token
                );
    }


    public boolean shouldShowLanguageSwitchKey() {

        if (mSettings.getCurrent()
                .isLanguageSwitchKeyDisabled()) {

            return false;
        }


        if (mRichImm
                .hasMultipleEnabledSubtypes()) {

            return true;
        }


        final IBinder token =
                getWindow()
                        .getWindow()
                        .getAttributes()
                        .token;


        if (token == null) {

            return false;
        }


        return shouldSwitchToOtherInputMethods(
                token
        );
    }


    private void setNavigationBarColor() {

        if (Build.VERSION.SDK_INT
                >= Build.VERSION_CODES.R) {

            final Window window =
                    getWindow().getWindow();


            if (window == null) {

                return;
            }


            final SharedPreferences prefs =
                    PreferenceManagerCompat
                            .getDeviceSharedPreferences(this);


            final int keyboardColor =
                    Settings.readKeyboardColor(
                            prefs,
                            this
                    );


            window.setNavigationBarColor(
                    keyboardColor
            );


            window.setNavigationBarContrastEnforced(
                    false
            );


            final int flag =
                    WindowInsetsController
                            .APPEARANCE_LIGHT_NAVIGATION_BARS;


            if (ResourceUtils
                    .isBrightColor(keyboardColor)) {

                window.getInsetsController()
                        .setSystemBarsAppearance(
                                flag,
                                flag
                        );

            } else {

                window.getInsetsController()
                        .setSystemBarsAppearance(
                                0,
                                flag
                        );
            }
        }
    }
}
