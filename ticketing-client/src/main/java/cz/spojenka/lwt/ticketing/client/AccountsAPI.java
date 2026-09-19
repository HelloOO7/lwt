package cz.spojenka.lwt.ticketing.client;

import cz.spojenka.lwt.ticketing.api.AccountResponse;
import cz.spojenka.lwt.ticketing.api.ClientOAuthConfig;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface AccountsAPI {

    @GET("/accounts/oauth-config")
    public Call<ClientOAuthConfig> getOAuthConfig();

    @GET("/accounts/me")
    public Call<AccountResponse> getAccount();

    @POST("/accounts/me")
    public Call<AccountResponse> getOrCreateAccountByOAuth(@Query("createIfNew") boolean createIfNew);

    @POST("/accounts/me/reset-cico-token")
    public Call<AccountResponse> resetCicoToken();
}
