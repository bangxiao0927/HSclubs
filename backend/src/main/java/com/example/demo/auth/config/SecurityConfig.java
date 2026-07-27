package com.example.demo.auth.config;

import java.util.List;

import com.example.demo.security.CustomOAuth2UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(securityProperties.getAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Cache-Control", "Content-Type", "X-Requested-With"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
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
