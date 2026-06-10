package cz.spojenka.lwdn;

import java.io.IOException;
import java.time.Duration;

public class SocketWatchdog extends Thread {

    private final LwdnSocket socket;
    private final Duration timeout;
    private boolean stopped = false;

    public SocketWatchdog(LwdnSocket socket, Duration timeout) {
        this.socket = socket;
        this.timeout = timeout;
        setDaemon(true);
    }

    public void resetWatchdog() {
        interrupt();
    }

    public void stopWatchdog() {
        stopped = true;
        interrupt();
    }

    @Override
    public void run() {
        while (true) {
            if (stopped || !socket.isOpen()) {
                break;
            }
            try {
                Thread.sleep(timeout.toMillis());
                if (socket.isOpen()) {
                    socket.close();
                }
            } catch (InterruptedException e) {
                continue;
            } catch (IOException e) {
                break;
            }
        }
    }
}
