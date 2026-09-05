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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A school that limits sign-in to its own email domain, which is what DEPLOYMENT.md recommends,
 * and a review account whose address is deliberately outside it.
 *
 * <p>The regression this pins down is not the login -- that never consulted the sign-in
 * restrictions -- but the step straight after it. Accepting the terms did, so the reviewer used
 * to land in a loop no amount of retrying escapes: signed in, acceptedTerms=false forever, and a
 * 403 on every attempt to change that.
 */
@SpringBootTest(properties = {
    "app.security.login.allowed-email-domains=mvla.net",
    "app.security.internal-review-account.email=app-review@hsclubs.net",
    "app.security.internal-review-account.display-name=App Review",
    "spring.datasource.url=jdbc:h2:mem:internal_review_domain;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
@AutoConfigureMockMvc
@Sql(scripts = "classpath:schema.sql")
class InternalReviewDomainRestrictionTest {

    private static final String PASSWORD = "test-review-password";

    @DynamicPropertySource
    static void passwordHash(DynamicPropertyRegistry registry) {
        registry.add("app.security.internal-review-account.password-hash",
            () -> new BCryptPasswordEncoder().encode(PASSWORD));
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void theReviewAccountCanGetPastTheTermsPageDespiteTheDomainRestriction() throws Exception {
        var login = mockMvc.perform(post("/api/auth/internal/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"app-review@hsclubs.net\",\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.acceptedTerms").value(false))
            .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        mockMvc.perform(post("/api/auth/accept-terms").session(session))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.acceptedTerms").value(true));
    }
}
