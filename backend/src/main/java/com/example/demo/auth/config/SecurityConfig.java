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
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/auth/me").authenticated()
                .requestMatchers("/api/auth/**", "/oauth2/**", "/login/**", "/error").permitAll()
                .requestMatchers("/api/**").permitAll()
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
