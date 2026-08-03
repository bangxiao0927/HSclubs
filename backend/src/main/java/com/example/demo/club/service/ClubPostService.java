package com.example.demo.club.service;

import com.example.demo.club.mapper.ClubMapper;
import com.example.demo.club.mapper.ClubPostMapper;
import com.example.demo.club.model.ClubPost;
import com.example.demo.club.model.PublicClubPost;
import com.example.demo.common.PaginationClamps;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Publishing a post and reading a club's public feed. Publishing is a single atomic operation:
 * the photo is validated and written by {@link ImageStorageService} first, entirely outside any
 * DB transaction (image decode/re-encode is CPU-bound work that must not hold a JDBC connection
 * open), then {@link ClubPostWriter} inserts the row and immediately reads it back in one real
 * DB transaction so the response can carry the same safe shape a feed item has (author display
 * name/avatar, {@code createdAt}, never the internal {@code author_oauth_user_id}). If that
 * insert-and-read-back fails for any reason, its transaction rolls back (no committed, unreadable
 * row left behind) and the file is deleted here (no orphan left on disk either).
 */
@Service
public class ClubPostService {

    private static final int MAX_TITLE_LENGTH = 140;
    private static final int MAX_PINNED_POSTS_PER_CLUB = 3;

    private final ClubPostMapper clubPostMapper;
    private final ClubMapper clubMapper;
    private final ImageStorageService imageStorageService;
    private final ClubPostWriter clubPostWriter;

    public ClubPostService(ClubPostMapper clubPostMapper, ClubMapper clubMapper,
                            ImageStorageService imageStorageService, ClubPostWriter clubPostWriter) {
        this.clubPostMapper = clubPostMapper;
        this.clubMapper = clubMapper;
        this.imageStorageService = imageStorageService;
        this.clubPostWriter = clubPostWriter;
    }

    public PublicClubPost publish(Long clubId, Long authorOauthUserId, String title, MultipartFile file)
            throws IOException {
        String trimmedTitle = validateTitle(title);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A photo is required");
        }

        String imageUrl = imageStorageService.store(file);
        try {
            return clubPostWriter.insertAndReadBack(clubId, authorOauthUserId, trimmedTitle, imageUrl);
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

    // The cap is not safe as a plain count-then-update: at REPEATABLE READ, SELECT COUNT(*) is
    // a non-locking consistent read, so two presidents pinning at once could each see 2 and
    // both commit a 3rd pin. Locking the parent club row first serializes every concurrent call
    // for that club, so the count that follows is always accurate. Lock, count and update all
    // live in this one @Transactional method -- called only from the controller, so the Spring
    // proxy is always in play (see ClubPostWriter's Javadoc for why self-invocation would
    // silently drop this).
    @Transactional
    public void pin(Long clubId, Long postId, Long pinnedByOauthUserId) {
        Long lockedClubId = clubMapper.lockClubIdForUpdate(clubId);
        if (lockedClubId == null) {
            throw new IllegalArgumentException("Club not found");
        }
        ClubPost post = clubPostMapper.findByIdAndClubId(postId, clubId);
        if (post == null) {
            throw new IllegalArgumentException("Post not found for this club");
        }
        boolean alreadyPinned = post.getPinnedAt() != null;
        int pinnedCount = clubPostMapper.countPinnedByClubId(clubId);
        if (!alreadyPinned && pinnedCount >= MAX_PINNED_POSTS_PER_CLUB) {
            throw new IllegalStateException(
                "At most " + MAX_PINNED_POSTS_PER_CLUB + " posts can be pinned. Unpin one first.");
        }
        clubPostMapper.pin(postId, pinnedByOauthUserId);
    }

    public void unpin(Long clubId, Long postId) {
        ClubPost post = clubPostMapper.findByIdAndClubId(postId, clubId);
        if (post == null) {
            throw new IllegalArgumentException("Post not found for this club");
        }
        clubPostMapper.unpin(postId);
    }

    /** Raw (author-identifying) read-back for the delete endpoint's own authorization check. */
    public ClubPost findByIdAndClubId(Long postId, Long clubId) {
        return clubPostMapper.findByIdAndClubId(postId, clubId);
    }

    public void delete(ClubPost post) {
        clubPostWriter.delete(post);
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
