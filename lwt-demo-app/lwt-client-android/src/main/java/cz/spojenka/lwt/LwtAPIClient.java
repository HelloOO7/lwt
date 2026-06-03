package cz.spojenka.lwt;

import android.bluetooth.BluetoothDevice;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

import cz.spojenka.lwdn.BluetoothLwdnSocketFactory;
import cz.spojenka.lwdn.LwdnSocketFactory;

public class LwtAPIClient extends LwtClient {

    private static final int BLUETOOTH_PSM = 0xD7;

    private final LwtAPI api;

    public LwtAPIClient(LwdnSocketFactory socketFactory) {
        super(socketFactory);
        api = bind(LwtAPI.class);
    }

    public static BluetoothLwdnSocketFactory bluetoothSocketFactory(BluetoothDevice device) {
        return new BluetoothLwdnSocketFactory(device, BLUETOOTH_PSM);
    }

    /**
     * Enqueues an LWT operation for execution. The operation will be run when execute() is called.
     *
     * @param operation the operation, as a LwtAPI method reference, e.g. LwtAPI::ping
     * @param <T>       the type of the operation result
     * @return a future that will be completed with the result of the operation once execute() is called
     */
    public <T> CompletableFuture<T> enqueue(Function<LwtAPI, CompletableFuture<T>> operation) {
        return operation.apply(api);
    }

    /**
     * Convenience method to enqueue and immediately execute an LWT operation. This is equivalent to calling
     * enqueue() followed by execute(). A new LWTP session and LWDN socket will be opened and closed for this operation,
     * so it is not recommended to use this method for multiple operations in a row, as it will be less
     * efficient than enqueuing them together and executing once.
     *
     * @param operation the operation, as a LwtAPI method reference, e.g. LwtAPI::ping
     * @param <T>       the type of the operation result
     * @return a future that will be completed with the result of the operation
     */
    public <T> CompletableFuture<T> call(Function<LwtAPI, CompletableFuture<T>> operation) {
        CompletableFuture<T> future = enqueue(operation);
        execute();
        return future;
    }

    /**
     * Enqueues an LWT operation that takes a ByteBuffer request. The operation will be run when execute() is called.
     *
     * @param operation the operation, as a LwtAPI method reference, e.g. LwtAPI::getTicketValidationInfo
     * @param request   the ByteBuffer flatbuffer request to pass to the operation
     * @param <T>       the type of the operation result
     * @return a future that will be completed with the result of the operation once execute() is called
     */
    public <T> CompletableFuture<T> enqueue(BiFunction<LwtAPI, ByteBuffer, CompletableFuture<T>> operation, ByteBuffer request) {
        return operation.apply(api, request);
    }

    /**
     * Convenience method to enqueue and immediately execute an LWT operation that takes a ByteBuffer request. This is equivalent to calling
     * enqueue() followed by execute(). A new LWTP session and LWDN socket will be opened and closed for this operation,
     * so it is not recommended to use this method for multiple operations in a row, as it will be less
     * efficient than enqueuing them together and executing once.
     *
     * @param operation the operation, as a LwtAPI method reference, e.g. LwtAPI::getTicketValidationInfo
     * @param request   the ByteBuffer flatbuffer request to pass to the operation
     * @param <T>       the type of the operation result
     * @return a future that will be completed with the result of the operation
     */
    public <T> CompletableFuture<T> call(BiFunction<LwtAPI, ByteBuffer, CompletableFuture<T>> operation, ByteBuffer request) {
        CompletableFuture<T> future = enqueue(operation, request);
        execute();
        return future;
    }
}
