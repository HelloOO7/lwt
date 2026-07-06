package cz.spojenka.lwt.util;

import android.os.Handler;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import cz.spojenka.lwdn.LwdnAddress;
import cz.spojenka.lwt.LwtDevice;
import cz.spojenka.lwt.LwtScan;

public class EagerScanChecker {

    private final Handler handler;
    private final LwtScan scan;

    private Set<LwdnAddress> addressesInLastSlice = new HashSet<>();

    public EagerScanChecker(Handler handler, LwtScan scan) {
        this.handler = handler;
        this.scan = scan;
    }

    public void startChecking(long sliceSize, Consumer<List<LwtDevice>> onEarlyResults) {
        createCheckRunnable(sliceSize, onEarlyResults).run();
    }

    private Runnable createCheckRunnable(long sliceSize, Consumer<List<LwtDevice>> onEarlyResults) {
        return () -> {
            var currentResults = scan.getResults();
            if (scan.isFinished()) {
                onEarlyResults.accept(currentResults);
            } else {
                Set<LwdnAddress> foundAddresses = new HashSet<>();
                for (LwtDevice device : currentResults) {
                    foundAddresses.add(device.getAddress());
                }
                if (currentResults.isEmpty() || !foundAddresses.equals(addressesInLastSlice)) {
                    addressesInLastSlice = foundAddresses;
                    handler.postDelayed(createCheckRunnable(sliceSize, onEarlyResults), sliceSize);
                } else {
                    onEarlyResults.accept(currentResults);
                }
            }
        };
    }
}
