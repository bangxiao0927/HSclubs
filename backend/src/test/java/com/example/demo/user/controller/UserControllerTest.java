package com.example.demo.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.auth.config.SecurityProperties;
import com.example.demo.auth.mapper.OAuthUserMapper;
import com.example.demo.auth.model.OAuthUserRecord;
import com.example.demo.security.AuthenticatedUserResolver;
import com.example.demo.user.service.UserService;
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
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = UserController.class,
    excludeAutoConfiguration = {
        OAuth2ClientAutoConfiguration.class,
        OAuth2ClientWebSecurityAutoConfiguration.class
    })
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    private static final String OWNER_EMAIL = "test-owner@example.com";
    private static final String STUDENT_EMAIL = "student@example.com";

    @TestConfiguration
    static class TestConfig {
        @Bean
        SecurityProperties securityProperties() {
            // Rebound from src/test/resources/application.yaml's app.security.owner-emails
            // by ConfigurationPropertiesBindingPostProcessor; OWNER_EMAIL must match that value.
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
    private UserService userService;

    @MockitoBean
    private OAuthUserMapper oAuthUserMapper;

    @Test
    void searchUsersReturnsForbiddenForNonOwner() throws Exception {
        mockMvc.perform(get("/api/users/search")
                .param("q", "al")
                .principal(oauthToken(STUDENT_EMAIL)))
            .andExpect(status().isForbidden());
    }

    @Test
    void searchUsersReturnsNarrowedResultsForOwner() throws Exception {
        OAuthUserRecord record = new OAuthUserRecord();
        record.setId(7L);
        record.setEmail("alice@example.com");
        record.setDisplayName("Alice");
        record.setProviderUserId("google-999");
        record.setRole("student");
        record.setAvatarUrl("https://example.com/avatar.png");
        when(oAuthUserMapper.searchByEmailOrName("al", 10)).thenReturn(List.of(record));

        String body = mockMvc.perform(get("/api/users/search")
                .param("q", "al")
                .principal(oauthToken(OWNER_EMAIL)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        assertThat(body).contains("\"id\":7", "\"email\":\"alice@example.com\"", "\"displayName\":\"Alice\"");
        assertThat(body).doesNotContain("providerUserId", "avatarUrl", "\"role\"");
    }

    private OAuth2AuthenticationToken oauthToken(String email) {
        OAuth2User principal = new DefaultOAuth2User(
            List.of(() -> "ROLE_USER"),
            Map.of("email", email, "sub", email),
            "sub");
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
    }
}
