package com.example.demo.club.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.auth.config.SecurityProperties;
import com.example.demo.club.model.Club;
import com.example.demo.club.service.ClubService;
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
        when(clubService.findById(eq(1L), any())).thenReturn(club);

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
        when(clubService.findById(eq(1L), any())).thenReturn(club);
        when(imageStorageService.store(any())).thenReturn("/uploads/club-posts/generated-uuid.jpg");

        MockMultipartFile file = realPngFile();

        mockMvc.perform(multipart("/api/clubs/1/image")
                .file(file)
                .principal(oauthToken(OWNER_EMAIL)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.imageUrl").value("/uploads/club-posts/generated-uuid.jpg"));
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
