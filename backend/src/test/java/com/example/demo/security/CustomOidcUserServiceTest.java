package com.example.demo.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import com.example.demo.auth.config.SecurityProperties;
import com.example.demo.auth.service.OAuthUserService;

/**
 * Google requests the {@code openid} scope, so this is the service Spring Security actually
 * calls at login -- {@link CustomOAuth2UserService} is never reached for it. Everything the
 * login path must do therefore has to be enforced here too.
 */
class CustomOidcUserServiceTest {

    private static OidcUser oidcUser(String email, Object emailVerified) {
        Map<String, Object> claims = Map.of(
            "sub", "google-123",
            "email", email,
            "email_verified", emailVerified,
            "name", "Ada Lovelace");
        OidcIdToken idToken = new OidcIdToken(
            "token-value", Instant.now(), Instant.now().plusSeconds(3600), claims);
        return new DefaultOidcUser(List.of(() -> "ROLE_USER"), idToken, "sub");
    }

    private record Fixture(CustomOidcUserService service, OAuthUserService oAuthUserService) {
    }

    @SuppressWarnings("unchecked")
    private static Fixture fixture(List<String> allowedDomains, List<String> ownerEmails, OidcUser user) {
        SecurityProperties properties = new SecurityProperties();
        properties.getLogin().setAllowedEmailDomains(allowedDomains);
        properties.setOwnerEmails(ownerEmails);

        OAuth2UserService<OidcUserRequest, OidcUser> delegate = mock(OAuth2UserService.class);
        when(delegate.loadUser(any())).thenReturn(user);
        OAuthUserService oAuthUserService = mock(OAuthUserService.class);

        return new Fixture(
            new CustomOidcUserService(delegate, oAuthUserService, new LoginEligibilityPolicy(properties)),
            oAuthUserService);
    }

    private static OidcUserRequest request() {
        OidcUserRequest request = mock(OidcUserRequest.class);
        org.springframework.security.oauth2.client.registration.ClientRegistration registration =
            org.springframework.security.oauth2.client.registration.ClientRegistration
                .withRegistrationId("google")
                .clientId("client-id")
                .authorizationGrantType(
                    org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/api/auth/google/callback")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .build();
        when(request.getClientRegistration()).thenReturn(registration);
        return request;
    }

    @Test
    void recordsTheLoginForAnAllowedAccount() {
        Fixture fixture = fixture(List.of("students.example.edu"), List.of(),
            oidcUser("ada@students.example.edu", true));

        assertThatCode(() -> fixture.service().loadUser(request())).doesNotThrowAnyException();

        verify(fixture.oAuthUserService()).recordLogin(eq("google"), any());
    }

    // The whole point of routing OIDC through this service: without it a restricted school's
    // sign-in rules would not apply to Google logins at all.
    @Test
    void rejectsAnAccountFromAnotherDomainAndStoresNothing() {
        Fixture fixture = fixture(List.of("students.example.edu"), List.of(),
            oidcUser("stranger@gmail.com", true));

        assertThatThrownBy(() -> fixture.service().loadUser(request()))
            .isInstanceOf(OAuth2AuthenticationException.class);

        verify(fixture.oAuthUserService(), never()).recordLogin(any(), any());
    }

    // Owner status is the highest privilege here and is decided purely by the email address.
    @Test
    void rejectsAnOwnerAddressTheProviderReportsAsUnverified() {
        Fixture fixture = fixture(List.of(), List.of("owner@example.com"),
            oidcUser("owner@example.com", false));

        assertThatThrownBy(() -> fixture.service().loadUser(request()))
            .isInstanceOf(OAuth2AuthenticationException.class);

        verify(fixture.oAuthUserService(), never()).recordLogin(any(), any());
    }

    @Test
    void anOrdinaryUnverifiedAccountIsStillAllowedWhenNothingRequiresVerification() {
        Fixture fixture = fixture(List.of(), List.of("owner@example.com"),
            oidcUser("student@gmail.com", false));

        assertThatCode(() -> fixture.service().loadUser(request())).doesNotThrowAnyException();
    }
}
