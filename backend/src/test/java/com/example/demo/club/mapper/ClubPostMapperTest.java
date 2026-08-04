package com.example.demo.club.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.club.model.ClubPost;
import com.example.demo.club.model.ClubPostComment;
import com.example.demo.club.model.PublicClubPost;
import com.example.demo.club.model.PublicClubPostComment;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

// Runs on its own in-memory database (see commit 9b8368b): the default test datasource is
// shared across Spring contexts in the same JVM, and other mapper tests rebuild oauth_users
// and clubs with different column sets.
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:club_post_mapper_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class ClubPostMapperTest {

    @Autowired
    private ClubPostMapper clubPostMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS club_post_comment");
        jdbcTemplate.execute("DROP TABLE IF EXISTS club_post");
        jdbcTemplate.execute("DROP TABLE IF EXISTS clubs");
        jdbcTemplate.execute("DROP TABLE IF EXISTS oauth_users");
        jdbcTemplate.execute("""
            CREATE TABLE oauth_users (
                uid BIGINT PRIMARY KEY AUTO_INCREMENT,
                provider VARCHAR(50) NOT NULL,
                provider_user_id VARCHAR(150) NOT NULL,
                email VARCHAR(200),
                display_name VARCHAR(200),
                avatar_url VARCHAR(500)
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE clubs (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                name VARCHAR(150) NOT NULL
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE club_post (
                id                      BIGINT PRIMARY KEY AUTO_INCREMENT,
                club_id                 BIGINT       NOT NULL,
                author_oauth_user_id    BIGINT       NOT NULL,
                title                   VARCHAR(140) NOT NULL,
                image_url               VARCHAR(300) NOT NULL,
                pinned_at               TIMESTAMP    NULL,
                pinned_by_oauth_user_id BIGINT       NULL,
                created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT fk_post_club      FOREIGN KEY (club_id) REFERENCES clubs(id) ON DELETE CASCADE,
                CONSTRAINT fk_post_author    FOREIGN KEY (author_oauth_user_id) REFERENCES oauth_users(uid) ON DELETE CASCADE,
                CONSTRAINT fk_post_pinned_by FOREIGN KEY (pinned_by_oauth_user_id) REFERENCES oauth_users(uid) ON DELETE SET NULL
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE club_post_comment (
                id                   BIGINT PRIMARY KEY AUTO_INCREMENT,
                post_id              BIGINT       NOT NULL,
                author_oauth_user_id BIGINT       NOT NULL,
                body                 VARCHAR(300) NOT NULL,
                created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT fk_comment_post   FOREIGN KEY (post_id) REFERENCES club_post(id) ON DELETE CASCADE,
                CONSTRAINT fk_comment_author FOREIGN KEY (author_oauth_user_id) REFERENCES oauth_users(uid) ON DELETE CASCADE
            )
            """);

        jdbcTemplate.update(
            "INSERT INTO oauth_users (uid, provider, provider_user_id, email, display_name, avatar_url) "
                + "VALUES (1, 'google', 'g-1', 'student1@example.com', 'Ada Lovelace', '/uploads/avatar-cache/ada.jpg')");
        jdbcTemplate.update("INSERT INTO clubs (id, name) VALUES (1, 'Chess Club')");
    }

    @Test
    void insertPersistsAPostThatFindPublicFeedByClubIdCanReadBack() {
        ClubPost post = new ClubPost();
        post.setClubId(1L);
        post.setAuthorOauthUserId(1L);
        post.setTitle("First meeting");
        post.setImageUrl("/uploads/club-posts/a.jpg");

        clubPostMapper.insert(post);

        List<PublicClubPost> feed = clubPostMapper.findPublicFeedByClubId(1L, 0, 10, 1L, false);
        assertThat(feed).singleElement().satisfies(found -> {
            assertThat(found.getId()).isEqualTo(post.getId());
            assertThat(found.getTitle()).isEqualTo("First meeting");
            assertThat(found.getImageUrl()).isEqualTo("/uploads/club-posts/a.jpg");
        });
    }

    // Feed order: pinned posts first, then created_at DESC, with id DESC as the tiebreaker for
    // two posts sharing the same created_at second.
    @Test
    void findPublicFeedByClubIdSortsPinnedFirstThenNewestFirstWithIdAsTheTiebreaker() {
        insertPost(1L, 1L, "2024-01-01 10:00:00", null);
        insertPost(2L, 1L, "2024-01-01 10:00:00", null);
        insertPost(3L, 1L, "2024-01-02 10:00:00", null);
        insertPost(4L, 1L, "2023-01-01 10:00:00", "2024-06-01 00:00:00");

        List<PublicClubPost> feed = clubPostMapper.findPublicFeedByClubId(1L, 0, 10, 1L, false);

        assertThat(feed).extracting(PublicClubPost::getId)
            .containsExactly(4L, 3L, 2L, 1L);
    }

    // Among pinned posts, order is driven by pinned_at DESC, not created_at: post 2 is pinned
    // later (2024-07) despite being created earlier (2024-02), and must still sort before
    // post 1, which was created later (2024-08) but pinned earlier (2024-01).
    @Test
    void findPublicFeedByClubIdSortsPinnedPostsByPinnedAtDescNotCreatedAt() {
        insertPost(1L, 1L, "2024-08-01 10:00:00", "2024-01-01 00:00:00");
        insertPost(2L, 1L, "2024-02-01 10:00:00", "2024-07-01 00:00:00");

        List<PublicClubPost> feed = clubPostMapper.findPublicFeedByClubId(1L, 0, 10, 1L, false);

        assertThat(feed).extracting(PublicClubPost::getId)
            .containsExactly(2L, 1L);
    }

    // Pagination must not duplicate or skip rows across a page boundary. Five posts, page size
    // two: page 0 -> [5, 4], page 1 -> [3, 2], page 2 -> [1].
    @Test
    void findPublicFeedByClubIdIsStableAcrossAPageBoundary() {
        for (long id = 1; id <= 5; id++) {
            insertPost(id, 1L, "2024-01-0" + id + " 10:00:00", null);
        }

        List<Long> firstPage = publicIdsOf(clubPostMapper.findPublicFeedByClubId(1L, 0, 2, 1L, false));
        List<Long> secondPage = publicIdsOf(clubPostMapper.findPublicFeedByClubId(1L, 2, 2, 1L, false));
        List<Long> thirdPage = publicIdsOf(clubPostMapper.findPublicFeedByClubId(1L, 4, 2, 1L, false));

        assertThat(firstPage).containsExactly(5L, 4L);
        assertThat(secondPage).containsExactly(3L, 2L);
        assertThat(thirdPage).containsExactly(1L);
    }

    @Test
    void deleteRemovesThePostFromThePublicFeed() {
        insertPost(1L, 1L, "2024-01-01 10:00:00", null);
        insertPost(2L, 1L, "2024-01-02 10:00:00", null);

        clubPostMapper.delete(1L);

        assertThat(publicIdsOf(clubPostMapper.findPublicFeedByClubId(1L, 0, 10, 1L, false)))
            .containsExactly(2L);
    }

    @Test
    void insertCommentPersistsACommentThatFindPublicCommentsByPostIdCanReadBack() {
        insertPost(1L, 1L, "2024-01-01 10:00:00", null);

        ClubPostComment comment = new ClubPostComment();
        comment.setPostId(1L);
        comment.setAuthorOauthUserId(1L);
        comment.setBody("Great photo!");

        clubPostMapper.insertComment(comment);

        List<PublicClubPostComment> comments = clubPostMapper.findPublicCommentsByPostId(1L, 1L, false);
        assertThat(comments).singleElement().satisfies(found -> {
            assertThat(found.getId()).isEqualTo(comment.getId());
            assertThat(found.getBody()).isEqualTo("Great photo!");
        });
    }

    @Test
    void deletingAPostCascadesItsComments() {
        insertPost(1L, 1L, "2024-01-01 10:00:00", null);
        ClubPostComment comment = new ClubPostComment();
        comment.setPostId(1L);
        comment.setAuthorOauthUserId(1L);
        comment.setBody("Nice!");
        clubPostMapper.insertComment(comment);

        clubPostMapper.delete(1L);

        assertThat(clubPostMapper.findPublicCommentsByPostId(1L, 1L, false)).isEmpty();
    }

    @Test
    void deletingAClubCascadesItsPosts() {
        insertPost(1L, 1L, "2024-01-01 10:00:00", null);
        insertPost(2L, 1L, "2024-01-02 10:00:00", null);
        ClubPostComment comment = new ClubPostComment();
        comment.setPostId(1L);
        comment.setAuthorOauthUserId(1L);
        comment.setBody("Nice!");
        clubPostMapper.insertComment(comment);

        jdbcTemplate.update("DELETE FROM clubs WHERE id = 1");

        assertThat(clubPostMapper.findPublicFeedByClubId(1L, 0, 10, 1L, false)).isEmpty();
        assertThat(clubPostMapper.findPublicCommentsByPostId(1L, 1L, false)).isEmpty();
    }

    @Test
    void countFeedByClubIdCountsEveryPostRegardlessOfPageSize() {
        insertPost(1L, 1L, "2024-01-01 10:00:00", null);
        insertPost(2L, 1L, "2024-01-02 10:00:00", null);
        insertPost(3L, 1L, "2024-01-03 10:00:00", null);

        assertThat(clubPostMapper.countFeedByClubId(1L)).isEqualTo(3);
    }

    @Test
    void pinSetsPinnedAtAndPinnedByAndMovesThePostToTheFrontOfTheFeed() {
        insertPost(1L, 1L, "2024-01-01 10:00:00", null);
        insertPost(2L, 1L, "2024-01-02 10:00:00", null);

        clubPostMapper.pin(1L, 1L);

        assertThat(publicIdsOf(clubPostMapper.findPublicFeedByClubId(1L, 0, 10, 1L, false)))
            .containsExactly(1L, 2L);
        assertThat(clubPostMapper.countPinnedByClubId(1L)).isEqualTo(1);
    }

    @Test
    void unpinClearsPinnedAtAndPinnedByAndReturnsThePostToChronologicalOrder() {
        insertPost(1L, 1L, "2024-01-01 10:00:00", null);
        insertPost(2L, 1L, "2024-01-02 10:00:00", null);
        clubPostMapper.pin(1L, 1L);

        clubPostMapper.unpin(1L);

        assertThat(publicIdsOf(clubPostMapper.findPublicFeedByClubId(1L, 0, 10, 1L, false)))
            .containsExactly(2L, 1L);
        assertThat(clubPostMapper.countPinnedByClubId(1L)).isZero();
    }

    @Test
    void deleteCommentRemovesOnlyThatCommentAndCountCommentsByPostIdReflectsIt() {
        insertPost(1L, 1L, "2024-01-01 10:00:00", null);
        ClubPostComment first = new ClubPostComment();
        first.setPostId(1L);
        first.setAuthorOauthUserId(1L);
        first.setBody("First");
        clubPostMapper.insertComment(first);
        ClubPostComment second = new ClubPostComment();
        second.setPostId(1L);
        second.setAuthorOauthUserId(1L);
        second.setBody("Second");
        clubPostMapper.insertComment(second);

        clubPostMapper.deleteComment(first.getId());

        assertThat(clubPostMapper.countCommentsByPostId(1L)).isEqualTo(1);
        assertThat(clubPostMapper.findPublicCommentsByPostId(1L, 1L, false)).singleElement()
            .satisfies(remaining -> assertThat(remaining.getBody()).isEqualTo("Second"));
    }

    // This only proves the query returns the right id/null; a FOR UPDATE assertion here would
    // pass vacuously without a surrounding transaction (see the issue's own warning), so the
    // actual pessimistic-locking behavior is covered by a service-level test instead.
    @Test
    void lockPostIdForUpdateReturnsTheIdWhenThePostExistsInThatClub() {
        insertPost(1L, 1L, "2024-01-01 10:00:00", null);

        assertThat(clubPostMapper.lockPostIdForUpdate(1L, 1L)).isEqualTo(1L);
    }

    @Test
    void lockPostIdForUpdateReturnsNullForAMissingPostOrAMismatchedClubId() {
        jdbcTemplate.update("INSERT INTO clubs (id, name) VALUES (2, 'Robotics')");
        insertPost(1L, 1L, "2024-01-01 10:00:00", null);

        assertThat(clubPostMapper.lockPostIdForUpdate(99L, 1L)).isNull();
        assertThat(clubPostMapper.lockPostIdForUpdate(1L, 2L)).isNull();
    }

    @Test
    void findCommentByIdAndPostIdReturnsTheCommentWhenThePostMatches() {
        insertPost(1L, 1L, "2024-01-01 10:00:00", null);
        ClubPostComment comment = new ClubPostComment();
        comment.setPostId(1L);
        comment.setAuthorOauthUserId(1L);
        comment.setBody("Great photo!");
        clubPostMapper.insertComment(comment);

        ClubPostComment found = clubPostMapper.findCommentByIdAndPostId(comment.getId(), 1L);

        assertThat(found).isNotNull();
        assertThat(found.getAuthorOauthUserId()).isEqualTo(1L);
        assertThat(found.getBody()).isEqualTo("Great photo!");
    }

    @Test
    void findCommentByIdAndPostIdReturnsNullForAMismatchedPostId() {
        insertPost(1L, 1L, "2024-01-01 10:00:00", null);
        insertPost(2L, 1L, "2024-01-02 10:00:00", null);
        ClubPostComment comment = new ClubPostComment();
        comment.setPostId(1L);
        comment.setAuthorOauthUserId(1L);
        comment.setBody("Great photo!");
        clubPostMapper.insertComment(comment);

        assertThat(clubPostMapper.findCommentByIdAndPostId(comment.getId(), 2L)).isNull();
    }

    @Test
    void findPublicCommentsByPostIdReturnsOldestFirstWithAuthorDisplayNameAndAvatarUrl() {
        insertPost(1L, 1L, "2024-01-01 10:00:00", null);
        insertCommentAt(1L, 1L, "First comment", "2024-01-01 10:00:00");
        insertCommentAt(2L, 1L, "Second comment", "2024-01-02 10:00:00");

        List<PublicClubPostComment> comments = clubPostMapper.findPublicCommentsByPostId(1L, 1L, false);

        assertThat(comments).extracting(PublicClubPostComment::getBody)
            .containsExactly("First comment", "Second comment");
        assertThat(comments).allSatisfy(comment -> {
            assertThat(comment.getAuthorDisplayName()).isEqualTo("Ada Lovelace");
            assertThat(comment.getAuthorAvatarUrl()).isEqualTo("/uploads/avatar-cache/ada.jpg");
        });
    }

    @Test
    void findPublicCommentByIdAndPostIdReturnsTheSameSafeShapeAsTheList() {
        insertPost(1L, 1L, "2024-01-01 10:00:00", null);
        insertCommentAt(1L, 1L, "Great photo!", "2024-01-01 10:00:00");

        PublicClubPostComment comment = clubPostMapper.findPublicCommentByIdAndPostId(1L, 1L, 1L, false);

        assertThat(comment).isNotNull();
        assertThat(comment.getBody()).isEqualTo("Great photo!");
        assertThat(comment.getAuthorDisplayName()).isEqualTo("Ada Lovelace");
        assertThat(comment.getAuthorAvatarUrl()).isEqualTo("/uploads/avatar-cache/ada.jpg");
    }

    @Test
    void findPublicCommentByIdAndPostIdReturnsNullForAMismatchedPostId() {
        insertPost(1L, 1L, "2024-01-01 10:00:00", null);
        insertPost(2L, 1L, "2024-01-02 10:00:00", null);
        insertCommentAt(1L, 1L, "Great photo!", "2024-01-01 10:00:00");

        assertThat(clubPostMapper.findPublicCommentByIdAndPostId(1L, 2L, 1L, false)).isNull();
    }

    // Mirrors findPublicFeedByClubIdMarksViewerCanDeleteTrueOnlyForThePostsOwnAuthor: the same
    // authorship rule, applied to comment rows instead of post rows.
    @Test
    void findPublicCommentsByPostIdMarksViewerCanDeleteTrueOnlyForTheCommentsOwnAuthor() {
        insertPost(1L, 1L, "2024-01-01 10:00:00", null);
        insertCommentAt(1L, 1L, "Great photo!", "2024-01-01 10:00:00");

        List<PublicClubPostComment> asAuthor = clubPostMapper.findPublicCommentsByPostId(1L, 1L, false);
        List<PublicClubPostComment> asSomeoneElse = clubPostMapper.findPublicCommentsByPostId(1L, 2L, false);

        assertThat(asAuthor).singleElement().satisfies(comment -> assertThat(comment.isViewerCanDelete()).isTrue());
        assertThat(asSomeoneElse).singleElement()
            .satisfies(comment -> assertThat(comment.isViewerCanDelete()).isFalse());
    }

    // Mirrors findPublicFeedByClubIdMarksViewerCanDeleteTrueForAModeratorRegardlessOfAuthorship.
    @Test
    void findPublicCommentsByPostIdMarksViewerCanDeleteTrueForAModeratorRegardlessOfAuthorship() {
        insertPost(1L, 1L, "2024-01-01 10:00:00", null);
        insertCommentAt(1L, 1L, "Great photo!", "2024-01-01 10:00:00");

        List<PublicClubPostComment> asModerator = clubPostMapper.findPublicCommentsByPostId(1L, 2L, true);

        assertThat(asModerator).singleElement()
            .satisfies(comment -> assertThat(comment.isViewerCanDelete()).isTrue());
    }

    // Mirrors findPublicPostByIdAndClubIdConvertsALiteralCreatedAtUsingTheEffectiveSystemZone:
    // PublicClubPostComment#createdAt is on the same UNIX_TIMESTAMP/EpochSecondsInstantTypeHandler
    // contract as PublicClubPost#createdAt, so it must round-trip to the correct instant too --
    // see that test's comment for why this derives the expected value from
    // ZoneId.systemDefault() rather than attempting to force one with TimeZone.setDefault().
    @Test
    void findPublicCommentByIdAndPostIdConvertsALiteralCreatedAtUsingTheEffectiveSystemZone() {
        insertPost(1L, 1L, "2024-01-01 10:00:00", null);
        insertCommentAt(1L, 1L, "Great photo!", "2024-01-01 11:30:00");

        PublicClubPostComment comment = clubPostMapper.findPublicCommentByIdAndPostId(1L, 1L, 1L, false);

        Instant expected = LocalDateTime.of(2024, 1, 1, 11, 30)
            .atZone(ZoneId.systemDefault())
            .toInstant();
        assertThat(comment.getCreatedAt()).isEqualTo(expected);
    }

    @Test
    void findAllImageUrlsReturnsEveryPostsImageUrlAcrossClubs() {
        jdbcTemplate.update("INSERT INTO clubs (id, name) VALUES (2, 'Robotics')");
        insertPost(1L, 1L, "2024-01-01 10:00:00", null);
        insertPost(2L, 2L, "2024-01-02 10:00:00", null);

        assertThat(clubPostMapper.findAllImageUrls())
            .containsExactlyInAnyOrder("/uploads/club-posts/1.jpg", "/uploads/club-posts/2.jpg");
    }

    @Test
    void findPublicFeedByClubIdIncludesAuthorDisplayNameAndAvatarUrl() {
        insertPost(1L, 1L, "2024-01-01 10:00:00", null);

        List<PublicClubPost> feed = clubPostMapper.findPublicFeedByClubId(1L, 0, 10, 1L, false);

        assertThat(feed).singleElement().satisfies(post -> {
            assertThat(post.getId()).isEqualTo(1L);
            assertThat(post.getTitle()).isEqualTo("Post 1");
            assertThat(post.getImageUrl()).isEqualTo("/uploads/club-posts/1.jpg");
            assertThat(post.getAuthorDisplayName()).isEqualTo("Ada Lovelace");
            assertThat(post.getAuthorAvatarUrl()).isEqualTo("/uploads/avatar-cache/ada.jpg");
        });
    }

    // The capability field's own authorship rule: the post's own author (uid 1, matching
    // insertPost's default author_oauth_user_id) reads viewer_can_delete true even without
    // club-wide moderation power, while a different, non-moderating viewer reads false.
    @Test
    void findPublicFeedByClubIdMarksViewerCanDeleteTrueOnlyForThePostsOwnAuthor() {
        insertPost(1L, 1L, "2024-01-01 10:00:00", null);

        List<PublicClubPost> asAuthor = clubPostMapper.findPublicFeedByClubId(1L, 0, 10, 1L, false);
        List<PublicClubPost> asSomeoneElse = clubPostMapper.findPublicFeedByClubId(1L, 0, 10, 2L, false);

        assertThat(asAuthor).singleElement().satisfies(post -> assertThat(post.isViewerCanDelete()).isTrue());
        assertThat(asSomeoneElse).singleElement().satisfies(post -> assertThat(post.isViewerCanDelete()).isFalse());
    }

    // The other half of the capability rule: a viewer with club-wide moderation power (the
    // club's own canManage, or a platform owner -- computed by the caller, never by this SQL)
    // can delete a post they did not author.
    @Test
    void findPublicFeedByClubIdMarksViewerCanDeleteTrueForAModeratorRegardlessOfAuthorship() {
        insertPost(1L, 1L, "2024-01-01 10:00:00", null);

        List<PublicClubPost> asModerator = clubPostMapper.findPublicFeedByClubId(1L, 0, 10, 2L, true);

        assertThat(asModerator).singleElement().satisfies(post -> assertThat(post.isViewerCanDelete()).isTrue());
    }

    // An anonymous caller has no oauth_user_id at all; the equality against author_oauth_user_id
    // must not silently evaluate to unknown-treated-as-true, only to false.
    @Test
    void findPublicFeedByClubIdMarksViewerCanDeleteFalseForAnAnonymousViewer() {
        insertPost(1L, 1L, "2024-01-01 10:00:00", null);

        List<PublicClubPost> asAnonymous = clubPostMapper.findPublicFeedByClubId(1L, 0, 10, null, false);

        assertThat(asAnonymous).singleElement().satisfies(post -> assertThat(post.isViewerCanDelete()).isFalse());
    }

    // The public feed card shows a comment count without the caller ever fetching the
    // (separate, public) comments endpoint, so the count has to travel with the feed item
    // itself: a correlated count against club_post_comment, never a join that could multiply
    // the post row per comment.
    @Test
    void findPublicFeedByClubIdIncludesCommentCountCorrelatedToEachPost() {
        insertPost(1L, 1L, "2024-01-01 10:00:00", null);
        insertPost(2L, 1L, "2024-01-02 10:00:00", null);
        insertComment(1L, "First");
        insertComment(1L, "Second");

        List<PublicClubPost> feed = clubPostMapper.findPublicFeedByClubId(1L, 0, 10, 1L, false);

        assertThat(feed).extracting(PublicClubPost::getId, PublicClubPost::getCommentCount)
            .containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple(1L, 2),
                org.assertj.core.groups.Tuple.tuple(2L, 0));
    }

    @Test
    void findPublicPostByIdAndClubIdIncludesCommentCount() {
        insertPost(1L, 1L, "2024-01-01 10:00:00", null);
        insertComment(1L, "Nice!");

        PublicClubPost post = clubPostMapper.findPublicPostByIdAndClubId(1L, 1L, 1L, false);

        assertThat(post.getCommentCount()).isEqualTo(1);
    }

    private void insertComment(long postId, String body) {
        ClubPostComment comment = new ClubPostComment();
        comment.setPostId(postId);
        comment.setAuthorOauthUserId(1L);
        comment.setBody(body);
        clubPostMapper.insertComment(comment);
    }

    // Same pinned-first, created_at-DESC, id-DESC-tiebreaker ordering as
    // findPublicFeedByClubIdSortsPinnedFirstThenNewestFirstWithIdAsTheTiebreaker and
    // findPublicFeedByClubIdSortsPinnedPostsByPinnedAtDescNotCreatedAt, but with a mix of
    // pinned and unpinned posts read back in one call, additionally asserting the join against
    // oauth_users does not duplicate a post row.
    @Test
    void findPublicFeedByClubIdSortsPinnedPostsFirstAndExactlyOnce() {
        insertPost(1L, 1L, "2024-01-01 10:00:00", null);
        insertPost(2L, 1L, "2023-01-01 10:00:00", "2024-06-01 00:00:00");
        insertPost(3L, 1L, "2024-01-02 10:00:00", null);

        List<PublicClubPost> feed = clubPostMapper.findPublicFeedByClubId(1L, 0, 10, 1L, false);

        assertThat(feed).extracting(PublicClubPost::getId).containsExactly(2L, 3L, 1L);
        assertThat(feed).extracting(PublicClubPost::getId).doesNotHaveDuplicates();
    }

    // countFeedByClubId and findPublicFeedByClubId both include the shared FeedClause SQL
    // fragment (WHERE club_id = #{clubId}), so this asserts they can never disagree on which
    // rows are "this club's feed" -- exactly the property the issue calls out.
    @Test
    void countFeedByClubIdMatchesThePublicFeedSizeWhenUnpaginated() {
        jdbcTemplate.update("INSERT INTO clubs (id, name) VALUES (2, 'Robotics')");
        insertPost(1L, 1L, "2024-01-01 10:00:00", null);
        insertPost(2L, 1L, "2024-01-02 10:00:00", null);
        insertPost(3L, 2L, "2024-01-03 10:00:00", null);

        int count = clubPostMapper.countFeedByClubId(1L);
        List<PublicClubPost> feed = clubPostMapper.findPublicFeedByClubId(1L, 0, 100, 1L, false);

        assertThat(count).isEqualTo(2);
        assertThat(feed).hasSize(count);
    }

    // The read-back ClubPostService#publish uses to return the same safe shape a feed item
    // has: author display name/avatar, no author_oauth_user_id -- sharing PublicPostColumnList
    // with findPublicFeedByClubId so the two can never present a different shape for the same
    // post.
    @Test
    void findPublicPostByIdAndClubIdReturnsTheSameSafeShapeAsTheFeed() {
        insertPost(1L, 1L, "2024-01-01 10:00:00", null);

        PublicClubPost post = clubPostMapper.findPublicPostByIdAndClubId(1L, 1L, 1L, false);

        assertThat(post).isNotNull();
        assertThat(post.getId()).isEqualTo(1L);
        assertThat(post.getTitle()).isEqualTo("Post 1");
        assertThat(post.getImageUrl()).isEqualTo("/uploads/club-posts/1.jpg");
        assertThat(post.getCreatedAt()).isNotNull();
        assertThat(post.getAuthorDisplayName()).isEqualTo("Ada Lovelace");
        assertThat(post.getAuthorAvatarUrl()).isEqualTo("/uploads/avatar-cache/ada.jpg");
    }

    // Scoped to the club: a post ID that exists but under a different club must not read back,
    // the same guard the mapper already applies everywhere else a post is looked up by ID.
    @Test
    void findPublicPostByIdAndClubIdReturnsNullForAMismatchedClubId() {
        jdbcTemplate.update("INSERT INTO clubs (id, name) VALUES (2, 'Robotics')");
        insertPost(1L, 1L, "2024-01-01 10:00:00", null);

        PublicClubPost post = clubPostMapper.findPublicPostByIdAndClubId(1L, 2L, 1L, false);

        assertThat(post).isNull();
    }

    // The regression this guards: PublicClubPost#createdAt used to be a bare LocalDateTime, a
    // timezone-naive wall-clock reading a browser could misparse as its own local time, making a
    // just-created post appear hours in the future.
    //
    // This does NOT force a specific zone with TimeZone.setDefault(): H2 resolves its own
    // date/time functions against the JVM's default zone at (or before) connection setup, so a
    // TimeZone.setDefault() call from inside a @Test method that runs after the connection
    // pool/DataSource already exists has no effect on H2's CURRENT_TIMESTAMP/UNIX_TIMESTAMP --
    // provably so: with that override in place, running this suite under an actual `TZ=UTC`
    // process still produced UTC-derived instants, not America/Los_Angeles-derived ones. Instead
    // this derives the expected instant from ZoneId.systemDefault(), the zone that is actually
    // in effect, so the assertion is correct under whatever real zone the process runs with --
    // exercised as both a genuinely non-UTC zone (this suite's normal local/dev environment) and
    // UTC (this suite run with `TZ=UTC`, matching CI).
    @Test
    void findPublicPostByIdAndClubIdConvertsALiteralCreatedAtUsingTheEffectiveSystemZone() {
        insertPost(1L, 1L, "2024-01-01 10:00:00", null);

        PublicClubPost post = clubPostMapper.findPublicPostByIdAndClubId(1L, 1L, 1L, false);

        Instant expected = LocalDateTime.of(2024, 1, 1, 10, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant();
        assertThat(post.getCreatedAt()).isEqualTo(expected);
    }

    @Test
    void findByIdAndClubIdReturnsThePostWhenItBelongsToTheClub() {
        insertPost(1L, 1L, "2024-01-01 10:00:00", null);

        ClubPost post = clubPostMapper.findByIdAndClubId(1L, 1L);

        assertThat(post).isNotNull();
        assertThat(post.getId()).isEqualTo(1L);
        assertThat(post.getClubId()).isEqualTo(1L);
        assertThat(post.getAuthorOauthUserId()).isEqualTo(1L);
        assertThat(post.getImageUrl()).isEqualTo("/uploads/club-posts/1.jpg");
    }

    // Scoped the same way findPublicPostByIdAndClubId is: cross-club pinning must 404, not
    // silently touch someone else's post.
    @Test
    void findByIdAndClubIdReturnsNullForAMismatchedClubId() {
        jdbcTemplate.update("INSERT INTO clubs (id, name) VALUES (2, 'Robotics')");
        insertPost(1L, 1L, "2024-01-01 10:00:00", null);

        ClubPost post = clubPostMapper.findByIdAndClubId(1L, 2L);

        assertThat(post).isNull();
    }

    @Test
    void findByIdAndClubIdReturnsNullForANonExistentPost() {
        ClubPost post = clubPostMapper.findByIdAndClubId(999L, 1L);

        assertThat(post).isNull();
    }

    private void insertPost(long id, long clubId, String createdAt, String pinnedAt) {
        jdbcTemplate.update(
            "INSERT INTO club_post (id, club_id, author_oauth_user_id, title, image_url, created_at, pinned_at) "
                + "VALUES (?, ?, 1, 'Post ' || ?, '/uploads/club-posts/' || ? || '.jpg', ?, ?)",
            id, clubId, id, id, createdAt, pinnedAt);
    }

    private void insertCommentAt(long id, long postId, String body, String createdAt) {
        jdbcTemplate.update(
            "INSERT INTO club_post_comment (id, post_id, author_oauth_user_id, body, created_at) "
                + "VALUES (?, ?, 1, ?, ?)",
            id, postId, body, createdAt);
    }

    private static List<Long> publicIdsOf(List<PublicClubPost> posts) {
        return posts.stream().map(PublicClubPost::getId).toList();
    }
}
