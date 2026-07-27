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
import java.util.concurrent.atomic.AtomicInteger;

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
        assertThat(avatar.maxAge()).isEqualTo(60);
        assertThat(avatar.maxAgeUnit()).isEqualTo(TimeUnit.DAYS);
    }

    @Test
    void resolveAvatarReadsLegacyJpegExtensionFromCache() throws Exception {
        // extensionForMediaType only ever WRITES the "jpg" extension, but older cache
        // files were written as ".jpeg" before that convention existed. The read path
        // (IMAGE_EXTENSIONS/mediaTypeForExtension) must keep recognizing them.
        Path cacheDir = tempDir.resolve("avatar-cache").resolve("instagram");
        Files.createDirectories(cacheDir);
        byte[] bytes = new byte[] { 4, 5, 6 };
        Files.write(cacheDir.resolve("mvhsclubs.jpeg"), bytes);
        InstagramAvatarCacheService service = service(true);

        InstagramAvatarCacheService.ResolvedAvatar avatar = service.resolveAvatar("mvhsclubs");

        assertThat(avatar.bytes()).isEqualTo(bytes);
        assertThat(avatar.mediaType()).isEqualTo(MediaType.IMAGE_JPEG);
        assertThat(avatar.maxAge()).isEqualTo(60);
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
    void resolveAvatarNeverSpawnsProcessForHandleNotLinkedToAKnownClub() throws IOException {
        Path invocations = tempDir.resolve("unknown-handle-invocations.txt");
        Files.writeString(invocations, "", StandardCharsets.UTF_8);
        Path script = tempDir.resolve("would-be-invoked.sh");
        Files.writeString(script, "#!/bin/sh\necho x >> \"%s\"\nexit 0\n".formatted(invocations), StandardCharsets.UTF_8);
        assertThat(script.toFile().setExecutable(true)).isTrue();
        // Only "mvhsclubs" is a known club handle -- "randomhandle" is well-formed but unknown.
        InstagramAvatarCacheService service =
            service(true, script.toString(), "http://127.0.0.1/unused?username=%s", 1000, List.of(club("mvhsclubs")));

        InstagramAvatarCacheService.ResolvedAvatar avatar = service.resolveAvatar("randomhandle");

        assertThat(avatar.mediaType().toString()).isEqualTo("image/svg+xml");
        assertThat(Files.readAllLines(invocations, StandardCharsets.UTF_8)).isEmpty();
    }

    @Test
    void resolveAvatarSkipsRetryForRecentlyFailedHandle() throws IOException {
        Path invocations = tempDir.resolve("negative-cache-invocations.txt");
        Files.writeString(invocations, "", StandardCharsets.UTF_8);
        Path script = tempDir.resolve("always-failing-instaloader.sh");
        Files.writeString(script, "#!/bin/sh\necho x >> \"%s\"\nexit 1\n".formatted(invocations), StandardCharsets.UTF_8);
        assertThat(script.toFile().setExecutable(true)).isTrue();
        InstagramAvatarCacheService service = service(true, script.toString(), "http://127.0.0.1/unused?username=%s", 1000);

        service.resolveAvatar("mvhsclubs");
        service.resolveAvatar("mvhsclubs");

        assertThat(Files.readAllLines(invocations, StandardCharsets.UTF_8)).hasSize(1);
    }

    @Test
    void resolveAvatarLimitsConcurrentOnDemandRefreshesAcrossDistinctHandles() throws Exception {
        Path script = tempDir.resolve("slow-instaloader.sh");
        Files.writeString(script, "#!/bin/sh\nsleep 2\nexit 1\n", StandardCharsets.UTF_8);
        assertThat(script.toFile().setExecutable(true)).isTrue();
        InstagramAvatarCacheService service = new InstagramAvatarCacheService(
            clubMapper(List.of(club("mvhsclubs"), club("otherclub"))),
            tempDir.toString(),
            true,
            script.toString(),
            "",
            "",
            "",
            "",
            "http://127.0.0.1/unused?username=%s",
            3000,
            TimeUnit.DAYS.toMillis(30),
            10,
            TimeUnit.DAYS.toMillis(30),
            1 // only one concurrent on-demand refresh allowed
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<InstagramAvatarCacheService.ResolvedAvatar> busy =
                executor.submit(() -> service.resolveAvatar("mvhsclubs"));
            // Give the first call time to acquire the only permit and start sleeping.
            Thread.sleep(300);

            InstagramAvatarCacheService.ResolvedAvatar secondHandleResult = service.resolveAvatar("otherclub");

            assertThat(secondHandleResult.mediaType().toString()).isEqualTo("image/svg+xml");
            assertThat(secondHandleResult.maxAgeUnit()).isEqualTo(TimeUnit.SECONDS);
            busy.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
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
    void resolveAvatarFallsBackToInstagramWebProfileApiWhenInstaloaderFails() throws Exception {
        byte[] bytes = new byte[] { (byte) 0xff, (byte) 0xd8, (byte) 0xff, 1, 2, 3 };
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/profile", exchange -> {
            String body = """
                {"data":{"user":{"profile_pic_url_hd":"http://127.0.0.1:%d/avatar.jpg"}}}
                """.formatted(server.getAddress().getPort());
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/avatar.jpg", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "image/jpg");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        Path script = tempDir.resolve("failing-instaloader.sh");
        Files.writeString(script, "#!/bin/sh\nexit 1\n", StandardCharsets.UTF_8);
        assertThat(script.toFile().setExecutable(true)).isTrue();
        InstagramAvatarCacheService service = service(
            true,
            script.toString(),
            "http://127.0.0.1:%d/profile?username=%%s".formatted(server.getAddress().getPort()),
            4000
        );

        try {
            InstagramAvatarCacheService.ResolvedAvatar avatar = service.resolveAvatar("mvhsclubs");

            assertThat(avatar.bytes()).isEqualTo(bytes);
            assertThat(avatar.mediaType()).isEqualTo(MediaType.IMAGE_JPEG);
        } finally {
            server.stop(0);
        }
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
            "http://127.0.0.1/unused?username=%s",
            1000,
            TimeUnit.DAYS.toMillis(30),
            2,
            TimeUnit.DAYS.toMillis(30),
            10
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
            clubMapper(List.of(club("mvhsclubs"))),
            tempDir.toString(),
            true,
            script.toString(),
            "",
            "",
            "firefox",
            "/tmp/cookies.sqlite",
            "http://127.0.0.1/unused?username=%s",
            1000,
            TimeUnit.DAYS.toMillis(30),
            10,
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

    @Test
    void resolveAvatarRejectsUnsupportedContentTypeAndDoesNotCacheIt() throws Exception {
        // Regression test for: unsupported avatar formats (e.g. AVIF) were previously
        // cached to disk as PNG because extensionForMediaType() silently defaulted to
        // "png" for any unrecognized media type. The fix rejects unsupported formats
        // before they reach the cache, so resolveAvatar must fall back to the generated
        // SVG placeholder and no file should be written for the handle.
        byte[] bytes = { 0, 1, 2, 3, 4, 5, 6, 7 };
        AtomicInteger hits = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/avatar.avif", exchange -> {
            hits.incrementAndGet();
            exchange.getResponseHeaders().add("Content-Type", "image/avif");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        int port = server.getAddress().getPort();
        Path script = tempDir.resolve("avif-instaloader.sh");
        Files.writeString(
            script,
            "#!/bin/sh\necho \"http://127.0.0.1:%d/avatar.avif\"\n".formatted(port),
            StandardCharsets.UTF_8
        );
        assertThat(script.toFile().setExecutable(true)).isTrue();
        InstagramAvatarCacheService service = service(true, script.toString(), 4000);

        try {
            InstagramAvatarCacheService.ResolvedAvatar avatar = service.resolveAvatar("mvhsclubs");

            // The download must actually have happened; otherwise this test would pass
            // just as well for a broken harness (unreachable server, bad script path...).
            assertThat(hits.get()).isGreaterThanOrEqualTo(1);
            // Assert the payload is the GENERATED placeholder (not merely that the media
            // type matches, which the served response also happens to report as SVG).
            assertThat(avatar.mediaType()).isEqualTo(MediaType.valueOf("image/svg+xml"));
            assertThat(new String(avatar.bytes(), StandardCharsets.UTF_8)).contains("<svg").contains("MV");
            assertThat(avatar.maxAge()).isEqualTo(15);
            assertThat(avatar.maxAgeUnit()).isEqualTo(TimeUnit.SECONDS);
            assertThat(cachedAvatarFiles("mvhsclubs")).isEmpty();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void resolveAvatarSniffsMagicBytesWhenContentTypeIsMisleading() throws Exception {
        // A CDN mislabeling a real PNG as application/octet-stream should still be
        // rescued by magic-byte sniffing, cached, and served as image/png thereafter.
        byte[] bytes = { (byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a };
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/avatar.bin", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        int port = server.getAddress().getPort();
        Path script = tempDir.resolve("mislabeled-png-instaloader.sh");
        Files.writeString(
            script,
            "#!/bin/sh\necho \"http://127.0.0.1:%d/avatar.bin\"\n".formatted(port),
            StandardCharsets.UTF_8
        );
        assertThat(script.toFile().setExecutable(true)).isTrue();
        InstagramAvatarCacheService service = service(true, script.toString(), 4000);

        try {
            InstagramAvatarCacheService.ResolvedAvatar first = service.resolveAvatar("mvhsclubs");
            assertThat(first.mediaType()).isEqualTo(MediaType.IMAGE_PNG);
            assertThat(first.bytes()).isEqualTo(bytes);

            InstagramAvatarCacheService.ResolvedAvatar second = service.resolveAvatar("mvhsclubs");
            assertThat(second.mediaType()).isEqualTo(MediaType.IMAGE_PNG);
            assertThat(second.bytes()).isEqualTo(bytes);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void resolveAvatarRejectsSvgContentTypeAndDoesNotCacheIt() throws Exception {
        // The reviewer's other flagged example (image/svg+xml pretending to be a
        // cacheable avatar) must also be rejected rather than cached. The served
        // Content-Type here is ALSO image/svg+xml, so the returned media type alone
        // cannot distinguish "rejected, replaced by the generated placeholder" from
        // "passed straight through" -- assert on the placeholder's actual payload/TTL
        // and on the download having happened instead.
        byte[] bytes = { 0, 1, 2, 3, 4, 5, 6, 7 };
        AtomicInteger hits = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/avatar.svg", exchange -> {
            hits.incrementAndGet();
            exchange.getResponseHeaders().add("Content-Type", "image/svg+xml");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        int port = server.getAddress().getPort();
        Path script = tempDir.resolve("svg-instaloader.sh");
        Files.writeString(
            script,
            "#!/bin/sh\necho \"http://127.0.0.1:%d/avatar.svg\"\n".formatted(port),
            StandardCharsets.UTF_8
        );
        assertThat(script.toFile().setExecutable(true)).isTrue();
        InstagramAvatarCacheService service = service(true, script.toString(), 4000);

        try {
            InstagramAvatarCacheService.ResolvedAvatar avatar = service.resolveAvatar("mvhsclubs");

            // The download must actually have happened; otherwise this test would pass
            // just as well for a broken harness (unreachable server, bad script path...).
            assertThat(hits.get()).isGreaterThanOrEqualTo(1);
            // Assert the payload is the GENERATED placeholder, not the served bytes,
            // and that it carries the placeholder's short TTL rather than the cached
            // 60-day TTL.
            assertThat(avatar.mediaType()).isEqualTo(MediaType.valueOf("image/svg+xml"));
            assertThat(new String(avatar.bytes(), StandardCharsets.UTF_8)).contains("<svg").contains("MV");
            assertThat(avatar.maxAge()).isEqualTo(15);
            assertThat(avatar.maxAgeUnit()).isEqualTo(TimeUnit.SECONDS);
            assertThat(cachedAvatarFiles("mvhsclubs")).isEmpty();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void resolveAvatarCachesAndServesSupportedWebpFormat() throws Exception {
        // Happy-path confirmation that the four supported formats still work end to
        // end after tightening the unsupported-format rejection.
        byte[] bytes = { 'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 1, 2, 3, 4 };
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/avatar.webp", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "image/webp");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        int port = server.getAddress().getPort();
        Path script = tempDir.resolve("webp-instaloader.sh");
        Files.writeString(
            script,
            "#!/bin/sh\necho \"http://127.0.0.1:%d/avatar.webp\"\n".formatted(port),
            StandardCharsets.UTF_8
        );
        assertThat(script.toFile().setExecutable(true)).isTrue();
        InstagramAvatarCacheService service = service(true, script.toString(), 4000);

        try {
            InstagramAvatarCacheService.ResolvedAvatar first = service.resolveAvatar("mvhsclubs");
            assertThat(first.mediaType()).isEqualTo(MediaType.valueOf("image/webp"));
            assertThat(first.bytes()).isEqualTo(bytes);

            InstagramAvatarCacheService.ResolvedAvatar second = service.resolveAvatar("mvhsclubs");
            assertThat(second.mediaType()).isEqualTo(MediaType.valueOf("image/webp"));
            assertThat(second.bytes()).isEqualTo(bytes);
        } finally {
            server.stop(0);
        }
    }

    private List<Path> cachedAvatarFiles(String handle) throws IOException {
        Path cacheDir = tempDir.resolve("avatar-cache").resolve("instagram");
        if (!Files.isDirectory(cacheDir)) {
            return List.of();
        }
        try (var stream = Files.list(cacheDir)) {
            return stream
                .filter(path -> path.getFileName().toString().startsWith(handle + "."))
                .toList();
        }
    }

    private InstagramAvatarCacheService service(boolean enabled) {
        return service(enabled, "python3", "http://127.0.0.1/unused?username=%s", 1000);
    }

    private InstagramAvatarCacheService service(boolean enabled, String pythonCommand, long fetchTimeoutMillis) {
        return service(enabled, pythonCommand, "http://127.0.0.1/unused?username=%s", fetchTimeoutMillis);
    }

    private InstagramAvatarCacheService service(
        boolean enabled,
        String pythonCommand,
        String profileApiUrlTemplate,
        long fetchTimeoutMillis
    ) {
        return service(enabled, pythonCommand, profileApiUrlTemplate, fetchTimeoutMillis, List.of(club("mvhsclubs")));
    }

    private InstagramAvatarCacheService service(
        boolean enabled,
        String pythonCommand,
        String profileApiUrlTemplate,
        long fetchTimeoutMillis,
        List<Club> knownClubs
    ) {
        return new InstagramAvatarCacheService(
            clubMapper(knownClubs),
            tempDir.toString(),
            enabled,
            pythonCommand,
            "",
            "",
            "",
            "",
            profileApiUrlTemplate,
            fetchTimeoutMillis,
            TimeUnit.DAYS.toMillis(30),
            10,
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
