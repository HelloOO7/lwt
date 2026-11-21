package cz.spojenka.lwt.ticketingserver.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
public class ImportMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(length = 512)
    private String source;
    private String destination;

    private Instant importTime;
    private byte[] dataChecksum;

    ImportMetadata() {

    }

    public ImportMetadata(String source, String destination, Instant importTime, byte[] dataChecksum) {
        this.source = source;
        this.destination = destination;
        this.importTime = importTime;
        this.dataChecksum = dataChecksum;
    }

    public String getSource() {
        return source;
    }

    public String getDestination() {
        return destination;
    }

    public Instant getImportTime() {
        return importTime;
    }

    public byte[] getDataChecksum() {
        return dataChecksum;
    }
}
