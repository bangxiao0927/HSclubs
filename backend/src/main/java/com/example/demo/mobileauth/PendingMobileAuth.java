package com.example.demo.mobileauth;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;

/**
 * The mobile-auth request in flight, stashed in the HTTP session between {@code start} and the
 * Google callback.
 *
 * <p>It lives in the session, not in a cookie or a URL, so nothing the app or the provider sees
 * can tamper with the school, the challenge or the return target once the flow has begun. The
 * session here is the ephemeral one inside {@code ASWebAuthenticationSession}, separate from the
 * WKWebView session the {@code complete} step will create.
 */
public record PendingMobileAuth(
    String schoolId,
    String state,
    String codeChallenge,
    String redirectUri,
    String returnTo,
    Instant startedAt
) implements Serializable {

    public static final String SESSION_ATTRIBUTE = "com.example.demo.mobileauth.PENDING";

    /**
     * How long a started flow may sit in the session waiting for the provider to answer.
     *
     * <p>Generous next to the code's own 60-120 seconds, because a person may take a while over a
     * password. The point is not to police that; it is that an abandoned flow must not still be
     * pending when the same browser is later used for an ordinary web login, which would send
     * that login off to the app's Universal Link instead of into the site.
     */
    public static final Duration LIFETIME = Duration.ofMinutes(10);

    public boolean isExpired(Instant now) {
        return startedAt == null || now.isAfter(startedAt.plus(LIFETIME));
    }
}
