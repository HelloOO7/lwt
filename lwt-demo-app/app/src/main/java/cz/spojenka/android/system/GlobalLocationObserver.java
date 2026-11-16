package cz.spojenka.android.system;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.CancellationSignal;
import android.os.Looper;
import android.util.Log;

import java.time.Duration;
import java.time.Instant;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.annotation.UiThread;
import androidx.core.content.ContextCompat;
import androidx.core.location.LocationListenerCompat;
import androidx.core.location.LocationManagerCompat;
import androidx.core.location.LocationRequestCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import cz.spojenka.android.util.LiveDataUtils;

/**
 * Singleton class that manages location updates and exposes them using LiveData.
 * Instead of using {@link LocationManager} manually, activities and fragments should
 * use this class to get location updates from a shared source.
 * <p>
 * Under the hood, this class uses the system-wide fused location provider if one is available,
 * and falls back to GPS provider otherwise. On Android 12 and newer, a fused provider should
 * always be available, even on devices without Google Play Services. On older versions, a
 * fused location provider is usually present as well, as it is internally used for location requests
 * using the (deprecated) location criteria API. {@link LocationManagerCompat} will use this provider
 * through reflection if it is present. If all attempts fail, GPS provider is used as a last resort.
 * <p>
 * AOSP APIs are used throughout, so this class works on devices without Google Play Services
 * and does not have any proprietary dependencies.
 */
@SuppressLint("MissingPermission")
public class GlobalLocationObserver implements LocationListenerCompat {

    private static final String TAG = "LocationObserver";

    private final Context context;
    private final LocationManager locationManager;
    private final int minTimeMs;
    private final int minDistanceM;

    private final MutableLiveData<Location> lastLocation = new MutableLiveData<>();

    private boolean awake = false;
    private boolean warmingUp = false;
    private CancellationSignal warmupCancellationSignal;

    /**
     * Constructor.
     *
     * @param context      Context
     * @param minTimeMs    Minimum time interval between location updates in milliseconds
     * @param minDistanceM Minimum distance between location updates in meters
     */
    public GlobalLocationObserver(Context context, int minTimeMs, int minDistanceM) {
        this.context = context;
        this.locationManager = context.getSystemService(LocationManager.class);
        this.minTimeMs = minTimeMs;
        this.minDistanceM = minDistanceM;
    }

    @SuppressLint("InlinedApi")
    private String getPreferredLocationProvider() {
        if (LocationManagerCompat.hasProvider(locationManager, LocationManager.FUSED_PROVIDER)) {
            return LocationManager.FUSED_PROVIDER;
        }
        return LocationManager.GPS_PROVIDER;
    }

    /**
     * Begin listening for location updates. The method will fail silently
     * if location permissions are not allowed - the caller must check
     * permissions on its own before calling this method.
     */
    @UiThread
    public void wake() {
        wake(false);
    }

    private void wake(boolean warmUp) {
        if (awake) {
            return;
        }
        if (warmUp && warmingUp) {
            return;
        }
        if (!PermissionRequestHelper.hasLocationPermission(context)) {
            return;
        }
        try {
            if (warmUp) {
                Log.d(TAG, "Warming up location observer...");
            } else {
                Log.d(TAG, "Starting location updates...");
            }
            String provider = getPreferredLocationProvider();

            setLastKnownIfCurrent(Duration.ofMillis(minTimeMs));

            Location location = lastLocation.getValue();
            if (!warmingUp) {
                if (location == null || Instant.ofEpochMilli(location.getTime()).isBefore(Instant.now().minusMillis(minTimeMs))) {
                    String type = warmUp ? "Warm-up" : location == null ? "Initial" : "Immediate";
                    Log.d(TAG, "Requesting " + type.toLowerCase() + " location...");
                    if (warmUp) {
                        warmingUp = true;
                    }
                    CancellationSignal cancellationSignal = null;
                    if (warmUp) {
                        warmupCancellationSignal = new CancellationSignal();
                        cancellationSignal = warmupCancellationSignal;
                    }
                    LocationManagerCompat.getCurrentLocation(locationManager, provider, cancellationSignal, ContextCompat.getMainExecutor(context), l -> {
                        if (warmUp) {
                            warmingUp = false;
                        }
                        if (l != null) {
                            Log.d(TAG, type + " location obtained: " + l);
                            onLocationChanged(l);
                        } else {
                            Log.d(TAG, type + " location request returned null location - location services may be disabled.");
                        }
                    });
                }
            } else {
                Log.d(TAG, "Awakening, canceling warm-up.");
                cancelWarmup();
            }

            if (!warmUp) {
                awake = true;
                warmingUp = false;
                LocationManagerCompat.requestLocationUpdates(locationManager, provider, createLocationRequest(), this, Looper.getMainLooper());
            }
        } catch (SecurityException ignored) {

        }
    }

