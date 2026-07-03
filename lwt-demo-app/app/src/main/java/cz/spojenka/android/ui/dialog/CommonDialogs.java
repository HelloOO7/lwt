package cz.spojenka.android.ui.dialog;

import android.content.Context;
import android.content.DialogInterface;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import cz.spojenka.android.util.ViewUtils;
import cz.spojenka.lwt.demoapp.R;

/**
 * Common dialogs that can be used in multiple places, similar to JOptionPane in Java Swing.
 */
public class CommonDialogs {

    /**
     * Create a new dialog with a single line text input field.
     *
     * @param context  Context
     * @param editText The EditText that should handle the input
     * @return A builder that can be used to further configure the dialog
     */
    public static AlertDialog.Builder newTextInputDialog(Context context, EditText editText) {
        editText.setSingleLine();
        FrameLayout container = new FrameLayout(context);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ViewUtils.setMarginDp(editText, params, 20, 12);
        container.addView(editText, params);

        return new MaterialAlertDialogBuilder(context)
                .setView(container);
    }

    public static AlertDialog.Builder newInfoDialog(Context context, @StringRes int title, @StringRes int message) {
        return newInfoDialog(context, title != 0 ? context.getString(title) : null, context.getString(message));
    }

    public static AlertDialog.Builder newInfoDialog(Context context, String title, String message) {
        return new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null);
    }

    public static AlertDialog.Builder newYesNoDialog(
            Context context, @StringRes int title, @StringRes int message,
            DialogInterface.OnClickListener yesListener,
            DialogInterface.OnClickListener noListener
    ) {
        return newYesNoDialog(
                context,
                title != 0 ? context.getString(title) : null,
                message != 0 ? context.getString(message) : null,
                yesListener,
                noListener
        );
    }

    public static AlertDialog.Builder newYesNoDialog(
            Context context, String title, String message,
            DialogInterface.OnClickListener yesListener,
            DialogInterface.OnClickListener noListener
    ) {
        return new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.yes, yesListener)
                .setNegativeButton(R.string.no, noListener);
    }
}
