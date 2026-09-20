package cz.spojenka.lwt.demoapp;

import android.app.Application;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import net.openid.appauth.AuthState;
import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationRequest;
import net.openid.appauth.AuthorizationResponse;
import net.openid.appauth.AuthorizationService;
import net.openid.appauth.AuthorizationServiceConfiguration;
import net.openid.appauth.ResponseTypeValues;
import net.openid.appauth.TokenResponse;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.content.res.ResourcesCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import cz.spojenka.android.system.livedata.LiveErrorSignal;
import cz.spojenka.android.ui.activity.BaseActivity;
import cz.spojenka.lwt.demoapp.databinding.ActivityLoginBinding;
import cz.spojenka.lwt.ticketing.api.AccountResponse;
import cz.spojenka.lwt.ticketing.api.ClientOAuthConfig;
import cz.spojenka.lwt.ticketing.client.AccountsAPI;
import cz.spojenka.lwt.ticketing.client.TicketingAuthenticator;
import cz.spojenka.lwt.ticketing.client.TicketingClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.HttpException;
import retrofit2.Response;

public class LoginActivity extends BaseActivity {

    private ActivityLoginBinding binding;

    private AuthorizationService authService;
    private ViewModel viewModel;
    private CustomTabsIntent customTabsIntent;

    private ActivityResultLauncher<Intent> oidcLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        authService = AccountRepository.getInstance(this).getAuthService();
        viewModel = new ViewModelProvider(this).get(ViewModel.class);
        customTabsIntent = authService.createCustomTabsIntentBuilder().build();

        oidcLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            viewModel.handleAuthorizationResponse(result.getData());
        });

        addOnNewIntentListener(intent -> viewModel.handleAuthorizationResponse(intent));
        viewModel.handleAuthorizationResponse(getIntent());

        viewModel.getLoadingState().observe(this, loading -> {
            boolean loadingVisible = loading != LoadingState.ERROR;
            binding.pbLoginProgress.setVisibility(loadingVisible ? View.VISIBLE : View.GONE);
            if (loadingVisible) {
                updateTextByLoginState();
            }
            if (loading == LoadingState.USER_AUTH) {
                callOidc();
            } else if (loading == LoadingState.ALL_DONE) {
                finish();
            }
        });

        viewModel.getErrorSignal().observe(this, err -> {
            if (err != null) {
                binding.tvLoginState.setText(err.getMessage());
                binding.btnRetryLogin.setVisibility(View.VISIBLE);
                binding.ivLoginErrorIcon.setVisibility(View.VISIBLE);
            } else {
                binding.btnRetryLogin.setVisibility(View.GONE);
                binding.ivLoginErrorIcon.setVisibility(View.GONE);
                updateTextByLoginState();
            }
        }, true);

        binding.btnRetryLogin.setOnClickListener(v -> {
            viewModel.getErrorSignal().ack();
            viewModel.retryLogin();
        });
    }

    private void updateTextByLoginState() {
        binding.tvLoginState.setText(switch (Objects.requireNonNull(viewModel.getLoadingState().getValue())) {
            case OIDC -> R.string.login_state_oidc;
            case USER_AUTH -> R.string.login_state_user_auth;
            case GET_TOKENS -> R.string.login_state_get_tokens;
            case ACCOUNT_DATA -> R.string.login_state_get_account;
            case ALL_DONE -> R.string.login_state_done;
            case ERROR -> R.string.error;
        });
    }

    private void callOidc() {
        AuthorizationRequest request = viewModel.createAuthorizationRequest();
        oidcLauncher.launch(authService.getAuthorizationRequestIntent(request, customTabsIntent));
    }

    public static class ViewModel extends AndroidViewModel {

        private final AccountRepository accountRepository;

        private final AccountsAPI accountsAPI;
        private CompletableFuture<ClientOAuthConfig> clientConfig = null;
        private CompletableFuture<AuthorizationServiceConfiguration> authConfig = null;

        private final LiveErrorSignal errorSignal = new LiveErrorSignal();
        private final MutableLiveData<LoadingState> loadingState = new MutableLiveData<>(LoadingState.OIDC);

        private AuthState authState;

        public ViewModel(@NonNull Application application) {
            super(application);
            accountRepository = AccountRepository.getInstance(getApplication());
            accountsAPI = new TicketingClient(
                    BuildConfig.TICKETING_SERVER_URL,
                    GlobalTrustManager.createMosNetworkClient(application),
                    new TicketingAuthenticator() {

                        private final TicketingAuthenticator baseAuth = accountRepository.createAuthenticator();

                        @Override
                        public String getAccessToken() throws IOException {
                            if (authState != null) {
                                return baseAuth.getAccessToken();
                            } else {
                                // before we authenticate, act as if there is no access token
                                // (which forces oidc configuration to be loaded without attempting refresh)
                                return null;
                            }
                        }
                    }
            ).getAccountsAPI();
            continueFetchConfigs();
        }

        private void continueFetchConfigs() {
            loadingState.setValue(LoadingState.OIDC);
            boolean clientConfigIsNew = false;
            if (clientConfig == null || clientConfig.isCompletedExceptionally()) {
                clientConfig = new CompletableFuture<>();
                clientConfigIsNew = true;
            }
            if (!clientConfig.isDone()) {
                accountsAPI.getOAuthConfig().enqueue(new Callback<ClientOAuthConfig>() {
                    @Override
                    public void onResponse(@NonNull Call<ClientOAuthConfig> call, @NonNull Response<ClientOAuthConfig> response) {
                        if (response.isSuccessful()) {
                            clientConfig.complete(response.body());
                        } else {
                            onFailure(call, new HttpException(response));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ClientOAuthConfig> call, @NonNull Throwable t) {
                        setError(t);
                        clientConfig.completeExceptionally(t);
                    }
                });
            }
            if (authConfig == null || authConfig.isCompletedExceptionally()) {
                authConfig = new CompletableFuture<>();
            }
            if (!authConfig.isDone() && clientConfigIsNew) {
                clientConfig.thenAccept(clientOAuthConfig -> {
                    AuthorizationServiceConfiguration.fetchFromIssuer(Uri.parse(clientOAuthConfig.issuerUri()), new AuthorizationServiceConfiguration.RetrieveConfigurationCallback() {
                        @Override
                        public void onFetchConfigurationCompleted(@Nullable AuthorizationServiceConfiguration serviceConfiguration, @Nullable AuthorizationException ex) {
                            if (ex != null) {
                                setError(ex);
                                authConfig.completeExceptionally(ex);
                            } else {
                                authState = new AuthState(Objects.requireNonNull(serviceConfiguration));
                                authConfig.complete(serviceConfiguration);
                                loadingState.setValue(LoadingState.USER_AUTH);
                            }
                        }
                    }, AccountRepository.createInsecureConnectionBuilder());
                });
            }
        }

        public void retryLogin() {
            if (!canUseOidc()) {
                continueFetchConfigs();
            } else {
                if (authState.isAuthorized()) {
                    loadAccountData();
                } else {
                    loadingState.setValue(LoadingState.USER_AUTH);
                }
            }
        }

        public boolean canUseOidc() {
            return clientConfig.isDone() && authConfig.isDone() && !clientConfig.isCompletedExceptionally() && !authConfig.isCompletedExceptionally();
        }

        public boolean isAuthorized() {
            return authState.isAuthorized();
        }

        public AuthorizationRequest createAuthorizationRequest() {
            if (!clientConfig.isDone() || !authConfig.isDone()) {
                throw new IllegalStateException("Configs not loaded yet");
            }
            AuthorizationRequest.Builder builder = new AuthorizationRequest.Builder(
                    authConfig.join(),
                    BuildConfig.OAUTH_CLIENT_ID,
                    ResponseTypeValues.CODE,
                    Uri.parse("lwtapp://oauth/callback")
            );
            builder.setScopes(clientConfig.join().requiredScopes());
            return builder.build();
        }

        public void handleAuthorizationResponse(Intent intent) {
            if (intent == null) {
                return;
            }
            AuthorizationException exception = AuthorizationException.fromIntent(intent);
            if (exception != null) {
                setError(exception);
            } else {
                AuthorizationResponse resp = AuthorizationResponse.fromIntent(intent);
                if (resp != null) {
                    authState.update(resp, exception);
                    loadingState.setValue(LoadingState.GET_TOKENS);
                    accountRepository.getAuthService().performTokenRequest(resp.createTokenExchangeRequest(), new AuthorizationService.TokenResponseCallback() {
                        @Override
                        public void onTokenRequestCompleted(@Nullable TokenResponse tokenResp, @Nullable AuthorizationException tokenEx) {
                            if (tokenEx != null) {
                                setError(tokenEx);
                            } else {
                                authState.update(tokenResp, tokenEx);
                                accountRepository.setAuthState(authState);
                                loadAccountData();
                            }
                        }
                    });
                }
            }
        }

        private void loadAccountData() {
            loadingState.setValue(LoadingState.ACCOUNT_DATA);
            accountsAPI.getOrCreateAccountByOAuth(true).enqueue(new Callback<AccountResponse>() {
                @Override
                public void onResponse(@NonNull Call<AccountResponse> call, @NonNull Response<AccountResponse> response) {
                    if (response.isSuccessful()) {
                        accountRepository.setAccountData(response.body());
                        loadingState.setValue(LoadingState.ALL_DONE);
                    } else {
                        setError(new HttpException(response));
                    }
                }

                @Override
                public void onFailure(@NonNull Call<AccountResponse> call, @NonNull Throwable t) {
                    setError(t);
                }
            });
        }

        private void setError(Throwable t) {
            errorSignal.setValue(t);
            loadingState.setValue(LoadingState.ERROR);
        }

        public LiveData<LoadingState> getLoadingState() {
            return loadingState;
        }

        public LiveErrorSignal getErrorSignal() {
            return errorSignal;
        }
    }

    public static enum LoadingState {
        OIDC,
        USER_AUTH,
        GET_TOKENS,
        ACCOUNT_DATA,
        ALL_DONE,
        ERROR
    }
}
