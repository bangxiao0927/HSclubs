package com.example.demo.club.service;

import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;

/**
 * Owns the full club-image upload pipeline: sniff the real format from magic bytes, reject
 * what is too big or too many pixels, re-encode everything except GIF into a flattened,
 * EXIF-stripped JPEG, and write the result under {@code club-posts/}. Also owns deleting a
 * previously stored image.
 * <p>
 * The extension on the returned URL always matches the bytes actually written (sniffed
 * format, never the client's declared {@code Content-Type}): the {@code /uploads/**} resource
 * handler derives its response {@code Content-Type} from that extension, so a mismatch would
 * make it lie about the content it serves.
 */
@Service
public class ImageStorageService {

    private static final String UPLOADS_URL_PREFIX = "/uploads/";
    private static final String IMAGE_SUBDIRECTORY = "club-posts";

    private static final long MAX_GIF_BYTES = 2L * 1024 * 1024;
    private static final long MAX_OTHER_BYTES = 5L * 1024 * 1024;
    private static final long MAX_PIXELS = 50_000_000L;
    private static final int MAX_DIMENSION = 1600;
    private static final float JPEG_QUALITY = 0.82f;

    private final Path uploadDir;
    private final Path imageDirectory;

    public ImageStorageService(@Value("${app.upload.dir:uploads}") String uploadDirPath) {
        this.uploadDir = Paths.get(uploadDirPath).toAbsolutePath().normalize();
        this.imageDirectory = this.uploadDir.resolve(IMAGE_SUBDIRECTORY);
        try {
            Files.createDirectories(this.imageDirectory);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create upload directory: " + this.imageDirectory, e);
        }
    }

    /**
     * Validates, re-encodes (except GIF, which passes through verbatim to preserve animation),
     * and stores the uploaded file. Returns its public URL, e.g. {@code /uploads/club-posts/<uuid>.jpg}.
     *
     * @throws IllegalArgumentException if the file is empty, is not a recognized image format,
     *                                   exceeds the size limit for its format, or exceeds the
     *                                   maximum allowed pixel count
     * @throws IOException              if reading the upload or writing the stored file fails
     */
    public String store(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        byte[] bytes = file.getBytes();
        ImageFormat format = sniffFormat(bytes);
        validateSize(format, bytes.length);

        Dimensions dimensions = readDimensions(bytes);
        if ((long) dimensions.width() * dimensions.height() > MAX_PIXELS) {
            throw new IllegalArgumentException("Image exceeds the maximum allowed resolution");
        }

        byte[] outputBytes;
        String extension;
        if (format == ImageFormat.GIF) {
            outputBytes = bytes;
            extension = ".gif";
        } else {
            outputBytes = reencodeToJpeg(bytes, dimensions);
            extension = ".jpg";
        }

        String filename = UUID.randomUUID() + extension;
        Path target = imageDirectory.resolve(filename).normalize();
        if (!target.startsWith(imageDirectory)) {
            throw new IllegalStateException("Generated an invalid file name: " + filename);
        }
        Files.write(target, outputBytes);

        return UPLOADS_URL_PREFIX + IMAGE_SUBDIRECTORY + "/" + filename;
    }

    /**
     * Deletes a previously stored image by its public URL (e.g. {@code /uploads/club-posts/<uuid>.jpg}).
     * Silently does nothing if the URL is outside {@code /uploads/}, resolves outside the upload
     * directory, or the file no longer exists.
     */
    public void delete(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith(UPLOADS_URL_PREFIX)) {
            return;
        }
        String relativePath = imageUrl.substring(UPLOADS_URL_PREFIX.length());
        Path target = uploadDir.resolve(relativePath).normalize();
        if (!target.startsWith(uploadDir)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // Best-effort cleanup; UploadCleanupService reclaims orphans left behind overnight.
        }
    }

    private void validateSize(ImageFormat format, int byteLength) {
        if (format == ImageFormat.GIF) {
            if (byteLength > MAX_GIF_BYTES) {
                throw new IllegalArgumentException("GIF must be 2MB or smaller");
            }
        } else if (byteLength > MAX_OTHER_BYTES) {
            throw new IllegalArgumentException("Image must be 5MB or smaller");
        }
    }

    private ImageFormat sniffFormat(byte[] bytes) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (iis == null) {
                throw new IllegalArgumentException("Unsupported image type");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("Unsupported image type");
            }
            ImageReader reader = readers.next();
            try {
                return ImageFormat.fromMimeTypes(reader.getOriginatingProvider().getMIMETypes());
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // Reads width/height from the format header only (no full decode) so a bomb -- a small
    // file whose header claims an enormous pixel count -- can be rejected before it is ever
    // fully decoded into memory.
    private Dimensions readDimensions(byte[] bytes) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("Unsupported image type");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis);
                return new Dimensions(reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // EXIF orientation must be applied here, from the original InputStream: the JDK JPEG reader
    // does not expose it in usable form, and any BufferedImage decoded beforehand has already
    // lost that metadata. Never upscale past the original resolution: only constrain to
    // MAX_DIMENSION when the source is already larger than that.
    private byte[] reencodeToJpeg(byte[] bytes, Dimensions original) throws IOException {
        BufferedImage decoded;
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            Thumbnails.Builder<? extends InputStream> builder = Thumbnails.of(in).useExifOrientation(true);
            if (original.width() > MAX_DIMENSION || original.height() > MAX_DIMENSION) {
                builder = builder.size(MAX_DIMENSION, MAX_DIMENSION).keepAspectRatio(true);
            } else {
                builder = builder.scale(1.0);
            }
            decoded = builder.asBufferedImage();
        }

        BufferedImage flattened = flattenToWhiteBackground(decoded);
        return encodeJpeg(flattened);
    }

    // JPEG has no transparency; Thumbnailator's own JPEG writer flattens onto black (see its
    // OutputStreamImageSink), not white, so transparency is flattened here instead.
    private static BufferedImage flattenToWhiteBackground(BufferedImage source) {
        BufferedImage flattened = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = flattened.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, source.getWidth(), source.getHeight());
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return flattened;
    }

    // Writing a fresh BufferedImage with null IIOMetadata is what strips EXIF (and every other
    // marker) from the original file as a side effect.
    private static byte[] encodeJpeg(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        try {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(image, null, null), param);
            }
            return out.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    private record Dimensions(int width, int height) {
    }

    private enum ImageFormat {
        JPEG, PNG, WEBP, GIF;

        static ImageFormat fromMimeTypes(String[] mimeTypes) {
            for (String mimeType : mimeTypes) {
                String lower = mimeType.toLowerCase(Locale.ROOT);
                if (lower.contains("jpeg")) {
                    return JPEG;
                }
                if (lower.contains("png")) {
                    return PNG;
                }
                if (lower.contains("webp")) {
                    return WEBP;
                }
                if (lower.contains("gif")) {
                    return GIF;
                }
            }
            throw new IllegalArgumentException("Unsupported image type");
        }
    }
}
