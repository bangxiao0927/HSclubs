package com.example.demo.auth.internal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.example.demo.auth.service.OAuthUserService;
import org.junit.jupiter.api.Test;

class InternalReviewAuthServiceTest {

    @Test
    void rejectsAPartiallyConfiguredAccountAtStartup() {
        InternalReviewAccountProperties properties = new InternalReviewAccountProperties();
        properties.setEmail("review@example.edu");

        assertThatThrownBy(() -> service(properties))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("both APP_INTERNAL_REVIEW_EMAIL");
    }

    @Test
    void rejectsAPlaintextPasswordInsteadOfMistakingItForAHash() {
        InternalReviewAccountProperties properties = new InternalReviewAccountProperties();
        properties.setEmail("review@example.edu");
        properties.setPasswordHash("plaintext-is-not-allowed");

        assertThatThrownBy(() -> service(properties))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("BCrypt");
    }

    @Test
    void rejectsAPartiallyConfiguredSecondaryAccountAtStartup() {
        InternalReviewAccountProperties properties = new InternalReviewAccountProperties();
        properties.setSecondaryEmail("review-2@example.edu");

        assertThatThrownBy(() -> service(properties))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("APP_INTERNAL_REVIEW_SECONDARY_EMAIL");
    }

    private static InternalReviewAuthService service(InternalReviewAccountProperties properties) {
        return new InternalReviewAuthService(
            properties, mock(OAuthUserService.class), new InternalLoginRateLimiter());
    }
}
