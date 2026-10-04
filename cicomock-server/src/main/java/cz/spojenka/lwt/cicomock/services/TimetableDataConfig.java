package cz.spojenka.lwt.cicomock.services;

import cz.spojenka.datasources.repository.spring.TTZPackageLocator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
public class TimetableDataConfig {

    @Value("${mock.data.timetable-bundle}")
    private String timetableBundlePath;

    public TimetableDataConfig() {
    }

    @Bean
    public TTZPackageLocator ttzPackageLocator() {
        return () -> Path.of(timetableBundlePath);
    }
}
