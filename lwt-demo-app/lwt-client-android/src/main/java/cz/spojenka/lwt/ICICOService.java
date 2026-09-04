package cz.spojenka.lwt;

import android.os.IBinder;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import androidx.lifecycle.LiveData;

public interface ICICOService extends IBinder {

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
     * Requests a CICO session with the specified device. This will validate
     * that the user has a valid session start token and the account is eligible
     * for CICO (e.g. not blocked, sufficient funds etc.).
     *
     * @param device The device
     */
    public CompletableFuture<?> requestSession(LwtDevice device);

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
     * Get a LiveData that is continuously updated with the LWT device that has issued
     * the most recent CICO ticket.
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
