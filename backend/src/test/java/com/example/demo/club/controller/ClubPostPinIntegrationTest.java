package com.example.demo.club.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Full-stack acceptance coverage for #80: real DB, real security filter chain, the real
 * {@link com.example.demo.club.service.ClubPostService}. Unit/mapper/service/controller tests
 * elsewhere cover each layer in isolation; this proves the whole PUT/DELETE .../pin request
 * works end to end for every acceptance criterion that spans layers.
 */
@SpringBootTest(properties =
    "spring.datasource.url=jdbc:h2:mem:club_post_pin_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@Sql(scripts = "classpath:schema.sql")
class ClubPostPinIntegrationTest {

    private static final String PRESIDENT_EMAIL = "president@example.com";
    private static final String MEMBER_EMAIL = "member@example.com";
    private static final String OWNER_EMAIL = "test-owner@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // @Sql's schema.sql only CREATE TABLE IF NOT EXISTS, so it does not reset data between test
    // methods sharing this class's Spring context; cascade deletes clear club_member/club_post
    // along with their owning rows.
    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM clubs");
        jdbcTemplate.update("DELETE FROM oauth_users");
    }

    private long createActiveClubWithAPresidentAndAMember() {
        jdbcTemplate.update("MERGE INTO club_category (cate_name) KEY (cate_name) VALUES ('STEM & Innovation')");
        long clubId = insertClub("Chess Club", "active");
        long presidentUid = insertOauthUser(PRESIDENT_EMAIL, "President Ada", null);
        long memberUid = insertOauthUser(MEMBER_EMAIL, "Member Bob", null);
        insertOauthUser(OWNER_EMAIL, "Owner", null);
        jdbcTemplate.update(
            "INSERT INTO club_member (club_id, oauth_user_id, role_name) VALUES (?, ?, 'president')",
            clubId, presidentUid);
        jdbcTemplate.update(
            "INSERT INTO club_member (club_id, oauth_user_id, role_name) VALUES (?, ?, 'member')",
            clubId, memberUid);
        return clubId;
    }

    @Test
    void presidentCanPinAndUnpinAPost() throws Exception {
        long clubId = createActiveClubWithAPresidentAndAMember();
        long postId = insertPost(clubId, "Post 1");

        mockMvc.perform(put("/api/clubs/" + clubId + "/posts/" + postId + "/pin")
                .with(authentication(oauthToken(PRESIDENT_EMAIL))))
            .andExpect(status().isNoContent());

        Boolean pinned = jdbcTemplate.queryForObject(
            "SELECT pinned_at IS NOT NULL FROM club_post WHERE id = ?", Boolean.class, postId);
        assertThat(pinned).isTrue();

        mockMvc.perform(delete("/api/clubs/" + clubId + "/posts/" + postId + "/pin")
                .with(authentication(oauthToken(PRESIDENT_EMAIL))))
            .andExpect(status().isNoContent());

        Boolean unpinned = jdbcTemplate.queryForObject(
            "SELECT pinned_at IS NULL FROM club_post WHERE id = ?", Boolean.class, postId);
        assertThat(unpinned).isTrue();
    }

    @Test
    void platformOwnerCanPinEvenWithoutBeingAMember() throws Exception {
        long clubId = createActiveClubWithAPresidentAndAMember();
        long postId = insertPost(clubId, "Post 1");

        mockMvc.perform(put("/api/clubs/" + clubId + "/posts/" + postId + "/pin")
                .with(authentication(oauthToken(OWNER_EMAIL))))
            .andExpect(status().isNoContent());
    }

    @Test
    void ordinaryMemberAttemptingToPinGetsForbidden() throws Exception {
        long clubId = createActiveClubWithAPresidentAndAMember();
        long postId = insertPost(clubId, "Post 1");

        mockMvc.perform(put("/api/clubs/" + clubId + "/posts/" + postId + "/pin")
                .with(authentication(oauthToken(MEMBER_EMAIL))))
            .andExpect(status().isForbidden());
    }

    @Test
    void pinRecordsPinnedByOauthUserIdAndUnpinClearsIt() throws Exception {
        long clubId = createActiveClubWithAPresidentAndAMember();
        long postId = insertPost(clubId, "Post 1");
        long presidentUid = jdbcTemplate.queryForObject(
            "SELECT uid FROM oauth_users WHERE email = ?", Long.class, PRESIDENT_EMAIL);

        mockMvc.perform(put("/api/clubs/" + clubId + "/posts/" + postId + "/pin")
                .with(authentication(oauthToken(PRESIDENT_EMAIL))))
            .andExpect(status().isNoContent());

        Long pinnedBy = jdbcTemplate.queryForObject(
            "SELECT pinned_by_oauth_user_id FROM club_post WHERE id = ?", Long.class, postId);
        assertThat(pinnedBy).isEqualTo(presidentUid);

        mockMvc.perform(delete("/api/clubs/" + clubId + "/posts/" + postId + "/pin")
                .with(authentication(oauthToken(PRESIDENT_EMAIL))))
            .andExpect(status().isNoContent());

        Long pinnedByAfterUnpin = jdbcTemplate.queryForObject(
            "SELECT pinned_by_oauth_user_id FROM club_post WHERE id = ?", Long.class, postId);
        assertThat(pinnedByAfterUnpin).isNull();
    }

    // The core rule the whole feature exists to enforce: a fourth pin must fail with a message
    // naming the limit, and none of the first three pins may be silently evicted to make room.
    @Test
    void pinningAFourthPostReturnsConflictAndDoesNotEvictAnyExistingPin() throws Exception {
        long clubId = createActiveClubWithAPresidentAndAMember();
        long firstPostId = insertPost(clubId, "Post 1");
        long secondPostId = insertPost(clubId, "Post 2");
        long thirdPostId = insertPost(clubId, "Post 3");
        long fourthPostId = insertPost(clubId, "Post 4");

        mockMvc.perform(put("/api/clubs/" + clubId + "/posts/" + firstPostId + "/pin")
                .with(authentication(oauthToken(PRESIDENT_EMAIL))))
            .andExpect(status().isNoContent());
        mockMvc.perform(put("/api/clubs/" + clubId + "/posts/" + secondPostId + "/pin")
                .with(authentication(oauthToken(PRESIDENT_EMAIL))))
            .andExpect(status().isNoContent());
        mockMvc.perform(put("/api/clubs/" + clubId + "/posts/" + thirdPostId + "/pin")
                .with(authentication(oauthToken(PRESIDENT_EMAIL))))
            .andExpect(status().isNoContent());

        mockMvc.perform(put("/api/clubs/" + clubId + "/posts/" + fourthPostId + "/pin")
                .with(authentication(oauthToken(PRESIDENT_EMAIL))))
            .andExpect(status().isConflict())
            .andExpect(status().reason("At most 3 posts can be pinned. Unpin one first."));

        List<Long> stillPinned = jdbcTemplate.queryForList(
            "SELECT id FROM club_post WHERE club_id = ? AND pinned_at IS NOT NULL ORDER BY id", Long.class, clubId);
        assertThat(stillPinned).containsExactly(firstPostId, secondPostId, thirdPostId);
    }

    @Test
    void pinningAPostBelongingToAnotherClubReturnsNotFound() throws Exception {
        long clubId = createActiveClubWithAPresidentAndAMember();
        jdbcTemplate.update("MERGE INTO club_category (cate_name) KEY (cate_name) VALUES ('Culture & Identity')");
        long otherClubId = insertClub("Robotics", "active");
        long otherClubPostId = insertPostForClub(otherClubId, "Other club post");

        mockMvc.perform(put("/api/clubs/" + clubId + "/posts/" + otherClubPostId + "/pin")
                .with(authentication(oauthToken(PRESIDENT_EMAIL))))
            .andExpect(status().isNotFound());
    }

    @Test
    void pinningANonExistentPostReturnsNotFound() throws Exception {
        long clubId = createActiveClubWithAPresidentAndAMember();

        mockMvc.perform(put("/api/clubs/" + clubId + "/posts/999999/pin")
                .with(authentication(oauthToken(PRESIDENT_EMAIL))))
            .andExpect(status().isNotFound());
    }

    // No promotion logic exists anywhere in this feature: removing a pinned post's row must
    // never cause a different post to become pinned in its place.
    @Test
    void deletingAPinnedPostLeavesTheRemainingPinsUntouchedAndPromotesNothing() throws Exception {
        long clubId = createActiveClubWithAPresidentAndAMember();
        long firstPostId = insertPost(clubId, "Post 1");
        long secondPostId = insertPost(clubId, "Post 2");
        mockMvc.perform(put("/api/clubs/" + clubId + "/posts/" + firstPostId + "/pin")
                .with(authentication(oauthToken(PRESIDENT_EMAIL))))
            .andExpect(status().isNoContent());

        jdbcTemplate.update("DELETE FROM club_post WHERE id = ?", firstPostId);

        List<Long> stillPinned = jdbcTemplate.queryForList(
            "SELECT id FROM club_post WHERE club_id = ? AND pinned_at IS NOT NULL", Long.class, clubId);
        assertThat(stillPinned).isEmpty();
        Boolean secondPostStillUnpinned = jdbcTemplate.queryForObject(
            "SELECT pinned_at IS NULL FROM club_post WHERE id = ?", Boolean.class, secondPostId);
        assertThat(secondPostStillUnpinned).isTrue();
    }

    @Test
    void unpinReturnsThePostToItsChronologicalPositionInTheFeed() throws Exception {
        long clubId = createActiveClubWithAPresidentAndAMember();
        long olderPostId = insertPostWithCreatedAt(clubId, "Older", "2024-01-01 10:00:00");
        long newerPostId = insertPostWithCreatedAt(clubId, "Newer", "2024-01-02 10:00:00");
        mockMvc.perform(put("/api/clubs/" + clubId + "/posts/" + olderPostId + "/pin")
                .with(authentication(oauthToken(PRESIDENT_EMAIL))))
            .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/clubs/" + clubId + "/posts/" + olderPostId + "/pin")
                .with(authentication(oauthToken(PRESIDENT_EMAIL))))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/clubs/" + clubId + "/posts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", Matchers.hasSize(2)))
            .andExpect(jsonPath("$.items[0].id").value(newerPostId))
            .andExpect(jsonPath("$.items[1].id").value(olderPostId));
    }

    private long insertClub(String name, String status) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO clubs (name, category, status, visibility) VALUES (?, 'STEM & Innovation', ?, 'public')",
                Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, name);
            statement.setString(2, status);
            return statement;
        }, keyHolder);
        return extractId(keyHolder);
    }

    private long insertOauthUser(String email, String displayName, String avatarUrl) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO oauth_users (provider, provider_user_id, email, display_name, avatar_url) "
                    + "VALUES ('google', ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, email);
            statement.setString(2, email);
            statement.setString(3, displayName);
            statement.setString(4, avatarUrl);
            return statement;
        }, keyHolder);
        return extractId(keyHolder, "uid");
    }

    private long insertPost(long clubId, String title) {
        long authorUid = jdbcTemplate.queryForObject(
            "SELECT oauth_user_id FROM club_member WHERE club_id = ? LIMIT 1", Long.class, clubId);
        return insertPostAsAuthor(clubId, authorUid, title);
    }

    private long insertPostForClub(long clubId, String title) {
        long authorUid = insertOauthUser("author-" + clubId + "@example.com", "Author", null);
        return insertPostAsAuthor(clubId, authorUid, title);
    }

    private long insertPostAsAuthor(long clubId, long authorUid, String title) {
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

    private long insertPostWithCreatedAt(long clubId, String title, String createdAt) {
        long authorUid = jdbcTemplate.queryForObject(
            "SELECT oauth_user_id FROM club_member WHERE club_id = ? LIMIT 1", Long.class, clubId);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO club_post (club_id, author_oauth_user_id, title, image_url, created_at) "
                    + "VALUES (?, ?, ?, '/uploads/club-posts/seed.jpg', ?)",
                Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, clubId);
            statement.setLong(2, authorUid);
            statement.setString(3, title);
            statement.setTimestamp(4, java.sql.Timestamp.valueOf(createdAt));
            return statement;
        }, keyHolder);
        return extractId(keyHolder);
    }

    // H2 reports every column with a default (id, created_at, updated_at) as "generated" here,
    // so getKey() (which requires exactly one) throws; look the id column up by name instead,
    // tolerating either case since drivers differ on how they report generated column names.
    private static long extractId(KeyHolder keyHolder) {
        return extractId(keyHolder, "id");
    }

    private static long extractId(KeyHolder keyHolder, String columnName) {
        Map<String, Object> keys = keyHolder.getKeys();
        Object idValue = keys.containsKey(columnName) ? keys.get(columnName) : keys.get(columnName.toUpperCase(Locale.ROOT));
        return ((Number) idValue).longValue();
    }

    private OAuth2AuthenticationToken oauthToken(String email) {
        OAuth2User principal = new DefaultOAuth2User(
            List.of(() -> "ROLE_USER"),
            Map.of("email", email, "sub", email),
            "sub");
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
    }
}
