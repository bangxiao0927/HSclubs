package com.example.demo.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * User-uploaded files are served straight from disk under our own origin, so this is standard
 * hardening against the browser trying to sniff a malicious upload's content type.
 */
@SpringBootTest
@AutoConfigureMockMvc
class WebConfigUploadsHeaderTest {

    @TempDir
    static Path uploadDir;

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void uploadDir(DynamicPropertyRegistry registry) {
        registry.add("app.upload.dir", () -> uploadDir.toString());
    }

    @BeforeEach
    void writeSampleFile() throws Exception {
        Files.write(uploadDir.resolve("sample.jpg"), new byte[] {1, 2, 3});
    }

    @Test
    void uploadedFileResponseCarriesNosniffHeader() throws Exception {
        mockMvc.perform(get("/uploads/sample.jpg"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }
}
