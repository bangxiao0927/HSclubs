package com.example.demo.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * With CSRF disabled (the default -- see {@link SecurityConfigCsrfEnabledTest} for the CSRF-on
 * case), Spring Security's logout matcher used to fall back to matching ANY HTTP method, which
 * meant a plain {@code <img src="/api/auth/logout">} from any origin would log a victim out.
 * SecurityConfig now pins the logout matcher to POST explicitly and unconditionally.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigLogoutTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getRequestNeverTriggersLogout() throws Exception {
        var result = mockMvc.perform(get("/api/auth/logout")).andReturn();

        // The logout filter no longer matches GET, so the request falls through to the
        // dispatcher with no mapped handler -- it must NOT be the logout success status (204).
        assertThat(result.getResponse().getStatus()).isNotEqualTo(204);
    }

    @Test
    void postRequestTriggersLogout() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
            .andExpect(status().isNoContent());
    }
}
