package com.example.demo.club.service;

import com.example.demo.club.model.Club;
import com.example.demo.security.AuthenticatedUserResolver;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Whether a club's non-active-facing content (its post feed, its comments) is visible to a
 * given viewer. Gated on {@code clubs.status = 'active'}, not {@code clubs.visibility} (that
 * column has no semantics yet -- see #78/#83): a non-active club is visible only to a member,
 * the club president, or a platform owner, so an unapproved club cannot publish to a public
 * page. Shared by {@code ClubPostController#feed} and {@code ClubPostCommentController#list} so
 * the two can never silently disagree about which clubs' content the public can see.
 */
@Component
public class ClubVisibilityPolicy {

    private final AuthenticatedUserResolver authenticatedUserResolver;

    public ClubVisibilityPolicy(AuthenticatedUserResolver authenticatedUserResolver) {
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    public boolean isVisibleTo(Club club, Authentication authentication) {
        boolean isActive = "active".equalsIgnoreCase(club.getStatus());
        if (isActive) {
            return true;
        }
        return Boolean.TRUE.equals(club.getViewerIsMember())
            || Boolean.TRUE.equals(club.getCanManage())
            || authenticatedUserResolver.isPlatformOwner(authentication);
    }
}
