package com.example.demo.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.security.LoginEligibilityPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;

/**
 * Which contract error a failed mobile sign-in is reported as.
 *
 * <p>This existed as untested code that returned the same answer down both branches, so an
 * eligibility check that looked deliberate was dead, and every provider outage reached the app as
 * a cancellation. The distinction is the whole point: the app returns quietly on
 * {@code access_denied} and says something, with a retry, on {@code temporarily_unavailable}.
 */
class MobileAuthFailureErrorTest {

    private static AuthenticationException oauth2(String errorCode) {
        return new OAuth2AuthenticationException(new OAuth2Error(errorCode), errorCode);
    }

    @Test
    void aRefusalOfThisAccountIsAccessDenied() {
        assertThat(mobileAuthFailureFor(oauth2(LoginEligibilityPolicy.EMAIL_DOMAIN_NOT_ALLOWED)))
            .isEqualTo("access_denied");
        assertThat(mobileAuthFailureFor(oauth2(LoginEligibilityPolicy.EMAIL_NOT_VERIFIED)))
            .isEqualTo("access_denied");
    }

    @Test
    void theProviderReportingTheUserDeclinedIsAccessDenied() {
        assertThat(mobileAuthFailureFor(oauth2(OAuth2ErrorCodes.ACCESS_DENIED)))
            .isEqualTo("access_denied");
    }

    // The regression this test exists for: a fault on our side or the provider's must not reach
    // the app as "you cancelled", which the app returns from without explanation.
    @Test
    void everythingElseIsTemporarilyUnavailable() {
        assertThat(mobileAuthFailureFor(oauth2(OAuth2ErrorCodes.SERVER_ERROR)))
            .isEqualTo("temporarily_unavailable");
        assertThat(mobileAuthFailureFor(oauth2(OAuth2ErrorCodes.INVALID_TOKEN)))
            .isEqualTo("temporarily_unavailable");
        assertThat(mobileAuthFailureFor(new BadCredentialsException("no")))
            .isEqualTo("temporarily_unavailable");
    }

    private static String mobileAuthFailureFor(AuthenticationException exception) {
        return SecurityConfig.mobileAuthFailureError(exception);
    }
}
