package cz.spojenka.lwt.cicomock.model;

import cz.spojenka.lwt.cicomock.services.Timekeeper;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

@Component
public class MockState {

    private AtomicReference<Location> location = new AtomicReference<>(new Location(50.0841331f, 14.4347067f));
    private final Timekeeper timekeeper = new Timekeeper();
    private AtomicReference<List<NearbyDevice>> nearbyDevices = new AtomicReference<>(new ArrayList<>());
    private AtomicInteger trackingConnectionId = new AtomicInteger();
    private AtomicInteger presenceTrackingConnectionId = new AtomicInteger();

    private final List<Observer>  observers = new ArrayList<>();

    public MockState() {
    }

    public void addObserver(Observer observer) {
        observers.add(observer);
    }

    public void removeObserver(Observer observer) {
        observers.remove(observer);
    }

    private <T> void invokeObserversNoDeadlock(BiConsumer<Observer, T> invoker, T arg) {
        List<Observer> observersCopy;
        synchronized (this) {
            observersCopy = List.copyOf(observers);
        }
        for (Observer observer : observersCopy) {
            invoker.accept(observer, arg);
        }
    }

    public void setTrackingConnectionId(int trackingConnectionId) {
        if (this.trackingConnectionId.getAndSet(trackingConnectionId) != trackingConnectionId) {
            updateLocationByTrackingConnection(trackingConnectionId);
        }
    }

    public int getTrackingConnectionId() {
        return trackingConnectionId.get();
    }

    public void setPresenceTrackingConnectionId(int presenceTrackingConnectionId) {
        if (this.presenceTrackingConnectionId.getAndSet(presenceTrackingConnectionId) != presenceTrackingConnectionId) {
            invokeObserversNoDeadlock(Observer::onPresenceTrackingChanged, presenceTrackingConnectionId);
        }
    }

    public NearbyDevice findNearbyDeviceByAddress(byte[] deviceAddress) {
        for (NearbyDevice dev : getNearbyDevices()) {
            if (Arrays.equals(dev.address(), deviceAddress)) {
                return dev;
            }
        }
        return null;
    }

    public void setPresenceTrackingDevice(byte[] deviceAddress) {
        NearbyDevice dev = findNearbyDeviceByAddress(deviceAddress);
        if (dev != null) {
            setPresenceTrackingConnectionId(dev.connectionId());
        } else {
            setPresenceTrackingConnectionId(0);
        }
    }

    public int getPresenceTrackingConnectionId() {
        return presenceTrackingConnectionId.get();
    }

    public Location getLocation() {
        return location.get();
    }

    public void updateLocation(Location newLocation) {
        if (this.location.getAndSet(newLocation).equals(newLocation)) {
            return;
        }
        invokeObserversNoDeadlock(Observer::onLocationChanged, newLocation);
    }

    public Timekeeper getTimekeeper() {
        return timekeeper;
    }

    public ZonedDateTime getCurrentTime() {
        return timekeeper.getCurrentTime();
    }

    public void setCurrentTime(ZonedDateTime newTime) {
        timekeeper.setCurrentTime(newTime);
    }

    public float getTimescale() {
        return timekeeper.getTimescale();
    }

    public void setTimescale(float timescale) {
        timekeeper.setTimescale(timescale);
        invokeObserversNoDeadlock(Observer::onTimescaleChanged, timescale);
    }

    public List<NearbyDevice> getNearbyDevices() {
        return nearbyDevices.get();
    }

    public void setNearbyDevices(List<NearbyDevice> nearbyDevices) {
        if (this.nearbyDevices.getAndSet(nearbyDevices).equals(nearbyDevices)) {
            return;
        }
        updateLocationByTrackingConnection(getTrackingConnectionId());
        invokeObserversNoDeadlock(Observer::onNearbyDevicesChanged, nearbyDevices);
    }

    private void updateLocationByTrackingConnection(int trackingConnectionId) {
        for (NearbyDevice dev : getNearbyDevices()) {
            if (dev.connectionId() == trackingConnectionId) {
                updateLocation(dev.location());
                break;
            }
        }
    }

    public static interface Observer {

        public default void onTimescaleChanged(float timescale) {

        }

        public default void onLocationChanged(Location location) {

        }

        public default void onNearbyDevicesChanged(List<NearbyDevice> nearbyDevices) {

        }

        public default void onPresenceTrackingChanged(int presenceTrackingConnectionId) {

        }
    }
}
