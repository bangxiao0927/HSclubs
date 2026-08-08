package com.example.demo.security;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.example.demo.auth.config.SecurityProperties;

/**
 * Decides whether an authenticated OAuth2 account may sign in to this school's site at all.
 *
 * <p>Both restrictions are opt-in and off by default, which is deliberate: this repo is meant to
 * be copied by other schools, so an out-of-the-box deployment must work with any Google account,
 * and a school that wants its site limited to its own Google Workspace turns the restriction on
 * in configuration rather than editing code. When nothing is configured this class is a no-op.
 *
 * <p>The email address matters beyond identity here: it is the key
 * {@link AuthenticatedUserResolver#isPlatformOwner} compares against {@code
 * app.security.owner-emails}, and the key {@code oauth_users} rows are looked up by.
 *
 * <p>This runs when an account signs in, not on every request, so turning a restriction on does
 * not by itself evict an existing session (they last 7 days). Sessions live in this
 * application's own memory, so the restart that applies the new configuration also drops them;
 * see docs/DEPLOYMENT.md.
 */
@Component
public class LoginEligibilityPolicy {

    /** Error codes echoed to the frontend as {@code ?error=...} on the post-login redirect. */
    public static final String EMAIL_DOMAIN_NOT_ALLOWED = "email_domain_not_allowed";
    public static final String EMAIL_NOT_VERIFIED = "email_not_verified";

    private final SecurityProperties securityProperties;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    public LoginEligibilityPolicy(SecurityProperties securityProperties,
                                  AuthenticatedUserResolver authenticatedUserResolver) {
        this.securityProperties = securityProperties;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    /**
     * @throws OAuth2AuthenticationException if this account may not sign in; the exception's
     *                                       error code is one of the constants above, which the
     *                                       OAuth2 failure handler passes to the frontend
     */
    public void verifyEligible(Map<String, Object> attributes) {
        SecurityProperties.Login login = securityProperties.getLogin();
        String email = emailOf(attributes);

        if (login.isRequireVerifiedEmail() && !isEmailVerified(attributes)) {
            throw reject(EMAIL_NOT_VERIFIED,
                "This account's email address is not verified with its provider.");
        }

        // Platform-owner status is decided by comparing this address against
        // app.security.owner-emails, and it is the highest privilege in the application, so an
        // address the provider explicitly reports as unverified may never be used to claim it --
        // whatever require-verified-email is set to. An absent claim is not "unverified" and is
        // left alone, so a provider that does not send it cannot lock the owner out.
        if (isOwnerEmail(email) && isEmailExplicitlyUnverified(attributes)) {
            throw reject(EMAIL_NOT_VERIFIED,
                "This account's email address is not verified with its provider.");
        }

        List<String> allowedDomains = login.getAllowedEmailDomains();
        if (allowedDomains.isEmpty()) {
            return;
        }
        if (!isDomainAllowed(email, allowedDomains)) {
            throw reject(EMAIL_DOMAIN_NOT_ALLOWED,
                "This site is limited to accounts from a specific email domain.");
        }
    }

    // Deliberately delegated rather than re-implemented: AuthenticatedUserResolver owns the
    // owner-email comparison for authorization, and a second copy here would let the two drift
    // -- this gate would keep admitting an address that authorization treats as an owner.
    private boolean isOwnerEmail(String email) {
        return authenticatedUserResolver.isPlatformOwner(email);
    }

    private static boolean isEmailExplicitlyUnverified(Map<String, Object> attributes) {
        Object verified = attributes == null ? null : attributes.get("email_verified");
        if (verified == null) {
            return false;
        }
        return !isEmailVerified(attributes);
    }

    private static boolean isEmailVerified(Map<String, Object> attributes) {
        Object verified = attributes == null ? null : attributes.get("email_verified");
        if (verified instanceof Boolean flag) {
            return flag;
        }
        // Some providers send the claim as a string; anything else (including absent) is "no".
        return verified != null && Boolean.parseBoolean(verified.toString());
    }

    private static String emailOf(Map<String, Object> attributes) {
        Object email = attributes == null ? null : attributes.get("email");
        return email == null ? null : email.toString();
    }

    private static boolean isDomainAllowed(String email, List<String> allowedDomains) {
        // An account with no email at all can never satisfy a domain restriction: the whole
        // authorization model downstream is keyed on that address.
        if (!StringUtils.hasText(email)) {
            return false;
        }
        int at = email.lastIndexOf('@');
        if (at < 0 || at == email.length() - 1) {
            return false;
        }
        String domain = email.substring(at + 1).trim().toLowerCase(Locale.ROOT);
        return allowedDomains.stream()
            .filter(StringUtils::hasText)
            // A leading "@" or stray whitespace in configuration is a natural thing to write.
            .map(allowed -> allowed.trim().toLowerCase(Locale.ROOT).replaceFirst("^@", ""))
            .anyMatch(domain::equals);
    }

    private static OAuth2AuthenticationException reject(String errorCode, String description) {
        return new OAuth2AuthenticationException(new OAuth2Error(errorCode, description, null), description);
    }
}
