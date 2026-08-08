package com.example.demo.club.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.example.demo.club.model.ClubMembershipRequest;
import com.example.demo.club.model.ViewerMembershipStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

class ClubServiceTest {

    private ClubMapper clubMapper;
    private OAuthUserMapper oAuthUserMapper;
    private ClubService clubService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        clubMapper = mock(ClubMapper.class);
        oAuthUserMapper = mock(OAuthUserMapper.class);
        when(clubMapper.findAllPaginated(anyInt(), anyInt())).thenReturn(List.of());
        when(clubMapper.search(any(), any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of());
        clubService = new ClubService(clubMapper, oAuthUserMapper, objectMapper);
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

        verify(clubMapper).search(eq(null), eq(null), eq(null), eq(null), eq(List.of()), eq(2), eq(1));
    }

    // Each word has to be able to match a different field, which is what the frontend filter
    // does too; a single-substring query missed "Chess Club" for "club chess".
    @Test
    void searchSplitsTheFreeTextQueryIntoWords() {
        clubService.search(null, null, null, null, "  club   chess  ", 0, 10);

        verify(clubMapper).search(any(), any(), any(), any(), eq(List.of("club", "chess")), anyInt(), anyInt());
    }

    @Test
    void searchCapsTheNumberOfWordsItWillTurnIntoSqlConditions() {
        String longQuery = String.join(" ", Collections.nCopies(50, "a").toArray(new String[0]));
        clubService.search(null, null, null, null, longQuery + " b c d e f g h i j k l", 0, 10);

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.captor();
        verify(clubMapper).search(any(), any(), any(), any(), captor.capture(), anyInt(), anyInt());
        assertThat(captor.getValue()).hasSizeLessThanOrEqualTo(10);
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

        assertThatThrownBy(() -> clubService.update(7L, json("{\"category\":\"Not A Category\"}")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid category");

        verify(clubMapper, never()).update(any());
    }

    // The endpoint takes raw JSON, so a body that is valid JSON but not an object reaches the
    // service instead of failing during binding. It must be a 400 (IllegalArgumentException,
    // which the controller maps) rather than an unhandled 500.
    @Test
    void updateRejectsABodyThatIsNotAJsonObject() {
        assertThatThrownBy(() -> clubService.update(7L, json("[1, 2, 3]")))
            .isInstanceOf(IllegalArgumentException.class);

        verify(clubMapper, never()).update(any());
    }

    // ---- Membership ----

    // The recommendation endpoint is public and returns at most 20 rows; reading every club to
    // sort and truncate in Java made that cost unbounded in table size.
    @Test
    void recommendationsForASignedOutVisitorAreOrderedAndLimitedInSql() {
        clubService.getRecommendations(null, 8);

        verify(clubMapper).findPopular(8);
        verify(clubMapper, never()).findAll();
    }

    @Test
    void recommendationsForAMemberAskSqlForTheirCategoriesExcludingClubsTheyJoined() {
        when(oAuthUserMapper.findIdByEmail("student@example.com")).thenReturn(21L);
        when(clubMapper.findCategoriesByOauthUserId(21L)).thenReturn(List.of("STEM & Innovation"));
        when(clubMapper.findClubIdsByOauthUserId(21L)).thenReturn(List.of(3L));
        when(clubMapper.findPopularInCategories(List.of("STEM & Innovation"), List.of(3L), 8))
            .thenReturn(List.of(new Club()));

        clubService.getRecommendations("student@example.com", 8);

        verify(clubMapper).findPopularInCategories(List.of("STEM & Innovation"), List.of(3L), 8);
        verify(clubMapper, never()).findAll();
    }

    @Test
    void recommendationsFallBackToPopularClubsWhenNothingMatchesTheirCategories() {
        when(oAuthUserMapper.findIdByEmail("student@example.com")).thenReturn(21L);
        when(clubMapper.findCategoriesByOauthUserId(21L)).thenReturn(List.of("STEM & Innovation"));
        when(clubMapper.findClubIdsByOauthUserId(21L)).thenReturn(List.of(3L));
        when(clubMapper.findPopularInCategories(any(), any(), anyInt())).thenReturn(List.of());

        clubService.getRecommendations("student@example.com", 8);

        verify(clubMapper).findPopular(8);
    }


    // status is deliberately not writable through update(), which a club president can reach,
    // so archiving has to go through the column-scoped statement instead.
    @Test
    void archiveWritesOnlyTheStatusColumnAndNeverTheGeneralUpdate() {
        when(clubMapper.updateStatus(7L, "archived")).thenReturn(1);

        clubService.archive(7L);

        verify(clubMapper).updateStatus(7L, "archived");
        verify(clubMapper, never()).update(any());
    }

    @Test
    void restorePutsTheClubBackIntoTheDirectory() {
        Club archived = storedClub();
        archived.setStatus("archived");
        when(clubMapper.findById(7L)).thenReturn(archived);

        clubService.restore(7L);

        verify(clubMapper).updateStatus(7L, "active");
    }

    // No previous status is recorded anywhere, so activating something that was never archived
    // would publish an unapproved club and lose its pending state for good.
    @Test
    void restoreRefusesAClubThatWasNeverArchived() {
        Club pending = storedClub();
        pending.setStatus("pending");
        when(clubMapper.findById(7L)).thenReturn(pending);

        assertThatThrownBy(() -> clubService.restore(7L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("archived");

        verify(clubMapper, never()).updateStatus(any(), any());
    }

    @Test
    void archivingAMissingClubIsRejectedBeforeAnyWrite() {
        assertThatThrownBy(() -> clubService.archive(404L))
            .isInstanceOf(IllegalArgumentException.class);
    }


    // Applying while already in the club is what let an approval overwrite the applicant's
    // stored role, so the request must never be created in the first place.
    @Test
    void applyForMembershipRejectsSomeoneWhoIsAlreadyAMember() {
        when(oAuthUserMapper.findIdByEmail("member@example.com")).thenReturn(20L);
        when(clubMapper.findMembershipStatusByUserId(3L, 20L)).thenReturn(membershipStatus());

        assertThatThrownBy(() -> clubService.applyForMembership(3L, "member@example.com"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already a member");

        verify(clubMapper, never()).insertMembershipRequest(any(), any());
    }

    @Test
    void applyForMembershipStillCreatesARequestForANonMember() {
        when(oAuthUserMapper.findIdByEmail("student@example.com")).thenReturn(21L);
        when(clubMapper.findMembershipStatusByUserId(3L, 21L)).thenReturn(null);

        clubService.applyForMembership(3L, "student@example.com");

        verify(clubMapper).insertMembershipRequest(3L, 21L);
    }

    // The duplicate check is check-then-insert, so a double-clicked Apply can race past it and
    // hit uq_club_membership_request instead. That is the same conflict, so it has to leave the
    // service as the same exception (which the controller maps to 409), not as a 500.
    @Test
    void applyForMembershipTurnsAConcurrentDuplicateIntoTheSameConflict() {
        when(oAuthUserMapper.findIdByEmail("student@example.com")).thenReturn(21L);
        when(clubMapper.findMembershipStatusByUserId(3L, 21L)).thenReturn(null);
        when(clubMapper.insertMembershipRequest(3L, 21L))
            .thenThrow(new DuplicateKeyException("uq_club_membership_request"));

        assertThatThrownBy(() -> clubService.applyForMembership(3L, "student@example.com"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already have a pending request");
    }

    // insertMember overwrites role_name (that is how a president is assigned), so approving a
    // join request has to use the insert-if-absent statement instead.
    @Test
    void approveMembershipRequestNeverOverwritesAnExistingRole() {
        ClubMembershipRequest request = new ClubMembershipRequest();
        request.setId(5L);
        request.setClubId(3L);
        request.setOauthUserId(20L);
        when(clubMapper.findMembershipRequestById(5L)).thenReturn(request);

        clubService.approveMembershipRequest(3L, 5L);

        verify(clubMapper).insertMemberIfAbsent(3L, 20L, "member");
        verify(clubMapper, never()).insertMember(any(), any(), any());
        verify(clubMapper).deleteMembershipRequest(5L);
    }

    private static ViewerMembershipStatus membershipStatus() {
        ViewerMembershipStatus status = new ViewerMembershipStatus();
        status.setMember(true);
        status.setRoleName("president");
        return status;
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
