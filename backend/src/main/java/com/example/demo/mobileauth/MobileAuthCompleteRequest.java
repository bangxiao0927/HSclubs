package com.example.demo.mobileauth;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Body of {@code POST /api/mobile-auth/complete}, sent from the school's own web view.
 *
 * <p>This is the only step that carries the PKCE verifier; it never travels in a URL. See
 * contracts/v1/schemas/mobile-auth-complete-request.schema.json.
 */
public record MobileAuthCompleteRequest(
    @JsonProperty("schoolId") String schoolId,
    @JsonProperty("code") String code,
    @JsonProperty("code_verifier") String codeVerifier
) {
}
