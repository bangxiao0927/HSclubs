package com.example.demo.auth.config;

import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.util.StringUtils;

/**
 * Wraps the {@link DefaultOAuth2AuthorizationRequestResolver} to also
 * capture the frontend's intended post-login destination — the {@code
 * redirect} query parameter on the authorize request — and stash it in the
 * {@link HttpSession} for the duration of the OAuth2 round trip, so the
 * success/failure handlers can echo it back into the callback redirect
 * (see {@link PostLoginRedirectResolver}).
 *
 * <p><strong>Only capture on genuine authorize requests.</strong>
 * {@code OAuth2AuthorizationRequestRedirectFilter} (the framework class that
 * drives this resolver) calls {@code resolve(request)} unconditionally on
 * <em>every</em> request that reaches it, not just ones aimed at the
 * authorization endpoint, and only null-checks the result afterwards. The
 * delegate only returns a non-null {@link OAuth2AuthorizationRequest} (or
 * throws, e.g. {@code InvalidClientRegistrationIdException}) once it has
 * already decided — from the request path for {@link #resolve(HttpServletRequest)},
 * or from a non-null {@code clientRegistrationId} for
 * {@link #resolve(HttpServletRequest, String)} — that this genuinely is an
 * attempt to build an authorization request. A clean {@code null} return
 * means the opposite: the request doesn't match the authorize shape at all
 * (e.g. the provider's callback request, an unrelated API call, a static
 * asset). We treat "the delegate produced a result, successfully or not" as
 * that signal, so the session is touched only for real authorize attempts —
 * never for the callback request or any other unrelated request in the same
 * session, which would otherwise wipe the just-captured target before the
 * success/failure handler ever reads it back.
 *
 * <p><strong>No session is created just to clear a value.</strong> A
 * request that turns out not to carry a {@code redirect} param, but that IS
 * a genuine authorize attempt, still clears any previously-stashed value
 * (an anti-hijack measure: a login attempt with no explicit target must not
 * inherit a stale one from an earlier, abandoned attempt in the same
 * browser session). That clearing only touches a session that already
 * exists ({@code getSession(false)}); it never eagerly allocates one. A
 * session is created ({@code getSession(true)}) only when there is an
 * actual value to store.
 *
 * <p>The captured value is NOT semantically validated here: it is fully
 * attacker-controlled (anyone can send a victim a link to the authorize
 * endpoint with any {@code redirect=} value) and is deliberately kept out
 * of the OAuth {@code state} parameter or any other value that round-trips
 * through the identity provider, since that would make it attacker-visible
 * and attacker-modifiable in transit. Stashing the raw value in the
 * server-side session is safe because it is only ever read back by this
 * same server, for this same browser session, and is semantically
 * validated exactly once — in {@link PostLoginRedirectResolver} — before it
 * is ever echoed back into a redirect {@code Location} header. The one
 * exception is the length cap below, applied here too, before storing, so
 * an unbounded attacker-controlled string can't sit in server-side session
 * storage for the lifetime of the OAuth2 round trip; see the field javadoc
 * for why that specific, narrow check is duplicated rather than shared.
 */
public class RedirectCapturingAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    /** Package-visible so {@link SecurityConfig} can read it back after login. */
    static final String SESSION_ATTRIBUTE =
        RedirectCapturingAuthorizationRequestResolver.class.getName() + ".REDIRECT_TARGET";

    private static final String REDIRECT_PARAM = "redirect";

    private final DefaultOAuth2AuthorizationRequestResolver delegate;

    public RedirectCapturingAuthorizationRequestResolver(
            ClientRegistrationRepository clientRegistrationRepository, String authorizationRequestBaseUri) {
        this.delegate =
            new DefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepository, authorizationRequestBaseUri);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return resolveAndCaptureIfGenuineAttempt(request, () -> delegate.resolve(request));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return resolveAndCaptureIfGenuineAttempt(request, () -> delegate.resolve(request, clientRegistrationId));
    }

    /**
     * Invokes {@code delegateCall} and captures the redirect target only
     * when the delegate's own outcome shows this was a genuine authorize
     * attempt: it produced an {@link OAuth2AuthorizationRequest}, or it
     * threw trying to (e.g. an authorize-shaped path with an unknown
     * registration id). A clean {@code null} return — the delegate's own
     * signal that this request doesn't match the authorize shape at all —
     * skips capture entirely.
     */
    private OAuth2AuthorizationRequest resolveAndCaptureIfGenuineAttempt(
            HttpServletRequest request, Supplier<OAuth2AuthorizationRequest> delegateCall) {
        OAuth2AuthorizationRequest authorizationRequest;
        try {
            authorizationRequest = delegateCall.get();
        } catch (RuntimeException ex) {
            captureRedirectTarget(request);
            throw ex;
        }
        if (authorizationRequest != null) {
            captureRedirectTarget(request);
        }
        return authorizationRequest;
    }

    private void captureRedirectTarget(HttpServletRequest request) {
        String redirectTarget = request.getParameter(REDIRECT_PARAM);
        if (StringUtils.hasText(redirectTarget) && redirectTarget.length() <= PostLoginRedirectResolver.MAX_REDIRECT_LENGTH) {
            request.getSession(true).setAttribute(SESSION_ATTRIBUTE, redirectTarget);
            return;
        }
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(SESSION_ATTRIBUTE);
        }
    }
}
