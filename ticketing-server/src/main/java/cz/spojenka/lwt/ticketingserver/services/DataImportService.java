package cz.spojenka.lwt.ticketingserver.services;

import cz.spojenka.lwt.ticketingserver.model.ImportMetadata;
import cz.spojenka.lwt.ticketingserver.model.ImportMetadataRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import shaded.org.apache.commons.codec.digest.DigestUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Service
public class DataImportService {

    private final ImportMetadataRepository repository;

    public DataImportService(ImportMetadataRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void checkAndImport(Path source, String destination, ImportCallback importAction) throws IOException {
        byte[] checksum = computeFileChecksum(source);
        var latestMetadata = repository.getLatestMetadata(destination);
        if (latestMetadata != null) {
            if (Arrays.equals(checksum, latestMetadata.getDataChecksum())) {
                importAction.onNotModified(checksum, latestMetadata.getImportTime());
                return;
            }
        }
        Instant ts = Instant.now();
        importAction.onImport(source, checksum);
        ImportMetadata metadata = new ImportMetadata(source.toString(), destination, ts, checksum);
        repository.save(metadata);
    }

    private byte[] computeFileChecksum(Path file) throws IOException {
        return DigestUtils.digest(DigestUtils.getSha256Digest(), file.toFile());
    }

    public static interface ImportCallback {

        public void onImport(Path path, byte[] checksum);
        public void onNotModified(byte[] checksum, Instant lastModified);
    }
}
