package cz.spojenka.lwt.util;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import androidx.annotation.RawRes;

public class TLSTrustManager {

    private static final String TAG = "TLSTrustManager";

    private final CertificateFactory certFactoryX509;
    private final KeyStore keyStore;

    private TrustManagerFactory tmf;

    public TLSTrustManager() throws IOException, GeneralSecurityException {
        certFactoryX509 = CertificateFactory.getInstance("X.509");
        keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
    }

    private X509Certificate loadCertificate(InputStream in) throws CertificateException {
        return (X509Certificate) certFactoryX509.generateCertificate(in);
    }

    private X509Certificate[] loadCertificates(InputStream in) throws CertificateException {
        List<X509Certificate> certs = new ArrayList<>();
        for (var cert : certFactoryX509.generateCertificates(in)) {
            certs.add((X509Certificate) cert);
        }
        return certs.toArray(new X509Certificate[0]);
    }

    public X509Certificate loadCertificate(Context context, @RawRes int resId) throws IOException, CertificateException {
        try (InputStream in = context.getResources().openRawResource(resId)) {
            return loadCertificate(in);
        }
    }

    public X509Certificate loadCertificate(AssetManager assetManager, String assetPath) throws IOException, CertificateException {
        try (InputStream in = assetManager.open(assetPath)) {
            return loadCertificate(in);
        }
    }

    public X509Certificate[] loadCertificates(byte[] certData) throws IOException, CertificateException {
        try (InputStream in = new ByteArrayInputStream(certData)) {
            return loadCertificates(in);
        }
    }

    public X509Certificate[] loadCertificates(ByteBuffer certData) throws IOException, CertificateException {
        try (InputStream in = new ByteBufferInputStream(certData)) {
            return loadCertificates(in);
        }
    }

    public void addCertificate(Context context, @RawRes int resId, String alias) throws IOException, GeneralSecurityException {
        X509Certificate cert = loadCertificate(context, resId);
        keyStore.setCertificateEntry(alias, cert);
        invalidateTmf();
    }

    public void addCertificate(AssetManager assetManager, String assetPath, String alias) throws IOException, GeneralSecurityException {
        X509Certificate cert = loadCertificate(assetManager, assetPath);
        keyStore.setCertificateEntry(alias, cert);
        invalidateTmf();
    }

    private void invalidateTmf() {
        tmf = null;
    }

    private TrustManagerFactory getTmf() throws NoSuchAlgorithmException, KeyStoreException {
        if (tmf == null) {
            tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keyStore);
        }
        return tmf;
    }

    public SSLContext createSSLContext() throws GeneralSecurityException {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, getTmf().getTrustManagers(), null);

        return sslContext;
    }

    public boolean isCertificateChainTrusted(byte[] certChainData) throws GeneralSecurityException, IOException {
        return isCertificateChainTrusted(loadCertificates(certChainData));
    }

    public boolean isCertificateChainTrusted(X509Certificate[] chain) throws GeneralSecurityException {
        TrustManagerFactory tmf = getTmf();
        for (var tm : tmf.getTrustManagers()) {
            if (tm instanceof X509TrustManager x509tm) {
                try {
                    x509tm.checkServerTrusted(chain, "RSA");
                    return true;
                } catch (CertificateException ex) {
                    Log.e(TAG, "Certificate chain not trusted", ex);
                }
            }
        }
        return false;
    }

    public boolean verifySignature(byte[] challenge, byte[] response, String algorithm, X509Certificate cert) throws GeneralSecurityException {
        var signature = Signature.getInstance(algorithm + "with" + translateKeyAlgToSignatureAlg(cert.getPublicKey().getAlgorithm()));
        signature.initVerify(cert);
        signature.update(challenge);
        return signature.verify(response);
    }

    private String translateKeyAlgToSignatureAlg(String keyAlg) {
        if ("EC".equals(keyAlg)) {
            return "ECDSA";
        }
        return keyAlg;
    }
}
