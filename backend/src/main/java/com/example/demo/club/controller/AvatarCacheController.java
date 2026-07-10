package com.example.demo.club.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/avatars")
public class AvatarCacheController {

    private static final long MAX_AVATAR_BYTES = 1024 * 1024;
    private static final String HANDLE_PATTERN = "^[A-Za-z0-9._]{1,64}$";

    private final Path instagramCacheDir;
    private final HttpClient httpClient;

    public AvatarCacheController(@Value("${app.upload.dir:uploads}") String uploadDirPath) {
        Path uploadDir = Paths.get(uploadDirPath).toAbsolutePath().normalize();
        this.instagramCacheDir = uploadDir.resolve("avatar-cache").resolve("instagram").normalize();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        try {
            Files.createDirectories(this.instagramCacheDir);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create avatar cache directory: " + this.instagramCacheDir, e);
        }
    }

    @GetMapping("/instagram/{handle}")
    public ResponseEntity<byte[]> instagramAvatar(@PathVariable String handle) {
        String safeHandle = normalizeHandle(handle);
        Optional<CachedAvatar> cached = readCachedAvatar(safeHandle);
        if (cached.isPresent()) {
            return avatarResponse(cached.get().bytes(), cached.get().mediaType(), 30, TimeUnit.DAYS);
        }

        try {
            CachedAvatar fetched = fetchInstagramAvatar(safeHandle);
            cacheAvatar(safeHandle, fetched);
            return avatarResponse(fetched.bytes(), fetched.mediaType(), 30, TimeUnit.DAYS);
        } catch (Exception ignored) {
            return avatarResponse(fallbackSvg(safeHandle), MediaType.valueOf("image/svg+xml"), 10, TimeUnit.MINUTES);
        }
    }

    private String normalizeHandle(String handle) {
        String normalized = handle == null ? "" : handle.trim().replaceFirst("^@", "");
        if (!normalized.matches(HANDLE_PATTERN)) {
            return "hsclubs";
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private Optional<CachedAvatar> readCachedAvatar(String safeHandle) {
        for (String extension : new String[] { "png", "jpg", "jpeg", "webp", "gif", "svg" }) {
            Path file = instagramCacheDir.resolve(safeHandle + "." + extension).normalize();
            if (!file.startsWith(instagramCacheDir) || !Files.isRegularFile(file)) {
                continue;
            }
            try {
                return Optional.of(new CachedAvatar(Files.readAllBytes(file), mediaTypeForExtension(extension)));
            } catch (IOException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private CachedAvatar fetchInstagramAvatar(String safeHandle) throws IOException, InterruptedException {
        String encodedHandle = URLEncoder.encode(safeHandle, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://unavatar.io/instagram/" + encodedHandle))
            .timeout(Duration.ofSeconds(3))
            .GET()
            .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Avatar provider returned " + response.statusCode());
        }
        byte[] bytes = response.body();
        if (bytes.length == 0 || bytes.length > MAX_AVATAR_BYTES) {
            throw new IOException("Avatar response size is invalid");
        }
        MediaType mediaType = response.headers()
            .firstValue("content-type")
            .map((value) -> value.split(";")[0].trim().toLowerCase(Locale.ROOT))
            .map(MediaType::valueOf)
            .filter((value) -> value.getType().equals("image"))
            .orElse(MediaType.IMAGE_PNG);
        return new CachedAvatar(bytes, mediaType);
    }

    private void cacheAvatar(String safeHandle, CachedAvatar avatar) throws IOException {
        String extension = extensionForMediaType(avatar.mediaType());
        Path file = instagramCacheDir.resolve(safeHandle + "." + extension).normalize();
        if (file.startsWith(instagramCacheDir)) {
            Files.write(file, avatar.bytes());
        }
    }

    private ResponseEntity<byte[]> avatarResponse(byte[] bytes, MediaType mediaType, long maxAge, TimeUnit unit) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(maxAge, unit).cachePublic())
            .contentType(mediaType)
            .body(bytes);
    }

    private byte[] fallbackSvg(String safeHandle) {
        String label = safeHandle.substring(0, Math.min(2, safeHandle.length())).toUpperCase(Locale.ROOT);
        String svg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="160" height="160" viewBox="0 0 160 160">
              <rect width="160" height="160" rx="32" fill="#0f766e"/>
              <circle cx="122" cy="32" r="48" fill="#67e8f9" opacity="0.18"/>
              <text x="50%" y="54%" text-anchor="middle" dominant-baseline="middle" font-family="Arial, sans-serif" font-size="54" font-weight="700" fill="#67e8f9">%s</text>
            </svg>
            """.formatted(label);
        return svg.getBytes(StandardCharsets.UTF_8);
    }

    private MediaType mediaTypeForExtension(String extension) {
        return switch (extension) {
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            case "gif" -> MediaType.IMAGE_GIF;
            case "svg" -> MediaType.valueOf("image/svg+xml");
            case "webp" -> MediaType.valueOf("image/webp");
            default -> MediaType.IMAGE_PNG;
        };
    }

    private String extensionForMediaType(MediaType mediaType) {
        if (MediaType.IMAGE_JPEG.includes(mediaType)) {
            return "jpg";
        }
        if (MediaType.IMAGE_GIF.includes(mediaType)) {
            return "gif";
        }
        if (MediaType.valueOf("image/svg+xml").includes(mediaType)) {
            return "svg";
        }
        if (MediaType.valueOf("image/webp").includes(mediaType)) {
            return "webp";
        }
        return "png";
    }

    private record CachedAvatar(byte[] bytes, MediaType mediaType) {}
}
