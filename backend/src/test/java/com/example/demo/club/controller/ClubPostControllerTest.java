package com.example.demo.club.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.auth.config.SecurityProperties;
import com.example.demo.auth.mapper.OAuthUserMapper;
import com.example.demo.club.model.Club;
import com.example.demo.club.model.ClubPost;
import com.example.demo.club.model.PublicClubPost;
import com.example.demo.club.service.ClubPostService;
import com.example.demo.club.service.ClubService;
import com.example.demo.security.AuthenticatedUserResolver;
import java.io.IOException;
import java.time.LocalDateTime;
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
import org.springframework.dao.CannotAcquireLockException;
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
    private static final String OWNER_EMAIL = "test-owner@example.com";
    private static final String PRESIDENT_EMAIL = "president@example.com";
    private static final String AUTHOR_EMAIL = "author@example.com";

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
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(null);

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
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(club);

        mockMvc.perform(multipart("/api/clubs/1/posts")
                .file(aPhoto())
                .param("title", "Meeting recap")
                .principal(oauthToken(NON_MEMBER_EMAIL)))
            .andExpect(status().isForbidden());
    }

    @Test
    void publishSucceedsForAMemberAndReturnsTheCreatedPostInTheSameSafeShapeAsTheFeed() throws Exception {
        Club club = new Club();
        club.setId(1L);
        club.setViewerIsMember(true);
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(club);
        when(oAuthUserMapper.findIdByEmail(MEMBER_EMAIL)).thenReturn(42L);
        PublicClubPost created = new PublicClubPost();
        created.setId(9L);
        created.setClubId(1L);
        created.setTitle("Meeting recap");
        created.setImageUrl("/uploads/club-posts/uuid.jpg");
        created.setCreatedAt(LocalDateTime.of(2024, 1, 1, 10, 0));
        created.setAuthorDisplayName("Ada Lovelace");
        created.setAuthorAvatarUrl("/uploads/avatar-cache/ada.jpg");
        when(clubPostService.publish(eq(1L), eq(42L), eq("Meeting recap"), any())).thenReturn(created);

        String responseBody = mockMvc.perform(multipart("/api/clubs/1/posts")
                .file(aPhoto())
                .param("title", "Meeting recap")
                .principal(oauthToken(MEMBER_EMAIL)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(9))
            .andExpect(jsonPath("$.title").value("Meeting recap"))
            .andExpect(jsonPath("$.imageUrl").value("/uploads/club-posts/uuid.jpg"))
            .andExpect(jsonPath("$.createdAt").exists())
            .andExpect(jsonPath("$.authorDisplayName").value("Ada Lovelace"))
            .andExpect(jsonPath("$.authorAvatarUrl").value("/uploads/avatar-cache/ada.jpg"))
            .andReturn().getResponse().getContentAsString();

        assertThat(responseBody).doesNotContain("authorOauthUserId");
    }

    @Test
    void publishReturnsBadRequestWhenTheServiceRejectsTheTitleOrFile() throws Exception {
        Club club = new Club();
        club.setId(1L);
        club.setViewerIsMember(true);
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(club);
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
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(club);

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
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(club);

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
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(club);
        when(oAuthUserMapper.findIdByEmail(MEMBER_EMAIL)).thenReturn(42L);
        when(clubPostService.publish(any(), any(), any(), any())).thenThrow(new IOException("disk full"));

        mockMvc.perform(multipart("/api/clubs/1/posts")
                .file(aPhoto())
                .param("title", "Meeting recap")
                .principal(oauthToken(MEMBER_EMAIL)))
            .andExpect(status().isInternalServerError());
    }

    // ClubPostService#publish throws IllegalStateException when its post-insert read-back
    // cannot find the row it just inserted (the transaction has already rolled back and the
    // file has already been cleaned up by then); the controller must still translate that into
    // a 500, not let it escape as an unhandled exception.
    @Test
    void publishReturnsServerErrorWhenTheReadBackCannotFindTheRow() throws Exception {
        Club club = new Club();
        club.setId(1L);
        club.setViewerIsMember(true);
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(club);
        when(oAuthUserMapper.findIdByEmail(MEMBER_EMAIL)).thenReturn(42L);
        when(clubPostService.publish(any(), any(), any(), any()))
            .thenThrow(new IllegalStateException("Post 9 was not found immediately after being inserted"));

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
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(club);
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
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(null);

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
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(club);

        mockMvc.perform(get("/api/clubs/1/posts"))
            .andExpect(status().isNotFound());
    }

    @Test
    void feedOfANonActiveClubIsVisibleToAMember() throws Exception {
        Club club = new Club();
        club.setId(1L);
        club.setStatus("pending");
        club.setViewerIsMember(true);
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(club);
        when(clubPostService.findPublicFeed(eq(1L), anyInt(), anyInt()))
            .thenReturn(new ClubPostService.PostFeedPage(List.of(), 0, 12, 0));

        mockMvc.perform(get("/api/clubs/1/posts").principal(oauthToken(MEMBER_EMAIL)))
            .andExpect(status().isOk());
    }

    // ---- pin ----

    @Test
    void pinRequiresAuthentication() throws Exception {
        mockMvc.perform(put("/api/clubs/1/posts/9/pin"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void pinReturnsNotFoundForAnUnknownClub() throws Exception {
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(null);

        mockMvc.perform(put("/api/clubs/1/posts/9/pin").principal(oauthToken(MEMBER_EMAIL)))
            .andExpect(status().isNotFound());
    }

    @Test
    void pinRejectsAnOrdinaryMemberWithForbidden() throws Exception {
        Club club = new Club();
        club.setId(1L);
        club.setCanManage(false);
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(club);

        mockMvc.perform(put("/api/clubs/1/posts/9/pin").principal(oauthToken(MEMBER_EMAIL)))
            .andExpect(status().isForbidden());
    }

    @Test
    void pinSucceedsForThePresidentAndReturnsNoContent() throws Exception {
        Club club = new Club();
        club.setId(1L);
        club.setCanManage(true);
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(club);
        when(oAuthUserMapper.findIdByEmail(MEMBER_EMAIL)).thenReturn(42L);

        mockMvc.perform(put("/api/clubs/1/posts/9/pin").principal(oauthToken(MEMBER_EMAIL)))
            .andExpect(status().isNoContent());
    }

    @Test
    void pinSucceedsForAPlatformOwnerWhoIsNotTheClubPresident() throws Exception {
        Club club = new Club();
        club.setId(1L);
        club.setCanManage(false);
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(club);
        when(oAuthUserMapper.findIdByEmail("test-owner@example.com")).thenReturn(7L);

        mockMvc.perform(put("/api/clubs/1/posts/9/pin").principal(oauthToken("test-owner@example.com")))
            .andExpect(status().isNoContent());
    }

    @Test
    void pinReturnsNotFoundWhenTheServiceReportsTheClubOrPostIsMissing() throws Exception {
        Club club = new Club();
        club.setId(1L);
        club.setCanManage(true);
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(club);
        when(oAuthUserMapper.findIdByEmail(MEMBER_EMAIL)).thenReturn(42L);
        org.mockito.Mockito.doThrow(new IllegalArgumentException("Post not found for this club"))
            .when(clubPostService).pin(any(), any(), any());

        mockMvc.perform(put("/api/clubs/1/posts/9/pin").principal(oauthToken(MEMBER_EMAIL)))
            .andExpect(status().isNotFound());
    }

    @Test
    void pinReturnsConflictWhenTheServiceReportsTheCapIsReached() throws Exception {
        Club club = new Club();
        club.setId(1L);
        club.setCanManage(true);
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(club);
        when(oAuthUserMapper.findIdByEmail(MEMBER_EMAIL)).thenReturn(42L);
        org.mockito.Mockito.doThrow(new IllegalStateException("At most 3 posts can be pinned. Unpin one first."))
            .when(clubPostService).pin(any(), any(), any());

        mockMvc.perform(put("/api/clubs/1/posts/9/pin").principal(oauthToken(MEMBER_EMAIL)))
            .andExpect(status().isConflict())
            .andExpect(status().reason("At most 3 posts can be pinned. Unpin one first."));
    }

    @Test
    void pinReturnsConflictWhenTheServiceReportsALockTimeout() throws Exception {
        Club club = new Club();
        club.setId(1L);
        club.setCanManage(true);
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(club);
        when(oAuthUserMapper.findIdByEmail(MEMBER_EMAIL)).thenReturn(42L);
        org.mockito.Mockito.doThrow(new CannotAcquireLockException("lock wait timeout"))
            .when(clubPostService).pin(any(), any(), any());

        mockMvc.perform(put("/api/clubs/1/posts/9/pin").principal(oauthToken(MEMBER_EMAIL)))
            .andExpect(status().isConflict());
    }

    // ---- unpin ----

    @Test
    void unpinRequiresAuthentication() throws Exception {
        mockMvc.perform(delete("/api/clubs/1/posts/9/pin"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void unpinRejectsAnOrdinaryMemberWithForbidden() throws Exception {
        Club club = new Club();
        club.setId(1L);
        club.setCanManage(false);
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(club);

        mockMvc.perform(delete("/api/clubs/1/posts/9/pin").principal(oauthToken(MEMBER_EMAIL)))
            .andExpect(status().isForbidden());
    }

    @Test
    void unpinSucceedsForThePresidentAndReturnsNoContent() throws Exception {
        Club club = new Club();
        club.setId(1L);
        club.setCanManage(true);
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(club);

        mockMvc.perform(delete("/api/clubs/1/posts/9/pin").principal(oauthToken(MEMBER_EMAIL)))
            .andExpect(status().isNoContent());
    }

    @Test
    void unpinReturnsNotFoundWhenTheServiceReportsThePostIsMissing() throws Exception {
        Club club = new Club();
        club.setId(1L);
        club.setCanManage(true);
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(club);
        org.mockito.Mockito.doThrow(new IllegalArgumentException("Post not found for this club"))
            .when(clubPostService).unpin(any(), any());

        mockMvc.perform(delete("/api/clubs/1/posts/9/pin").principal(oauthToken(MEMBER_EMAIL)))
            .andExpect(status().isNotFound());
    }

    private static MockMultipartFile aPhoto() {
        return new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[] {1, 2, 3});
    }

    @Test
    void deletePostRequiresAuthentication() throws Exception {
        mockMvc.perform(delete("/api/clubs/1/posts/9"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void deletePostReturnsNotFoundForAnUnknownClub() throws Exception {
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(null);

        mockMvc.perform(delete("/api/clubs/1/posts/9").principal(oauthToken(AUTHOR_EMAIL)))
            .andExpect(status().isNotFound());
    }

    @Test
    void deletePostReturnsNotFoundWhenThePostDoesNotBelongToTheClub() throws Exception {
        Club club = new Club();
        club.setId(1L);
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(club);
        when(clubPostService.findByIdAndClubId(9L, 1L)).thenReturn(null);

        mockMvc.perform(delete("/api/clubs/1/posts/9").principal(oauthToken(AUTHOR_EMAIL)))
            .andExpect(status().isNotFound());
    }

    @Test
    void deletePostRejectsANonAuthorNonPresidentNonOwnerWithForbidden() throws Exception {
        Club club = new Club();
        club.setId(1L);
        club.setCanManage(false);
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(club);
        when(oAuthUserMapper.findIdByEmail(NON_MEMBER_EMAIL)).thenReturn(77L);
        ClubPost post = new ClubPost();
        post.setId(9L);
        post.setAuthorOauthUserId(42L);
        when(clubPostService.findByIdAndClubId(9L, 1L)).thenReturn(post);

        mockMvc.perform(delete("/api/clubs/1/posts/9").principal(oauthToken(NON_MEMBER_EMAIL)))
            .andExpect(status().isForbidden());
    }

    @Test
    void deletePostSucceedsForThePostsAuthor() throws Exception {
        Club club = new Club();
        club.setId(1L);
        club.setCanManage(false);
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(club);
        when(oAuthUserMapper.findIdByEmail(AUTHOR_EMAIL)).thenReturn(42L);
        ClubPost post = new ClubPost();
        post.setId(9L);
        post.setAuthorOauthUserId(42L);
        when(clubPostService.findByIdAndClubId(9L, 1L)).thenReturn(post);

        mockMvc.perform(delete("/api/clubs/1/posts/9").principal(oauthToken(AUTHOR_EMAIL)))
            .andExpect(status().isNoContent());

        verify(clubPostService).delete(post);
    }

    @Test
    void deletePostSucceedsForTheClubPresident() throws Exception {
        Club club = new Club();
        club.setId(1L);
        club.setCanManage(true);
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(club);
        when(oAuthUserMapper.findIdByEmail(PRESIDENT_EMAIL)).thenReturn(7L);
        ClubPost post = new ClubPost();
        post.setId(9L);
        post.setAuthorOauthUserId(42L);
        when(clubPostService.findByIdAndClubId(9L, 1L)).thenReturn(post);

        mockMvc.perform(delete("/api/clubs/1/posts/9").principal(oauthToken(PRESIDENT_EMAIL)))
            .andExpect(status().isNoContent());

        verify(clubPostService).delete(post);
    }

    @Test
    void deletePostSucceedsForAPlatformOwner() throws Exception {
        Club club = new Club();
        club.setId(1L);
        club.setCanManage(false);
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(club);
        when(oAuthUserMapper.findIdByEmail(OWNER_EMAIL)).thenReturn(99L);
        ClubPost post = new ClubPost();
        post.setId(9L);
        post.setAuthorOauthUserId(42L);
        when(clubPostService.findByIdAndClubId(9L, 1L)).thenReturn(post);

        mockMvc.perform(delete("/api/clubs/1/posts/9").principal(oauthToken(OWNER_EMAIL)))
            .andExpect(status().isNoContent());

        verify(clubPostService).delete(post);
    }

    @Test
    void deletePostNeverDeletesWhenAuthorizationFails() throws Exception {
        Club club = new Club();
        club.setId(1L);
        club.setCanManage(false);
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(club);
        when(oAuthUserMapper.findIdByEmail(NON_MEMBER_EMAIL)).thenReturn(77L);
        ClubPost post = new ClubPost();
        post.setId(9L);
        post.setAuthorOauthUserId(42L);
        when(clubPostService.findByIdAndClubId(9L, 1L)).thenReturn(post);

        mockMvc.perform(delete("/api/clubs/1/posts/9").principal(oauthToken(NON_MEMBER_EMAIL)))
            .andExpect(status().isForbidden());

        verify(clubPostService, never()).delete(any());
    }

    @Test
    void deletePostReturnsServerErrorWhenTheServiceReportsAStaleRead() throws Exception {
        Club club = new Club();
        club.setId(1L);
        club.setCanManage(false);
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(club);
        when(oAuthUserMapper.findIdByEmail(AUTHOR_EMAIL)).thenReturn(42L);
        ClubPost post = new ClubPost();
        post.setId(9L);
        post.setAuthorOauthUserId(42L);
        when(clubPostService.findByIdAndClubId(9L, 1L)).thenReturn(post);
        org.mockito.Mockito.doThrow(new IllegalStateException("Post 9 was not found when deleting"))
            .when(clubPostService).delete(post);

        mockMvc.perform(delete("/api/clubs/1/posts/9").principal(oauthToken(AUTHOR_EMAIL)))
            .andExpect(status().isInternalServerError());
    }

    private OAuth2AuthenticationToken oauthToken(String email) {
        OAuth2User principal = new DefaultOAuth2User(
            List.of(() -> "ROLE_USER"),
            Map.of("email", email, "sub", email),
            "sub");
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
    }
}
