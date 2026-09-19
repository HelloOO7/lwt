package cz.spojenka.lwt.ticketing.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.androidrecord.AndroidRecordModule;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.IOException;

public class TicketingClient {

    public static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .addModule(new AndroidRecordModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            .build();

    private final Retrofit retrofit;

    private InspectionAPI inspectionAPI;
    private AccountsAPI accountsAPI;
    private CicoAPI cicoAPI;

    public TicketingClient(String baseUrl, OkHttpClient okHttpClient) {
        this(baseUrl, okHttpClient, null);
    }

    public TicketingClient(String baseUrl, OkHttpClient okHttpClient, TicketingAuthenticator authenticator) {
        this.retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient
                        .newBuilder()
                        .addInterceptor(new Interceptor() {
                            @NotNull
                            @Override
                            public Response intercept(@NotNull Chain chain) throws IOException {
                                if (authenticator != null) {
                                    String jwt = authenticator.getAccessToken();
                                    if (jwt != null) {
                                        return chain.proceed(chain.request().newBuilder()
                                                .header("Authorization", "Bearer " + jwt)
                                                .build());
                                    }
                                }
                                return chain.proceed(chain.request());
                            }
                        })
                        .build())
                .addConverterFactory(JacksonConverterFactory.create(OBJECT_MAPPER))
                .build();
    }

    public InspectionAPI getInspectionAPI() {
        if (inspectionAPI == null) {
            inspectionAPI = retrofit.create(InspectionAPI.class);
        }
        return inspectionAPI;
    }

    public AccountsAPI getAccountsAPI() {
        if (accountsAPI == null) {
            accountsAPI = retrofit.create(AccountsAPI.class);
        }
        return accountsAPI;
    }

    public CicoAPI getCicoAPI() {
        if (cicoAPI == null) {
            cicoAPI = retrofit.create(CicoAPI.class);
        }
        return cicoAPI;
    }
}
