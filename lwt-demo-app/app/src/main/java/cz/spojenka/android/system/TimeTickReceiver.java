package cz.spojenka.android.system;

import android.content.Intent;

/**
 * Broadcast receiver that runs a callback on the {@link Intent#ACTION_TIME_TICK} broadcast event.
 */
public class TimeTickReceiver extends SimpleBroadcastReceiver {

    public TimeTickReceiver(Runnable callback) {
        super(Intent.ACTION_TIME_TICK, callback);
    }
}
