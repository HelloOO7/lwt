package cz.spojenka.lwt.demoapp;

import android.app.Application;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.net.ssl.SSLContext;

import cz.spojenka.lwt.util.TLSTrustManager;

public class GlobalTrustManager {

    public static final String APP_CLIENT_KEY_ALIAS = "app_client_key";

    private static TLSTrustManager INSTANCE;

    private static KeyStore androidKeyStore;

    private GlobalTrustManager(Application app) {

    }

    public static TLSTrustManager getInstance(Application app) {
        if (INSTANCE == null) {
            try {
                INSTANCE = new TLSTrustManager();
                INSTANCE.addCertificate(app.getAssets(), "ROPID_Root_CA_Certificate_[DEBUG].crt", "Root CA");
                INSTANCE.addClientKey(getAndroidKeyStore(), null);
            } catch (GeneralSecurityException | IOException e) {
                throw new RuntimeException(e);
            }
        }
        return INSTANCE;
    }

    public static SSLContext createSSLContext(Application app) {
        try {
            TLSTrustManager trustManager = getInstance(app);
            return trustManager.createSSLContext();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    public static KeyStore getAndroidKeyStore() {
        if (androidKeyStore == null) {
            try {
                androidKeyStore = KeyStore.getInstance("AndroidKeyStore");
                androidKeyStore.load(null);
            } catch (GeneralSecurityException | IOException e) {
                throw new RuntimeException(e);
            }
        }
        return androidKeyStore;
    }

    public static boolean isClientKeyPresent() {
        try {
            KeyStore keyStore = getAndroidKeyStore();
            return keyStore.containsAlias(APP_CLIENT_KEY_ALIAS);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }
}
