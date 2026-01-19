package com.example.demo.auth.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AuthUserSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void avatarUrlIsSerializedWithCamelCaseKey() throws Exception {
        AuthUser user = new AuthUser();
        user.setId("abc123");
        user.setAvatarUrl("https://example.com/avatar.png");

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(user));

        assertThat(json.get("avatarUrl")).isNotNull();
        assertThat(json.get("avatarUrl").asText()).isEqualTo("https://example.com/avatar.png");
        assertThat(json.get("id").asText()).isEqualTo("abc123");
    }
}
