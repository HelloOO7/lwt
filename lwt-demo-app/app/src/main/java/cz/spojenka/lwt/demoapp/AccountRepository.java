package cz.spojenka.lwt.demoapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import net.openid.appauth.AppAuthConfiguration;
import net.openid.appauth.AuthState;
import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationService;
import net.openid.appauth.connectivity.ConnectionBuilder;

import org.json.JSONException;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import cz.spojenka.android.settings.SharedPrefsHelper;
import cz.spojenka.lwt.ticketing.api.AccountResponse;
import cz.spojenka.lwt.ticketing.client.TicketingAuthenticator;

public class AccountRepository {

    private static final String TAG = AccountRepository.class.getSimpleName();

    private static AccountRepository instance;

    private static final String PK_AUTH_STATE = "authState";
    private static final String PK_ACCOUNT_DATA = "accountData";

    private final SharedPreferences prefs;

    private AuthorizationService authService;
    private AuthState authState;
    private String lastSerializedAuthState;
    private AccountResponse accountData;
    private CompletableFuture<String> tokenRefresh;

    public AccountRepository(Context context) {
        context = context.getApplicationContext();
        authService = new AuthorizationService(context, new AppAuthConfiguration.Builder()
                .setSkipIssuerHttpsCheck(true)
                .setConnectionBuilder(createInsecureConnectionBuilder())
                .build());
        prefs = context.getSharedPreferences("account", Context.MODE_PRIVATE);
    }

    public static ConnectionBuilder createInsecureConnectionBuilder() {
        return new ConnectionBuilder() {
            @NonNull
            @Override
            public HttpURLConnection openConnection(@NonNull Uri uri) throws IOException {
                // default connection builder rejects cleartext, which we use for the debug server
                HttpURLConnection conn = (HttpURLConnection) new URL(uri.toString()).openConnection();
                conn.setConnectTimeout(1000);
                conn.setReadTimeout(5000);
                return conn;
            }
        };
    }

    public static AccountRepository getInstance(Context context) {
        if (instance == null) {
            instance = new AccountRepository(context);
        }
        return instance;
    }

    public AuthorizationService getAuthService() {
        return authService;
    }

    public void setAuthState(AuthState authState) {
        this.authState = authState;
        saveAuthState();
    }

    public boolean isLoggedIn() {
        return getAuthState() != null && getAuthState().isAuthorized();
    }

    public AuthState getAuthState() {
        if (authState == null) {
            String json = prefs.getString(PK_AUTH_STATE, null);
            if (json != null) {
                try {
                    authState = AuthState.jsonDeserialize(json);
                    lastSerializedAuthState = json;
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing auth state", e);
                }
            }
        }
        return authState;
    }

    private void saveAuthState() {
        if (authState != null) {
            String serialized = authState.jsonSerializeString();
            if (!Objects.equals(lastSerializedAuthState, serialized)) {
                prefs.edit().putString(PK_AUTH_STATE, serialized).apply();
                lastSerializedAuthState = serialized;
            }
        } else {
            prefs.edit().remove(PK_AUTH_STATE).apply();
        }
    }

    public AccountResponse getAccountData() {
        if (accountData == null) {
            accountData = SharedPrefsHelper.loadObject(prefs, PK_ACCOUNT_DATA, AccountResponse.class);
        }
        return accountData;
    }

    public void setAccountData(AccountResponse accountData) {
        this.accountData = accountData;
        SharedPrefsHelper.saveObject(prefs, PK_ACCOUNT_DATA, accountData);
    }

    public TicketingAuthenticator createAuthenticator() {
        return new TicketingAuthenticator() {
            @Override
            public String getAccessToken() throws IOException {
                CompletableFuture<String> future;
                synchronized (AccountRepository.this) {
                    AuthState currentAuthState = getAuthState();
                    if (currentAuthState == null) {
                        return null;
                    }

                    // Refresh tokens may be rotated by the server. Share one refresh
                    // operation so concurrent requests cannot race condition and reuse the old token.
                    if (tokenRefresh == null) {
                        CompletableFuture<String> refresh = new CompletableFuture<>();
                        tokenRefresh = refresh;
                        future = refresh;
                        currentAuthState.performActionWithFreshTokens(authService, new AuthState.AuthStateAction() {
                            @Override
                            public void execute(@Nullable String accessToken, @Nullable String idToken, @Nullable AuthorizationException ex) {
                                synchronized (AccountRepository.this) {
                                    if (ex == null) {
                                        saveAuthState();
                                        refresh.complete(accessToken);
                                    } else {
                                        if (ex != AuthorizationException.GeneralErrors.NETWORK_ERROR && ex != AuthorizationException.GeneralErrors.SERVER_ERROR) {
                                            setAuthState(null);
                                        }
                                        refresh.completeExceptionally(ex);
                                    }
                                    tokenRefresh = null;
                                }
                            }
                        });
                    } else {
                        future = tokenRefresh;
                    }
                }

                try {
                    return future.join();
                } catch (CompletionException ex) {
                    if (ex.getCause() instanceof IOException ioe) {
                        throw ioe;
                    } else if (ex.getCause() instanceof AuthorizationException auth) {
                        throw new IOException("Authorization error", auth);
                    } else {
                        throw ex;
                    }
                }
            }
        };
    }
}
