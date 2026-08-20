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
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import androidx.annotation.RawRes;

public class TLSTrustManager {

    private static final String TAG = "TLSTrustManager";

    private final CertificateFactory certFactoryX509;

    private KeyStore clientKeyStore;
    private char[] clientKeyStorePassword;
    private final KeyStore peerKeyStore;

    private TrustManagerFactory tmf;
    private KeyManagerFactory kmf;

    public TLSTrustManager() throws IOException, GeneralSecurityException {
        certFactoryX509 = CertificateFactory.getInstance("X.509");
        peerKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        peerKeyStore.load(null, null);
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

    public KeyStore loadPKCS12(InputStream in, char[] password) throws IOException, GeneralSecurityException {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(in, password);
        return keyStore;
    }

    public KeyStore loadPKCS12(Context context, @RawRes int resId, char[] password) throws IOException, GeneralSecurityException {
        try (InputStream in = context.getResources().openRawResource(resId)) {
            return loadPKCS12(in, password);
        }
    }

    public KeyStore loadPKCS12(AssetManager assetManager, String assetPath, char[] password) throws IOException, GeneralSecurityException {
        try (InputStream in = assetManager.open(assetPath)) {
            return loadPKCS12(in, password);
        }
    }

    public static boolean isWrongPassword(Throwable throwable) {
        if (throwable instanceof UnrecoverableKeyException || throwable.getCause() instanceof UnrecoverableKeyException) {
            return true;
        }
        if (throwable.getMessage() != null) {
            // Android's bouncycastle returns rather unhelpful errors
            if ((throwable instanceof NullPointerException || throwable instanceof IOException) && throwable.getMessage().contains("password")) {
                return true;
            }
        }
        return false;
    }

    public void addCertificate(Context context, @RawRes int resId, String alias) throws IOException, GeneralSecurityException {
        X509Certificate cert = loadCertificate(context, resId);
        peerKeyStore.setCertificateEntry(alias, cert);
        invalidateTmf();
    }

    public void addCertificate(AssetManager assetManager, String assetPath, String alias) throws IOException, GeneralSecurityException {
        X509Certificate cert = loadCertificate(assetManager, assetPath);
        peerKeyStore.setCertificateEntry(alias, cert);
        invalidateTmf();
    }

    private void addClientKeyImpl(KeyStore keyStore, char[] password) {
        clientKeyStore = keyStore;
        clientKeyStorePassword = password;
        invalidateKmf();
    }

    public void addClientKey(Context context, @RawRes int pfxResId, String password) throws IOException, GeneralSecurityException {
        char[] pwdChars = password != null ? password.toCharArray() : null;
        addClientKeyImpl(loadPKCS12(context, pfxResId, pwdChars), pwdChars);
    }

    public void addClientKey(AssetManager assetManager, String pfxAssetPath, String password) throws IOException, GeneralSecurityException {
        char[] pwdChars = password != null ? password.toCharArray() : null;
        addClientKeyImpl(loadPKCS12(assetManager, pfxAssetPath, pwdChars), pwdChars);
    }

    public void addClientKey(KeyStore keyStore, String password) {
        char[] pwdChars = password != null ? password.toCharArray() : null;
        addClientKeyImpl(keyStore, pwdChars);
    }

    private void invalidateTmf() {
        tmf = null;
    }

    private void invalidateKmf() {
        kmf = null;
    }

    private TrustManagerFactory getTmf() throws NoSuchAlgorithmException, KeyStoreException {
        if (tmf == null) {
            tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(peerKeyStore);
        }
        return tmf;
    }

    private KeyManagerFactory getKmf() throws NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException {
        if (kmf == null) {
            kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(clientKeyStore, clientKeyStorePassword);
        }
        return kmf;
    }

    public SSLContext createSSLContext() throws GeneralSecurityException {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(
                clientKeyStore != null ? getKmf().getKeyManagers() : null,
                getTmf().getTrustManagers(),
                null
        );

        return sslContext;
    }

    public X509TrustManager getX509TrustManager() throws GeneralSecurityException {
        return (X509TrustManager) getTmf().getTrustManagers()[0];
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

    public boolean isDNSNameMatched(X509Certificate[] chain, String domain) throws GeneralSecurityException {
        for (X509Certificate cert : chain) {
            var altNames = cert.getSubjectAlternativeNames();
            if (altNames == null) {
                continue;
            }
            for (var altName : altNames) {
                if (altName != null && altName.size() >= 2 && altName.get(0) instanceof Integer type && altName.get(1) instanceof String value) {
                    if (type == 2 && domain.equalsIgnoreCase(value)) { // DNS name
                        return true;
                    }
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
