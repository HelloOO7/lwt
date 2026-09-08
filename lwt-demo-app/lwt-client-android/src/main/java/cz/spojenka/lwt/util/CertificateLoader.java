package cz.spojenka.lwt.util;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.RawRes;

public class CertificateLoader {

    private static final CertificateFactory certFactoryX509;

    static {
        try {
            certFactoryX509 = CertificateFactory.getInstance("X.509");
        } catch (CertificateException e) {
            throw new RuntimeException("Failed to initialize CertificateFactory", e);
        }
    }

    public static X509Certificate loadCertificate(InputStream in) throws CertificateException {
        return (X509Certificate) certFactoryX509.generateCertificate(in);
    }

    public static X509Certificate[] loadCertificates(InputStream in) throws CertificateException {
        List<X509Certificate> certs = new ArrayList<>();
        for (var cert : certFactoryX509.generateCertificates(in)) {
            certs.add((X509Certificate) cert);
        }
        return certs.toArray(new X509Certificate[0]);
    }

    public static X509Certificate loadCertificate(Context context, @RawRes int resId) throws IOException, CertificateException {
        try (InputStream in = context.getResources().openRawResource(resId)) {
            return loadCertificate(in);
        }
    }

    public static X509Certificate loadCertificate(AssetManager assetManager, String assetPath) throws IOException, CertificateException {
        try (InputStream in = assetManager.open(assetPath)) {
            return loadCertificate(in);
        }
    }

    public static X509Certificate loadCertificate(byte[] certData) throws IOException, CertificateException {
        try (InputStream in = new ByteArrayInputStream(certData)) {
            return loadCertificate(in);
        }
    }

    public static X509Certificate loadCertificate(ByteBuffer certData) throws IOException, CertificateException {
        try (InputStream in = new ByteBufferInputStream(certData)) {
            return loadCertificate(in);
        }
    }

    public static X509Certificate[] loadCertificates(byte[] certData) throws IOException, CertificateException {
        try (InputStream in = new ByteArrayInputStream(certData)) {
            return loadCertificates(in);
        }
    }

    public static X509Certificate[] loadCertificates(ByteBuffer certData) throws IOException, CertificateException {
        try (InputStream in = new ByteBufferInputStream(certData)) {
            return loadCertificates(in);
        }
    }
}
