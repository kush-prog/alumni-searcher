package com.alumni.searcher;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AlumniSearcherApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlumniSearcherApplication.class, args);
    }
}
