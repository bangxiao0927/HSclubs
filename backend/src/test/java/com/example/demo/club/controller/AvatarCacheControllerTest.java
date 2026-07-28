package com.example.demo.club.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.club.service.InstagramAvatarCacheService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Confirms GET /api/avatars/instagram/{handle} always renders the resolved avatar (never surfaces
 * an exception) regardless of authentication, matching SecurityConfig's permitAll rule for this
 * route. The "reject unknown handles" behavior itself is unit-tested at the service layer
 * (InstagramAvatarCacheServiceTest), since that's where the process-spawn decision is made.
 */
@WebMvcTest(
    controllers = AvatarCacheController.class,
    excludeAutoConfiguration = {
        OAuth2ClientAutoConfiguration.class,
        OAuth2ClientWebSecurityAutoConfiguration.class
    })
@AutoConfigureMockMvc(addFilters = false)
class AvatarCacheControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InstagramAvatarCacheService instagramAvatarCacheService;

    @Test
    void unauthenticatedRequestForUnknownHandleGetsThePlaceholderImage() throws Exception {
        byte[] svg = "<svg>placeholder</svg>".getBytes();
        when(instagramAvatarCacheService.resolveAvatar("randomhandle")).thenReturn(
            new InstagramAvatarCacheService.ResolvedAvatar(svg, MediaType.valueOf("image/svg+xml"), 15, TimeUnit.SECONDS));

        mockMvc.perform(get("/api/avatars/instagram/randomhandle"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("image/svg+xml"))
            .andExpect(content().bytes(svg));
    }
}
