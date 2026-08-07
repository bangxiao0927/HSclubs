package com.example.demo.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Walks the real first-time-registration round trips end to end through the live security
 * filter chain: POST /api/auth/accept-terms followed by the GET /api/auth/me the SPA issues
 * right after, which together decide whether a student can ever leave /accept-terms.
 *
 * <p>Runs against the real schema fixture (the "h2" profile applies db/h2/reset.sql +
 * schema.sql) rather than a hand-rolled table, so column details that change the outcome --
 * last_login_at's ON UPDATE CURRENT_TIMESTAMP, the provider/provider_user_id unique key the
 * login upsert keys on -- match production instead of drifting away from it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:accept_terms_flow_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    // The h2 profile turns on MyBatis stdout logging for local development; keep it out of the
    // build log, which src/test/resources/application.yaml cannot do (a profile-specific file
    // wins over it).
    "mybatis.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl"})
class AcceptTermsFlowIntegrationTest {

    private static final String EMAIL = "new-student@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // Membership rows reference oauth_users, so clear the seeded graph before planting the
        // single brand-new student these tests are about.
        jdbcTemplate.update("DELETE FROM club_member");
        jdbcTemplate.update("DELETE FROM club_membership_requests");
        jdbcTemplate.update("DELETE FROM user_profiles");
        jdbcTemplate.update("DELETE FROM oauth_users");
        jdbcTemplate.update(
            """
            INSERT INTO oauth_users (provider, provider_user_id, email, display_name, avatar_url, role)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            "google", "google-new-1", EMAIL, "New Student", "https://example.com/a.png", "student");
    }

    private OAuth2AuthenticationToken googleToken() {
        DefaultOAuth2User principal = new DefaultOAuth2User(
            Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
            Map.of("sub", "google-new-1", "id", "google-new-1", "email", EMAIL, "name", "New Student"),
            "sub");
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
    }

    @Test
    void meReportsAcceptedTermsRightAfterAcceptTerms() throws Exception {
        OAuth2AuthenticationToken token = googleToken();

        mockMvc.perform(get("/api/auth/me").with(authentication(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.acceptedTerms").value(false));

        mockMvc.perform(post("/api/auth/accept-terms").with(authentication(token)))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").with(authentication(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.acceptedTerms").value(true));
    }

    @Test
    void acceptTermsRepairsAStoredRowThatWentMissingUnderAStillValidSession() throws Exception {
        // A database restore or migration can wipe oauth_users while 7-day sessions survive.
        // The write then matched nothing, was reported as 204, and /api/auth/me kept saying
        // acceptedTerms=false -- so the terms page could never be left.
        jdbcTemplate.update("DELETE FROM oauth_users");
        OAuth2AuthenticationToken token = googleToken();

        mockMvc.perform(post("/api/auth/accept-terms").with(authentication(token)))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").with(authentication(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.acceptedTerms").value(true));
    }

    @Test
    void repairingAMissingRowNeverRewritesAnExistingOne() throws Exception {
        // The repair must stay an INSERT of an absent row only. A manually elevated role is
        // the canary: the login upsert would reset it to 'student', which is the regression
        // that made GET /api/auth/me read-only in the first place (see AuthServiceTest).
        jdbcTemplate.update("UPDATE oauth_users SET role = 'president'");
        OAuth2AuthenticationToken token = googleToken();

        mockMvc.perform(post("/api/auth/accept-terms").with(authentication(token)))
            .andExpect(status().isNoContent());

        Long rows = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM oauth_users WHERE LOWER(email) = LOWER(?)", Long.class, EMAIL);
        assertThat(rows).isEqualTo(1L);
        String role = jdbcTemplate.queryForObject(
            "SELECT role FROM oauth_users WHERE LOWER(email) = LOWER(?)", String.class, EMAIL);
        assertThat(role).isEqualTo("president");
    }

    @Test
    void acceptTermsStaysIdempotentForAnAccountThatAlreadyAccepted() throws Exception {
        OAuth2AuthenticationToken token = googleToken();

        mockMvc.perform(post("/api/auth/accept-terms").with(authentication(token)))
            .andExpect(status().isNoContent());
        // The UPDATE only touches rows whose accepted_terms_at is NULL, so the second call
        // matches nothing -- it must still succeed rather than look like a missing account.
        mockMvc.perform(post("/api/auth/accept-terms").with(authentication(token)))
            .andExpect(status().isNoContent());
    }
}
