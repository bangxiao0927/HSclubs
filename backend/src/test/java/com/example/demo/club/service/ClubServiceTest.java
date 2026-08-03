package com.example.demo.club.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.auth.mapper.OAuthUserMapper;
import com.example.demo.club.mapper.ClubMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClubServiceTest {

    private ClubMapper clubMapper;
    private ClubService clubService;

    @BeforeEach
    void setUp() {
        clubMapper = mock(ClubMapper.class);
        when(clubMapper.findAllPaginated(anyInt(), anyInt())).thenReturn(List.of());
        when(clubMapper.search(any(), any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of());
        clubService = new ClubService(clubMapper, mock(OAuthUserMapper.class));
    }

    @Test
    void findAllPaginatedTreatsNegativePageAsFirstPage() {
        clubService.findAllPaginated(-5, 10);

        verify(clubMapper).findAllPaginated(0, 10);
    }

    @Test
    void findAllPaginatedTreatsNegativeSizeAsSmallestValidPageSize() {
        clubService.findAllPaginated(2, -100);

        // Offset must be computed from the *clamped* limit (1), matching search()'s behavior --
        // this is the inconsistency the bug report called out.
        verify(clubMapper).findAllPaginated(2, 1);
    }

    @Test
    void findAllPaginatedCapsOversizedPageSizeAtOneHundred() {
        clubService.findAllPaginated(0, 10_000);

        verify(clubMapper).findAllPaginated(0, 100);
    }

    @Test
    void findAllPaginatedNeverOverflowsOffsetForHugePageNumbers() {
        clubService.findAllPaginated(Integer.MAX_VALUE, 100);

        verify(clubMapper).findAllPaginated(eq(Integer.MAX_VALUE), eq(100));
    }

    @Test
    void searchUsesTheSameClampedLimitToComputeOffsetAsFindAllPaginated() {
        clubService.search(null, null, null, null, null, 2, -100);

        verify(clubMapper).search(eq(null), eq(null), eq(null), eq(null), eq(null), eq(2), eq(1));
    }

    // Only this dedicated method (backed by ClubMapper#updateImageUrl, a column-scoped SQL
    // statement -- see ClubMapperTest) may change a club's stored image URL. Ordinary update()
    // must never call it, and must never be the path that lets a caller-supplied imageUrl
    // reach the database.
    @Test
    void updateImageUrlDelegatesToTheDedicatedMapperMethodNotTheGeneralUpdate() {
        clubService.updateImageUrl(1L, "/uploads/club-posts/new-uuid.jpg");

        verify(clubMapper).updateImageUrl(1L, "/uploads/club-posts/new-uuid.jpg");
        verify(clubMapper, never()).update(any());
    }
}
