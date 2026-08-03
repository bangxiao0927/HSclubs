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
        return isAuthor
            || Boolean.TRUE.equals(club.getCanManage())
            || authenticatedUserResolver.isPlatformOwner(authentication);
    }
}
