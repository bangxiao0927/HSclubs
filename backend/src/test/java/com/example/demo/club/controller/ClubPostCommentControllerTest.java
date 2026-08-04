package com.example.demo.club.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.auth.config.SecurityProperties;
import com.example.demo.auth.mapper.OAuthUserMapper;
import com.example.demo.club.model.Club;
import com.example.demo.club.model.ClubPost;
import com.example.demo.club.model.ClubPostComment;
import com.example.demo.club.model.PublicClubPostComment;
import com.example.demo.club.service.ClubContentModerationPolicy;
import com.example.demo.club.service.ClubPostCommentService;
import com.example.demo.club.service.ClubPostNotFoundException;
import com.example.demo.club.service.ClubPostService;
import com.example.demo.club.service.ClubService;
import com.example.demo.club.service.ClubVisibilityPolicy;
import com.example.demo.club.service.CommentLimitExceededException;
import com.example.demo.security.AuthenticatedUserResolver;
import java.time.Instant;
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
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = ClubPostCommentController.class,
    excludeAutoConfiguration = {
        OAuth2ClientAutoConfiguration.class,
        OAuth2ClientWebSecurityAutoConfiguration.class
    })
@AutoConfigureMockMvc(addFilters = false)
class ClubPostCommentControllerTest {

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

        @Bean
        ClubVisibilityPolicy clubVisibilityPolicy(AuthenticatedUserResolver authenticatedUserResolver) {
            return new ClubVisibilityPolicy(authenticatedUserResolver);
        }

