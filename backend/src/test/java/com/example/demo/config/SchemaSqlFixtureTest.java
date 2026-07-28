package com.example.demo.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * schema.sql is an H2-only local dev fixture (see the file header for why), never a production
 * schema-migration script -- backend/.env pins SPRING_SQL_INIT_MODE=never in production so this
 * never runs against MySQL, but that is a single environment variable away from being flipped
 * back on. This test is the regression guard for the destructive habit that variable is masking:
 * ten unconditional "DROP TABLE" statements used to run at the top of this file on every
 * startup, which is fine for a disposable H2 fixture but would wipe the production database in
 * one restart if SPRING_SQL_INIT_MODE were ever "always" there. A from-scratch, destructive H2
 * reset now lives in its own h2-profile-only script instead (see application-h2.yaml).
 */
class SchemaSqlFixtureTest {

    @Test
    void schemaSqlNeverDropsTables() throws IOException {
        String schemaSql = readClasspathResource("schema.sql");

        assertThat(schemaSql.toUpperCase(Locale.ROOT)).doesNotContain("DROP TABLE");
    }

    private static String readClasspathResource(String path) throws IOException {
        try (InputStream inputStream = new ClassPathResource(path).getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
