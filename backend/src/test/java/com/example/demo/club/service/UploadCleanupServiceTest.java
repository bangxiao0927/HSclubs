package com.example.demo.club.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.demo.club.mapper.ClubMapper;
import com.example.demo.club.mapper.ClubPostMapper;
import com.example.demo.club.model.Club;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UploadCleanupServiceTest {

    @TempDir
    Path uploadDir;

    @Test
    void doesNotDeleteAnImageStillUsedByAnArchivedClub() throws Exception {
        Path stillUsed = ageFile(uploadDir.resolve("still-used.png"));
        Path orphan = ageFile(uploadDir.resolve("orphan.png"));

        Club archivedClub = new Club();
        archivedClub.setStatus("archived");
        archivedClub.setImageUrl("/uploads/still-used.png");

        ClubMapper clubMapper = mock(ClubMapper.class);
        when(clubMapper.findAllRegardlessOfStatus()).thenReturn(List.of(archivedClub));
        ClubPostMapper clubPostMapper = mock(ClubPostMapper.class);
        when(clubPostMapper.findAllImageUrls()).thenReturn(List.of());

        UploadCleanupService service = new UploadCleanupService(clubMapper, clubPostMapper, uploadDir.toString());

        service.cleanOrphanedUploads();

        assertThat(Files.exists(stillUsed)).isTrue();
        assertThat(Files.exists(orphan)).isFalse();
    }

    @Test
    void doesNotDeleteAnImageStillReferencedByAClubPost() throws Exception {
        Path stillUsed = ageFile(uploadDir.resolve("club-posts").resolve("post-photo.jpg"));
        Path orphan = ageFile(uploadDir.resolve("club-posts").resolve("orphan-photo.jpg"));

        ClubMapper clubMapper = mock(ClubMapper.class);
        when(clubMapper.findAllRegardlessOfStatus()).thenReturn(List.of());
        ClubPostMapper clubPostMapper = mock(ClubPostMapper.class);
        when(clubPostMapper.findAllImageUrls()).thenReturn(List.of("/uploads/club-posts/post-photo.jpg"));

        UploadCleanupService service = new UploadCleanupService(clubMapper, clubPostMapper, uploadDir.toString());

        service.cleanOrphanedUploads();

        assertThat(Files.exists(stillUsed)).isTrue();
        assertThat(Files.exists(orphan)).isFalse();
    }

    @Test
    void reclaimsOrphansNestedSeveralDirectoriesDeep() throws Exception {
        Path nestedOrphan = ageFile(uploadDir.resolve("club-posts").resolve("2026").resolve("orphan.jpg"));

        ClubMapper clubMapper = mock(ClubMapper.class);
        when(clubMapper.findAllRegardlessOfStatus()).thenReturn(List.of());
        ClubPostMapper clubPostMapper = mock(ClubPostMapper.class);
        when(clubPostMapper.findAllImageUrls()).thenReturn(List.of());

        UploadCleanupService service = new UploadCleanupService(clubMapper, clubPostMapper, uploadDir.toString());

        service.cleanOrphanedUploads();

        assertThat(Files.exists(nestedOrphan)).isFalse();
    }

    // Guards the race in the issue: a file can land on disk moments before its row commits, so
    // a cleanup pass that lists files and then queries the database could still observe it as
    // unreferenced in between. A file modified within the grace period is left alone even if it
    // isn't (yet) referenced by anything; only files old enough to not be mid-upload are reclaimed.
    @Test
    void doesNotDeleteAnUnreferencedFileThatWasJustWritten() throws Exception {
        Path justWritten = uploadDir.resolve("club-posts").resolve("brand-new.jpg");
        Files.createDirectories(justWritten.getParent());
        Files.write(justWritten, new byte[] {1});

        ClubMapper clubMapper = mock(ClubMapper.class);
        when(clubMapper.findAllRegardlessOfStatus()).thenReturn(List.of());
        ClubPostMapper clubPostMapper = mock(ClubPostMapper.class);
        when(clubPostMapper.findAllImageUrls()).thenReturn(List.of());

        UploadCleanupService service = new UploadCleanupService(clubMapper, clubPostMapper, uploadDir.toString());

        service.cleanOrphanedUploads();

        assertThat(Files.exists(justWritten)).isTrue();
    }

    private static Path ageFile(Path file) throws Exception {
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[] {1});
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().minus(1, ChronoUnit.DAYS)));
        return file;
    }
}
