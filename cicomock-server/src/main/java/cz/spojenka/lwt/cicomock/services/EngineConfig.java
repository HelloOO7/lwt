package cz.spojenka.lwt.cicomock.services;

import cz.spojenka.engine.JSearchEngine;
import cz.spojenka.engine.jni.SearchLibrary;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.FileNotFoundException;
import java.nio.file.Path;

@Configuration
public class EngineConfig {

    @Value("${engine.natives.path}")
    public String nativePath;

    @PostConstruct
    private void startupJni() throws FileNotFoundException {
        Path[] dlls = JSearchEngine.findDlls(Path.of(nativePath));
        if (dlls.length == 0) {
            throw new FileNotFoundException("Could not find any valid native libraries in " + nativePath);
        }
        JSearchEngine.initJni(dlls);
    }

    public void testReady() {
        SearchLibrary.getSupportedDataVersion();
    }
}
