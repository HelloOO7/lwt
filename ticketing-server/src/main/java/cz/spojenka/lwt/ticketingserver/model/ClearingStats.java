package cz.spojenka.lwt.ticketingserver.model;

public record ClearingStats(long eventsCleared, int sessionsCleared, int accountsAffected, double totalFareCollected) {

    public static ClearingStats empty() {
        return new ClearingStats(0, 0, 0, 0.0);
    }

    public ClearingStats add(ClearingStats other) {
        return new ClearingStats(
            this.eventsCleared + other.eventsCleared,
            this.sessionsCleared + other.sessionsCleared,
            this.accountsAffected + other.accountsAffected,
            this.totalFareCollected + other.totalFareCollected
        );
    }
}
