package com.example.demo.summary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.demo.club.mapper.ClubMapper;
import com.example.demo.summary.model.ClubSummaryProjection;
import com.example.demo.summary.model.SummaryResponse;

class SummaryServiceTest {

    private static ClubSummaryProjection club(long id, String name, String category, int memberCount,
                                              LocalDateTime updatedAt) {
        ClubSummaryProjection projection = new ClubSummaryProjection();
        projection.setId(id);
        projection.setName(name);
        projection.setCategory(category);
        projection.setMemberCount(memberCount);
        projection.setUpdatedAt(updatedAt);
        return projection;
    }

    private static List<ClubSummaryProjection> directory() {
        return List.of(
            club(1L, "Chess Club", "Competition & Strategy", 12, LocalDateTime.of(2026, 1, 2, 3, 4)),
            club(2L, "Robotics", "STEM & Innovation", 30, LocalDateTime.of(2026, 2, 3, 4, 5)),
            club(3L, "Debate", "Competition & Strategy", 8, null));
    }

    private static SummaryService service(ClubMapper clubMapper, long cacheTtlMillis) {
        return new SummaryService(clubMapper, "Example High School", "EHS", "ehs", cacheTtlMillis);
    }

    @Test
    void summarizesClubCountsMembersAndTheNewestUpdate() {
        ClubMapper clubMapper = mock(ClubMapper.class);
        when(clubMapper.findSummaryProjections()).thenReturn(directory());

        SummaryResponse summary = service(clubMapper, 0).buildSummary();

        assertThat(summary.getSchoolName()).isEqualTo("Example High School");
        assertThat(summary.getClubCount()).isEqualTo(3);
        assertThat(summary.getMemberCount()).isEqualTo(50);
        assertThat(summary.getCategories())
            .containsEntry("Competition & Strategy", 2)
            .containsEntry("STEM & Innovation", 1);
        assertThat(summary.getLastUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 2, 3, 4, 5));
        assertThat(summary.getDataHash()).isNotBlank();
    }

    // The aggregator compares this hash to decide whether to re-fetch, so it has to change when
    // the directory changes and stay put when it does not.
    @Test
    void theDataHashTracksTheDirectoryContents() {
        ClubMapper clubMapper = mock(ClubMapper.class);
        when(clubMapper.findSummaryProjections()).thenReturn(directory());
        String first = service(clubMapper, 0).buildSummary().getDataHash();

        ClubMapper sameContents = mock(ClubMapper.class);
        when(sameContents.findSummaryProjections()).thenReturn(directory());
        assertThat(service(sameContents, 0).buildSummary().getDataHash()).isEqualTo(first);

        ClubMapper changed = mock(ClubMapper.class);
        when(changed.findSummaryProjections()).thenReturn(
            List.of(club(1L, "Chess Club", "Competition & Strategy", 13, null)));
        assertThat(service(changed, 0).buildSummary().getDataHash()).isNotEqualTo(first);
    }

    // This endpoint is unauthenticated, so a burst of requests is the cheapest way to make the
    // site do work. Within the TTL they must collapse onto a single query.
    @Test
    void repeatedRequestsInsideTheTtlReuseOneQuery() {
        ClubMapper clubMapper = mock(ClubMapper.class);
        when(clubMapper.findSummaryProjections()).thenReturn(directory());
        SummaryService service = service(clubMapper, 60_000);

        SummaryResponse first = service.buildSummary();
        SummaryResponse second = service.buildSummary();

        assertThat(second).isSameAs(first);
        verify(clubMapper, times(1)).findSummaryProjections();
    }

    @Test
    void aZeroTtlDisablesTheCacheSoEveryRequestSeesCurrentData() {
        ClubMapper clubMapper = mock(ClubMapper.class);
        when(clubMapper.findSummaryProjections()).thenReturn(directory());
        SummaryService service = service(clubMapper, 0);

        service.buildSummary();
        service.buildSummary();

        verify(clubMapper, times(2)).findSummaryProjections();
    }
}
