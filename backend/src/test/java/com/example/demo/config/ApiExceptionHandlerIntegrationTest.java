package com.example.demo.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/**
 * Full-stack regression coverage for {@link ApiExceptionHandler}: a real embedded Tomcat
 * connector, raw {@link HttpClient} requests, and assertions on the real HTTP response -- not a
 * MockMvc call unit-invoking the handler.
 * <p>
 * MockMvc cannot exercise this at all: {@code MockMultipartHttpServletRequestBuilder} builds a
 * {@code MockMultipartHttpServletRequest}, which already implements
 * {@code MultipartHttpServletRequest}. {@code DispatcherServlet#checkMultipart} special-cases
 * exactly that -- {@code WebUtils.getNativeRequest(request, MultipartHttpServletRequest.class)
 * != null} -- and returns the request as-is without ever calling
 * {@code multipartResolver.resolveMultipart(request)}. {@code StandardServletMultipartResolver}
 * (and the {@code MaxUploadSizeExceededException} it throws on a real oversize failure) is
 * therefore never invoked by a MockMvc multipart test, no matter how large the mock file is.
 * {@code ResponseStatusException} does not have that specific problem, but MockMvc's
 * {@code MockHttpServletResponse#sendError} never writes a body either (see
 * {@code ApiExceptionHandler}'s own Javadoc) -- existing controller tests that only assert
 * {@code status().isBadRequest()} etc. would pass identically whether or not this handler
 * existed, so they cannot stand in for the coverage below.
 * <p>
 * The probe endpoints below are deliberately mounted outside {@code /api/**}. The real
 * production endpoints that can throw either exception type
 * ({@code POST /api/clubs/{id}/posts}, {@code POST /api/clubs/{id}/image}, and every other
 * authenticated {@code /api/**} route) require an authenticated session, but Spring Security's
 * authorization check runs purely from the request line (method + path) -- it does not need the
 * body -- and it runs before {@code DispatcherServlet}, hence before either exception can ever
 * be thrown, ever sees the request. An unauthenticated request to a real endpoint would simply
 * 401 without ever reaching the code under test, and standing up a genuine OAuth2 session for a
 * raw HTTP client is out of scope for this regression test. Mounting the probes under a path
 * {@code SecurityConfig}'s existing, unmodified {@code .anyRequest().permitAll()} rule (the same
 * rule that already permits static resources and frontend routes) reaches the identical
 * {@code DispatcherServlet -> (StandardServletMultipartResolver | ResponseStatusExceptionResolver
 * fallthrough) -> ApiExceptionHandler} pipeline the real endpoints use, without loosening or
 * otherwise touching {@code SecurityConfig} or any controller's auth behavior.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    // Matches the real spring.servlet.multipart.* values in application.yaml: the test
    // classpath's own application.yaml (src/test/resources) shadows the main one, so those
    // production values are not otherwise in effect during a test run.
    "spring.servlet.multipart.max-file-size=6MB",
    "spring.servlet.multipart.max-request-size=8MB",
    // Comfortably above the oversized file below: at the framework's own default (2MB), Tomcat
    // aborts the connection instead of delivering the 413 body this test asserts on -- see the
    // identical reasoning in application.yaml's own max-swallow-size comment.
    "server.tomcat.max-swallow-size=10MB"
})
class ApiExceptionHandlerIntegrationTest {

    private static final String BOUNDARY = "HSClubsMultipartTestBoundary";

    @LocalServerPort
    private int port;

    @TestConfiguration
    static class ProbeConfig {
        @Bean
        MultipartProbeController multipartProbeController() {
            return new MultipartProbeController();
        }

        @Bean
        ResponseStatusProbeController responseStatusProbeController() {
            return new ResponseStatusProbeController();
        }
    }

    @RestController
    static class MultipartProbeController {
        @PostMapping("/internal-test/multipart-probe")
        public String receive(@RequestParam("file") MultipartFile file) {
            return "received " + file.getSize() + " bytes";
        }
    }

    // Mirrors the exact English reason text real controllers already pass to
    // ResponseStatusException today (see e.g. ClubPostService#validateTitle,
    // ClubPostController#deletePost, ClubService#resolveBySlugOrId callers, and
    // ClubPostService#pin), so this proves the handler preserves those specific messages, not
    // just some arbitrary reason string.
    @RestController
    static class ResponseStatusProbeController {
        @GetMapping("/internal-test/rse-probe/400")
        public void badRequest() {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title must be 140 characters or fewer");
        }

        @GetMapping("/internal-test/rse-probe/403")
        public void forbidden() {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to delete this post");
        }

        @GetMapping("/internal-test/rse-probe/404")
        public void notFound() {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found");
        }

        @GetMapping("/internal-test/rse-probe/409")
        public void conflict() {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "At most 3 posts can be pinned. Unpin one first.");
        }
    }

    @Test
    void fileOverTheConfiguredMaxFileSizeReturns413WithAnEnglishProblemDetailBody() throws Exception {
        // 7MB: over the 6MB max-file-size configured above, comfortably under both
        // max-request-size (8MB) and max-swallow-size (10MB) so this exercises exactly the
        // per-file ceiling, not the request-wide one or a swallowed/aborted connection.
        byte[] oversizedFile = new byte[7 * 1024 * 1024];
        Arrays.fill(oversizedFile, (byte) 'a');
        byte[] requestBody = multipartBody(oversizedFile);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/internal-test/multipart-probe"))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
            .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(413);
        // Content-Type is application/problem+json, not a bare "application/json": this is the
        // RFC 9457 media type ProblemDetail's own message converter registers, and docs/API.md
        // documents it explicitly rather than leaving it to guesswork.
        assertThat(response.headers().firstValue("Content-Type"))
            .hasValueSatisfying(contentType -> assertThat(contentType).startsWith("application/problem+json"));

        // Asserts the exact, stable fields docs/API.md documents -- not a substring match, so a
        // field this test doesn't know about being added or removed is caught precisely, and a
        // future change to the handler's own message is caught here before it silently drifts
        // out of sync with the docs.
        JsonNode body = new ObjectMapper().readTree(response.body());
        assertThat(body.get("status").asInt()).isEqualTo(413);
        assertThat(body.get("title").asText()).isEqualTo("Content Too Large");
        assertThat(body.get("detail").asText())
            .isEqualTo("The uploaded file is too large. Please choose a smaller file and try again.");
        assertThat(body.get("instance").asText()).isEqualTo("/internal-test/multipart-probe");
        // ProblemDetail's "type" defaults to unset (RFC 9457 then treats it as "about:blank"),
        // and ApiExceptionHandler never sets one -- so, unlike the other four
        // fields above, it is never actually present in the response body. Documenting it as
        // present in docs/API.md would overpromise a field this handler does not emit.
        assertThat(body.has("type")).isFalse();
    }

    // Companion to the oversized case above: proves the probe/multipart pipeline itself works
    // end to end (real server, real multipart parsing) so the 413 above is genuinely caused by
    // crossing the size ceiling, not by some unrelated defect in the hand-built request body.
    @Test
    void fileUnderTheConfiguredMaxFileSizeIsAcceptedNormally() throws Exception {
        byte[] smallFile = new byte[1024];
        Arrays.fill(smallFile, (byte) 'a');
        byte[] requestBody = multipartBody(smallFile);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/internal-test/multipart-probe"))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
            .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("received 1024 bytes");
    }

    // Representative statuses actually thrown across this codebase's own controllers/services
    // as ResponseStatusException: 400 (validation), 403 (authorization after a successful
    // login), 404 (not found), and 409 (conflict). Each assertion below is on the real HTTP
    // response from a real server, exactly like the 413 case above -- not MockMvc.
    @ParameterizedTest(name = "{0} preserves its exact English reason as a real application/problem+json body")
    @MethodSource("representativeResponseStatusExceptions")
    void responseStatusExceptionPreservesItsReasonAsAnEnglishProblemDetailBody(
            int status, String reasonPhrase, String detail) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/internal-test/rse-probe/" + status))
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type"))
            .hasValueSatisfying(contentType -> assertThat(contentType).startsWith("application/problem+json"));

        JsonNode body = new ObjectMapper().readTree(response.body());
        assertThat(body.get("status").asInt()).isEqualTo(status);
        assertThat(body.get("title").asText()).isEqualTo(reasonPhrase);
        assertThat(body.get("detail").asText()).isEqualTo(detail);
        assertThat(body.get("instance").asText()).isEqualTo("/internal-test/rse-probe/" + status);
        assertThat(body.has("type")).isFalse();
    }

    private static Stream<Arguments> representativeResponseStatusExceptions() {
        return Stream.of(
            Arguments.of(400, "Bad Request", "Title must be 140 characters or fewer"),
            Arguments.of(403, "Forbidden", "You do not have access to delete this post"),
            Arguments.of(404, "Not Found", "Club not found"),
            Arguments.of(409, "Conflict", "At most 3 posts can be pinned. Unpin one first."));
    }

    private static byte[] multipartBody(byte[] fileBytes) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(("--" + BOUNDARY + "\r\n").getBytes(StandardCharsets.US_ASCII));
        out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"oversized.jpg\"\r\n")
            .getBytes(StandardCharsets.US_ASCII));
        out.write("Content-Type: image/jpeg\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        out.write(fileBytes);
        out.write(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.US_ASCII));
        return out.toByteArray();
    }
}
