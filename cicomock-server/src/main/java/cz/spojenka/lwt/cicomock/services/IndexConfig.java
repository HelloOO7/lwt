package cz.spojenka.lwt.cicomock.services;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class IndexConfig {

    @Value("${mock.data.index-directory:}")
    private String workingDirectory;

    @PostConstruct
    private void initDirectories() throws IOException {
        Files.createDirectories(Path.of(workingDirectory));
    }

    public Path getCustomIndexDir(String dirName) throws IOException {
        Path path = Path.of(workingDirectory, dirName);
        if (!Files.exists(path)) {
            Files.createDirectory(path);
        }
        return path;
    }

    public boolean isIndexUpdateNeeded(Path indexPath, Path sourcePath) throws IOException {
        if (!Files.exists(indexPath)) {
            return true;
        }
        var indexMTime = Files.getLastModifiedTime(indexPath);
        var sourceMTime = Files.getLastModifiedTime(sourcePath);
        return sourceMTime.compareTo(indexMTime) > 0;
    }
}
