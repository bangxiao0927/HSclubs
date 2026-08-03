package com.example.demo.club.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.auth.config.SecurityProperties;
import com.example.demo.auth.mapper.OAuthUserMapper;
import com.example.demo.club.model.Club;
import com.example.demo.club.model.ClubPost;
import com.example.demo.club.service.ClubPostService;
import com.example.demo.club.service.ClubService;
import com.example.demo.security.AuthenticatedUserResolver;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = ClubPostController.class,
    excludeAutoConfiguration = {
        OAuth2ClientAutoConfiguration.class,
        OAuth2ClientWebSecurityAutoConfiguration.class
    })
@AutoConfigureMockMvc(addFilters = false)
class ClubPostControllerTest {

    private static final String MEMBER_EMAIL = "member@example.com";
    private static final String NON_MEMBER_EMAIL = "outsider@example.com";

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
    private ClubPostService clubPostService;

    @MockitoBean
    private OAuthUserMapper oAuthUserMapper;

    @Test
    void publishRequiresAuthentication() throws Exception {
        mockMvc.perform(multipart("/api/clubs/1/posts")
                .file(aPhoto())
                .param("title", "Meeting recap"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void publishReturnsNotFoundForAnUnknownClub() throws Exception {
        when(clubService.findById(eq(1L), any())).thenReturn(null);

        mockMvc.perform(multipart("/api/clubs/1/posts")
                .file(aPhoto())
                .param("title", "Meeting recap")
                .principal(oauthToken(MEMBER_EMAIL)))
            .andExpect(status().isNotFound());
    }

    @Test
    void publishRejectsANonMemberWithForbidden() throws Exception {
        Club club = new Club();
        club.setId(1L);
        club.setViewerIsMember(false);
        when(clubService.findById(eq(1L), any())).thenReturn(club);

        mockMvc.perform(multipart("/api/clubs/1/posts")
                .file(aPhoto())
                .param("title", "Meeting recap")
                .principal(oauthToken(NON_MEMBER_EMAIL)))
            .andExpect(status().isForbidden());
    }

    @Test
    void publishSucceedsForAMemberAndReturnsTheCreatedPost() throws Exception {
        Club club = new Club();
        club.setId(1L);
        club.setViewerIsMember(true);
        when(clubService.findById(eq(1L), any())).thenReturn(club);
        when(oAuthUserMapper.findIdByEmail(MEMBER_EMAIL)).thenReturn(42L);
        ClubPost created = new ClubPost();
        created.setId(9L);
        created.setClubId(1L);
        created.setAuthorOauthUserId(42L);
        created.setTitle("Meeting recap");
        created.setImageUrl("/uploads/club-posts/uuid.jpg");
        when(clubPostService.publish(eq(1L), eq(42L), eq("Meeting recap"), any())).thenReturn(created);

        mockMvc.perform(multipart("/api/clubs/1/posts")
                .file(aPhoto())
                .param("title", "Meeting recap")
                .principal(oauthToken(MEMBER_EMAIL)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(9))
            .andExpect(jsonPath("$.title").value("Meeting recap"))
            .andExpect(jsonPath("$.imageUrl").value("/uploads/club-posts/uuid.jpg"));
    }

    @Test
    void publishReturnsBadRequestWhenTheServiceRejectsTheTitleOrFile() throws Exception {
        Club club = new Club();
        club.setId(1L);
        club.setViewerIsMember(true);
        when(clubService.findById(eq(1L), any())).thenReturn(club);
        when(oAuthUserMapper.findIdByEmail(MEMBER_EMAIL)).thenReturn(42L);
        when(clubPostService.publish(any(), any(), any(), any()))
            .thenThrow(new IllegalArgumentException("Title is required"));

        mockMvc.perform(multipart("/api/clubs/1/posts")
                .file(aPhoto())
                .param("title", "   ")
                .principal(oauthToken(MEMBER_EMAIL)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void publishReturnsBadRequestWhenTheTitleParameterIsMissingEntirely() throws Exception {
        Club club = new Club();
        club.setId(1L);
        club.setViewerIsMember(true);
        when(clubService.findById(eq(1L), any())).thenReturn(club);

        mockMvc.perform(multipart("/api/clubs/1/posts")
                .file(aPhoto())
                .principal(oauthToken(MEMBER_EMAIL)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void publishReturnsBadRequestWhenTheFilePartIsMissingEntirely() throws Exception {
        Club club = new Club();
        club.setId(1L);
        club.setViewerIsMember(true);
        when(clubService.findById(eq(1L), any())).thenReturn(club);

        mockMvc.perform(multipart("/api/clubs/1/posts")
                .param("title", "Meeting recap")
                .principal(oauthToken(MEMBER_EMAIL)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void publishReturnsServerErrorWhenStorageFailsWithAnIOException() throws Exception {
        Club club = new Club();
        club.setId(1L);
        club.setViewerIsMember(true);
        when(clubService.findById(eq(1L), any())).thenReturn(club);
        when(oAuthUserMapper.findIdByEmail(MEMBER_EMAIL)).thenReturn(42L);
        when(clubPostService.publish(any(), any(), any(), any())).thenThrow(new IOException("disk full"));

        mockMvc.perform(multipart("/api/clubs/1/posts")
                .file(aPhoto())
                .param("title", "Meeting recap")
                .principal(oauthToken(MEMBER_EMAIL)))
            .andExpect(status().isInternalServerError());
    }

    @Test
    void feedIsReachableByALoggedOutVisitor() throws Exception {
        Club club = new Club();
        club.setId(1L);
        club.setStatus("active");
        when(clubService.findById(eq(1L), any())).thenReturn(club);
        when(clubPostService.findPublicFeed(eq(1L), anyInt(), anyInt()))
            .thenReturn(new ClubPostService.PostFeedPage(List.of(), 0, 12, 0));

        mockMvc.perform(get("/api/clubs/1/posts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(12))
            .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void feedReturnsNotFoundForAnUnknownClub() throws Exception {
        when(clubService.findById(eq(1L), any())).thenReturn(null);

        mockMvc.perform(get("/api/clubs/1/posts"))
            .andExpect(status().isNotFound());
    }

    @Test
    void feedOfANonActiveClubReturnsNotFoundToALoggedOutVisitor() throws Exception {
        Club club = new Club();
        club.setId(1L);
        club.setStatus("pending");
        club.setViewerIsMember(false);
        club.setCanManage(false);
        when(clubService.findById(eq(1L), any())).thenReturn(club);

        mockMvc.perform(get("/api/clubs/1/posts"))
            .andExpect(status().isNotFound());
    }

    @Test
    void feedOfANonActiveClubIsVisibleToAMember() throws Exception {
        Club club = new Club();
        club.setId(1L);
        club.setStatus("pending");
        club.setViewerIsMember(true);
        when(clubService.findById(eq(1L), any())).thenReturn(club);
        when(clubPostService.findPublicFeed(eq(1L), anyInt(), anyInt()))
            .thenReturn(new ClubPostService.PostFeedPage(List.of(), 0, 12, 0));

        mockMvc.perform(get("/api/clubs/1/posts").principal(oauthToken(MEMBER_EMAIL)))
            .andExpect(status().isOk());
    }

    private static MockMultipartFile aPhoto() {
        return new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[] {1, 2, 3});
    }

    private OAuth2AuthenticationToken oauthToken(String email) {
        OAuth2User principal = new DefaultOAuth2User(
            List.of(() -> "ROLE_USER"),
            Map.of("email", email, "sub", email),
            "sub");
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
    }
}
