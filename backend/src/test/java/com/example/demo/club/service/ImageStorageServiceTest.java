package com.example.demo.club.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;

class ImageStorageServiceTest {

    @TempDir
    Path uploadDir;

    private ImageStorageService service(Path dir) {
        return new ImageStorageService(dir.toString(), 0, 5000);
    }

    @Test
    void rejectsAnEmptyFile() {
        ImageStorageService service = service(uploadDir);
        MockMultipartFile file = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> service.store(file)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBytesThatAreNotARecognizedImageFormatRegardlessOfDeclaredContentType() {
        ImageStorageService service = service(uploadDir);
        byte[] pdfBytes = "%PDF-1.4 not actually an image".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "fake.gif", "image/gif", pdfBytes);

        assertThatThrownBy(() -> service.store(file)).isInstanceOf(IllegalArgumentException.class);
    }

    // The issue's own requirement (#82): the error a student sees for an unsupported format
    // (most commonly a raw .heic pulled from the iPhone Files app, since iOS Safari's own file
    // picker usually converts to JPEG for web uploads) must name every accepted format and call
    // out that HEIC specifically is not one of them, not just say "unsupported".
    @Test
    void rejectsAnUnrecognizedFormatWithAMessageNamingTheAcceptedFormatsAndHeic() {
        ImageStorageService service = service(uploadDir);
        byte[] pdfBytes = "%PDF-1.4 not actually an image".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "fake.gif", "image/gif", pdfBytes);

