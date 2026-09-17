package cz.spojenka.lwt.cicomock.services;

import cz.spojenka.datasources.model.timetable.TimetableDatabase;
import cz.spojenka.engine.jni.SearchEnums;
import cz.spojenka.engine.jni.SearchLibrary;
import cz.spojenka.lwt.cicomock.model.Location;
import cz.spojenka.lwt.cicomock.model.MockState;
import cz.spojenka.lwt.cicomock.model.NearbyDevice;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class DeviceScanThread extends Thread implements MockState.Observer {

    private final Logger logger = LoggerFactory.getLogger(DeviceScanThread.class);

    @Value("${mock.scan.result-interval}")
    private long resultInterval;
    @Value("${mock.scan.radius}")
    private float scanRadius;

    private final MockState state;
    private final SearchLibrary engine;
    private final TimetableDatabase db;
    private final AdvDataConverter advDataConverter;
    private boolean shouldEnd = false;

    public DeviceScanThread(MockState state, SearchLibrary engine, TimetableDatabase db, AdvDataConverter advDataConverter) {
        this.state = state;
        this.engine = engine;
        this.db = db;
        this.advDataConverter = advDataConverter;
    }

    @Override
    public void run() {
        state.addObserver(this);
        try {
            while (true) {
                try {
                    scanAndReportResults();
                } catch (IOException e) {
                    logger.error("Error occurred while scanning devices", e);
                    // continue scan until stopped
                }
                state.getTimekeeper().sleepCurrentThread(Duration.ofMillis(resultInterval), () -> shouldEnd);
                if (shouldEnd) {
                    break;
                }
            }
        } catch (Exception ex) {
            logger.error("Device scan thread encountered unexpected error", ex);
        } finally {
            state.removeObserver(this);
        }
        synchronized (this) {
            notifyAll();
        }
        logger.info("Device scan thread ended");
    }

    @Override
    public void onTimescaleChanged(float timescale) {
        interrupt();
    }

    @PreDestroy
    public void endAllScansAndWait() throws InterruptedException {
        synchronized (this) {
            if (!isAlive()) {
                return;
            }
            endScan();
            wait();
        }
    }

    public void endScan() {
        shouldEnd = true;
        interrupt();
    }

    private void scanAndReportResults() throws IOException {
        Location location = state.getLocation();
        ZonedDateTime time = state.getCurrentTime();

        var searchResults = engine.searchVehiclesInRadius(location.lat(), location.lon(), scanRadius, time, SearchEnums.VSP_TRAJECTORY, null, Integer.MAX_VALUE, 0);

        List<NearbyDevice> convResults = new ArrayList<>();

        for (var searchResult : searchResults) {
            convResults.add(new NearbyDevice(
                    AdvDataConverter.makeFakeAddressForConnId(searchResult.connectionId),
                    searchResult.connectionId,
                    AdvDataConverter.getLineName(db.getConnectionById(searchResult.connectionId), searchResult.linearPosition),
                    new Location(searchResult.lat, searchResult.lon),
                    searchResult.linearPosition,
                    advDataConverter.makeTripInformation(searchResult)
            ));
        }

        state.setNearbyDevices(convResults);
    }
}
