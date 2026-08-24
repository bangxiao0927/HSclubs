package com.example.demo.mobileauth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * When a started flow stops counting as pending.
 *
 * <p>Without a cut-off, a mobile sign-in someone abandoned stayed in the session, and the next
 * ordinary web login in that browser was redirected to the app's Universal Link instead of into
 * the site.
 */
class PendingMobileAuthTest {

    private static final Instant STARTED = Instant.parse("2026-08-23T12:00:00Z");

    private static PendingMobileAuth flow(Instant startedAt) {
        return new PendingMobileAuth("sch_x", "state", "challenge", "https://cb.example/x", null, startedAt);
    }

    @Test
    void aFreshFlowIsStillPending() {
        assertThat(flow(STARTED).isExpired(STARTED)).isFalse();
        assertThat(flow(STARTED).isExpired(STARTED.plusSeconds(9 * 60))).isFalse();
    }

    @Test
    void aFlowOlderThanItsLifetimeIsAbandoned() {
        assertThat(flow(STARTED).isExpired(STARTED.plus(PendingMobileAuth.LIFETIME).plusSeconds(1)))
            .isTrue();
    }

    // A flow deserialized from a session written before this field existed has no start time.
    // Treating that as still pending would preserve exactly the hijack this cut-off removes.
    @Test
    void aFlowWithNoStartTimeIsAbandoned() {
        assertThat(flow(null).isExpired(STARTED)).isTrue();
    }
}
