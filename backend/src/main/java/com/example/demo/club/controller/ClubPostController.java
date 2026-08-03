package com.example.demo.club.controller;

import com.example.demo.auth.mapper.OAuthUserMapper;
import com.example.demo.club.model.Club;
import com.example.demo.club.model.PublicClubPost;
import com.example.demo.club.service.ClubPostService;
import com.example.demo.club.service.ClubService;
import com.example.demo.security.AuthenticatedUserResolver;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    public ClubPostController(ClubService clubService,
                              ClubPostService clubPostService,
                              OAuthUserMapper oAuthUserMapper,
                              AuthenticatedUserResolver authenticatedUserResolver) {
        this.clubService = clubService;
        this.clubPostService = clubPostService;
        this.oAuthUserMapper = oAuthUserMapper;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    @PostMapping("/{clubSlugOrId}/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public PublicClubPost publish(@PathVariable String clubSlugOrId,
                                   @RequestParam("title") String title,
                                   @RequestParam("file") MultipartFile file,
                                   Authentication authentication) {
        String viewerEmail = authenticatedUserResolver.requireEmail(authentication);
        Club club = resolveClub(clubSlugOrId, viewerEmail);
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
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store file");
        } catch (IllegalStateException e) {
            // The read-back immediately after insert() could not find the row (see
            // ClubPostService#publish); the transaction has already been rolled back and the
            // file cleaned up, so this is a genuine, if unexpected, server-side failure.
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to publish post");
        }
    }

    // Gated on clubs.status = 'active', not clubs.visibility (that column has no semantics yet
    // -- see #78/#83). A non-active club's feed 404s to anyone who is not a member, the club
    // president, or a platform owner, so an unapproved club cannot publish to a public page.
    @GetMapping("/{clubSlugOrId}/posts")
    public ClubPostService.PostFeedPage feed(@PathVariable String clubSlugOrId,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "12") int size,
                                             Authentication authentication) {
        String viewerEmail = authenticatedUserResolver.resolveEmail(authentication);
        Club club = resolveClub(clubSlugOrId, viewerEmail);
        if (club == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found");
        }

        boolean isActive = "active".equalsIgnoreCase(club.getStatus());
        boolean canViewNonActiveClub = Boolean.TRUE.equals(club.getViewerIsMember())
            || Boolean.TRUE.equals(club.getCanManage())
            || authenticatedUserResolver.isPlatformOwner(authentication);
        if (!isActive && !canViewNonActiveClub) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found");
        }

        return clubPostService.findPublicFeed(club.getId(), page, size);
    }

    private Club resolveClub(String clubSlugOrId, String viewerEmail) {
        try {
            Long numericId = Long.valueOf(clubSlugOrId);
            return clubService.findById(numericId, viewerEmail);
        } catch (NumberFormatException e) {
            return clubService.findBySlug(clubSlugOrId, viewerEmail);
        }
    }
}
