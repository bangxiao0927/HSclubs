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

    // Cleanup only owns top-level files this application could itself have written, i.e. the
    // UUID names ImageStorageService produces -- see the non-generated-name test below.
    private static final String STILL_USED_FLAT_FILE = "11111111-1111-4111-8111-111111111111.png";
    private static final String ORPHAN_FLAT_FILE = "22222222-2222-4222-8222-222222222222.png";
    private static final String LEGACY_ORPHAN_FLAT_FILE = "33333333-3333-4333-8333-333333333333.png";

    @TempDir
    Path uploadDir;

    @Test
    void doesNotDeleteAnImageStillUsedByAnArchivedClub() throws Exception {
        Path stillUsed = ageFile(uploadDir.resolve(STILL_USED_FLAT_FILE));
        Path orphan = ageFile(uploadDir.resolve(ORPHAN_FLAT_FILE));

        Club archivedClub = new Club();
        archivedClub.setStatus("archived");
        archivedClub.setImageUrl("/uploads/" + STILL_USED_FLAT_FILE);

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

    // The upload root also holds completely unrelated subsystems -- notably
    // avatar-cache/instagram/, managed by InstagramAvatarCacheService -- that must never be
    // touched by a recursive walk scoped to club images. Cleanup only ever owns legacy flat
    // files directly under the upload root plus anything under club-posts/.
    @Test
    void doesNotDeleteFilesUnderAvatarCacheOrOtherUnrelatedSubdirectories() throws Exception {
        Path avatarCacheFile = ageFile(uploadDir.resolve("avatar-cache").resolve("instagram").resolve("someclub.jpg"));

        ClubMapper clubMapper = mock(ClubMapper.class);
        when(clubMapper.findAllRegardlessOfStatus()).thenReturn(List.of());
        ClubPostMapper clubPostMapper = mock(ClubPostMapper.class);
        when(clubPostMapper.findAllImageUrls()).thenReturn(List.of());

        UploadCleanupService service = new UploadCleanupService(clubMapper, clubPostMapper, uploadDir.toString());

        service.cleanOrphanedUploads();

        assertThat(Files.exists(avatarCacheFile)).isTrue();
    }

    @Test
    void stillReclaimsALegacyFlatOrphanDirectlyUnderTheUploadRoot() throws Exception {
        Path legacyOrphan = ageFile(uploadDir.resolve(LEGACY_ORPHAN_FLAT_FILE));

        ClubMapper clubMapper = mock(ClubMapper.class);
        when(clubMapper.findAllRegardlessOfStatus()).thenReturn(List.of());
        ClubPostMapper clubPostMapper = mock(ClubPostMapper.class);
        when(clubPostMapper.findAllImageUrls()).thenReturn(List.of());

        UploadCleanupService service = new UploadCleanupService(clubMapper, clubPostMapper, uploadDir.toString());

        service.cleanOrphanedUploads();

        assertThat(Files.exists(legacyOrphan)).isFalse();
    }

    // Files.list returns every regular file directly under the upload root, so without a name
    // check this job would reclaim anything an operator or another subsystem keeps there --
    // a README, an .env copy, a database dump -- purely because no club row points at it.
    // ImageStorageService's own delete guard is scoped the same way.
    @Test
    void neverDeletesATopLevelFileThisApplicationCouldNotHaveWritten() throws Exception {
        Path operatorFile = ageFile(uploadDir.resolve("README.txt"));
        Path databaseDump = ageFile(uploadDir.resolve("backup-2026-02-01.sql"));
        Path notAUuidImage = ageFile(uploadDir.resolve("logo.png"));

        ClubMapper clubMapper = mock(ClubMapper.class);
        when(clubMapper.findAllRegardlessOfStatus()).thenReturn(List.of());
        ClubPostMapper clubPostMapper = mock(ClubPostMapper.class);
        when(clubPostMapper.findAllImageUrls()).thenReturn(List.of());

        UploadCleanupService service = new UploadCleanupService(clubMapper, clubPostMapper, uploadDir.toString());

        service.cleanOrphanedUploads();

        assertThat(Files.exists(operatorFile)).isTrue();
        assertThat(Files.exists(databaseDump)).isTrue();
        assertThat(Files.exists(notAUuidImage)).isTrue();
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
