package com.example.demo.summary.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.auth.config.SecurityProperties;
import com.example.demo.summary.config.SchoolIdentity;
import com.example.demo.summary.model.SummaryResponse;
import com.example.demo.summary.service.SummaryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * What a school with an identity publishes, checked against the contract three repositories share.
 *
 * <p>The assertions are driven by contracts/v1/schemas: a required member the schema names and
 * this deployment does not send is the failure that would keep this school out of the app's
 * directory, and it should be a red test here rather than a support question there.
 */
@WebMvcTest(
    controllers = {SummaryController.class, SchoolManifestController.class},
    excludeAutoConfiguration = {
        OAuth2ClientAutoConfiguration.class,
        OAuth2ClientWebSecurityAutoConfiguration.class
    })
@AutoConfigureMockMvc(addFilters = false)
class SchoolIdentityPublicationTest {

    private static final String SCHOOL_ID = "sch_7Qb3Xf9KLm2ZpR4tVn6Y";
    private static final String ORIGIN = "https://clubs.example.edu";

    @TestConfiguration
    static class TestConfig {
        @Bean
        SecurityProperties securityProperties() {
            return new SecurityProperties();
        }

        @Bean
        SchoolIdentity schoolIdentity() {
            return new SchoolIdentity(SCHOOL_ID, ORIGIN + "/");
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SummaryService summaryService;

    private static Path contractsDir() {
        for (Path candidate = Paths.get("").toAbsolutePath(); candidate != null; candidate = candidate.getParent()) {
            Path contracts = candidate.resolve("contracts").resolve("v1");
            if (Files.isDirectory(contracts)) {
                return contracts;
            }
        }
        throw new IllegalStateException("contracts/v1 is missing from this checkout");
    }

    private static List<String> requiredMembersOf(String schema) throws Exception {
        JsonNode root = new ObjectMapper().readTree(contractsDir().resolve("schemas").resolve(schema).toFile());
        List<String> required = new ArrayList<>();
        root.get("required").forEach(member -> required.add(member.asText()));
        return required;
    }

    private static void assertPublishesEveryRequiredMember(
        org.springframework.test.web.servlet.ResultActions response, String schema) throws Exception {
        JsonNode body = new ObjectMapper()
            .readTree(response.andReturn().getResponse().getContentAsString());
        for (String member : requiredMembersOf(schema)) {
            org.assertj.core.api.Assertions.assertThat(body.has(member))
                .as("%s requires %s", schema, member)
                .isTrue();
        }
    }

    @BeforeEach
    void stubSummary() {
        SummaryResponse summary = new SummaryResponse();
        summary.setSchoolName("Example High School");
        summary.setShortName("EHS");
        summary.setSlug("example");
        summary.setStatus("active");
        summary.setClubCount(3);
        summary.setMemberCount(0);
        summary.setCategories(java.util.Map.of("STEM", 3));
        summary.setDataHash("abc123");
        summary.setLastUpdatedAt(java.time.OffsetDateTime.parse("2026-02-03T04:05:00-08:00"));
        when(summaryService.buildSnapshot())
            .thenReturn(new SummaryService.Snapshot(summary, "representation-tag"));
    }

    @Test
    void publishesEveryMemberTheVersionedSummaryContractRequires() throws Exception {
        var response = mockMvc.perform(get("/api/v1/summary")).andExpect(status().isOk());
        // Presence, not truthiness: a required member whose value is null -- an address nobody
        // configured -- is still published, so a consumer checks for null and never for absence.
        assertPublishesEveryRequiredMember(response, "summary.schema.json");
        response
            .andExpect(jsonPath("$.contract").value("hsclubs.summary"))
            .andExpect(jsonPath("$.version").value(1))
            .andExpect(jsonPath("$.schoolId").value(SCHOOL_ID))
            .andExpect(jsonPath("$.slug").value("example"))
            .andExpect(jsonPath("$.clubCount").value(3))
            // Same wire format as the unversioned endpoint: an instant that keeps its offset, so
            // schools in different zones stay comparable.
            .andExpect(jsonPath("$.lastUpdatedAt").value("2026-02-03T04:05:00-08:00"));
    }

    // The two representations differ, so they must not share an entity tag: a poller holding one
    // must never be told its copy of the other is current.
    @Test
    void tagsTheVersionedSummaryDistinctlyFromTheUnversionedOne() throws Exception {
        String unversioned = mockMvc.perform(get("/api/summary"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getHeader("ETag");
        String versioned = mockMvc.perform(get("/api/v1/summary"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getHeader("ETag");

        org.assertj.core.api.Assertions.assertThat(versioned).isNotEqualTo(unversioned);
        mockMvc.perform(get("/api/v1/summary").header("If-None-Match", versioned))
            .andExpect(status().isNotModified());
    }

    @Test
    void publishesEveryMemberTheManifestContractRequires() throws Exception {
        var response = mockMvc.perform(get("/.well-known/hsclubs-app.json")).andExpect(status().isOk());
        assertPublishesEveryRequiredMember(response, "school-manifest.schema.json");
        response
            .andExpect(jsonPath("$.contract").value("hsclubs.school-manifest"))
            .andExpect(jsonPath("$.version").value(1))
            .andExpect(jsonPath("$.schoolId").value(SCHOOL_ID))
            // Origin only: a manifest whose siteOrigin carries a path or a trailing slash does not
            // match the origin the registry verified, and the school is refused.
            .andExpect(jsonPath("$.siteOrigin").value(ORIGIN))
            .andExpect(jsonPath("$.summaryUrl").value(ORIGIN + "/api/v1/summary"))
            .andExpect(jsonPath("$.capabilities").value(org.hamcrest.Matchers.hasItem("summary.v1")))
            // Declared only when the endpoints exist; an app that believed otherwise would send
            // somebody into a sign-in that cannot complete.
            .andExpect(jsonPath("$.auth.mobile.supported").value(false));
    }

    // The identity this school publishes has to be the same one in both documents, or the
    // registry sees an origin claiming two identities and refuses it.
    @Test
    void statesOneIdentityInBothDocuments() throws Exception {
        String fromSummary = mockMvc.perform(get("/api/v1/summary"))
            .andReturn().getResponse().getContentAsString();
        String fromManifest = mockMvc.perform(get("/.well-known/hsclubs-app.json"))
            .andReturn().getResponse().getContentAsString();

        ObjectMapper mapper = new ObjectMapper();
        org.assertj.core.api.Assertions
            .assertThat(mapper.readTree(fromSummary).get("schoolId").asText())
            .isEqualTo(mapper.readTree(fromManifest).get("schoolId").asText());
    }
}
