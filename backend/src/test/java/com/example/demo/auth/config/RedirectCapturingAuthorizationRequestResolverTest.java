package com.example.demo.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

/**
 * Plain unit tests for {@link RedirectCapturingAuthorizationRequestResolver}
 * — no Spring context needed. The delegate ({@code
 * DefaultOAuth2AuthorizationRequestResolver}) is constructed with a real
 * {@link InMemoryClientRegistrationRepository} holding one dummy Google-like
 * {@link ClientRegistration}, registered under {@link #BASE_URI}, so that a
 * {@code GET {BASE_URI}/google} request is a <em>genuine</em> authorize
 * request as far as the delegate is concerned — it actually returns a
 * non-null {@code OAuth2AuthorizationRequest} — rather than a request the
 * wrapper merely hopes looks like one. That distinction matters because the
 * wrapper only captures the redirect target when the delegate's own outcome
 * says this is a real authorize attempt (see the production class javadoc):
 * a request whose path doesn't match {@link #BASE_URI}{@code /{registrationId}}
 * at all (e.g. {@link #NON_MATCHING_PATH}, or the provider's callback
 * request) makes the delegate return a clean {@code null}, and the wrapper
 * must not touch the session for those.
 *
 * <p>The {@code resolve(request, clientRegistrationId)} overload never
 * consults the request path at all — only the explicit {@code
 * clientRegistrationId} argument matters. Supplying an id that isn't
 * registered (see {@link #UNKNOWN_REGISTRATION_ID}) still counts as a
 * genuine authorize *attempt* (the caller unambiguously asked to build an
 * authorization request for a specific client), so the delegate throws
 * {@code InvalidClientRegistrationIdException} (a package-private subclass
 * of {@link IllegalArgumentException}) rather than returning {@code null}
 * — and the wrapper still captures before letting that exception propagate.
 */
class RedirectCapturingAuthorizationRequestResolverTest {

    /** Same shape as {@code app.security.authorization-request-base-uri}'s default (see {@code application.yaml}). */
    private static final String BASE_URI = "/api/auth/authorize";
    private static final String REGISTERED_ID = "google";
    private static final String MATCHING_PATH = BASE_URI + "/" + REGISTERED_ID;
    private static final String NON_MATCHING_PATH = "/not/an/authorization/request";
    private static final String UNKNOWN_REGISTRATION_ID = "not-registered";

    private final RedirectCapturingAuthorizationRequestResolver resolver =
        new RedirectCapturingAuthorizationRequestResolver(googleClientRegistrationRepository(), BASE_URI);

    @Test
    void storesTheRedirectParamValueInTheSession() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", MATCHING_PATH);
        request.setParameter("redirect", "/clubs/9");

        resolver.resolve(request);

