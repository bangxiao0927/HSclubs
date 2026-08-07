package com.example.demo.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

/**
 * Guards the defaults a deployment actually runs with. There is no production profile -- the
 * documented deployment starts the app with no SPRING_PROFILES_ACTIVE at all -- so whatever the
 * base application.yaml says is what production gets, and a "dev only" comment next to a key is
 * not a control. These assertions read the real main-source files from disk on purpose:
 * src/test/resources/application.yaml shadows the base config for every @SpringBootTest, so a
 * context-based test would only be checking the test fixture.
 */
class ProductionConfigDefaultsTest {

    private static final Path BASE_CONFIG = Path.of("src/main/resources/application.yaml");
    private static final Path H2_PROFILE_CONFIG = Path.of("src/main/resources/application-h2.yaml");

    // The H2 driver is a runtime dependency and SecurityConfig ends in anyRequest().permitAll(),
    // so enabling the console outside the h2 profile publishes an unauthenticated page that
    // connects to any JDBC URL a visitor types -- including the production database.
    @Test
    void baseConfigDoesNotEnableTheH2Console() throws IOException {
        assertThat(resolve(BASE_CONFIG, "spring.h2.console.enabled")).isNull();
        assertThat(resolve(BASE_CONFIG, "spring.h2.console.path")).isNull();
    }

    @Test
    void theH2ProfileStillEnablesTheConsoleForLocalDevelopment() throws IOException {
        assertThat(resolve(H2_PROFILE_CONFIG, "spring.h2.console.enabled")).isEqualTo("true");
    }

    // StdOutImpl prints every statement with its bound parameters, so the auth-layer queries
    // put user emails, display names, and avatar URLs into the process log.
    @Test
    void baseConfigDoesNotLogEverySqlStatementAndItsParameters() throws IOException {
        assertThat(resolve(BASE_CONFIG, "mybatis.configuration.log-impl"))
            .isNotNull()
            .doesNotContain("StdOutImpl");
    }

    @Test
    void theH2ProfileKeepsStatementLoggingForLocalDevelopment() throws IOException {
        assertThat(resolve(H2_PROFILE_CONFIG, "mybatis.configuration.log-impl")).contains("StdOutImpl");
    }

    // Tomcat only marks JSESSIONID as Secure when request.isSecure() is true, which behind a
    // TLS-terminating proxy depends entirely on X-Forwarded-Proto being honoured.
    @Test
    void baseConfigCanMarkTheSessionCookieSecureAndTrustsTheProxyProtocolHeader() throws IOException {
        assertThat(resolve(BASE_CONFIG, "server.servlet.session.cookie.secure")).isNotNull();
        assertThat(resolve(BASE_CONFIG, "server.forward-headers-strategy")).isEqualTo("framework");
    }

    /**
     * Returns the property's value with placeholders resolved against nothing but the file
     * itself, i.e. the default a deployment gets when the corresponding environment variable is
     * not set. Returns null when the key is absent.
     */
    private static String resolve(Path configFile, String key) throws IOException {
        List<PropertySource<?>> loaded =
            new YamlPropertySourceLoader().load(configFile.toString(), new FileSystemResource(configFile));
        MutablePropertySources sources = new MutablePropertySources();
        loaded.forEach(sources::addLast);

        Object raw = null;
        for (PropertySource<?> source : loaded) {
            Object value = source.getProperty(key);
            if (value != null) {
                raw = value;
                break;
            }
        }
        if (raw == null) {
            return null;
        }
        return new PropertySourcesPlaceholdersResolver(sources).resolvePlaceholders(raw.toString()).toString();
    }
}
