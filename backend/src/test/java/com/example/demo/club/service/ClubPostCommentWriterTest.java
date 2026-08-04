package com.example.demo.club.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.club.mapper.ClubPostMapper;
import com.example.demo.club.model.ClubPostComment;
import com.example.demo.club.model.PublicClubPostComment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class ClubPostCommentWriterTest {

    private ClubPostMapper clubPostMapper;
    private ClubPostCommentWriter clubPostCommentWriter;

    @BeforeEach
    void setUp() {
        clubPostMapper = mock(ClubPostMapper.class);
        clubPostCommentWriter = new ClubPostCommentWriter(clubPostMapper);
        // Mirrors MyBatis's useGeneratedKeys behavior: insertComment() sets the generated id
        // onto the same ClubPostComment instance it was given, which the read-back then looks
        // up by.
        doAnswer(invocation -> {
            ClubPostComment comment = invocation.getArgument(0);
            comment.setId(99L);
            return 1;
        }).when(clubPostMapper).insertComment(any());
    }

    // The issue's own "must not fall through to an unguarded insert" requirement: a missing
    // post (or one belonging to a different club) means lockPostIdForUpdate finds no row and
    // took no lock, so the count and insert must never run.
    @Test
    void lockCountAndInsertThrowsClubPostNotFoundWhenTheLockFindsNoRowAndNeverInserts() {
        when(clubPostMapper.lockPostIdForUpdate(1L, 10L)).thenReturn(null);

        assertThatThrownBy(() -> clubPostCommentWriter.lockCountAndInsert(10L, 1L, 5L, "Nice!"))
            .isInstanceOf(ClubPostNotFoundException.class);

        verify(clubPostMapper, never()).insertComment(any());
    }

    // The concurrency-cap requirement: the 51st comment must be rejected, never inserted.
    @Test
    void lockCountAndInsertThrowsCommentLimitExceededWhenTheCountIsAlreadyFiftyAndNeverInserts() {
        when(clubPostMapper.lockPostIdForUpdate(1L, 10L)).thenReturn(1L);
        when(clubPostMapper.countCommentsByPostId(1L)).thenReturn(50);

        assertThatThrownBy(() -> clubPostCommentWriter.lockCountAndInsert(10L, 1L, 5L, "Nice!"))
            .isInstanceOf(CommentLimitExceededException.class);

        verify(clubPostMapper, never()).insertComment(any());
    }

    @Test
    void lockCountAndInsertInsertsWhenTheCountIsBelowFifty() {
        when(clubPostMapper.lockPostIdForUpdate(1L, 10L)).thenReturn(1L);
        when(clubPostMapper.countCommentsByPostId(1L)).thenReturn(49);
        PublicClubPostComment readBack = new PublicClubPostComment();
        readBack.setBody("Nice!");
        when(clubPostMapper.findPublicCommentByIdAndPostId(99L, 1L, 5L, false)).thenReturn(readBack);

        clubPostCommentWriter.lockCountAndInsert(10L, 1L, 5L, "Nice!");

        ArgumentCaptor<ClubPostComment> insertedComment = ArgumentCaptor.forClass(ClubPostComment.class);
        verify(clubPostMapper).insertComment(insertedComment.capture());
        assertThat(insertedComment.getValue().getPostId()).isEqualTo(1L);
        assertThat(insertedComment.getValue().getAuthorOauthUserId()).isEqualTo(5L);
        assertThat(insertedComment.getValue().getBody()).isEqualTo("Nice!");
    }

    @Test
    void lockCountAndInsertReturnsExactlyWhatTheReadBackProduces() {
        when(clubPostMapper.lockPostIdForUpdate(1L, 10L)).thenReturn(1L);
        when(clubPostMapper.countCommentsByPostId(1L)).thenReturn(0);
        PublicClubPostComment readBack = new PublicClubPostComment();
        readBack.setId(99L);
        readBack.setAuthorDisplayName("Ada Lovelace");
        when(clubPostMapper.findPublicCommentByIdAndPostId(99L, 1L, 5L, false)).thenReturn(readBack);

        PublicClubPostComment result = clubPostCommentWriter.lockCountAndInsert(10L, 1L, 5L, "Nice!");

        assertThat(result).isSameAs(readBack);
    }

    @Test
    void lockCountAndInsertThrowsWhenTheReadBackFindsNoRow() {
        when(clubPostMapper.lockPostIdForUpdate(1L, 10L)).thenReturn(1L);
        when(clubPostMapper.countCommentsByPostId(1L)).thenReturn(0);
        when(clubPostMapper.findPublicCommentByIdAndPostId(99L, 1L, 5L, false)).thenReturn(null);

        assertThatThrownBy(() -> clubPostCommentWriter.lockCountAndInsert(10L, 1L, 5L, "Nice!"))
            .isInstanceOf(IllegalStateException.class);
    }

    // The exact ordering the issue's concurrency section demands: lock, then count, then insert,
    // all as calls the mock can observe happening in that order.
    @Test
    void lockCountAndInsertLocksBeforeCountingBeforeInserting() {
        when(clubPostMapper.lockPostIdForUpdate(1L, 10L)).thenReturn(1L);
        when(clubPostMapper.countCommentsByPostId(1L)).thenReturn(0);
        when(clubPostMapper.findPublicCommentByIdAndPostId(any(), any(), any(), anyBoolean()))
            .thenReturn(new PublicClubPostComment());

        clubPostCommentWriter.lockCountAndInsert(10L, 1L, 5L, "Nice!");

        InOrder order = inOrder(clubPostMapper);
        order.verify(clubPostMapper).lockPostIdForUpdate(1L, 10L);
        order.verify(clubPostMapper).countCommentsByPostId(1L);
        order.verify(clubPostMapper).insertComment(any());
    }
}
