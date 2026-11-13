package com.alumni.searcher.phantombuster;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "phantombuster")
public record PhantomBusterProperties(
        String baseUrl,
        String apiKey,
        String agentId,
        int profilesPerSearch,
        Duration pollInterval,
        int maxPollAttempts,
        @DefaultValue("linkedInSearchUrl") String searchArgument,
        @DefaultValue("numberOfResultsPerSearch") String profileCountArgument
) {
}
