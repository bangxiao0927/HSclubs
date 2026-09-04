package com.example.demo.auth.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "app.security.internal-review-account.email=review@example.edu",
    "app.security.internal-review-account.display-name=App Review",
    "spring.datasource.url=jdbc:h2:mem:internal_review_auth;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
@AutoConfigureMockMvc
@Sql(scripts = "classpath:schema.sql")
class InternalReviewAuthIntegrationTest {

    private static final String PASSWORD = "test-review-password";

    @DynamicPropertySource
    static void passwordHash(DynamicPropertyRegistry registry) {
        registry.add("app.security.internal-review-account.password-hash",
            () -> new BCryptPasswordEncoder().encode(PASSWORD));
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void advertisesTheReviewAccountOnlyWhenConfigured() throws Exception {
        mockMvc.perform(get("/api/auth/providers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == 'internal')].name").value("Password"));
    }

    @Test
    void validCredentialsCreateANormalProtectedSession() throws Exception {
        var result = mockMvc.perform(post("/api/auth/internal/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"review@example.edu\",\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("review@example.edu"))
            .andExpect(jsonPath("$.displayName").value("App Review"))
            .andExpect(jsonPath("$.provider").value("internal"))
            .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        mockMvc.perform(get("/api/auth/me").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.provider").value("internal"));
    }

    @Test
    void wrongCredentialsReturnTheSameGenericError() throws Exception {
        mockMvc.perform(post("/api/auth/internal/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"someone@example.edu\",\"password\":\"wrong\"}"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/internal/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"review@example.edu\",\"password\":\"wrong\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void repeatedFailuresAreTemporarilyRateLimited() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/auth/internal/login")
                    .with(request -> {
                        request.setRemoteAddr("192.0.2.25");
                        return request;
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"review@example.edu\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/auth/internal/login")
                .with(request -> {
                    request.setRemoteAddr("192.0.2.25");
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"review@example.edu\",\"password\":\"wrong\"}"))
            .andExpect(status().isTooManyRequests());
    }
}
