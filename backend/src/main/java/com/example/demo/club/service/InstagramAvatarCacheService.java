package com.example.demo.club.service;

import com.example.demo.club.mapper.ClubMapper;
import com.example.demo.club.model.Club;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Service
public class InstagramAvatarCacheService {

    private static final Logger LOGGER = LoggerFactory.getLogger(InstagramAvatarCacheService.class);
    private static final long MAX_AVATAR_BYTES = 1024 * 1024;
    private static final Pattern HANDLE_PATTERN = Pattern.compile("^[A-Za-z0-9._]{1,64}$");
    private static final List<String> IMAGE_EXTENSIONS = List.of("png", "jpg", "jpeg", "webp", "gif");
    private static final MediaType SVG_MEDIA_TYPE = MediaType.valueOf("image/svg+xml");
    private static final MediaType WEBP_MEDIA_TYPE = MediaType.valueOf("image/webp");
    private static final String INSTAGRAM_WEB_PROFILE_API =
        "https://www.instagram.com/api/v1/users/web_profile_info/?username=%s";
    private static final String BROWSER_USER_AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String INSTALOADER_SCRIPT = """
        import sys
        from pathlib import Path
        import instaloader

        handle = sys.argv[1]
        session_user = sys.argv[2].strip() if len(sys.argv) > 2 else ""
        session_file = sys.argv[3].strip() if len(sys.argv) > 3 else ""
        cookie_browser = sys.argv[4].strip().lower() if len(sys.argv) > 4 else ""
        cookie_file = sys.argv[5].strip() if len(sys.argv) > 5 else ""

        loader = instaloader.Instaloader(
            download_pictures=False,
            download_videos=False,
            download_video_thumbnails=False,
            download_geotags=False,
            download_comments=False,
            save_metadata=False,
            compress_json=False,
            quiet=True,
            max_connection_attempts=1,
            request_timeout=8.0,
        )
        if session_user:
            loader.load_session_from_file(session_user, session_file or None)
        elif cookie_browser:
            def load_firefox_cookies():
                import sqlite3
                candidates = []
                if cookie_file:
                    candidates.append(Path(cookie_file).expanduser())
                else:
                    home = Path.home()
                    candidates.extend(home.glob("Library/Application Support/Firefox/Profiles/*/cookies.sqlite"))
                    candidates.extend(home.glob(".mozilla/firefox/*/cookies.sqlite"))
                    candidates.extend(home.glob("AppData/Roaming/Mozilla/Firefox/Profiles/*/cookies.sqlite"))
                cookies = {}
                for candidate in sorted(set(candidates), key=lambda path: path.stat().st_mtime if path.exists() else 0, reverse=True):
                    if not candidate.is_file():
                        continue
                    connection = sqlite3.connect(candidate.resolve().as_uri() + "?mode=ro&immutable=1", uri=True)
                    try:
                        rows = connection.execute(
                            "SELECT host, name, value FROM moz_cookies WHERE host LIKE ?",
                            ("%instagram%",),
                        ).fetchall()
                    finally:
                        connection.close()
                    for _, name, value in rows:
                        cookies[name] = value
                    if cookies:
                        break
                return cookies

            if cookie_browser == "firefox":
                cookies = load_firefox_cookies()
            else:
                import browser_cookie3
                supported_browsers = {
                    "brave": browser_cookie3.brave,
                    "chrome": browser_cookie3.chrome,
                    "chromium": browser_cookie3.chromium,
                    "edge": browser_cookie3.edge,
                    "librewolf": browser_cookie3.librewolf,
                    "opera": browser_cookie3.opera,
                    "opera_gx": browser_cookie3.opera_gx,
                    "safari": browser_cookie3.safari,
                    "vivaldi": browser_cookie3.vivaldi,
                }
                if cookie_browser not in supported_browsers:
                    raise RuntimeError("Unsupported browser for Instagram cookies: " + cookie_browser)
                cookies = {}
                for cookie in supported_browsers[cookie_browser](cookie_file=cookie_file or None):
                    if "instagram" in cookie.domain:
                        cookies[cookie.name] = cookie.value
            if not cookies:
                raise RuntimeError("No Instagram cookies found in " + cookie_browser)
            loader.context.update_cookies(cookies)
            username = loader.test_login()
            if not username:
                raise RuntimeError("Instagram cookies are present but not logged in")
            loader.context.username = username
        profile = instaloader.Profile.from_username(loader.context, handle)
        url = profile.profile_pic_url or profile.get_profile_pic_url()
        print(url)
        """;

