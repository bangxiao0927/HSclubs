package com.example.demo.auth.config;

import java.nio.charset.StandardCharsets;

import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

/**
 * Builds the final URL the OAuth2 success/failure handlers redirect the
 * browser to: the configured post-login redirect URI, plus an optional
 * {@code redirect} query parameter carrying the frontend's intended in-app
 * destination, plus an optional {@code error} query parameter.
 *
 * <p><strong>Security:</strong> {@code candidateTarget} is fully
 * attacker-controlled (anyone can link a victim to the authorize endpoint
 * with any {@code redirect=} value, which the {@link
 * RedirectCapturingAuthorizationRequestResolver} stashes verbatim without
 * validation). This class is the single place that value is validated
 * before being echoed back into a redirect {@code Location} header. A
 * rejected value is dropped silently — the bare redirect URI (plus any
 * error param) is returned instead. Login itself never fails because of an
 * invalid redirect target.
 */
public final class PostLoginRedirectResolver {

    /**
     * Defense-in-depth cap on the length of an in-app redirect target we
     * will accept and echo back through the OAuth2 callback. Well beyond
     * anything a legitimate in-app route needs, but short enough to keep
     * the resulting redirect header small.
     */
    static final int MAX_REDIRECT_LENGTH = 2048;

    private static final String REDIRECT_PARAM = "redirect";
    private static final String ERROR_PARAM = "error";

    private PostLoginRedirectResolver() {
    }

    /**
     * Builds the redirect URI with a validated {@code redirect} param, if
     * {@code candidateTarget} is safe, and no {@code error} param.
     */
    public static String buildRedirectUri(String baseRedirectUri, String candidateTarget) {
        return buildRedirectUri(baseRedirectUri, candidateTarget, null);
    }

    /**
     * Builds the redirect URI with a validated {@code redirect} param, if
     * {@code candidateTarget} is safe, and an {@code error} param, if
     * {@code errorCode} is present.
     *
     * <p>Both parameters are percent-encoded exactly once: the raw values
     * are handed to {@link UriComponentsBuilder} and the resulting
     * {@link org.springframework.web.util.UriComponents} is built with
     * {@code build(true)} (components already encoded) so that {@code
     * toUriString()} does not re-encode them.
     */
    public static String buildRedirectUri(String baseRedirectUri, String candidateTarget, String errorCode) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseRedirectUri);

        if (isSafeInAppTarget(candidateTarget)) {
            builder.queryParam(REDIRECT_PARAM, encodeQueryParamForFrontendRouter(candidateTarget));
        }

        if (StringUtils.hasText(errorCode)) {
            // errorCode is always one of our own fixed literal error codes
            // (e.g. "oauth2_login_failed"), never attacker- or user-supplied
            // text, so it can never contain a literal '+'. No need for the
            // same escaping as the redirect target below.
            builder.queryParam(ERROR_PARAM, UriUtils.encodeQueryParam(errorCode, StandardCharsets.UTF_8));
        }

        return builder.build(true).toUriString();
    }

    /**
     * Percent-encodes a value for use as a single query parameter, with one
     * extra pass beyond {@link UriUtils#encodeQueryParam}: a literal
     * {@code +} in the input is additionally escaped to {@code %2B}.
     *
     * <p>{@code +} is a legal, unreserved-by-RFC-3986 character inside a
     * URI query component, so {@code encodeQueryParam} deliberately leaves
     * it untouched. That is correct for RFC 3986 consumers, but vue-router
     * (like most {@code application/x-www-form-urlencoded} query parsers)
     * decodes an unescaped {@code +} in a query string as a space. Left
     * alone, a target such as {@code /search?q=C++} would arrive at the
     * frontend as {@code /search?q=C  }. Escaping it to {@code %2B} here
     * keeps the value byte-for-byte round-trippable by any correct decoder
     * without double-encoding anything else: {@code encodeQueryParam} never
     * itself emits a literal {@code +} (it turns a literal space into
     * {@code %20}), so every {@code +} seen after encoding came from a
     * {@code +} in the original input.
     */
    private static String encodeQueryParamForFrontendRouter(String value) {
        String encoded = UriUtils.encodeQueryParam(value, StandardCharsets.UTF_8);
        return encoded.replace("+", "%2B");
    }

    /**
     * Returns {@code true} only when {@code candidate} is safe to treat as
     * an in-app relative path (optionally carrying its own query string
     * and/or fragment). Rejects (non-exhaustive rationale in parentheses):
     * <ul>
     *   <li>{@code null}/blank values</li>
     *   <li>anything not starting with {@code /} (absolute URLs, scheme-bearing
     *       values such as {@code javascript:...} or {@code mailto:...})</li>
     *   <li>{@code //evil.com} and {@code /\evil.com} (protocol-relative:
     *       browsers treat a leading {@code //} or {@code /\} as "same
     *       scheme, different host")</li>
     *   <li>any value containing {@code ://} (an embedded absolute URL)</li>
     *   <li>values containing CR, LF, TAB, or any other control character
     *       (HTTP header / redirect response splitting)</li>
     *   <li>values longer than {@link #MAX_REDIRECT_LENGTH}</li>
     * </ul>
     */
    static boolean isSafeInAppTarget(String candidate) {
        if (!StringUtils.hasText(candidate)) {
            return false;
        }
        if (candidate.length() > MAX_REDIRECT_LENGTH) {
            return false;
        }
        if (!candidate.startsWith("/")) {
            return false;
        }
        if (candidate.length() > 1) {
            char second = candidate.charAt(1);
            if (second == '/' || second == '\\') {
                return false;
            }
        }
        if (candidate.contains("://")) {
            return false;
        }
        return !containsControlCharacter(candidate);
    }

    private static boolean containsControlCharacter(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
