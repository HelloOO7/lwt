package cz.spojenka.lwdn;

import android.net.wifi.aware.AttachCallback;
import android.net.wifi.aware.WifiAwareManager;
import android.net.wifi.aware.WifiAwareSession;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.RequiresApi;

/**
 * A wrapper around {@link WifiAwareSession} that manages a running session until it is
 * explicitly closed. It allows multiple code paths to use the standard {@link AttachCallback}
 * API without having the responsibility to close the session themselves. The session must
 * therefore be explicitly closed by the owner of the {@link WifiAwareSessionManager} instance.
 */
public class WifiAwareSessionManager implements AutoCloseable {

    private final WifiAwareManager awareManager;
    private final Handler callbackHandler;

    private WifiAwareSession currentSession;
    private boolean isAttaching;

    private final List<AttachCallback> registeredCallbacks = new ArrayList<>();

    public WifiAwareSessionManager(WifiAwareManager awareManager) {
        this.awareManager = awareManager;
        callbackHandler = new Handler(Looper.getMainLooper());
    }

    public WifiAwareManager getAwareManager() {
        return awareManager;
    }

    public void close() {
        if (currentSession != null) {
            currentSession.close();
            currentSession = null;
        }
    }

    public void attach(AttachCallback callback) {
        if (registeredCallbacks.contains(callback)) {
            return;
        }

        if (currentSession != null) {
            callback.onAttached(currentSession);
        } else {
            registeredCallbacks.add(callback);
            if (!isAttaching) {
                isAttaching = true;
                awareManager.attach(new AttachCallback() {

                    @Override
                    public void onAttached(WifiAwareSession session) {
                        currentSession = session;
                        isAttaching = false;
                        for (AttachCallback cb : registeredCallbacks) {
                            cb.onAttached(session);
                        }
                    }

                    @Override
                    public void onAttachFailed() {
                        isAttaching = false;
                        for (AttachCallback cb : registeredCallbacks) {
                            cb.onAttachFailed();
                        }
                    }

                    @Override
                    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
                    public void onAwareSessionTerminated() {
                        currentSession = null;
                        for (AttachCallback cb : registeredCallbacks) {
                            cb.onAwareSessionTerminated();
                        }
                    }
                }, callbackHandler);
            }
        }
    }

    public void detach(AttachCallback callback) {
        registeredCallbacks.remove(callback);
    }

    public Handler getCallbackHandler() {
        return callbackHandler;
    }
}
