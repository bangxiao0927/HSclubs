package com.example.demo.club.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.club.mapper.ClubPostMapper;
import com.example.demo.club.model.PublicClubPostComment;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClubPostCommentServiceTest {

    private ClubPostMapper clubPostMapper;
    private ClubPostCommentWriter clubPostCommentWriter;
    private ClubPostCommentService clubPostCommentService;

    @BeforeEach
    void setUp() {
        clubPostMapper = mock(ClubPostMapper.class);
        clubPostCommentWriter = mock(ClubPostCommentWriter.class);
        clubPostCommentService = new ClubPostCommentService(clubPostMapper, clubPostCommentWriter);
    }

    @Test
    void createRejectsANullBody() {
        assertThatThrownBy(() -> clubPostCommentService.create(1L, 10L, 5L, null))
            .isInstanceOf(IllegalArgumentException.class);

        verify(clubPostCommentWriter, never()).lockCountAndInsert(eq(1L), eq(10L), eq(5L), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createRejectsABodyThatIsBlankAfterTrimming() {
        assertThatThrownBy(() -> clubPostCommentService.create(1L, 10L, 5L, "   "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createRejectsABodyOverThreeHundredCharacters() {
        String tooLong = "x".repeat(301);

        assertThatThrownBy(() -> clubPostCommentService.create(1L, 10L, 5L, tooLong))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createAcceptsABodyOfExactlyThreeHundredCharacters() {
        String exactlyMax = "x".repeat(300);
        when(clubPostCommentWriter.lockCountAndInsert(1L, 10L, 5L, exactlyMax))
            .thenReturn(new PublicClubPostComment());

        clubPostCommentService.create(1L, 10L, 5L, exactlyMax);

        verify(clubPostCommentWriter).lockCountAndInsert(1L, 10L, 5L, exactlyMax);
    }

    @Test
    void createTrimsTheBodyBeforeDelegatingToTheWriter() {
        when(clubPostCommentWriter.lockCountAndInsert(1L, 10L, 5L, "Nice!"))
            .thenReturn(new PublicClubPostComment());

        clubPostCommentService.create(1L, 10L, 5L, "  Nice!  ");

        verify(clubPostCommentWriter).lockCountAndInsert(1L, 10L, 5L, "Nice!");
    }

    @Test
    void createReturnsExactlyWhatTheWriterProduces() {
        PublicClubPostComment created = new PublicClubPostComment();
        created.setId(99L);
        when(clubPostCommentWriter.lockCountAndInsert(1L, 10L, 5L, "Nice!")).thenReturn(created);

        PublicClubPostComment result = clubPostCommentService.create(1L, 10L, 5L, "Nice!");

        assertThat(result).isSameAs(created);
    }

    @Test
    void findPublicCommentsDelegatesToTheMapper() {
        PublicClubPostComment comment = new PublicClubPostComment();
        when(clubPostMapper.findPublicCommentsByPostId(10L)).thenReturn(List.of(comment));

        List<PublicClubPostComment> comments = clubPostCommentService.findPublicComments(10L);

        assertThat(comments).containsExactly(comment);
    }
}
