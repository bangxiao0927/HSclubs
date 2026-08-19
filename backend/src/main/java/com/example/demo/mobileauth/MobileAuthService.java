package com.example.demo.mobileauth;

import com.example.demo.mobileauth.MobileAuthException.Error;
import com.example.demo.summary.config.SchoolIdentity;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.regex.Pattern;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * The rules of the mobile-auth flow, kept out of the controllers so the security checks are one
 * readable list and can be tested without a servlet.
 *
 * <p>Three responsibilities: validate a {@code start} request into a pending flow, issue a
 * one-time code once Google has authenticated the person, and redeem that code at {@code
 * complete}. Every rejection is a {@link MobileAuthException} carrying the contract's error code.
 */
@Service
public class MobileAuthService {

    private static final Pattern STATE = Pattern.compile("^[A-Za-z0-9_-]{22,128}$");
    // Site-relative only: a leading single slash, never "//" (protocol-relative) or a scheme.
    private static final Pattern RETURN_TO = Pattern.compile("^/(?![/\\\\])[^\\s?#]*(?:\\?[^\\s#]*)?$");
    private static final int CODE_BYTES = 32;

    private final SchoolIdentity schoolIdentity;
    private final MobileAuthProperties properties;
    private final MobileAuthCodeStore codeStore;
    private final SecureRandom random = new SecureRandom();

    public MobileAuthService(
        SchoolIdentity schoolIdentity,
        MobileAuthProperties properties,
        MobileAuthCodeStore codeStore
    ) {
        this.schoolIdentity = schoolIdentity;
        this.properties = properties;
        this.codeStore = codeStore;
    }

    public boolean isEnabled() {
        return schoolIdentity.publishesV1() && properties.isConfigured();
    }

    /**
     * Validates the start parameters into a pending flow, or throws.
     *
     * <p>Order matters only for the error the caller sees; all of these must hold. The school id
     * must be this deployment's, the redirect must be a registered callback (this is the check
     * that stops a tampered link diverting the code), and the return path must be same-origin.
     */
    public PendingMobileAuth validateStart(
        String schoolId,
        String state,
        String codeChallenge,
        String codeChallengeMethod,
        String redirectUri,
        String returnTo
    ) {
        if (!isEnabled()) {
            throw new MobileAuthException(Error.TEMPORARILY_UNAVAILABLE, "mobile auth is not enabled here");
        }
        String expectedSchoolId = schoolIdentity.schoolId().orElse(null);
        if (schoolId == null || !schoolId.equals(expectedSchoolId)) {
            throw new MobileAuthException(Error.UNKNOWN_SCHOOL, "schoolId is not this deployment");
        }
        if (state == null || !STATE.matcher(state).matches()) {
            throw new MobileAuthException(Error.INVALID_REQUEST, "state is missing or malformed");
        }
        if (!"S256".equals(codeChallengeMethod)) {
            throw new MobileAuthException(Error.INVALID_REQUEST, "code_challenge_method must be S256");
        }
        if (!Pkce.isValidChallenge(codeChallenge)) {
            throw new MobileAuthException(Error.INVALID_REQUEST, "code_challenge is missing or malformed");
        }
        if (!properties.isRegisteredCallback(redirectUri)) {
            throw new MobileAuthException(Error.INVALID_REQUEST, "redirect_uri is not a registered callback");
        }
        if (returnTo != null && !RETURN_TO.matcher(returnTo).matches()) {
            throw new MobileAuthException(Error.INVALID_REQUEST, "return_to must be a site-relative path");
        }
        return new PendingMobileAuth(schoolId, state, codeChallenge, redirectUri, returnTo);
    }

    /**
     * Issues a one-time code for an authenticated person, storing only its digest.
     *
     * @return the raw code, to be handed to the app once via the callback URL and never stored.
     */
    public String issueCode(PendingMobileAuth pending, Authentication authentication, Instant now) {
        byte[] bytes = new byte[CODE_BYTES];
        random.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        codeStore.store(code, new MobileAuthCodeStore.Entry(
            pending.schoolId(),
            pending.codeChallenge(),
            now.plusSeconds(properties.resolvedCodeTtlSeconds()),
            authentication,
            pending.returnTo()));
        return code;
    }

    /**
     * Redeems a code at {@code complete}, returning the authentication to establish, or throwing.
     *
     * <p>The code is removed as it is read, so a replay finds nothing. The school must match the
     * one the code was issued for, the code must not have expired, and the PKCE verifier must hash
     * to the challenge the flow was started with.
     */
    public MobileAuthCodeStore.Entry redeem(String schoolId, String code, String codeVerifier, Instant now) {
        if (!isEnabled()) {
            throw new MobileAuthException(Error.TEMPORARILY_UNAVAILABLE, "mobile auth is not enabled here");
        }
        String expectedSchoolId = schoolIdentity.schoolId().orElse(null);
        if (schoolId == null || !schoolId.equals(expectedSchoolId)) {
            throw new MobileAuthException(Error.UNKNOWN_SCHOOL, "schoolId is not this deployment");
        }
        if (code == null || code.isBlank()) {
            throw new MobileAuthException(Error.INVALID_GRANT, "code is missing");
        }
        if (!Pkce.isValidVerifier(codeVerifier)) {
            throw new MobileAuthException(Error.INVALID_REQUEST, "code_verifier is missing or malformed");
        }

        MobileAuthCodeStore.Entry entry = codeStore.redeem(code);
        if (entry == null) {
            // Unknown, or already spent: indistinguishable on purpose, and both are invalid_grant.
            throw new MobileAuthException(Error.INVALID_GRANT, "code is unknown or already used");
        }
        if (now.isAfter(entry.expiresAt())) {
            throw new MobileAuthException(Error.EXPIRED_CODE, "code has expired");
        }
        if (!entry.schoolId().equals(schoolId)) {
            // A code issued for another school being redeemed here.
            throw new MobileAuthException(Error.INVALID_GRANT, "code was issued for a different school");
        }
        if (!Pkce.matches(codeVerifier, entry.codeChallenge())) {
            throw new MobileAuthException(Error.INVALID_GRANT, "code_verifier does not match the challenge");
        }
        return entry;
    }
}
