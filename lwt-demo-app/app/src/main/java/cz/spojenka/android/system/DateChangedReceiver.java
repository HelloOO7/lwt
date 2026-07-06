package cz.spojenka.android.system;

import android.content.Intent;

/**
 * Broadcast receiver that runs a callback on the {@link Intent#ACTION_DATE_CHANGED} broadcast event.
 */
public class DateChangedReceiver extends SimpleBroadcastReceiver {

    public DateChangedReceiver(Runnable callback) {
        super(Intent.ACTION_DATE_CHANGED, callback);
    }
}
