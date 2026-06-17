package com.example.demo.club.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.auth.mapper.OAuthUserMapper;
import com.example.demo.club.mapper.ClubMapper;
import com.example.demo.club.model.Club;
import com.example.demo.club.model.ClubMemberView;
import com.example.demo.club.model.ClubMembershipRequest;
import com.example.demo.club.model.ViewerMembershipStatus;
import com.example.demo.school.mapper.SchoolMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClubServiceMembershipTest {

    @Mock private ClubMapper clubMapper;
    @Mock private SchoolMapper schoolMapper;
    @Mock private OAuthUserMapper oAuthUserMapper;

    private ClubService clubService;
    private static final Long CLUB_ID = 1L;
    private static final Long USER_ID = 100L;
    private static final String USER_EMAIL = "student@example.com";

    @BeforeEach
    void setUp() {
        clubService = new ClubService(clubMapper, schoolMapper, oAuthUserMapper);
    }

    @Test
    void applyForMembershipShouldInsertRequest() {
        when(oAuthUserMapper.findIdByEmail(USER_EMAIL)).thenReturn(USER_ID);
        when(clubMapper.findMembershipStatus(CLUB_ID, USER_EMAIL)).thenReturn(null);
        when(clubMapper.findPendingRequestByClubAndUser(CLUB_ID, USER_ID)).thenReturn(null);

        clubService.applyForMembership(CLUB_ID, USER_EMAIL);

        verify(clubMapper).insertMembershipRequest(CLUB_ID, USER_ID);
    }

    @Test
    void applyForMembershipShouldRejectDuplicateApplication() {
        when(oAuthUserMapper.findIdByEmail(USER_EMAIL)).thenReturn(USER_ID);
        when(clubMapper.findMembershipStatus(CLUB_ID, USER_EMAIL)).thenReturn(null);
        ClubMembershipRequest existing = new ClubMembershipRequest();
        existing.setId(5L);
        when(clubMapper.findPendingRequestByClubAndUser(CLUB_ID, USER_ID)).thenReturn(existing);

        assertThatThrownBy(() -> clubService.applyForMembership(CLUB_ID, USER_EMAIL))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already have a pending request");
    }

    @Test
    void applyForMembershipShouldRejectExistingMember() {
        when(oAuthUserMapper.findIdByEmail(USER_EMAIL)).thenReturn(USER_ID);
        ViewerMembershipStatus status = new ViewerMembershipStatus();
        status.setMember(true);
        when(clubMapper.findMembershipStatus(CLUB_ID, USER_EMAIL)).thenReturn(status);

        assertThatThrownBy(() -> clubService.applyForMembership(CLUB_ID, USER_EMAIL))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already a member");
    }

    @Test
    void approveMembershipShouldAddMemberAndRemoveRequest() {
        ClubMembershipRequest request = new ClubMembershipRequest();
        request.setId(10L);
        request.setClubId(CLUB_ID);
        request.setOauthUserId(USER_ID);
        when(clubMapper.findMembershipRequestById(10L)).thenReturn(request);

        clubService.approveMembershipRequest(CLUB_ID, 10L);

        verify(clubMapper).insertMember(CLUB_ID, USER_ID, "member");
        verify(clubMapper).deleteMembershipRequest(10L);
    }

    @Test
    void approveMembershipShouldRejectWrongClub() {
        ClubMembershipRequest request = new ClubMembershipRequest();
        request.setId(10L);
        request.setClubId(999L);
        request.setOauthUserId(USER_ID);
        when(clubMapper.findMembershipRequestById(10L)).thenReturn(request);

        assertThatThrownBy(() -> clubService.approveMembershipRequest(CLUB_ID, 10L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not found");
    }

    @Test
    void cancelMembershipShouldDeleteRequest() {
        when(oAuthUserMapper.findIdByEmail(USER_EMAIL)).thenReturn(USER_ID);
        ClubMembershipRequest pending = new ClubMembershipRequest();
        pending.setId(7L);
        when(clubMapper.findPendingRequestByClubAndUser(CLUB_ID, USER_ID)).thenReturn(pending);

        clubService.cancelMembershipRequest(CLUB_ID, USER_EMAIL);

        verify(clubMapper).deleteMembershipRequest(7L);
    }

    @Test
    void findMembersShouldReturnMemberList() {
        ClubMemberView member = new ClubMemberView();
        member.setOauthUserId(USER_ID);
        member.setDisplayName("Test Student");
        member.setRoleName("member");
        when(clubMapper.findMembersByClubId(CLUB_ID)).thenReturn(List.of(member));

        List<ClubMemberView> members = clubService.findMembers(CLUB_ID);

        assertThat(members).hasSize(1);
        assertThat(members.get(0).getDisplayName()).isEqualTo("Test Student");
    }

    @Test
    void findPendingRequestsShouldReturnOnlyPending() {
        ClubMembershipRequest pending = new ClubMembershipRequest();
        pending.setId(1L);
        pending.setClubId(CLUB_ID);
        when(clubMapper.findPendingRequestsByClubId(CLUB_ID)).thenReturn(List.of(pending));

        List<ClubMembershipRequest> requests = clubService.findPendingRequests(CLUB_ID);

        assertThat(requests).hasSize(1);
    }

    @Test
    void viewerPermissionsShouldSetCanManageForPresident() {
        Club club = new Club();
        club.setId(CLUB_ID);
        club.setName("Test Club");
        when(clubMapper.findById(CLUB_ID)).thenReturn(club);
        ViewerMembershipStatus status = new ViewerMembershipStatus();
        status.setMember(true);
        status.setRoleName("president");
        when(clubMapper.findMembershipStatus(CLUB_ID, USER_EMAIL)).thenReturn(status);

        Club result = clubService.findById(CLUB_ID, USER_EMAIL);

        assertThat(result.getViewerIsMember()).isTrue();
        assertThat(result.getCanManage()).isTrue();
        assertThat(result.getViewerRole()).isEqualTo("president");
    }
}
