package cz.spojenka.lwt.util;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import androidx.annotation.RawRes;

public class TLSTrustManager {

    private final CertificateFactory certFactoryX509;
    private final KeyStore keyStore;

    public TLSTrustManager() throws IOException, GeneralSecurityException {
        certFactoryX509 = CertificateFactory.getInstance("X.509");
        keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
    }

    private X509Certificate loadCertificate(InputStream in) throws CertificateException {
        return (X509Certificate) certFactoryX509.generateCertificate(in);
    }

    private X509Certificate loadCertificate(Context context, @RawRes int resId) throws IOException, CertificateException {
        try (InputStream in = context.getResources().openRawResource(resId)) {
            return loadCertificate(in);
        }
    }

    private X509Certificate loadCertificate(AssetManager assetManager, String assetPath) throws IOException, CertificateException {
        try (InputStream in = assetManager.open(assetPath)) {
            return loadCertificate(in);
        }
    }

    public void addCertificate(Context context, @RawRes int resId, String alias) throws IOException, GeneralSecurityException {
        X509Certificate cert = loadCertificate(context, resId);
        keyStore.setCertificateEntry(alias, cert);
    }

    public void addCertificate(AssetManager assetManager, String assetPath, String alias) throws IOException, GeneralSecurityException {
        X509Certificate cert = loadCertificate(assetManager, assetPath);
        keyStore.setCertificateEntry(alias, cert);
    }

    public SSLContext createSSLContext() throws GeneralSecurityException {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keyStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, tmf.getTrustManagers(), null);

        return sslContext;
    }
}
