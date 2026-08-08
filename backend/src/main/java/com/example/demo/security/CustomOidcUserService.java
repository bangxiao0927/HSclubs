package com.example.demo.security;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import com.example.demo.auth.service.OAuthUserService;

/**
 * The OpenID Connect counterpart of {@link CustomOAuth2UserService}, and the one that actually
 * runs for Google.
 *
 * <p>The Google registration requests the {@code openid} scope, so Spring Security authenticates
 * the callback with its OIDC provider and its {@link OidcUserService} -- it never calls the
 * plain {@code OAuth2UserService} configured through {@code userInfoEndpoint().userService(...)}.
 * Both services therefore have to enforce the same two things, or a login through the provider
 * this school actually uses would skip them entirely: the school's sign-in restrictions, and
 * recording the login.
 *
 * <p>Delegates to {@link OidcUserService} rather than extending it so the delegate can be
 * replaced in tests, where calling the real one would hit the provider's userinfo endpoint.
 */
@Service
public class CustomOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final OAuth2UserService<OidcUserRequest, OidcUser> delegate;
    private final OAuthUserService oAuthUserService;
    private final LoginEligibilityPolicy loginEligibilityPolicy;

    @Autowired
    public CustomOidcUserService(OAuthUserService oAuthUserService,
                                 LoginEligibilityPolicy loginEligibilityPolicy) {
        this(new OidcUserService(), oAuthUserService, loginEligibilityPolicy);
    }

    /** Test seam: lets the userinfo call be stubbed instead of hitting the provider. */
    CustomOidcUserService(OAuth2UserService<OidcUserRequest, OidcUser> delegate,
                          OAuthUserService oAuthUserService,
                          LoginEligibilityPolicy loginEligibilityPolicy) {
        this.delegate = delegate;
        this.oAuthUserService = oAuthUserService;
        this.loginEligibilityPolicy = loginEligibilityPolicy;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = delegate.loadUser(userRequest);

        Map<String, Object> claims = oidcUser.getClaims();
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        // Before recordLogin, for the same reason as in CustomOAuth2UserService: a rejected
        // account must not leave an oauth_users row behind, since that row is what every later
        // lookup keys on. The standard OIDC claims (sub, email, name, picture) are exactly the
        // ones recordLogin and AuthService already read, so no normalization is needed here.
        loginEligibilityPolicy.verifyEligible(claims);
        oAuthUserService.recordLogin(registrationId, claims);

        return oidcUser;
    }
}