    private final ClubMapper clubMapper;
    private final Path instagramCacheDir;
    private final HttpClient httpClient;
    private final String pythonCommand;
    private final String sessionUser;
    private final String sessionFile;
    private final String cookieBrowser;
    private final String cookieFile;
    private final String profileApiUrlTemplate;
    private final boolean enabled;
    private final long fetchTimeoutMillis;
    private final long cacheTtlMillis;
    private final int maxRefreshPerRun;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, CompletableFuture<RefreshResult>> inFlightRefreshes;

    public InstagramAvatarCacheService(
        ClubMapper clubMapper,
        @Value("${app.upload.dir:uploads}") String uploadDirPath,
        @Value("${app.avatar.instagram.enabled:true}") boolean enabled,
        @Value("${app.avatar.instagram.python-command:python3}") String pythonCommand,
        @Value("${app.avatar.instagram.session-user:}") String sessionUser,
        @Value("${app.avatar.instagram.session-file:}") String sessionFile,
        @Value("${app.avatar.instagram.cookie-browser:}") String cookieBrowser,
        @Value("${app.avatar.instagram.cookie-file:}") String cookieFile,
        @Value("${app.avatar.instagram.profile-api-url-template:" + INSTAGRAM_WEB_PROFILE_API + "}") String profileApiUrlTemplate,
        @Value("${app.avatar.instagram.fetch-timeout-ms:10000}") long fetchTimeoutMillis,
        @Value("${app.avatar.instagram.cache-ttl-ms:5184000000}") long cacheTtlMillis,
        @Value("${app.avatar.instagram.max-refresh-per-run:120}") int maxRefreshPerRun
    ) {
        this.clubMapper = clubMapper;
        Path uploadDir = Paths.get(uploadDirPath).toAbsolutePath().normalize();
        this.instagramCacheDir = uploadDir.resolve("avatar-cache").resolve("instagram").normalize();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        this.enabled = enabled;
        this.pythonCommand = pythonCommand;
        this.sessionUser = sessionUser == null ? "" : sessionUser.trim();
        this.sessionFile = sessionFile == null ? "" : sessionFile.trim();
        this.cookieBrowser = cookieBrowser == null ? "" : cookieBrowser.trim();
        this.cookieFile = cookieFile == null ? "" : cookieFile.trim();
        this.profileApiUrlTemplate =
            profileApiUrlTemplate == null || profileApiUrlTemplate.isBlank()
                ? INSTAGRAM_WEB_PROFILE_API
                : profileApiUrlTemplate.trim();
        this.fetchTimeoutMillis = Math.max(1000, fetchTimeoutMillis);
        this.cacheTtlMillis = Math.max(TimeUnit.HOURS.toMillis(1), cacheTtlMillis);
        this.maxRefreshPerRun = Math.max(1, maxRefreshPerRun);
        this.objectMapper = new ObjectMapper();
        this.inFlightRefreshes = new ConcurrentHashMap<>();
        try {
            Files.createDirectories(this.instagramCacheDir);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create Instagram avatar cache directory: " + this.instagramCacheDir, e);
        }
    }

    public ResolvedAvatar resolveAvatar(String handle) {
        String safeHandle = normalizeHandle(handle);
        Optional<CachedAvatar> cached = readCachedAvatar(safeHandle);
        if (cached.isPresent()) {
            return ResolvedAvatar.cached(cached.get());
        }
        if (!enabled) {
            return ResolvedAvatar.fallback(fallbackSvg(safeHandle));
        }
        try {
            return refreshAvatarIfNeeded(safeHandle, false).avatar()
                .map(ResolvedAvatar::cached)
                .orElseGet(() -> ResolvedAvatar.fallback(fallbackSvg(safeHandle)));
        } catch (Exception ex) {
            LOGGER.debug("Unable to fetch Instagram avatar for {}", safeHandle, ex);
            return ResolvedAvatar.fallback(fallbackSvg(safeHandle));
        }
    }

    @Scheduled(
        initialDelayString = "${app.avatar.instagram.refresh-initial-delay-ms:30000}",
        fixedDelayString = "${app.avatar.instagram.refresh-fixed-delay-ms:43200000}"
    )
    public void refreshClubInstagramAvatars() {
        if (!enabled) {
            return;
        }
        Set<String> handles = instagramHandlesFromClubs(clubMapper.findAll());
        int attempted = 0;
        int refreshed = 0;
        for (String handle : handles) {
            if (attempted >= maxRefreshPerRun) {
                break;
            }
            if (hasFreshCachedAvatar(handle)) {
                continue;
            }
            attempted++;
            try {
                if (refreshAvatarIfNeeded(handle, true).refreshed()) {
                    refreshed++;
                }
            } catch (Exception ex) {
                LOGGER.debug("Unable to prewarm Instagram avatar for {}", handle, ex);
            }
        }
        LOGGER.info(
            "Instagram avatar check complete: {} handle(s), {} attempted, {} refreshed",
            handles.size(),
            attempted,
            refreshed
        );
    }

