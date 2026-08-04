package com.example.demo.club.controller;

import com.example.demo.auth.mapper.OAuthUserMapper;
import com.example.demo.club.model.Club;
import com.example.demo.club.model.ClubPost;
import com.example.demo.club.model.ClubPostComment;
import com.example.demo.club.model.PublicClubPostComment;
import com.example.demo.club.service.ClubContentModerationPolicy;
import com.example.demo.club.service.ClubPostCommentService;
import com.example.demo.club.service.ClubPostNotFoundException;
import com.example.demo.club.service.ClubPostService;
import com.example.demo.club.service.ClubService;
import com.example.demo.club.service.ClubVisibilityPolicy;
import com.example.demo.club.service.CommentLimitExceededException;
import com.example.demo.security.AuthenticatedUserResolver;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Discussion on a club post (see #79): public read, member-only write, deletable by the
 * comment's own author, the club president, or a platform owner -- see
 * {@link ClubContentModerationPolicy}, the same moderation decision
 * {@link ClubPostController#deletePost} applies to the post itself.
 */
@RestController
@RequestMapping("/api/clubs")
public class ClubPostCommentController {

    private final ClubService clubService;
    private final ClubPostService clubPostService;
    private final ClubPostCommentService clubPostCommentService;
    private final OAuthUserMapper oAuthUserMapper;
    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final ClubVisibilityPolicy clubVisibilityPolicy;
    private final ClubContentModerationPolicy clubContentModerationPolicy;

    public ClubPostCommentController(ClubService clubService,
                                      ClubPostService clubPostService,
                                      ClubPostCommentService clubPostCommentService,
                                      OAuthUserMapper oAuthUserMapper,
                                      AuthenticatedUserResolver authenticatedUserResolver,
                                      ClubVisibilityPolicy clubVisibilityPolicy,
                                      ClubContentModerationPolicy clubContentModerationPolicy) {
        this.clubService = clubService;
        this.clubPostService = clubPostService;
        this.clubPostCommentService = clubPostCommentService;
        this.oAuthUserMapper = oAuthUserMapper;
        this.authenticatedUserResolver = authenticatedUserResolver;
        this.clubVisibilityPolicy = clubVisibilityPolicy;
        this.clubContentModerationPolicy = clubContentModerationPolicy;
    }

    // Visibility decision delegated to ClubVisibilityPolicy, shared with
    // ClubPostController#feed: comments belong to a post on a club's feed, so they must not be
    // visible to anyone the feed itself hides them from.
    @GetMapping("/{clubSlugOrId}/posts/{postId}/comments")
    public List<PublicClubPostComment> list(@PathVariable String clubSlugOrId, @PathVariable Long postId,
                                             Authentication authentication) {
        String viewerEmail = authenticatedUserResolver.resolveEmail(authentication);
        Club club = clubService.resolveBySlugOrId(clubSlugOrId, viewerEmail);
        if (club == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found");
        }

        if (!clubVisibilityPolicy.isVisibleTo(club, authentication)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found");
        }

        requirePostInClub(postId, club.getId());

        Long viewerOauthUserId = viewerEmail != null ? oAuthUserMapper.findIdByEmail(viewerEmail) : null;
        boolean viewerCanModerateAnyPost = Boolean.TRUE.equals(club.getCanManage())
            || authenticatedUserResolver.isPlatformOwner(authentication);
        return clubPostCommentService.findPublicComments(postId, viewerOauthUserId, viewerCanModerateAnyPost);
    }

    @PostMapping("/{clubSlugOrId}/posts/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public PublicClubPostComment create(@PathVariable String clubSlugOrId, @PathVariable Long postId,
                                         @RequestBody CommentRequest request, Authentication authentication) {
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
            return clubPostCommentService.create(club.getId(), postId, authorOauthUserId, request.body());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (ClubPostNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (CommentLimitExceededException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (CannotAcquireLockException e) {
            // The comment cap's FOR UPDATE lock timed out (H2 error 50200 / MySQL 1205, both
            // translated by Spring into this exception): another request is very likely
            // mid-insert on the same post, so this is a transient conflict, not a server error.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Too many comments are being posted on this post right now; please try again");
        }
    }

    @DeleteMapping("/{clubSlugOrId}/posts/{postId}/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String clubSlugOrId, @PathVariable Long postId, @PathVariable Long commentId,
                        Authentication authentication) {
        String viewerEmail = authenticatedUserResolver.requireEmail(authentication);
        Club club = clubService.resolveBySlugOrId(clubSlugOrId, viewerEmail);
        if (club == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found");
        }
        requirePostInClub(postId, club.getId());

        ClubPostComment comment = clubPostCommentService.findByIdAndPostId(commentId, postId);
        if (comment == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found");
        }

        Long viewerOauthUserId = oAuthUserMapper.findIdByEmail(viewerEmail);
        boolean canModerate = clubContentModerationPolicy.canModerate(
            viewerOauthUserId, comment.getAuthorOauthUserId(), club, authentication);
        if (!canModerate) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to delete this comment");
        }

        clubPostCommentService.delete(commentId);
    }

    private void requirePostInClub(Long postId, Long clubId) {
        ClubPost post = clubPostService.findByIdAndClubId(postId, clubId);
        if (post == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found");
        }
    }

    public record CommentRequest(String body) {
    }
}
