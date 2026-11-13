package com.alumni.searcher.config;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stops the application at startup, before any connection is attempted, when a required
 * environment variable is missing, and names the variables to set.
 * Without this check the failure is a less helpful "'url' must start with jdbc".
 */
public class RequiredSettingsCheck implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    private static final Map<String, String> REQUIRED = new LinkedHashMap<>();

    static {
        REQUIRED.put("spring.datasource.url", "DB_URL");
        REQUIRED.put("spring.datasource.username", "DB_USERNAME");
        REQUIRED.put("spring.datasource.password", "DB_PASSWORD");
    }

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        verify(event.getEnvironment());
    }

    static void verify(Environment environment) {
        List<String> missing = new ArrayList<>();
        REQUIRED.forEach((property, variable) -> {
            if (!isSet(environment, property)) {
                missing.add(variable);
            }
        });
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Missing required environment variable(s): "
                    + String.join(", ", missing)
                    + ". Set them, or run with SPRING_PROFILES_ACTIVE=local for local development.");
        }
    }

    private static boolean isSet(Environment environment, String property) {
        try {
            String value = environment.getProperty(property);
            return value != null && !value.isBlank() && !value.contains("${");
        } catch (IllegalArgumentException unresolvedPlaceholder) {
            return false;
        }
    }
}
