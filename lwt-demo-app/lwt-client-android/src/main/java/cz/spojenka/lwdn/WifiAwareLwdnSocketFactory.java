package cz.spojenka.lwdn;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.aware.WifiAwareNetworkInfo;
import android.net.wifi.aware.WifiAwareNetworkSpecifier;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import androidx.annotation.NonNull;

/**
 * Usage note: NAN datapaths are often VERY unstable if there is ongoing activity on the Wi-Fi radio.
 * This is because some devices prefer STA network over NAN datapath and will throttle NAN to the
 * point of unusability.
 */
public class WifiAwareLwdnSocketFactory implements LwdnSocketFactory {

    private static final String TAG = "WifiAwareLwdnSocketFactory";

    private final WifiAwareLwdnAddress address;

    private final ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    private InetLwdnSocketFactory currentSocketFactory;
    private CompletableFuture<InetLwdnSocketFactory> socketFactoryFuture = new CompletableFuture<>();
    private boolean isDatapathRequested = false;
    private boolean isDatapathLostDefinitively = false;
    private boolean isClosed = false;

    public WifiAwareLwdnSocketFactory(ConnectivityManager connectivityManager, WifiAwareLwdnAddress address) {
        this.connectivityManager = connectivityManager;
        this.address = address;
    }

    private void setupDatapath() {
        if (isDatapathLostDefinitively) {
            return;
        }
        if (!address.isValid()) {
            isDatapathLostDefinitively = true;
            onDatapathLost();
            return;
        }

        Log.d(TAG, "Requesting datapath to " + address);
        connectivityManager
                .requestNetwork(
                        new NetworkRequest.Builder()
                                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
                                .setNetworkSpecifier(new WifiAwareNetworkSpecifier.Builder(address.getDiscoverySession(), address.getPeerHandle()).build())
                                .build(),
                        networkCallback = new ConnectivityManager.NetworkCallback() {

                            private WifiAwareNetworkInfo lastAwareNetworkInfo;

                            @Override
                            public void onAvailable(@NonNull Network network) {
                                if (isClosed) {
                                    Log.w(TAG, "Datapath available but socket factory is closed");
                                    return;
                                }
                                Log.d(TAG, "Datapath available - " + network);
                            }

                            @Override
                            public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
                                if (isClosed) {
                                    Log.w(TAG, "Datapath capabilities changed but socket factory is closed");
                                    return;
                                }
                                if (networkCapabilities.getTransportInfo() instanceof WifiAwareNetworkInfo awareNetworkInfo) {
                                    if (awareNetworkInfo.equals(lastAwareNetworkInfo)) {
                                        return;
                                    } else {
                                        lastAwareNetworkInfo = awareNetworkInfo;
                                    }
                                    Log.d(TAG, "Datapath established - " + awareNetworkInfo);
                                    // we do not use the port number from awareNetworkInfo.getPortNumber
                                    onDatapathEstablished(new InetLwdnSocketFactory(network.getSocketFactory(), awareNetworkInfo.getPeerIpv6Addr(), address.getPortNumber()));
                                } else {
                                    onDatapathLost();
                                }
                            }

                            @Override
                            public void onLost(@NonNull Network network) {
                                Log.d(TAG, "Datapath lost");
                                onDatapathLost();
                            }

                            @Override
                            public void onUnavailable() {
                                Log.d(TAG, "Datapath unavailable");
                                onDatapathEstablishFailed();
                            }
                        }
                );
    }

    private synchronized void onDatapathEstablished(InetLwdnSocketFactory socketFactory) {
        currentSocketFactory = socketFactory;
        if (!socketFactoryFuture.isDone()) {
            socketFactoryFuture.complete(socketFactory);
        } else {
            socketFactoryFuture = CompletableFuture.completedFuture(socketFactory);
        }
    }

    private synchronized void onDatapathEstablishFailed() {
        if (!socketFactoryFuture.isDone()) {
            socketFactoryFuture.completeExceptionally(new IOException("Unable to establish datapath"));
        }
        socketFactoryFuture = new CompletableFuture<>();
        stopDatapathRequest();
    }

    private synchronized void onDatapathLost() {
        releaseSocketFactory();
        if (!socketFactoryFuture.isDone()) {
            socketFactoryFuture.completeExceptionally(new IOException("Datapath lost"));
        }
        socketFactoryFuture = new CompletableFuture<>();
        if (isDatapathLostDefinitively) {
            socketFactoryFuture.completeExceptionally(new IOException("DiscoverySession ended"));
        } else {
            stopDatapathRequest();
        }
    }

    private void releaseSocketFactory() {
        if (currentSocketFactory != null) {
            currentSocketFactory.close();
            currentSocketFactory = null;
        }
    }

    @Override
    public LwdnSocket openSocket() throws IOException {
        return new Socket();
    }

    @Override
    public synchronized void close() {
        Log.d(TAG, "Closing WifiAwareLwdnSocketFactory");
        isClosed = true;
        releaseSocketFactory();
        if (!socketFactoryFuture.isDone()) {
            socketFactoryFuture.completeExceptionally(new IOException("Socket closed"));
        }
        stopDatapathRequest();
    }

    private void stopDatapathRequest() {
        if (networkCallback != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
            networkCallback = null;
        }
        isDatapathRequested = false;
    }

    private synchronized CompletableFuture<InetLwdnSocketFactory> getSocketFactoryFuture() {
        if (!isDatapathRequested && !isClosed) {
            isDatapathRequested = true;
            setupDatapath();
        }
        return socketFactoryFuture;
    }

    private class Socket implements LwdnSocket {

        private InetLwdnSocket implSocket;

        private InetLwdnSocket ensureImplSocket() throws IOException {
            while (implSocket == null) {
                try {
                    implSocket = getSocketFactoryFuture().join().openSocket();
                } catch (CompletionException ex) {
                    if (ex.getCause() instanceof IOException ioe) {
                        // throw a new one so that we get correct stack trace
                        throw new IOException(ioe.getMessage(), ioe.getCause());
                    } else {
                        throw ex;
                    }
                }
            }
            return implSocket;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return ensureImplSocket().getInputStream();
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            return ensureImplSocket().getOutputStream();
        }

        @Override
        public boolean isOpen() {
            return implSocket != null && implSocket.isOpen();
        }

        @Override
        public void close() throws IOException {
            if (implSocket != null) {
                implSocket.close();
            }
        }
    }
}
