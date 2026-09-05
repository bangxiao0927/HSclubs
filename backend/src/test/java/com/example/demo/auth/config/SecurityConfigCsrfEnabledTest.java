package com.example.demo.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exercises the real {@code SecurityFilterChain} with {@code app.security.csrf.enabled=true}
 * (the opt-in state -- production defaults to {@code false} until the frontend is updated to
 * send the token back). Uses a separate Spring context from every other test class because the
 * property differs.
 */
@SpringBootTest(properties = "app.security.csrf.enabled=true")
@AutoConfigureMockMvc
class SecurityConfigCsrfEnabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anyGetResponseCarriesTheXsrfTokenCookie() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/api/auth/providers")).andReturn().getResponse();

        assertThat(response.getCookie("XSRF-TOKEN")).isNotNull();
        assertThat(response.getCookie("XSRF-TOKEN").getValue()).isNotBlank();
    }

    @Test
    void postWithoutTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
            .andExpect(status().isForbidden());
    }

    @Test
    void postWithMatchingCookieAndHeaderIsAccepted() throws Exception {
        MockHttpServletResponse tokenResponse = mockMvc.perform(get("/api/auth/providers")).andReturn().getResponse();
        Cookie xsrfCookie = tokenResponse.getCookie("XSRF-TOKEN");

        mockMvc.perform(post("/api/auth/logout")
                .cookie(xsrfCookie)
                .header("X-XSRF-TOKEN", xsrfCookie.getValue()))
            .andExpect(status().isNoContent());
    }

    @Test
    void getRequestStillNeverTriggersLogoutEvenWithCsrfEnabled() throws Exception {
        var result = mockMvc.perform(get("/api/auth/logout")).andReturn();

        assertThat(result.getResponse().getStatus()).isNotEqualTo(204);
    }

    // Password sign-in establishes a session, so a cross-site submission of it is worth
    // refusing: otherwise a page on another origin could drop a visitor into the review
    // account's session. It is deliberately not on the CSRF ignore list, and a 403 here (rather
    // than a 401 from the credentials check) is what proves the token is being demanded.
    @Test
    void passwordSignInWithoutTokenIsRejectedBeforeTheCredentialsAreEvenRead() throws Exception {
        mockMvc.perform(post("/api/auth/internal/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"app-review@hsclubs.net\",\"password\":\"anything\"}"))
            .andExpect(status().isForbidden());
    }
}
