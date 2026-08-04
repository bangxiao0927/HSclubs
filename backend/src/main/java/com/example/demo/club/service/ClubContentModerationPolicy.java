package com.example.demo.club.service;

import com.example.demo.club.model.Club;
import com.example.demo.security.AuthenticatedUserResolver;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Whether a viewer may delete a piece of club content (a post or a comment): its own author,
 * the club president, or a platform owner -- the only moderation matrix this feature defines.
 * Shared by {@code ClubPostController#deletePost} and {@code ClubPostCommentController#delete}
 * so the two deletion endpoints can never silently drift apart on who is allowed to moderate.
 */
@Component
public class ClubContentModerationPolicy {

    private final AuthenticatedUserResolver authenticatedUserResolver;

    public ClubContentModerationPolicy(AuthenticatedUserResolver authenticatedUserResolver) {
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    public boolean canModerate(Long viewerOauthUserId, Long contentAuthorOauthUserId, Club club,
                                Authentication authentication) {
        boolean isAuthor = viewerOauthUserId != null && viewerOauthUserId.equals(contentAuthorOauthUserId);
        return isAuthor || canModerateAnyContent(club, authentication);
    }

    /**
     * The club-wide half of {@link #canModerate}, with no notion of a specific piece of content
     * or its author: the club's own president ({@code club.canManage}) or a platform owner.
     * Exposed on its own so callers that only need "does this viewer outrank every author in
     * this club" -- {@code ClubPostController#feed}'s and {@code ClubPostCommentController#list}'s
     * {@code viewerCanModerateAnyPost} capability flag, and {@code ClubPostController}'s own
     * pin/unpin {@code requireManageAccess} guard -- use the exact same branch {@link #canModerate}
     * itself delegates to here, rather than a second, independently-maintained copy of it.
     */
    public boolean canModerateAnyContent(Club club, Authentication authentication) {
        return Boolean.TRUE.equals(club.getCanManage()) || authenticatedUserResolver.isPlatformOwner(authentication);
    }
}
