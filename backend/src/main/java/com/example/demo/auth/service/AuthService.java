package com.example.demo.auth.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.example.demo.auth.config.SecurityProperties;
import com.example.demo.auth.model.AuthProvider;
import com.example.demo.auth.model.AuthUser;
import com.example.demo.auth.mapper.OAuthUserMapper;
import com.example.demo.security.AuthenticatedUserResolver;
import com.example.demo.security.LoginEligibilityPolicy;
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
    private final OAuthUserMapper oAuthUserMapper;
    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final LoginEligibilityPolicy loginEligibilityPolicy;
    private final String authorizationRequestBaseUri;

    public AuthService(ClientRegistrationRepository clientRegistrationRepository,
                       OAuthUserMapper oAuthUserMapper,
                       OAuthUserService oAuthUserService,
                       SecurityProperties securityProperties,
                       UserService userService,
                       AuthenticatedUserResolver authenticatedUserResolver,
                       LoginEligibilityPolicy loginEligibilityPolicy) {
        if (clientRegistrationRepository instanceof Iterable<?>) {
            this.clientRegistrations = (Iterable<ClientRegistration>) clientRegistrationRepository;
        } else {
            throw new IllegalStateException("ClientRegistrationRepository is not iterable");
        }
        this.oAuthUserService = oAuthUserService;
        this.securityProperties = securityProperties;
        this.userService = userService;
        this.oAuthUserMapper = oAuthUserMapper;
        this.authenticatedUserResolver = authenticatedUserResolver;
        this.loginEligibilityPolicy = loginEligibilityPolicy;
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
        user.setAcceptedTerms(hasAcceptedTerms(user.getEmail()));

        // Set member-since from database
        java.time.LocalDateTime created = oAuthUserService.findCreatedAtByEmail(user.getEmail());
        if (created != null) {
            user.setCreatedAt(created.toString());
        }

        return user;
    }

    /**
     * Records terms acceptance for the signed-in session.
     *
     * <p>Takes the session rather than just an email so a row that has gone missing under a
     * still-valid session (sessions last 7 days, so a database restore or migration can
     * outlive one) can be re-created from the session principal first. Without that the
     * UPDATE below matches nothing and the student is walled out of registration for good:
     * /api/auth/me keeps reporting acceptedTerms=false, so the SPA keeps sending them back to
     * the terms page. Unlike the removed recordLogin() call on the read path (see commit
     * "make /api/auth/me read-only"), this only ever INSERTs a row that is absent, so a
     * manually elevated role and the stored last_login_at are never touched.
     *
     * @return true when the account now has an acceptance timestamp -- either because this
     *         call wrote one, or because a previous call already did (the endpoint has to stay
     *         idempotent: the SPA can retry, and the UPDATE deliberately only touches rows
     *         whose accepted_terms_at is still NULL). false means acceptance could not be
     *         recorded at all, which must never be reported to the SPA as success.
     */
    public boolean acceptTerms(OAuth2AuthenticationToken authentication) {
        OAuth2User principal = authentication == null ? null : authentication.getPrincipal();
        if (principal == null) {
            return false;
        }
        Map<String, Object> attributes = principal.getAttributes();
        String email = firstNonBlank(attributes, "email");
        if (email == null) {
            return false;
        }
        // The replay below re-creates an oauth_users row from the session principal, so it has
        // to answer the same question the login path does: a session that predates a newly
        // enabled sign-in restriction must not be able to write itself back in through it.
        loginEligibilityPolicy.verifyEligible(attributes);
        oAuthUserService.ensureStoredUser(authentication.getAuthorizedClientRegistrationId(), attributes);
        int updated = oAuthUserMapper.acceptTerms(email);
        return updated > 0 || hasAcceptedTerms(email);
    }

    public boolean hasAcceptedTerms(String email) {
        if (!StringUtils.hasText(email)) {
            return false;
        }
        Boolean result = oAuthUserMapper.hasAcceptedTerms(email);
        return Boolean.TRUE.equals(result);
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
        return authenticatedUserResolver.isPlatformOwner(email);
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
