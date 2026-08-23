package com.example.demo.auth.config;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.io.IOException;
import java.time.Instant;

import com.example.demo.security.CustomOAuth2UserService;
import com.example.demo.security.CustomOidcUserService;
import com.example.demo.security.LoginEligibilityPolicy;
import com.example.demo.mobileauth.MobileAuthProperties;
import com.example.demo.mobileauth.MobileAuthService;
import com.example.demo.mobileauth.PendingMobileAuth;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The authorization model for this single-school deployment, in one place.
 *
 * <h2>Two layers, doing different jobs</h2>
 * <ol>
 *   <li><b>This filter chain answers "who are you".</b> It is a coarse safety net: everything
 *       under {@code /api/**} is denied to an anonymous caller unless it appears in the
 *       permitAll() list below, so a newly added controller is authenticated by default rather
 *       than public by accident. An unauthenticated call gets a bare 401 from
 *       {@link org.springframework.security.web.authentication.HttpStatusEntryPoint} instead of a
 *       redirect to a login page -- the client here is an SPA, not a browser form.</li>
 *   <li><b>Controllers answer "what may you do".</b> Role and ownership checks live in the
 *       controllers ({@code requireManageAccess}, {@code requirePlatformOwner} in
 *       {@code ClubController} and friends), because they depend on the club being addressed and
 *       on the caller's membership row -- neither of which a path pattern can express. Do not
 *       read a bare {@code .authenticated()} below as "any signed-in student may do this".</li>
 * </ol>
 *
 * <h2>What is public, and why</h2>
 * <ul>
 *   <li>{@code OPTIONS /**} -- CORS preflight, which by spec carries no credentials.</li>
 *   <li>{@code /api/auth/**}, {@code /oauth2/**}, {@code /login/**}, {@code /error} -- the login
 *       handshake itself, which by definition runs before there is a session.</li>
 *   <li>{@code GET /api/clubs}, {@code /api/clubs/calendar}, {@code /api/clubs/*},
 *       {@code /api/clubs/recommendations} -- the club information a visitor is meant to browse
 *       before deciding to sign in. Whether a non-active club is visible at all is decided by
 *       {@code ClubVisibilityPolicy}, not here.</li>
 *   <li>{@code GET /api/clubs/{id}/posts} and {@code GET /api/clubs/{id}/posts/{postId}/comments}
 *       -- the public media feed (#78, #79). Separate matchers because a single-star pattern does
 *       not span path segments.</li>
 *   <li>{@code GET /api/avatars/instagram/*} -- cached public avatar images.</li>
 *   <li>{@code GET /api/summary} and {@code GET /api/v1/summary} -- the aggregate the guiding page
 *       polls; counts only, no personal data.</li>
 *   <li>{@code GET /api/mobile-auth/start} and {@code POST /api/mobile-auth/complete} -- the two
 *       ends of the app sign-in flow. Start runs before a session exists and complete is what
 *       creates one, so neither can require authentication; their safety comes from the
 *       allow-listed Universal Link, the single-use code and the PKCE verifier.</li>
 *   <li>Everything outside {@code /api} -- static assets, the SPA's own routes and the
 *       {@code /.well-known} files.</li>
 * </ul>
 *
 * <h2>Sessions</h2>
 * A cookie session ({@code IF_REQUIRED}), deliberately long-lived: {@code
 * server.servlet.session.timeout} and the {@code JSESSIONID} max-age are both 7 days, so a student
 * stays signed in across browser restarts for a week without a remember-me token store (#16).
 * Logout is POST-only and clears the cookie, the session and the SecurityContext.
 *
 * <h2>The two OAuth2 outcome handlers</h2>
 * {@code handleLoginSuccess} and {@code handleLoginFailure} both branch on whether a mobile-auth
 * flow is pending in the session. Web sign-in redirects to the SPA as it always has; an app
 * sign-in is instead returned to the registered Universal Link carrying a one-time {@code code} or
 * an {@code error}. Nothing else about the web login changes, and no token or session identifier
 * ever travels in a URL.
 *
 * <h2>CSRF</h2>
 * See {@link #configureCsrf}: off by default (the SPA does not echo the token yet), on via
 * {@code app.security.csrf.enabled}. CORS is documented on {@link #corsConfigurationSource()},
 * which runs two distinct policies -- first-party credentialed, and anonymous wildcard for the
 * public read-only endpoints.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({SecurityProperties.class, MobileAuthProperties.class})
public class SecurityConfig {

    private final SecurityProperties securityProperties;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final CustomOidcUserService customOidcUserService;
    private final ClientRegistrationRepository clientRegistrationRepository;
    private final MobileAuthService mobileAuthService;

    /**
     * Built once from {@code securityProperties} (immutable after construction) and reused by
     * both {@link #corsConfigurationSource()} and {@link #isFirstPartyOrigin(HttpServletRequest)}
     * so the two never disagree about what counts as a first-party origin -- see the note on
     * {@link #isFirstPartyOrigin(HttpServletRequest)} for why that matters.
     */
    private final CorsConfiguration authenticatedCorsConfiguration;

    public SecurityConfig(SecurityProperties securityProperties,
                          CustomOAuth2UserService customOAuth2UserService,
                          CustomOidcUserService customOidcUserService,
                          ClientRegistrationRepository clientRegistrationRepository,
                          MobileAuthService mobileAuthService) {
        this.securityProperties = securityProperties;
        this.customOAuth2UserService = customOAuth2UserService;
        this.customOidcUserService = customOidcUserService;
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.mobileAuthService = mobileAuthService;
        this.authenticatedCorsConfiguration = buildAuthenticatedCorsConfiguration();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(this::configureCsrf)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .authorizeHttpRequests(authorize -> authorize
                // CORS preflight
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // OAuth2 login flow (must be unauthenticated)
                .requestMatchers("/api/auth/**", "/oauth2/**", "/login/**", "/error").permitAll()
                // Public club data
                .requestMatchers(HttpMethod.GET, "/api/clubs/recommendations").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/clubs").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/clubs/calendar").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/clubs/*").permitAll()
                // Club media feed (#78): "/api/clubs/*" above does not span path segments, so
                // it does not cover this. Deliberately its own matcher, not added to
                // PUBLIC_READ_ONLY_CORS_PATTERNS below -- see that list's Javadoc.
                .requestMatchers(HttpMethod.GET, "/api/clubs/*/posts").permitAll()
                // Club post comments (#79): public read, same reasoning and same exclusion
                // from PUBLIC_READ_ONLY_CORS_PATTERNS as the post feed line above.
                .requestMatchers(HttpMethod.GET, "/api/clubs/*/posts/*/comments").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/avatars/instagram/*").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/summary").permitAll()
                // The versioned summary is the same public data as /api/summary, so it is
                // readable by the same anonymous aggregator. The manifest beside it is outside
                // /api and therefore already covered by anyRequest().permitAll() below.
                .requestMatchers(HttpMethod.GET, "/api/v1/summary").permitAll()
                // Mobile auth starts before there is a session and completes to create one, so
                // both endpoints are reachable unauthenticated. Their safety comes from the
                // one-time code and PKCE, not from a session that does not exist yet.
                .requestMatchers(HttpMethod.GET, "/api/mobile-auth/start").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/mobile-auth/complete").permitAll()
                // Everything else under /api requires authentication
                .requestMatchers("/api/**").authenticated()
                // Static resources, frontend routes
                .anyRequest().permitAll())
            .oauth2Login(oauth2 -> oauth2
                .authorizationEndpoint(authorization -> authorization
                    .baseUri(resolveAuthorizationRequestBaseUri())
                    .authorizationRequestResolver(new RedirectCapturingAuthorizationRequestResolver(
                        clientRegistrationRepository, resolveAuthorizationRequestBaseUri())))
                .redirectionEndpoint(redirection -> redirection.baseUri("/api/auth/*/callback"))
                // Both services, deliberately: the Google registration requests the openid
                // scope, so Spring Security takes the OIDC path and only ever calls the
                // oidcUserService. Wiring just the plain one left the sign-in restrictions and
                // the login record unreachable for the provider this school actually uses.
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(customOAuth2UserService)
                    .oidcUserService(customOidcUserService))
                .successHandler((request, response, authentication) ->
                    handleLoginSuccess(request, response, authentication))
                .failureHandler((request, response, exception) ->
                    handleLoginFailure(request, response, exception)))
            .logout(logout -> logout
                // Explicit POST-only matcher: logoutUrl(String) alone degrades to matching ANY
                // HTTP method once CSRF protection is off, which let a plain
                // <img src="/api/auth/logout"> log out any visitor cross-site. Pinned to POST
                // unconditionally (not just when CSRF is enabled) since the two are independent
                // defenses against different attacks.
                .logoutRequestMatcher(PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/auth/logout"))
                .logoutSuccessHandler((request, response, authentication) ->
                    response.setStatus(HttpServletResponse.SC_NO_CONTENT))
                .deleteCookies("JSESSIONID")
                .clearAuthentication(true)
                .invalidateHttpSession(true));

        if (securityProperties.getCsrf().isEnabled()) {
            http.addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class);
        }

        return http.build();
    }

    /**
     * CSRF enforcement is opt-in via app.security.csrf.enabled (default false): the frontend's
     * fetch() calls don't send the token back yet, so turning this on unconditionally would
     * break every write request. When enabled, the token is exposed through a non-HttpOnly
     * XSRF-TOKEN cookie (read by the SPA) and expected back as the X-XSRF-TOKEN header --
     * Axios and several other HTTP clients do this automatically; a hand-rolled fetch()
     * wrapper needs to do it explicitly. The plain (non-XOR) CsrfTokenRequestAttributeHandler
     * is used deliberately: Spring's BREACH-safe handler defers token rendering in a way that
     * assumes a server-rendered view resolves it, which never happens for a pure JSON API, so
     * following Spring Security's own SPA guidance here instead.
     */
    private void configureCsrf(
        org.springframework.security.config.annotation.web.configurers.CsrfConfigurer<HttpSecurity> csrf) {
        if (!securityProperties.getCsrf().isEnabled()) {
            csrf.disable();
            return;
        }
        csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
            .ignoringRequestMatchers(
                // OAuth2 handshake: these are GET redirects anyway (CSRF only ever checks
                // unsafe methods), but excluded explicitly so a future change to the OAuth2
                // flow can't accidentally start requiring a token the provider has no way to
                // send back.
                resolveAuthorizationRequestBaseUri() + "/**",
                "/api/auth/*/callback",
                "/oauth2/**",
                "/login/**",
                // Complete is a POST from the school's own WKWebView, which has no CSRF cookie of
                // its own; it is protected by the one-time code and PKCE verifier, which is a
                // stronger proof than a CSRF token would be here.
                "/api/mobile-auth/complete");
        // Logout is intentionally NOT in the ignore list: it is a real state-changing request
        // triggered by our own frontend (which knows how to attach the token when this flag is
        // on), so it gets the same CSRF protection as any other write.
    }

    /**
     * Path patterns that carry a permitAll() GET in SecurityConfig's authorizeHttpRequests
     * block above -- but not every one of them: only the ones meant to be readable by
     * literally any origin (public club data meant for future data-collection clients, plus
     * the cached Instagram avatar images those clients would also want to fetch() rather than
     * just <img>-load). GET /api/clubs/{clubSlugOrId}/posts (#78) is deliberately excluded
     * even though it is also permitAll(): a club's activity photos and student display names
     * are not the kind of data this wildcard-CORS list exists to let off-site aggregators
     * script-read, so a cross-origin caller for that path falls through to the authenticated,
     * exact-origin CORS policy instead (see SecurityConfigCorsTest for the behavior this
     * protects). Kept as a constant so *this* list and the CORS policy built from it can't
     * silently drift apart -- it is a deliberate subset of the permitAll() GETs above, not a
     * mechanical mirror of all of them.
     */
    private static final List<String> PUBLIC_READ_ONLY_CORS_PATTERNS = List.of(
        "/api/summary",
        "/api/v1/summary",
        "/.well-known/hsclubs-app.json",
        "/api/clubs",
        "/api/clubs/calendar",
        "/api/clubs/*",
        "/api/clubs/recommendations",
        "/api/avatars/instagram/*");

    private static final Set<String> SAFE_CORS_METHODS = Set.of(HttpMethod.GET.name(), HttpMethod.HEAD.name());

    /**
     * Two independent CORS policies, not one:
     *
     *  - authenticatedCorsConfiguration (built once by buildAuthenticatedCorsConfiguration()):
     *    first-party only (FRONTEND_ORIGIN), credentialed --
     *    everything that touches a session, i.e. all of /api/** by default.
     *  - publicReadOnlyCorsConfiguration(): any origin, no credentials -- the read-only club
     *    data endpoints above, so a future off-site data-collection client (a script or a page
     *    running in someone else's browser) can fetch() public club info without our backend
     *    ever seeing a cookie for it. allowCredentials(true) + allowedOrigins("*") are mutually
     *    exclusive by spec, which is exactly why this needs its own CorsConfiguration rather
     *    than loosening the existing one.
     *
     * The wrinkle: /api/clubs and /api/clubs/{id} carry BOTH a public GET and an authenticated
     * write (POST /api/clubs, PUT/DELETE /api/clubs/{id}) on the exact same path, and
     * UrlBasedCorsConfigurationSource only keys its registrations by path, never by method. So
     * path-based registration alone can't express "public for GET, first-party for POST" on one
     * path. The method dimension is therefore resolved by hand below: the public policy is only
     * ever handed back for GET/HEAD (and an OPTIONS preflight asking for one of those); every
     * other method -- and every preflight asking for another method -- falls through to the
     * authenticated, first-party-only policy. A cross-origin POST to /api/clubs from an
     * arbitrary origin is never opened up, only the read.
     *
     * A second wrinkle, and the reason for the isFirstPartyOrigin() check below: the public
     * policy's allowedOrigins("*") + allowCredentials(false) combination is not just "more
     * permissive" than the authenticated one -- it is a *different, incompatible* response
     * shape. A request whose Origin IS the first-party FRONTEND_ORIGIN must never receive it,
     * because our own frontend's fetch() calls are made with credentials: 'include'. Per the
     * Fetch/CORS spec, a credentialed request that gets back a wildcard
     * Access-Control-Allow-Origin (rather than the literal origin) and no
     * Access-Control-Allow-Credentials: true is rejected by the browser outright. Dispatching
     * on "is this a safe method" alone (the original bug) handed the public, wildcard policy to
     * first-party GETs too, which broke every credentialed read of public club data whenever
     * frontend and backend are cross-origin (any non-same-host deployment, including local dev).
     * So first-party origins are routed to the authenticated source unconditionally, before the
     * method-based public/private split is even considered; only non-first-party origins are
     * eligible for the public, wildcard policy.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource authenticatedSource = new UrlBasedCorsConfigurationSource();
        authenticatedSource.registerCorsConfiguration("/**", authenticatedCorsConfiguration);

        UrlBasedCorsConfigurationSource publicReadOnlySource = new UrlBasedCorsConfigurationSource();
        CorsConfiguration publicReadOnly = publicReadOnlyCorsConfiguration();
        PUBLIC_READ_ONLY_CORS_PATTERNS.forEach(pattern -> publicReadOnlySource.registerCorsConfiguration(pattern, publicReadOnly));

        return request -> {
            if (isFirstPartyOrigin(request)) {
                return authenticatedSource.getCorsConfiguration(request);
            }

            CorsConfiguration publicMatch = publicReadOnlySource.getCorsConfiguration(request);
            return (publicMatch != null && isSafeCorsRequest(request))
                ? publicMatch
                : authenticatedSource.getCorsConfiguration(request);
        };
    }

    /**
     * Whether the request's Origin header is one of the configured first-party origins
     * (app.security.allowed-origins), so it must be routed to the credentialed,
     * exact-origin CORS policy rather than the public, wildcard one -- see the "second
     * wrinkle" note above corsConfigurationSource().
     *
     * A request with no Origin header at all is not a CORS request in the first place (browsers
     * always send Origin on cross-origin fetches, same-origin requests don't need CORS headers
     * and are unaffected by whichever branch runs), so it's treated as not first-party and left
     * to fall through to the existing method-based split.
     *
     * Delegates to {@link CorsConfiguration#checkOrigin(String)} on the very same
     * {@code authenticatedCorsConfiguration} instance used by {@link #corsConfigurationSource()},
     * rather than re-implementing the comparison here. checkOrigin() strips a trailing slash from
     * both the configured origin and the incoming Origin header before comparing (case-
     * insensitively); a hand-rolled equalsIgnoreCase() comparison against the raw configured value
     * would not, so an allowed-origins entry configured with a trailing slash (e.g.
     * "https://app.example.com/") would match here but mismatch there (or vice versa), and a
     * first-party request would silently fall through to the wildcard, non-credentialed policy --
     * reintroducing the exact bug this method exists to prevent. Delegating keeps the two checks
     * permanently in sync.
     */
    private boolean isFirstPartyOrigin(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (!StringUtils.hasText(origin)) {
            return false;
        }
        return authenticatedCorsConfiguration.checkOrigin(origin) != null;
    }

    private CorsConfiguration buildAuthenticatedCorsConfiguration() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(securityProperties.getAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
            "Authorization", "Cache-Control", "Content-Type", "X-Requested-With", "X-XSRF-TOKEN"));
        configuration.setAllowCredentials(true);
        return configuration;
    }

    private CorsConfiguration publicReadOnlyCorsConfiguration() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "HEAD", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(false);
        // ETag is not a CORS-safelisted response header, so without this a page on another
        // origin -- exactly the caller this policy exists for -- could not read the tag off
        // /api/summary and could never start a conditional poll from it.
        configuration.setExposedHeaders(List.of(HttpHeaders.ETAG));
        return configuration;
    }

    /**
     * True for a plain GET/HEAD, or for the preflight that precedes one (identified by the
     * Access-Control-Request-Method header a browser sends on the OPTIONS request -- the
     * request's own HTTP method is always OPTIONS during a preflight, so that has to be
     * consulted instead of request.getMethod()).
     */
    private boolean isSafeCorsRequest(HttpServletRequest request) {
        if (HttpMethod.OPTIONS.name().equalsIgnoreCase(request.getMethod())) {
            String requestedMethod = request.getHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD);
            return requestedMethod == null || SAFE_CORS_METHODS.contains(requestedMethod.toUpperCase(Locale.ROOT));
        }
        return SAFE_CORS_METHODS.contains(request.getMethod().toUpperCase(Locale.ROOT));
    }

    private String resolveLoginRedirectUri() {
        String redirect = securityProperties.getPostLoginRedirectUri();
        return StringUtils.hasText(redirect) ? redirect : "/";
    }

    /**
     * The {@code error} code handed to the frontend after a failed login. Only this project's
     * own eligibility codes are passed through, so a student turned away by the school's
     * sign-in restrictions can be told why; anything else stays the generic code, since a
     * provider-supplied error string is not ours to echo into a redirect.
     */
    private static String resolveLoginFailureCode(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oauth2Exception) {
            String errorCode = oauth2Exception.getError().getErrorCode();
            if (LoginEligibilityPolicy.EMAIL_DOMAIN_NOT_ALLOWED.equals(errorCode)
                || LoginEligibilityPolicy.EMAIL_NOT_VERIFIED.equals(errorCode)) {
                return errorCode;
            }
        }
        return "oauth2_login_failed";
    }

    private String resolveAuthorizationRequestBaseUri() {
        String configured = securityProperties.getAuthorizationRequestBaseUri();
        if (StringUtils.hasText(configured)) {
            return configured;
        }
        return OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI;
    }

    /**
     * Reads back the redirect target that {@link RedirectCapturingAuthorizationRequestResolver}
     * stashed in the session when the authorize request was first made, and
     * removes it so it cannot leak into a later, unrelated login attempt in
     * the same browser session.
     */
    private String consumeSessionRedirectTarget(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object redirectTarget = session.getAttribute(RedirectCapturingAuthorizationRequestResolver.SESSION_ATTRIBUTE);
        session.removeAttribute(RedirectCapturingAuthorizationRequestResolver.SESSION_ATTRIBUTE);
        return redirectTarget instanceof String ? (String) redirectTarget : null;
    }

    /**
     * Login succeeded. A normal web sign-in redirects into the app as before; a sign-in that began
     * at {@code /api/mobile-auth/start} instead issues a one-time code and hands it back on the
     * registered Universal Link, so the app -- not this browser session -- carries the sign-in
     * onward.
     */
    private void handleLoginSuccess(
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication
    ) throws IOException {
        PendingMobileAuth pending = consumePendingMobileAuth(request);
        if (pending != null) {
            String code = mobileAuthService.issueCode(pending, authentication, Instant.now());
            response.sendRedirect(buildMobileAuthCallback(pending, "code", code));
            return;
        }
        response.sendRedirect(PostLoginRedirectResolver.buildRedirectUri(
            resolveLoginRedirectUri(), consumeSessionRedirectTarget(request)));
    }

    /**
     * Login failed. A mobile-auth sign-in is returned to the app on the same Universal Link with
     * an {@code error} -- a cancel or an eligibility refusal the app can act on -- rather than the
     * web login's error redirect, so the person is not left on a page inside the sign-in sheet.
     */
    private void handleLoginFailure(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException exception
    ) throws IOException {
        PendingMobileAuth pending = consumePendingMobileAuth(request);
        if (pending != null) {
            response.sendRedirect(buildMobileAuthCallback(pending, "error", mobileAuthFailureError(exception)));
            return;
        }
        response.sendRedirect(PostLoginRedirectResolver.buildRedirectUri(
            resolveLoginRedirectUri(),
            consumeSessionRedirectTarget(request),
            resolveLoginFailureCode(exception)));
    }

    private PendingMobileAuth consumePendingMobileAuth(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object pending = session.getAttribute(PendingMobileAuth.SESSION_ATTRIBUTE);
        session.removeAttribute(PendingMobileAuth.SESSION_ATTRIBUTE);
        if (!(pending instanceof PendingMobileAuth flow)) {
            return null;
        }
        // An abandoned flow is not a flow. Without this, a mobile sign-in someone gave up on
        // would still be pending when that browser later logs in normally, and the ordinary web
        // login would be redirected to the app's Universal Link instead of into the site.
        return flow.isExpired(Instant.now()) ? null : flow;
    }

    private static String buildMobileAuthCallback(PendingMobileAuth pending, String parameter, String value) {
        return UriComponentsBuilder.fromUriString(pending.redirectUri())
            .queryParam("schoolId", pending.schoolId())
            .queryParam("state", pending.state())
            .queryParam(parameter, value)
            .encode()
            .build()
            .toUriString();
    }

    /**
     * Which contract error the app is told about a failed mobile sign-in.
     *
     * <p>Two outcomes, and the difference matters to the person: {@code access_denied} means this
     * account will not be let in -- they cancelled, or the eligibility policy refused them -- and
     * the app returns them quietly, because retrying changes nothing. Everything else is a fault
     * on our side or the provider's, which is {@code temporarily_unavailable}: the app says so and
     * a retry is worth making. Reporting a provider outage as a cancellation left the person with
     * no explanation for something that was never their doing.
     */
    private static String mobileAuthFailureError(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oauth2Exception) {
            String code = oauth2Exception.getError().getErrorCode();
            if (LoginEligibilityPolicy.EMAIL_DOMAIN_NOT_ALLOWED.equals(code)
                || LoginEligibilityPolicy.EMAIL_NOT_VERIFIED.equals(code)
                || OAuth2ErrorCodes.ACCESS_DENIED.equals(code)) {
                return "access_denied";
            }
            return "temporarily_unavailable";
        }
        return "temporarily_unavailable";
    }
}
