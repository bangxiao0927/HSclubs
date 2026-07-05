package com.example.demo.club.controller;

import com.example.demo.auth.config.SecurityProperties;
import com.example.demo.club.model.Club;
import com.example.demo.club.service.ClubService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/clubs")
public class ClubImageController {

    private static final long MAX_IMAGE_BYTES = 5 * 1024 * 1024;

    private final ClubService clubService;
    private final SecurityProperties securityProperties;
    private final Path uploadDir;

    public ClubImageController(ClubService clubService,
                                SecurityProperties securityProperties,
                                @Value("${app.upload.dir:uploads}") String uploadDirPath) {
        this.clubService = clubService;
        this.securityProperties = securityProperties;
        this.uploadDir = Paths.get(uploadDirPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create upload directory: " + this.uploadDir, e);
        }
    }

    @PostMapping("/{clubSlugOrId}/image")
    public Map<String, String> uploadImage(@PathVariable String clubSlugOrId,
                                           @RequestParam("file") MultipartFile file,
                                           Authentication authentication) {
        Club club = requireManageAccess(clubSlugOrId, authentication);

        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image must be 5MB or smaller");
        }

        String filename = UUID.randomUUID() + resolveImageExtension(file.getContentType());

        try {
            Path target = uploadDir.resolve(filename).normalize();
            if (!target.startsWith(uploadDir)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file name");
            }
            file.transferTo(target);

            String imageUrl = "/uploads/" + filename;
            club.setImageUrl(imageUrl);
            clubService.update(club.getId(), club);

            return Map.of("imageUrl", imageUrl);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store file");
        }
    }

    private String resolveImageExtension(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image content type is required");
        }
        return switch (contentType.toLowerCase()) {
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported image type");
        };
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
        String viewerEmail = resolveViewerEmail(authentication);
        if (viewerEmail == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        Club club = resolveClub(clubSlugOrId, viewerEmail);
        boolean canManage = Boolean.TRUE.equals(club.getCanManage())
            || isPlatformOwner(authentication);
        if (!canManage) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to update this club image");
        }
        return club;
    }

    private String resolveViewerEmail(Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken token)) {
            return null;
        }
        OAuth2User principal = token.getPrincipal();
        if (principal == null) {
            return null;
        }
        Object email = principal.getAttributes().get("email");
        return (email instanceof String str && !str.isBlank()) ? str : null;
    }

    private boolean isPlatformOwner(Authentication authentication) {
        String email = resolveViewerEmail(authentication);
        if (email == null || securityProperties.getOwnerEmails() == null) {
            return false;
        }
        return securityProperties.getOwnerEmails().stream()
            .filter(item -> item != null && !item.isBlank())
            .anyMatch(item -> email.equalsIgnoreCase(item.trim()));
    }
}
