package com.example.demo.club.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.auth.config.SecurityProperties;
import com.example.demo.club.model.Club;
import com.example.demo.club.service.ClubService;
import com.example.demo.club.service.ImageProcessingUnavailableException;
import com.example.demo.club.service.ImageStorageService;
import com.example.demo.security.AuthenticatedUserResolver;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = ClubImageController.class,
    excludeAutoConfiguration = {
        OAuth2ClientAutoConfiguration.class,
        OAuth2ClientWebSecurityAutoConfiguration.class
    })
@AutoConfigureMockMvc(addFilters = false)
class ClubImageControllerTest {

    private static final String OWNER_EMAIL = "test-owner@example.com";
    private static final String STUDENT_EMAIL = "student@example.com";

    @TestConfiguration
    static class TestConfig {
        @Bean
        SecurityProperties securityProperties() {
            return new SecurityProperties();
        }

        @Bean
        AuthenticatedUserResolver authenticatedUserResolver(SecurityProperties securityProperties) {
            return new AuthenticatedUserResolver(securityProperties);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClubService clubService;

    @MockitoBean
    private ImageStorageService imageStorageService;

    @Test
    void uploadImageRequiresManageAccess() throws Exception {
        Club club = new Club();
        club.setId(1L);
        club.setCanManage(false);
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(club);

        MockMultipartFile file = realPngFile();

        mockMvc.perform(multipart("/api/clubs/1/image")
                .file(file)
                .principal(oauthToken(STUDENT_EMAIL)))
            .andExpect(status().isForbidden());
    }

    @Test
    void uploadImageSucceedsForPlatformOwner() throws Exception {
        Club club = new Club();
        club.setId(1L);
        club.setCanManage(false);
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(club);
        when(imageStorageService.store(any())).thenReturn("/uploads/club-posts/generated-uuid.jpg");

        MockMultipartFile file = realPngFile();

        mockMvc.perform(multipart("/api/clubs/1/image")
                .file(file)
                .principal(oauthToken(OWNER_EMAIL)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.imageUrl").value("/uploads/club-posts/generated-uuid.jpg"));

        // The upload path must go through the dedicated updateImageUrl() method (backed by a
        // column-scoped SQL statement -- see ClubMapperTest), never the general update(), which
        // no longer writes image_url at all and would silently drop this change if used here.
        verify(clubService).updateImageUrl(1L, "/uploads/club-posts/generated-uuid.jpg");
        verify(clubService, never()).update(any(), any());
    }

    // Regression coverage for the controller side of the corrupt/truncated-image fix:
    // ImageStorageService.store() rejects unreadable images with IllegalArgumentException (see
    // ImageStorageServiceTest's truncated PNG/JPEG cases), and the controller must translate
    // that into a 400, never let it escape as an uncaught 500.
    @Test
    void uploadImageReturnsBadRequestWhenTheServiceRejectsAnUnreadableFile() throws Exception {
        Club club = new Club();
        club.setId(1L);
        club.setCanManage(false);
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(club);
        when(imageStorageService.store(any())).thenThrow(new IllegalArgumentException("Unreadable image"));

        MockMultipartFile file = realPngFile();

        mockMvc.perform(multipart("/api/clubs/1/image")
                .file(file)
                .principal(oauthToken(OWNER_EMAIL)))
            .andExpect(status().isBadRequest());
    }

    // The corrupt-image fix must not blur the line between bad client input and a genuine
    // server-side failure: an IOException from store() (e.g. a real disk write failure) is
    // still a 500, distinct from the IllegalArgumentException/400 case above.
    @Test
    void uploadImageReturnsServerErrorWhenStorageFailsWithAnIOException() throws Exception {
        Club club = new Club();
        club.setId(1L);
        club.setCanManage(false);
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(club);
        when(imageStorageService.store(any())).thenThrow(new IOException("disk full"));

        MockMultipartFile file = realPngFile();

        mockMvc.perform(multipart("/api/clubs/1/image")
                .file(file)
                .principal(oauthToken(OWNER_EMAIL)))
            .andExpect(status().isInternalServerError());
    }

    // The decode-capacity fix (round 3): too many concurrent decodes is a transient server
    // capacity limit, not a problem with this particular file, so it must be a 503 -- distinct
    // from both the 400 (bad file) and 500 (storage failure) cases above.
    @Test
    void uploadImageReturnsServiceUnavailableWhenDecodeCapacityIsExhausted() throws Exception {
        Club club = new Club();
        club.setId(1L);
        club.setCanManage(false);
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(club);
        when(imageStorageService.store(any())).thenThrow(
            new ImageProcessingUnavailableException("Too many images are being processed right now."));

        MockMultipartFile file = realPngFile();

        mockMvc.perform(multipart("/api/clubs/1/image")
                .file(file)
                .principal(oauthToken(OWNER_EMAIL)))
            .andExpect(status().isServiceUnavailable());
    }

    private static MockMultipartFile realPngFile() throws IOException {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x00FF00);
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
