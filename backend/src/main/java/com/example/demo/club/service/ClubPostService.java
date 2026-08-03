package com.example.demo.club.service;

import com.example.demo.club.mapper.ClubPostMapper;
import com.example.demo.club.model.ClubPost;
import com.example.demo.club.model.PublicClubPost;
import com.example.demo.common.PaginationClamps;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Publishing a post and reading a club's public feed. Publishing is a single atomic operation:
 * the photo is written by {@link ImageStorageService} first, and if the subsequent row insert
 * fails for any reason, that file is deleted again so a failed publish never leaves an orphaned
 * file on disk with no corresponding row.
 */
@Service
public class ClubPostService {

    private static final int MAX_TITLE_LENGTH = 140;

    private final ClubPostMapper clubPostMapper;
    private final ImageStorageService imageStorageService;

    public ClubPostService(ClubPostMapper clubPostMapper, ImageStorageService imageStorageService) {
        this.clubPostMapper = clubPostMapper;
        this.imageStorageService = imageStorageService;
    }

    public ClubPost publish(Long clubId, Long authorOauthUserId, String title, MultipartFile file)
            throws IOException {
        String trimmedTitle = validateTitle(title);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A photo is required");
        }

        String imageUrl = imageStorageService.store(file);
        try {
            ClubPost post = new ClubPost();
            post.setClubId(clubId);
            post.setAuthorOauthUserId(authorOauthUserId);
            post.setTitle(trimmedTitle);
            post.setImageUrl(imageUrl);
            clubPostMapper.insert(post);
            return post;
        } catch (RuntimeException e) {
            // The single-atomic-request contract: a file the row insert never ends up
            // referencing must not be left behind as an orphan.
            imageStorageService.delete(imageUrl);
            throw e;
        }
    }

    public PostFeedPage findPublicFeed(Long clubId, int page, int size) {
        int limit = PaginationClamps.clampPageSize(size);
        int offset = PaginationClamps.clampOffset(page, limit);
        int clampedPage = PaginationClamps.clampPage(page);

        List<PublicClubPost> items = clubPostMapper.findPublicFeedByClubId(clubId, offset, limit);
        int total = clubPostMapper.countFeedByClubId(clubId);

        return new PostFeedPage(items, clampedPage, limit, total);
    }

    private static String validateTitle(String title) {
        if (!StringUtils.hasText(title)) {
            throw new IllegalArgumentException("Title is required");
        }
        String trimmed = title.trim();
        if (trimmed.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("Title must be " + MAX_TITLE_LENGTH + " characters or fewer");
        }
        return trimmed;
    }

    /**
     * The public feed envelope: {@code page} and {@code size} echo the values actually served
     * (post-clamp), never the raw request parameters, so a client computing
     * {@code ceil(total / size)} cannot be misled into thinking there is only one page.
     */
    public record PostFeedPage(List<PublicClubPost> items, int page, int size, int total) {
    }
}
