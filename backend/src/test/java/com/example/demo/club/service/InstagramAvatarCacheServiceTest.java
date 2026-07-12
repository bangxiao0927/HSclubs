package com.example.demo.club.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.example.demo.club.mapper.ClubMapper;
import com.example.demo.club.model.Club;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
        assertThat(avatar.maxAge()).isEqualTo(15);
        assertThat(avatar.maxAgeUnit()).isEqualTo(TimeUnit.SECONDS);
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

    @Test
    void resolveAvatarCoalescesConcurrentCacheMisses() throws Exception {
        byte[] bytes = new byte[] { 9, 8, 7 };
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/avatar.png", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        int port = server.getAddress().getPort();

        Path invocations = tempDir.resolve("coalesced-invocations.txt");
        Files.writeString(invocations, "", StandardCharsets.UTF_8);
        Path script = tempDir.resolve("coalesced-instaloader.sh");
        Files.writeString(
            script,
            "#!/bin/sh\necho x >> \"%s\"\nsleep 1\necho \"http://127.0.0.1:%d/avatar.png\"\n"
                .formatted(invocations, port),
            StandardCharsets.UTF_8
        );
        assertThat(script.toFile().setExecutable(true)).isTrue();
        InstagramAvatarCacheService service = service(true, script.toString(), 4000);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<InstagramAvatarCacheService.ResolvedAvatar> first = executor.submit(() -> {
                start.await();
                return service.resolveAvatar("mvhsclubs");
            });
            Future<InstagramAvatarCacheService.ResolvedAvatar> second = executor.submit(() -> {
                start.await();
                return service.resolveAvatar("mvhsclubs");
            });

            start.countDown();

            assertThat(first.get(5, TimeUnit.SECONDS).bytes()).isEqualTo(bytes);
            assertThat(second.get(5, TimeUnit.SECONDS).bytes()).isEqualTo(bytes);
        } finally {
            executor.shutdownNow();
            server.stop(0);
        }

        assertThat(Files.readAllLines(invocations, StandardCharsets.UTF_8)).hasSize(1);
    }

    @Test
    void refreshClubInstagramAvatarsCapsFailedAttempts() throws IOException {
        Path invocations = tempDir.resolve("failed-prewarm-invocations.txt");
        Files.writeString(invocations, "", StandardCharsets.UTF_8);
        Path script = tempDir.resolve("failing-instaloader.sh");
        Files.writeString(
            script,
            "#!/bin/sh\nprintf '%s\\n' \"$3\" >> \"%s\"\nexit 1\n".formatted("%s", invocations),
            StandardCharsets.UTF_8
        );
        assertThat(script.toFile().setExecutable(true)).isTrue();
        InstagramAvatarCacheService service = new InstagramAvatarCacheService(
            clubMapper(List.of(club("alpha"), club("beta"), club("gamma"))),
            tempDir.toString(),
            true,
            script.toString(),
            "",
            "",
            "",
            "",
            1000,
            TimeUnit.DAYS.toMillis(30),
            2
        );

        service.refreshClubInstagramAvatars();

        assertThat(Files.readAllLines(invocations, StandardCharsets.UTF_8)).containsExactly("alpha", "beta");
    }

    @Test
    void resolveAvatarPassesBrowserCookieConfigurationToInstaloader() throws Exception {
        Path argsFile = tempDir.resolve("instaloader-args.txt");
        Path script = tempDir.resolve("capturing-instaloader.sh");
        Files.writeString(
            script,
            "#!/bin/sh\necho \"$6\" > \"%s\"\necho \"$7\" >> \"%s\"\nexit 1\n".formatted(argsFile, argsFile),
            StandardCharsets.UTF_8
        );
        assertThat(script.toFile().setExecutable(true)).isTrue();
        InstagramAvatarCacheService service = new InstagramAvatarCacheService(
            null,
            tempDir.toString(),
            true,
            script.toString(),
            "",
            "",
            "firefox",
            "/tmp/cookies.sqlite",
            1000,
            TimeUnit.DAYS.toMillis(30),
            10
        );

        InstagramAvatarCacheService.ResolvedAvatar avatar = service.resolveAvatar("mvhsclubs");

        assertThat(avatar.mediaType().toString()).isEqualTo("image/svg+xml");
        assertThat(Files.readAllLines(argsFile, StandardCharsets.UTF_8)).containsExactly(
            "firefox",
            "/tmp/cookies.sqlite"
        );
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
            "",
            "",
            fetchTimeoutMillis,
            TimeUnit.DAYS.toMillis(30),
            10
        );
    }

    private ClubMapper clubMapper(List<Club> clubs) {
        return (ClubMapper) Proxy.newProxyInstance(
            ClubMapper.class.getClassLoader(),
            new Class<?>[] { ClubMapper.class },
            (proxy, method, args) -> {
                if (method.getName().equals("findAll")) {
                    return clubs;
                }
                if (method.getReturnType().equals(int.class)) {
                    return 0;
                }
                return null;
            }
        );
    }

    private Club club(String handle) {
        Club club = new Club();
        club.setInstagramUrl("https://instagram.com/" + handle);
        return club;
    }
}
