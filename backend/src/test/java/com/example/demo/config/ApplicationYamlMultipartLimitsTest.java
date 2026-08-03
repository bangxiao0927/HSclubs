package com.example.demo.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Regression guard for the multipart/Tomcat limits documented in the ImageStorageService
 * pipeline. Before these were set, spring.servlet.multipart.max-file-size defaulted to 1MB, so
 * uploads between 1MB and the application's own 5MB limit failed with a 500 instead of the
 * intended 400 -- the multipart ceiling must sit strictly above the 5MB application limit, or
 * Tomcat rejects the request before that readable error can ever be produced. max-swallow-size
 * must also exceed max-request-size, or Tomcat aborts the connection on an oversize upload
 * instead of delivering the 413 response body.
 *
 * <p>Reads src/main/resources/application.yaml directly by file path for the same reason as
 * {@link ApplicationYamlSqlInitModeTest}: on the test classpath, "application.yaml" resolves to
 * the test-only override instead.
 */
class ApplicationYamlMultipartLimitsTest {

    @Test
    void multipartAndTomcatLimitsSitAboveTheApplicationLevelUploadLimit() throws IOException {
        String applicationYaml = Files.readString(Path.of("src/main/resources/application.yaml"));

        assertThat(applicationYaml).contains("max-file-size: 6MB");
        assertThat(applicationYaml).contains("max-request-size: 8MB");
        assertThat(applicationYaml).contains("max-swallow-size: 10MB");
    }
}
