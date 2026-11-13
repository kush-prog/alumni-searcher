package com.alumni.searcher;

import com.alumni.searcher.phantombuster.PhantomBusterClient;
import com.alumni.searcher.phantombuster.ScrapedProfile;
import com.alumni.searcher.repository.AlumniRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Starts the whole application with an in-memory database. Only PhantomBuster
 * is replaced, so this checks the real path: HTTP request, service, database, HTTP reply.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
class AlumniApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AlumniRepository alumniRepository;

    @MockitoBean
    private PhantomBusterClient phantomBusterClient;

    @BeforeEach
    void startWithEmptyTable() {
        alumniRepository.deleteAll();
    }

    @Test
    void searchedAlumniAreSavedAndListedAndNotDuplicatedOnRepeatSearch() throws Exception {
        when(phantomBusterClient.searchProfiles(any())).thenReturn(List.of(
                new ScrapedProfile("John Doe", "Passionate Software Engineer at XYZ Corp", "Software Engineer",
                        "New York, NY", "University of XYZ", "https://www.linkedin.com/in/johndoe"),
                new ScrapedProfile("Jane Smith", "Data Scientist | AI Enthusiast", null,
                        "San Francisco, CA", null, "https://www.linkedin.com/in/janesmith")));

        mockMvc.perform(get("/api/alumni/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        mockMvc.perform(search())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].name").value("John Doe"))
                .andExpect(jsonPath("$.data[0].passoutYear").value(2020));

        mockMvc.perform(search()).andExpect(status().isOk());

        mockMvc.perform(get("/api/alumni/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[?(@.name == 'Jane Smith')].currentRole").value("Software Engineer"))
                .andExpect(jsonPath("$.data[?(@.name == 'Jane Smith')].university").value("University of XYZ"));
    }

    private org.springframework.test.web.servlet.RequestBuilder search() {
        return post("/api/alumni/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"university": "University of XYZ", "designation": "Software Engineer", "passoutYear": "2020"}
                        """);
    }
}
