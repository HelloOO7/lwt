package cz.spojenka.lwt.cicomock.services;

import cz.spojenka.datasources.model.util.OfflineTimetableBundle;
import jakarta.annotation.PostConstruct;
import org.apache.commons.io.input.BoundedInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Set;

@Service
public class TimetableDataService {

    private final Logger logger = LoggerFactory.getLogger(TimetableDataService.class);

    private static final Set<String> NEEDED_FILES = Set.of(
            OfflineTimetableBundle.FILE_DB,
            OfflineTimetableBundle.FILE_CPPDB
    );

    @Value("${mock.data.timetable-bundle}")
    private String timetableBundlePath;

    private final IndexConfig indexes;

    private final Path extractRootPath;

    @Autowired
    public TimetableDataService(IndexConfig indexes) throws IOException {
        this.indexes = indexes;
        extractRootPath = indexes.getCustomIndexDir("ttdata");
    }

    @PostConstruct
    private void ensureIndexesReady() throws IOException {
        Path bundlePath = Path.of(timetableBundlePath);
        if (indexes.isIndexUpdateNeeded(getTimetableDBPath(), bundlePath)) {
            logger.info("Timetable bundle is newer than extracted data, reading...");
            FileTime bundleModTime = Files.getLastModifiedTime(bundlePath);
            OfflineTimetableBundle.readTTZ(bundlePath, (OfflineTimetableBundle.TtzVisitor) (name, in, size) -> {
                if (!NEEDED_FILES.contains(name)) {
                    return false;
                }

                var boundedStream = BoundedInputStream.builder()
                                .setInputStream(in)
                                .setPropagateClose(false)
                                .setMaxCount(size)
                                .get();

                logger.info("Extracting {} from bundle", name);
                Path destFile = extractRootPath.resolve(name);
                try (FileOutputStream out = new FileOutputStream(destFile.toFile())) {
                    boundedStream.transferTo(out);
                }
                // set last modified time to be that of the bundle, to account for time shifts between
                // the data source and the server machine
                Files.setLastModifiedTime(destFile, bundleModTime);
                return true;
            });
            logger.info("Timetable bundle extracted");
        }
    }

    public Path getTimetableDBPath() {
        return extractRootPath.resolve(OfflineTimetableBundle.FILE_DB);
    }

    public Path getEngineDatabasePath() {
        return extractRootPath.resolve(OfflineTimetableBundle.FILE_CPPDB);
    }

    public Path getCustomIndexDir(String name) throws IOException {
        return indexes.getCustomIndexDir(name);
    }
}
