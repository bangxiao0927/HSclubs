package com.example.demo.security;

import com.example.demo.auth.config.SecurityProperties;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Single source of truth for "who is the authenticated user" and "is this user a platform owner".
 *
 * <p>This logic used to be copy-pasted into {@code UserController}, {@code ClubController},
 * {@code ClubImageController} and {@code AuthService}, which meant a security fix applied to one
 * copy could silently miss the others. Every call site now delegates here instead.
 */
@Component
public class AuthenticatedUserResolver {

    private final SecurityProperties securityProperties;

    public AuthenticatedUserResolver(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    /** Returns the authenticated user's email, or {@code null} if it cannot be resolved. Never throws. */
    public String resolveEmail(Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken token)) {
            return null;
        }
        OAuth2User principal = token.getPrincipal();
        if (principal == null) {
            return null;
        }
        Object email = principal.getAttributes().get("email");
        return (email instanceof String str && !str.isBlank()) ? str : null;
    }

    /**
     * Resolves the authenticated email, matching {@code UserController}'s historical contract:
     * 401 when there is no authenticated OAuth2 principal, 400 when the principal has no usable
     * email attribute.
     */
    public String requireAuthenticatedEmail(Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not authenticated");
        }
        OAuth2User principal = token.getPrincipal();
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not authenticated");
        }
        Object emailAttribute = principal.getAttributes().get("email");
        String email = emailAttribute instanceof String str ? str : null;
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Authenticated email is required");
        }
        return email;
    }

    /**
     * Resolves the authenticated email, matching {@code ClubController}/{@code ClubImageController}'s
     * historical contract: 401 "Authentication required" when no email can be resolved.
     */
    public String requireEmail(Authentication authentication) {
        String email = resolveEmail(authentication);
        if (email == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return email;
    }

    public boolean isPlatformOwner(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return securityProperties.getOwnerEmails().stream()
            .filter(item -> item != null && !item.isBlank())
            .anyMatch(item -> email.equalsIgnoreCase(item.trim()));
    }

    public boolean isPlatformOwner(Authentication authentication) {
        return isPlatformOwner(resolveEmail(authentication));
    }

    /** Throws 401/403, matching {@code ClubController}'s historical {@code requirePlatformOwner} contract. */
    public void requirePlatformOwner(Authentication authentication) {
        String email = requireEmail(authentication);
        if (!isPlatformOwner(email)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Platform owner access required");
        }
    }
}
