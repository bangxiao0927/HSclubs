package com.example.demo.security;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import com.example.demo.auth.service.OAuthUserService;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final OAuthUserService oAuthUserService;
    private final LoginEligibilityPolicy loginEligibilityPolicy;

    public CustomOAuth2UserService(OAuthUserService oAuthUserService,
                                   LoginEligibilityPolicy loginEligibilityPolicy) {
        this.oAuthUserService = oAuthUserService;
        this.loginEligibilityPolicy = loginEligibilityPolicy;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(userRequest);

        Map<String, Object> attributes = new HashMap<>(oauth2User.getAttributes());
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        normalizeAttributes(attributes, registrationId);
        // Before recordLogin: a rejected account must not leave an oauth_users row behind, since
        // that row is what every later lookup (and the owner-email comparison) keys on.
        loginEligibilityPolicy.verifyEligible(attributes);
        oAuthUserService.recordLogin(registrationId, attributes);

        String userNameAttribute = userRequest.getClientRegistration()
            .getProviderDetails()
            .getUserInfoEndpoint()
            .getUserNameAttributeName();

        return new DefaultOAuth2User(
            Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
            attributes,
            userNameAttribute);
    }

    private void normalizeAttributes(Map<String, Object> attributes, String registrationId) {
        if ("google".equalsIgnoreCase(registrationId)) {
            Object subject = attributes.get("sub");
            attributes.putIfAbsent("id", subject);
            attributes.putIfAbsent("displayName", attributes.get("name"));
            attributes.putIfAbsent("avatar", attributes.get("picture"));
        }
    }
}
