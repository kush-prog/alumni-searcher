package com.alumni.searcher;

import com.alumni.searcher.config.RequiredSettingsCheck;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AlumniSearcherApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(AlumniSearcherApplication.class);
        application.addListeners(new RequiredSettingsCheck());
        application.run(args);
    }
}
