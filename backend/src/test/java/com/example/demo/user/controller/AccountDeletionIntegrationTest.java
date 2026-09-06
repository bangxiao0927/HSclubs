package com.example.demo.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:account_deletion_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "mybatis.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl"})
class AccountDeletionIntegrationTest {

    private static final String EMAIL = "delete-me@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long userId;
    private Long clubId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM club_post_comment");
        jdbcTemplate.update("DELETE FROM club_post");
        jdbcTemplate.update("DELETE FROM club_member");
        jdbcTemplate.update("DELETE FROM club_membership_requests");
        jdbcTemplate.update("DELETE FROM user_profiles");
        jdbcTemplate.update("DELETE FROM oauth_users");

        jdbcTemplate.update(
            "INSERT INTO oauth_users (provider, provider_user_id, email, display_name, role) VALUES (?, ?, ?, ?, ?)",
            "google", "delete-user-1", EMAIL, "Delete Me", "student");
        userId = jdbcTemplate.queryForObject(
            "SELECT uid FROM oauth_users WHERE email = ?", Long.class, EMAIL);
        clubId = jdbcTemplate.queryForObject("SELECT id FROM clubs ORDER BY id LIMIT 1", Long.class);

        jdbcTemplate.update("INSERT INTO user_profiles (oauth_user_id, graduation_year) VALUES (?, ?)", userId, 2027);
        jdbcTemplate.update("INSERT INTO club_member (club_id, oauth_user_id, role_name) VALUES (?, ?, ?)",
            clubId, userId, "president");
        jdbcTemplate.update("INSERT INTO club_membership_requests (club_id, oauth_user_id) VALUES (?, ?)",
            clubId, userId);
        jdbcTemplate.update(
            "INSERT INTO club_post (club_id, author_oauth_user_id, title, image_url) VALUES (?, ?, ?, ?)",
            clubId, userId, "Temporary post", "/uploads/temporary.webp");
        Long postId = jdbcTemplate.queryForObject("SELECT id FROM club_post LIMIT 1", Long.class);
        jdbcTemplate.update(
            "INSERT INTO club_post_comment (post_id, author_oauth_user_id, body) VALUES (?, ?, ?)",
            postId, userId, "Temporary comment");
        jdbcTemplate.update("UPDATE clubs SET approved_by_oauth_user_id = ? WHERE id = ?", userId, clubId);
    }

    @Test
    void deletesTheEntireUserGraphAndInvalidatesTheSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("sentinel", "present");

        mockMvc.perform(delete("/api/users/me")
                .session(session)
                .with(authentication(googleToken())))
            .andExpect(status().isNoContent())
            .andExpect(cookie().maxAge("JSESSIONID", 0));

        assertThat(count("oauth_users")).isZero();
        assertThat(count("user_profiles")).isZero();
        assertThat(count("club_member")).isZero();
        assertThat(count("club_membership_requests")).isZero();
        assertThat(count("club_post")).isZero();
        assertThat(count("club_post_comment")).isZero();
        assertThat(count("clubs")).isPositive();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT approved_by_oauth_user_id FROM clubs WHERE id = ?", Long.class, clubId)).isNull();
        assertThatThrownBy(() -> session.getAttribute("sentinel")).isInstanceOf(IllegalStateException.class);
    }

    private Long count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private OAuth2AuthenticationToken googleToken() {
        DefaultOAuth2User principal = new DefaultOAuth2User(
            Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
            Map.of("sub", "delete-user-1", "email", EMAIL, "name", "Delete Me"),
            "sub");
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
    }
}
