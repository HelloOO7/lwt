package cz.spojenka.lwt.demoapp;

import android.app.Application;

import java.io.IOException;
import java.security.GeneralSecurityException;

import javax.net.ssl.SSLContext;

import cz.spojenka.lwt.util.TLSTrustManager;

public class GlobalTrustManager {

    private static TLSTrustManager INSTANCE;

    private TLSTrustManager trustManager;

    private GlobalTrustManager(Application app) {

    }

    public static TLSTrustManager getInstance(Application app) {
        if (INSTANCE == null) {
            try {
                INSTANCE = new TLSTrustManager();
                INSTANCE.addCertificate(app.getAssets(), "ROPID_Root_CA_Certificate_[DEBUG].crt", "Root CA");
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
}
