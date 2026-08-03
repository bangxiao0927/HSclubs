package com.example.demo.club.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

import com.example.demo.club.mapper.ClubPostMapper;
import com.example.demo.club.model.PublicClubPostComment;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;

/**
 * Real-transaction, real-H2 coverage for #79's concurrency race: two genuinely overlapping
 * requests for the same post's 51st comment, which a mocked-mapper unit test cannot exercise
 * (see ClubPostCommentWriterTest for the ordering guarantee that makes this race safe).
 */
@SpringBootTest(properties =
    "spring.datasource.url=jdbc:h2:mem:club_post_comment_concurrency_test;MODE=MySQL;"
        + "DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@Sql(scripts = "classpath:schema.sql")
class ClubPostCommentConcurrencyIntegrationTest {

    @Autowired
    private ClubPostCommentService clubPostCommentService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // A spy, not a mock: every test must keep going through the real mapper against the real H2
    // database; the delay it injects below only widens the race window, it never fakes a result.
    @MockitoSpyBean
    private ClubPostMapper clubPostMapperSpy;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM clubs");
        jdbcTemplate.update("DELETE FROM oauth_users");
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
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

    private void insertComments(long postId, long authorUid, int count) {
        for (int i = 0; i < count; i++) {
            jdbcTemplate.update(
                "INSERT INTO club_post_comment (post_id, author_oauth_user_id, body) VALUES (?, ?, ?)",
                postId, authorUid, "Existing comment " + i);
        }
    }

    private static long extractId(KeyHolder keyHolder, String columnName) {
        Object idValue = keyHolder.getKeys().containsKey(columnName)
            ? keyHolder.getKeys().get(columnName)
            : keyHolder.getKeys().get(columnName.toUpperCase(Locale.ROOT));
        return ((Number) idValue).longValue();
    }

    // The issue's own race: without the FOR UPDATE lock serializing the two requests, both
    // could observe 49 existing comments and both insert, producing 51. With it, the second
    // caller must observe the first caller's committed 50th comment and be rejected.
    @Test
    void onlyOneOfTwoConcurrentRequestsForThe51stCommentSucceeds() throws Exception {
        long clubId = createClubWithAuthor();
        long authorUid = jdbcTemplate.queryForObject("SELECT uid FROM oauth_users LIMIT 1", Long.class);
        long postId = insertPost(clubId, authorUid);
        insertComments(postId, authorUid, 49);

        // Widens the race window deterministically: whichever caller is first to acquire the
        // FOR UPDATE lock now has time to also finish its count before the second caller's own
        // lock attempt can even begin, instead of leaving the outcome to incidental thread
        // scheduling. Re-runs the exact query countCommentsByPostId's mapper XML defines (see
        // ClubPostMapper.xml) rather than invocation.callRealMethod(), which Mockito does not
        // support for interface spies backed by a MyBatis mapper proxy.
        doAnswer(invocation -> {
            Thread.sleep(300);
            Long queriedPostId = invocation.getArgument(0);
            return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM club_post_comment WHERE post_id = ?", Integer.class, queriedPostId);
        }).when(clubPostMapperSpy).countCommentsByPostId(anyLong());

        CyclicBarrier barrier = new CyclicBarrier(2);
        Callable<PublicClubPostComment> attempt = () -> {
            barrier.await(5, TimeUnit.SECONDS);
            return clubPostCommentService.create(clubId, postId, authorUid, "Racing comment");
        };

        Future<PublicClubPostComment> first = executor.submit(attempt);
        Future<PublicClubPostComment> second = executor.submit(attempt);

        int succeeded = 0;
        int rejected = 0;
        for (Future<PublicClubPostComment> future : List.of(first, second)) {
            try {
                future.get(10, TimeUnit.SECONDS);
                succeeded++;
            } catch (ExecutionException e) {
                assertThat(e.getCause()).isInstanceOf(CommentLimitExceededException.class);
                rejected++;
            }
        }

        assertThat(succeeded).isEqualTo(1);
        assertThat(rejected).isEqualTo(1);
        Integer finalCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM club_post_comment WHERE post_id = ?", Integer.class, postId);
        assertThat(finalCount).isEqualTo(50);
    }
}
