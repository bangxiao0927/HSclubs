package com.example.demo.club.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.example.demo.club.mapper.ClubMapper;
import com.example.demo.club.model.Club;

/**
 * Spring Boot propagates anything an ApplicationRunner throws and closes the context, so a
 * best-effort cleanup job that lets one bad row escape takes the whole site down -- and keeps
 * taking it down on every restart, since nothing ever fixes the row.
 */
class ClubLocationRepairRunnerTest {

    private static Club club(long id, String location) {
        Club club = new Club();
        club.setId(id);
        club.setLocation(location);
        return club;
    }

    @Test
    void oneUnrepairableRowDoesNotStopTheApplicationFromStarting() {
        ClubMapper clubMapper = mock(ClubMapper.class);
        when(clubMapper.findAllLocations()).thenReturn(List.of(club(1L, "101"), club(2L, "room  102")));
        when(clubMapper.updateLocation(eq(1L), any()))
            .thenThrow(new DataIntegrityViolationException("value too long for column location"));
        when(clubMapper.updateLocation(eq(2L), any())).thenReturn(1);

        ClubLocationRepairRunner runner = new ClubLocationRepairRunner(clubMapper);

        assertThatCode(() -> runner.run(null)).doesNotThrowAnyException();
        // The rest of the table is still repaired: one bad row must not skip the others either.
        verify(clubMapper).updateLocation(eq(2L), any());
    }

    @Test
    void rowsAlreadyNormalizedAreLeftAlone() {
        ClubMapper clubMapper = mock(ClubMapper.class);
        String normalized = ClubLocationNormalizer.normalize("Room 101");
        when(clubMapper.findAllLocations()).thenReturn(List.of(club(1L, normalized)));

        new ClubLocationRepairRunner(clubMapper).run(null);

        verify(clubMapper, never()).updateLocation(any(), any());
    }
}
