package cz.spojenka.lwt.ticketing.client;

import java.util.List;

import cz.spojenka.lwt.ticketing.api.InspectionSecretResponse;
import retrofit2.Call;
import retrofit2.http.GET;

public interface InspectionAPI {

    @GET("/inspection/secrets")
    public Call<List<InspectionSecretResponse>> getInspectionSecrets();

    @GET("/inspection/public-keys/der")
    public Call<List<byte[]>> getPublicKeys();

    @GET("/inspection/public-keys/pem")
    public Call<List<String>> getPublicKeysAsPem();
}
