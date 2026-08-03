package com.example.demo.club.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Locale;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.jdbc.Sql;

/**
 * Real-transaction, real-H2 coverage for #79's other concurrency requirement: a lock timeout
 * must surface as {@link CannotAcquireLockException} (which {@code ClubPostCommentController}
 * maps to 409), never an unhandled 500. Runs in its own {@code @SpringBootTest} context, with
 * its own H2 database and a short {@code LOCK_TIMEOUT}, so this test does not have to wait out
 * H2's ~2s default and does not affect other tests' lock behavior.
 */
@SpringBootTest(properties =
    "spring.datasource.url=jdbc:h2:mem:club_post_comment_lock_timeout_test;MODE=MySQL;"
        + "DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;INIT=SET LOCK_TIMEOUT 300")
@Sql(scripts = "classpath:schema.sql")
class ClubPostCommentLockTimeoutIntegrationTest {

    @Autowired
    private ClubPostCommentService clubPostCommentService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM clubs");
        jdbcTemplate.update("DELETE FROM oauth_users");
    }

    private long createClubWithAuthor() {
        jdbcTemplate.update("MERGE INTO club_category (cate_name) KEY (cate_name) VALUES ('STEM & Innovation')");
        KeyHolder clubKeyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> connection.prepareStatement(
            "INSERT INTO clubs (name, category, status, visibility) "
                + "VALUES ('Chess Club', 'STEM & Innovation', 'active', 'public')",
            Statement.RETURN_GENERATED_KEYS), clubKeyHolder);

        jdbcTemplate.update(
            "INSERT INTO oauth_users (provider, provider_user_id, email, display_name) "
                + "VALUES ('google', 'g-1', 'author@example.com', 'Ada Lovelace')");

        return extractId(clubKeyHolder, "id");
    }

    private long insertPost(long clubId, long authorUid) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO club_post (club_id, author_oauth_user_id, title, image_url) "
                    + "VALUES (?, ?, 'Meeting recap', '/uploads/club-posts/seed.jpg')",
                Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, clubId);
            statement.setLong(2, authorUid);
            return statement;
        }, keyHolder);
        return extractId(keyHolder, "id");
    }

    private static long extractId(KeyHolder keyHolder, String columnName) {
        Object idValue = keyHolder.getKeys().containsKey(columnName)
            ? keyHolder.getKeys().get(columnName)
            : keyHolder.getKeys().get(columnName.toUpperCase(Locale.ROOT));
        return ((Number) idValue).longValue();
    }

    @Test
    void aRowLockedByAnotherConnectionSurfacesAsCannotAcquireLockException() throws Exception {
        long clubId = createClubWithAuthor();
        long authorUid = jdbcTemplate.queryForObject("SELECT uid FROM oauth_users LIMIT 1", Long.class);
        long postId = insertPost(clubId, authorUid);

        try (Connection lockHolder = dataSource.getConnection()) {
            lockHolder.setAutoCommit(false);
            try (PreparedStatement statement =
                     lockHolder.prepareStatement("SELECT id FROM club_post WHERE id = ? FOR UPDATE")) {
                statement.setLong(1, postId);
                statement.execute();
            }

            assertThatThrownBy(() ->
                clubPostCommentService.create(clubId, postId, authorUid, "Blocked comment"))
                .isInstanceOf(CannotAcquireLockException.class);

            lockHolder.rollback();
        }
    }
}
