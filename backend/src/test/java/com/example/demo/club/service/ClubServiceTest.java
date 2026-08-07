package com.example.demo.club.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.auth.mapper.OAuthUserMapper;
import com.example.demo.club.mapper.ClubMapper;
import com.example.demo.club.model.Club;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ClubServiceTest {

    private ClubMapper clubMapper;
    private ClubService clubService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        clubMapper = mock(ClubMapper.class);
        when(clubMapper.findAllPaginated(anyInt(), anyInt())).thenReturn(List.of());
        when(clubMapper.search(any(), any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of());
        clubService = new ClubService(clubMapper, mock(OAuthUserMapper.class), objectMapper);
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

    // The shared slug-or-id resolver every controller that takes a {clubSlugOrId} path
    // variable delegates to: a purely numeric path segment is looked up by id, anything else
    // by slug -- never both, so a numeric slug (were one ever created) cannot be reached this
    // way, which matches every one of these controllers' pre-existing behavior.
    @Test
    void resolveBySlugOrIdLooksUpByIdWhenTheSegmentIsNumeric() {
        Club club = new Club();
        club.setId(42L);
        when(clubMapper.findById(42L)).thenReturn(club);

        Club resolved = clubService.resolveBySlugOrId("42", "viewer@example.com");

        assertThat(resolved).isSameAs(club);
        verify(clubMapper, never()).findBySlug(any());
    }

    @Test
    void resolveBySlugOrIdLooksUpBySlugWhenTheSegmentIsNotNumeric() {
        Club club = new Club();
        club.setId(42L);
        when(clubMapper.findBySlug("chess-club")).thenReturn(club);

        Club resolved = clubService.resolveBySlugOrId("chess-club", "viewer@example.com");

        assertThat(resolved).isSameAs(club);
        verify(clubMapper, never()).findById(any());
    }

    @Test
    void resolveBySlugOrIdReturnsNullWhenNeitherLookupFindsTheClub() {
        Club resolved = clubService.resolveBySlugOrId("does-not-exist", "viewer@example.com");

        assertThat(resolved).isNull();
    }

    // The update statement writes every column, so what the service hands it *is* the new row.
    // A manager editing one field must not blank out the fields their form never sent.
    @Test
    void updateKeepsStoredValuesForFieldsTheRequestDidNotMention() {
        when(clubMapper.findById(7L)).thenReturn(storedClub());

        clubService.update(7L, json("{\"name\":\"Chess Team\"}"));

        Club written = captureUpdatedClub();
        assertThat(written.getName()).isEqualTo("Chess Team");
        assertThat(written.getDescription()).isEqualTo("Weekly chess practice");
        assertThat(written.getAdvisor()).isEqualTo("Ms. Lee");
        assertThat(written.getContactEmail()).isEqualTo("chess@example.com");
        assertThat(written.getMeetingSchedule()).isEqualTo("Wednesday lunch");
        assertThat(written.getAchievements()).containsExactly("State finalist");
    }

    // Clearing an optional field is a real edit the admin form performs (it sends null), and
    // must stay distinguishable from not mentioning the field at all.
    @Test
    void updateStillClearsAFieldThatIsExplicitlyNull() {
        when(clubMapper.findById(7L)).thenReturn(storedClub());

        clubService.update(7L, json("{\"advisor\":null}"));

        assertThat(captureUpdatedClub().getAdvisor()).isNull();
    }

    // status/visibility/approval decide whether a club is listed and published at all, and slug
    // is its public URL. A club president reaches this method through PUT /api/clubs/{id}, so
    // these must be carried over from the stored row no matter what the body contains.
    @Test
    void updateIgnoresStatusVisibilityApprovalAndSlugFromTheRequestBody() {
        when(clubMapper.findById(7L)).thenReturn(storedClub());

        clubService.update(7L, json("""
            {"name":"Chess Team","slug":"hijacked","status":"archived","visibility":"private",
             "approvedByOauthUserId":99,"imageUrl":"/uploads/club-posts/someone-elses.jpg"}"""));

        Club written = captureUpdatedClub();
        assertThat(written.getSlug()).isEqualTo("chess-club");
        assertThat(written.getStatus()).isEqualTo("active");
        assertThat(written.getVisibility()).isEqualTo("public");
        assertThat(written.getApprovedByOauthUserId()).isEqualTo(11L);
        assertThat(written.getImageUrl()).isEqualTo("/uploads/club-posts/original.jpg");
    }

    @Test
    void updateRejectsAnUnknownCategoryEvenWhenEveryOtherFieldIsOmitted() {
        when(clubMapper.findById(7L)).thenReturn(storedClub());

        try {
            clubService.update(7L, json("{\"category\":\"Not A Category\"}"));
            assertThat(false).as("expected an IllegalArgumentException").isTrue();
        } catch (IllegalArgumentException expected) {
            assertThat(expected).hasMessage("Invalid category");
        }
        verify(clubMapper, never()).update(any());
    }

    private Club captureUpdatedClub() {
        ArgumentCaptor<Club> captor = ArgumentCaptor.forClass(Club.class);
        verify(clubMapper).update(captor.capture());
        return captor.getValue();
    }

    private JsonNode json(String body) {
        return objectMapper.readTree(body);
    }

    private static Club storedClub() {
        Club club = new Club();
        club.setId(7L);
        club.setName("Chess Club");
        club.setSlug("chess-club");
        club.setDescription("Weekly chess practice");
        club.setCategory("Competition & Strategy");
        club.setMeetingSchedule("Wednesday lunch");
        club.setAdvisor("Ms. Lee");
        club.setContactEmail("chess@example.com");
        club.setAchievements(List.of("State finalist"));
        club.setStatus("active");
        club.setVisibility("public");
        club.setApprovedByOauthUserId(11L);
        club.setImageUrl("/uploads/club-posts/original.jpg");
        return club;
    }
}
