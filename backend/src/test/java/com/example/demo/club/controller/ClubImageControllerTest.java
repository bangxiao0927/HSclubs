package com.example.demo.club.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.auth.config.SecurityProperties;
import com.example.demo.club.model.Club;
import com.example.demo.club.service.ClubService;
import com.example.demo.school.model.School;
import com.example.demo.school.service.SchoolUserService;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ClubImageControllerTest {

    private static final String SCHOOL_SLUG = "mvhs";
    private static final Long SCHOOL_ID = 1L;

    @TempDir
    Path uploadDir;

    @Mock
    private ClubService clubService;

    @Mock
    private SchoolUserService schoolUserService;

    private ClubImageController controller;

    @BeforeEach
    void setUp() {
        SecurityProperties securityProperties = new SecurityProperties();
        securityProperties.setOwnerEmails(List.of("owner@example.com"));
        controller = new ClubImageController(clubService, schoolUserService, securityProperties, uploadDir.toString());

        School school = new School();
        school.setId(SCHOOL_ID);
        school.setSlug(SCHOOL_SLUG);
        school.setSchoolName("Mountain View High School");
        school.setStatus("active");
        lenient().when(clubService.resolveSchool(SCHOOL_SLUG)).thenReturn(school);
        lenient().when(clubService.resolveOauthUserId("student@example.com")).thenReturn(100L);
        lenient().when(clubService.resolveOauthUserId("admin@example.com")).thenReturn(200L);
    }

    @Test
    void uploadShouldRequireAuthentication() {
        try {
            controller.uploadImage(SCHOOL_SLUG, "1", pngFile(), null);
            assertThat(true).as("Expected 401").isFalse();
        } catch (ResponseStatusException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(401);
        }
        verify(clubService, never()).updateInSchool(any(), any(), any());
    }

    @Test
    void uploadShouldRejectStudentWithoutManageAccess() {
        Club club = club(false);
        when(clubService.findBySchoolAndClubId(SCHOOL_ID, 1L, "student@example.com")).thenReturn(club);
        when(schoolUserService.isSchoolAdmin(SCHOOL_ID, 100L)).thenReturn(false);

        try {
            controller.uploadImage(SCHOOL_SLUG, "1", pngFile(), createAuth("student@example.com"));
            assertThat(true).as("Expected 403").isFalse();
        } catch (ResponseStatusException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }
        verify(clubService, never()).updateInSchool(any(), any(), any());
    }

    @Test
    void uploadShouldAllowPresidentForOwnClub() {
        Club club = club(true);
        when(clubService.findBySchoolAndClubId(SCHOOL_ID, 1L, "president@example.com")).thenReturn(club);
        when(clubService.updateInSchool(eq(SCHOOL_ID), eq(1L), eq(club))).thenReturn(club);

        Map<String, String> result = controller.uploadImage(
            SCHOOL_SLUG,
            "1",
            pngFile(),
            createAuth("president@example.com"));

        assertThat(result.get("imageUrl")).startsWith("/uploads/").endsWith(".png");
        assertThat(club.getImageUrl()).isEqualTo(result.get("imageUrl"));
        verify(clubService).updateInSchool(SCHOOL_ID, 1L, club);
    }

    @Test
    void uploadShouldAllowSchoolAdmin() {
        Club club = club(false);
        when(clubService.findBySchoolAndClubId(SCHOOL_ID, 1L, "admin@example.com")).thenReturn(club);
        when(schoolUserService.isSchoolAdmin(SCHOOL_ID, 200L)).thenReturn(true);
        when(clubService.updateInSchool(eq(SCHOOL_ID), eq(1L), eq(club))).thenReturn(club);

        Map<String, String> result = controller.uploadImage(
            SCHOOL_SLUG,
            "1",
            pngFile(),
            createAuth("admin@example.com"));

        assertThat(result.get("imageUrl")).startsWith("/uploads/").endsWith(".png");
        verify(clubService).updateInSchool(SCHOOL_ID, 1L, club);
    }

    @Test
    void uploadShouldRejectUnsupportedContentType() {
        Club club = club(true);
        when(clubService.findBySchoolAndClubId(SCHOOL_ID, 1L, "president@example.com")).thenReturn(club);
        MockMultipartFile textFile = new MockMultipartFile(
            "file",
            "note.txt",
            "text/plain",
            "not an image".getBytes());

        try {
            controller.uploadImage(SCHOOL_SLUG, "1", textFile, createAuth("president@example.com"));
            assertThat(true).as("Expected 400").isFalse();
        } catch (ResponseStatusException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(400);
        }
        verify(clubService, never()).updateInSchool(any(), any(), any());
    }

    private Club club(boolean canManage) {
        Club club = new Club();
        club.setId(1L);
        club.setName("Robotics");
        club.setSchoolId(SCHOOL_ID);
        club.setCanManage(canManage);
        return club;
    }

    private MockMultipartFile pngFile() {
        return new MockMultipartFile(
            "file",
            "club.png",
            "image/png",
            new byte[] {1, 2, 3});
    }

    private Authentication createAuth(String email) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("email", email);
        attributes.put("sub", email.replace("@", "-id"));
        OAuth2User principal = new DefaultOAuth2User(
            Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
            attributes,
            "sub");
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
    }
}
