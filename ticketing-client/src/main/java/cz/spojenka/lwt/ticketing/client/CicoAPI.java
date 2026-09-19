package cz.spojenka.lwt.ticketing.client;

import cz.spojenka.lwt.ticketing.api.CICOEventBatch;
import cz.spojenka.lwt.ticketing.api.CheckInRequest;
import cz.spojenka.lwt.ticketing.api.CheckInResponse;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface CicoAPI {

    @POST("/cico/check-in")
    public Call<CheckInResponse> checkIn(@Body CheckInRequest request);

    @POST("/cico/events")
    public Call<ResponseBody> pushEvents(@Body CICOEventBatch events);
}
