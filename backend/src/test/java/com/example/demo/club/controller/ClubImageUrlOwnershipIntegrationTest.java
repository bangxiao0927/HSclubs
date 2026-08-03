package com.example.demo.club.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end regression coverage (real DB, real security filter chain, real
 * {@link com.example.demo.club.service.ImageStorageService} bean) for the fix closing the
 * ownership exploit at its root: a club manager including an {@code imageUrl} field in an
 * ordinary {@code PUT /api/clubs/{id}} body -- e.g. another club's real, guessable
 * {@code /uploads/club-posts/<uuid>.jpg} path -- must never change what is actually stored,
 * while the dedicated, authenticated upload endpoint still works.
 */
@SpringBootTest(properties =
    "spring.datasource.url=jdbc:h2:mem:club_image_url_ownership_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@Sql(scripts = "classpath:schema.sql")
class ClubImageUrlOwnershipIntegrationTest {

    // Matches src/test/resources/application.yaml's app.security.owner-emails.
    private static final String OWNER_EMAIL = "test-owner@example.com";
    private static final String ORIGINAL_IMAGE_URL = "/uploads/club-posts/original-uuid.jpg";

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

    @Test
    void putUpdateIgnoresACallerSuppliedImageUrlEvenAsPlatformOwner() throws Exception {
        insertClub(1L, "Chess Club", ORIGINAL_IMAGE_URL);

        String requestBody = """
            {"name": "Chess Club (renamed)", "category": "STEM & Innovation",
             "imageUrl": "/uploads/club-posts/attacker-guessed-uuid.jpg"}
            """;

        mockMvc.perform(put("/api/clubs/1")
                .with(authentication(oauthToken(OWNER_EMAIL)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Chess Club (renamed)"))
            .andExpect(jsonPath("$.imageUrl").value(ORIGINAL_IMAGE_URL));

        assertThat(storedImageUrl(1L)).isEqualTo(ORIGINAL_IMAGE_URL);
        assertThat(storedName(1L)).isEqualTo("Chess Club (renamed)");
    }

    @Test
    void dedicatedImageUploadEndpointStillChangesTheStoredImageUrl() throws Exception {
        insertClub(2L, "Robotics", ORIGINAL_IMAGE_URL);

        mockMvc.perform(multipart("/api/clubs/2/image")
                .file(realPngFile())
                .with(authentication(oauthToken(OWNER_EMAIL))))
            .andExpect(status().isOk());

        String newImageUrl = storedImageUrl(2L);
        assertThat(newImageUrl).isNotEqualTo(ORIGINAL_IMAGE_URL);
        assertThat(newImageUrl).startsWith("/uploads/club-posts/").endsWith(".jpg");
    }

    private void insertClub(long id, String name, String imageUrl) {
        jdbcTemplate.update(
            "MERGE INTO club_category (cate_name) KEY (cate_name) VALUES ('STEM & Innovation')");
        jdbcTemplate.update(
            "INSERT INTO clubs (id, name, category, status, visibility, image_url) "
                + "VALUES (?, ?, 'STEM & Innovation', 'active', 'public', ?)",
            id, name, imageUrl);
    }

    private String storedImageUrl(long id) {
        return jdbcTemplate.queryForObject("SELECT image_url FROM clubs WHERE id = ?", String.class, id);
    }

    private String storedName(long id) {
        return jdbcTemplate.queryForObject("SELECT name FROM clubs WHERE id = ?", String.class, id);
    }

    private static MockMultipartFile realPngFile() throws Exception {
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.GREEN);
        graphics.fillRect(0, 0, 10, 10);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return new MockMultipartFile("file", "avatar.png", "image/png", out.toByteArray());
    }

    private OAuth2AuthenticationToken oauthToken(String email) {
        OAuth2User principal = new DefaultOAuth2User(
            List.of(() -> "ROLE_USER"),
            Map.of("email", email, "sub", email),
            "sub");
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
    }
}
