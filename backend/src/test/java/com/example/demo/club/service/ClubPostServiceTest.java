package com.example.demo.club.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.club.mapper.ClubPostMapper;
import com.example.demo.club.model.ClubPost;
import com.example.demo.club.model.PublicClubPost;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class ClubPostServiceTest {

    private ClubPostMapper clubPostMapper;
    private ImageStorageService imageStorageService;
    private ClubPostService clubPostService;

    @BeforeEach
    void setUp() {
        clubPostMapper = mock(ClubPostMapper.class);
        imageStorageService = mock(ImageStorageService.class);
        clubPostService = new ClubPostService(clubPostMapper, imageStorageService);
        // Mirrors MyBatis's useGeneratedKeys behavior: insert() sets the generated id onto the
        // same ClubPost instance it was given, which publish()'s read-back then looks up by.
        doAnswer(invocation -> {
            ClubPost post = invocation.getArgument(0);
            post.setId(99L);
            return 1;
        }).when(clubPostMapper).insert(any());
    }

    private static MultipartFile aFile() {
        return new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[] {1, 2, 3});
    }

    @Test
    void publishRejectsANullTitle() {
        assertThatThrownBy(() -> clubPostService.publish(1L, 10L, null, aFile()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publishRejectsATitleThatIsBlankAfterTrimming() {
        assertThatThrownBy(() -> clubPostService.publish(1L, 10L, "   ", aFile()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publishRejectsATitleOverOneHundredFortyCharacters() {
        String tooLong = "x".repeat(141);

        assertThatThrownBy(() -> clubPostService.publish(1L, 10L, tooLong, aFile()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publishAcceptsATitleOfExactlyOneHundredFortyCharacters() throws IOException {
        String exactlyMax = "x".repeat(140);
        when(imageStorageService.store(any())).thenReturn("/uploads/club-posts/uuid.jpg");
        PublicClubPost readBack = new PublicClubPost();
        readBack.setTitle(exactlyMax);
        when(clubPostMapper.findPublicPostByIdAndClubId(99L, 1L)).thenReturn(readBack);

        PublicClubPost post = clubPostService.publish(1L, 10L, exactlyMax, aFile());

        assertThat(post.getTitle()).isEqualTo(exactlyMax);
    }

    @Test
    void publishRejectsAMissingFile() {
        assertThatThrownBy(() -> clubPostService.publish(1L, 10L, "Meeting recap", null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publishRejectsAnEmptyFile() {
        MultipartFile empty = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> clubPostService.publish(1L, 10L, "Meeting recap", empty))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publishTrimsTheTitleAndDelegatesTheFileEntirelyToImageStorageService() throws IOException {
        when(imageStorageService.store(any())).thenReturn("/uploads/club-posts/uuid.jpg");
        PublicClubPost readBack = new PublicClubPost();
        readBack.setTitle("Meeting recap");
        readBack.setImageUrl("/uploads/club-posts/uuid.jpg");
        when(clubPostMapper.findPublicPostByIdAndClubId(99L, 1L)).thenReturn(readBack);

        PublicClubPost post = clubPostService.publish(1L, 10L, "  Meeting recap  ", aFile());

        assertThat(post.getTitle()).isEqualTo("Meeting recap");
        assertThat(post.getImageUrl()).isEqualTo("/uploads/club-posts/uuid.jpg");

        ArgumentCaptor<ClubPost> insertedPost = ArgumentCaptor.forClass(ClubPost.class);
        verify(clubPostMapper).insert(insertedPost.capture());
        assertThat(insertedPost.getValue().getClubId()).isEqualTo(1L);
        assertThat(insertedPost.getValue().getAuthorOauthUserId()).isEqualTo(10L);
        assertThat(insertedPost.getValue().getTitle()).isEqualTo("Meeting recap");
    }

    // The atomic-request contract: if the row insert fails after the file was already written,
    // the file must not become an orphan (nor should it be silently kept if the post never
    // actually got created).
    @Test
    void publishDeletesTheStoredFileWhenTheRowInsertFails() throws IOException {
        when(imageStorageService.store(any())).thenReturn("/uploads/club-posts/uuid.jpg");
        RuntimeException insertFailure = new RuntimeException("constraint violation");
        org.mockito.Mockito.doThrow(insertFailure).when(clubPostMapper).insert(any());

        assertThatThrownBy(() -> clubPostService.publish(1L, 10L, "Meeting recap", aFile()))
            .isSameAs(insertFailure);

        verify(imageStorageService).delete("/uploads/club-posts/uuid.jpg");
    }

    // The read-back is what actually returns the safe, feed-shaped post; if it can't find the
    // row it just inserted (should not happen in practice, since both run in the same
    // transaction, but this is the last line of defense), the whole publish must fail rather
    // than return null or a half-populated object, and the file must not be left orphaned.
    @Test
    void publishDeletesTheStoredFileWhenTheReadBackFindsNoRow() throws IOException {
        when(imageStorageService.store(any())).thenReturn("/uploads/club-posts/uuid.jpg");
        when(clubPostMapper.findPublicPostByIdAndClubId(99L, 1L)).thenReturn(null);

        assertThatThrownBy(() -> clubPostService.publish(1L, 10L, "Meeting recap", aFile()))
            .isInstanceOf(IllegalStateException.class);

        verify(imageStorageService).delete("/uploads/club-posts/uuid.jpg");
    }

    @Test
    void publishReturnsExactlyWhatTheReadBackProduces() throws IOException {
        when(imageStorageService.store(any())).thenReturn("/uploads/club-posts/uuid.jpg");
        PublicClubPost readBack = new PublicClubPost();
        readBack.setId(99L);
        readBack.setAuthorDisplayName("Ada Lovelace");
        readBack.setAuthorAvatarUrl("/uploads/avatar-cache/ada.jpg");
        when(clubPostMapper.findPublicPostByIdAndClubId(99L, 1L)).thenReturn(readBack);

        PublicClubPost post = clubPostService.publish(1L, 10L, "Meeting recap", aFile());

        assertThat(post).isSameAs(readBack);
    }

    @Test
    void publishNeverInsertsARowWhenImageStorageServiceRejectsTheFile() throws IOException {
        when(imageStorageService.store(any())).thenThrow(new IllegalArgumentException("Unsupported image type"));

        assertThatThrownBy(() -> clubPostService.publish(1L, 10L, "Meeting recap", aFile()))
            .isInstanceOf(IllegalArgumentException.class);

        verify(clubPostMapper, never()).insert(any());
    }

    @Test
    void findPublicFeedPassesTheClampedOffsetAndLimitToTheMapper() {
        when(clubPostMapper.findPublicFeedByClubId(eq(1L), anyInt(), anyInt())).thenReturn(List.of());
        when(clubPostMapper.countFeedByClubId(1L)).thenReturn(0);

        clubPostService.findPublicFeed(1L, 2, 10);

        verify(clubPostMapper).findPublicFeedByClubId(1L, 20, 10);
    }

    @Test
    void findPublicFeedEchoesTheClampedSizeNotTheRequestedOne() {
        when(clubPostMapper.findPublicFeedByClubId(eq(1L), anyInt(), anyInt())).thenReturn(List.of());
        when(clubPostMapper.countFeedByClubId(1L)).thenReturn(0);

        ClubPostService.PostFeedPage feedPage = clubPostService.findPublicFeed(1L, 0, 1000);

        assertThat(feedPage.size()).isEqualTo(100);
        verify(clubPostMapper).findPublicFeedByClubId(1L, 0, 100);
    }

    @Test
    void findPublicFeedEchoesTheClampedPageForANegativeRequestedPage() {
        when(clubPostMapper.findPublicFeedByClubId(eq(1L), anyInt(), anyInt())).thenReturn(List.of());
        when(clubPostMapper.countFeedByClubId(1L)).thenReturn(0);

        ClubPostService.PostFeedPage feedPage = clubPostService.findPublicFeed(1L, -5, 10);

        assertThat(feedPage.page()).isEqualTo(0);
    }

    @Test
    void findPublicFeedReportsTotalFromTheSharedCountQuery() {
        PublicClubPost item = new PublicClubPost();
        when(clubPostMapper.findPublicFeedByClubId(eq(1L), anyInt(), anyInt())).thenReturn(List.of(item));
        when(clubPostMapper.countFeedByClubId(1L)).thenReturn(57);

        ClubPostService.PostFeedPage feedPage = clubPostService.findPublicFeed(1L, 0, 12);

        assertThat(feedPage.items()).containsExactly(item);
        assertThat(feedPage.total()).isEqualTo(57);
        assertThat(feedPage.page()).isEqualTo(0);
        assertThat(feedPage.size()).isEqualTo(12);
    }
}