    public String normalizeHandle(String handle) {
        String normalized = handle == null ? "" : handle.trim().replaceFirst("^@", "");
        if (!HANDLE_PATTERN.matcher(normalized).matches()) {
            return "hsclubs";
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private Set<String> instagramHandlesFromClubs(List<Club> clubs) {
        Set<String> handles = new LinkedHashSet<>();
        for (Club club : clubs) {
            extractInstagramHandle(club.getInstagramUrl()).ifPresent(handles::add);
        }
        return handles;
    }

    private Optional<String> extractInstagramHandle(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        String direct = trimmed.replaceFirst("^@", "");
        if (HANDLE_PATTERN.matcher(direct).matches()) {
            return Optional.of(direct.toLowerCase(Locale.ROOT));
        }
        String normalizedUrl = trimmed.matches("(?i)^https?://.*") ? trimmed : "https://" + trimmed;
        try {
            URI uri = URI.create(normalizedUrl);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
            if (!host.endsWith("instagram.com")) {
                return Optional.empty();
            }
            String path = uri.getPath() == null ? "" : uri.getPath();
            for (String segment : path.split("/")) {
                String candidate = segment.trim();
                if (candidate.isEmpty()) {
                    continue;
                }
                String lower = candidate.toLowerCase(Locale.ROOT);
                if (Set.of("p", "reel", "reels", "stories", "explore").contains(lower)) {
                    return Optional.empty();
                }
                return HANDLE_PATTERN.matcher(candidate).matches()
                    ? Optional.of(candidate.toLowerCase(Locale.ROOT))
                    : Optional.empty();
            }
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private boolean hasFreshCachedAvatar(String safeHandle) {
        for (String extension : IMAGE_EXTENSIONS) {
            Path file = instagramCacheDir.resolve(safeHandle + "." + extension).normalize();
            if (!file.startsWith(instagramCacheDir) || !Files.isRegularFile(file)) {
                continue;
            }
            try {
                Instant modified = Files.getLastModifiedTime(file).toInstant();
                return modified.plusMillis(cacheTtlMillis).isAfter(Instant.now());
            } catch (IOException ignored) {
                return false;
            }
        }
        return false;
    }

    private Optional<CachedAvatar> readCachedAvatar(String safeHandle) {
        for (String extension : IMAGE_EXTENSIONS) {
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

    private CachedAvatar refreshAvatar(String safeHandle) throws IOException, InterruptedException {
        String profilePicUrl = fetchProfilePicUrl(safeHandle);
        CachedAvatar avatar = downloadAvatar(profilePicUrl);
        cacheAvatar(safeHandle, avatar);
        return avatar;
    }

    private RefreshResult refreshAvatarIfNeeded(String safeHandle, boolean requireFreshCache)
        throws IOException, InterruptedException {
        CompletableFuture<RefreshResult> future = new CompletableFuture<>();
        CompletableFuture<RefreshResult> inFlight = inFlightRefreshes.putIfAbsent(safeHandle, future);
        if (inFlight != null) {
            return waitForInFlightRefresh(safeHandle, inFlight);
        }
        try {
            Optional<CachedAvatar> cached = readCachedAvatar(safeHandle);
            if (cached.isPresent() && (!requireFreshCache || hasFreshCachedAvatar(safeHandle))) {
                RefreshResult result = new RefreshResult(cached, false);
                future.complete(result);
                return result;
            }
            RefreshResult result = new RefreshResult(Optional.of(refreshAvatar(safeHandle)), true);
            future.complete(result);
            return result;
        } catch (IOException | InterruptedException | RuntimeException ex) {
            future.completeExceptionally(ex);
            throw ex;
        } finally {
            inFlightRefreshes.remove(safeHandle, future);
        }
    }

    private RefreshResult waitForInFlightRefresh(String safeHandle, CompletableFuture<RefreshResult> inFlight)
        throws IOException, InterruptedException {
        try {
            return inFlight.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw ex;
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            if (cause instanceof InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                throw interruptedException;
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IOException("Unable to join in-flight Instagram avatar refresh for " + safeHandle, cause);
        }
    }

    private String fetchProfilePicUrl(String safeHandle) throws IOException, InterruptedException {
        try {
            return fetchProfilePicUrlWithInstaloader(safeHandle);
        } catch (IOException instaloaderException) {
            LOGGER.debug(
                "Instaloader avatar lookup failed for {}; trying Instagram web profile API",
                safeHandle,
                instaloaderException
            );
            Optional<String> webProfileUrl = fetchProfilePicUrlFromWebProfileApi(safeHandle);
            if (webProfileUrl.isPresent()) {
                return webProfileUrl.get();
            }
            throw instaloaderException;
        }
    }

    private String fetchProfilePicUrlWithInstaloader(String safeHandle) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(
            pythonCommand,
            "-c",
            INSTALOADER_SCRIPT,
            safeHandle,
            sessionUser,
            sessionFile,
            cookieBrowser,
            cookieFile
        );
        processBuilder.redirectErrorStream(true);
        processBuilder.environment().put("PYTHONUNBUFFERED", "1");
        Process process = processBuilder.start();
        boolean finished = process.waitFor(fetchTimeoutMillis, TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(1, TimeUnit.SECONDS);
            throw new IOException("Instaloader timed out for " + safeHandle);
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (process.exitValue() != 0) {
            throw new IOException("Instaloader failed for " + safeHandle + ": " + abbreviate(output));
        }
        return lastHttpUrl(output)
            .orElseThrow(() -> new IOException("Instaloader did not return an avatar URL for " + safeHandle));
    }

    private Optional<String> fetchProfilePicUrlFromWebProfileApi(String safeHandle) throws IOException, InterruptedException {
        String encodedHandle = URLEncoder.encode(safeHandle, StandardCharsets.UTF_8);
        URI uri = URI.create(profileApiUrlTemplate.formatted(encodedHandle));
        HttpRequest request = HttpRequest.newBuilder()
            .uri(uri)
            .timeout(Duration.ofMillis(fetchTimeoutMillis))
            .header("User-Agent", BROWSER_USER_AGENT)
            .header("Accept", "application/json,text/plain,*/*")
            .header("X-IG-App-ID", "936619743392459")
            .header("Referer", "https://www.instagram.com/" + safeHandle + "/")
            .GET()
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            LOGGER.debug("Instagram web profile API returned {} for {}", response.statusCode(), safeHandle);
            return Optional.empty();
        }
        JsonNode user = objectMapper.readTree(response.body()).path("data").path("user");
        return firstHttpUrl(user, "profile_pic_url_hd", "profile_pic_url");
    }

    private Optional<String> firstHttpUrl(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = node.path(fieldName).asText("").trim();
            if (value.startsWith("https://") || value.startsWith("http://")) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    private Optional<String> lastHttpUrl(String output) {
        String[] lines = output.split("\\R");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (line.startsWith("https://") || line.startsWith("http://")) {
                return Optional.of(line);
            }
        }
        return Optional.empty();
    }

    private CachedAvatar downloadAvatar(String imageUrl) throws IOException, InterruptedException {
        URI uri = URI.create(imageUrl);
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) {
            throw new IOException("Unsupported avatar URL scheme");
        }
        HttpRequest request = HttpRequest.newBuilder()
            .uri(uri)
            .timeout(Duration.ofMillis(fetchTimeoutMillis))
            .header("User-Agent", BROWSER_USER_AGENT)
            .header("Accept", "image/webp,image/png,image/jpeg,image/gif")
            .GET()
            .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Avatar URL returned " + response.statusCode());
        }
        byte[] bytes = response.body();
        if (bytes.length == 0 || bytes.length > MAX_AVATAR_BYTES) {
            throw new IOException("Avatar response size is invalid");
        }
        MediaType mediaType = mediaTypeFromResponse(response, bytes);
        return new CachedAvatar(bytes, mediaType);
    }

    private MediaType mediaTypeFromResponse(HttpResponse<byte[]> response, byte[] bytes) {
        Optional<MediaType> headerMediaType = response.headers()
            .firstValue("content-type")
            .flatMap(this::safeImageMediaType);
        return headerMediaType.or(() -> mediaTypeFromMagicBytes(bytes)).orElse(MediaType.IMAGE_JPEG);
    }

    private Optional<MediaType> safeImageMediaType(String value) {
        try {
            MediaType mediaType = MediaType.valueOf(value.split(";")[0].trim().toLowerCase(Locale.ROOT));
            if (!mediaType.getType().equals("image")) {
                return Optional.empty();
            }
            if (Set.of("jpg", "jpeg", "pjpeg").contains(mediaType.getSubtype())) {
                return Optional.of(MediaType.IMAGE_JPEG);
            }
            return Optional.of(mediaType);
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private Optional<MediaType> mediaTypeFromMagicBytes(byte[] bytes) {
        if (bytes.length >= 12
            && bytes[0] == 'R'
            && bytes[1] == 'I'
            && bytes[2] == 'F'
            && bytes[3] == 'F'
            && bytes[8] == 'W'
            && bytes[9] == 'E'
            && bytes[10] == 'B'
            && bytes[11] == 'P') {
            return Optional.of(WEBP_MEDIA_TYPE);
        }
        if (bytes.length >= 4
            && Byte.toUnsignedInt(bytes[0]) == 0x89
            && bytes[1] == 'P'
            && bytes[2] == 'N'
            && bytes[3] == 'G') {
            return Optional.of(MediaType.IMAGE_PNG);
        }
        if (bytes.length >= 3
            && Byte.toUnsignedInt(bytes[0]) == 0xff
            && Byte.toUnsignedInt(bytes[1]) == 0xd8
            && Byte.toUnsignedInt(bytes[2]) == 0xff) {
            return Optional.of(MediaType.IMAGE_JPEG);
        }
        if (bytes.length >= 6
            && bytes[0] == 'G'
            && bytes[1] == 'I'
            && bytes[2] == 'F') {
            return Optional.of(MediaType.IMAGE_GIF);
        }
        return Optional.empty();
    }

    private void cacheAvatar(String safeHandle, CachedAvatar avatar) throws IOException {
        String extension = extensionForMediaType(avatar.mediaType());
        Path file = instagramCacheDir.resolve(safeHandle + "." + extension).normalize();
        if (!file.startsWith(instagramCacheDir)) {
            throw new IOException("Avatar cache path escaped cache directory");
        }
        Path temporaryFile = Files.createTempFile(instagramCacheDir, safeHandle + "-", ".tmp");
        try {
            Files.write(temporaryFile, avatar.bytes());
            try {
                Files.move(
                    temporaryFile,
                    file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporaryFile, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
        for (String existingExtension : IMAGE_EXTENSIONS) {
            if (existingExtension.equals(extension)) {
                continue;
            }
            Path oldFile = instagramCacheDir.resolve(safeHandle + "." + existingExtension).normalize();
            if (oldFile.startsWith(instagramCacheDir)) {
                Files.deleteIfExists(oldFile);
            }
        }
    }

    private byte[] fallbackSvg(String safeHandle) {
        String label = safeHandle.substring(0, Math.min(2, safeHandle.length())).toUpperCase(Locale.ROOT);
        String svg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="160" height="160" viewBox="0 0 160 160">
              <rect width="160" height="160" rx="32" fill="#0f766e"/>
              <circle cx="122" cy="32" r="48" fill="#67e8f9" opacity="0.18"/>
              <text x="50%%" y="54%%" text-anchor="middle" dominant-baseline="middle" font-family="Arial, sans-serif" font-size="54" font-weight="700" fill="#67e8f9">%s</text>
            </svg>
            """.formatted(label);
        return svg.getBytes(StandardCharsets.UTF_8);
    }

    private String abbreviate(String value) {
        return value.length() > 200 ? value.substring(0, 200) + "..." : value;
    }

    private MediaType mediaTypeForExtension(String extension) {
        return switch (extension) {
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            case "gif" -> MediaType.IMAGE_GIF;
            case "webp" -> WEBP_MEDIA_TYPE;
            default -> MediaType.IMAGE_PNG;
        };
    }

    private String extensionForMediaType(MediaType mediaType) {
        if (MediaType.IMAGE_JPEG.includes(mediaType)) {
            return "jpg";
        }
        if (Set.of("jpg", "jpeg", "pjpeg").contains(mediaType.getSubtype())) {
            return "jpg";
        }
        if (MediaType.IMAGE_GIF.includes(mediaType)) {
            return "gif";
        }
        if (WEBP_MEDIA_TYPE.includes(mediaType)) {
            return "webp";
        }
        return "png";
    }

    public record CachedAvatar(byte[] bytes, MediaType mediaType) {}

    private record RefreshResult(Optional<CachedAvatar> avatar, boolean refreshed) {}

    public record ResolvedAvatar(byte[] bytes, MediaType mediaType, long maxAge, TimeUnit maxAgeUnit) {
        private static ResolvedAvatar cached(CachedAvatar avatar) {
            return new ResolvedAvatar(avatar.bytes(), avatar.mediaType(), 60, TimeUnit.DAYS);
        }

        private static ResolvedAvatar fallback(byte[] bytes) {
            return new ResolvedAvatar(bytes, SVG_MEDIA_TYPE, 15, TimeUnit.SECONDS);
        }
    }
}
