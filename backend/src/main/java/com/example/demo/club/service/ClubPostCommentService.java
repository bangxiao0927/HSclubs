package com.example.demo.club.service;

import com.example.demo.club.mapper.ClubPostMapper;
import com.example.demo.club.model.ClubPostComment;
import com.example.demo.club.model.PublicClubPostComment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Posting a comment, reading a post's public comments, and deleting a comment (see #79). Posting
 * validates the body here, then delegates the concurrency-sensitive lock-count-insert to
 * {@link ClubPostCommentWriter}, exactly like {@link ClubPostService#publish} delegates to
 * {@link ClubPostWriter}.
 */
@Service
public class ClubPostCommentService {

    private static final int MAX_BODY_LENGTH = 300;

    private final ClubPostMapper clubPostMapper;
    private final ClubPostCommentWriter clubPostCommentWriter;

    public ClubPostCommentService(ClubPostMapper clubPostMapper, ClubPostCommentWriter clubPostCommentWriter) {
        this.clubPostMapper = clubPostMapper;
        this.clubPostCommentWriter = clubPostCommentWriter;
    }

    public PublicClubPostComment create(Long clubId, Long postId, Long authorOauthUserId, String body) {
        String trimmedBody = validateBody(body);
        return clubPostCommentWriter.lockCountAndInsert(clubId, postId, authorOauthUserId, trimmedBody);
    }

    public List<PublicClubPostComment> findPublicComments(Long postId, Long viewerOauthUserId,
                                                           boolean viewerCanModerateAnyPost) {
        return clubPostMapper.findPublicCommentsByPostId(postId, viewerOauthUserId, viewerCanModerateAnyPost);
    }

    public ClubPostComment findByIdAndPostId(Long commentId, Long postId) {
        return clubPostMapper.findCommentByIdAndPostId(commentId, postId);
    }

    public void delete(Long commentId) {
        clubPostMapper.deleteComment(commentId);
    }

    private static String validateBody(String body) {
        if (!StringUtils.hasText(body)) {
            throw new IllegalArgumentException("Comment body is required");
        }
        String trimmed = body.trim();
        if (trimmed.length() > MAX_BODY_LENGTH) {
            throw new IllegalArgumentException("Comment body must be " + MAX_BODY_LENGTH + " characters or fewer");
        }
        return trimmed;
    }
}
