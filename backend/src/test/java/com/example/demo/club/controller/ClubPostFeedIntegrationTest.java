package com.example.demo.club.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Full-stack acceptance coverage for #78: real DB, real security filter chain, real
 * {@link com.example.demo.club.service.ImageStorageService}. Unit/mapper/service/controller
 * tests elsewhere cover each layer in isolation; this proves the whole request actually works
 * end to end for every acceptance criterion that spans layers.
 */
@SpringBootTest(properties =
    "spring.datasource.url=jdbc:h2:mem:club_post_feed_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@Sql(scripts = "classpath:schema.sql")
class ClubPostFeedIntegrationTest {

    private static final String MEMBER_EMAIL = "member@example.com";
    private static final String OUTSIDER_EMAIL = "outsider@example.com";

    @TempDir
    static Path uploadDir;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void uploadDir(DynamicPropertyRegistry registry) {
        registry.add("app.upload.dir", () -> uploadDir.toString());
    }

    // @Sql's schema.sql only CREATE TABLE IF NOT EXISTS, so it does not reset data between test
    // methods sharing this class's Spring context; cascade deletes clear club_member/club_post/
    // membership_requests along with their owning rows.
    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM clubs");
        jdbcTemplate.update("DELETE FROM oauth_users");
    }

    private long createActiveClubWithAMember() {
        jdbcTemplate.update("MERGE INTO club_category (cate_name) KEY (cate_name) VALUES ('STEM & Innovation')");
        long clubId = insertClub("Chess Club", "active");
        long memberUid = insertOauthUser(MEMBER_EMAIL, "Ada Lovelace", "/uploads/avatar-cache/ada.jpg");
        insertOauthUser(OUTSIDER_EMAIL, "Outsider", null);
        jdbcTemplate.update(
            "INSERT INTO club_member (club_id, oauth_user_id, role_name) VALUES (?, ?, 'member')",
            clubId, memberUid);
        return clubId;
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

    @Test
    void memberCanPublishAndTheStoredFileIsShrunkBelowFourHundredKilobytesWithNoExif() throws Exception {
        long clubId = createActiveClubWithAMember();
        byte[] fourMegabyteJpeg = fourMegabyteJpegWithExif();

        String responseBody = mockMvc.perform(multipart("/api/clubs/" + clubId + "/posts")
                .file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", fourMegabyteJpeg))
                .param("title", "Meeting recap")
                .with(authentication(oauthToken(MEMBER_EMAIL))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value("Meeting recap"))
            .andReturn().getResponse().getContentAsString();

        String imageUrl = extractJsonStringField(responseBody, "imageUrl");
        Path storedFile = uploadDir.resolve(imageUrl.substring("/uploads/".length()));
        assertThat(Files.exists(storedFile)).isTrue();
        byte[] storedBytes = Files.readAllBytes(storedFile);
        assertThat(storedBytes.length).isLessThan(400 * 1024);
        assertThat(containsApp1ExifMarker(storedBytes)).isFalse();
    }

    @Test
    void nonMemberCannotPublish() throws Exception {
        long clubId = createActiveClubWithAMember();

        mockMvc.perform(multipart("/api/clubs/" + clubId + "/posts")
                .file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", tinyJpeg()))
                .param("title", "Meeting recap")
                .with(authentication(oauthToken(OUTSIDER_EMAIL))))
            .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCallerCannotPublish() throws Exception {
        long clubId = createActiveClubWithAMember();

        mockMvc.perform(multipart("/api/clubs/" + clubId + "/posts")
                .file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", tinyJpeg()))
                .param("title", "Meeting recap"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void loggedOutVisitorCanReadTheFeed() throws Exception {
        long clubId = createActiveClubWithAMember();
        insertPost(clubId, "Post 1", null);

        mockMvc.perform(get("/api/clubs/" + clubId + "/posts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", Matchers.hasSize(1)))
            .andExpect(jsonPath("$.items[0].authorDisplayName").value("Ada Lovelace"))
            .andExpect(jsonPath("$.items[0].authorAvatarUrl").value("/uploads/avatar-cache/ada.jpg"));
    }

    @Test
    void pinnedPostsSortToTheFrontExactlyOnce() throws Exception {
        long clubId = createActiveClubWithAMember();
        long firstId = insertPost(clubId, "First", "2024-01-01 10:00:00");
        long secondId = insertPost(clubId, "Second", "2024-01-02 10:00:00");
        long thirdId = insertPost(clubId, "Third pinned", "2023-06-01 10:00:00");
        jdbcTemplate.update("UPDATE club_post SET pinned_at = '2024-06-01 00:00:00' WHERE id = ?", thirdId);

        String responseBody = mockMvc.perform(get("/api/clubs/" + clubId + "/posts"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        List<String> ids = extractJsonArrayField(responseBody, "id");
        assertThat(ids).containsExactly(String.valueOf(thirdId), String.valueOf(secondId), String.valueOf(firstId));
        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    void feedOfANonActiveClubReturnsNotFoundToANonMemberAndOkToAMember() throws Exception {
        jdbcTemplate.update("MERGE INTO club_category (cate_name) KEY (cate_name) VALUES ('STEM & Innovation')");
        long clubId = insertClub("Pending Club", "pending");
        long memberUid = insertOauthUser(MEMBER_EMAIL, "Ada Lovelace", null);
        insertOauthUser(OUTSIDER_EMAIL, "Outsider", null);
        jdbcTemplate.update(
            "INSERT INTO club_member (club_id, oauth_user_id, role_name) VALUES (?, ?, 'member')",
            clubId, memberUid);

        mockMvc.perform(get("/api/clubs/" + clubId + "/posts"))
            .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/clubs/" + clubId + "/posts")
                .with(authentication(oauthToken(OUTSIDER_EMAIL))))
            .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/clubs/" + clubId + "/posts")
                .with(authentication(oauthToken(MEMBER_EMAIL))))
            .andExpect(status().isOk());
    }

    @Test
    void requestingSizeOneThousandEchoesOneHundred() throws Exception {
        long clubId = createActiveClubWithAMember();
        insertPost(clubId, "Post 1", null);

        mockMvc.perform(get("/api/clubs/" + clubId + "/posts").param("size", "1000"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void totalMatchesTheNumberOfPostsForTheClub() throws Exception {
        long clubId = createActiveClubWithAMember();
        insertPost(clubId, "Post 1", null);
        insertPost(clubId, "Post 2", null);
        insertPost(clubId, "Post 3", null);

        mockMvc.perform(get("/api/clubs/" + clubId + "/posts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(3))
            .andExpect(jsonPath("$.items", Matchers.hasSize(3)));
    }

    @Test
    void publicFeedResponseContainsNoEmailKeyOrAtSubstring() throws Exception {
        long clubId = createActiveClubWithAMember();
        insertPost(clubId, "Post 1", null);

        String responseBody = mockMvc.perform(get("/api/clubs/" + clubId + "/posts"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(responseBody).doesNotContain("\"email\"");
        assertThat(responseBody).doesNotContain("@");
    }

    private long insertPost(long clubId, String title, String createdAt) {
        long authorUid = jdbcTemplate.queryForObject(
            "SELECT oauth_user_id FROM club_member WHERE club_id = ? LIMIT 1", Long.class, clubId);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        if (createdAt == null) {
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
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO club_post (club_id, author_oauth_user_id, title, image_url, created_at) "
                    + "VALUES (?, ?, ?, '/uploads/club-posts/seed.jpg', ?)",
                Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, clubId);
            statement.setLong(2, authorUid);
            statement.setString(3, title);
            statement.setTimestamp(4, Timestamp.valueOf(createdAt));
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

    private static String extractJsonStringField(String json, String field) {
        String needle = "\"" + field + "\":\"";
        int start = json.indexOf(needle) + needle.length();
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }

    private static List<String> extractJsonArrayField(String json, String field) {
        List<String> values = new ArrayList<>();
        String needle = "\"" + field + "\":";
        int index = 0;
        while (true) {
            int at = json.indexOf(needle, index);
            if (at < 0) {
                break;
            }
            int start = at + needle.length();
            int end = start;
            while (end < json.length() && Character.isDigit(json.charAt(end))) {
                end++;
            }
            values.add(json.substring(start, end));
            index = end;
        }
        return values;
    }

    private static boolean containsApp1ExifMarker(byte[] bytes) {
        for (int i = 0; i < bytes.length - 1; i++) {
            if ((bytes[i] & 0xFF) == 0xFF && (bytes[i + 1] & 0xFF) == 0xE1) {
                return true;
            }
        }
        return false;
    }

    private static byte[] tinyJpeg() throws Exception {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    // A large (~4MB), fine-grained-but-not-uniformly-random JPEG: real camera photos are large
    // because of sensor-level detail across a big canvas, which mostly averages away once
    // Thumbnailator downscales to 1600x1600 -- unlike pure per-pixel noise, which doesn't
    // compress any better after resampling and would fail the "under 400KB" expectation.
    // Embeds a minimal Exif APP1 segment so "carries no EXIF" is a meaningful assertion rather
    // than trivially true for any input.
    private static byte[] fourMegabyteJpegWithExif() throws Exception {
        int size = 4000;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(3);
        int[] row = new int[size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int base = 120 + (int) (20 * Math.sin(x / 50.0) + 20 * Math.cos(y / 70.0));
                int noise = random.nextInt(30) - 15;
                int v = Math.max(0, Math.min(255, base + noise));
                row[x] = (v << 16) | (v << 8) | v;
            }
            image.setRGB(0, y, size, 1, row, 0, size);
        }

        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        try {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.85f);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(image, null, null), param);
            }
            byte[] plain = out.toByteArray();
            byte[] app1 = buildMinimalExifApp1Segment();
            ByteArrayOutputStream combined = new ByteArrayOutputStream();
            combined.write(plain, 0, 2);
            combined.write(app1);
            combined.write(plain, 2, plain.length - 2);
            return combined.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    private static byte[] buildMinimalExifApp1Segment() {
        // TIFF header + an empty IFD0 (0 entries) -- enough for the sniff/header-read/full-decode
        // pipeline to see a real Exif segment without needing a specific tag for this test.
        ByteBuffer tiff = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN);
        tiff.put((byte) 'I').put((byte) 'I');
        tiff.putShort((short) 42);
        tiff.putInt(8);
        tiff.putShort((short) 0); // 0 IFD0 entries

        byte[] exifHeader = {'E', 'x', 'i', 'f', 0, 0};
        byte[] tiffBytes = tiff.array();
        int payloadLength = exifHeader.length + tiffBytes.length;
        int segmentLength = 2 + payloadLength;

        ByteBuffer buffer = ByteBuffer.allocate(2 + 2 + payloadLength).order(ByteOrder.BIG_ENDIAN);
        buffer.put((byte) 0xFF).put((byte) 0xE1);
        buffer.putShort((short) segmentLength);
        buffer.put(exifHeader);
        buffer.put(tiffBytes);
        return buffer.array();
    }

    private OAuth2AuthenticationToken oauthToken(String email) {
        OAuth2User principal = new DefaultOAuth2User(
            List.of(() -> "ROLE_USER"),
            Map.of("email", email, "sub", email),
            "sub");
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
    }
}
