package com.example.demo.club.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// P1 from the media-decode-hardening audit: a small progressive JPEG whose header claims a
// large resolution used to force ImageStorageService to materialize the full-resolution
// raster (Thumbnailator's subsampling only ever helped baseline JPEG and PNG, never
// progressive JPEG), letting a handful of small uploads OOM the whole JVM. This test
// reproduces that failure mode directly -- not just a 400 assertion -- by running the real
// store() pipeline in a separate, heap-constrained JVM process and checking that process's
// exit code, the same way the audit's own repro did.
class ImageStorageServiceOomRegressionTest {

    @TempDir
    Path tempDir;

    @Test
    void progressiveJpegNearTheMegapixelLimitIsHandledWithoutUnboundedDecodeMemory() throws Exception {
        Path imageFile = tempDir.resolve("progressive.jpg");
        Files.write(imageFile, flatWhiteProgressiveJpeg(5000, 5000));
        Path uploadDir = tempDir.resolve("uploads");

        HarnessResult result = runHarnessInAConstrainedJvm(uploadDir, imageFile, "160m");

        assertThat(result.exitCode())
            .withFailMessage(
                "harness process should not crash/OOM under a constrained heap%nstdout:%n%s%nstderr:%n%s",
                result.stdout(), result.stderr())
            .isZero();
        assertThat(result.stdout()).contains("ACCEPTED");
    }

    private static HarnessResult runHarnessInAConstrainedJvm(Path uploadDir, Path imageFile, String maxHeap)
            throws IOException, InterruptedException {
        String javaBinary = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("java.class.path");

        Process process = new ProcessBuilder(
                javaBinary,
                "-Xmx" + maxHeap,
                "-cp", classpath,
                ImageStorageServiceOomHarnessMain.class.getName(),
                uploadDir.toString(),
                imageFile.toString())
            .start();

        boolean finished = process.waitFor(60, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new AssertionError("harness process did not finish within " + Duration.ofSeconds(60));
        }
        String stdout = new String(process.getInputStream().readAllBytes());
        String stderr = new String(process.getErrorStream().readAllBytes());
        return new HarnessResult(process.exitValue(), stdout, stderr);
    }

    private record HarnessResult(int exitCode, String stdout, String stderr) {
    }

    private static byte[] flatWhiteProgressiveJpeg(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        ImageWriter writer = writers.next();
        try {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
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
}