        assertThat(sessionAttribute(request)).isEqualTo("/clubs/9");
    }

    @Test
    void anAuthorizeRequestWithNoRedirectParamClearsAStaleTargetSoItCannotHijackTheNextLogin() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(RedirectCapturingAuthorizationRequestResolver.SESSION_ATTRIBUTE, "/clubs/9");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", MATCHING_PATH);
        request.setSession(session);
        // No "redirect" parameter set: this is an unrelated/abandoned login
        // attempt arriving on a browser session that still remembers a
        // target from an earlier, different login flow.

        resolver.resolve(request);

        assertThat(session.getAttribute(RedirectCapturingAuthorizationRequestResolver.SESSION_ATTRIBUTE)).isNull();
    }

    @Test
    void emptyOrBlankRedirectParamClearsRatherThanStoresAStaleTarget() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(RedirectCapturingAuthorizationRequestResolver.SESSION_ATTRIBUTE, "/clubs/9");
        MockHttpServletRequest emptyRequest = new MockHttpServletRequest("GET", MATCHING_PATH);
        emptyRequest.setSession(session);
        emptyRequest.setParameter("redirect", "");

        resolver.resolve(emptyRequest);

        assertThat(session.getAttribute(RedirectCapturingAuthorizationRequestResolver.SESSION_ATTRIBUTE)).isNull();

        session.setAttribute(RedirectCapturingAuthorizationRequestResolver.SESSION_ATTRIBUTE, "/clubs/9");
        MockHttpServletRequest blankRequest = new MockHttpServletRequest("GET", MATCHING_PATH);
        blankRequest.setSession(session);
        blankRequest.setParameter("redirect", "   ");

        resolver.resolve(blankRequest);

        assertThat(session.getAttribute(RedirectCapturingAuthorizationRequestResolver.SESSION_ATTRIBUTE)).isNull();
    }

    @Test
    void storesAHostilePayloadVerbatimBecauseValidationHappensOnceInPostLoginRedirectResolver() {
        // This class deliberately does NOT semantically validate the
        // redirect target — see PostLoginRedirectResolver, which is the
        // single place that validation happens, right before the value is
        // ever echoed back into a redirect Location header. A
        // protocol-relative value like "//evil.com" is exactly the kind of
        // payload PostLoginRedirectResolver rejects; it is asserted here
        // purely to document (and pin) that this class stores it unexamined
        // (it is well within the length cap, so that narrower check does
        // not interfere either).
        MockHttpServletRequest request = new MockHttpServletRequest("GET", MATCHING_PATH);
        request.setParameter("redirect", "//evil.com");

        resolver.resolve(request);

        assertThat(sessionAttribute(request)).isEqualTo("//evil.com");
    }

    @Test
    void bothResolveOverloadsCaptureTheRedirectTarget() {
        MockHttpServletRequest oneArgRequest = new MockHttpServletRequest("GET", MATCHING_PATH);
        oneArgRequest.setParameter("redirect", "/clubs/1");

        resolver.resolve(oneArgRequest);

        assertThat(sessionAttribute(oneArgRequest)).isEqualTo("/clubs/1");

        MockHttpServletRequest twoArgRequest = new MockHttpServletRequest("GET", NON_MATCHING_PATH);
        twoArgRequest.setParameter("redirect", "/clubs/2");

        // The two-arg overload never looks at the request path — only the
        // explicit registration id matters. UNKNOWN_REGISTRATION_ID isn't in
        // the repository, so the delegate throws rather than returning
        // null; the capture happens before that call fails, so the session
        // assertion still holds.
        assertThatThrownBy(() -> resolver.resolve(twoArgRequest, UNKNOWN_REGISTRATION_ID))
            .isInstanceOf(IllegalArgumentException.class);

        assertThat(sessionAttribute(twoArgRequest)).isEqualTo("/clubs/2");
    }

    @Test
    void aRequestWithNoPreExistingSessionCreatesOneAndStoresAValidTarget() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", MATCHING_PATH);
        request.setParameter("redirect", "/clubs/9");

        assertThat(request.getSession(false)).isNull();

        assertThatCode(() -> resolver.resolve(request)).doesNotThrowAnyException();

        assertThat(request.getSession(false)).isNotNull();
        assertThat(sessionAttribute(request)).isEqualTo("/clubs/9");
    }

    @Test
    void aStoredRedirectTargetSurvivesTheProviderCallbackRequest() {
        // This is the Bug 1 regression: OAuth2AuthorizationRequestRedirectFilter
        // calls resolve(request) on every request that reaches it, including
        // the provider's own callback request once the user has authenticated.
        // That request carries no "redirect" param, so if the wrapper is not
        // gated on the delegate's own decision, it wipes the just-captured
        // target before the success handler ever reads it back — silently
        // breaking the whole feature. The callback path below does not match
        // BASE_URI + "/{registrationId}", so the delegate returns a clean
        // null and this resolver must leave the session alone.
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(RedirectCapturingAuthorizationRequestResolver.SESSION_ATTRIBUTE, "/clubs/9");
        MockHttpServletRequest callbackRequest = new MockHttpServletRequest("GET", "/api/auth/google/callback");
        callbackRequest.setSession(session);
        callbackRequest.setParameter("code", "authorization-code-from-provider");
        callbackRequest.setParameter("state", "state-value-from-provider");

        resolver.resolve(callbackRequest);

        assertThat(session.getAttribute(RedirectCapturingAuthorizationRequestResolver.SESSION_ATTRIBUTE))
            .isEqualTo("/clubs/9");
    }

    @Test
    void aNonAuthorizeRequestWithNoRedirectParamDoesNotCreateASession() {
        // This is the Bug 2 regression: since resolve(request) runs on every
        // request (see the test above), eagerly calling getSession(true)
        // for a request that isn't even an authorize attempt would allocate
        // an HttpSession (and emit a Set-Cookie: JSESSIONID) for every
        // anonymous hit to a public endpoint.
        MockHttpServletRequest callbackRequest = new MockHttpServletRequest("GET", "/api/auth/google/callback");
        callbackRequest.setParameter("code", "authorization-code-from-provider");
        callbackRequest.setParameter("state", "state-value-from-provider");

        assertThat(callbackRequest.getSession(false)).isNull();

        resolver.resolve(callbackRequest);

        assertThat(callbackRequest.getSession(false)).isNull();
    }

    @Test
    void aGenuineAuthorizeRequestWithNoRedirectParamStillClearsAStaleTarget() {
        // The anti-hijack clearing behavior (see
        // anAuthorizeRequestWithNoRedirectParamClearsAStaleTargetSoItCannotHijackTheNextLogin
        // above) must survive the Bug 1 fix for genuine authorize requests,
        // not just disappear along with the over-eager capture-on-every-request
        // behavior that caused Bug 1.
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(RedirectCapturingAuthorizationRequestResolver.SESSION_ATTRIBUTE, "/clubs/9");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", MATCHING_PATH);
        request.setSession(session);

        resolver.resolve(request);

        assertThat(session.getAttribute(RedirectCapturingAuthorizationRequestResolver.SESSION_ATTRIBUTE)).isNull();
    }

    @Test
    void anOverLengthRedirectTargetIsNotStored() {
        // Bug 3: MAX_REDIRECT_LENGTH is enforced here too, before the value
        // ever reaches the session, not only when PostLoginRedirectResolver
        // later reads it back — otherwise an unbounded attacker-controlled
        // string sits in server-side session storage for the lifetime of
        // the OAuth2 round trip for nothing.
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(RedirectCapturingAuthorizationRequestResolver.SESSION_ATTRIBUTE, "/clubs/9");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", MATCHING_PATH);
        request.setSession(session);
        String tooLong = "/" + "a".repeat(PostLoginRedirectResolver.MAX_REDIRECT_LENGTH);
        request.setParameter("redirect", tooLong);

        resolver.resolve(request);

        assertThat(session.getAttribute(RedirectCapturingAuthorizationRequestResolver.SESSION_ATTRIBUTE)).isNull();
    }

    private static Object sessionAttribute(MockHttpServletRequest request) {
        return request.getSession(false).getAttribute(RedirectCapturingAuthorizationRequestResolver.SESSION_ATTRIBUTE);
    }

    private static ClientRegistrationRepository googleClientRegistrationRepository() {
        ClientRegistration google = ClientRegistration.withRegistrationId(REGISTERED_ID)
            .clientId("dummy-client-id")
            .clientSecret("dummy-client-secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
            .tokenUri("https://oauth2.googleapis.com/token")
            .build();
        return new InMemoryClientRegistrationRepository(google);
    }
}
