package cz.spojenka.lwt.ticketingserver.services;

import cz.spojenka.datasources.model.timetable.TimetableDatabase;
import cz.spojenka.datasources.persistentids.JniConnectionInServiceChecker;
import cz.spojenka.datasources.persistentids.JniNamedStopIDMatcher;
import cz.spojenka.datasources.persistentids.PersistentIDService;
import cz.spojenka.engine.jni.SearchLibrary;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PersistentIDConfiguration {

    @Bean
    public PersistentIDService persistentIDService(TimetableDatabase tt, SearchLibrary jni) {
        return new PersistentIDService(
                tt,
                new PersistentIDService.Services()
                        .setConnectionInServiceChecker(new JniConnectionInServiceChecker(jni))
        );
    }
}
