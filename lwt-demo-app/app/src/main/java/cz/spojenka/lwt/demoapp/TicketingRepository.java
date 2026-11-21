package cz.spojenka.lwt.demoapp;

import android.app.Application;
import android.content.SharedPreferences;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import androidx.annotation.NonNull;
import cz.spojenka.android.settings.SharedPrefsHelper;
import cz.spojenka.android.util.AsyncUtils;
import cz.spojenka.lwt.ticketing.api.ProductResponse;
import cz.spojenka.lwt.ticketing.client.TicketingAuthenticator;
import cz.spojenka.lwt.ticketing.client.TicketingClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.HttpException;
import retrofit2.Response;

public class TicketingRepository {

    private static final String PRODUCTS_ETAG = "products_etag";
    private static final String PRODUCTS_DATA = "products_data";

    private static TicketingRepository INSTANCE;

    private final Application app;
    private final SharedPreferences productsPrefs;

    private List<ProductResponse> cachedProducts;
    private CompletableFuture<List<ProductResponse>> updatingProductsFuture;

    private TicketingRepository(Application app) {
        this.app = app;
        this.productsPrefs = app.getSharedPreferences("products", Application.MODE_PRIVATE);
    }

    public static synchronized TicketingRepository getInstance(Application app) {
        if (INSTANCE == null) {
            INSTANCE = new TicketingRepository(app);
        }
        return INSTANCE;
    }

    public TicketingClient createTicketingClient() {
        return createTicketingClient(AccountRepository.getInstance(app).createAuthenticator());
    }

    public TicketingClient createTicketingClient(TicketingAuthenticator authenticator) {
        return new TicketingClient(BuildConfig.TICKETING_SERVER_URL, GlobalTrustManager.createMosNetworkClient(app), authenticator);
    }

    private void tryLoadLocalProductCache() {
        if (cachedProducts == null) {
            var loaded = SharedPrefsHelper.loadObject(productsPrefs, PRODUCTS_DATA, ProductResponse[].class);
            if (loaded != null) {
                cachedProducts = Arrays.asList(loaded);
            }
        }
    }

    private void saveLocalProductCache(String etag) {
        if (cachedProducts != null) {
            SharedPrefsHelper.saveObject(productsPrefs, PRODUCTS_DATA, cachedProducts);
            productsPrefs.edit().putString(PRODUCTS_ETAG, etag).apply();
        }
    }

    public synchronized CompletableFuture<List<ProductResponse>> updateProducts() {
        if (updatingProductsFuture != null) {
            return updatingProductsFuture;
        }

        CompletableFuture<List<ProductResponse>> out = new CompletableFuture<>();

        createTicketingClient(null).getTicketsAPI().getAvailableTickets(false, productsPrefs.getString(PRODUCTS_ETAG, null)).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<ProductResponse>> call, @NonNull Response<List<ProductResponse>> response) {
                if (response.isSuccessful()) {
                    cachedProducts = response.body();
                    saveLocalProductCache(response.headers().get("ETag"));
                    out.complete(cachedProducts);
                    return;
                } else if (response.code() == 304) {
                    tryLoadLocalProductCache();
                    if (cachedProducts != null) {
                        out.complete(cachedProducts);
                        return;
                    }
                }
                onFailure(call, new HttpException(response));
            }

            @Override
            public void onFailure(@NonNull Call<List<ProductResponse>> call, @NonNull Throwable t) {
                if (t instanceof IOException) {
                    tryLoadLocalProductCache();
                    if (cachedProducts != null) {
                        out.complete(cachedProducts);
                        return;
                    }
                }
                out.completeExceptionally(t);
            }
        });

        updatingProductsFuture = out;
        out.whenComplete((productResponses, throwable) -> {
            synchronized (this) {
                updatingProductsFuture = null;
            }
        });

        return out;
    }

    public CompletableFuture<List<ProductResponse>> getProducts() {
        if (cachedProducts != null) {
            return CompletableFuture.completedFuture(cachedProducts);
        } else {
            return updateProducts();
        }
    }
}
