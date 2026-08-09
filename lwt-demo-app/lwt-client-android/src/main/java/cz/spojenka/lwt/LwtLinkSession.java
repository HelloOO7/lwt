package cz.spojenka.lwt;

import android.net.wifi.aware.WifiAwareManager;

import cz.spojenka.lwdn.WifiAwareSessionManager;

public class LwtLinkSession {

    private WifiAwareSessionManager awareSessionManager;

    WifiAwareSessionManager getAwareSessionManager(WifiAwareManager awareManager) {
        if (awareSessionManager == null) {
            awareSessionManager = new WifiAwareSessionManager(awareManager);
        }
        return awareSessionManager;
    }

    /**
     * Close the link session. The session may still be used afterwards, which will cause it to
     * reopen all needed underlying resources if possible.
     */
    public void close() {
        if (awareSessionManager != null) {
            awareSessionManager.close();
            awareSessionManager = null;
        }
    }
}
