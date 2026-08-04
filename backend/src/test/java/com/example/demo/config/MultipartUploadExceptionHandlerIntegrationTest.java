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

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Full-stack regression coverage for {@link MultipartUploadExceptionHandler}: a real embedded
 * Tomcat connector, a raw {@link HttpClient} request that genuinely exceeds the configured
 * multipart ceiling on the wire, and an assertion on the real HTTP response -- not a MockMvc
 * call unit-invoking the handler.
 * <p>
 * MockMvc cannot exercise this at all: {@code MockMultipartHttpServletRequestBuilder} builds a
 * {@code MockMultipartHttpServletRequest}, which already implements
 * {@code MultipartHttpServletRequest}. {@code DispatcherServlet#checkMultipart} special-cases
 * exactly that -- {@code WebUtils.getNativeRequest(request, MultipartHttpServletRequest.class)
 * != null} -- and returns the request as-is without ever calling
 * {@code multipartResolver.resolveMultipart(request)}. {@code StandardServletMultipartResolver}
 * (and the {@code MaxUploadSizeExceededException} it throws on a real oversize failure) is
 * therefore never invoked by a MockMvc multipart test, no matter how large the mock file is.
 * <p>
 * The probe endpoint below is deliberately mounted outside {@code /api/**}. The real production
 * upload endpoints ({@code POST /api/clubs/{id}/posts}, {@code POST /api/clubs/{id}/image})
 * require an authenticated session, but Spring Security's authorization check runs purely from
 * the request line (method + path) -- it does not need the body -- and it runs before
 * {@code DispatcherServlet}, hence before multipart parsing, ever sees the request. An
 * unauthenticated request to either real endpoint would simply 401 without ever reaching the
 * code under test, and standing up a genuine OAuth2 session for a raw HTTP client is out of
 * scope for this regression test. Mounting the probe under a path already covered by
 * {@code SecurityConfig}'s existing, unmodified {@code .anyRequest().permitAll()} rule (the same
 * rule that already permits static resources and frontend routes) reaches the identical
 * {@code DispatcherServlet -> StandardServletMultipartResolver ->
 * MultipartUploadExceptionHandler} pipeline the real endpoints use, without loosening or
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
class MultipartUploadExceptionHandlerIntegrationTest {

    private static final String BOUNDARY = "HSClubsMultipartTestBoundary";

    @LocalServerPort
    private int port;

    @TestConfiguration
    static class ProbeConfig {
        @Bean
        MultipartProbeController multipartProbeController() {
            return new MultipartProbeController();
        }
    }

    @RestController
    static class MultipartProbeController {
        @PostMapping("/internal-test/multipart-probe")
        public String receive(@RequestParam("file") MultipartFile file) {
            return "received " + file.getSize() + " bytes";
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
        // and MultipartUploadExceptionHandler never sets one -- so, unlike the other four
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
