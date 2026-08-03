package com.example.demo.club.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.demo.club.model.Club;
import com.example.demo.security.AuthenticatedUserResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class ClubVisibilityPolicyTest {

    private AuthenticatedUserResolver authenticatedUserResolver;
    private ClubVisibilityPolicy clubVisibilityPolicy;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        authenticatedUserResolver = mock(AuthenticatedUserResolver.class);
        clubVisibilityPolicy = new ClubVisibilityPolicy(authenticatedUserResolver);
        authentication = mock(Authentication.class);
    }

    private static Club aClub(String status, Boolean viewerIsMember, Boolean canManage) {
        Club club = new Club();
        club.setStatus(status);
        club.setViewerIsMember(viewerIsMember);
        club.setCanManage(canManage);
        return club;
    }

    @Test
    void isVisibleToIsTrueForAnActiveClubRegardlessOfTheViewer() {
        Club club = aClub("active", false, false);

        assertThat(clubVisibilityPolicy.isVisibleTo(club, authentication)).isTrue();
    }

    @Test
    void isVisibleToTreatsTheActiveStatusCaseInsensitively() {
        Club club = aClub("ACTIVE", false, false);

        assertThat(clubVisibilityPolicy.isVisibleTo(club, authentication)).isTrue();
    }

    @Test
    void isVisibleToIsFalseForANonActiveClubToAnAnonymousOrUnrelatedViewer() {
        Club club = aClub("pending", false, false);
        when(authenticatedUserResolver.isPlatformOwner(authentication)).thenReturn(false);

        assertThat(clubVisibilityPolicy.isVisibleTo(club, authentication)).isFalse();
    }

    @Test
    void isVisibleToIsTrueForANonActiveClubToAMember() {
        Club club = aClub("pending", true, false);

        assertThat(clubVisibilityPolicy.isVisibleTo(club, authentication)).isTrue();
    }

    @Test
    void isVisibleToIsTrueForANonActiveClubToThePresident() {
        Club club = aClub("pending", false, true);

        assertThat(clubVisibilityPolicy.isVisibleTo(club, authentication)).isTrue();
    }

    @Test
    void isVisibleToIsTrueForANonActiveClubToAPlatformOwner() {
        Club club = aClub("pending", false, false);
        when(authenticatedUserResolver.isPlatformOwner(authentication)).thenReturn(true);

        assertThat(clubVisibilityPolicy.isVisibleTo(club, authentication)).isTrue();
    }

    @Test
    void isVisibleToTreatsANullStatusAsNotActive() {
        Club club = aClub(null, false, false);
        when(authenticatedUserResolver.isPlatformOwner(authentication)).thenReturn(false);

        assertThat(clubVisibilityPolicy.isVisibleTo(club, authentication)).isFalse();
    }
}
