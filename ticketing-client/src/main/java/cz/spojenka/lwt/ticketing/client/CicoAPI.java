package cz.spojenka.lwt.ticketing.client;

import cz.spojenka.lwt.ticketing.api.*;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

import java.util.UUID;

public interface CicoAPI {

    @POST("/cico/check-in")
    public Call<CheckInResponse> checkIn(@Body CheckInRequest request);

    @POST("/cico/self-check-out")
    public Call<ResponseBody> selfCheckOut(@Body CheckOutRequest request);

    @POST("/cico/events")
    public Call<ResponseBody> pushEvents(@Body CICOEventBatch events);

    @GET("/cico/evaluate-session")
    public Call<CICOClearedFare> evaluateSession(@Query("sessionId") UUID sessionId);
}
