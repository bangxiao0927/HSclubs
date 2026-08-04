package com.example.demo.club.service;

import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

// Not a JUnit test (no @Test methods, not named *Test): a standalone entry point that
// ImageStorageServiceOomRegressionTest launches as a separate, heap-constrained JVM process,
// so an OutOfMemoryError in ImageStorageService.store() shows up as that process's exit code
// rather than being caught (or crashing) the test runner's own JVM.
public final class ImageStorageServiceOomHarnessMain {

    private ImageStorageServiceOomHarnessMain() {
    }

    public static void main(String[] args) throws IOException {
        Path uploadDir = Paths.get(args[0]);
        Path imageFile = Paths.get(args[1]);
        byte[] bytes = Files.readAllBytes(imageFile);

        ImageStorageService service = new ImageStorageService(uploadDir.toString());
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", bytes);
        try {
            String imageUrl = service.store(file);
            System.out.println("ACCEPTED " + imageUrl);
        } catch (IllegalArgumentException e) {
            System.out.println("REJECTED " + e.getMessage());
        }
    }
}
