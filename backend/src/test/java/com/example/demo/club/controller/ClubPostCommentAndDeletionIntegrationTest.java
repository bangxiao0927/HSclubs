package com.example.demo.club.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.club.mapper.ClubPostMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Full-stack acceptance coverage for #79's post-deletion and comment endpoints: real DB, real
 * security filter chain, real {@link com.example.demo.club.service.ImageStorageService}.
 * Unit/mapper/service/controller tests elsewhere cover each layer and each authorization branch
 * in isolation; this proves the whole request works end to end for the criteria that span
 * layers, especially the file-removal-after-commit contract.
 */
@SpringBootTest(properties =
    "spring.datasource.url=jdbc:h2:mem:club_post_comment_deletion_test;MODE=MySQL;"
        + "DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@Sql(scripts = "classpath:schema.sql")
class ClubPostCommentAndDeletionIntegrationTest {

    private static final String MEMBER_EMAIL = "member@example.com";
    private static final String PRESIDENT_EMAIL = "president@example.com";
    private static final String OUTSIDER_EMAIL = "outsider@example.com";
    private static final String OWNER_EMAIL = "test-owner@example.com";

    @TempDir
    static Path uploadDir;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // A spy, not a mock: every test except the rollback one must keep going through the real
    // mapper against the real H2 database.
    @MockitoSpyBean
    private ClubPostMapper clubPostMapperSpy;

    @DynamicPropertySource
    static void uploadDir(DynamicPropertyRegistry registry) {
        registry.add("app.upload.dir", () -> uploadDir.toString());
    }

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM clubs");
        jdbcTemplate.update("DELETE FROM oauth_users");
    }

    private long createClub() {
        jdbcTemplate.update("MERGE INTO club_category (cate_name) KEY (cate_name) VALUES ('STEM & Innovation')");
        return insertClub("Chess Club", "active");
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

    private long insertOauthUser(String email, String displayName) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO oauth_users (provider, provider_user_id, email, display_name) VALUES ('google', ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, email);
            statement.setString(2, email);
            statement.setString(3, displayName);
            return statement;
        }, keyHolder);
        return extractId(keyHolder, "uid");
    }

    private void addMember(long clubId, long uid, String role) {
        jdbcTemplate.update(
            "INSERT INTO club_member (club_id, oauth_user_id, role_name) VALUES (?, ?, ?)", clubId, uid, role);
    }

    private String writeStoredPhoto() throws Exception {
        Path clubPostsDir = uploadDir.resolve("club-posts");
        Files.createDirectories(clubPostsDir);
        String filename = UUID.randomUUID() + ".jpg";
        Files.write(clubPostsDir.resolve(filename), new byte[] {1, 2, 3});
        return "/uploads/club-posts/" + filename;
    }

    private long insertPost(long clubId, long authorUid, String imageUrl) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO club_post (club_id, author_oauth_user_id, title, image_url) "
                    + "VALUES (?, ?, 'Meeting recap', ?)",
                Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, clubId);
            statement.setLong(2, authorUid);
            statement.setString(3, imageUrl);
            return statement;
        }, keyHolder);
        return extractId(keyHolder);
    }

    private long insertComment(long postId, long authorUid, String body) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO club_post_comment (post_id, author_oauth_user_id, body) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, postId);
            statement.setLong(2, authorUid);
            statement.setString(3, body);
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

    private static String extractJsonStringField(String json, String field) {
        String needle = "\"" + field + "\":\"";
        int start = json.indexOf(needle) + needle.length();
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }

    private int countPostRows(long postId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM club_post WHERE id = ?", Integer.class, postId);
    }

    private int countCommentRows(long postId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM club_post_comment WHERE post_id = ?", Integer.class, postId);
    }

    private OAuth2AuthenticationToken oauthToken(String email) {
        OAuth2User principal = new DefaultOAuth2User(
            List.of(() -> "ROLE_USER"),
            Map.of("email", email, "sub", email),
            "sub");
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
    }

    // ---- Post deletion ----

    @Test
    void authorCanDeleteOwnPostWhichCascadesItsCommentsAndRemovesTheFileOnlyAfterCommit() throws Exception {
        long clubId = createClub();
        long authorUid = insertOauthUser(MEMBER_EMAIL, "Ada Lovelace");
        addMember(clubId, authorUid, "member");
        String imageUrl = writeStoredPhoto();
        long postId = insertPost(clubId, authorUid, imageUrl);
        insertComment(postId, authorUid, "Nice!");
        Path storedFile = uploadDir.resolve(imageUrl.substring("/uploads/".length()));
        assertThat(Files.exists(storedFile)).isTrue();

        mockMvc.perform(delete("/api/clubs/" + clubId + "/posts/" + postId)
                .with(authentication(oauthToken(MEMBER_EMAIL))))
            .andExpect(status().isNoContent());

        assertThat(countPostRows(postId)).isZero();
        assertThat(countCommentRows(postId)).isZero();
        assertThat(Files.exists(storedFile)).isFalse();
    }

    @Test
    void presidentCanDeleteAnotherMembersPost() throws Exception {
        long clubId = createClub();
        long authorUid = insertOauthUser(MEMBER_EMAIL, "Ada Lovelace");
        long presidentUid = insertOauthUser(PRESIDENT_EMAIL, "President");
        addMember(clubId, authorUid, "member");
        addMember(clubId, presidentUid, "president");
        long postId = insertPost(clubId, authorUid, writeStoredPhoto());

        mockMvc.perform(delete("/api/clubs/" + clubId + "/posts/" + postId)
                .with(authentication(oauthToken(PRESIDENT_EMAIL))))
            .andExpect(status().isNoContent());

        assertThat(countPostRows(postId)).isZero();
    }

    @Test
    void platformOwnerCanDeleteAnyonesPost() throws Exception {
        long clubId = createClub();
        long authorUid = insertOauthUser(MEMBER_EMAIL, "Ada Lovelace");
        insertOauthUser(OWNER_EMAIL, "Owner");
        addMember(clubId, authorUid, "member");
        long postId = insertPost(clubId, authorUid, writeStoredPhoto());

        mockMvc.perform(delete("/api/clubs/" + clubId + "/posts/" + postId)
                .with(authentication(oauthToken(OWNER_EMAIL))))
            .andExpect(status().isNoContent());

        assertThat(countPostRows(postId)).isZero();
    }

    @Test
    void nonAuthorNonPresidentCannotDeleteAPost() throws Exception {
        long clubId = createClub();
        long authorUid = insertOauthUser(MEMBER_EMAIL, "Ada Lovelace");
        insertOauthUser(OUTSIDER_EMAIL, "Outsider");
        addMember(clubId, authorUid, "member");
        String imageUrl = writeStoredPhoto();
        long postId = insertPost(clubId, authorUid, imageUrl);
        Path storedFile = uploadDir.resolve(imageUrl.substring("/uploads/".length()));

        mockMvc.perform(delete("/api/clubs/" + clubId + "/posts/" + postId)
                .with(authentication(oauthToken(OUTSIDER_EMAIL))))
            .andExpect(status().isForbidden());

        assertThat(countPostRows(postId)).isEqualTo(1);
        assertThat(Files.exists(storedFile)).isTrue();
    }

    // The transaction-boundary contract this exists for: if the delete's own transaction rolls
    // back for any reason, the row must still exist and the file must still be on disk --
    // afterCommit must never have run.
    @Test
    void aRolledBackDeleteLeavesBothTheRowAndTheFileIntact() throws Exception {
        long clubId = createClub();
        long authorUid = insertOauthUser(MEMBER_EMAIL, "Ada Lovelace");
        addMember(clubId, authorUid, "member");
        String imageUrl = writeStoredPhoto();
        long postId = insertPost(clubId, authorUid, imageUrl);
        Path storedFile = uploadDir.resolve(imageUrl.substring("/uploads/".length()));

        // Simulates a bug elsewhere in the same transactional method surfacing after the DELETE
        // statement itself has already run: physically deletes the row (the exact statement
        // ClubPostMapper.xml's delete defines) on the same transactional connection, then
        // reports zero rows affected, which ClubPostWriter#delete treats as a failure and
        // throws -- rolling back everything, including the DELETE that already ran.
        doAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            jdbcTemplate.update("DELETE FROM club_post WHERE id = ?", id);
            return 0;
        }).when(clubPostMapperSpy).delete(anyLong());

        mockMvc.perform(delete("/api/clubs/" + clubId + "/posts/" + postId)
                .with(authentication(oauthToken(MEMBER_EMAIL))))
            .andExpect(status().is5xxServerError());

        assertThat(countPostRows(postId)).isEqualTo(1);
        assertThat(Files.exists(storedFile)).isTrue();
    }

    @Test
    void deletingAnUnknownPostReturnsNotFound() throws Exception {
        long clubId = createClub();
        long authorUid = insertOauthUser(MEMBER_EMAIL, "Ada Lovelace");
        addMember(clubId, authorUid, "member");

        mockMvc.perform(delete("/api/clubs/" + clubId + "/posts/999999")
                .with(authentication(oauthToken(MEMBER_EMAIL))))
            .andExpect(status().isNotFound());
    }

    // ---- Comments ----

    @Test
    void memberCanPostACommentAndAnyoneCanReadItBackWithNoPii() throws Exception {
        long clubId = createClub();
        long authorUid = insertOauthUser(MEMBER_EMAIL, "Ada Lovelace");
        addMember(clubId, authorUid, "member");
        long postId = insertPost(clubId, authorUid, writeStoredPhoto());

        mockMvc.perform(post("/api/clubs/" + clubId + "/posts/" + postId + "/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"  Great meeting!  \"}")
                .with(authentication(oauthToken(MEMBER_EMAIL))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.body").value("Great meeting!"));

        String responseBody = mockMvc.perform(get("/api/clubs/" + clubId + "/posts/" + postId + "/comments"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
            .andExpect(jsonPath("$[0].body").value("Great meeting!"))
            .andExpect(jsonPath("$[0].authorDisplayName").value("Ada Lovelace"))
            .andReturn().getResponse().getContentAsString();

        assertThat(responseBody).doesNotContain("\"email\"");
        assertThat(responseBody).doesNotContain("@");
    }

    // Mirrors ClubPostFeedIntegrationTest's equivalent post-level test: a comment posted "just
    // now" (DB default CURRENT_TIMESTAMP, no literal override) must serialize as an instant
    // close to the real Instant.now(), never shifted hours away.
    //
    // No TimeZone.setDefault() here: this property holds regardless of timezone by
    // construction (UNIX_TIMESTAMP inverts CURRENT_TIMESTAMP within the same session no matter
    // which zone is actually in effect), and see ClubPostMapperTest's literal round-trip tests
    // for why an in-test override would not reliably change H2's already-established session
    // zone anyway. Running this suite under both this repo's normal non-UTC local/dev
    // environment and an explicit `TZ=UTC` (CI-like) process both exercise this meaningfully.
    @Test
    void justPostedCommentsCreatedAtIsCloseToNow() throws Exception {
        long clubId = createClub();
        long authorUid = insertOauthUser(MEMBER_EMAIL, "Ada Lovelace");
        addMember(clubId, authorUid, "member");
        long postId = insertPost(clubId, authorUid, writeStoredPhoto());
        insertComment(postId, authorUid, "Fresh comment");

        String responseBody = mockMvc.perform(get("/api/clubs/" + clubId + "/posts/" + postId + "/comments"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        String createdAt = extractJsonStringField(responseBody, "createdAt");
        assertThat(createdAt).endsWith("Z");
        assertThat(Instant.parse(createdAt)).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS));
    }

    @Test
    void nonMemberCannotPostAComment() throws Exception {
        long clubId = createClub();
        long authorUid = insertOauthUser(MEMBER_EMAIL, "Ada Lovelace");
        insertOauthUser(OUTSIDER_EMAIL, "Outsider");
        addMember(clubId, authorUid, "member");
        long postId = insertPost(clubId, authorUid, writeStoredPhoto());

        mockMvc.perform(post("/api/clubs/" + clubId + "/posts/" + postId + "/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"Nice!\"}")
                .with(authentication(oauthToken(OUTSIDER_EMAIL))))
            .andExpect(status().isForbidden());
    }

    @Test
    void loggedOutVisitorCanReadCommentsButCannotPost() throws Exception {
        long clubId = createClub();
        long authorUid = insertOauthUser(MEMBER_EMAIL, "Ada Lovelace");
        addMember(clubId, authorUid, "member");
        long postId = insertPost(clubId, authorUid, writeStoredPhoto());
        insertComment(postId, authorUid, "Nice!");

        mockMvc.perform(get("/api/clubs/" + clubId + "/posts/" + postId + "/comments"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].body").value("Nice!"));

        mockMvc.perform(post("/api/clubs/" + clubId + "/posts/" + postId + "/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"Nice!\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void aCommentOverThreeHundredCharactersReturnsBadRequest() throws Exception {
        long clubId = createClub();
        long authorUid = insertOauthUser(MEMBER_EMAIL, "Ada Lovelace");
        addMember(clubId, authorUid, "member");
        long postId = insertPost(clubId, authorUid, writeStoredPhoto());
        String tooLong = "x".repeat(301);

        mockMvc.perform(post("/api/clubs/" + clubId + "/posts/" + postId + "/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"" + tooLong + "\"}")
                .with(authentication(oauthToken(MEMBER_EMAIL))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void commentingOnANonExistentPostReturnsNotFound() throws Exception {
        long clubId = createClub();
        long authorUid = insertOauthUser(MEMBER_EMAIL, "Ada Lovelace");
        addMember(clubId, authorUid, "member");

        mockMvc.perform(post("/api/clubs/" + clubId + "/posts/999999/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"Nice!\"}")
                .with(authentication(oauthToken(MEMBER_EMAIL))))
            .andExpect(status().isNotFound());
    }

    @Test
    void commentAuthorCanDeleteTheirOwnComment() throws Exception {
        long clubId = createClub();
        long authorUid = insertOauthUser(MEMBER_EMAIL, "Ada Lovelace");
        addMember(clubId, authorUid, "member");
        long postId = insertPost(clubId, authorUid, writeStoredPhoto());
        long commentId = insertComment(postId, authorUid, "Nice!");

        mockMvc.perform(delete("/api/clubs/" + clubId + "/posts/" + postId + "/comments/" + commentId)
                .with(authentication(oauthToken(MEMBER_EMAIL))))
            .andExpect(status().isNoContent());

        assertThat(countCommentRows(postId)).isZero();
    }

    @Test
    void presidentCanDeleteAnotherMembersComment() throws Exception {
        long clubId = createClub();
        long authorUid = insertOauthUser(MEMBER_EMAIL, "Ada Lovelace");
        long presidentUid = insertOauthUser(PRESIDENT_EMAIL, "President");
        addMember(clubId, authorUid, "member");
        addMember(clubId, presidentUid, "president");
        long postId = insertPost(clubId, authorUid, writeStoredPhoto());
        long commentId = insertComment(postId, authorUid, "Nice!");

        mockMvc.perform(delete("/api/clubs/" + clubId + "/posts/" + postId + "/comments/" + commentId)
                .with(authentication(oauthToken(PRESIDENT_EMAIL))))
            .andExpect(status().isNoContent());

        assertThat(countCommentRows(postId)).isZero();
    }

    @Test
    void platformOwnerCanDeleteAnyonesComment() throws Exception {
        long clubId = createClub();
        long authorUid = insertOauthUser(MEMBER_EMAIL, "Ada Lovelace");
        insertOauthUser(OWNER_EMAIL, "Owner");
        addMember(clubId, authorUid, "member");
        long postId = insertPost(clubId, authorUid, writeStoredPhoto());
        long commentId = insertComment(postId, authorUid, "Nice!");

        mockMvc.perform(delete("/api/clubs/" + clubId + "/posts/" + postId + "/comments/" + commentId)
                .with(authentication(oauthToken(OWNER_EMAIL))))
            .andExpect(status().isNoContent());

        assertThat(countCommentRows(postId)).isZero();
    }

    @Test
    void nonAuthorNonPresidentCannotDeleteAComment() throws Exception {
        long clubId = createClub();
        long authorUid = insertOauthUser(MEMBER_EMAIL, "Ada Lovelace");
        insertOauthUser(OUTSIDER_EMAIL, "Outsider");
        addMember(clubId, authorUid, "member");
        long postId = insertPost(clubId, authorUid, writeStoredPhoto());
        long commentId = insertComment(postId, authorUid, "Nice!");

        mockMvc.perform(delete("/api/clubs/" + clubId + "/posts/" + postId + "/comments/" + commentId)
                .with(authentication(oauthToken(OUTSIDER_EMAIL))))
            .andExpect(status().isForbidden());

        assertThat(countCommentRows(postId)).isEqualTo(1);
    }
}
