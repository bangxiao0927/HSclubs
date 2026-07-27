package com.example.demo.auth.config;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Forces the deferred {@link CsrfToken} to resolve on every request, which is what actually
 * writes the XSRF-TOKEN cookie to the response.
 *
 * <p>Spring Security 6+ makes CsrfToken resolution lazy for performance: the token supplier
 * placed on the request as an attribute is only evaluated if something calls
 * {@code CsrfToken#getToken()}. Server-rendered apps trigger that naturally when a view reads
 * the token to embed in a hidden form field; a JS SPA that only reads the token back out of a
 * cookie never triggers it on its own. Without this filter the cookie would never be written on
 * a plain GET, and the frontend would have no token to send back on the next write request.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            csrfToken.getToken();
        }
        filterChain.doFilter(request, response);
    }
}
