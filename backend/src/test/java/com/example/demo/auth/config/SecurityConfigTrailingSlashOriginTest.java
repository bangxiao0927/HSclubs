package com.example.demo.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Regression coverage for a trailing-slash mismatch between {@code isFirstPartyOrigin()} and
 * Spring's own {@link org.springframework.web.cors.CorsConfiguration#checkOrigin(String)}: if
 * {@code app.security.allowed-origins} is configured with a trailing slash (a plausible
 * operator typo, e.g. "https://app.example.com/") while the browser's Origin header never has
 * one, a comparison that doesn't normalize the same way Spring does would treat a genuine
 * first-party request as third-party and silently drop it back to the public, wildcard,
 * non-credentialed CORS policy -- reintroducing the credentialed-GET bug fixed in 5863e7b, but
 * only under this one configuration, which makes it especially easy to miss.
 */
@SpringBootTest(properties = {
    "app.security.allowed-origins=https://frontend.example.com/",
    // See SecurityConfigCorsTest for why GET /api/clubs needs a real schema and why this points
    // at its own named in-memory database rather than the shared "hsclubs_test" one.
    "spring.datasource.url=jdbc:h2:mem:security_config_trailing_slash_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
@AutoConfigureMockMvc
@Sql(scripts = "classpath:schema.sql")
class SecurityConfigTrailingSlashOriginTest {

    private static final String FIRST_PARTY_ORIGIN_NO_TRAILING_SLASH = "https://frontend.example.com";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicClubListGetFromFirstPartyOriginConfiguredWithTrailingSlashGetsExactOriginAndCredentials() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(
                get("/api/clubs").header(HttpHeaders.ORIGIN, FIRST_PARTY_ORIGIN_NO_TRAILING_SLASH))
            .andReturn().getResponse();

        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
            .isEqualTo(FIRST_PARTY_ORIGIN_NO_TRAILING_SLASH);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isEqualTo("true");
    }
}
