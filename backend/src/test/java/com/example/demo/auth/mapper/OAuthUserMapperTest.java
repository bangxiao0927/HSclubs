package com.example.demo.auth.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.auth.model.OAuthUserRecord;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class OAuthUserMapperTest {

    @Autowired
    private OAuthUserMapper oAuthUserMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
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
                last_login_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                accepted_terms_at TIMESTAMP
            )
            """);
        jdbcTemplate.update(
            """
            INSERT INTO oauth_users (provider, provider_user_id, email, display_name, avatar_url, role)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            "google",
            "google-123",
            "bangxiao0927@gmail.com",
            "Bang Xiao",
            "https://example.com/avatar.png",
            "student"
        );
    }

    @Test
    void searchUsersMapsUidToApiId() {
        List<OAuthUserRecord> users = oAuthUserMapper.searchByEmailOrName("bangxiao", 10);

        assertThat(users).singleElement().satisfies((user) -> {
            assertThat(user.getId()).isEqualTo(1L);
            assertThat(user.getEmail()).isEqualTo("bangxiao0927@gmail.com");
        });
    }
}
