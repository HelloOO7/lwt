package cz.spojenka.lwt.ticketing.client;

import cz.spojenka.lwt.ticketing.api.OrderResponse;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface OrderingAPI {

    @POST("/orders/new")
    public Call<OrderResponse> createOrder(@Query("products[]") int[] productIds);

    @POST("/orders/{id}/test-pay")
    public Call<OrderResponse> testPayOrder(@Query("id") long orderId);

    @GET("/orders/{id}")
    public Call<OrderResponse> getOrderInfo(@Query("id") long orderId);
}
