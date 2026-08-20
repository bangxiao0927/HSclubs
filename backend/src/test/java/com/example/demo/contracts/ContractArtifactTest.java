package com.example.demo.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The vendored copy of the cross-repository v1 contract is byte-for-byte the published one.
 *
 * <p>contracts/v1 is not written here: it is produced by hsclubs-guiding-page and copied in, so
 * this school template, that page and the iOS app validate against the same schemas and the same
 * fixtures. The failure this guards against is the quiet one -- somebody edits a schema on this
 * side to make a local test pass, and the three repositories drift apart without anyone noticing
 * until a school stops appearing in the app.
 *
 * <p>See contracts/v1/README.md. Digests are taken over LF-normalised content, so a checkout on a
 * platform with other line endings is not mistaken for a contract change.
 */
class ContractArtifactTest {

    private static final String MANIFEST = "manifest.json";

    private static Path contractsDir() {
        // Surefire runs from backend/, but a developer may run from the repository root.
        for (Path candidate = Paths.get("").toAbsolutePath();
                candidate != null;
                candidate = candidate.getParent()) {
            Path contracts = candidate.resolve("contracts").resolve("v1");
            if (Files.isDirectory(contracts)) {
                return contracts;
            }
        }
        throw new IllegalStateException("contracts/v1 is missing from this checkout");
    }

    private static String digestOf(Path file) throws IOException, NoSuchAlgorithmException {
        String content = Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(sha256.digest(content.getBytes(StandardCharsets.UTF_8)));
    }

    private static Map<String, String> actualDigests(Path root) throws Exception {
        Map<String, String> digests = new TreeMap<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String key = root.relativize(file).toString().replace('\\', '/');
                if (MANIFEST.equals(key)) {
                    continue;
                }
                digests.put(key, digestOf(file));
            }
        }
        return digests;
    }

    private static Map<String, String> recordedDigests(Path root) throws Exception {
        JsonNode manifest = new ObjectMapper().readTree(root.resolve(MANIFEST).toFile());
        Map<String, String> digests = new TreeMap<>();
        JsonNode files = manifest.get("files");
        for (Iterator<String> names = files.fieldNames(); names.hasNext(); ) {
            String name = names.next();
            digests.put(name, files.get(name).asText());
        }
        return digests;
    }

    @Test
    void everyVendoredFileMatchesTheRecordedChecksum() throws Exception {
        Path root = contractsDir();
        assertThat(actualDigests(root))
                .as("edit contracts/ in hsclubs-guiding-page and copy the directory over, "
                        + "rather than editing the vendored copy")
                .isEqualTo(recordedDigests(root));
    }

    @Test
    void carriesTheContractsThisTemplateProduces() throws Exception {
        Path root = contractsDir();
        assertThat(recordedDigests(root).keySet())
                .contains(
                        "schemas/summary.schema.json",
                        "schemas/school-manifest.schema.json",
                        "schemas/mobile-auth-start.schema.json",
                        "schemas/mobile-auth-callback.schema.json",
                        "schemas/mobile-auth-complete-request.schema.json",
                        "schemas/mobile-auth-complete-response.schema.json",
                        "schemas/mobile-auth-error.schema.json",
                        "vectors/mobile-auth.json");
    }

    @Test
    void fixtureNamesStateTheirOwnExpectation() throws Exception {
        Set<String> fixtures = recordedDigests(contractsDir()).keySet();
        for (String file : fixtures) {
            if (!file.startsWith("fixtures/")) {
                continue;
            }
            String name = Objects.requireNonNull(Paths.get(file).getFileName()).toString();
            assertThat(name.startsWith("valid") || name.startsWith("invalid-"))
                    .as("%s must be named valid*.json or invalid-*.json", file)
                    .isTrue();
        }
    }
}
