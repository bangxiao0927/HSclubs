package com.example.demo.mobileauth;

import java.io.Serializable;

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
    String returnTo
) implements Serializable {

    public static final String SESSION_ATTRIBUTE = "com.example.demo.mobileauth.PENDING";
}
