package com.alumni.searcher.phantombuster;

import com.alumni.searcher.exception.PhantomBusterException;
import com.alumni.searcher.exception.ScrapingTimeoutException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

// Runs a LinkedIn search through PhantomBuster in three steps:
// start the phantom, wait for it to finish, then read its results.

@Component
public class PhantomBusterClient {

    private static final Logger log = LoggerFactory.getLogger(PhantomBusterClient.class);
    private static final String API_KEY_HEADER = "X-Phantombuster-Key";
    private static final String LINKEDIN_PEOPLE_SEARCH_URL = "https://www.linkedin.com/search/results/people/?keywords=";
    private static final TypeReference<List<Map<String, Object>>> PROFILE_LIST = new TypeReference<>() {
    };

    private final RestClient restClient;
    private final JsonMapper jsonMapper;
    private final PhantomBusterProperties properties;

    public PhantomBusterClient(RestClient.Builder restClientBuilder,
                               JsonMapper jsonMapper,
                               PhantomBusterProperties properties) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .defaultHeader(API_KEY_HEADER, Objects.requireNonNullElse(properties.apiKey(), ""))
                .build();
    }

    public List<ScrapedProfile> searchProfiles(String searchQuery) {
        checkConfigured();

        String containerId = launchSearch(searchQuery);
        log.info("PhantomBuster search started, container {} for query: {}", containerId, searchQuery);

        waitUntilFinished(containerId);
        List<ScrapedProfile> profiles = fetchProfiles(containerId);

        log.info("PhantomBuster container {} returned {} profiles", containerId, profiles.size());
        return profiles;
    }

    private void checkConfigured() {
        if (isBlank(properties.apiKey()) || isBlank(properties.agentId())) {
            throw new PhantomBusterException(
                    "PhantomBuster is not configured. Please set the API key and agent id.");
        }
    }

    private String launchSearch(String searchQuery) {
        Map<String, Object> body = Map.of(
                "id", properties.agentId(),
                "bonusArgument", Map.of(
                        properties.searchArgument(), toLinkedinPeopleSearchUrl(searchQuery),
                        properties.profileCountArgument(), properties.profilesPerSearch()
                )
        );

        LaunchResponse response = call("start the search", () -> restClient.post()
                .uri("/agents/launch")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(LaunchResponse.class));

        if (response == null || isBlank(response.containerId())) {
            throw new PhantomBusterException("PhantomBuster did not return a job id after starting the search.");
        }
        return response.containerId();
    }

    // The LinkedIn Search Export phantom takes a LinkedIn search URL, so the keywords
    // are turned into a people search, the same URL LinkedIn itself builds.
    static String toLinkedinPeopleSearchUrl(String keywords) {
        String encoded = URLEncoder.encode(keywords, StandardCharsets.UTF_8).replace("+", "%20");
        return LINKEDIN_PEOPLE_SEARCH_URL + encoded;
    }

    private void waitUntilFinished(String containerId) {
        for (int attempt = 1; attempt <= properties.maxPollAttempts(); attempt++) {
            pause();

            ContainerStatus container = call("check the search progress", () -> restClient.get()
                    .uri("/containers/fetch?id={id}", containerId)
                    .retrieve()
                    .body(ContainerStatus.class));

            if (container == null) {
                continue;
            }
            if ("launch error".equals(container.status())) {
                throw new PhantomBusterException("PhantomBuster could not start the search job.");
            }
            if ("finished".equals(container.status())) {
                if (container.exitCode() != null && container.exitCode() != 0) {
                    throw new PhantomBusterException(
                            "The PhantomBuster search job failed (exit code " + container.exitCode() + ").");
                }
                return;
            }
        }

        throw new ScrapingTimeoutException(
                "The LinkedIn search is taking longer than expected. Please try again in a few minutes.");
    }

    private List<ScrapedProfile> fetchProfiles(String containerId) {
        ResultObjectResponse response = call("read the search results", () -> restClient.get()
                .uri("/containers/fetch-result-object?id={id}", containerId)
                .retrieve()
                .body(ResultObjectResponse.class));

        if (response == null || isBlank(response.resultObject())) {
            return List.of();
        }

        try {
            return jsonMapper.readValue(response.resultObject(), PROFILE_LIST).stream()
                    .map(ScrapedProfile::fromRawFields)
                    .toList();
        } catch (JacksonException e) {
            throw new PhantomBusterException("PhantomBuster returned results in an unexpected format.", e);
        }
    }

    private <T> T call(String action, Supplier<T> request) {
        try {
            return request.get();
        } catch (HttpStatusCodeException e) {
            int status = e.getStatusCode().value();
            if (status == 401 || status == 403) {
                throw new PhantomBusterException("PhantomBuster rejected the API key while trying to " + action + ".", e);
            }
            throw new PhantomBusterException(
                    "PhantomBuster responded with status " + status + " while trying to " + action + ".", e);
        } catch (RestClientException e) {
            throw new PhantomBusterException("Could not reach PhantomBuster to " + action + ".", e);
        }
    }

    private void pause() {
        try {
            Thread.sleep(properties.pollInterval());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PhantomBusterException("The search was interrupted while waiting for PhantomBuster.", e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LaunchResponse(String containerId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ContainerStatus(String status, Integer exitCode) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ResultObjectResponse(String resultObject) {
    }
}
