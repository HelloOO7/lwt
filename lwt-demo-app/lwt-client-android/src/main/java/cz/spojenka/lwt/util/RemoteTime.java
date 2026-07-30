package cz.spojenka.lwt.util;

import java.time.Duration;
import java.time.Instant;

public class RemoteTime {

    private final Duration offset;

    public RemoteTime(Instant local, Instant remote) {
        this.offset = Duration.between(local, remote);
    }

    public RemoteTime(Instant local, Instant remote, Duration rtt) {
        this.offset = Duration.between(local.plus(rtt.dividedBy(2)), remote);
    }

    public Instant remoteToLocal(Instant remote) {
        return remote.minus(offset);
    }

    public Instant localToRemote(Instant local) {
        return local.plus(offset);
    }
}
