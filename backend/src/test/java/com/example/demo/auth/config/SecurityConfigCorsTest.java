package com.example.demo.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Public, read-only club data (GET /api/summary, /api/clubs, /api/clubs/calendar,
 * /api/clubs/{id}, /api/clubs/recommendations) is meant to be scraped by future data-collection
 * clients running from arbitrary origins, so it gets its own credential-less, any-origin CORS
 * policy. Everything else -- including writes to those same /api/clubs paths -- stays locked to
 * the first-party FRONTEND_ORIGIN with credentials, exactly as before.
 */
@SpringBootTest(properties = {
    "app.security.allowed-origins=https://frontend.example.com",
    // Public GET /api/clubs runs a real query, so the schema needs to exist even though
    // sql.init.mode is "never" for the shared test datasource; this is unrelated to CORS and
    // only here to let the request reach a 200 instead of a 500 from a missing table. Pointed
    // at its own named in-memory database (rather than the shared "hsclubs_test" every other
    // test class uses) so the schema.sql tables created below don't leak into, and break,
    // unrelated tests that manage their own schema (e.g. OAuthUserMapperTest's setUp() does its
    // own DROP TABLE, which fails once foreign keys from these tables exist).
    "spring.datasource.url=jdbc:h2:mem:security_config_cors_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
@AutoConfigureMockMvc
@Sql(scripts = "classpath:schema.sql")
class SecurityConfigCorsTest {

    private static final String ARBITRARY_ORIGIN = "https://data-collector.example";
    private static final String FIRST_PARTY_ORIGIN = "https://frontend.example.com";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicClubListGetAllowsArbitraryOriginWithoutCredentials() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/api/clubs").header(HttpHeaders.ORIGIN, ARBITRARY_ORIGIN))
            .andReturn().getResponse();

        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo("*");
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isNull();
    }

    @Test
    void publicClubDetailPreflightAllowsArbitraryOrigin() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(options("/api/clubs/some-slug")
                .header(HttpHeaders.ORIGIN, ARBITRARY_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
            .andReturn().getResponse();

        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo("*");
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isNull();
    }

    @Test
    void writingToAPublicClubPathIsNotOpenedToArbitraryOrigins() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(options("/api/clubs")
                .header(HttpHeaders.ORIGIN, ARBITRARY_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
            .andReturn().getResponse();

        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNotEqualTo("*");
    }

    @Test
    void authenticatedEndpointStillRejectsArbitraryOrigin() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/api/auth/me").header(HttpHeaders.ORIGIN, ARBITRARY_ORIGIN))
            .andReturn().getResponse();

        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
    }

    @Test
    void authenticatedEndpointStillAllowsFirstPartyOrigin() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/api/auth/providers").header(HttpHeaders.ORIGIN, FIRST_PARTY_ORIGIN))
            .andReturn().getResponse();

        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo(FIRST_PARTY_ORIGIN);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isEqualTo("true");
    }

    @Test
    void authenticatedWritePreflightAllowsCsrfHeaderForFirstPartyOrigin() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(options("/api/clubs/1")
                .header(HttpHeaders.ORIGIN, FIRST_PARTY_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type, X-XSRF-TOKEN"))
            .andReturn().getResponse();

        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo(FIRST_PARTY_ORIGIN);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS)).containsIgnoringCase("X-XSRF-TOKEN");
    }

    /**
     * Regression coverage for the bug this test class originally missed: dispatching purely on
     * "is this a safe method" (GET/HEAD) handed the public, wildcard, credential-less CORS
     * policy to first-party requests too, including credentialed GETs from our own frontend.
     * A request whose Origin is FRONTEND_ORIGIN must always get back the exact-origin,
     * credentialed policy on the public read paths, never the wildcard one -- otherwise the
     * browser rejects the frontend's own credentials:'include' fetch() calls to /api/clubs,
     * /api/summary, etc. whenever frontend and backend are cross-origin (e.g. local dev).
     */
    @Test
    void publicClubListGetAllowsFirstPartyOriginWithCredentials() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/api/clubs").header(HttpHeaders.ORIGIN, FIRST_PARTY_ORIGIN))
            .andReturn().getResponse();

        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo(FIRST_PARTY_ORIGIN);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isEqualTo("true");
    }

    @Test
    void publicSummaryGetAllowsFirstPartyOriginWithCredentials() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/api/summary").header(HttpHeaders.ORIGIN, FIRST_PARTY_ORIGIN))
            .andReturn().getResponse();

        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo(FIRST_PARTY_ORIGIN);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isEqualTo("true");
    }

    @Test
    void publicClubListPreflightAllowsFirstPartyOriginWithCredentials() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(options("/api/clubs")
                .header(HttpHeaders.ORIGIN, FIRST_PARTY_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
            .andReturn().getResponse();

        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo(FIRST_PARTY_ORIGIN);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isEqualTo("true");
    }

    @Test
    void publicSummaryPreflightAllowsFirstPartyOriginWithCredentials() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(options("/api/summary")
                .header(HttpHeaders.ORIGIN, FIRST_PARTY_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
            .andReturn().getResponse();

        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo(FIRST_PARTY_ORIGIN);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isEqualTo("true");
    }
}
