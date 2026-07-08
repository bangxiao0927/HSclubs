package com.example.demo.club.service;

import com.example.demo.club.mapper.ClubMapper;
import com.example.demo.club.model.Club;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Removes uploaded image files that are no longer referenced by any club.
 * Runs daily at 3 AM.
 */
@Service
public class UploadCleanupService {

    private static final Logger log = LoggerFactory.getLogger(UploadCleanupService.class);

    private final ClubMapper clubMapper;
    private final Path uploadDir;

    public UploadCleanupService(ClubMapper clubMapper,
                                @Value("${app.upload.dir:uploads}") String uploadDirPath) {
        this.clubMapper = clubMapper;
        this.uploadDir = Paths.get(uploadDirPath).toAbsolutePath().normalize();
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void cleanOrphanedUploads() {
        log.info("Starting orphaned upload cleanup...");

        // Collect all image URLs currently referenced by clubs
        List<Club> allClubs = clubMapper.findAll();
        Set<String> referencedFilenames = allClubs.stream()
            .map(Club::getImageUrl)
            .filter(url -> url != null && url.startsWith("/uploads/"))
            .map(url -> url.substring("/uploads/".length()))
            .collect(Collectors.toSet());

        if (!Files.exists(uploadDir) || !Files.isDirectory(uploadDir)) {
            return;
        }

        try (var entries = Files.list(uploadDir)) {
            entries.filter(Files::isRegularFile)
                .forEach(file -> {
                    String filename = file.getFileName().toString();
                    if (!referencedFilenames.contains(filename)) {
                        try {
                            Files.deleteIfExists(file);
                            log.info("Deleted orphaned upload: {}", filename);
                        } catch (IOException e) {
                            log.warn("Failed to delete orphaned upload: {}", filename, e);
                        }
                    }
                });
        } catch (IOException e) {
            log.error("Failed to list upload directory for cleanup", e);
        }

        log.info("Orphaned upload cleanup complete. Referenced files: {}", referencedFilenames.size());
    }
}
