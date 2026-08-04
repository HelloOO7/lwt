package cz.spojenka.lwt;

import java.time.LocalDateTime;
import java.util.List;

public class TicketActivationParams {

    private byte[] activationToken;
    private LocalDateTime activationTime;
    private boolean activateNowIfEarlier;
    private List<String> activationZones;
    private String activationAppId;
    private byte[] preauthorizationToken;

    private TicketActivationParams() {

    }

    private TicketActivationParams(TicketActivationParams copy) {
        this.activationToken = copy.activationToken;
        this.activationTime = copy.activationTime;
        this.activateNowIfEarlier = copy.activateNowIfEarlier;
        this.activationZones = copy.activationZones;
        this.activationAppId = copy.activationAppId;
        this.preauthorizationToken = copy.preauthorizationToken;
    }

    public byte[] getActivationToken() {
        return activationToken;
    }

    public LocalDateTime getActivationTime() {
        return activationTime;
    }

    public boolean isActivateNowIfEarlier() {
        return activateNowIfEarlier;
    }

    public List<String> getActivationZones() {
        return activationZones;
    }

    public String getActivationAppId() {
        return activationAppId;
    }

    public byte[] getPreauthorizationToken() {
        return preauthorizationToken;
    }

    public static class Builder {

        private final TicketActivationParams params = new TicketActivationParams();

        public Builder(byte[] activationToken, String activationAppId) {
            params.activationToken = activationToken;
            params.activationAppId = activationAppId;
        }

        public Builder setActivationToken(byte[] activationToken) {
            params.activationToken = activationToken;
            return this;
        }

        public Builder setActivationTime(LocalDateTime activationTime) {
            params.activationTime = activationTime;
            return this;
        }

        public Builder setActivateNowIfEarlier(boolean activateNowIfEarlier) {
            params.activateNowIfEarlier = activateNowIfEarlier;
            return this;
        }

        public Builder setActivationZones(List<String> activationZones) {
            params.activationZones = activationZones;
            return this;
        }

        public Builder setActivationAppId(String activationAppId) {
            params.activationAppId = activationAppId;
            return this;
        }

        public Builder setPreauthorizationToken(byte[] preauthorizationToken) {
            params.preauthorizationToken = preauthorizationToken;
            return this;
        }

        public TicketActivationParams build() {
            return new TicketActivationParams(params);
        }
    }
}
