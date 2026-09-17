package cz.spojenka.lwt.cicomock.services;

import cz.spojenka.lwt.cicomock.api.ticketing.CICOEventBatch;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.CertificateFactory;

@Service
public class EventSenderService {

    @Value("${ticketing-server.base-url}")
    private String ticketingServerBaseUrl;
    @Value("${ticketing-server.ca-cert.path}")
    private String caCertPath;
    @Value("${ticketing-server.client-cert.path}")
    private String clientCertPath;
    @Value("${ticketing-server.client-cert.password}")
    private String clientCertPassword;

    private HttpClient client;
    private ObjectMapper objectMapper = JsonMapper.builder().build();

    @PostConstruct
    public void init() throws GeneralSecurityException, IOException {
        client = HttpClient.newBuilder()
                .sslContext(createSslContext())
                .build();
    }

    private SSLContext createSslContext() throws IOException, GeneralSecurityException {
        KeyManagerFactory kmf = loadClientCert();
        KeyManager[] keyManagers = kmf.getKeyManagers();
        TrustManagerFactory tmf = loadCACert();
        TrustManager[] trustManagers = tmf.getTrustManagers();
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagers, trustManagers, null);
        return context;
    }

    private KeyManagerFactory loadClientCert() throws IOException, GeneralSecurityException {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(clientCertPath)) {
            keyStore.load(fis, clientCertPassword.toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, clientCertPassword.toCharArray());
        return kmf;
    }

    private TrustManagerFactory loadCACert() throws IOException, GeneralSecurityException {
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        try (FileInputStream fis = new FileInputStream(caCertPath)) {
            trustStore.setCertificateEntry("ca-cert", cf.generateCertificate(fis));
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        return tmf;
    }

    public void sendEventsToMOS(CICOEventBatch events) throws IOException, InterruptedException {
        String json = objectMapper.writeValueAsString(events);
        HttpRequest request = HttpRequest.newBuilder()
                .header("Content-Type", "application/json")
                .uri(URI.create(ticketingServerBaseUrl + "/cico/events"))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
