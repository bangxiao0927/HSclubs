package com.example.demo.club.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.auth.config.SecurityProperties;
import com.example.demo.club.model.Club;
import com.example.demo.club.service.ClubService;
import com.example.demo.security.AuthenticatedUserResolver;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = ClubController.class,
    excludeAutoConfiguration = {
        OAuth2ClientAutoConfiguration.class,
        OAuth2ClientWebSecurityAutoConfiguration.class
    })
@AutoConfigureMockMvc(addFilters = false)
class ClubControllerTest {

    private static final String OWNER_EMAIL = "test-owner@example.com";
    private static final String PRESIDENT_EMAIL = "president@example.com";
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

    @Test
    void deleteClubRequiresPlatformOwner() throws Exception {
        mockMvc.perform(delete("/api/clubs/1").principal(oauthToken(STUDENT_EMAIL)))
            .andExpect(status().isForbidden());
    }

    @Test
    void deleteClubSucceedsForPlatformOwner() throws Exception {
        Club club = new Club();
        club.setId(1L);
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(club);

        mockMvc.perform(delete("/api/clubs/1").principal(oauthToken(OWNER_EMAIL)))
            .andExpect(status().isNoContent());
    }

    @Test
    void updateClubRequiresManageAccess() throws Exception {
        Club club = new Club();
        club.setId(1L);
        club.setCanManage(false);
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(club);

        mockMvc.perform(put("/api/clubs/1")
                .principal(oauthToken(STUDENT_EMAIL))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void updateClubSucceedsForClubPresident() throws Exception {
        Club club = new Club();
        club.setId(1L);
        club.setCategory("STEM & Innovation");
        club.setCanManage(true);
        when(clubService.resolveBySlugOrId(eq("1"), any())).thenReturn(club);
        when(clubService.update(anyLong(), any())).thenReturn(club);

        mockMvc.perform(put("/api/clubs/1")
                .principal(oauthToken(PRESIDENT_EMAIL))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"STEM & Innovation\"}"))
            .andExpect(status().isOk());
    }

    private OAuth2AuthenticationToken oauthToken(String email) {
        OAuth2User principal = new DefaultOAuth2User(
            List.of(() -> "ROLE_USER"),
            Map.of("email", email, "sub", email),
            "sub");
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
    }
}
