package com.example.demo.mobileauth;

import com.example.demo.auth.config.SecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Set;
import org.springframework.security.web.util.UrlUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/mobile-auth/start}: the fixed entry point the app's web view is sent to.
 *
 * <p>It validates the request into a pending flow, stores that flow in the session, and then
 * hands off to the school's existing Google login by redirecting to the same authorization
 * endpoint an ordinary web sign-in uses. Nothing about the normal browser login changes: the only
 * difference is the pending flow left in the session, which the success handler notices to issue a
 * one-time code instead of the usual redirect.
 */
@RestController
@RequestMapping("/api/mobile-auth")
public class MobileAuthStartController {

    private static final String DEFAULT_AUTHORIZE_BASE = "/api/auth/authorize";
    private static final String REGISTRATION_ID = "google";
    /** Keeps an echoed parameter name well inside the contract's 200-character description. */
    private static final int MAX_ECHOED_PARAMETER = 40;
    /** The only parameters the start entry accepts; the contract is additionalProperties:false. */
    private static final Set<String> ALLOWED_PARAMETERS = Set.of(
        "schoolId", "state", "code_challenge", "code_challenge_method", "redirect_uri", "return_to");

    private final MobileAuthService service;
    private final SecurityProperties securityProperties;

    public MobileAuthStartController(MobileAuthService service, SecurityProperties securityProperties) {
        this.service = service;
        this.securityProperties = securityProperties;
    }

    @GetMapping("/start")
    public void start(
        @RequestParam(name = "schoolId", required = false) String schoolId,
        @RequestParam(name = "state", required = false) String state,
        @RequestParam(name = "code_challenge", required = false) String codeChallenge,
        @RequestParam(name = "code_challenge_method", required = false) String codeChallengeMethod,
        @RequestParam(name = "redirect_uri", required = false) String redirectUri,
        @RequestParam(name = "return_to", required = false) String returnTo,
        HttpServletRequest request,
        HttpServletResponse response
    ) throws IOException {
        // The contract closes this parameter set: a request carrying anything else is malformed and
        // refused, rather than silently ignored, so a parameter the school would have to act on can
        // never slip through unrecognised.
        for (String name : request.getParameterMap().keySet()) {
            if (!ALLOWED_PARAMETERS.contains(name)) {
                // The name is echoed so an operator reading a log can see what was sent, but it
                // came from the caller: cut it, and let the schema's own 200-character ceiling on
                // error_description hold no matter what arrived.
                throw new MobileAuthException(
                    MobileAuthException.Error.INVALID_REQUEST,
                    "unexpected parameter: " + abbreviate(name));
            }
        }

        PendingMobileAuth pending = service.validateStart(
            schoolId, state, codeChallenge, codeChallengeMethod, redirectUri, returnTo);

        // A fresh session for the flow, so an unrelated login already in this browser cannot
        // supply the pending state.
        HttpSession session = request.getSession(true);
        session.setAttribute(PendingMobileAuth.SESSION_ATTRIBUTE, pending);

        // Relative redirect to this app's own authorization endpoint; the redirect stays on this
        // origin, so it is not an open redirect regardless of the (already validated) parameters.
        response.sendRedirect(authorizeUri());
    }

    private String authorizeUri() {
        String base = securityProperties.getAuthorizationRequestBaseUri();
        if (!StringUtils.hasText(base)) {
            base = DEFAULT_AUTHORIZE_BASE;
        }
        String normalized = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return normalized + "/" + REGISTRATION_ID;
    }

    /** Guards against a base URI that is somehow absolute; the redirect must stay on this origin. */
    static boolean isRelative(String uri) {
        return uri != null && !UrlUtils.isAbsoluteUrl(uri);
    }

    private static String abbreviate(String value) {
        return value.length() <= MAX_ECHOED_PARAMETER ? value : value.substring(0, MAX_ECHOED_PARAMETER);
    }
}
