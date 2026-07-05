package com.example.demo.auth.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.example.demo.auth.config.SecurityProperties;
import com.example.demo.auth.model.AuthProvider;
import com.example.demo.auth.model.AuthUser;
import com.example.demo.user.service.UserService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuthService {

    private final Iterable<ClientRegistration> clientRegistrations;
    private final OAuthUserService oAuthUserService;
    private final SecurityProperties securityProperties;
    private final UserService userService;
    private final String authorizationRequestBaseUri;

    public AuthService(ClientRegistrationRepository clientRegistrationRepository,
                       OAuthUserService oAuthUserService,
                       SecurityProperties securityProperties,
                       UserService userService) {
        if (clientRegistrationRepository instanceof Iterable<?>) {
            this.clientRegistrations = (Iterable<ClientRegistration>) clientRegistrationRepository;
        } else {
            throw new IllegalStateException("ClientRegistrationRepository is not iterable");
        }
        this.oAuthUserService = oAuthUserService;
        this.securityProperties = securityProperties;
        this.userService = userService;
        this.authorizationRequestBaseUri = resolveAuthorizationRequestBaseUri(securityProperties);
    }

    public List<AuthProvider> getProviders() {
        List<AuthProvider> providers = new ArrayList<>();
        for (ClientRegistration registration : clientRegistrations) {
            String authorizationPath = buildAuthorizationPath(registration.getRegistrationId());
            providers.add(new AuthProvider(registration.getRegistrationId(),
                registration.getClientName(), authorizationPath));
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
        user.setAvatarUrl(stringAttribute(attributes, "picture", "avatar", "avatar_url"));
        String provider = authentication.getAuthorizedClientRegistrationId();
        user.setProvider(provider);
        user.setGraduationYear(readGraduationYear(attributes));
        Integer storedGraduationYear = userService.findGraduationYearByEmail(user.getEmail());
        if (storedGraduationYear != null) {
            user.setGraduationYear(storedGraduationYear);
        }
        user.setPlatformOwner(isPlatformOwner(user.getEmail()));

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

    private boolean isPlatformOwner(String email) {
        if (!StringUtils.hasText(email)) {
            return false;
        }
        return securityProperties.getOwnerEmails().stream()
            .filter(StringUtils::hasText)
            .anyMatch(allowed -> email.equalsIgnoreCase(allowed.trim()));
    }

    private String buildAuthorizationPath(String registrationId) {
        String normalizedBase = authorizationRequestBaseUri.endsWith("/")
            ? authorizationRequestBaseUri
            : authorizationRequestBaseUri + "/";
        return normalizedBase + registrationId;
    }

    private String resolveAuthorizationRequestBaseUri(SecurityProperties properties) {
        String configured = properties.getAuthorizationRequestBaseUri();
        if (StringUtils.hasText(configured)) {
            return configured;
        }
        return OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI;
    }

    private Integer readGraduationYear(Map<String, Object> attributes) {
        if (attributes == null) {
            return null;
        }

        Integer year = extractGraduationYear(attributes, "graduationYear", "gradYear",
            "classYear", "class_of", "classOf");
        if (year != null) {
            return year;
        }

        Object education = attributes.get("education");
        if (education instanceof Map<?, ?> nested) {
            year = extractGraduationYear((Map<String, Object>) nested,
                "graduationYear", "gradYear", "classYear");
            if (year != null) {
                return year;
            }
        }

        return null;
    }

    private Integer extractGraduationYear(Map<String, Object> attributes, String... keys) {
        for (String key : keys) {
            Object value = attributes.get(key);
            Integer parsed = parseYear(value);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private Integer parseYear(Object value) {
        if (value instanceof Number number) {
            int candidate = number.intValue();
            return isPlausibleYear(candidate) ? candidate : null;
        }
        if (value instanceof String str && !str.isBlank()) {
            String digits = str.trim().replaceAll("[^0-9]", "");
            if (digits.length() == 4) {
                try {
                    int candidate = Integer.parseInt(digits);
                    return isPlausibleYear(candidate) ? candidate : null;
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private boolean isPlausibleYear(int year) {
        return year >= 1900 && year <= 2100;
    }
}
