package com.example.demo.club.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

@ExtendWith(MockitoExtension.class)
class SchoolClubControllerTest {

    @Mock
    private ClubService clubService;

    @Mock
    private SchoolUserService schoolUserService;

    private SecurityProperties securityProperties;
    private SchoolClubController controller;

    private static final String SCHOOL_SLUG = "mvhs";
    private static final Long SCHOOL_ID = 1L;

    @BeforeEach
    void setUp() {
        securityProperties = new SecurityProperties();
        securityProperties.setOwnerEmails(List.of("owner@example.com"));
        controller = new SchoolClubController(clubService, schoolUserService, securityProperties);

        School school = new School();
        school.setId(SCHOOL_ID);
        school.setSlug(SCHOOL_SLUG);
        school.setSchoolName("Mountain View High School");
        school.setStatus("active");

        lenient().when(clubService.resolveSchool(eq(SCHOOL_SLUG))).thenReturn(school);
        lenient().when(clubService.resolveOauthUserId(eq("student@example.com"))).thenReturn(100L);
        lenient().when(clubService.resolveOauthUserId(eq("admin@example.com"))).thenReturn(200L);
    }

    @Test
    void listShouldReturnClubsForSchool() {
        Club club = new Club();
        club.setId(1L);
        club.setName("Robotics");
        when(clubService.findAllBySchoolIdPaginated(eq(SCHOOL_ID), eq(0), eq(50))).thenReturn(List.of(club));

        List<Club> result = controller.list(SCHOOL_SLUG, 0, 50);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Robotics");
    }

    @Test
    void listShouldReturnEmptyForSchoolWithNoClubs() {
        when(clubService.findAllBySchoolIdPaginated(eq(SCHOOL_ID), eq(0), eq(50))).thenReturn(Collections.emptyList());

        List<Club> result = controller.list(SCHOOL_SLUG, 0, 50);

        assertThat(result).isEmpty();
    }

    @Test
    void applyShouldRequireAuthentication() {
        try {
            controller.applyToClub(SCHOOL_SLUG, "1", null);
            assertThat(true).as("Expected exception").isFalse();
        } catch (org.springframework.web.server.ResponseStatusException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(401);
        }
    }

    @Test
    void listMembersShouldRequireManageAccess() {
        Authentication auth = createAuth("student@example.com");
        Club club = new Club();
        club.setId(1L);
        club.setCanManage(false);
        when(clubService.findBySchoolAndClubId(eq(SCHOOL_ID), eq(1L), eq("student@example.com"))).thenReturn(club);
        when(schoolUserService.isSchoolAdmin(eq(SCHOOL_ID), eq(100L))).thenReturn(false);

        try {
            controller.listMembers(SCHOOL_SLUG, "1", auth);
            assertThat(true).as("Expected 403").isFalse();
        } catch (org.springframework.web.server.ResponseStatusException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }
    }

    @Test
    void createClubShouldRejectNonAdmin() {
        Authentication auth = createAuth("student@example.com");
        when(schoolUserService.isSchoolAdmin(eq(SCHOOL_ID), eq(100L))).thenReturn(false);

        try {
            controller.create(SCHOOL_SLUG, new Club(), auth);
            assertThat(true).as("Expected 403").isFalse();
        } catch (org.springframework.web.server.ResponseStatusException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }
    }

    @Test
    void createClubShouldAllowPlatformOwner() {
        Authentication auth = createAuth("owner@example.com");

        Club input = new Club();
        input.setName("New Club");
        input.setCategory("STEM & Innovation");
        Club created = new Club();
        created.setId(99L);
        created.setName("New Club");
        when(clubService.createInSchool(eq(SCHOOL_ID), any(Club.class))).thenReturn(created);

        Club result = controller.create(SCHOOL_SLUG, input, auth);

        assertThat(result.getId()).isEqualTo(99L);
        assertThat(result.getName()).isEqualTo("New Club");
        verify(clubService).createInSchool(eq(SCHOOL_ID), any(Club.class));
    }

    @Test
    void createClubShouldAllowSchoolAdmin() {
        Authentication auth = createAuth("admin@example.com");
        when(schoolUserService.isSchoolAdmin(eq(SCHOOL_ID), eq(200L))).thenReturn(true);

        Club input = new Club();
        input.setName("Admin Club");
        input.setCategory("Service & Leadership");
        Club created = new Club();
        created.setId(50L);
        when(clubService.createInSchool(eq(SCHOOL_ID), any(Club.class))).thenReturn(created);

        Club result = controller.create(SCHOOL_SLUG, input, auth);

        assertThat(result.getId()).isEqualTo(50L);
    }

    @Test
    void resolveSchoolSafeShouldReturn404ForMissingSchool() {
        when(clubService.resolveSchool(eq("nonexistent")))
            .thenThrow(new IllegalArgumentException("School not found: nonexistent"));

        try {
            controller.list("nonexistent", 0, 50);
            assertThat(true).as("Expected 404").isFalse();
        } catch (org.springframework.web.server.ResponseStatusException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(404);
        }
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
