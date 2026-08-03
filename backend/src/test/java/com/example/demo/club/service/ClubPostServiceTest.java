package com.example.demo.club.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.club.mapper.ClubPostMapper;
import com.example.demo.club.model.PublicClubPost;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class ClubPostServiceTest {

    private ClubPostMapper clubPostMapper;
    private ImageStorageService imageStorageService;
    private ClubPostWriter clubPostWriter;
    private ClubPostService clubPostService;

    @BeforeEach
    void setUp() {
        clubPostMapper = mock(ClubPostMapper.class);
        imageStorageService = mock(ImageStorageService.class);
        clubPostWriter = mock(ClubPostWriter.class);
        clubPostService = new ClubPostService(clubPostMapper, imageStorageService, clubPostWriter);
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
        when(clubPostWriter.insertAndReadBack(1L, 10L, exactlyMax, "/uploads/club-posts/uuid.jpg"))
            .thenReturn(readBack);

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
        when(clubPostWriter.insertAndReadBack(1L, 10L, "Meeting recap", "/uploads/club-posts/uuid.jpg"))
            .thenReturn(readBack);

        PublicClubPost post = clubPostService.publish(1L, 10L, "  Meeting recap  ", aFile());

        assertThat(post.getTitle()).isEqualTo("Meeting recap");
        assertThat(post.getImageUrl()).isEqualTo("/uploads/club-posts/uuid.jpg");
        verify(clubPostWriter).insertAndReadBack(1L, 10L, "Meeting recap", "/uploads/club-posts/uuid.jpg");
    }

    // The atomic-request contract: if the DB write fails after the file was already written,
    // the file must not become an orphan (nor should it be silently kept if the post never
    // actually got created).
    @Test
    void publishDeletesTheStoredFileWhenTheDatabaseWriteFails() throws IOException {
        when(imageStorageService.store(any())).thenReturn("/uploads/club-posts/uuid.jpg");
        RuntimeException writeFailure = new RuntimeException("constraint violation");
        when(clubPostWriter.insertAndReadBack(any(), any(), any(), any())).thenThrow(writeFailure);

        assertThatThrownBy(() -> clubPostService.publish(1L, 10L, "Meeting recap", aFile()))
            .isSameAs(writeFailure);

        verify(imageStorageService).delete("/uploads/club-posts/uuid.jpg");
    }

    // ClubPostWriter is the last line of defense against an unreadable just-inserted row (see
    // ClubPostWriterTest); this proves publish() reacts to that failure the same way it reacts
    // to any other database failure -- by cleaning up the file it already wrote.
    @Test
    void publishDeletesTheStoredFileWhenClubPostWriterReportsAnUnreadableRow() throws IOException {
        when(imageStorageService.store(any())).thenReturn("/uploads/club-posts/uuid.jpg");
        when(clubPostWriter.insertAndReadBack(any(), any(), any(), any()))
            .thenThrow(new IllegalStateException("Post 99 was not found immediately after being inserted"));

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
        when(clubPostWriter.insertAndReadBack(any(), any(), any(), any())).thenReturn(readBack);

        PublicClubPost post = clubPostService.publish(1L, 10L, "Meeting recap", aFile());

        assertThat(post).isSameAs(readBack);
    }

    @Test
    void publishNeverWritesToTheDatabaseWhenImageStorageServiceRejectsTheFile() throws IOException {
        when(imageStorageService.store(any())).thenThrow(new IllegalArgumentException("Unsupported image type"));

        assertThatThrownBy(() -> clubPostService.publish(1L, 10L, "Meeting recap", aFile()))
            .isInstanceOf(IllegalArgumentException.class);

        verify(clubPostWriter, never()).insertAndReadBack(any(), any(), any(), any());
    }

    // The transaction-boundary regression this refactor exists for: ClubPostWriter must never
    // be invoked until ImageStorageService.store() has already completed, since store() is the
    // CPU-bound image decode/re-encode/write that must not run inside ClubPostWriter's DB
    // transaction (see ClubPostWriter's Javadoc).
    @Test
    void publishStoresTheImageBeforeWritingToTheDatabase() throws IOException {
        when(imageStorageService.store(any())).thenReturn("/uploads/club-posts/uuid.jpg");
        when(clubPostWriter.insertAndReadBack(any(), any(), any(), any())).thenReturn(new PublicClubPost());

        clubPostService.publish(1L, 10L, "Meeting recap", aFile());

        InOrder inOrder = inOrder(imageStorageService, clubPostWriter);
        inOrder.verify(imageStorageService).store(any());
        inOrder.verify(clubPostWriter).insertAndReadBack(any(), any(), any(), any());
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