        @Bean
        ClubContentModerationPolicy clubContentModerationPolicy(AuthenticatedUserResolver authenticatedUserResolver) {
            return new ClubContentModerationPolicy(authenticatedUserResolver);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClubService clubService;

    @MockitoBean
    private ClubPostService clubPostService;

    @MockitoBean
    private ClubPostCommentService clubPostCommentService;

    @MockitoBean
    private OAuthUserMapper oAuthUserMapper;

    private static Club activeClub() {
        Club club = new Club();
        club.setId(1L);
        club.setViewerIsMember(true);
        return club;
    }

    // ---- GET ----

    @Test
    void listIsReachableByALoggedOutVisitor() throws Exception {
        Club club = new Club();
        club.setId(1L);
        club.setStatus("active");
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(club);
        ClubPost post = new ClubPost();
        post.setId(9L);
        when(clubPostService.findByIdAndClubId(9L, 1L)).thenReturn(post);
        PublicClubPostComment comment = new PublicClubPostComment();
        comment.setBody("Nice!");
        when(clubPostCommentService.findPublicComments(eq(9L), any(), anyBoolean())).thenReturn(List.of(comment));

        mockMvc.perform(get("/api/clubs/1/posts/9/comments"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].body").value("Nice!"));
    }

    // Mirrors ClubPostControllerTest's own capability-field test: the delete control for a
    // comment's own author is driven by this same viewerCanDelete flag, not by comparing display
    // names.
    @Test
    void listItemsCarryViewerCanDeleteFromTheServiceUnchanged() throws Exception {
        Club club = new Club();
        club.setId(1L);
        club.setStatus("active");
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(club);
        ClubPost post = new ClubPost();
        post.setId(9L);
        when(clubPostService.findByIdAndClubId(9L, 1L)).thenReturn(post);
        PublicClubPostComment comment = new PublicClubPostComment();
        comment.setViewerCanDelete(true);
        when(clubPostCommentService.findPublicComments(eq(9L), any(), anyBoolean())).thenReturn(List.of(comment));

        mockMvc.perform(get("/api/clubs/1/posts/9/comments"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].viewerCanDelete").value(true));
    }

    @Test
    void listReturnsNotFoundForAnUnknownClub() throws Exception {
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(null);

        mockMvc.perform(get("/api/clubs/1/posts/9/comments"))
            .andExpect(status().isNotFound());
    }

    @Test
    void listReturnsNotFoundWhenThePostDoesNotBelongToTheClub() throws Exception {
        Club club = new Club();
        club.setId(1L);
        club.setStatus("active");
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(club);
        when(clubPostService.findByIdAndClubId(9L, 1L)).thenReturn(null);

        mockMvc.perform(get("/api/clubs/1/posts/9/comments"))
            .andExpect(status().isNotFound());
    }

    // Same gating as ClubPostController#feed: a non-active club's comments 404 to anyone who
    // is not a member, the club president, or a platform owner, matching the feed they belong to.
    @Test
    void listOfANonActiveClubReturnsNotFoundToALoggedOutVisitor() throws Exception {
        Club club = new Club();
        club.setId(1L);
        club.setStatus("pending");
        club.setViewerIsMember(false);
        club.setCanManage(false);
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(club);

        mockMvc.perform(get("/api/clubs/1/posts/9/comments"))
            .andExpect(status().isNotFound());
    }

    @Test
    void listOfANonActiveClubIsVisibleToAMember() throws Exception {
        Club club = new Club();
        club.setId(1L);
        club.setStatus("pending");
        club.setViewerIsMember(true);
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(club);
        ClubPost post = new ClubPost();
        post.setId(9L);
        when(clubPostService.findByIdAndClubId(9L, 1L)).thenReturn(post);
        when(clubPostCommentService.findPublicComments(eq(9L), any(), anyBoolean())).thenReturn(List.of());

        mockMvc.perform(get("/api/clubs/1/posts/9/comments").principal(oauthToken(MEMBER_EMAIL)))
            .andExpect(status().isOk());
    }

    // ---- POST ----

    @Test
    void createRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/clubs/1/posts/9/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"Nice!\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void createReturnsNotFoundForAnUnknownClub() throws Exception {
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(null);

        mockMvc.perform(post("/api/clubs/1/posts/9/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"Nice!\"}")
                .principal(oauthToken(MEMBER_EMAIL)))
            .andExpect(status().isNotFound());
    }

    @Test
    void createRejectsANonMemberWithForbidden() throws Exception {
        Club club = new Club();
        club.setId(1L);
        club.setViewerIsMember(false);
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(club);

        mockMvc.perform(post("/api/clubs/1/posts/9/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"Nice!\"}")
                .principal(oauthToken(NON_MEMBER_EMAIL)))
            .andExpect(status().isForbidden());

        verify(clubPostCommentService, never()).create(any(), any(), any(), any());
    }

    @Test
    void createSucceedsForAMemberAndReturnsThePublicComment() throws Exception {
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(activeClub());
        when(oAuthUserMapper.findIdByEmail(MEMBER_EMAIL)).thenReturn(42L);
        PublicClubPostComment created = new PublicClubPostComment();
        created.setId(5L);
        created.setBody("Nice!");
        created.setCreatedAt(Instant.parse("2024-01-01T10:00:00Z"));
        created.setAuthorDisplayName("Ada Lovelace");
        when(clubPostCommentService.create(1L, 9L, 42L, "Nice!")).thenReturn(created);

        String responseBody = mockMvc.perform(post("/api/clubs/1/posts/9/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"Nice!\"}")
                .principal(oauthToken(MEMBER_EMAIL)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(5))
            .andExpect(jsonPath("$.body").value("Nice!"))
            .andExpect(jsonPath("$.createdAt").value("2024-01-01T10:00:00Z"))
            .andExpect(jsonPath("$.authorDisplayName").value("Ada Lovelace"))
            .andReturn().getResponse().getContentAsString();

        assertThat(responseBody).doesNotContain("authorOauthUserId");
    }

    @Test
    void createReturnsBadRequestWhenTheServiceRejectsTheBody() throws Exception {
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(activeClub());
        when(oAuthUserMapper.findIdByEmail(MEMBER_EMAIL)).thenReturn(42L);
        when(clubPostCommentService.create(any(), any(), any(), any()))
            .thenThrow(new IllegalArgumentException("Comment body is required"));

        mockMvc.perform(post("/api/clubs/1/posts/9/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"   \"}")
                .principal(oauthToken(MEMBER_EMAIL)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createReturnsNotFoundWhenTheServiceReportsAMissingPost() throws Exception {
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(activeClub());
        when(oAuthUserMapper.findIdByEmail(MEMBER_EMAIL)).thenReturn(42L);
        when(clubPostCommentService.create(any(), any(), any(), any()))
            .thenThrow(new ClubPostNotFoundException(9L));

        mockMvc.perform(post("/api/clubs/1/posts/9/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"Nice!\"}")
                .principal(oauthToken(MEMBER_EMAIL)))
            .andExpect(status().isNotFound());
    }

    @Test
    void createReturnsConflictWhenTheServiceReportsTheCommentLimitIsReached() throws Exception {
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(activeClub());
        when(oAuthUserMapper.findIdByEmail(MEMBER_EMAIL)).thenReturn(42L);
        when(clubPostCommentService.create(any(), any(), any(), any()))
            .thenThrow(new CommentLimitExceededException(9L));

        mockMvc.perform(post("/api/clubs/1/posts/9/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"Nice!\"}")
                .principal(oauthToken(MEMBER_EMAIL)))
            .andExpect(status().isConflict());
    }

    // The lock-timeout requirement: an H2/MySQL lock timeout, surfaced by the mapper as
    // Spring's CannotAcquireLockException, must become a 409, not an unhandled 500.
    @Test
    void createReturnsConflictWhenTheServiceReportsALockTimeout() throws Exception {
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(activeClub());
        when(oAuthUserMapper.findIdByEmail(MEMBER_EMAIL)).thenReturn(42L);
        when(clubPostCommentService.create(any(), any(), any(), any()))
            .thenThrow(new CannotAcquireLockException("Timeout trying to lock table"));

        mockMvc.perform(post("/api/clubs/1/posts/9/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"Nice!\"}")
                .principal(oauthToken(MEMBER_EMAIL)))
            .andExpect(status().isConflict());
    }

    // ---- DELETE ----

    @Test
    void deleteRequiresAuthentication() throws Exception {
        mockMvc.perform(delete("/api/clubs/1/posts/9/comments/5"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteReturnsNotFoundForAnUnknownClub() throws Exception {
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(null);

        mockMvc.perform(delete("/api/clubs/1/posts/9/comments/5").principal(oauthToken(AUTHOR_EMAIL)))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteReturnsNotFoundWhenThePostDoesNotBelongToTheClub() throws Exception {
        Club club = new Club();
        club.setId(1L);
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(club);
        when(clubPostService.findByIdAndClubId(9L, 1L)).thenReturn(null);

        mockMvc.perform(delete("/api/clubs/1/posts/9/comments/5").principal(oauthToken(AUTHOR_EMAIL)))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteReturnsNotFoundWhenTheCommentDoesNotBelongToThePost() throws Exception {
        Club club = new Club();
        club.setId(1L);
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(club);
        ClubPost post = new ClubPost();
        post.setId(9L);
        when(clubPostService.findByIdAndClubId(9L, 1L)).thenReturn(post);
        when(clubPostCommentService.findByIdAndPostId(5L, 9L)).thenReturn(null);

        mockMvc.perform(delete("/api/clubs/1/posts/9/comments/5").principal(oauthToken(AUTHOR_EMAIL)))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteRejectsANonAuthorNonPresidentNonOwnerWithForbidden() throws Exception {
        Club club = new Club();
        club.setId(1L);
        club.setCanManage(false);
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(club);
        when(oAuthUserMapper.findIdByEmail(NON_MEMBER_EMAIL)).thenReturn(77L);
        ClubPost post = new ClubPost();
        post.setId(9L);
        when(clubPostService.findByIdAndClubId(9L, 1L)).thenReturn(post);
        ClubPostComment comment = new ClubPostComment();
        comment.setId(5L);
        comment.setAuthorOauthUserId(42L);
        when(clubPostCommentService.findByIdAndPostId(5L, 9L)).thenReturn(comment);

        mockMvc.perform(delete("/api/clubs/1/posts/9/comments/5").principal(oauthToken(NON_MEMBER_EMAIL)))
            .andExpect(status().isForbidden());

        verify(clubPostCommentService, never()).delete(any());
    }

    @Test
    void deleteSucceedsForTheCommentsAuthor() throws Exception {
        Club club = new Club();
        club.setId(1L);
        club.setCanManage(false);
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(club);
        when(oAuthUserMapper.findIdByEmail(AUTHOR_EMAIL)).thenReturn(42L);
        ClubPost post = new ClubPost();
        post.setId(9L);
        when(clubPostService.findByIdAndClubId(9L, 1L)).thenReturn(post);
        ClubPostComment comment = new ClubPostComment();
        comment.setId(5L);
        comment.setAuthorOauthUserId(42L);
        when(clubPostCommentService.findByIdAndPostId(5L, 9L)).thenReturn(comment);

        mockMvc.perform(delete("/api/clubs/1/posts/9/comments/5").principal(oauthToken(AUTHOR_EMAIL)))
            .andExpect(status().isNoContent());

        verify(clubPostCommentService).delete(5L);
    }

    @Test
    void deleteSucceedsForTheClubPresident() throws Exception {
        Club club = new Club();
        club.setId(1L);
        club.setCanManage(true);
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(club);
        when(oAuthUserMapper.findIdByEmail(PRESIDENT_EMAIL)).thenReturn(7L);
        ClubPost post = new ClubPost();
        post.setId(9L);
        when(clubPostService.findByIdAndClubId(9L, 1L)).thenReturn(post);
        ClubPostComment comment = new ClubPostComment();
        comment.setId(5L);
        comment.setAuthorOauthUserId(42L);
        when(clubPostCommentService.findByIdAndPostId(5L, 9L)).thenReturn(comment);

        mockMvc.perform(delete("/api/clubs/1/posts/9/comments/5").principal(oauthToken(PRESIDENT_EMAIL)))
            .andExpect(status().isNoContent());

        verify(clubPostCommentService).delete(5L);
    }

    @Test
    void deleteSucceedsForAPlatformOwner() throws Exception {
        Club club = new Club();
        club.setId(1L);
        club.setCanManage(false);
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(club);
        when(oAuthUserMapper.findIdByEmail(OWNER_EMAIL)).thenReturn(99L);
        ClubPost post = new ClubPost();
        post.setId(9L);
        when(clubPostService.findByIdAndClubId(9L, 1L)).thenReturn(post);
        ClubPostComment comment = new ClubPostComment();
        comment.setId(5L);
        comment.setAuthorOauthUserId(42L);
        when(clubPostCommentService.findByIdAndPostId(5L, 9L)).thenReturn(comment);

        mockMvc.perform(delete("/api/clubs/1/posts/9/comments/5").principal(oauthToken(OWNER_EMAIL)))
            .andExpect(status().isNoContent());

        verify(clubPostCommentService).delete(5L);
    }

    private OAuth2AuthenticationToken oauthToken(String email) {
        OAuth2User principal = new DefaultOAuth2User(
            List.of(() -> "ROLE_USER"),
            Map.of("email", email, "sub", email),
            "sub");
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
    }
}
