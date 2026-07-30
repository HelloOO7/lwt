package cz.spojenka.android.system;

import android.content.Context;
import android.os.Handler;
import android.os.SystemClock;

/**
 * Class for firing a callback with a specified period. The interval is tied to the ticks
 * of a real-time clock - the actual time at which the event is fired is rounded down
 * to the nearest modulo of the interval, except for the first run, which is always fired
 * when {@link #register()} is called.
 * <p>
 * For example, a TickNotifier with interval of 500 ms started at 12:15:30.123 will fire
 * the first event immediately, and then at 12:15:30.500, 12:15:31.000, 12:15:31.500, and so on.
 * Internal updates may be run more often (up to 50 ms) to ensure that processing delays do not alter the timing.
 */
public class TickNotifier implements Runnable {

    private final Handler handler;
    private boolean running = false;
    private final Runnable callback;
    private final int intervalMs;
    private final int updateIntervalMs;
    private long lastTick = 0;

    /**
     * Constructor.
     *
     * @param callback Callback to run on each tick.
     * @param intervalMs Tick interval (time modulus) in milliseconds.
     */
    public TickNotifier(Context context, Runnable callback, int intervalMs) {
        handler = new Handler(context.getMainLooper());
        this.callback = callback;
        this.intervalMs = intervalMs;
        this.updateIntervalMs = Math.min(intervalMs, Math.max(50, intervalMs / 10));
    }

    /**
     * Register the notifier. The first event will be fired immediately, and then
     * subsequent events will be fired with the specified period.
     */
    public void register() {
        if (running) {
            return;
        }
        running = true;
        run();
    }

    /**
     * Unregister the notifier. No further events will be fired and all pending
     * events will be cancelled.
     */
    public void unregister() {
        if (running) {
            running = false;
            handler.removeCallbacks(this);
        }
    }

    @Override
    public void run() {
        if (!running) {
            return;
        }
        long ts = SystemClock.elapsedRealtime();
        if (lastTick == 0 || (lastTick / intervalMs != ts / intervalMs)) {
            lastTick = SystemClock.elapsedRealtime();
            callback.run();
        }
        handler.postDelayed(this, updateIntervalMs);
    }
}
