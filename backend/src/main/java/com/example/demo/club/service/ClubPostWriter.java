package com.example.demo.club.service;

import com.example.demo.club.mapper.ClubPostMapper;
import com.example.demo.club.model.ClubPost;
import com.example.demo.club.model.PublicClubPost;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The database half of {@link ClubPostService#publish}: insert the row and immediately read
 * it back, in one real transaction. Split into its own Spring bean so that transaction is a
 * genuine proxied call from {@link ClubPostService} -- annotating a same-class private method
 * with {@code @Transactional} and calling it via {@code this} would bypass Spring's proxy and
 * silently run without a transaction. Deliberately does not touch {@link ImageStorageService}:
 * the photo is validated and written by the caller before this runs, so this method never holds
 * a JDBC connection open during image decode/re-encode/write.
 * <p>
 * Also owns {@link ClubPostService#delete}'s database half: delete the row, then register the
 * photo's removal to run only {@link TransactionSynchronization#afterCommit() after the
 * transaction commits}. Registering it eagerly (or calling {@link ImageStorageService#delete}
 * directly) would destroy a photo that still has a row on a rolled-back delete; registering it
 * here, inside the same proxied transactional method, is what ties the two together correctly.
 */
@Service
class ClubPostWriter {

    private final ClubPostMapper clubPostMapper;
    private final ImageStorageService imageStorageService;

    ClubPostWriter(ClubPostMapper clubPostMapper, ImageStorageService imageStorageService) {
        this.clubPostMapper = clubPostMapper;
        this.imageStorageService = imageStorageService;
    }

    // @Transactional so the insert does not auto-commit before the read-back has succeeded:
    // MyBatis auto-commits each statement on its own connection otherwise, which would leave a
    // successfully inserted row committed even if the read-back that follows it throws.
    @Transactional
    PublicClubPost insertAndReadBack(Long clubId, Long authorOauthUserId, String title, String imageUrl) {
        ClubPost post = new ClubPost();
        post.setClubId(clubId);
        post.setAuthorOauthUserId(authorOauthUserId);
        post.setTitle(title);
        post.setImageUrl(imageUrl);
        clubPostMapper.insert(post);

        PublicClubPost created = clubPostMapper.findPublicPostByIdAndClubId(post.getId(), clubId);
        if (created == null) {
            throw new IllegalStateException(
                "Post " + post.getId() + " was not found immediately after being inserted");
        }
        return created;
    }

    @Transactional
    void delete(ClubPost post) {
        int deletedRows = clubPostMapper.delete(post.getId());
        if (deletedRows == 0) {
            throw new IllegalStateException("Post " + post.getId() + " was not found when deleting");
        }

        String imageUrl = post.getImageUrl();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                imageStorageService.delete(imageUrl);
            }
        });
    }
}
