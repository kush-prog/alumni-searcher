package com.alumni.searcher.phantombuster;

import com.alumni.searcher.exception.PhantomBusterException;
import com.alumni.searcher.exception.ScrapingTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PhantomBusterClientTest {

    private static final String BASE_URL = "https://api.phantombuster.test/api/v2";
    private static final String LAUNCH_URL = BASE_URL + "/agents/launch";
    private static final String STATUS_URL = BASE_URL + "/containers/fetch?id=c-1";
    private static final String RESULT_URL = BASE_URL + "/containers/fetch-result-object?id=c-1";

    private MockRestServiceServer server;
    private PhantomBusterClient client;

    @BeforeEach
    void setUp() {
        client = clientWith(properties("test-key", "agent-42", "linkedInSearchUrl", "numberOfResultsPerSearch"));
    }

    @Test
    void usesConfiguredArgumentNamesForTheLaunch() {
        PhantomBusterClient custom = clientWith(properties("test-key", "agent-42", "searches", "numberOfProfiles"));
        server.expect(requestTo(LAUNCH_URL))
                .andExpect(jsonPath("$.bonusArgument.searches").value(
                        "https://www.linkedin.com/search/results/people/?keywords=query"))
                .andExpect(jsonPath("$.bonusArgument.numberOfProfiles").value(10))
                .andExpect(jsonPath("$.bonusArgument.linkedInSearchUrl").doesNotExist())
                .andRespond(json("{\"containerId\": \"c-1\"}"));
        server.expect(requestTo(STATUS_URL)).andRespond(json("{\"status\": \"finished\", \"exitCode\": 0}"));
        server.expect(requestTo(RESULT_URL)).andRespond(json("{\"resultObject\": \"[]\"}"));

        assertThat(custom.searchProfiles("query")).isEmpty();
        server.verify();
    }

    @Test
    void launchesWaitsAndReadsProfiles() {
        server.expect(requestTo(LAUNCH_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Phantombuster-Key", "test-key"))
                .andExpect(jsonPath("$.id").value("agent-42"))
                .andExpect(jsonPath("$.bonusArgument.linkedInSearchUrl").value(
                        "https://www.linkedin.com/search/results/people/?keywords=%22Engineer%22%20%22XYZ%22"))
                .andExpect(jsonPath("$.bonusArgument.numberOfResultsPerSearch").value(10))
                .andRespond(json("{\"containerId\": \"c-1\"}"));
        server.expect(requestTo(STATUS_URL)).andRespond(json("{\"status\": \"running\"}"));
        server.expect(requestTo(STATUS_URL)).andRespond(json("{\"status\": \"finished\", \"exitCode\": 0}"));
        server.expect(requestTo(RESULT_URL)).andRespond(json("""
                {"resultObject": "[{\\"fullName\\": \\"John Doe\\", \\"headline\\": \\"Engineer at XYZ\\", \\"location\\": \\"New York, NY\\", \\"profileUrl\\": \\"https://www.linkedin.com/in/johndoe\\"}, {\\"firstName\\": \\"Jane\\", \\"lastName\\": \\"Smith\\", \\"job\\": \\"Data Scientist\\", \\"linkedinProfileUrl\\": \\"https://www.linkedin.com/in/janesmith\\"}]"}
                """));

        List<ScrapedProfile> profiles = client.searchProfiles("\"Engineer\" \"XYZ\"");

        assertThat(profiles).containsExactly(
                new ScrapedProfile("John Doe", "Engineer at XYZ", null, "New York, NY", null,
                        "https://www.linkedin.com/in/johndoe"),
                new ScrapedProfile("Jane Smith", "Data Scientist", null, null, null,
                        "https://www.linkedin.com/in/janesmith"));
        server.verify();
    }

    @Test
    void returnsNoProfilesWhenResultIsEmpty() {
        expectLaunchAndFinish();
        server.expect(requestTo(RESULT_URL)).andRespond(json("{\"resultObject\": null}"));

        assertThat(client.searchProfiles("query")).isEmpty();
    }

    @Test
    void failsClearlyWhenApiKeyIsRejected() {
        server.expect(requestTo(LAUNCH_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.searchProfiles("query"))
                .isInstanceOf(PhantomBusterException.class)
                .hasMessageContaining("rejected the API key");
    }

    @Test
    void failsWhenPhantomBusterIsDown() {
        server.expect(requestTo(LAUNCH_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.searchProfiles("query"))
                .isInstanceOf(PhantomBusterException.class)
                .hasMessageContaining("status 503");
    }

    @Test
    void failsWhenLaunchGivesNoJobId() {
        server.expect(requestTo(LAUNCH_URL)).andRespond(json("{}"));

        assertThatThrownBy(() -> client.searchProfiles("query"))
                .isInstanceOf(PhantomBusterException.class)
                .hasMessageContaining("did not return a job id");
    }

    @Test
    void failsWhenJobEndsWithAnError() {
        server.expect(requestTo(LAUNCH_URL)).andRespond(json("{\"containerId\": \"c-1\"}"));
        server.expect(requestTo(STATUS_URL)).andRespond(json("{\"status\": \"finished\", \"exitCode\": 1}"));

        assertThatThrownBy(() -> client.searchProfiles("query"))
                .isInstanceOf(PhantomBusterException.class)
                .hasMessageContaining("exit code 1");
    }

    @Test
    void failsWhenJobCannotStart() {
        server.expect(requestTo(LAUNCH_URL)).andRespond(json("{\"containerId\": \"c-1\"}"));
        server.expect(requestTo(STATUS_URL)).andRespond(json("{\"status\": \"launch error\"}"));

        assertThatThrownBy(() -> client.searchProfiles("query"))
                .isInstanceOf(PhantomBusterException.class)
                .hasMessageContaining("could not start");
    }

    @Test
    void failsWhenJobDisappears() {
        server.expect(requestTo(LAUNCH_URL)).andRespond(json("{\"containerId\": \"c-1\"}"));
        server.expect(requestTo(STATUS_URL)).andRespond(withResourceNotFound());

        assertThatThrownBy(() -> client.searchProfiles("query"))
                .isInstanceOf(PhantomBusterException.class)
                .hasMessageContaining("status 404");
    }

    @Test
    void timesOutWhenJobKeepsRunning() {
        server.expect(requestTo(LAUNCH_URL)).andRespond(json("{\"containerId\": \"c-1\"}"));
        for (int i = 0; i < 3; i++) {
            server.expect(requestTo(STATUS_URL)).andRespond(json("{\"status\": \"running\"}"));
        }

        assertThatThrownBy(() -> client.searchProfiles("query"))
                .isInstanceOf(ScrapingTimeoutException.class);
        server.verify();
    }

    @Test
    void failsWhenResultIsNotAList() {
        expectLaunchAndFinish();
        server.expect(requestTo(RESULT_URL)).andRespond(json("{\"resultObject\": \"not json\"}"));

        assertThatThrownBy(() -> client.searchProfiles("query"))
                .isInstanceOf(PhantomBusterException.class)
                .hasMessageContaining("unexpected format");
    }

    @Test
    void refusesToRunWithoutConfiguration() {
        PhantomBusterClient unconfigured =
                clientWith(properties("", "", "linkedInSearchUrl", "numberOfResultsPerSearch"));

        assertThatThrownBy(() -> unconfigured.searchProfiles("query"))
                .isInstanceOf(PhantomBusterException.class)
                .hasMessageContaining("not configured");
    }

    private static PhantomBusterProperties properties(String apiKey, String agentId,
                                                      String searchArgument, String profileCountArgument) {
        return new PhantomBusterProperties(BASE_URL, apiKey, agentId, 10, Duration.ZERO, 3,
                searchArgument, profileCountArgument);
    }

    private PhantomBusterClient clientWith(PhantomBusterProperties properties) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new PhantomBusterClient(builder, JsonMapper.builder().build(), properties);
    }

    private void expectLaunchAndFinish() {
        server.expect(requestTo(LAUNCH_URL)).andRespond(json("{\"containerId\": \"c-1\"}"));
        server.expect(requestTo(STATUS_URL)).andRespond(json("{\"status\": \"finished\", \"exitCode\": 0}"));
    }

    private static org.springframework.test.web.client.ResponseCreator json(String body) {
        return withSuccess(body, MediaType.APPLICATION_JSON);
    }
}
