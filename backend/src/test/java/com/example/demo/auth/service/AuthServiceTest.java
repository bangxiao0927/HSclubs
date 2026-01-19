package com.example.demo.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.example.demo.auth.config.SecurityProperties;
import com.example.demo.auth.model.AuthUser;
import com.example.demo.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private AuthService authService;

    @Mock
    private OAuthUserService oAuthUserService;

    @Mock
    private UserService userService;

    @BeforeEach
    void setUp() {
        ClientRegistration github = ClientRegistration.withRegistrationId("github")
            .clientId("client-id")
            .clientSecret("client-secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost/callback")
            .scope("read:user")
            .authorizationUri("https://github.com/login/oauth/authorize")
            .tokenUri("https://github.com/login/oauth/access_token")
            .userInfoUri("https://api.github.com/user")
            .userNameAttributeName("id")
            .clientName("GitHub")
            .build();

        ClientRegistrationRepository repository = new InMemoryClientRegistrationRepository(github);
        SecurityProperties securityProperties = new SecurityProperties();
        securityProperties.setOwnerEmails(List.of("owner@example.com"));

        authService = new AuthService(repository, oAuthUserService, securityProperties, userService);
    }

    @Test
    void getAuthenticatedUserPrefersAvatarUrlFromProviderAttributes() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", "12345");
        attributes.put("email", "octavia@example.com");
        attributes.put("name", "Octavia Student");
        attributes.put("avatar_url", "https://avatars.example.com/octavia.png");

        OAuth2User principal = new DefaultOAuth2User(
            Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
            attributes,
            "id");

        OAuth2AuthenticationToken authenticationToken = new OAuth2AuthenticationToken(
            principal,
            principal.getAuthorities(),
            "github");

        when(userService.findGraduationYearByEmail("octavia@example.com")).thenReturn(null);

        AuthUser user = authService.getAuthenticatedUser(authenticationToken);

        assertThat(user).isNotNull();
        assertThat(user.getAvatarUrl()).isEqualTo("https://avatars.example.com/octavia.png");
        assertThat(user.getDisplayName()).isEqualTo("Octavia Student");
        assertThat(user.getEmail()).isEqualTo("octavia@example.com");

        verify(oAuthUserService).recordLogin("github", attributes);
    }
}
