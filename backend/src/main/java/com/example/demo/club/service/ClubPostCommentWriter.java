package com.example.demo.club.service;

import com.example.demo.club.mapper.ClubPostMapper;
import com.example.demo.club.model.ClubPostComment;
import com.example.demo.club.model.PublicClubPostComment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The concurrency-safe half of posting a comment (see #79): lock the parent post row, count its
 * existing comments, and insert the new one, all inside one real transaction. Split into its own
 * Spring bean for the same reason as {@link ClubPostWriter}: a same-class {@code @Transactional}
 * method called via {@code this} bypasses Spring's proxy and runs with no transaction at all --
 * which here would mean the {@code FOR UPDATE} lock is released the instant it is taken (mybatis-
 * spring gives every mapper call outside an active Spring transaction its own {@code SqlSession},
 * whose connection returns to the pool as soon as the statement completes), leaving the count
 * that follows completely unguarded and the whole cap meaningless.
 * <p>
 * Order matters: the lock must be acquired before the count, and the count must happen before
 * the insert, all without releasing the lock in between -- otherwise two concurrent callers can
 * each observe 49 existing comments and both insert, producing 51 (see the issue's own
 * description of the race under REPEATABLE READ).
 */
@Service
class ClubPostCommentWriter {

    // The issue's own bound: the 51st comment on a post must be rejected.
    private static final int MAX_COMMENTS_PER_POST = 50;

    private final ClubPostMapper clubPostMapper;

    ClubPostCommentWriter(ClubPostMapper clubPostMapper) {
        this.clubPostMapper = clubPostMapper;
    }

    @Transactional
    PublicClubPostComment lockCountAndInsert(Long clubId, Long postId, Long authorOauthUserId, String body) {
        // A FOR UPDATE matching zero rows takes no lock; null here means the post does not
        // exist (or belongs to a different club), and must not fall through to an unguarded
        // count-then-insert.
        Long lockedPostId = clubPostMapper.lockPostIdForUpdate(postId, clubId);
        if (lockedPostId == null) {
            throw new ClubPostNotFoundException(postId);
        }

        int existingCommentCount = clubPostMapper.countCommentsByPostId(postId);
        if (existingCommentCount >= MAX_COMMENTS_PER_POST) {
            throw new CommentLimitExceededException(postId);
        }

        ClubPostComment comment = new ClubPostComment();
        comment.setPostId(postId);
        comment.setAuthorOauthUserId(authorOauthUserId);
        comment.setBody(body);
        clubPostMapper.insertComment(comment);

        // Mirrors ClubPostWriter#insertAndReadBack: the just-created comment's own author is
        // always the viewer here, so viewerCanDelete reads back true unconditionally.
        PublicClubPostComment created = clubPostMapper.findPublicCommentByIdAndPostId(
            comment.getId(), postId, authorOauthUserId, false);
        if (created == null) {
            throw new IllegalStateException(
                "Comment " + comment.getId() + " was not found immediately after being inserted");
        }
        return created;
    }
}
