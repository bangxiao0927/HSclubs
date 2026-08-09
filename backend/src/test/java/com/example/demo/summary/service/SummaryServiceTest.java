package com.example.demo.summary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
        // A fixed zone, so the published instant is deterministic rather than whatever zone the
        // machine running the tests happens to keep.
        return new SummaryService(clubMapper, "Example High School", "EHS", "ehs", "1 Example Way, Springfield", "UTC", "", cacheTtlMillis);
    }

    @Test
    void summarizesClubCountsMembersAndTheNewestUpdate() {
        ClubMapper clubMapper = mock(ClubMapper.class);
        when(clubMapper.findSummaryProjections()).thenReturn(directory());

        SummaryResponse summary = service(clubMapper, 0).buildSummary();

        assertThat(summary.getSchoolName()).isEqualTo("Example High School");
        assertThat(summary.getAddress()).isEqualTo("1 Example Way, Springfield");
        assertThat(summary.getClubCount()).isEqualTo(3);
        assertThat(summary.getMemberCount()).isEqualTo(50);
        assertThat(summary.getCategories())
            .containsEntry("Competition & Strategy", 2)
            .containsEntry("STEM & Innovation", 1);
        assertThat(summary.getLastUpdatedAt())
            .isEqualTo(LocalDateTime.of(2026, 2, 3, 4, 5).atOffset(ZoneOffset.UTC));
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

    // School identity, like the name: configured, never derived from a club row, and absent
    // rather than blank when a deployment has not set one.
    @Test
    void omitsTheAddressWhenNoneIsConfigured() {
        ClubMapper clubMapper = mock(ClubMapper.class);
        when(clubMapper.findSummaryProjections()).thenReturn(directory());

        SummaryService unaddressed = new SummaryService(
            clubMapper, "Example High School", "EHS", "ehs", "   ", "UTC", "", 0);

        assertThat(unaddressed.buildSummary().getAddress()).isNull();
    }

    // The tag validates the whole representation, so changing the school's address has to
    // invalidate a poller's cached copy even though no club changed.
    @Test
    void theEntityTagCoversTheAddress() {
        ClubMapper first = mock(ClubMapper.class);
        when(first.findSummaryProjections()).thenReturn(directory());
        ClubMapper second = mock(ClubMapper.class);
        when(second.findSummaryProjections()).thenReturn(directory());

        String before = service(first, 0).buildSnapshot().entityTag();
        String after = new SummaryService(
            second, "Example High School", "EHS", "ehs", "2 Other Road, Springfield", "UTC", "", 0)
            .buildSnapshot().entityTag();

        assertThat(after).isNotEqualTo(before);
    }

    // The stored timestamp is a bare wall clock; what leaves the building has to say which zone
    // it meant, or a page comparing schools in different zones is comparing nothing.
    @Test
    void publishesTheLastUpdateAsAnInstantInTheConfiguredZone() {
        ClubMapper clubMapper = mock(ClubMapper.class);
        when(clubMapper.findSummaryProjections()).thenReturn(directory());

        SummaryService tokyo = new SummaryService(
            clubMapper, "Example High School", "EHS", "ehs", "", "Asia/Tokyo", "", 0);

        assertThat(tokyo.buildSummary().getLastUpdatedAt())
            .isEqualTo(LocalDateTime.of(2026, 2, 3, 4, 5).atZone(ZoneId.of("Asia/Tokyo")).toOffsetDateTime());
    }

    @Test
    void fallsBackToThisMachinesZoneWhenNoneIsConfigured() {
        ClubMapper clubMapper = mock(ClubMapper.class);
        when(clubMapper.findSummaryProjections()).thenReturn(directory());

        SummaryService unconfigured = new SummaryService(
            clubMapper, "Example High School", "EHS", "ehs", "", "  ", "", 0);

        assertThat(unconfigured.buildSummary().getLastUpdatedAt())
            .isEqualTo(LocalDateTime.of(2026, 2, 3, 4, 5).atZone(ZoneId.systemDefault()).toOffsetDateTime());
    }

    // The projection reads updated_at into a LocalDateTime, so the driver hands back the
    // *database session's* wall clock, not this JVM's. Defaulting to the JVM zone would stamp a
    // false offset onto it whenever the two differ -- and the URL this repo ships declares
    // Asia/Shanghai, so they usually would.
    @Test
    void defaultsToTheZoneTheDatasourceUrlDeclares() {
        ClubMapper clubMapper = mock(ClubMapper.class);
        when(clubMapper.findSummaryProjections()).thenReturn(directory());
        String url = "jdbc:mysql://localhost:3306/mydb?useUnicode=true&serverTimezone=Asia/Shanghai&useSSL=false";

        SummaryService service = new SummaryService(
            clubMapper, "Example High School", "EHS", "ehs", "", "", url, 0);

        assertThat(service.buildSummary().getLastUpdatedAt())
            .isEqualTo(LocalDateTime.of(2026, 2, 3, 4, 5).atZone(ZoneId.of("Asia/Shanghai")).toOffsetDateTime());
    }

    @Test
    void anExplicitZoneWinsOverTheDatasourceUrl() {
        ClubMapper clubMapper = mock(ClubMapper.class);
        when(clubMapper.findSummaryProjections()).thenReturn(directory());
        String url = "jdbc:mysql://localhost:3306/mydb?serverTimezone=Asia/Shanghai";

        SummaryService service = new SummaryService(
            clubMapper, "Example High School", "EHS", "ehs", "", "America/Los_Angeles", url, 0);

        assertThat(service.buildSummary().getLastUpdatedAt())
            .isEqualTo(LocalDateTime.of(2026, 2, 3, 4, 5)
                .atZone(ZoneId.of("America/Los_Angeles")).toOffsetDateTime());
    }

    @Test
    void anUnparseableServerTimezoneFallsBackInsteadOfFailingStartup() {
        ClubMapper clubMapper = mock(ClubMapper.class);
        when(clubMapper.findSummaryProjections()).thenReturn(directory());
        String url = "jdbc:mysql://localhost:3306/mydb?serverTimezone=Not%2FAZone";

        SummaryService service = new SummaryService(
            clubMapper, "Example High School", "EHS", "ehs", "", "", url, 0);

        assertThat(service.buildSummary().getLastUpdatedAt())
            .isEqualTo(LocalDateTime.of(2026, 2, 3, 4, 5).atZone(ZoneId.systemDefault()).toOffsetDateTime());
    }

    @Test
    void aDirectoryThatWasNeverUpdatedHasNoLastUpdate() {
        ClubMapper clubMapper = mock(ClubMapper.class);
        when(clubMapper.findSummaryProjections())
            .thenReturn(List.of(club(1L, "Chess Club", "Competition & Strategy", 12, null)));

        assertThat(service(clubMapper, 0).buildSummary().getLastUpdatedAt()).isNull();
    }
    // The entity tag validates the whole response, so it must move even when dataHash cannot:
    // editing a club's description bumps updated_at, and therefore lastUpdatedAt, without
    // touching any of the four fields dataHash digests. Reusing dataHash as the ETag would
    // answer 304 for a body that really did change.
    @Test
    void theEntityTagCoversFieldsTheDataHashDoesNot() {
        ClubMapper first = mock(ClubMapper.class);
        when(first.findSummaryProjections()).thenReturn(directory());
        SummaryService.Snapshot before = service(first, 0).buildSnapshot();

        ClubMapper second = mock(ClubMapper.class);
        when(second.findSummaryProjections()).thenReturn(List.of(
            club(1L, "Chess Club", "Competition & Strategy", 12, LocalDateTime.of(2026, 1, 2, 3, 4)),
            club(2L, "Robotics", "STEM & Innovation", 30, LocalDateTime.of(2026, 9, 9, 9, 9)),
            club(3L, "Debate", "Competition & Strategy", 8, null)));
        SummaryService.Snapshot after = service(second, 0).buildSnapshot();

        assertThat(after.summary().getDataHash()).isEqualTo(before.summary().getDataHash());
        assertThat(after.summary().getLastUpdatedAt()).isNotEqualTo(before.summary().getLastUpdatedAt());
        assertThat(after.entityTag()).isNotEqualTo(before.entityTag());
    }

    @Test
    void theEntityTagIsStableForUnchangedData() {
        ClubMapper first = mock(ClubMapper.class);
        when(first.findSummaryProjections()).thenReturn(directory());
        ClubMapper second = mock(ClubMapper.class);
        when(second.findSummaryProjections()).thenReturn(directory());

        assertThat(service(second, 0).buildSnapshot().entityTag())
            .isEqualTo(service(first, 0).buildSnapshot().entityTag());
    }
}
