package com.example.demo.summary.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.example.demo.auth.config.SecurityProperties;
import com.example.demo.summary.model.SummaryResponse;
import com.example.demo.summary.service.SummaryService;

/**
 * The aggregator polls this endpoint, so "has anything changed?" has to be answerable without
 * downloading the whole directory each time.
 */
@WebMvcTest(
    controllers = SummaryController.class,
    excludeAutoConfiguration = {
        OAuth2ClientAutoConfiguration.class,
        OAuth2ClientWebSecurityAutoConfiguration.class
    })
@AutoConfigureMockMvc(addFilters = false)
class SummaryControllerTest {

    private static final String DATA_HASH = "abc123";
    // Deliberately different from dataHash: the tag validates the whole representation, and a
    // test that reused dataHash here would not notice if the two were confused again.
    private static final String ENTITY_TAG = "representation-tag";
    private static final String ETAG = "\"" + ENTITY_TAG + "\"";

    @TestConfiguration
    static class TestConfig {
        @Bean
        SecurityProperties securityProperties() {
            return new SecurityProperties();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SummaryService summaryService;

    @BeforeEach
    void stubSummary() {
        SummaryResponse summary = new SummaryResponse();
        summary.setSchoolName("Example High School");
        summary.setClubCount(3);
        summary.setDataHash(DATA_HASH);
        when(summaryService.buildSnapshot())
            .thenReturn(new SummaryService.Snapshot(summary, ENTITY_TAG));
    }

    @Test
    void returnsTheSummaryWithItsDataHashAsTheEtag() throws Exception {
        mockMvc.perform(get("/api/summary"))
            .andExpect(status().isOk())
            .andExpect(header().string("ETag", ETAG))
            .andExpect(jsonPath("$.clubCount").value(3))
            .andExpect(jsonPath("$.dataHash").value(DATA_HASH));
    }

    @Test
    void answersNotModifiedWhenThePollerAlreadyHasThisVersion() throws Exception {
        mockMvc.perform(get("/api/summary").header("If-None-Match", ETAG))
            .andExpect(status().isNotModified())
            .andExpect(content().string(""));
    }

    @Test
    void sendsTheBodyWhenThePollersVersionIsStale() throws Exception {
        mockMvc.perform(get("/api/summary").header("If-None-Match", "\"an-older-hash\""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dataHash").value(DATA_HASH));
    }

    // A proxy may weaken the tag, and a client may send several; anything unrecognised has to
    // fall back to the full body, never to a wrong 304.
    @Test
    void understandsAWeakenedOrMultiValuedIfNoneMatch() throws Exception {
        mockMvc.perform(get("/api/summary").header("If-None-Match", "\"other\", W/" + ETAG))
            .andExpect(status().isNotModified());

        mockMvc.perform(get("/api/summary").header("If-None-Match", "not-a-tag"))
            .andExpect(status().isOk());
    }
}
