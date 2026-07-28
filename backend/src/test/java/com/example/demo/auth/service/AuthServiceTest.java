package com.example.demo.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import com.example.demo.auth.model.AuthUser;

@SpringBootTest
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS user_profiles");
        jdbcTemplate.execute("DROP TABLE IF EXISTS oauth_users");
        jdbcTemplate.execute("""
            CREATE TABLE oauth_users (
                uid BIGINT PRIMARY KEY AUTO_INCREMENT,
                provider VARCHAR(50) NOT NULL,
                provider_user_id VARCHAR(150) NOT NULL,
                email VARCHAR(255),
                display_name VARCHAR(255),
                avatar_url VARCHAR(500),
                role VARCHAR(50),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                last_login_at TIMESTAMP,
                accepted_terms_at TIMESTAMP,
                CONSTRAINT uq_provider_user UNIQUE (provider, provider_user_id)
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE user_profiles (
                oauth_user_id BIGINT PRIMARY KEY,
                graduation_year INT NOT NULL,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """);
    }

    @Test
    void checkingTheCurrentSessionDoesNotOverwriteAnAlreadyElevatedRoleOrLoginTimestamp() {
        LocalDateTime originalLoginAt = LocalDateTime.of(2024, 1, 1, 0, 0);
        jdbcTemplate.update(
            """
            INSERT INTO oauth_users (provider, provider_user_id, email, display_name, role, last_login_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            "google", "google-42", "president@example.com", "Original Name", "president", originalLoginAt);

        OAuth2AuthenticationToken token = googleToken("president@example.com", "Session Name");

        AuthUser result = authService.getAuthenticatedUser(token);

        assertThat(result.getEmail()).isEqualTo("president@example.com");
        Map<String, Object> row = jdbcTemplate.queryForMap(
            "SELECT role, last_login_at FROM oauth_users WHERE email = ?", "president@example.com");
        assertThat(row.get("role")).isEqualTo("president");
        assertThat(row.get("last_login_at")).isEqualTo(java.sql.Timestamp.valueOf(originalLoginAt));
    }

    @Test
    void checkingTheCurrentSessionNeverWritesToTheDatabase() {
        // Login itself (CustomOAuth2UserService#loadUser, exercised elsewhere) is what creates the
        // oauth_users row; a plain session check must not insert or update anything, including for
        // a brand-new user who has no row yet.
        OAuth2AuthenticationToken token = googleToken("newstudent@example.com", "New Student");

        AuthUser result = authService.getAuthenticatedUser(token);

        assertThat(result.getEmail()).isEqualTo("newstudent@example.com");
        assertThat(result.getDisplayName()).isEqualTo("New Student");
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM oauth_users", Integer.class);
        assertThat(count).isZero();
    }

    private OAuth2AuthenticationToken googleToken(String email, String name) {
        OAuth2User principal = new DefaultOAuth2User(
            java.util.List.of(() -> "ROLE_USER"),
            Map.of("email", email, "name", name, "sub", "google-42"),
            "sub");
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
    }
}
