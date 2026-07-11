package com.example.demo.club.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

class InstagramAvatarCacheServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void normalizeHandleStripsAtAndLowercases() {
        InstagramAvatarCacheService service = service(false);

        assertThat(service.normalizeHandle("@MVHS.Clubs")).isEqualTo("mvhs.clubs");
    }

    @Test
    void normalizeHandleFallsBackForUnsafeInput() {
        InstagramAvatarCacheService service = service(false);

        assertThat(service.normalizeHandle("../secret")).isEqualTo("hsclubs");
    }

    @Test
    void resolveAvatarReturnsLocalCacheBeforeFetching() throws Exception {
        Path cacheDir = tempDir.resolve("avatar-cache").resolve("instagram");
        Files.createDirectories(cacheDir);
        byte[] bytes = new byte[] { 1, 2, 3 };
        Files.write(cacheDir.resolve("mvhsclubs.png"), bytes);
        InstagramAvatarCacheService service = service(true);

        InstagramAvatarCacheService.ResolvedAvatar avatar = service.resolveAvatar("mvhsclubs");

        assertThat(avatar.bytes()).isEqualTo(bytes);
        assertThat(avatar.mediaType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(avatar.maxAge()).isEqualTo(30);
        assertThat(avatar.maxAgeUnit()).isEqualTo(TimeUnit.DAYS);
    }

    @Test
    void resolveAvatarFallsBackWhenFetchingIsDisabled() {
        InstagramAvatarCacheService service = service(false);

        InstagramAvatarCacheService.ResolvedAvatar avatar = service.resolveAvatar("mvhsclubs");

        assertThat(avatar.mediaType().toString()).isEqualTo("image/svg+xml");
        assertThat(new String(avatar.bytes())).contains("MV");
        assertThat(avatar.maxAge()).isEqualTo(10);
        assertThat(avatar.maxAgeUnit()).isEqualTo(TimeUnit.MINUTES);
    }

    @Test
    void resolveAvatarTimesOutHungInstaloaderProcess() throws IOException {
        Path sleeper = tempDir.resolve("sleepy-instaloader.sh");
        Files.writeString(sleeper, "#!/bin/sh\nsleep 5\n", StandardCharsets.UTF_8);
        assertThat(sleeper.toFile().setExecutable(true)).isTrue();
        InstagramAvatarCacheService service = service(true, sleeper.toString(), 1000);

        InstagramAvatarCacheService.ResolvedAvatar avatar = assertTimeoutPreemptively(
            Duration.ofSeconds(3),
            () -> service.resolveAvatar("mvhsclubs")
        );

        assertThat(avatar.mediaType().toString()).isEqualTo("image/svg+xml");
    }

    private InstagramAvatarCacheService service(boolean enabled) {
        return service(enabled, "python3", 1000);
    }

    private InstagramAvatarCacheService service(boolean enabled, String pythonCommand, long fetchTimeoutMillis) {
        return new InstagramAvatarCacheService(
            null,
            tempDir.toString(),
            enabled,
            pythonCommand,
            "",
            "",
            fetchTimeoutMillis,
            TimeUnit.DAYS.toMillis(30),
            10
        );
    }
}
