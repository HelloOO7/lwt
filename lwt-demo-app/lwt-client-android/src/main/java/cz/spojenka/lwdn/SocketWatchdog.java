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
            try {
                Thread.sleep(timeout.toMillis());
                if (socket.isOpen()) {
                    System.out.println("Closing socket due to watchdog timeout");
                    socket.close();
                }
            } catch (InterruptedException e) {
                continue;
            } catch (IOException e) {
                break;
            }
            if (stopped || !socket.isOpen()) {
                break;
            }
        }
    }
}
