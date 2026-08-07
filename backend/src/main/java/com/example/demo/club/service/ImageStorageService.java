package com.example.demo.club.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
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
    // Lowered from the original 50,000,000: this endpoint only ever needs a MAX_DIMENSION
    // derivative, and 30MP still comfortably covers ordinary phone/camera photos. This bound
    // is defense in depth, not the primary memory guard for JPEG/PNG -- see decodeFullImage
    // below for that (reader-level subsampling, which both formats honour).
    private static final long MAX_PIXELS = 30_000_000L;
    // WebP has no equivalent primary guard: TwelveMonkeys' WebPImageReader does honour
    // ImageReadParam.setSourceSubsampling for the *output* it returns (confirmed empirically --
    // a 2000x2000 WebP read with a subsampling factor of 4 does return a 500x500 image), but
    // peak decode memory is dominated by VP8Frame's per-macroblock decode state, which it
    // allocates from the *source* image's own pixel dimensions regardless of the requested
    // subsampling factor -- so a 6000x5000 (30-megapixel) WebP still OOMs a 512MB heap even
    // when asked to subsample all the way down to 750x625. This is therefore WebP's *only*
    // memory guard, and must be low enough on its own: a 3-megapixel WebP was confirmed safe
    // to fully decode at a 96MB heap.
    private static final long MAX_WEBP_PIXELS = 3_000_000L;
    private static final int MAX_DIMENSION = 1600;
    private static final float JPEG_QUALITY = 0.82f;
    private static final int EXIF_ORIENTATION_TAG = 0x0112;

    // Bounds how many decodes (the memory- and CPU-heavy part of store()) run at once. Peak
    // memory per decode is now bounded (see decodeFullImage), but bounding concurrency too is
    // cheap insurance against many simultaneous uploads each holding their own bounded-but-
    // nonzero decode buffers at the same time. Sized from the configured heap rather than CPU
    // count: CPU count does not track how much memory a small/constrained container actually
    // has, which is exactly the axis this bound needs to track.
    private static final long ASSUMED_PEAK_DECODE_BYTES = 250L * 1024 * 1024;
    private final Semaphore decodeSemaphore;
    private final long decodeAcquireTimeoutMillis;

    private static int computeMaxConcurrentDecodesFromHeap() {
        long maxMemory = Runtime.getRuntime().maxMemory();
        long byMemory = maxMemory / ASSUMED_PEAK_DECODE_BYTES;
        return (int) Math.max(1, Math.min(byMemory, 16));
    }

    // Named once so every "we could not sniff a supported format" site (the type is genuinely
    // unsupported, the stream has no readers at all, or the bytes are truncated before a
    // reader is even chosen) shows the same actionable copy: which formats are accepted, and
    // why the iPhone default (HEIC) so often trips this -- iOS Safari's own file picker usually
    // converts to JPEG for web uploads, but a raw .heic pulled from the Files app is not decodable
    // here (see #82).
    private static final String UNSUPPORTED_IMAGE_TYPE_MESSAGE =
        "Unsupported image type. Supported formats are JPEG, PNG, WebP, and GIF; HEIC is not supported.";

    // A UUID (as produced by UUID.randomUUID()) followed by an extension store() could have
    // written: ".jpg"/".gif" under club-posts/, since that is all store() has ever produced;
    // the wider extension set for the flat legacy pattern below matches what the pre-
    // ImageStorageService controller used to write (jpg/jpeg/png/webp/gif).
    private static final String UUID_PATTERN =
        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

    // imageUrl passed to delete() is not fully trusted: it is read back from a club's stored
    // image_url, which can be set to an arbitrary string through the general club-update
    // endpoint, not only through this service's own store(). Restricting deletable relative
    // paths to exactly what store() itself could have produced -- and nothing else under
    // uploadDir, e.g. avatar-cache/ (a completely different subsystem) -- is what keeps that
    // caller-controlled string from being usable to delete arbitrary files.
    private static final Pattern GENERATED_CLUB_POST_IMAGE_PATH = Pattern.compile(
        "^" + IMAGE_SUBDIRECTORY + "/" + UUID_PATTERN + "\\.(?:jpg|gif)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEGACY_FLAT_IMAGE_PATH = Pattern.compile(
        "^" + UUID_PATTERN + "\\.(?:jpg|jpeg|png|webp|gif)$", Pattern.CASE_INSENSITIVE);

    private final Path uploadDir;
    private final Path imageDirectory;

    public ImageStorageService(
            @Value("${app.upload.dir:uploads}") String uploadDirPath,
            // 0 means "compute from the configured heap" (see computeMaxConcurrentDecodesFromHeap);
            // overridable so a deployment (or a test exercising the exhaustion path
            // deterministically) can pin an exact permit count instead.
            @Value("${app.image.max-concurrent-decodes:0}") int configuredMaxConcurrentDecodes,
            @Value("${app.image.decode-acquire-timeout-ms:5000}") long decodeAcquireTimeoutMillis) {
        this.uploadDir = Paths.get(uploadDirPath).toAbsolutePath().normalize();
        this.imageDirectory = this.uploadDir.resolve(IMAGE_SUBDIRECTORY);
        try {
            Files.createDirectories(this.imageDirectory);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create upload directory: " + this.imageDirectory, e);
        }
        int maxConcurrentDecodes = configuredMaxConcurrentDecodes > 0
            ? configuredMaxConcurrentDecodes
            : computeMaxConcurrentDecodesFromHeap();
        this.decodeSemaphore = new Semaphore(maxConcurrentDecodes);
        this.decodeAcquireTimeoutMillis = decodeAcquireTimeoutMillis;
    }

    // Visible for testing: lets the decode-capacity test wait until a permit is observably held
    // before asserting the fail-fast path, instead of racing a fixed sleep against a background
    // upload thread.
    int availableDecodePermits() {
        return decodeSemaphore.availablePermits();
    }

    /**
     * Validates, re-encodes (except GIF, which passes through verbatim to preserve animation),
     * and stores the uploaded file. Returns its public URL, e.g. {@code /uploads/club-posts/<uuid>.jpg}.
     *
     * @throws IllegalArgumentException if the file is empty, is not a recognized image format,
     *                                   exceeds the size limit for its format, or exceeds the
     *                                   maximum allowed pixel count
     * @throws ImageProcessingUnavailableException if too many decodes are already running and
     *                                              a bounded wait for a free permit timed out
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
        long pixelCount = (long) dimensions.width() * dimensions.height();
        if (format == ImageFormat.WEBP && pixelCount > MAX_WEBP_PIXELS) {
            // WebP's cap is much stricter than JPEG/PNG's (3MP vs 30MP -- see MAX_WEBP_PIXELS'
            // own comment for why) and low enough to plausibly reject something a viewer would
            // expect to just work, e.g. a 2560x1440 screenshot (3.7MP), so the generic message
            // is not good enough here: name the actual limit and point to the workaround.
            throw new IllegalArgumentException(
                "WebP images are limited to 3 megapixels. Please re-upload this image as a JPEG or PNG, "
                    + "or use a smaller image.");
        }
        if (pixelCount > MAX_PIXELS) {
            throw new IllegalArgumentException("Image exceeds the maximum allowed resolution of 30 megapixels.");
        }

        byte[] outputBytes;
        String extension;
        if (format == ImageFormat.GIF) {
            outputBytes = bytes;
            extension = ".gif";
        } else {
            outputBytes = reencodeToJpeg(bytes, dimensions, format);
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
        if (!isDeletableRelativePath(relativePath)) {
            return;
        }
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

    private static boolean isDeletableRelativePath(String relativePath) {
        return GENERATED_CLUB_POST_IMAGE_PATH.matcher(relativePath).matches()
            || LEGACY_FLAT_IMAGE_PATH.matcher(relativePath).matches();
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
                throw new IllegalArgumentException(UNSUPPORTED_IMAGE_TYPE_MESSAGE);
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException(UNSUPPORTED_IMAGE_TYPE_MESSAGE);
            }
            ImageReader reader = readers.next();
            try {
                ImageReaderSpi provider = reader.getOriginatingProvider();
                return ImageFormat.fromMimeTypes(provider == null ? null : provider.getMIMETypes());
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            // A magic-byte prefix is enough to pick a candidate reader; a corrupt or truncated
            // body is a bad *upload*, not a server failure, so this must be a 400, not a 500.
            throw new IllegalArgumentException("Unreadable or corrupt image", e);
        }
    }

    // Reads width/height from the format header only (no full decode) so a bomb -- a small
    // file whose header claims an enormous pixel count -- can be rejected before it is ever
    // fully decoded into memory.
    private Dimensions readDimensions(byte[] bytes) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException(UNSUPPORTED_IMAGE_TYPE_MESSAGE);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis);
                return new Dimensions(reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            // A truncated/corrupt file can pass the magic-byte sniff above but still throw here
            // when the reader actually tries to parse the header (e.g. IIOException "I/O error
            // reading PNG header" or "JFIF not permitted in stream metadata" for a cut-off
            // JPEG). That is bad client input, not a server failure -- a 400, not a 500.
            throw new IllegalArgumentException("Unreadable or corrupt image", e);
        }
    }

    // A bounded wait, not acquireUninterruptibly(): that had no timeout and no queue bound, so
    // an upload burst on a small heap (where computeMaxConcurrentDecodesFromHeap() yields as
    // few as 1-2 permits) could pin every waiting request's Tomcat worker thread indefinitely,
    // stalling unrelated endpoints too -- including for clients that have already disconnected.
    // InstagramAvatarCacheService already has the right shape for this (tryAcquire, fail fast);
    // this waits briefly first, since a short queueing delay is preferable to an immediate
    // rejection for what is normally a sub-second decode, but still fails fast (a 503, not an
    // indefinite hang) once that wait is exhausted.
    private byte[] reencodeToJpeg(byte[] bytes, Dimensions original, ImageFormat format) throws IOException {
        boolean acquired;
        try {
            acquired = decodeSemaphore.tryAcquire(decodeAcquireTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ImageProcessingUnavailableException(
                "Image processing was interrupted. Please try again.");
        }
        if (!acquired) {
            throw new ImageProcessingUnavailableException(
                "Too many images are being processed right now. Please try again in a moment.");
        }
        try {
            BufferedImage decoded = decodeFullImage(bytes, original, format);
            BufferedImage flattened = flattenToWhiteBackground(decoded);
            return encodeJpeg(flattened);
        } finally {
            decodeSemaphore.release();
        }
    }

    // Every accepted format's peak decode memory must be bounded regardless of its own
    // internal encoding (this was previously assumed true for baseline JPEG and PNG "because
    // they decode row-by-row", which turned out to be wrong: a large-enough raster still
    // needs a full-size destination buffer even when the *source* decode is row-by-row --
    // see the RGB PNG reproduction in ImageStorageServiceOomRegressionTest). So JPEG and PNG
    // both always decode at a reader-subsampled resolution once the source exceeds
    // MAX_DIMENSION, via ImageReadParam.setSourceSubsampling, which both readers honour.
    //
    // WebP is the exception: subsampling cannot bound its peak decode memory (see
    // MAX_WEBP_PIXELS above for why -- it is not that TwelveMonkeys ignores the subsampling
    // request, its *output* is correctly subsampled, but the per-macroblock decode state it
    // allocates along the way scales with the source image regardless); MAX_WEBP_PIXELS alone
    // bounds it instead, and a WebP that reaches this method is already small enough to decode
    // at full resolution.
    //
    // The header/dimensions read earlier in the pipeline only parses enough of the file to find
    // width and height; it does not guarantee the pixel data or embedded metadata (e.g. an ICC
    // profile) that comes after is intact. A file that fails here (IIOException) still passed
    // that earlier header read, so this is the full-decode failure mode of a corrupt or
    // truncated upload -- bad client input, not a server failure.
    private static BufferedImage decodeFullImage(byte[] bytes, Dimensions original, ImageFormat format) {
        Integer orientation = format == ImageFormat.JPEG ? safeReadJpegExifOrientation(bytes) : null;
        int subsamplingFactor = format == ImageFormat.WEBP
            ? 1
            : computeSubsamplingFactor(original.width(), original.height());
        BufferedImage decoded = readAtResolution(bytes, subsamplingFactor);
        BufferedImage oriented = applyExifOrientation(decoded, orientation);
        return fitWithinMaxDimension(oriented);
    }

    // Uniform integer subsampling, derived from whichever side is larger, mirrors the aspect-
    // ratio-preserving fit into the (square) MAX_DIMENSION x MAX_DIMENSION box that the final
    // image must land in; keeping it uniform (not independent per axis) is what keeps this
    // consistent with the never-upscale and keep-aspect-ratio behavior below. Never subsamples
    // an image that already fits, so small images are still decoded at full resolution.
    private static int computeSubsamplingFactor(int width, int height) {
        int largerDimension = Math.max(width, height);
        if (largerDimension <= MAX_DIMENSION) {
            return 1;
        }
        return largerDimension / MAX_DIMENSION;
    }

    private static BufferedImage readAtResolution(byte[] bytes, int subsamplingFactor) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException(UNSUPPORTED_IMAGE_TYPE_MESSAGE);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis);
                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(subsamplingFactor, subsamplingFactor, 0, 0);
                return reader.read(0, param);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Unreadable or corrupt image", e);
        }
    }

    // Scales the already-subsampled (and already-oriented) image down to fit inside the
    // MAX_DIMENSION x MAX_DIMENSION box, preserving aspect ratio. Integer subsampling above
    // only coarsens resolution in whole-pixel steps, so the subsampled image can still be up
    // to roughly twice MAX_DIMENSION on a side; this final single-step resize (well within
    // quality range for a sub-2x downscale) brings it the rest of the way to the target size.
    // Never upscales: an image that already fits is returned unchanged.
    private static BufferedImage fitWithinMaxDimension(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        if (width <= MAX_DIMENSION && height <= MAX_DIMENSION) {
            return image;
        }
        double scale = Math.min((double) MAX_DIMENSION / width, (double) MAX_DIMENSION / height);
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));
        return resizeTo(image, targetWidth, targetHeight, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }

    private static BufferedImage resizeTo(BufferedImage source, int targetWidth, int targetHeight, Object interpolationHint) {
        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = resized.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolationHint);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        graphics.dispose();
        return resized;
    }

    // Applies the rotation/flip that the Exif Orientation tag (1-8) calls for. Each case is
    // built from the same handful of primitive transforms (see below), matching the mapping
    // exiftool reports for these values; verified against the orientation=6 fixture this
    // service is tested with (see ImageStorageServiceTest#everyExifOrientationValueDisplaysTheRightWayUp).
    private static BufferedImage applyExifOrientation(BufferedImage source, Integer orientation) {
        int value = orientation == null ? 1 : orientation;
        switch (value) {
            case 2:
                return flipHorizontal(source);
            case 3:
                return rotate180(source);
            case 4:
                return flipVertical(source);
            case 5:
                return flipHorizontal(rotate90Clockwise(source));
            case 6:
                return rotate90Clockwise(source);
            case 7:
                return flipHorizontal(rotate90CounterClockwise(source));
            case 8:
                return rotate90CounterClockwise(source);
            default:
                return source;
        }
    }

    private static BufferedImage flipHorizontal(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        BufferedImage dest = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = dest.createGraphics();
        graphics.scale(-1.0, 1.0);
        graphics.translate(-width, 0);
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return dest;
    }

    private static BufferedImage flipVertical(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        BufferedImage dest = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = dest.createGraphics();
        graphics.scale(1.0, -1.0);
        graphics.translate(0, -height);
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return dest;
    }

    private static BufferedImage rotate180(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        BufferedImage dest = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = dest.createGraphics();
        graphics.translate(width, height);
        graphics.rotate(Math.PI);
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return dest;
    }

    private static BufferedImage rotate90Clockwise(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        BufferedImage dest = new BufferedImage(height, width, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = dest.createGraphics();
        graphics.translate(height, 0);
        graphics.rotate(Math.PI / 2);
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return dest;
    }

    private static BufferedImage rotate90CounterClockwise(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        BufferedImage dest = new BufferedImage(height, width, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = dest.createGraphics();
        graphics.translate(0, width);
        graphics.rotate(-Math.PI / 2);
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return dest;
    }

    // Wraps readJpegExifOrientation so a malformed or hostile Exif segment can never escape as
    // an uncaught exception (a 500): it degrades to "no orientation" instead. The bounds
    // checks in readJpegExifOrientation/readOrientationFromTiff are believed to already
    // reject every malformed offset safely on their own; this is a deliberate second layer,
    // since the cost of a mis-rotated-but-otherwise-fine upload is far lower than a 500 for
    // metadata we are about to strip by re-encoding regardless.
    private static Integer safeReadJpegExifOrientation(byte[] bytes) {
        try {
            return readJpegExifOrientation(bytes);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // A single pass over JPEG marker segments from the start of the file, looking for the
    // Exif Orientation tag inside an APP1 "Exif\0\0" segment's TIFF IFD0 -- entirely
    // independently of ImageIO's own metadata reader, which throws when Exif is the very
    // first marker after SOI, before any JFIF APP0 (a real camera/phone JPEG layout this
    // service is also tested against; see
    // ImageStorageServiceTest#everyExifOrientationValueDisplaysTheRightWayUp). Every offset
    // computed here is derived from -- and bounded by -- `position`, which is itself bounded
    // by bytes.length (already capped at 5MB by validateSize before this is ever called), so
    // none of the additions below can overflow. The TIFF payload's *own* embedded offsets are
    // a different story -- see readOrientationFromTiff.
    private static Integer readJpegExifOrientation(byte[] bytes) {
        if (bytes.length < 4 || (bytes[0] & 0xFF) != 0xFF || (bytes[1] & 0xFF) != 0xD8) {
            return null;
        }
        int position = 2;
        while (position + 4 <= bytes.length) {
            if ((bytes[position] & 0xFF) != 0xFF) {
                break;
            }
            // A marker may be preceded by any number of 0xFF fill bytes (ITU-T T.81 B.1.1.2),
            // so the marker is the first byte after this run of 0xFF -- not unconditionally
            // bytes[position + 1], which for a padded file would be another 0xFF and would
            // desynchronize the whole walk (a legal photo would then silently lose its
            // orientation).
            int markerPosition = position;
            while (markerPosition + 1 < bytes.length && (bytes[markerPosition + 1] & 0xFF) == 0xFF) {
                markerPosition++;
            }
            if (markerPosition + 1 >= bytes.length) {
                break;
            }
            int marker = bytes[markerPosition + 1] & 0xFF;
            position = markerPosition + 2;
            if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                continue;
            }
            if (marker == 0xD9 || marker == 0xDA) {
                break;
            }
            if (position + 2 > bytes.length) {
                break;
            }
            int segmentLength = ((bytes[position] & 0xFF) << 8) | (bytes[position + 1] & 0xFF);
            int segmentStart = position + 2;
            int segmentEnd = position + segmentLength;
            if (segmentLength < 2 || segmentEnd > bytes.length) {
                break;
            }
            if (marker == 0xE1 && isExifApp1Segment(bytes, segmentStart, segmentEnd)) {
                byte[] tiff = Arrays.copyOfRange(bytes, segmentStart + 6, segmentEnd);
                return readOrientationFromTiff(tiff);
            }
            position = segmentEnd;
        }
        return null;
    }

    private static boolean isExifApp1Segment(byte[] bytes, int segmentStart, int segmentEnd) {
        return segmentEnd - segmentStart >= 6
            && bytes[segmentStart] == 'E' && bytes[segmentStart + 1] == 'x'
            && bytes[segmentStart + 2] == 'i' && bytes[segmentStart + 3] == 'f'
            && bytes[segmentStart + 4] == 0 && bytes[segmentStart + 5] == 0;
    }

    // ifd0Offset and entryOffset below are read from the file's own bytes (buffer.getInt/
    // getShort), not derived from tiff.length -- a hostile file can set them to any 32-bit
    // value, including one near Integer.MAX_VALUE. Validating with `offset + length >
    // tiff.length`-style addition is exactly what let a hostile offset (e.g. 0x7FFFFFFF)
    // overflow into a small/negative number and slip past the check, so isWithinBounds below
    // only ever subtracts two already-small, trusted values (tiff.length and length) instead
    // of adding to the untrusted offset.
    private static Integer readOrientationFromTiff(byte[] tiff) {
        if (tiff.length < 8) {
            return null;
        }
        boolean littleEndian = tiff[0] == 'I' && tiff[1] == 'I';
        boolean bigEndian = tiff[0] == 'M' && tiff[1] == 'M';
        if (!littleEndian && !bigEndian) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(tiff).order(littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        int ifd0Offset = buffer.getInt(4);
        if (!isWithinBounds(ifd0Offset, 2, tiff.length)) {
            return null;
        }
        int entryCount = buffer.getShort(ifd0Offset) & 0xFFFF;
        for (int i = 0; i < entryCount; i++) {
            // ifd0Offset is already validated within [0, tiff.length), and tiff.length is a
            // small, trusted value (an APP1 segment payload, capped well under 64KB) -- so
            // this addition (unlike the untrusted offsets it is validated against) cannot
            // overflow even for the maximum possible entryCount (65535).
            int entryOffset = ifd0Offset + 2 + i * 12;
            if (!isWithinBounds(entryOffset, 12, tiff.length)) {
                break;
            }
            int tag = buffer.getShort(entryOffset) & 0xFFFF;
            if (tag == EXIF_ORIENTATION_TAG) {
                return buffer.getShort(entryOffset + 8) & 0xFFFF;
            }
        }
        return null;
    }

    // True if and only if [offset, offset + length) is entirely within [0, arrayLength),
    // without adding to the untrusted `offset` value: `arrayLength - length` is a subtraction
    // of two small, trusted values (never near Integer.MIN_VALUE/MAX_VALUE), so it cannot
    // overflow regardless of what `offset` itself is -- including Integer.MAX_VALUE or a
    // negative number from a hostile/corrupt file.
    private static boolean isWithinBounds(int offset, int length, int arrayLength) {
        return offset >= 0 && offset <= arrayLength - length;
    }

    // JPEG has no transparency, and ImageIO's JPEG writer does not flatten for us: handed an
    // image with an alpha band it either writes wrong colours or refuses outright. Flattening
    // explicitly here also pins *which* colour transparency collapses to -- white, matching the
    // page background, rather than the black that the previously-used Thumbnailator picked.
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
            // A reader is allowed to report no MIME types at all (ImageReaderSpi.getMIMETypes()
            // is documented as nullable, and getOriginatingProvider() is null for a reader
            // constructed outside the registry). We cannot tell what such a reader decodes, so
            // it is "unsupported format" -- a 400 -- not a NullPointerException escaping as a 500.
            if (mimeTypes == null) {
                throw new IllegalArgumentException(UNSUPPORTED_IMAGE_TYPE_MESSAGE);
            }
            for (String mimeType : mimeTypes) {
                if (mimeType == null) {
                    continue;
                }
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
            throw new IllegalArgumentException(UNSUPPORTED_IMAGE_TYPE_MESSAGE);
        }
    }
}
