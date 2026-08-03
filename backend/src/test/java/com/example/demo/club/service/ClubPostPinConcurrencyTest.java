package com.example.demo.club.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.club.mapper.ClubPostMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.jdbc.Sql;

/**
 * Proves the cap in {@link ClubPostService#pin} is actually concurrency-safe (#80): a plain
 * count-then-update is not, since InnoDB (and H2 in MySQL mode) at REPEATABLE READ makes
 * {@code SELECT COUNT(*)} a non-locking consistent read. A mapper test cannot prove this --
 * mapper tests run without a surrounding Spring transaction, so a {@code FOR UPDATE} there
 * would take no lock and pass vacuously. This test runs against a real, autowired (proxied)
 * {@link ClubPostService} bean and a real H2 database so the actual row lock is exercised.
 */
@SpringBootTest(properties =
    "spring.datasource.url=jdbc:h2:mem:club_post_pin_concurrency_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@Sql(scripts = "classpath:schema.sql")
class ClubPostPinConcurrencyTest {

    @Autowired
    private ClubPostService clubPostService;

    @Autowired
    private ClubPostMapper clubPostMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM clubs");
        jdbcTemplate.update("DELETE FROM oauth_users");
    }

    // Forces the two pin() calls below to genuinely overlap, rather than merely happening to
    // race (which would make the test's outcome depend on scheduling luck): a raw JDBC
    // connection takes the same club-row FOR UPDATE lock first and holds it while both
    // background threads call the real, autowired (proxied) ClubPostService.pin(), so both are
    // guaranteed to queue up behind it. Releasing the held lock lets them proceed one at a
    // time -- whichever commits its pin first makes the count 3 for the other, which must then
    // see the cap and reject.
    @Test
    void pinningTwoDifferentPostsConcurrentlyNeverExceedsTheCapOfThree() throws Exception {
        long clubId = insertClub("Chess Club");
        long uid = insertOauthUser("author@example.com");
        long alreadyPinnedFirst = insertPost(clubId, uid, "Already pinned 1");
        long alreadyPinnedSecond = insertPost(clubId, uid, "Already pinned 2");
        long candidateA = insertPost(clubId, uid, "Candidate A");
        long candidateB = insertPost(clubId, uid, "Candidate B");
        clubPostMapper.pin(alreadyPinnedFirst, uid);
        clubPostMapper.pin(alreadyPinnedSecond, uid);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (Connection rawConnection = dataSource.getConnection()) {
            rawConnection.setAutoCommit(false);
            try (PreparedStatement statement =
                     rawConnection.prepareStatement("SELECT id FROM clubs WHERE id = ? FOR UPDATE")) {
                statement.setLong(1, clubId);
                statement.executeQuery();
            }

            Callable<Exception> pinCandidateA = () -> attemptPin(clubId, candidateA, uid);
            Callable<Exception> pinCandidateB = () -> attemptPin(clubId, candidateB, uid);
            Future<Exception> resultA = executor.submit(pinCandidateA);
            Future<Exception> resultB = executor.submit(pinCandidateB);
            // Give both background threads time to reach and queue up behind the held lock
            // before it is released, so releasing it is what actually lets either proceed.
            Thread.sleep(300);

            rawConnection.commit();

            Exception failureA = resultA.get(5, TimeUnit.SECONDS);
            Exception failureB = resultB.get(5, TimeUnit.SECONDS);

            List<Exception> failures = Arrays.asList(failureA, failureB).stream()
                .filter(Objects::nonNull)
                .toList();
            assertThat(failures).hasSize(1);
            assertThat(failures.get(0)).isInstanceOf(IllegalStateException.class);
            assertThat(failures.get(0).getMessage()).contains("3");
        } finally {
            executor.shutdownNow();
        }

        assertThat(clubPostMapper.countPinnedByClubId(clubId)).isEqualTo(3);
    }

    private Exception attemptPin(long clubId, long postId, long uid) {
        try {
            clubPostService.pin(clubId, postId, uid);
            return null;
        } catch (Exception e) {
            return e;
        }
    }

    // H2's lock timeout is roughly 2 seconds (SQL error 50200); this asserts a concurrent
    // FOR UPDATE holder makes pin() surface that as a Spring PessimisticLockingFailureException
    // (translated by Spring's SQLErrorCodeSQLExceptionTranslator), which the controller maps to
    // 409 -- not the 500 an unmapped SQLException/PersistenceException would otherwise produce.
    @Test
    void pinSurfacesALockTimeoutAsAPessimisticLockingFailure() throws Exception {
        long clubId = insertClub("Chess Club");
        long uid = insertOauthUser("author@example.com");
        long postId = insertPost(clubId, uid, "Post 1");

        try (Connection rawConnection = dataSource.getConnection()) {
            rawConnection.setAutoCommit(false);
            try (PreparedStatement statement =
                     rawConnection.prepareStatement("SELECT id FROM clubs WHERE id = ? FOR UPDATE")) {
                statement.setLong(1, clubId);
                statement.executeQuery();
            }

            assertThatThrownBy(() -> clubPostService.pin(clubId, postId, uid))
                .isInstanceOf(PessimisticLockingFailureException.class);
        }
    }

    private long insertClub(String name) {
        jdbcTemplate.update("MERGE INTO club_category (cate_name) KEY (cate_name) VALUES ('STEM & Innovation')");
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO clubs (name, category, status, visibility) VALUES (?, 'STEM & Innovation', 'active', 'public')",
                Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, name);
            return statement;
        }, keyHolder);
        return extractId(keyHolder);
    }

    private long insertOauthUser(String email) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO oauth_users (provider, provider_user_id, email, display_name) "
                    + "VALUES ('google', ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, email);
            statement.setString(2, email);
            statement.setString(3, "Author");
            return statement;
        }, keyHolder);
        return extractId(keyHolder, "uid");
    }

    private long insertPost(long clubId, long authorUid, String title) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO club_post (club_id, author_oauth_user_id, title, image_url) "
                    + "VALUES (?, ?, ?, '/uploads/club-posts/seed.jpg')",
                Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, clubId);
            statement.setLong(2, authorUid);
            statement.setString(3, title);
            return statement;
        }, keyHolder);
        return extractId(keyHolder);
    }

    private static long extractId(KeyHolder keyHolder) {
        return extractId(keyHolder, "id");
    }

    private static long extractId(KeyHolder keyHolder, String columnName) {
        Map<String, Object> keys = keyHolder.getKeys();
        Object idValue = keys.containsKey(columnName) ? keys.get(columnName) : keys.get(columnName.toUpperCase(Locale.ROOT));
        return ((Number) idValue).longValue();
    }
}
