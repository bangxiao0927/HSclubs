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
}
