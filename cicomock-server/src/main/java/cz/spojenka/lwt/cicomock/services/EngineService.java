package cz.spojenka.lwt.cicomock.services;

import cz.spojenka.backend.services.impl.JniTimetableStorage;
import cz.spojenka.datasources.model.timetable.TimetableDatabase;
import cz.spojenka.datasources.model.util.CachingExternalTimetableStorage;
import cz.spojenka.engine.jni.SearchLibrary;
import cz.spojenka.engine.jni.autocomplete.AutocompleteLibrary;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class EngineService {
    private final TimetableDataService dataSources;

    public EngineService(EngineConfig engineCfg, TimetableDataService dataSources) {
        engineCfg.testReady();
        this.dataSources = dataSources;
    }

    @Bean
    public SearchLibrary library() throws IOException {
        return SearchLibrary.newInstance(
                dataSources.getEngineDatabasePath().toAbsolutePath().toString(),
                dataSources.getCustomIndexDir("engine_workdir").toAbsolutePath().toString()
        );
    }

    @Bean
    public TimetableDatabase getTimetableDB(SearchLibrary library) throws IOException {
        TimetableDatabase db = TimetableDatabase.readBlobFromFile(dataSources.getTimetableDBPath());
        db.attachExternalStorage(new CachingExternalTimetableStorage(new JniTimetableStorage(library), 100));
        return db;
    }
}
