package cz.spojenka.android.ui.resources;

import android.content.Context;

import java.text.DecimalFormat;

import cz.spojenka.lwt.demoapp.R;

/**
 * Formatting for currency values in CZK (mostly).
 */
public class CurrencyFormat {

    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.00");

    /**
     * Format a monetary amount into the format "X.XX CZK".
     * In certain cases, the amount is scaled by a factor defined in the resources.
     *
     * @param context Context
     * @param amount The amount to format, in CZK
     * @return The formatted amount, with either none or 2 decimal places.
     */
    public static String formatCzk(Context context, double amount) {
        int amountCents = (int) Math.round(amount * 100);
        String formatted = (amountCents % 100 == 0) ? String.valueOf(amountCents / 100) : DECIMAL_FORMAT.format(amount);
        return context.getString(R.string.czk_format, formatted);
    }
}
