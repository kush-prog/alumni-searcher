package com.alumni.searcher.config;

import com.alumni.searcher.phantombuster.PhantomBusterClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
class OpenApiConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PhantomBusterClient phantomBusterClient;

    @Test
    void publishesApiDescriptionWithBothEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Alumni LinkedIn Profile Searcher API"))
                .andExpect(jsonPath("$.info.version").value("1.0.0"))
                .andExpect(jsonPath("$.paths['/api/alumni/search'].post").exists())
                .andExpect(jsonPath("$.paths['/api/alumni/all'].get").exists())
                .andExpect(jsonPath("$.paths['/api/alumni/search'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/alumni/search'].post.responses['502']").exists())
                .andExpect(jsonPath("$.paths['/api/alumni/all'].get.responses['503']").exists())
                .andExpect(jsonPath("$.paths['/api/alumni/all'].get.responses['400']").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.message").exists());
    }

    @Test
    void pointsSwaggerAtThePublicAddressWhenBehindAProxy() throws Exception {
        mockMvc.perform(get("/v3/api-docs")
                        .header("X-Forwarded-Proto", "https")
                        .header("X-Forwarded-Host", "alumni-searcher.onrender.com")
                        .header("X-Forwarded-Port", "443"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servers[0].url").value("https://alumni-searcher.onrender.com"));
    }

    @Test
    void servesSwaggerUi() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
