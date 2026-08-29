package rkr.simplekeyboard.inputmethod.nexus;

import android.inputmethodservice.InputMethodService;
import android.view.inputmethod.InputConnection;

import java.lang.ref.WeakReference;

public final class NexusImeBridge {

    private static WeakReference<InputMethodService>
            imeServiceRef =
            new WeakReference<>(null);

    private static String pendingText = null;

    private NexusImeBridge() {
        // Utility class
    }

    // =========================================================
    // IME SERVICE
    // =========================================================

    public static synchronized void setImeService(
            InputMethodService service) {

        imeServiceRef =
                new WeakReference<>(
                        service
                );
    }

    public static synchronized void clearImeService() {

        /*
         * Do NOT clear pending text here.
         *
         * The Voice/OCR Activity may finish while the
         * keyboard is transitioning back to the screen.
         */
        imeServiceRef =
                new WeakReference<>(null);
    }

    // =========================================================
    // PENDING TEXT
    // =========================================================

    public static synchronized void setPendingText(
            String text) {

        if (
                text == null ||
                        text.trim().isEmpty()
        ) {
            pendingText = null;
            return;
        }

        pendingText =
                text.trim();
    }

    public static synchronized String getPendingText() {

        return pendingText;
    }

    public static synchronized void clearPendingText() {

        pendingText = null;
    }

    // =========================================================
    // INSERT PENDING TEXT
    // =========================================================

    public static boolean commitPendingText() {

        final InputMethodService imeService =
                imeServiceRef.get();

        if (imeService == null) {
            return false;
        }

        final InputConnection inputConnection =
                imeService.getCurrentInputConnection();

        if (inputConnection == null) {
            return false;
        }

        final String text;

        synchronized (NexusImeBridge.class) {

            text = pendingText;

            if (
                    text == null ||
                            text.trim().isEmpty()
            ) {
                return false;
            }
        }

        try {

            final boolean success =
                    inputConnection.commitText(
                            text,
                            1
                    );

            if (success) {

                synchronized (
                        NexusImeBridge.class
                ) {

                    /*
                     * Only remove the pending text after
                     * commitText() succeeds.
                     */
                    pendingText = null;
                }
            }

            return success;

        } catch (Exception e) {

            return false;
        }
    }

    // =========================================================
    // DIRECT INSERT
    // =========================================================

    public static boolean commitText(
            String text) {

        if (
                text == null ||
                        text.trim().isEmpty()
        ) {
            return false;
        }

        setPendingText(text);

        return commitPendingText();
    }
}