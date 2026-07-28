package com.example.demo.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Regression guard for the production SQL-init default. spring.sql.init.mode defaulting to
 * "always" is what let schema.sql's old DROP TABLE statements run unconditionally against
 * whatever datasource was configured; it should default to "never" so a production deploy
 * without an explicit SPRING_SQL_INIT_MODE override does not attempt to run the H2-only
 * schema.sql fixture against MySQL. The h2 profile (application-h2.yaml) still overrides this
 * back to "always" for local dev -- see H2SchemaFixtureIntegrationTest for that path.
 *
 * <p>Reads src/main/resources/application.yaml directly by file path rather than as a classpath
 * resource: on the test classpath, "application.yaml" resolves to the test-only override at
 * src/test/resources/application.yaml instead (it shadows the main one), which doesn't contain
 * this key at all.
 */
class ApplicationYamlSqlInitModeTest {

    @Test
    void productionSqlInitModeDefaultsToNever() throws IOException {
        String applicationYaml = Files.readString(Path.of("src/main/resources/application.yaml"));

        assertThat(applicationYaml).contains("${SPRING_SQL_INIT_MODE:never}");
    }
}
