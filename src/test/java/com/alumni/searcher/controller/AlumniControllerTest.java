package com.alumni.searcher.controller;

import com.alumni.searcher.dto.AlumniProfileResponse;
import com.alumni.searcher.dto.AlumniSearchRequest;
import com.alumni.searcher.exception.PhantomBusterException;
import com.alumni.searcher.exception.ScrapingTimeoutException;
import com.alumni.searcher.service.AlumniService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AlumniController.class)
class AlumniControllerTest {

    private static final String SEARCH_URL = "/api/alumni/search";
    private static final String ALL_URL = "/api/alumni/all";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AlumniService alumniService;

    @Test
    void returnsFoundAlumniForAValidSearch() throws Exception {
        when(alumniService.searchAlumni(any())).thenReturn(List.of(
                new AlumniProfileResponse("John Doe", "Software Engineer", "University of XYZ",
                        "New York, NY", "Passionate Software Engineer at XYZ Corp", 2020,
                        "https://www.linkedin.com/in/johndoe")));

        mockMvc.perform(post(SEARCH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"university": "University of XYZ", "designation": "Software Engineer", "passoutYear": "2020"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data[0].name").value("John Doe"))
                .andExpect(jsonPath("$.data[0].currentRole").value("Software Engineer"))
                .andExpect(jsonPath("$.data[0].university").value("University of XYZ"))
                .andExpect(jsonPath("$.data[0].location").value("New York, NY"))
                .andExpect(jsonPath("$.data[0].linkedinHeadline").value("Passionate Software Engineer at XYZ Corp"))
                .andExpect(jsonPath("$.data[0].passoutYear").value(2020));

        verify(alumniService).searchAlumni(new AlumniSearchRequest("University of XYZ", "Software Engineer", 2020));
    }

    @Test
    void passoutYearIsOptional() throws Exception {
        when(alumniService.searchAlumni(any())).thenReturn(List.of());

        mockMvc.perform(post(SEARCH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"university": "University of XYZ", "designation": "Data Scientist"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void rejectsMissingUniversityAndDesignation() throws Exception {
        mockMvc.perform(post(SEARCH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"university": " ", "passoutYear": 2020}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.errors.university").value("University name is required"))
                .andExpect(jsonPath("$.errors.designation").value("Designation is required"));

        verify(alumniService, never()).searchAlumni(any());
    }

    @Test
    void rejectsUnrealisticPassoutYear() throws Exception {
        mockMvc.perform(post(SEARCH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"university": "University of XYZ", "designation": "Engineer", "passoutYear": 1800}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.passoutYear").value("Pass-out year must be 1950 or later"));
    }

    @Test
    void rejectsPassoutYearThatIsNotANumber() throws Exception {
        mockMvc.perform(post(SEARCH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"university": "University of XYZ", "designation": "Engineer", "passoutYear": "last year"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void rejectsBrokenJson() throws Exception {
        mockMvc.perform(post(SEARCH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"university\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void rejectsNonJsonContent() throws Exception {
        mockMvc.perform(post(SEARCH_URL)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("university=XYZ"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void rejectsWrongHttpMethod() throws Exception {
        mockMvc.perform(get(SEARCH_URL))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void reportsBadGatewayWhenPhantomBusterFails() throws Exception {
        when(alumniService.searchAlumni(any()))
                .thenThrow(new PhantomBusterException("PhantomBuster rejected the API key while trying to start the search."));

        mockMvc.perform(validSearch())
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("PhantomBuster rejected the API key while trying to start the search."));
    }

    @Test
    void reportsGatewayTimeoutWhenSearchTakesTooLong() throws Exception {
        when(alumniService.searchAlumni(any())).thenThrow(new ScrapingTimeoutException("Taking too long"));

        mockMvc.perform(validSearch())
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.message").value("Taking too long"));
    }

    @Test
    void reportsServiceUnavailableWhenDatabaseIsDown() throws Exception {
        when(alumniService.searchAlumni(any())).thenThrow(new DataAccessResourceFailureException("connection refused"));

        mockMvc.perform(validSearch())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void hidesDetailsOfUnexpectedErrors() throws Exception {
        when(alumniService.searchAlumni(any())).thenThrow(new IllegalStateException("secret internal detail"));

        mockMvc.perform(validSearch())
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Something went wrong on our side. Please try again later."));
    }

    @Test
    void reportsConflictWhenTheSameProfileIsSavedTwiceAtOnce() throws Exception {
        when(alumniService.searchAlumni(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        mockMvc.perform(validSearch())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value(
                        "Another search saved the same profiles at the same moment. Please try again."));
    }

    @Test
    void returnsAllSavedAlumni() throws Exception {
        when(alumniService.getAllAlumni()).thenReturn(List.of(
                new AlumniProfileResponse("John Doe", "Software Engineer", "University of XYZ",
                        "New York, NY", "Passionate Software Engineer at XYZ Corp", 2020,
                        "https://www.linkedin.com/in/johndoe"),
                new AlumniProfileResponse("Jane Smith", "Data Scientist", "University of XYZ",
                        "San Francisco, CA", "Data Scientist | AI Enthusiast", 2019,
                        "https://www.linkedin.com/in/janesmith")));

        mockMvc.perform(get(ALL_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].name").value("John Doe"))
                .andExpect(jsonPath("$.data[1].name").value("Jane Smith"))
                .andExpect(jsonPath("$.data[1].currentRole").value("Data Scientist"))
                .andExpect(jsonPath("$.data[1].passoutYear").value(2019));
    }

    @Test
    void returnsEmptyListWhenNothingIsSavedYet() throws Exception {
        when(alumniService.getAllAlumni()).thenReturn(List.of());

        mockMvc.perform(get(ALL_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void reportsServiceUnavailableWhenDatabaseIsDownWhileListing() throws Exception {
        when(alumniService.getAllAlumni()).thenThrow(new DataAccessResourceFailureException("connection refused"));

        mockMvc.perform(get(ALL_URL))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void reportsServiceUnavailableWhenNoDatabaseConnectionCanBeOpened() throws Exception {
        when(alumniService.getAllAlumni())
                .thenThrow(new CannotCreateTransactionException("Could not open JPA EntityManager for transaction"));

        mockMvc.perform(get(ALL_URL))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value(
                        "Could not save or read alumni data right now. Please try again later."));
    }

    @Test
    void rejectsWrongHttpMethodOnListUrl() throws Exception {
        mockMvc.perform(post(ALL_URL))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void reportsNotFoundForUnknownUrl() throws Exception {
        mockMvc.perform(get("/api/alumni/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("No API exists at this URL."));
    }

    private org.springframework.test.web.servlet.RequestBuilder validSearch() {
        return post(SEARCH_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"university": "University of XYZ", "designation": "Software Engineer"}
                        """);
    }
}
