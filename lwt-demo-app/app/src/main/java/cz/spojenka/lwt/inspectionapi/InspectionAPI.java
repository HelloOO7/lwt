package cz.spojenka.lwt.inspectionapi;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.security.GeneralSecurityException;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;

import cz.spojenka.lwt.util.TLSTrustManager;
import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import retrofit2.http.GET;

public interface InspectionAPI {

    public static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            .build();

    @GET("/inspection/secrets")
    public Call<List<InspectionSecretResponse>> getInspectionSecrets();

    @GET("/inspection/public-keys/der")
    public Call<List<byte[]>> getPublicKeys();

    public static InspectionAPI create(String baseUrl, TLSTrustManager trustManager) {
        try {
            SSLContext sslContext = trustManager.createSSLContext();
            X509TrustManager x509TrustManager = trustManager.getX509TrustManager();
            OkHttpClient client = new OkHttpClient.Builder()
                    .sslSocketFactory(sslContext.getSocketFactory(), x509TrustManager)
                    .build();

            return new Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(JacksonConverterFactory.create(OBJECT_MAPPER))
                    .build()
                    .create(InspectionAPI.class);
        } catch (GeneralSecurityException ex) {
            throw new RuntimeException(ex);
        }
    }
}
