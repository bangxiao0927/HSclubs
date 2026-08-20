package com.example.demo.mobileauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/mobile-auth/complete}: the school's own web view spends the one-time code for a
 * session on this origin.
 *
 * <p>Redeeming the code validates the school, the expiry, the single-use status and the PKCE
 * verifier (in {@link MobileAuthService#redeem}). On success the authentication captured when the
 * code was issued is written into this request's session, so the WKWebView -- which shares no
 * cookies with the system sign-in browser -- now holds a normal school session, exactly as if the
 * person had logged in on the web.
 */
@RestController
@RequestMapping("/api/mobile-auth")
public class MobileAuthCompleteController {

    private final MobileAuthService service;

    public MobileAuthCompleteController(MobileAuthService service) {
        this.service = service;
    }

    @PostMapping(path = "/complete", consumes = MediaType.APPLICATION_JSON_VALUE)
    public MobileAuthCompleteResponse complete(
        @RequestBody MobileAuthCompleteRequest body,
        HttpServletRequest request
    ) {
        MobileAuthCodeStore.Entry entry = service.redeem(
            body == null ? null : body.schoolId(),
            body == null ? null : body.code(),
            body == null ? null : body.codeVerifier(),
            Instant.now());

        establishSession(entry.authentication(), request);

        // The contract requires a site-relative returnTo; the flow may have started without one, so
        // fall back to the school's root rather than emit a null the app would reject.
        String returnTo = entry.returnTo() != null ? entry.returnTo() : "/";
        return new MobileAuthCompleteResponse(
            entry.schoolId(),
            returnTo,
            describeUser(entry.authentication()));
    }

    private void establishSession(Authentication authentication, HttpServletRequest request) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        // Guard against session fixation: any session the web view already carried is discarded and
        // a fresh one is minted for the authenticated context, so a pre-authentication session id
        // can never be reused as an authenticated one.
        HttpSession existing = request.getSession(false);
        if (existing != null) {
            existing.invalidate();
        }
        // Store under the repository's well-known key so the next request on this origin is
        // authenticated. Writing the attribute directly avoids the response-wrapper contract of
        // saveContext, and creating the session here is what sets the cookie on this origin.
        HttpSession session = request.getSession(true);
        session.setAttribute(
            HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }

    private MobileAuthCompleteResponse.User describeUser(Authentication authentication) {
        // Only role tokens the contract's vocabulary allows (^[A-Z_]{1,40}$): Spring mixes in
        // scope authorities like "SCOPE_openid" that are lower-case and not roles the app should
        // see, so they are dropped rather than sent in a shape the app would reject.
        List<String> roles = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(authority -> authority.matches("^[A-Z_]{1,40}$"))
            .limit(16)
            .toList();
        String displayName = authentication.getName();
        String email = null;
        if (authentication.getPrincipal() instanceof OAuth2User oauthUser) {
            Object name = oauthUser.getAttributes().get("name");
            Object mail = oauthUser.getAttributes().get("email");
            if (name instanceof String s && !s.isBlank()) {
                displayName = s;
            }
            if (mail instanceof String s && !s.isBlank()) {
                email = s;
            }
        }
        return new MobileAuthCompleteResponse.User(displayName, email, roles);
    }
}
