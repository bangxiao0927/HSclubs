package com.example.demo.club.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.club.mapper.ClubMapper;
import com.example.demo.club.mapper.ClubPostMapper;
import com.example.demo.club.model.ClubPost;
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
    private ClubMapper clubMapper;
    private ImageStorageService imageStorageService;
    private ClubPostWriter clubPostWriter;
    private ClubPostService clubPostService;

    @BeforeEach
    void setUp() {
        clubPostMapper = mock(ClubPostMapper.class);
        clubMapper = mock(ClubMapper.class);
        imageStorageService = mock(ImageStorageService.class);
        clubPostWriter = mock(ClubPostWriter.class);
        clubPostService = new ClubPostService(clubPostMapper, clubMapper, imageStorageService, clubPostWriter);
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
        when(clubPostMapper.findPublicFeedByClubId(eq(1L), anyInt(), anyInt(), any(), anyBoolean()))
            .thenReturn(List.of());
        when(clubPostMapper.countFeedByClubId(1L)).thenReturn(0);

        clubPostService.findPublicFeed(1L, 2, 10, 7L, false);

        verify(clubPostMapper).findPublicFeedByClubId(1L, 20, 10, 7L, false);
    }

    @Test
    void findPublicFeedEchoesTheClampedSizeNotTheRequestedOne() {
        when(clubPostMapper.findPublicFeedByClubId(eq(1L), anyInt(), anyInt(), any(), anyBoolean()))
            .thenReturn(List.of());
        when(clubPostMapper.countFeedByClubId(1L)).thenReturn(0);

        ClubPostService.PostFeedPage feedPage = clubPostService.findPublicFeed(1L, 0, 1000, null, false);

        assertThat(feedPage.size()).isEqualTo(100);
        verify(clubPostMapper).findPublicFeedByClubId(1L, 0, 100, null, false);
    }

    @Test
    void findPublicFeedEchoesTheClampedPageForANegativeRequestedPage() {
        when(clubPostMapper.findPublicFeedByClubId(eq(1L), anyInt(), anyInt(), any(), anyBoolean()))
            .thenReturn(List.of());
        when(clubPostMapper.countFeedByClubId(1L)).thenReturn(0);

        ClubPostService.PostFeedPage feedPage = clubPostService.findPublicFeed(1L, -5, 10, null, false);

        assertThat(feedPage.page()).isEqualTo(0);
    }

    @Test
    void findPublicFeedReportsTotalFromTheSharedCountQuery() {
        PublicClubPost item = new PublicClubPost();
        when(clubPostMapper.findPublicFeedByClubId(eq(1L), anyInt(), anyInt(), any(), anyBoolean()))
            .thenReturn(List.of(item));
        when(clubPostMapper.countFeedByClubId(1L)).thenReturn(57);

        ClubPostService.PostFeedPage feedPage = clubPostService.findPublicFeed(1L, 0, 12, null, false);

        assertThat(feedPage.items()).containsExactly(item);
        assertThat(feedPage.total()).isEqualTo(57);
        assertThat(feedPage.page()).isEqualTo(0);
        assertThat(feedPage.size()).isEqualTo(12);
    }

    // The new capability-plumbing contract: whatever the caller passes as the viewer's identity
    // and moderation power must reach the mapper unchanged, since PublicClubPost#viewerCanDelete
    // is computed entirely by the mapper's own SQL (see ClubPostMapper.xml's PublicPostColumnList).
    @Test
    void findPublicFeedPassesTheViewerIdentityAndModerationPowerToTheMapperUnchanged() {
        when(clubPostMapper.findPublicFeedByClubId(eq(1L), anyInt(), anyInt(), any(), anyBoolean()))
            .thenReturn(List.of());
        when(clubPostMapper.countFeedByClubId(1L)).thenReturn(0);

        clubPostService.findPublicFeed(1L, 0, 10, 42L, true);

        verify(clubPostMapper).findPublicFeedByClubId(1L, 0, 10, 42L, true);
    }

    // ---- pin ----

    @Test
    void pinLocksTheClubRowCountsAndUpdatesInsideOnePinCall() {
        when(clubMapper.lockClubIdForUpdate(1L)).thenReturn(1L);
        ClubPost post = new ClubPost();
        post.setId(10L);
        post.setClubId(1L);
        when(clubPostMapper.findByIdAndClubId(10L, 1L)).thenReturn(post);
        when(clubPostMapper.countPinnedByClubId(1L)).thenReturn(0);

        clubPostService.pin(1L, 10L, 42L);

        InOrder inOrder = inOrder(clubMapper, clubPostMapper);
        inOrder.verify(clubMapper).lockClubIdForUpdate(1L);
        inOrder.verify(clubPostMapper).countPinnedByClubId(1L);
        inOrder.verify(clubPostMapper).pin(10L, 42L);
    }

    @Test
    void pinThrowsWhenTheClubDoesNotExist() {
        when(clubMapper.lockClubIdForUpdate(1L)).thenReturn(null);

        assertThatThrownBy(() -> clubPostService.pin(1L, 10L, 42L))
            .isInstanceOf(IllegalArgumentException.class);

        verify(clubPostMapper, never()).pin(any(), any());
    }

    @Test
    void pinThrowsWhenThePostBelongsToAnotherClub() {
        when(clubMapper.lockClubIdForUpdate(1L)).thenReturn(1L);
        when(clubPostMapper.findByIdAndClubId(10L, 1L)).thenReturn(null);

        assertThatThrownBy(() -> clubPostService.pin(1L, 10L, 42L))
            .isInstanceOf(IllegalArgumentException.class);

        verify(clubPostMapper, never()).pin(any(), any());
    }

    // The cap the whole feature exists to enforce: a fourth pin must not silently evict the
    // oldest one, so it is a 409-worthy failure, not a 400.
    @Test
    void pinThrowsWhenTheClubAlreadyHasThreePinnedPosts() {
        when(clubMapper.lockClubIdForUpdate(1L)).thenReturn(1L);
        ClubPost post = new ClubPost();
        post.setId(10L);
        post.setClubId(1L);
        when(clubPostMapper.findByIdAndClubId(10L, 1L)).thenReturn(post);
        when(clubPostMapper.countPinnedByClubId(1L)).thenReturn(3);

        assertThatThrownBy(() -> clubPostService.pin(1L, 10L, 42L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("3");

        verify(clubPostMapper, never()).pin(any(), any());
    }

    // Re-pinning an already-pinned post (e.g. to bump it to the front again) must not be
    // blocked by the cap: it is already one of the (at most) three, not a fourth.
    @Test
    void pinDoesNotCountAnAlreadyPinnedPostAgainstItsOwnCap() {
        when(clubMapper.lockClubIdForUpdate(1L)).thenReturn(1L);
        ClubPost post = new ClubPost();
        post.setId(10L);
        post.setClubId(1L);
        post.setPinnedAt(java.time.LocalDateTime.of(2024, 1, 1, 0, 0));
        when(clubPostMapper.findByIdAndClubId(10L, 1L)).thenReturn(post);
        when(clubPostMapper.countPinnedByClubId(1L)).thenReturn(3);

        clubPostService.pin(1L, 10L, 42L);

        verify(clubPostMapper).pin(10L, 42L);
    }

    // ---- unpin ----

    @Test
    void unpinClearsThePinOnAnExistingPost() {
        ClubPost post = new ClubPost();
        post.setId(10L);
        post.setClubId(1L);
        when(clubPostMapper.findByIdAndClubId(10L, 1L)).thenReturn(post);

        clubPostService.unpin(1L, 10L);

        verify(clubPostMapper).unpin(10L);
    }

    @Test
    void unpinThrowsWhenThePostBelongsToAnotherClub() {
        when(clubPostMapper.findByIdAndClubId(10L, 1L)).thenReturn(null);

        assertThatThrownBy(() -> clubPostService.unpin(1L, 10L))
            .isInstanceOf(IllegalArgumentException.class);

        verify(clubPostMapper, never()).unpin(any());
    }

    @Test
    void findByIdAndClubIdDelegatesToTheMapper() {
        ClubPost post = new ClubPost();
        post.setId(9L);
        when(clubPostMapper.findByIdAndClubId(9L, 1L)).thenReturn(post);

        ClubPost result = clubPostService.findByIdAndClubId(9L, 1L);

        assertThat(result).isSameAs(post);
    }

    @Test
    void deleteDelegatesToTheWriter() {
        ClubPost post = new ClubPost();
        post.setId(9L);

        clubPostService.delete(post);

        verify(clubPostWriter).delete(post);
    }
}
