package cz.spojenka.android.system;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import androidx.core.content.ContextCompat;

/**
 * A simple broadcast receiver that observes a specific broadcast action and executes a callback when that action is received.
 * The receiver is not exported.
 */
public class SimpleBroadcastReceiver extends BroadcastReceiver {

    private final String action;
    private final Runnable callback;

    /**
     * Constructor
     *
     * @param action   Action, such as {@link Intent#ACTION_TIME_TICK}, to observe. This will be used as the intent filter.
     * @param callback Callback to run when the broadcast is received.
     */
    public SimpleBroadcastReceiver(String action, Runnable callback) {
        this.action = action;
        this.callback = callback;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (callback != null) {
            callback.run();
        }
    }

    private IntentFilter createIntentFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(action);
        return filter;
    }

    /**
     * Activate the receiver. Should be called in {@link android.app.Activity#onCreate(Bundle)} or similar.
     *
     * @param context Context
     */
    public void register(Context context) {
        ContextCompat.registerReceiver(context, this, createIntentFilter(), ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    /**
     * Deactivate the receiver. Should be called in {@link android.app.Activity#onDestroy()} or similar.
     *
     * @param context Context
     */
    public void unregister(Context context) {
        context.unregisterReceiver(this);
    }
}
