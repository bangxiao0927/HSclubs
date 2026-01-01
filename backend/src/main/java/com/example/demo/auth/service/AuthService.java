package com.example.demo.auth.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.example.demo.auth.model.AuthProvider;
import com.example.demo.auth.model.AuthUser;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final Iterable<ClientRegistration> clientRegistrations;
    private final OAuthUserService oAuthUserService;

    public AuthService(ClientRegistrationRepository clientRegistrationRepository,
                       OAuthUserService oAuthUserService) {
        if (clientRegistrationRepository instanceof Iterable<?>) {
            this.clientRegistrations = (Iterable<ClientRegistration>) clientRegistrationRepository;
        } else {
            throw new IllegalStateException("ClientRegistrationRepository is not iterable");
        }
        this.oAuthUserService = oAuthUserService;
    }

    public List<AuthProvider> getProviders() {
        List<AuthProvider> providers = new ArrayList<>();
        for (ClientRegistration registration : clientRegistrations) {
            String authorizationPath = OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI
                + "/" + registration.getRegistrationId();
            providers.add(new AuthProvider(registration.getRegistrationId(), registration.getClientName(), authorizationPath));
        }
        return providers;
    }

    public AuthUser getAuthenticatedUser(OAuth2AuthenticationToken authentication) {
        if (authentication == null) {
            return null;
        }

        OAuth2User principal = authentication.getPrincipal();
        if (principal == null) {
            return null;
        }

        Map<String, Object> attributes = principal.getAttributes();
        AuthUser user = new AuthUser();
        user.setId(firstNonBlank(attributes, "id", "sub", "email"));
        user.setEmail(stringAttribute(attributes, "email"));
        user.setDisplayName(firstNonBlank(attributes, "name", "given_name"));
        user.setAvatarUrl(stringAttribute(attributes, "picture", "avatar"));
        String provider = authentication.getAuthorizedClientRegistrationId();
        user.setProvider(provider);
        oAuthUserService.recordLogin(provider, attributes);
        return user;
    }

    private String stringAttribute(Map<String, Object> attributes, String... keys) {
        String value = firstNonBlank(attributes, keys);
        return value == null ? "" : value;
    }

    private String firstNonBlank(Map<String, Object> attributes, String... keys) {
        if (attributes == null) {
            return null;
        }
        for (String key : keys) {
            Object value = attributes.get(key);
            if (value instanceof String str && !str.isBlank()) {
                return str;
            }
        }
        return null;
    }
}
