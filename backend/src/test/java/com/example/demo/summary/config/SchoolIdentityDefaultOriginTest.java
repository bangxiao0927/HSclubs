package com.example.demo.summary.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * What happens when {@code app.school.site-origin} is not set and inherits the frontend origin.
 *
 * <p>On a development machine that origin is {@code http://localhost:5173}. Refusing to start over
 * it would make the whole application unbootable for a v1 surface the deployment never asked to
 * publish, so an unusable inherited value means "no v1 surface" -- an already supported state --
 * while an explicitly configured value that cannot work is still refused.
 */
class SchoolIdentityDefaultOriginTest {

    private static final String SCHOOL_ID = "sch_7Qb3Xf9KLm2ZpR4tVn6Y";

    @Test
    void anInheritedNonHttpsOriginPublishesNothingInsteadOfFailingStartup() {
        SchoolIdentity identity =
            new SchoolIdentity(SCHOOL_ID, "http://localhost:5173", "http://localhost:5173");

        assertThat(identity.siteOrigin()).isEmpty();
        assertThat(identity.publishesV1()).isFalse();
    }

    @Test
    void anExplicitNonHttpsOriginIsStillRefused() {
        assertThatThrownBy(() -> new SchoolIdentity(SCHOOL_ID, "http://mvhs.example", "https://mvhs.example"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("https");
    }

    @Test
    void anHttpsOriginPublishesTheV1Surface() {
        SchoolIdentity identity =
            new SchoolIdentity(SCHOOL_ID, "https://mvhs.hsclubs.net", "https://mvhs.hsclubs.net");

        assertThat(identity.siteOrigin()).contains("https://mvhs.hsclubs.net");
        assertThat(identity.publishesV1()).isTrue();
    }
}
