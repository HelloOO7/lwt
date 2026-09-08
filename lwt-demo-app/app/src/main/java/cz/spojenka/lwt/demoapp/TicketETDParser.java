package cz.spojenka.lwt.demoapp;

import java.time.OffsetDateTime;
import java.util.List;

import cz.dpp.praguepublictransport.LitackaUtils;
import cz.dpp.praguepublictransport.etd.ETDUtils;
import cz.dpp.praguepublictransport.etd.LitackaETD;
import cz.spojenka.lwt.LwtTicketMetadata;

public class TicketETDParser {

    private final LitackaETD etd;

    public TicketETDParser(LitackaETD etd) {
        this.etd = etd;
    }

    public TicketETDParser(String etdString) {
        this(LitackaETD.parse(etdString));
    }

    public String getIssuerName() {
        return etd.getProperty("IN");
    }

    private OffsetDateTime getDateTime(String propertyName) {
        String value = etd.getProperty(propertyName);
        if (value == null) {
            return null;
        }
        return OffsetDateTime.parse(value);
    }

    public OffsetDateTime getValidSince() {
        return getDateTime("VS");
    }

    public OffsetDateTime getValidUntil() {
        return getDateTime("VU");
    }

    public List<String> getValidZones() {
        return LitackaUtils.parseCommaSeparatedList(etd.getProperty("VZ"));
    }

    public String getLwtMetadata() {
        return etd.getProperty("X-LWT");
    }

    public LwtTicketMetadata parseLwtMetadata() {
        String metadata = getLwtMetadata();
        return LwtTicketMetadata.parse(metadata != null ? metadata : "");
    }

    public String getCicoSessionId() {
        return etd.getProperty("X-SID");
    }

    public byte[] getSignature() {
        if (etd.getProperty(ETDUtils.SIGNATURE_PROPERTY) == null) {
            return null;
        }
        return ETDUtils.getDecodedTicketSignature(etd);
    }

    public String getTOTP() {
        return etd.getProperty("X-TOTP");
    }
}
