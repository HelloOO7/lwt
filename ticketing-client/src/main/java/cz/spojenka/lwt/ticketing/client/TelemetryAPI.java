package cz.spojenka.lwt.ticketing.client;

import cz.spojenka.lwt.ticketing.api.*;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface TelemetryAPI {

    @POST("/telemetry/events")
    public Call<ResponseBody> pushEvents(@Body TelemetryEventBatch events);
}
