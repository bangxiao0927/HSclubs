package com.example.demo.mobileauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.example.demo.mobileauth.MobileAuthException.Error;
import com.example.demo.summary.config.SchoolIdentity;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

class MobileAuthServiceTest {

    private static final String SCHOOL_ID = "sch_7Qb3Xf9KLm2ZpR4tVn6Y";
    private static final String ORIGIN = "https://clubs.example.edu";
    private static final String CALLBACK = "https://hsclubs.net/mobile-auth/callback";
    private static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
    private static final String STATE = "M2Rk8sYq1vB7nT4wX0cLpZ";

    private final MobileAuthCodeStore store = new MobileAuthCodeStore();
    private final MobileAuthService service = new MobileAuthService(
        new SchoolIdentity(SCHOOL_ID, ORIGIN), new MobileAuthProperties(), store);
    private final Authentication user = new TestingAuthenticationToken("alex@example.edu", "n/a", "ROLE_USER");
    private final Instant now = Instant.parse("2026-08-18T04:11:02Z");

    private Error errorOf(Runnable call) {
        return catchThrowableOfType(call::run, MobileAuthException.class).error();
    }

    @Test
    void acceptsAWellFormedStart() {
        PendingMobileAuth pending = service.validateStart(
            SCHOOL_ID, STATE, CHALLENGE, "S256", CALLBACK, "/clubs/robotics");
        assertThat(pending.schoolId()).isEqualTo(SCHOOL_ID);
        assertThat(pending.returnTo()).isEqualTo("/clubs/robotics");
    }

    @Test
    void rejectsEachMalformedStartWithItsContractError() {
        assertThat(errorOf(() -> service.validateStart("sch_other000000000000", STATE, CHALLENGE, "S256", CALLBACK, null)))
            .isEqualTo(Error.UNKNOWN_SCHOOL);
        assertThat(errorOf(() -> service.validateStart(SCHOOL_ID, "short", CHALLENGE, "S256", CALLBACK, null)))
            .isEqualTo(Error.INVALID_REQUEST);
        assertThat(errorOf(() -> service.validateStart(SCHOOL_ID, STATE, CHALLENGE, "plain", CALLBACK, null)))
            .isEqualTo(Error.INVALID_REQUEST);
        assertThat(errorOf(() -> service.validateStart(SCHOOL_ID, STATE, "bad", "S256", CALLBACK, null)))
            .isEqualTo(Error.INVALID_REQUEST);
        // A redirect that is not a registered callback is the tampering the allow-list stops.
        assertThat(errorOf(() -> service.validateStart(SCHOOL_ID, STATE, CHALLENGE, "S256", "https://evil.example/callback", null)))
            .isEqualTo(Error.INVALID_REQUEST);
        // An absolute or protocol-relative return path is refused.
        assertThat(errorOf(() -> service.validateStart(SCHOOL_ID, STATE, CHALLENGE, "S256", CALLBACK, "https://evil.example")))
            .isEqualTo(Error.INVALID_REQUEST);
        assertThat(errorOf(() -> service.validateStart(SCHOOL_ID, STATE, CHALLENGE, "S256", CALLBACK, "//evil.example")))
            .isEqualTo(Error.INVALID_REQUEST);
    }

    @Test
    void completesTheHappyPathOnce() {
        PendingMobileAuth pending = service.validateStart(SCHOOL_ID, STATE, CHALLENGE, "S256", CALLBACK, "/clubs");
        String code = service.issueCode(pending, user, now);

        MobileAuthCodeStore.Entry entry = service.redeem(SCHOOL_ID, code, VERIFIER, now.plusSeconds(10));
        assertThat(entry.authentication()).isSameAs(user);
        assertThat(entry.returnTo()).isEqualTo("/clubs");

        // Replays: the code is gone.
        assertThat(errorOf(() -> service.redeem(SCHOOL_ID, code, VERIFIER, now.plusSeconds(11))))
            .isEqualTo(Error.INVALID_GRANT);
    }

    @Test
    void rejectsAnExpiredCode() {
        PendingMobileAuth pending = service.validateStart(SCHOOL_ID, STATE, CHALLENGE, "S256", CALLBACK, null);
        String code = service.issueCode(pending, user, now);
        assertThat(errorOf(() -> service.redeem(SCHOOL_ID, code, VERIFIER, now.plusSeconds(1_000))))
            .isEqualTo(Error.EXPIRED_CODE);
    }

    @Test
    void rejectsAMismatchedVerifier() {
        PendingMobileAuth pending = service.validateStart(SCHOOL_ID, STATE, CHALLENGE, "S256", CALLBACK, null);
        String code = service.issueCode(pending, user, now);
        assertThat(errorOf(() -> service.redeem(SCHOOL_ID, code, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", now.plusSeconds(5))))
            .isEqualTo(Error.INVALID_GRANT);
    }

    @Test
    void rejectsRedeemingUnderTheWrongSchool() {
        PendingMobileAuth pending = service.validateStart(SCHOOL_ID, STATE, CHALLENGE, "S256", CALLBACK, null);
        String code = service.issueCode(pending, user, now);
        assertThat(errorOf(() -> service.redeem("sch_other000000000000", code, VERIFIER, now.plusSeconds(5))))
            .isEqualTo(Error.UNKNOWN_SCHOOL);
    }

    @Test
    void rejectsAnUnknownCode() {
        assertThatThrownBy(() -> service.redeem(SCHOOL_ID, "never-issued", VERIFIER, now))
            .isInstanceOf(MobileAuthException.class);
    }
}
