package com.example.demo.auth.config;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.example.demo.security.CustomOAuth2UserService;
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

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    private final SecurityProperties securityProperties;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final ClientRegistrationRepository clientRegistrationRepository;

    public SecurityConfig(SecurityProperties securityProperties,
                          CustomOAuth2UserService customOAuth2UserService,
                          ClientRegistrationRepository clientRegistrationRepository) {
        this.securityProperties = securityProperties;
        this.customOAuth2UserService = customOAuth2UserService;
        this.clientRegistrationRepository = clientRegistrationRepository;
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
                .requestMatchers(HttpMethod.GET, "/api/avatars/instagram/*").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/summary").permitAll()
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
                .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                .successHandler((request, response, authentication) ->
                    response.sendRedirect(PostLoginRedirectResolver.buildRedirectUri(
                        resolveLoginRedirectUri(), consumeSessionRedirectTarget(request))))
                .failureHandler((request, response, exception) ->
                    response.sendRedirect(PostLoginRedirectResolver.buildRedirectUri(
                        resolveLoginRedirectUri(), consumeSessionRedirectTarget(request), "oauth2_login_failed"))))
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
                "/login/**");
        // Logout is intentionally NOT in the ignore list: it is a real state-changing request
        // triggered by our own frontend (which knows how to attach the token when this flag is
        // on), so it gets the same CSRF protection as any other write.
    }

    /**
     * Path patterns that carry a permitAll() GET in SecurityConfig's authorizeHttpRequests
     * block above (public club data meant for future data-collection clients, plus the cached
     * Instagram avatar images those clients would also want to fetch() rather than just
     * <img>-load). Kept as a constant so the authorization rules and the CORS policy can't
     * silently drift apart.
     */
    private static final List<String> PUBLIC_READ_ONLY_CORS_PATTERNS = List.of(
        "/api/summary",
        "/api/clubs",
        "/api/clubs/calendar",
        "/api/clubs/*",
        "/api/clubs/recommendations",
        "/api/avatars/instagram/*");

    private static final Set<String> SAFE_CORS_METHODS = Set.of(HttpMethod.GET.name(), HttpMethod.HEAD.name());

    /**
     * Two independent CORS policies, not one:
     *
     *  - authenticatedCorsConfiguration(): first-party only (FRONTEND_ORIGIN), credentialed --
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
        authenticatedSource.registerCorsConfiguration("/**", authenticatedCorsConfiguration());

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
     * Comparison is case-insensitive: scheme and host in an Origin header are not
     * case-sensitive, and treating a mismatch in case alone as "not first party" would silently
     * degrade our own frontend to the wildcard, non-credentialed policy.
     */
    private boolean isFirstPartyOrigin(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (!StringUtils.hasText(origin)) {
            return false;
        }
        return securityProperties.getAllowedOrigins().stream()
            .anyMatch(allowedOrigin -> allowedOrigin.equalsIgnoreCase(origin));
    }

    private CorsConfiguration authenticatedCorsConfiguration() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(securityProperties.getAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Cache-Control", "Content-Type", "X-Requested-With"));
        configuration.setAllowCredentials(true);
        return configuration;
    }

    private CorsConfiguration publicReadOnlyCorsConfiguration() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "HEAD", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(false);
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
}
/**
 * Security configuration for the single-school HSclubs platform.
 *
 * Authorization model (defense-in-depth):
 *   1. Spring Security filter-level rules (this file) — safety net for all /api/** routes.
 *   2. Controller-level permission checks (@{code requireManageAccess}, @{code requirePlatformOwner})
 *      — enforce role-specific access (platform owner, club president).
 *
 * Public endpoints (no authentication required):
 *   GET  /api/auth/providers        — OAuth provider list
 *   GET  /api/clubs                 — club listing
 *   GET  /api/clubs/calendar        — weekly schedule
 *   GET  /api/clubs/{id}            — club detail (permissions applied optionally)
 *   GET  /api/avatars/instagram/{handle} — cached public club avatar image
 *   GET  /api/summary               — aggregated stats for aggregator
 *
 * All other /api/** endpoints require authentication.
 * Controller methods enforce further role checks where needed.
 */
