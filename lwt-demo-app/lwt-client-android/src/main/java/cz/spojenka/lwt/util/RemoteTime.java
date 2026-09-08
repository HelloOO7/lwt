package cz.spojenka.lwt.util;

import java.time.Duration;
import java.time.Instant;

public class RemoteTime {

    private final Duration offset;
    private final long offsetMillis;

    public RemoteTime(Instant local, Instant remote) {
        this.offset = Duration.between(local, remote);
        this.offsetMillis = offset.toMillis();
    }

    public RemoteTime(Instant local, Instant remote, Duration rtt) {
        this.offset = Duration.between(local.plus(rtt.dividedBy(2)), remote);
        this.offsetMillis = offset.toMillis();
    }

    public Instant remoteToLocal(Instant remote) {
        return remote.minus(offset);
    }

    public long remoteToLocal(long remoteMillis) {
        return remoteMillis - offsetMillis;
    }

    public Instant remoteToLocalInstant(long remoteMillis) {
        return Instant.ofEpochMilli(remoteToLocal(remoteMillis));
    }

    public Instant localToRemote(Instant local) {
        return local.plus(offset);
    }
}
