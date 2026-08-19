package com.example.demo.mobileauth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.auth.config.SecurityProperties;
import com.example.demo.summary.config.SchoolIdentity;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * The two endpoints and their error contract, wired standalone so the checks are about the
 * controllers and the advice rather than the whole security filter chain.
 */
class MobileAuthEndpointTest {

    private static final String SCHOOL_ID = "sch_7Qb3Xf9KLm2ZpR4tVn6Y";
    private static final String ORIGIN = "https://clubs.example.edu";
    private static final String CALLBACK = "https://clubs.bangxiao.net/mobile-auth/callback";
    private static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
    private static final String STATE = "M2Rk8sYq1vB7nT4wX0cLpZ";

    private MobileAuthService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = new MobileAuthService(
            new SchoolIdentity(SCHOOL_ID, ORIGIN), new MobileAuthProperties(), new MobileAuthCodeStore());
        SecurityProperties securityProperties = new SecurityProperties();
        mockMvc = MockMvcBuilders
            .standaloneSetup(
                new MobileAuthStartController(service, securityProperties),
                new MobileAuthCompleteController(service))
            .setControllerAdvice(new MobileAuthExceptionHandler())
            .build();
    }

    @Test
    void startValidatesAndRedirectsIntoTheExistingGoogleLogin() throws Exception {
        mockMvc.perform(get("/api/mobile-auth/start")
                .param("schoolId", SCHOOL_ID)
                .param("state", STATE)
                .param("code_challenge", CHALLENGE)
                .param("code_challenge_method", "S256")
                .param("redirect_uri", CALLBACK)
                .param("return_to", "/clubs"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/api/auth/authorize/google"));
    }

    @Test
    void startRejectsATamperedRedirectWithTheContractError() throws Exception {
        mockMvc.perform(get("/api/mobile-auth/start")
                .param("schoolId", SCHOOL_ID)
                .param("state", STATE)
                .param("code_challenge", CHALLENGE)
                .param("code_challenge_method", "S256")
                .param("redirect_uri", "https://evil.example/callback"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.contract").value("hsclubs.mobile-auth-error"))
            .andExpect(jsonPath("$.error").value("invalid_request"));
    }

    @Test
    void startRejectsAnUnknownSchoolWith404() throws Exception {
        mockMvc.perform(get("/api/mobile-auth/start")
                .param("schoolId", "sch_other000000000000")
                .param("state", STATE)
                .param("code_challenge", CHALLENGE)
                .param("code_challenge_method", "S256")
                .param("redirect_uri", CALLBACK))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("unknown_school"));
    }

    // The contract closes the parameter set, so anything unexpected is refused rather than ignored.
    @Test
    void startRejectsAnUnknownParameter() throws Exception {
        mockMvc.perform(get("/api/mobile-auth/start")
                .param("schoolId", SCHOOL_ID)
                .param("state", STATE)
                .param("code_challenge", CHALLENGE)
                .param("code_challenge_method", "S256")
                .param("redirect_uri", CALLBACK)
                .param("prompt", "consent"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("invalid_request"));
    }

    @Test
    void completeSpendsACodeAndReturnsTheContractBody() throws Exception {
        PendingMobileAuth pending = service.validateStart(SCHOOL_ID, STATE, CHALLENGE, "S256", CALLBACK, "/clubs");
        String code = service.issueCode(
            pending,
            new TestingAuthenticationToken("alex@example.edu", "n/a", "ROLE_USER"),
            Instant.now());

        var result = mockMvc.perform(post("/api/mobile-auth/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"schoolId\":\"" + SCHOOL_ID + "\",\"code\":\"" + code + "\",\"code_verifier\":\"" + VERIFIER + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contract").value("hsclubs.mobile-auth-complete"))
            .andExpect(jsonPath("$.schoolId").value(SCHOOL_ID))
            .andExpect(jsonPath("$.returnTo").value("/clubs"))
            .andExpect(jsonPath("$.user.displayName").value("alex@example.edu"))
            .andExpect(jsonPath("$.user.roles[0]").value("ROLE_USER"))
            .andReturn();

        // The session now carries an authenticated security context: the WKWebView is signed in.
        var session = result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNotNull();
    }

    @Test
    void completeRejectsAReplayedCode() throws Exception {
        PendingMobileAuth pending = service.validateStart(SCHOOL_ID, STATE, CHALLENGE, "S256", CALLBACK, null);
        String code = service.issueCode(
            pending, new TestingAuthenticationToken("alex@example.edu", "n/a", "ROLE_USER"), Instant.now());
        String body = "{\"schoolId\":\"" + SCHOOL_ID + "\",\"code\":\"" + code + "\",\"code_verifier\":\"" + VERIFIER + "\"}";

        mockMvc.perform(post("/api/mobile-auth/complete").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/mobile-auth/complete").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    // A flow started without a return path still yields a contract-valid returnTo: the school root,
    // never a null the app would reject.
    @Test
    void completeDefaultsReturnToWhenTheFlowHadNone() throws Exception {
        PendingMobileAuth pending = service.validateStart(SCHOOL_ID, STATE, CHALLENGE, "S256", CALLBACK, null);
        String code = service.issueCode(
            pending, new TestingAuthenticationToken("alex@example.edu", "n/a", "ROLE_USER"), Instant.now());

        mockMvc.perform(post("/api/mobile-auth/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"schoolId\":\"" + SCHOOL_ID + "\",\"code\":\"" + code + "\",\"code_verifier\":\"" + VERIFIER + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.returnTo").value("/"));
    }

    // Session fixation: a session the web view already held is discarded, so its id cannot be
    // reused as an authenticated one.
    @Test
    void completeReplacesAnyPreExistingSession() throws Exception {
        PendingMobileAuth pending = service.validateStart(SCHOOL_ID, STATE, CHALLENGE, "S256", CALLBACK, null);
        String code = service.issueCode(
            pending, new TestingAuthenticationToken("alex@example.edu", "n/a", "ROLE_USER"), Instant.now());

        var preExisting = new org.springframework.mock.web.MockHttpSession();
        preExisting.setAttribute("pre", "value");

        var result = mockMvc.perform(post("/api/mobile-auth/complete")
                .session(preExisting)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"schoolId\":\"" + SCHOOL_ID + "\",\"code\":\"" + code + "\",\"code_verifier\":\"" + VERIFIER + "\"}"))
            .andExpect(status().isOk())
            .andReturn();

        assertThat(preExisting.isInvalid()).isTrue();
        var session = result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        assertThat(session).isNotSameAs(preExisting);
        assertThat(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNotNull();
    }
}
