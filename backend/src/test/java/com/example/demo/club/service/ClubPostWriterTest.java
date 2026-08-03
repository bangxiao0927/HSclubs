package com.example.demo.club.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.club.mapper.ClubPostMapper;
import com.example.demo.club.model.ClubPost;
import com.example.demo.club.model.PublicClubPost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ClubPostWriterTest {

    private ClubPostMapper clubPostMapper;
    private ClubPostWriter clubPostWriter;

    @BeforeEach
    void setUp() {
        clubPostMapper = mock(ClubPostMapper.class);
        clubPostWriter = new ClubPostWriter(clubPostMapper);
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
        when(clubPostMapper.findPublicPostByIdAndClubId(99L, 1L)).thenReturn(readBack);

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
        when(clubPostMapper.findPublicPostByIdAndClubId(99L, 1L)).thenReturn(readBack);

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
        when(clubPostMapper.findPublicPostByIdAndClubId(99L, 1L)).thenReturn(null);

        assertThatThrownBy(() ->
            clubPostWriter.insertAndReadBack(1L, 10L, "Meeting recap", "/uploads/club-posts/uuid.jpg"))
            .isInstanceOf(IllegalStateException.class);
    }
}