    private LocationRequestCompat createLocationRequest() {
        return new LocationRequestCompat.Builder(minTimeMs)
                .setMinUpdateDistanceMeters(minDistanceM)
                .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY)
                .build();
    }

    public boolean isUpToDate(Duration maxAge) {
        return isLocationUpToDate(lastLocation.getValue(), maxAge);
    }

    private boolean isLocationUpToDate(Location location, Duration maxAge) {
        if (location == null) {
            return false;
        }
        Instant ttlEndTime = Instant.ofEpochMilli(location.getTime() + maxAge.toMillis());
        return Instant.now().isBefore(ttlEndTime);
    }

    public LiveData<Location> requestSingleUpdate() {
        LiveData<Location> result = LiveDataUtils.sinceNextValue(getLastLocation());
        if (canUseLocation()) {
            Log.d(TAG, "Requesting single location update...");
            LocationManagerCompat.getCurrentLocation(locationManager, getPreferredLocationProvider(), (CancellationSignal) null, ContextCompat.getMainExecutor(context), l -> {
                if (l != null) {
                    Log.d(TAG, "Single location update obtained: " + l);
                    onLocationChanged(l);
                } else {
                    Log.d(TAG, "Single location request returned null location - location services may be disabled or app is in background.");
                }
            });
        }
        return result;
    }

    @RequiresPermission(anyOf = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})
    private boolean setLastKnownIfCurrent(Duration maxAge) {
        Location fromProvider = locationManager.getLastKnownLocation(getPreferredLocationProvider());
        if (isLocationUpToDate(fromProvider, maxAge)) {
            onLocationChanged(fromProvider);
            return true;
        } else {
            return false;
        }
    }

    public LiveData<Location> requestRecentLocation(Duration maxAge) {
        if (!canUseLocation()) {
            return getLastLocation();
        }
        if (isUpToDate(maxAge) || setLastKnownIfCurrent(maxAge)) {
            return getLastLocation();
        } else {
            return requestSingleUpdate();
        }
    }

    /**
     * Stop listening for further location updates.
     */
    @UiThread
    public void sleep() {
        if (!awake) {
            return;
        }
        Log.d(TAG, "Stopping location updates.");
        try {
            LocationManagerCompat.removeUpdates(locationManager, this);
            awake = false;
        } catch (SecurityException ignored) {

        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        Location current = lastLocation.getValue();
        if (current == null || location.getTime() >= current.getTime()) {
            Log.d(TAG, "Location updated: " + location);
            lastLocation.setValue(location);
        } else {
            Log.d(TAG, "Discarding out-of-date location: " + location);
        }
    }

    /**
     * Get the LiveData that contains the last known location.
     * Initially, this is the {@link LocationManager#getLastKnownLocation(String)}
     * until a more precise location is received. It is then refreshed with each
     * observed location update.
     *
     * @return The LiveData
     */
    public LiveData<Location> getLastLocation() {
        return lastLocation;
    }

    /**
     * Warms up the location observer in the background.
     * This should be called when the app is started so that location is available
     * immediately when the user actually gets to it. Once the first update is received,
     * {@link #sleep()} is called automatically (unless {@link #wake()} is called explicitly
     * by the parent context).
     */
    @UiThread
    public void warmup() {
        wake(true);
    }

    /**
     * Cancel an ongoing warmup previously requested by {@link #warmup()}.
     */
    public void cancelWarmup() {
        if (warmupCancellationSignal != null) {
            warmupCancellationSignal.cancel();
            warmingUp = false;
        }
    }

    public boolean isLocationEnabled() {
        return LocationManagerCompat.isLocationEnabled(locationManager);
    }

    public boolean canUseLocation() {
        return PermissionRequestHelper.hasLocationPermission(context) && isLocationEnabled();
    }
}
