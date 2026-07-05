package com.example.demo.auth.config;

import java.util.List;

import com.example.demo.security.CustomOAuth2UserService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.util.UriComponentsBuilder;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    private final SecurityProperties securityProperties;
    private final CustomOAuth2UserService customOAuth2UserService;

    public SecurityConfig(SecurityProperties securityProperties,
                          CustomOAuth2UserService customOAuth2UserService) {
        this.securityProperties = securityProperties;
        this.customOAuth2UserService = customOAuth2UserService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .authorizeHttpRequests(authorize -> authorize
                // CORS preflight
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // OAuth2 login flow (must be unauthenticated)
                .requestMatchers("/api/auth/**", "/oauth2/**", "/login/**", "/error").permitAll()
                // Public club data
                .requestMatchers(HttpMethod.GET, "/api/clubs").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/clubs/calendar").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/clubs/*").permitAll()
                // Everything else under /api requires authentication
                .requestMatchers("/api/**").authenticated()
                // Static resources, frontend routes
                .anyRequest().permitAll())
            .oauth2Login(oauth2 -> oauth2
                .authorizationEndpoint(authorization -> authorization
                    .baseUri(resolveAuthorizationRequestBaseUri()))
                .redirectionEndpoint(redirection -> redirection.baseUri("/api/auth/*/callback"))
                .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                .successHandler((request, response, authentication) ->
                    response.sendRedirect(resolveLoginRedirectUri()))
                .failureHandler((request, response, exception) -> {
                    String targetUrl = UriComponentsBuilder
                        .fromUriString(resolveLoginRedirectUri())
                        .queryParam("error", "oauth2_login_failed")
                        .build()
                        .toUriString();
                    response.sendRedirect(targetUrl);
                }))
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler((request, response, authentication) ->
                    response.setStatus(HttpServletResponse.SC_NO_CONTENT))
                .deleteCookies("JSESSIONID")
                .clearAuthentication(true)
                .invalidateHttpSession(true));

        return http.build();
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
 *
 * All other /api/** endpoints require authentication.
 * Controller methods enforce further role checks where needed.
 */
