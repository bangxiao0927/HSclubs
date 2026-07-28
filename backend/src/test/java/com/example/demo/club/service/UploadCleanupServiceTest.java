package com.example.demo.club.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.demo.club.mapper.ClubMapper;
import com.example.demo.club.model.Club;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UploadCleanupServiceTest {

    @TempDir
    Path uploadDir;

    @Test
    void doesNotDeleteAnImageStillUsedByAnArchivedClub() throws Exception {
        Path stillUsed = uploadDir.resolve("still-used.png");
        Files.write(stillUsed, new byte[] { 1 });
        Path orphan = uploadDir.resolve("orphan.png");
        Files.write(orphan, new byte[] { 2 });

        Club archivedClub = new Club();
        archivedClub.setStatus("archived");
        archivedClub.setImageUrl("/uploads/still-used.png");

        ClubMapper clubMapper = mock(ClubMapper.class);
        when(clubMapper.findAllRegardlessOfStatus()).thenReturn(List.of(archivedClub));

        UploadCleanupService service = new UploadCleanupService(clubMapper, uploadDir.toString());

        service.cleanOrphanedUploads();

        assertThat(Files.exists(stillUsed)).isTrue();
        assertThat(Files.exists(orphan)).isFalse();
    }
}
