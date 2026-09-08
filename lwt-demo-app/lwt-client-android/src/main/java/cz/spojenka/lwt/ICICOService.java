package cz.spojenka.lwt;

import android.os.IBinder;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import javax.net.ssl.SSLContext;

import androidx.lifecycle.LiveData;

public interface ICICOService extends IBinder {

    /**
     * Sets the SSL context for secure communication with the ticketing server.
     * This can be left null to use cleartext communication. However, that is a major
     * security flaw and the service will warn you about this.
     *
     * @param sslContext The SSL context to use
     */
    public void initSecureContext(SSLContext sslContext);

    /**
     * Prepares a CICO session by starting a device scan.
     * The returned LiveData will be continuously updated with a list of devices
     * which can be used to request a session.
     * If a session is already being prepared, the same LiveData instance will be returned.
     *
     * @return LiveData with device list
     * @see #cancelPrepareSession()
     */
    public LiveData<List<LwtDevice>> prepareSession();

    /**
     * Cancels the device scan started by prepareSession().
     * After calling this method, the LiveData returned by prepareSession()
     * will no longer be updated, even if another session is prepared later.
     */
    public void cancelPrepareSession();

    /**
     * Returns whether a CICO session is currently being prepared (device scan is running).
     * This can be set to false either as a result of {@link #cancelPrepareSession()},
     * or a failure.
     *
     * @return true/false
     */
    public boolean isPrepareSessionRunning();

    /**
     * Requests a CICO session with the specified device. This will validate
     * that the user has a valid session start token and the account is eligible
     * for CICO (e.g. not blocked, sufficient funds etc.).
     *
     * @param device The device
     * @param cicoToken Token obtained from ticketing server for CICO operations
     * @return Future that will be completed when device communication is done. This future
     * can not be canceled - for that, use {@link #cancelRequestSession()}.
     */
    public CompletableFuture<?> requestSession(LwtDevice device, byte[] cicoToken);

    /**
     * Cancel a pending session request (including disconnecting from client).
     * If no session is pending, this has no effect.
     */
    public void cancelRequestSession();

    /**
     * Starts a CICO session with the previously requested device.
     *
     * @return CompletableFuture<Void> that completes when the session is started. It may
     * complete exceptionally with an IOException if device connection fails.
     * @throws IllegalStateException                                 if a session is already active, or if no session has been requested
     * @throws android.app.ForegroundServiceStartNotAllowedException if the foreground service can not be started,
     *                                                               such as because of Android background start restrictions
     */
    public CompletableFuture<?> startSession();

    /**
     * Ends the current CICO session. If a device is currently linked,
     * a check-out request will be sent to it.
     *
     * @return CompletableFuture<Void> that completes when the session is ended. It may
     * complete exceptionally with an IOException if device communication fails. The session
     * will, however, be terminated regardless.
     * @throws IllegalStateException if no session is active
     */
    public CompletableFuture<?> endSession();

    /**
     * Returns whether a CICO session is currently active.
     *
     * @return true if a session is active, false otherwise
     */
    public boolean isSessionActive();

    /**
     * Get the continuously updated list of devices in proximity.
     * The LiveData persists across session end/restart. It is only updated when
     * a session is running, not when it is being prepared (a separate
     * LiveData is used for that, see {@link #prepareSession()}).
     *
     * @return the LiveData
     */
    public LiveData<List<LwtDevice>> getDevicesInProximityLiveData();

    /**
     * Force a switch to another device for further CICO operations. This is intended
     * for use during ticket inspection, where the passenger can choose to request
     * a new ticket from the vehicle they are currently on, in case that it is not
     * the device with the highest RSSI. The device used does not affect the final calculation,
     * if it is guessed wrong (provided there is enough data given to correct it), but
     * for inspection, this serves as an override.
     *
     * @param device The device to use
     * @return Future that completes successfully when the device was changed and a ticket
     * was obtained, or exceptionally with the error that occurred.
     */
    public CompletableFuture<?> forceDeviceChange(LwtDevice device);

    /**
     * Check if a device is connected, either as part of a requested or active session.
     *
     * @return true/false
     */
    public boolean isConnectedToDevice();

    /**
     * Get a LiveData that is continuously updated with the LWT device that has issued
     * the most recent CICO ticket. The LiveData will be automatically updated whenever
     * a BLE advertisement is received from the currently connected device.
     *
     * @return The LiveData
     */
    public LiveData<LwtDevice> getCurrentDeviceLiveData();

    /**
     * Get a LiveData that is continuously updated with the most up-to-date
     * CICO ticket fragment. This ticket fragment may be out of date if there
     * was no device present for a long period of time.
     *
     * @return The LiveData
     */
    public LiveData<CICOTicketFragment> getCurrentTicketLiveData();
}
