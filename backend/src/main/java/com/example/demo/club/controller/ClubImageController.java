package com.example.demo.club.controller;

import com.example.demo.club.model.Club;
import com.example.demo.club.service.ClubService;
import com.example.demo.club.service.ImageStorageService;
import com.example.demo.security.AuthenticatedUserResolver;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/clubs")
public class ClubImageController {

    private final ClubService clubService;
    private final ImageStorageService imageStorageService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    public ClubImageController(ClubService clubService,
                                ImageStorageService imageStorageService,
                                AuthenticatedUserResolver authenticatedUserResolver) {
        this.clubService = clubService;
        this.imageStorageService = imageStorageService;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    @PostMapping("/{clubSlugOrId}/image")
    public Map<String, String> uploadImage(@PathVariable String clubSlugOrId,
                                           @RequestParam("file") MultipartFile file,
                                           Authentication authentication) {
        Club club = requireManageAccess(clubSlugOrId, authentication);

        String imageUrl;
        try {
            imageUrl = imageStorageService.store(file);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store file");
        }

        String oldImageUrl = club.getImageUrl();
        club.setImageUrl(imageUrl);
        clubService.update(club.getId(), club);
        imageStorageService.delete(oldImageUrl);

        return Map.of("imageUrl", imageUrl);
    }

    private Club resolveClub(String clubSlugOrId, String viewerEmail) {
        try {
            Long numericId = Long.valueOf(clubSlugOrId);
            Club club = clubService.findById(numericId, viewerEmail);
            if (club == null) throw new IllegalArgumentException("Club not found");
            return club;
        } catch (NumberFormatException e) {
            Club club = clubService.findBySlug(clubSlugOrId, viewerEmail);
            if (club == null) throw new IllegalArgumentException("Club not found");
            return club;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }

    private Club requireManageAccess(String clubSlugOrId, Authentication authentication) {
        String viewerEmail = authenticatedUserResolver.requireEmail(authentication);
        Club club = resolveClub(clubSlugOrId, viewerEmail);
        boolean canManage = Boolean.TRUE.equals(club.getCanManage())
            || authenticatedUserResolver.isPlatformOwner(authentication);
        if (!canManage) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to update this club image");
        }
        return club;
    }
}
