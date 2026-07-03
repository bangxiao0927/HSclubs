package com.example.demo.club.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.auth.config.SecurityProperties;
import com.example.demo.club.model.Club;
import com.example.demo.club.service.ClubService;
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
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ClubControllerTest {

    @Mock
    private ClubService clubService;

    private ClubController controller;

    @BeforeEach
    void setUp() {
        SecurityProperties securityProperties = new SecurityProperties();
        securityProperties.setOwnerEmails(List.of("owner@example.com"));
        controller = new ClubController(clubService, securityProperties);
    }

    @Test
    void createShouldRequireAuthentication() {
        try {
            controller.create(new Club(), null);
            assertThat(true).as("Expected 401").isFalse();
        } catch (ResponseStatusException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(401);
        }
        verify(clubService, never()).create(any(Club.class));
    }

    @Test
    void createShouldRejectNonOwner() {
        try {
            controller.create(new Club(), createAuth("student@example.com"));
            assertThat(true).as("Expected 403").isFalse();
        } catch (ResponseStatusException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }
        verify(clubService, never()).create(any(Club.class));
    }

    @Test
    void createShouldAllowPlatformOwner() {
        Club input = new Club();
        input.setName("New Club");
        Club created = new Club();
        created.setId(10L);
        when(clubService.create(eq(input))).thenReturn(created);

        Club result = controller.create(input, createAuth("owner@example.com"));

        assertThat(result.getId()).isEqualTo(10L);
        verify(clubService).create(input);
    }

    @Test
    void deleteShouldRejectNonOwner() {
        try {
            controller.delete(1L, createAuth("student@example.com"));
            assertThat(true).as("Expected 403").isFalse();
        } catch (ResponseStatusException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }
        verify(clubService, never()).delete(1L);
    }

    @Test
    void deleteShouldAllowPlatformOwner() {
        Club existing = new Club();
        existing.setId(1L);
        when(clubService.findById(1L)).thenReturn(existing);

        controller.delete(1L, createAuth("owner@example.com"));

        verify(clubService).delete(1L);
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
