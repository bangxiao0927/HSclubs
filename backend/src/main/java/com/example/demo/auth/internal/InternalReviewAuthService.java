package com.example.demo.auth.internal;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.example.demo.auth.service.OAuthUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/** Authenticates the optional, server-configured App Review account into the normal session model. */
@Service
public class InternalReviewAuthService {

    public static final String REGISTRATION_ID = "internal";

    private final InternalReviewAccountProperties properties;
    private final OAuthUserService oAuthUserService;
    private final InternalLoginRateLimiter rateLimiter;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final HttpSessionSecurityContextRepository securityContextRepository =
        new HttpSessionSecurityContextRepository();

    public InternalReviewAuthService(InternalReviewAccountProperties properties,
                                     OAuthUserService oAuthUserService,
                                     InternalLoginRateLimiter rateLimiter) {
        this.properties = properties;
        this.oAuthUserService = oAuthUserService;
        this.rateLimiter = rateLimiter;
        if (properties.isPartiallyConfigured() && !properties.isConfigured()) {
            throw new IllegalStateException(
                "Set both APP_INTERNAL_REVIEW_EMAIL and APP_INTERNAL_REVIEW_PASSWORD_HASH, or neither");
        }
        if (properties.isConfigured() && !isBcryptHash(properties.getPasswordHash())) {
            throw new IllegalStateException("APP_INTERNAL_REVIEW_PASSWORD_HASH must be a BCrypt hash");
        }
    }

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    public OAuth2AuthenticationToken login(InternalLoginRequest login,
                                           HttpServletRequest request,
                                           HttpServletResponse response) {
        if (!isConfigured()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        String rateLimitKey = clientKey(request);
        if (rateLimiter.isBlocked(rateLimitKey)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Too many sign-in attempts. Please try again later.");
        }

        String suppliedEmail = login == null ? null : login.email();
        String suppliedPassword = login == null ? null : login.password();
        boolean passwordMatches = StringUtils.hasText(suppliedPassword)
            && passwordEncoder.matches(suppliedPassword, properties.getPasswordHash());
        boolean emailMatches = StringUtils.hasText(suppliedEmail)
            && properties.getEmail().equalsIgnoreCase(suppliedEmail.trim());
        if (!emailMatches || !passwordMatches) {
            rateLimiter.recordFailure(rateLimitKey);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        rateLimiter.clear(rateLimitKey);

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("sub", "internal-review:" + properties.getEmail().toLowerCase(Locale.ROOT));
        attributes.put("email", properties.getEmail());
        attributes.put("email_verified", true);
        attributes.put("name", StringUtils.hasText(properties.getDisplayName())
            ? properties.getDisplayName().trim()
            : "App Review");
        oAuthUserService.recordLogin(REGISTRATION_ID, attributes);

        DefaultOAuth2User principal = new DefaultOAuth2User(
            Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")), attributes, "sub");
        OAuth2AuthenticationToken authentication =
            new OAuth2AuthenticationToken(principal, principal.getAuthorities(), REGISTRATION_ID);

        // Do not carry a pre-login session id into an authenticated session.
        HttpSession oldSession = request.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        request.getSession(true);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        return authentication;
    }

    private static boolean isBcryptHash(String value) {
        return value != null && value.matches("^\\$2[aby]\\$\\d{2}\\$.{53}$");
    }

    private static String clientKey(HttpServletRequest request) {
        // The server is normally behind a trusted nginx proxy. Deliberately use the immediate
        // remote address rather than an attacker-controlled X-Forwarded-For value.
        String address = request.getRemoteAddr();
        return StringUtils.hasText(address) ? address : "unknown";
    }
}
