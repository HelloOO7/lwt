package cz.spojenka.lwt.cicomock.services;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.function.Supplier;

public class Timekeeper {

    private ZonedDateTime lastSetTime = ZonedDateTime.now();
    private Instant lastSetInstant = Instant.now();
    private float timescale = 1.0f;

    public synchronized void setTimescale(float timescale) {
        ZonedDateTime time = getCurrentTime();
        this.timescale = timescale;
        setCurrentTime(time);
    }

    public synchronized float getTimescale() {
        return timescale;
    }

    public synchronized void setCurrentTime(ZonedDateTime newTime) {
        this.lastSetTime = newTime;
        this.lastSetInstant = Instant.now();
    }

    public synchronized ZonedDateTime getCurrentTime() {
        return lastSetTime.plus(translateRealToScaled(Duration.between(lastSetInstant, Instant.now())));
    }

    public synchronized Duration translateRealToScaled(Duration duration) {
        return translate(duration, timescale);
    }

    public synchronized Duration translateScaledToReal(Duration duration) {
        return translate(duration, 1f / timescale);
    }

    private static Duration translate(Duration duration, float timescale) {
        if (timescale == 0) {
            return Duration.ZERO;
        }
        long millis = duration.toMillis();
        long newMillis = (long) (millis * timescale);
        return Duration.ofMillis(newMillis);
    }

    public void sleepCurrentThread(Duration duration, Supplier<Boolean> shouldEnd) {
        long sleepStart = System.currentTimeMillis();
        while (true) {
            long sleepDuration = translateScaledToReal(duration).toMillis();
            long remaining = sleepDuration - (System.currentTimeMillis() - sleepStart);
            if (remaining > 0) {
                try {
                    Thread.sleep(remaining);
                } catch (InterruptedException e) {
                    if (shouldEnd.get()) {
                        break;
                    }
                }
            } else {
                break;
            }
        }
    }
}
