package com.example.demo.club.controller;

import com.example.demo.club.model.Club;
import com.example.demo.club.service.ClubService;
import com.example.demo.school.model.School;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/schools/{schoolSlug}/clubs")
public class ClubImageController {

    private final ClubService clubService;
    private final Path uploadDir;

    public ClubImageController(ClubService clubService,
                               @Value("${app.upload.dir:uploads}") String uploadDirPath) {
        this.clubService = clubService;
        this.uploadDir = Paths.get(uploadDirPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create upload directory: " + this.uploadDir, e);
        }
    }

    @PostMapping("/{clubSlugOrId}/image")
    public Map<String, String> uploadImage(@PathVariable String schoolSlug,
                                           @PathVariable String clubSlugOrId,
                                           @RequestParam("file") MultipartFile file) {
        School school = clubService.resolveSchool(schoolSlug);
        Club club = resolveClub(school.getId(), clubSlugOrId);

        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
        }

        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf('.'));
        }
        String filename = UUID.randomUUID() + ext;

        try {
            Path target = uploadDir.resolve(filename);
            file.transferTo(target);

            String imageUrl = "/uploads/" + filename;
            club.setImageUrl(imageUrl);
            clubService.updateInSchool(school.getId(), club.getId(), club);

            return Map.of("imageUrl", imageUrl);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store file");
        }
    }

    private Club resolveClub(Long schoolId, String clubSlugOrId) {
        try {
            Long numericId = Long.valueOf(clubSlugOrId);
            Club club = clubService.findBySchoolAndClubId(schoolId, numericId, null);
            if (club == null) throw new IllegalArgumentException("Club not found");
            return club;
        } catch (NumberFormatException e) {
            Club club = clubService.findBySchoolAndClubSlug(schoolId, clubSlugOrId, null);
            if (club == null) throw new IllegalArgumentException("Club not found");
            return club;
        }
    }
}
