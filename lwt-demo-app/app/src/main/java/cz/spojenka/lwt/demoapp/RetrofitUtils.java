package cz.spojenka.lwt.demoapp;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import androidx.annotation.NonNull;
import cz.spojenka.android.system.livedata.LiveErrorSignal;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.HttpException;

public class RetrofitUtils {

    public static <T> CompletableFuture<T> toCompletableFuture(Call<T> call) {
        CompletableFuture<T> future = new CompletableFuture<>();
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<T> call, @NonNull retrofit2.Response<T> response) {
                if (response.isSuccessful()) {
                    future.complete(response.body());
                } else {
                    onFailure(call, new HttpException(response));
                }
            }

            @Override
            public void onFailure(@NonNull Call<T> call, @NonNull Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    public static <T> void handle(Call<T> call, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<T> call, @NonNull retrofit2.Response<T> response) {
                if (response.isSuccessful()) {
                    onSuccess.accept(response.body());
                } else {
                    onFailure.accept(new HttpException(response));
                }
            }

            @Override
            public void onFailure(@NonNull Call<T> call, @NonNull Throwable t) {
                onFailure.accept(t);
            }
        });
    }

    public static <T> void handleWithErrorSignal(Call<T> call, LiveErrorSignal errorSignal, Consumer<T> onSuccess) {
        handleWithErrorSignal(call, errorSignal, onSuccess, () -> {
        });
    }

    public static <T> void handleWithErrorSignal(Call<T> call, LiveErrorSignal errorSignal, Consumer<T> onSuccess, Runnable always) {
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<T> call, @NonNull retrofit2.Response<T> response) {
                if (response.isSuccessful()) {
                    onSuccess.accept(response.body());
                    always.run();
                } else {
                    onFailure(call, new HttpException(response));
                }
            }

            @Override
            public void onFailure(@NonNull Call<T> call, @NonNull Throwable t) {
                errorSignal.setValue(t);
                always.run();
            }
        });
    }
}
