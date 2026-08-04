package com.example.demo.club.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.club.mapper.ClubPostMapper;
import com.example.demo.club.model.ClubPost;
import com.example.demo.club.model.PublicClubPost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class ClubPostWriterTest {

    private ClubPostMapper clubPostMapper;
    private ImageStorageService imageStorageService;
    private ClubPostWriter clubPostWriter;

    @BeforeEach
    void setUp() {
        clubPostMapper = mock(ClubPostMapper.class);
        imageStorageService = mock(ImageStorageService.class);
        clubPostWriter = new ClubPostWriter(clubPostMapper, imageStorageService);
        // Mirrors MyBatis's useGeneratedKeys behavior: insert() sets the generated id onto the
        // same ClubPost instance it was given, which the read-back then looks up by.
        doAnswer(invocation -> {
            ClubPost post = invocation.getArgument(0);
            post.setId(99L);
            return 1;
        }).when(clubPostMapper).insert(any());
    }

    @Test
    void insertAndReadBackInsertsExactlyWhatItWasGiven() {
        PublicClubPost readBack = new PublicClubPost();
        readBack.setTitle("Meeting recap");
        when(clubPostMapper.findPublicPostByIdAndClubId(99L, 1L, 10L, false)).thenReturn(readBack);

        clubPostWriter.insertAndReadBack(1L, 10L, "Meeting recap", "/uploads/club-posts/uuid.jpg");

        ArgumentCaptor<ClubPost> insertedPost = ArgumentCaptor.forClass(ClubPost.class);
        verify(clubPostMapper).insert(insertedPost.capture());
        assertThat(insertedPost.getValue().getClubId()).isEqualTo(1L);
        assertThat(insertedPost.getValue().getAuthorOauthUserId()).isEqualTo(10L);
        assertThat(insertedPost.getValue().getTitle()).isEqualTo("Meeting recap");
        assertThat(insertedPost.getValue().getImageUrl()).isEqualTo("/uploads/club-posts/uuid.jpg");
    }

    @Test
    void insertAndReadBackReturnsExactlyWhatTheReadBackProduces() {
        PublicClubPost readBack = new PublicClubPost();
        readBack.setId(99L);
        readBack.setAuthorDisplayName("Ada Lovelace");
        when(clubPostMapper.findPublicPostByIdAndClubId(99L, 1L, 10L, false)).thenReturn(readBack);

        PublicClubPost result =
            clubPostWriter.insertAndReadBack(1L, 10L, "Meeting recap", "/uploads/club-posts/uuid.jpg");

        assertThat(result).isSameAs(readBack);
    }

    // The last line of defense: this should not happen in practice, since both the insert and
    // the read-back run in the same transaction, but if the read-back cannot find the row it
    // just inserted, the whole operation must fail rather than return null or a half-populated
    // object.
    @Test
    void insertAndReadBackThrowsWhenTheReadBackFindsNoRow() {
        when(clubPostMapper.findPublicPostByIdAndClubId(99L, 1L, 10L, false)).thenReturn(null);

        assertThatThrownBy(() ->
            clubPostWriter.insertAndReadBack(1L, 10L, "Meeting recap", "/uploads/club-posts/uuid.jpg"))
            .isInstanceOf(IllegalStateException.class);
    }

    // TransactionSynchronizationManager only tracks synchronizations while a transaction is
    // actually active; running the delete inside one here (rather than mocking Spring's
    // transaction manager) is what proves delete() really does register the file removal
    // against the surrounding transaction, not just call ImageStorageService directly.
    private static void runInATestTransaction(Runnable action) {
        TransactionSynchronizationManager.initSynchronization();
        try {
            action.run();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deleteRemovesTheRowThroughTheMapper() {
        when(clubPostMapper.delete(99L)).thenReturn(1);
        ClubPost post = new ClubPost();
        post.setId(99L);
        post.setImageUrl("/uploads/club-posts/uuid.jpg");

        runInATestTransaction(() -> clubPostWriter.delete(post));

        verify(clubPostMapper).delete(99L);
    }

    @Test
    void deleteDoesNotRemoveTheFileBeforeTheSynchronizationsAreTriggered() {
        when(clubPostMapper.delete(99L)).thenReturn(1);
        ClubPost post = new ClubPost();
        post.setId(99L);
        post.setImageUrl("/uploads/club-posts/uuid.jpg");

        runInATestTransaction(() -> clubPostWriter.delete(post));

        verify(imageStorageService, never()).delete(any());
    }

    // The transaction-boundary contract this exists for: a rolled-back delete must leave the
    // file on disk, so the removal can only run from an afterCommit callback, never eagerly.
    @Test
    void deleteRemovesTheFileOnlyAfterTheTransactionCommits() {
        when(clubPostMapper.delete(99L)).thenReturn(1);
        ClubPost post = new ClubPost();
        post.setId(99L);
        post.setImageUrl("/uploads/club-posts/uuid.jpg");

        runInATestTransaction(() -> {
            clubPostWriter.delete(post);
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
        });

        verify(imageStorageService).delete("/uploads/club-posts/uuid.jpg");
    }

    @Test
    void deleteThrowsWhenTheRowWasAlreadyGone() {
        when(clubPostMapper.delete(99L)).thenReturn(0);
        ClubPost post = new ClubPost();
        post.setId(99L);
        post.setImageUrl("/uploads/club-posts/uuid.jpg");

        assertThatThrownBy(() -> runInATestTransaction(() -> clubPostWriter.delete(post)))
            .isInstanceOf(IllegalStateException.class);
    }
}
