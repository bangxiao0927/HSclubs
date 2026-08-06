package com.example.demo.club.controller;

import com.example.demo.auth.mapper.OAuthUserMapper;
import com.example.demo.club.model.Club;
import com.example.demo.club.model.ClubPost;
import com.example.demo.club.model.PublicClubPost;
import com.example.demo.club.service.ClubContentModerationPolicy;
import com.example.demo.club.service.ImageProcessingUnavailableException;
import com.example.demo.club.service.ClubPostService;
import com.example.demo.club.service.ClubService;
import com.example.demo.club.service.ClubVisibilityPolicy;
import com.example.demo.security.AuthenticatedUserResolver;
import org.springframework.http.HttpStatus;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

/**
 * Publishing a club media post and reading a club's public feed. Publishing is one atomic
 * multipart request (title + file); a "upload the photo, then create the post" two-request
 * design would either orphan the file when a caller abandons the form or force the server to
 * prove a client-supplied imageUrl was actually minted by us for this request.
 */
@RestController
@RequestMapping("/api/clubs")
public class ClubPostController {

    private final ClubService clubService;
    private final ClubPostService clubPostService;
    private final OAuthUserMapper oAuthUserMapper;
    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final ClubVisibilityPolicy clubVisibilityPolicy;
    private final ClubContentModerationPolicy clubContentModerationPolicy;

    public ClubPostController(ClubService clubService,
                              ClubPostService clubPostService,
                              OAuthUserMapper oAuthUserMapper,
                              AuthenticatedUserResolver authenticatedUserResolver,
                              ClubVisibilityPolicy clubVisibilityPolicy,
                              ClubContentModerationPolicy clubContentModerationPolicy) {
        this.clubService = clubService;
        this.clubPostService = clubPostService;
        this.oAuthUserMapper = oAuthUserMapper;
        this.authenticatedUserResolver = authenticatedUserResolver;
        this.clubVisibilityPolicy = clubVisibilityPolicy;
        this.clubContentModerationPolicy = clubContentModerationPolicy;
    }

    @PostMapping("/{clubSlugOrId}/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public PublicClubPost publish(@PathVariable String clubSlugOrId,
                                   @RequestParam("title") String title,
                                   @RequestParam("file") MultipartFile file,
                                   Authentication authentication) {
        String viewerEmail = authenticatedUserResolver.requireEmail(authentication);
        Club club = clubService.resolveBySlugOrId(clubSlugOrId, viewerEmail);
        if (club == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found");
        }
        if (!Boolean.TRUE.equals(club.getViewerIsMember())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Members only");
        }

        Long authorOauthUserId = oAuthUserMapper.findIdByEmail(viewerEmail);
        try {
            return clubPostService.publish(club.getId(), authorOauthUserId, title, file);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (ImageProcessingUnavailableException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store file");
        } catch (IllegalStateException e) {
            // The read-back immediately after insert() could not find the row (see
            // ClubPostWriter#insertAndReadBack); the transaction has already been rolled back
            // and the file cleaned up, so this is a genuine, if unexpected, server-side failure.
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to publish post");
        }
    }

    // Visibility decision delegated to ClubVisibilityPolicy, shared with
    // ClubPostCommentController#list so the feed and its comments can never silently disagree.
    @GetMapping("/{clubSlugOrId}/posts")
    public ClubPostService.PostFeedPage feed(@PathVariable String clubSlugOrId,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "12") int size,
                                             Authentication authentication) {
        String viewerEmail = authenticatedUserResolver.resolveEmail(authentication);
        Club club = clubService.resolveBySlugOrId(clubSlugOrId, viewerEmail);
        if (club == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found");
        }

        if (!clubVisibilityPolicy.isVisibleTo(club, authentication)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found");
        }

        Long viewerOauthUserId = viewerEmail != null ? oAuthUserMapper.findIdByEmail(viewerEmail) : null;
        boolean viewerCanModerateAnyPost = clubContentModerationPolicy.canModerateAnyContent(club, authentication);
        return clubPostService.findPublicFeed(club.getId(), page, size, viewerOauthUserId, viewerCanModerateAnyPost);
    }

    // ---- Pinning (president or platform owner only) ----

    @PutMapping("/{clubSlugOrId}/posts/{postId}/pin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void pin(@PathVariable String clubSlugOrId,
                     @PathVariable Long postId,
                     Authentication authentication) {
        Club club = requireManageAccess(clubSlugOrId, authentication);
        String viewerEmail = authenticatedUserResolver.requireEmail(authentication);
        Long pinnedByOauthUserId = oAuthUserMapper.findIdByEmail(viewerEmail);
        try {
            clubPostService.pin(club.getId(), postId, pinnedByOauthUserId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (PessimisticLockingFailureException e) {
            // H2's ~2s lock timeout (SQL error 50200) and InnoDB's 50s one both translate here
            // via Spring's SQLErrorCodeSQLExceptionTranslator -- a lock held by a concurrent
            // pin() call must read as "try again", not as a server error.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Could not pin post right now, please try again");
        }
    }

    @DeleteMapping("/{clubSlugOrId}/posts/{postId}/pin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unpin(@PathVariable String clubSlugOrId,
                       @PathVariable Long postId,
                       Authentication authentication) {
        Club club = requireManageAccess(clubSlugOrId, authentication);
        try {
            clubPostService.unpin(club.getId(), postId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    // Moderation decision delegated to ClubContentModerationPolicy, shared with
    // ClubPostCommentController#delete. Hard delete; the photo file and comment cascade are
    // ClubPostService#delete's responsibility, not this controller's.
    @DeleteMapping("/{clubSlugOrId}/posts/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePost(@PathVariable String clubSlugOrId, @PathVariable Long postId,
                            Authentication authentication) {
        String viewerEmail = authenticatedUserResolver.requireEmail(authentication);
        Club club = clubService.resolveBySlugOrId(clubSlugOrId, viewerEmail);
        if (club == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found");
        }

        ClubPost post = clubPostService.findByIdAndClubId(postId, club.getId());
        if (post == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found");
        }

        Long viewerOauthUserId = oAuthUserMapper.findIdByEmail(viewerEmail);
        boolean canModerate = clubContentModerationPolicy.canModerate(
            viewerOauthUserId, post.getAuthorOauthUserId(), club, authentication);
        if (!canModerate) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to delete this post");
        }

        try {
            clubPostService.delete(post);
        } catch (IllegalStateException e) {
            // ClubPostWriter#delete's own guard against a stale read (the row was already gone
            // by the time the DELETE statement ran); the transaction has already rolled back,
            // so this is a genuine, if unexpected, server-side failure -- same translation
            // publish() applies to its own post-insert read-back guard.
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete post");
        }
    }

    // Same matrix as ClubController/ClubImageController's requireManageAccess, delegated to
    // ClubContentModerationPolicy#canModerateAnyContent so this branch can never silently drift
    // from the one #feed uses for viewerCanModerateAnyPost or #deletePost uses for canModerate:
    // the club's own president, or a platform owner. Pinning is an editorial power, not a
    // publishing one, so an ordinary member (even the post's own author) is not enough.
    private Club requireManageAccess(String clubSlugOrId, Authentication authentication) {
        String viewerEmail = authenticatedUserResolver.requireEmail(authentication);
        Club club = clubService.resolveBySlugOrId(clubSlugOrId, viewerEmail);
        if (club == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found");
        }
        boolean canManage = clubContentModerationPolicy.canModerateAnyContent(club, authentication);
        if (!canManage) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Only the club president or a platform owner can pin posts");
        }
        return club;
    }
}
