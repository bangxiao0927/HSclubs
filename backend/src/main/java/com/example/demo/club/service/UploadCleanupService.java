package com.example.demo.club.service;

import com.example.demo.club.mapper.ClubMapper;
import com.example.demo.club.mapper.ClubPostMapper;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Removes uploaded image files that are no longer referenced by any club or club post.
 * Runs daily at 3 AM.
 */
@Service
public class UploadCleanupService {

    private static final Logger log = LoggerFactory.getLogger(UploadCleanupService.class);

    private static final String UPLOADS_URL_PREFIX = "/uploads/";

    // Cleanup is scoped to exactly what this service is allowed to own: legacy flat files
    // written directly under the upload root by the old (pre-ImageStorageService) controller,
    // plus anything under club-posts/. A blanket recursive walk of the whole upload root would
    // also sweep up completely unrelated subsystems that happen to live under the same root,
    // notably avatar-cache/instagram/ (InstagramAvatarCacheService).
    private static final String CLUB_POSTS_SUBDIRECTORY = "club-posts";

    // ...and, at the top level, only files this application could itself have written: a
    // UUID name with an image extension, matching ImageStorageService's own delete guard.
    // Files.list() returns every regular file directly under the upload root, so without this
    // the job would also reclaim anything an operator or another subsystem happens to keep
    // there (an .env copy, a README, a database dump) purely because no club row points at it.
    private static final Pattern GENERATED_FLAT_FILE_NAME = Pattern.compile(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
            + "\\.(?:jpg|jpeg|png|webp|gif)$",
        Pattern.CASE_INSENSITIVE);

    // Files must be at least this old to be reclaimed. A file can land on disk moments before
    // its row commits; listing files before querying the database (below) already narrows that
    // race, but this grace period is the guard that actually closes it, so a concurrent upload
    // is never mistaken for an orphan.
    private static final Duration MINIMUM_ORPHAN_AGE = Duration.ofMinutes(10);

    private final ClubMapper clubMapper;
    private final ClubPostMapper clubPostMapper;
    private final Path uploadDir;

    public UploadCleanupService(ClubMapper clubMapper,
                                ClubPostMapper clubPostMapper,
                                @Value("${app.upload.dir:uploads}") String uploadDirPath) {
        this.clubMapper = clubMapper;
        this.clubPostMapper = clubPostMapper;
        this.uploadDir = Paths.get(uploadDirPath).toAbsolutePath().normalize();
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void cleanOrphanedUploads() {
        log.info("Starting orphaned upload cleanup...");

        if (!Files.exists(uploadDir) || !Files.isDirectory(uploadDir)) {
            return;
        }

        // List before querying the database, so a file written moments ago -- whose row may
        // not have committed yet -- is at least as likely to already be missing from this
        // snapshot as it is to be missing from the referenced set queried next.
        List<Path> candidateFiles = listCandidateFiles();
        Set<String> referencedRelativePaths = collectReferencedRelativePaths();
        Instant cutoff = Instant.now().minus(MINIMUM_ORPHAN_AGE);

        int deletedCount = 0;
        for (Path file : candidateFiles) {
            String relativePath = relativePathOf(file);
            if (referencedRelativePaths.contains(relativePath)) {
                continue;
            }
            if (isModifiedAfter(file, cutoff)) {
                continue;
            }
            if (deleteQuietly(file, relativePath)) {
                deletedCount++;
            }
        }

        log.info("Orphaned upload cleanup complete. Referenced files: {}, deleted: {}",
            referencedRelativePaths.size(), deletedCount);
    }

    private List<Path> listCandidateFiles() {
        List<Path> candidates = new ArrayList<>(listLegacyFlatFiles());
        candidates.addAll(listClubPostFiles());
        return candidates;
    }

    private List<Path> listLegacyFlatFiles() {
        try (Stream<Path> topLevel = Files.list(uploadDir)) {
            return topLevel
                .filter(Files::isRegularFile)
                .filter(file -> GENERATED_FLAT_FILE_NAME.matcher(file.getFileName().toString()).matches())
                .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Failed to list upload directory for cleanup", e);
            return List.of();
        }
    }

    private List<Path> listClubPostFiles() {
        Path clubPostsDir = uploadDir.resolve(CLUB_POSTS_SUBDIRECTORY);
        if (!Files.isDirectory(clubPostsDir)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(clubPostsDir)) {
            return walk.filter(Files::isRegularFile).collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Failed to list club-posts directory for cleanup", e);
            return List.of();
        }
    }

    private Set<String> collectReferencedRelativePaths() {
        List<Club> allClubs = clubMapper.findAllRegardlessOfStatus();
        Set<String> referenced = allClubs.stream()
            .map(Club::getImageUrl)
            .filter(url -> url != null && url.startsWith(UPLOADS_URL_PREFIX))
            .map(url -> url.substring(UPLOADS_URL_PREFIX.length()))
            .collect(Collectors.toCollection(HashSet::new));

        clubPostMapper.findAllImageUrls().stream()
            .filter(url -> url != null && url.startsWith(UPLOADS_URL_PREFIX))
            .map(url -> url.substring(UPLOADS_URL_PREFIX.length()))
            .forEach(referenced::add);

        return referenced;
    }

    private String relativePathOf(Path file) {
        return uploadDir.relativize(file).toString().replace('\\', '/');
    }

    private boolean isModifiedAfter(Path file, Instant cutoff) {
        try {
            return Files.getLastModifiedTime(file).toInstant().isAfter(cutoff);
        } catch (IOException e) {
            // If we can't stat it, don't risk deleting something mid-write.
            return true;
        }
    }

    private boolean deleteQuietly(Path file, String relativePath) {
        try {
            Files.deleteIfExists(file);
            log.info("Deleted orphaned upload: {}", relativePath);
            return true;
        } catch (IOException e) {
            log.warn("Failed to delete orphaned upload: {}", relativePath, e);
            return false;
        }
    }
}