        assertThatThrownBy(() -> service.store(file))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("JPEG")
            .hasMessageContaining("PNG")
            .hasMessageContaining("WebP")
            .hasMessageContaining("GIF")
            .hasMessageContaining("HEIC")
            .hasMessageContaining("not supported");
    }

    // A magic-byte prefix is enough for ImageIO to pick a candidate reader, but the reader then
    // throws IOException trying to actually parse the (truncated) header -- e.g. real-world
    // partial uploads from a dropped connection. That must surface as a readable 400, not an
    // IllegalArgumentException-shaped IOException wrapper that becomes an uncaught 500.
    @Test
    void rejectsATruncatedPngWithAReadableErrorInsteadOfAServerError() throws IOException {
        ImageStorageService service = service(uploadDir);
        byte[] truncatedPng = Arrays.copyOf(solidColorPng(50, 50, Color.RED, false), 20);
        MockMultipartFile file = new MockMultipartFile("file", "truncated.png", "image/png", truncatedPng);

        assertThatThrownBy(() -> service.store(file)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsATruncatedJpegWithAReadableErrorInsteadOfAServerError() throws IOException {
        ImageStorageService service = service(uploadDir);
        byte[] truncatedJpeg = Arrays.copyOf(solidColorJpeg(50, 50, Color.RED), 50);
        MockMultipartFile file = new MockMultipartFile("file", "truncated.jpg", "image/jpeg", truncatedJpeg);

        assertThatThrownBy(() -> service.store(file)).isInstanceOf(IllegalArgumentException.class);
    }

    // A PNG truncated right after its IHDR chunk has a fully readable header (width/height
    // parse fine, so the sniff and megapixel-guard steps both pass) but no IDAT/IEND at all,
    // so the *full* decode performed during re-encoding throws IIOException
    // ("Error reading PNG metadata"). That is still bad client input, not a server failure,
    // and must surface the same way a header-read failure does: IllegalArgumentException / 400.
    @Test
    void rejectsAPngWithReadableHeaderButCorruptPixelDataAsAClientError() throws IOException {
        ImageStorageService service = service(uploadDir);
        byte[] pngWithNoImageData = truncateAfterFirstPngChunk(solidColorPng(50, 50, Color.RED, false));
        MockMultipartFile file = new MockMultipartFile("file", "corrupt.png", "image/png", pngWithNoImageData);

        assertThatThrownBy(() -> service.store(file)).isInstanceOf(IllegalArgumentException.class);
    }

    // The decode-failure fix must not swallow genuine server-side I/O failures into the same
    // 400 bucket: once a file has fully and validly decoded, a failure writing it to disk is a
    // storage problem, not a bad upload, and must remain an IOException (the controller maps
    // that to 500).
    @Test
    void ioFailuresWritingTheStoredFileRemainIOExceptionsNotClientErrors() throws IOException {
        ImageStorageService service = service(uploadDir);
        Path clubPostsDir = uploadDir.resolve("club-posts");
        Files.delete(clubPostsDir);
        Files.write(clubPostsDir, new byte[] {1});

        byte[] jpegBytes = solidColorJpeg(10, 10, Color.RED);
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpegBytes);

        assertThatThrownBy(() -> service.store(file))
            .isInstanceOf(IOException.class)
            .isNotInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] truncateAfterFirstPngChunk(byte[] pngBytes) {
        int chunkDataLength = ((pngBytes[8] & 0xFF) << 24) | ((pngBytes[9] & 0xFF) << 16)
            | ((pngBytes[10] & 0xFF) << 8) | (pngBytes[11] & 0xFF);
        int firstChunkEnd = 8 + 4 + 4 + chunkDataLength + 4; // signature + length + type + data + crc
        return Arrays.copyOf(pngBytes, firstChunkEnd);
    }

    @Test
    void storesAJpegUploadAsAJpegFile() throws IOException {
        ImageStorageService service = service(uploadDir);
        byte[] jpegBytes = solidColorJpeg(20, 20, Color.RED);
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpegBytes);

        String imageUrl = service.store(file);

        assertThat(imageUrl).startsWith("/uploads/club-posts/").endsWith(".jpg");
        byte[] stored = readStoredFile(imageUrl);
        assertThat(isJpeg(stored)).isTrue();
    }

    @Test
    void storesAPngUploadAsARealJpegFile() throws IOException {
        ImageStorageService service = service(uploadDir);
        byte[] pngBytes = solidColorPng(20, 20, Color.BLUE, false);
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", pngBytes);

        String imageUrl = service.store(file);

        assertThat(imageUrl).endsWith(".jpg");
        byte[] stored = readStoredFile(imageUrl);
        assertThat(isJpeg(stored)).isTrue();
        assertThat(isPng(stored)).isFalse();
    }

    @Test
    void storesAWebpUploadAsAJpegFile() throws IOException {
        ImageStorageService service = service(uploadDir);
        byte[] webpBytes = new ClassPathResource("images/tiny.webp").getContentAsByteArray();
        MockMultipartFile file = new MockMultipartFile("file", "photo.webp", "image/webp", webpBytes);

        String imageUrl = service.store(file);

        assertThat(imageUrl).endsWith(".jpg");
        assertThat(isJpeg(readStoredFile(imageUrl))).isTrue();
    }

    // WebP's own megapixel cap is much stricter than the general one: TwelveMonkeys'
    // WebPImageReader's *output* does honour ImageReadParam.setSourceSubsampling (confirmed
    // empirically), but its peak decode memory does not -- VP8Frame allocates its per-
    // macroblock decode state from the source image's own pixel dimensions regardless of the
    // requested subsampling factor, so a large source still OOMs even when heavily
    // subsampled. This is WebP's *only* memory guard, not just defense in depth.
    @Test
    void storesAWebpJustUnderItsOwnStricterMegapixelLimit() throws IOException {
        ImageStorageService service = service(uploadDir);
        byte[] webpBytes = new ClassPathResource("images/near-limit.webp").getContentAsByteArray();
        MockMultipartFile file = new MockMultipartFile("file", "photo.webp", "image/webp", webpBytes);

        String imageUrl = service.store(file);

        assertThat(imageUrl).endsWith(".jpg");
        assertThat(isJpeg(readStoredFile(imageUrl))).isTrue();
    }

    // The reviewer's own finding: WebP's cap is much stricter than JPEG/PNG's (3MP vs 30MP,
    // since it has no subsampling-based defense -- see the class-level comment on
    // MAX_WEBP_PIXELS), so it silently rejects things a viewer would reasonably expect to work
    // (e.g. a 2560x1440 screenshot, 3.7MP). The generic "maximum allowed resolution" message
    // must therefore name this specific, lower limit and point to a workaround, not just repeat
    // the same wording the general 30MP case uses.
    @Test
    void rejectsAWebpOverItsOwnStricterMegapixelLimitEvenThoughItIsUnderTheGeneralLimit() throws IOException {
        ImageStorageService service = service(uploadDir);
        byte[] webpBytes = new ClassPathResource("images/oversized.webp").getContentAsByteArray();
        MockMultipartFile file = new MockMultipartFile("file", "photo.webp", "image/webp", webpBytes);

        assertThatThrownBy(() -> service.store(file))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("WebP")
            .hasMessageContaining("3 megapixels")
            .hasMessageContaining("JPEG")
            .hasMessageContaining("PNG");
    }

    @Test
    void storesAGifUnchangedWithAGifExtension() throws IOException {
        ImageStorageService service = service(uploadDir);
        byte[] gifBytes = solidColorGif(10, 10, Color.GREEN);
        MockMultipartFile file = new MockMultipartFile("file", "photo.gif", "image/gif", gifBytes);

        String imageUrl = service.store(file);

        assertThat(imageUrl).endsWith(".gif");
        assertThat(readStoredFile(imageUrl)).isEqualTo(gifBytes);
    }

    @Test
    void animatedGifStaysAnimatedAfterStorage() throws IOException {
        ImageStorageService service = service(uploadDir);
        byte[] animatedGifBytes = animatedGif(10, 10);
        MockMultipartFile file = new MockMultipartFile("file", "animated.gif", "image/gif", animatedGifBytes);

        String imageUrl = service.store(file);

        assertThat(frameCount(readStoredFile(imageUrl))).isEqualTo(2);
    }

    @Test
    void rejectsAGifLargerThanTwoMegabytes() throws IOException {
        ImageStorageService service = service(uploadDir);
        byte[] oversizedGif = padTo(solidColorGif(10, 10, Color.GREEN), 2 * 1024 * 1024 + 1);
        MockMultipartFile file = new MockMultipartFile("file", "big.gif", "image/gif", oversizedGif);

        assertThatThrownBy(() -> service.store(file)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsANonGifLargerThanFiveMegabytes() throws IOException {
        ImageStorageService service = service(uploadDir);
        byte[] oversizedJpeg = padTo(solidColorJpeg(20, 20, Color.RED), 5 * 1024 * 1024 + 1);
        MockMultipartFile file = new MockMultipartFile("file", "big.jpg", "image/jpeg", oversizedJpeg);

        assertThatThrownBy(() -> service.store(file)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnImageOverTheMaximumAllowedMegapixelsBeforeFullDecode() throws IOException {
        ImageStorageService service = service(uploadDir);
        byte[] hugeHeaderPng = solidColorPng(8000, 7000, Color.BLACK, false);
        MockMultipartFile file = new MockMultipartFile("file", "huge.png", "image/png", hugeHeaderPng);

        assertThatThrownBy(() -> service.store(file)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void neverUpscalesAnImageSmallerThanTheTargetBoundingBox() throws IOException {
        ImageStorageService service = service(uploadDir);
        byte[] smallJpeg = solidColorJpeg(40, 30, Color.RED);
        MockMultipartFile file = new MockMultipartFile("file", "small.jpg", "image/jpeg", smallJpeg);

        String imageUrl = service.store(file);

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(readStoredFile(imageUrl)));
        assertThat(decoded.getWidth()).isEqualTo(40);
        assertThat(decoded.getHeight()).isEqualTo(30);
    }

    @Test
    void flattensTransparentPixelsToWhite() throws IOException {
        ImageStorageService service = service(uploadDir);
        byte[] transparentPng = solidColorPng(10, 10, new Color(255, 0, 0, 0), true);
        MockMultipartFile file = new MockMultipartFile("file", "transparent.png", "image/png", transparentPng);

        String imageUrl = service.store(file);

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(readStoredFile(imageUrl)));
        int rgb = decoded.getRGB(5, 5);
        assertThat(new Color(rgb, false)).isEqualTo(Color.WHITE);
    }

    @Test
    void reencodingStripsExifIncludingGpsData() throws IOException {
        ImageStorageService service = service(uploadDir);
        byte[] jpegWithExif = jpegWithOrientationAndGps(6);
        MockMultipartFile file = new MockMultipartFile("file", "phone-photo.jpg", "image/jpeg", jpegWithExif);

        String imageUrl = service.store(file);

        assertThat(containsApp1ExifMarker(readStoredFile(imageUrl))).isFalse();
    }

    // A 32x32 source image split into four solid-color quadrants (top-left red, top-right
    // green, bottom-left blue, bottom-right yellow) tagged with EXIF Orientation=6 ("Rotate 90
    // CW"). After correction, each quadrant's content moves one corner clockwise: original
    // bottom-left -> displayed top-left, top-left -> top-right, top-right -> bottom-right,
    // bottom-right -> bottom-left. Verified independently against this exact fixture with
    // `exiftool` and a standalone Thumbnailator invocation before being hardcoded here.
    //
    // All 8 Exif Orientation values are parameterized here, not just 6: each of the expected
    // quadrant layouts below was independently verified against this exact fixture with
    // `exiftool`, both at baseline and progressive JPEG encoding, before being hardcoded --
    // regression protection for the mirrored cases (2, 4, 5, 7), which a single orientation=6
    // case leaves entirely unasserted.
    @ParameterizedTest
    @MethodSource("orientationExpectations")
    void everyExifOrientationValueDisplaysTheRightWayUp(
            int orientation, Color expectedTopLeft, Color expectedTopRight,
            Color expectedBottomLeft, Color expectedBottomRight) throws IOException {
        ImageStorageService service = service(uploadDir);
        byte[] jpegWithExif = jpegWithOrientationAndGps(orientation);
        MockMultipartFile file = new MockMultipartFile("file", "phone-photo.jpg", "image/jpeg", jpegWithExif);

        String imageUrl = service.store(file);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(readStoredFile(imageUrl)));

        assertColorCloseTo(colorAt(decoded, 8, 8), expectedTopLeft);
        assertColorCloseTo(colorAt(decoded, 24, 8), expectedTopRight);
        assertColorCloseTo(colorAt(decoded, 8, 24), expectedBottomLeft);
        assertColorCloseTo(colorAt(decoded, 24, 24), expectedBottomRight);
    }

    private static Stream<Arguments> orientationExpectations() {
        return Stream.of(
            Arguments.of(1, Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW),
            Arguments.of(2, Color.GREEN, Color.RED, Color.YELLOW, Color.BLUE),
            Arguments.of(3, Color.YELLOW, Color.BLUE, Color.GREEN, Color.RED),
            Arguments.of(4, Color.BLUE, Color.YELLOW, Color.RED, Color.GREEN),
            Arguments.of(5, Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW),
            Arguments.of(6, Color.BLUE, Color.RED, Color.YELLOW, Color.GREEN),
            Arguments.of(7, Color.YELLOW, Color.GREEN, Color.BLUE, Color.RED),
            Arguments.of(8, Color.GREEN, Color.YELLOW, Color.RED, Color.BLUE));
    }

    // The reviewer's own reproduction: an Exif IFD0 offset of 0x7FFFFFFF (as bytes FF FF FF 7F,
    // little-endian) makes naive bounds arithmetic like `ifd0Offset + 2 > tiff.length` overflow
    // (wrapping negative) instead of correctly rejecting the offset, so the subsequent
    // buffer.getShort(ifd0Offset) throws an unchecked IndexOutOfBoundsException that is not an
    // IllegalArgumentException and would escape store() as an uncaught 500. A malformed Exif
    // offset like this must degrade to "no orientation" -- the image itself decodes fine, and
    // its (corrupt) Exif metadata is stripped by re-encoding regardless.
    @Test
    void toleratesAHostileExifIfdOffsetThatWouldOverflowIntArithmeticInsteadOfFailingWithA500() throws IOException {
        ImageStorageService service = service(uploadDir);
        byte[] hostileJpeg = jpegWithHostileExifIfdOffset();
        MockMultipartFile file = new MockMultipartFile("file", "hostile.jpg", "image/jpeg", hostileJpeg);

        String imageUrl = service.store(file);

        assertThat(imageUrl).endsWith(".jpg");
        assertThat(isJpeg(readStoredFile(imageUrl))).isTrue();
    }

    // JPEG allows any number of 0xFF fill bytes ahead of a marker (ITU-T T.81 B.1.1.2). A
    // marker walk that assumes exactly one 0xFF reads the second one as the marker itself,
    // mistakes the bytes after it for a segment length, and desynchronizes -- so the Exif APP1
    // is never found and a sideways phone photo is stored sideways. Same fixture and
    // expectations as the Orientation=6 case above, with fill bytes spliced in.
    @Test
    void appliesExifOrientationEvenWhenFillBytesPrecedeTheApp1Marker() throws IOException {
        ImageStorageService service = service(uploadDir);
        byte[] paddedJpeg = jpegWithFillBytesBeforeOrientationApp1(6);
        MockMultipartFile file = new MockMultipartFile("file", "phone-photo.jpg", "image/jpeg", paddedJpeg);

        String imageUrl = service.store(file);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(readStoredFile(imageUrl)));

        assertColorCloseTo(colorAt(decoded, 8, 8), Color.BLUE);
        assertColorCloseTo(colorAt(decoded, 24, 8), Color.RED);
        assertColorCloseTo(colorAt(decoded, 8, 24), Color.YELLOW);
        assertColorCloseTo(colorAt(decoded, 24, 24), Color.GREEN);
    }

    @Test
    void deletesAFileStoredUnderTheNestedClubPostsDirectory() throws IOException {
        ImageStorageService service = service(uploadDir);
        byte[] jpegBytes = solidColorJpeg(10, 10, Color.RED);
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpegBytes);
        String imageUrl = service.store(file);
        assertThat(Files.exists(pathFor(imageUrl))).isTrue();

        service.delete(imageUrl);

        assertThat(Files.exists(pathFor(imageUrl))).isFalse();
    }

    @Test
    void deleteIgnoresAUrlThatWouldEscapeTheUploadDirectory() throws IOException {
        ImageStorageService service = service(uploadDir);
        Path outsideFile = uploadDir.resolveSibling("outside-secret.txt");
        Files.writeString(outsideFile, "do not delete me");

        service.delete("/uploads/../outside-secret.txt");

        assertThat(Files.exists(outsideFile)).isTrue();
        Files.deleteIfExists(outsideFile);
    }

    // imageUrl is not fully trusted input: it is read back from a club's stored image_url,
    // which a club admin can set to an arbitrary string through the general club-update
    // endpoint (not just through this service's own store()). delete() must therefore only
    // ever touch filenames matching what store() itself could have produced -- a UUID name
    // under club-posts/, or a legacy flat UUID name directly under the upload root -- never an
    // arbitrary path elsewhere under uploadDir.
    @Test
    void deleteRemovesALegacyFlatUuidNamedImageDirectlyUnderTheUploadRoot() throws IOException {
        ImageStorageService service = service(uploadDir);
        Path legacyFile = uploadDir.resolve("3fa85f64-5717-4562-b3fc-2c963f66afa6.png");
        Files.write(legacyFile, new byte[] {1, 2, 3});

        service.delete("/uploads/3fa85f64-5717-4562-b3fc-2c963f66afa6.png");

        assertThat(Files.exists(legacyFile)).isFalse();
    }

    @Test
    void deleteDoesNotTouchFilesUnderAvatarCacheEvenIfNamedLikeAStoredImage() throws IOException {
        ImageStorageService service = service(uploadDir);
        Path avatarCacheFile = uploadDir.resolve("avatar-cache").resolve("instagram").resolve("someclub.jpg");
        Files.createDirectories(avatarCacheFile.getParent());
        Files.write(avatarCacheFile, new byte[] {1, 2, 3});

        service.delete("/uploads/avatar-cache/instagram/someclub.jpg");

        assertThat(Files.exists(avatarCacheFile)).isTrue();
    }

    @Test
    void deleteDoesNotTouchAFileUnderClubPostsWithANonGeneratedName() throws IOException {
        ImageStorageService service = service(uploadDir);
        Path suspiciousFile = uploadDir.resolve("club-posts").resolve("not-a-generated-name.jpg");
        Files.createDirectories(suspiciousFile.getParent());
        Files.write(suspiciousFile, new byte[] {1, 2, 3});

        service.delete("/uploads/club-posts/not-a-generated-name.jpg");

        assertThat(Files.exists(suspiciousFile)).isTrue();
    }

    @Test
    void deleteDoesNotTouchAFileDirectlyUnderTheUploadRootWithANonUuidName() throws IOException {
        ImageStorageService service = service(uploadDir);
        Path suspiciousFile = uploadDir.resolve("not-a-uuid.png");
        Files.write(suspiciousFile, new byte[] {1, 2, 3});

        service.delete("/uploads/not-a-uuid.png");

        assertThat(Files.exists(suspiciousFile)).isTrue();
    }

    // ImageReaderSpi.getMIMETypes() is documented as nullable, so a reader on the classpath can
    // legitimately claim a stream while reporting no MIME type for it. Sniffing must then treat
    // the upload as an unsupported format (IllegalArgumentException / 400), the same as every
    // other undecodable input, rather than throwing NullPointerException out as a 500 (#95).
    @Test
    void rejectsAnUploadWhoseOnlyReaderReportsNullMimeTypesAsAClientError() throws IOException {
        IIORegistry registry = IIORegistry.getDefaultInstance();
        NullMimeTypeReaderSpi spi = new NullMimeTypeReaderSpi();
        registry.registerServiceProvider(spi, ImageReaderSpi.class);
        try {
            ImageStorageService service = service(uploadDir);
            MockMultipartFile file = new MockMultipartFile(
                "file", "weird.img", "image/png", NullMimeTypeReaderSpi.MAGIC.clone());

            assertThatThrownBy(() -> service.store(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not supported");
        } finally {
            registry.deregisterServiceProvider(spi, ImageReaderSpi.class);
        }
    }

    private Path pathFor(String imageUrl) {
        return uploadDir.resolve(imageUrl.substring("/uploads/".length()));
    }

    private byte[] readStoredFile(String imageUrl) throws IOException {
        return Files.readAllBytes(pathFor(imageUrl));
    }

    private static boolean isJpeg(byte[] bytes) {
        return bytes.length > 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8;
    }

    private static boolean isPng(byte[] bytes) {
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        return bytes.length >= signature.length && Arrays.equals(Arrays.copyOf(bytes, signature.length), signature);
    }

    private static boolean containsApp1ExifMarker(byte[] bytes) {
        for (int i = 0; i < bytes.length - 1; i++) {
            if ((bytes[i] & 0xFF) == 0xFF && (bytes[i + 1] & 0xFF) == 0xE1) {
                return true;
            }
        }
        return false;
    }

    private static Color colorAt(BufferedImage image, int x, int y) {
        return new Color(image.getRGB(x, y), false);
    }

    // Solid color quadrants encoded at high JPEG quality should survive lossy compression
    // almost exactly; a generous per-channel tolerance absorbs the residual DCT rounding.
    private static void assertColorCloseTo(Color actual, Color expected) {
        int tolerance = 40;
        assertThat(Math.abs(actual.getRed() - expected.getRed())).isLessThanOrEqualTo(tolerance);
        assertThat(Math.abs(actual.getGreen() - expected.getGreen())).isLessThanOrEqualTo(tolerance);
        assertThat(Math.abs(actual.getBlue() - expected.getBlue())).isLessThanOrEqualTo(tolerance);
    }

    private static byte[] padTo(byte[] original, int targetLength) {
        return Arrays.copyOf(original, targetLength);
    }

    private static byte[] solidColorJpeg(int width, int height, Color color) throws IOException {
        BufferedImage image = solidColorImage(width, height, color, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    private static byte[] solidColorPng(int width, int height, Color color, boolean withAlpha) throws IOException {
        BufferedImage image = solidColorImage(
            width, height, color, withAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static byte[] solidColorGif(int width, int height, Color color) throws IOException {
        BufferedImage image = solidColorImage(width, height, color, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "gif", out);
        return out.toByteArray();
    }

    private static BufferedImage solidColorImage(int width, int height, Color color, int type) {
        BufferedImage image = new BufferedImage(width, height, type);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        return image;
    }

    private static byte[] animatedGif(int width, int height) throws IOException {
        BufferedImage frame1 = solidColorImage(width, height, Color.RED, BufferedImage.TYPE_INT_RGB);
        BufferedImage frame2 = solidColorImage(width, height, Color.BLUE, BufferedImage.TYPE_INT_RGB);

        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.prepareWriteSequence(null);
            for (BufferedImage frame : new BufferedImage[] {frame1, frame2}) {
                ImageWriteParam param = writer.getDefaultWriteParam();
                IIOMetadata metadata = writer.getDefaultImageMetadata(new ImageTypeSpecifier(frame), param);
                writer.writeToSequence(new IIOImage(frame, null, metadata), param);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static int frameCount(byte[] gifBytes) throws IOException {
        ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(gifBytes))) {
            reader.setInput(iis);
            return reader.getNumImages(true);
        } finally {
            reader.dispose();
        }
    }

    // Hand-builds a minimal, standards-compliant Exif APP1 segment (TIFF header + IFD0 with an
    // Orientation tag and a GPSInfo pointer + a GPS IFD with Lat/Long) and splices it in right
    // after the JPEG SOI marker. Independently verified against this exact fixture with
    // `exiftool` (reports "Orientation: Rotate 90 CW" and real GPS coordinates).
    private static byte[] jpegWithOrientationAndGps(int orientation) throws IOException {
        byte[] quadrants = quadrantJpeg();
        byte[] app1 = buildApp1Segment(orientation);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(quadrants, 0, 2); // SOI
        out.write(app1);
        out.write(quadrants, 2, quadrants.length - 2);
        return out.toByteArray();
    }

    // Splices an APP1 "Exif\0\0" segment containing only a bare, minimal TIFF header --
    // "II*\0" (little-endian marker) followed by an IFD0 offset of FF FF FF 7F, i.e.
    // 0x7FFFFFFF (Integer.MAX_VALUE) -- right after the JPEG SOI marker. This is the
    // reviewer's own reproduction for the int-overflow bounds-check bug: no IFD0, no
    // Orientation tag, just a hostile offset.
    private static byte[] jpegWithHostileExifIfdOffset() throws IOException {
        byte[] quadrants = quadrantJpeg();
        byte[] app1 = buildHostileExifApp1Segment();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(quadrants, 0, 2); // SOI
        out.write(app1);
        out.write(quadrants, 2, quadrants.length - 2);
        return out.toByteArray();
    }

    // The same spliced-in Exif APP1 segment as jpegWithOrientationAndGps, preceded by two
    // 0xFF fill bytes -- legal JPEG padding that a decoder must skip over.
    private static byte[] jpegWithFillBytesBeforeOrientationApp1(int orientation) throws IOException {
        byte[] quadrants = quadrantJpeg();
        byte[] app1 = buildApp1Segment(orientation);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(quadrants, 0, 2); // SOI
        out.write(0xFF);
        out.write(0xFF);
        out.write(app1);
        out.write(quadrants, 2, quadrants.length - 2);
        return out.toByteArray();
    }

    private static byte[] buildHostileExifApp1Segment() {
        byte[] exifHeader = {'E', 'x', 'i', 'f', 0, 0};
        byte[] tiff = {'I', 'I', 0x2A, 0x00, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x7F};
        int payloadLength = exifHeader.length + tiff.length;
        int segmentLength = 2 + payloadLength;

        ByteBuffer buffer = ByteBuffer.allocate(2 + 2 + payloadLength).order(ByteOrder.BIG_ENDIAN);
        buffer.put((byte) 0xFF).put((byte) 0xE1);
        buffer.putShort((short) segmentLength);
        buffer.put(exifHeader);
        buffer.put(tiff);
        return buffer.array();
    }

    private static byte[] quadrantJpeg() throws IOException {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, 16, 16);
        graphics.setColor(Color.GREEN);
        graphics.fillRect(16, 0, 16, 16);
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 16, 16, 16);
        graphics.setColor(Color.YELLOW);
        graphics.fillRect(16, 16, 16, 16);
        graphics.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        try {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.95f);
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(image, null, null), param);
            }
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static byte[] buildApp1Segment(int orientation) {
        byte[] tiff = buildTiffWithOrientationAndGps(orientation);
        byte[] exifHeader = {'E', 'x', 'i', 'f', 0, 0};
        int payloadLength = exifHeader.length + tiff.length;
        int segmentLength = 2 + payloadLength;

        ByteBuffer buffer = ByteBuffer.allocate(2 + 2 + payloadLength).order(ByteOrder.BIG_ENDIAN);
        buffer.put((byte) 0xFF).put((byte) 0xE1);
        buffer.putShort((short) segmentLength);
        buffer.put(exifHeader);
        buffer.put(tiff);
        return buffer.array();
    }

    // Layout (all offsets relative to the start of the TIFF header, little-endian):
    // 0-7 header, 8-9 IFD0 field count, 10-33 IFD0 entries (Orientation, GPSInfo pointer),
    // 34-37 next-IFD offset (0), 38-.. GPS IFD (LatRef, Lat, LonRef, Lon), then the RATIONAL
    // data blocks the GPS Lat/Long entries point to.
    private static byte[] buildTiffWithOrientationAndGps(int orientation) {
        int ifd0Start = 8;
        int ifd0Count = 2;
        int ifd0NextOffsetPos = ifd0Start + 2 + ifd0Count * 12;
        int gpsIfdStart = ifd0NextOffsetPos + 4;
        int gpsCount = 4;
        int gpsNextOffsetPos = gpsIfdStart + 2 + gpsCount * 12;
        int gpsDataStart = gpsNextOffsetPos + 4;
        int latDataOffset = gpsDataStart;
        int lonDataOffset = latDataOffset + 24;
        int totalLength = lonDataOffset + 24;

        ByteBuffer buffer = ByteBuffer.allocate(totalLength).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte) 'I').put((byte) 'I');
        buffer.putShort((short) 42);
        buffer.putInt(8);

        buffer.putShort((short) ifd0Count);
        buffer.putShort((short) 0x0112).putShort((short) 3).putInt(1); // Orientation, SHORT, count 1
        buffer.putShort((short) orientation).putShort((short) 0);
        buffer.putShort((short) 0x8825).putShort((short) 4).putInt(1); // GPSInfo pointer, LONG, count 1
        buffer.putInt(gpsIfdStart);
        buffer.putInt(0); // next IFD0 offset

        buffer.putShort((short) gpsCount);
        buffer.putShort((short) 0x0001).putShort((short) 2).putInt(2); // GPSLatitudeRef, ASCII, "N\0"
        buffer.put((byte) 'N').put((byte) 0).put((byte) 0).put((byte) 0);
        buffer.putShort((short) 0x0002).putShort((short) 5).putInt(3); // GPSLatitude, RATIONAL x3
        buffer.putInt(latDataOffset);
        buffer.putShort((short) 0x0003).putShort((short) 2).putInt(2); // GPSLongitudeRef, ASCII, "E\0"
        buffer.put((byte) 'E').put((byte) 0).put((byte) 0).put((byte) 0);
        buffer.putShort((short) 0x0004).putShort((short) 5).putInt(3); // GPSLongitude, RATIONAL x3
        buffer.putInt(lonDataOffset);
        buffer.putInt(0); // next GPS IFD offset

        buffer.putInt(37).putInt(1); // 37 deg
        buffer.putInt(25).putInt(1); // 25 min
        buffer.putInt(192).putInt(10); // 19.2 sec
        buffer.putInt(122).putInt(1); // 122 deg
        buffer.putInt(5).putInt(1); // 5 min
        buffer.putInt(60).putInt(10); // 6.0 sec

        return buffer.array();
    }

    // A minimal reader that claims a private magic prefix and, like any reader is allowed to,
    // reports no MIME types at all. Registered only for the duration of the test above.
    public static final class NullMimeTypeReaderSpi extends ImageReaderSpi {

        static final byte[] MAGIC = {'N', 'U', 'L', 'L', 'M', 'I', 'M', 'E'};

        public NullMimeTypeReaderSpi() {
            super(
                "test", "1.0",
                new String[] {"nullmime"},
                new String[] {"nullmime"},
                null, // MIME types: the whole point of this reader
                NullMimeTypeReader.class.getName(),
                new Class<?>[] {ImageInputStream.class},
                null,
                false, null, null, null, null,
                false, null, null, null, null);
        }

        @Override
        public boolean canDecodeInput(Object source) throws IOException {
            if (!(source instanceof ImageInputStream stream)) {
                return false;
            }
            byte[] header = new byte[MAGIC.length];
            stream.mark();
            try {
                stream.readFully(header);
            } catch (IOException e) {
                return false;
            } finally {
                stream.reset();
            }
            return Arrays.equals(header, MAGIC);
        }

        @Override
        public ImageReader createReaderInstance(Object extension) {
            return new NullMimeTypeReader(this);
        }

        @Override
        public String getDescription(Locale locale) {
            return "Reader with no MIME types (test)";
        }
    }

    public static final class NullMimeTypeReader extends ImageReader {

        NullMimeTypeReader(ImageReaderSpi originatingProvider) {
            super(originatingProvider);
        }

        @Override
        public int getNumImages(boolean allowSearch) {
            return 1;
        }

        @Override
        public int getWidth(int imageIndex) {
            return 1;
        }

        @Override
        public int getHeight(int imageIndex) {
            return 1;
        }

        @Override
        public Iterator<ImageTypeSpecifier> getImageTypes(int imageIndex) {
            return List.of(ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_RGB))
                .iterator();
        }

        @Override
        public IIOMetadata getStreamMetadata() {
            return null;
        }

        @Override
        public IIOMetadata getImageMetadata(int imageIndex) {
            return null;
        }

        @Override
        public BufferedImage read(int imageIndex, ImageReadParam param) {
            return new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        }
    }
}
