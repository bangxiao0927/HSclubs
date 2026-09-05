package com.example.demo.auth.internal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Every school that has not configured a review account, which is the default and the state most
 * deployments stay in. The password endpoint has to be absent for them, not merely unadvertised:
 * a school should not be carrying a sign-in path it never asked for.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:internal_review_disabled;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
@AutoConfigureMockMvc
class InternalReviewDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void theSignInPageIsOfferedNoPasswordOption() throws Exception {
        mockMvc.perform(get("/api/auth/providers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == 'internal')]").isEmpty());
    }

    @Test
    void thePasswordEndpointIsNotThere() throws Exception {
        mockMvc.perform(post("/api/auth/internal/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"someone@example.edu\",\"password\":\"anything\"}"))
            .andExpect(status().isNotFound());
    }
}
