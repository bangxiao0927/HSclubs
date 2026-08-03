package com.example.demo.club.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.demo.club.model.Club;
import com.example.demo.security.AuthenticatedUserResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class ClubContentModerationPolicyTest {

    private static final Long AUTHOR_OAUTH_USER_ID = 42L;

    private AuthenticatedUserResolver authenticatedUserResolver;
    private ClubContentModerationPolicy clubContentModerationPolicy;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        authenticatedUserResolver = mock(AuthenticatedUserResolver.class);
        clubContentModerationPolicy = new ClubContentModerationPolicy(authenticatedUserResolver);
        authentication = mock(Authentication.class);
    }

    private static Club clubWithCanManage(boolean canManage) {
        Club club = new Club();
        club.setCanManage(canManage);
        return club;
    }

    @Test
    void canModerateIsTrueForTheContentsOwnAuthor() {
        Club club = clubWithCanManage(false);

        boolean result = clubContentModerationPolicy.canModerate(
            AUTHOR_OAUTH_USER_ID, AUTHOR_OAUTH_USER_ID, club, authentication);

        assertThat(result).isTrue();
    }

    @Test
    void canModerateIsTrueForTheClubPresident() {
        Club club = clubWithCanManage(true);

        boolean result = clubContentModerationPolicy.canModerate(99L, AUTHOR_OAUTH_USER_ID, club, authentication);

        assertThat(result).isTrue();
    }

    @Test
    void canModerateIsTrueForAPlatformOwner() {
        Club club = clubWithCanManage(false);
        when(authenticatedUserResolver.isPlatformOwner(authentication)).thenReturn(true);

        boolean result = clubContentModerationPolicy.canModerate(99L, AUTHOR_OAUTH_USER_ID, club, authentication);

        assertThat(result).isTrue();
    }

    @Test
    void canModerateIsFalseForANonAuthorNonPresidentNonOwner() {
        Club club = clubWithCanManage(false);
        when(authenticatedUserResolver.isPlatformOwner(authentication)).thenReturn(false);

        boolean result = clubContentModerationPolicy.canModerate(99L, AUTHOR_OAUTH_USER_ID, club, authentication);

        assertThat(result).isFalse();
    }

    // A viewer the caller could not resolve to an oauth_users row at all (e.g. findIdByEmail
    // returned null) must never accidentally match a null-authored row by coincidence.
    @Test
    void canModerateIsFalseWhenTheViewersOauthUserIdIsNull() {
        Club club = clubWithCanManage(false);
        when(authenticatedUserResolver.isPlatformOwner(authentication)).thenReturn(false);

        boolean result = clubContentModerationPolicy.canModerate(null, AUTHOR_OAUTH_USER_ID, club, authentication);

        assertThat(result).isFalse();
    }
}
