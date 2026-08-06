package com.example.demo.club.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

// P2 from the round-3 review: decodeSemaphore.acquireUninterruptibly() had no timeout and no
// queue bound, so an upload burst on a small heap (where the semaphore may have as few as 1-2
// permits) could pin every waiting request's Tomcat worker thread indefinitely, stalling
// unrelated endpoints too. This exercises the fix -- a bounded wait that fails fast with
// ImageProcessingUnavailableException once exhausted -- using a service instance configured
// with exactly one decode permit and a short timeout, so the exhaustion path is reachable
// deterministically instead of depending on the real, heap-derived permit count.
class ImageStorageServiceDecodeCapacityTest {

    @TempDir
    Path uploadDir;

    @Test
    void throwsImageProcessingUnavailableWhenTheSingleDecodePermitIsHeldAndTheBoundedWaitTimesOut() throws Exception {
        // 1 permit, a 20ms bounded wait.
        ImageStorageService service = new ImageStorageService(uploadDir.toString(), 1, 20);

        // Large enough that decoding, resizing, and re-encoding it takes a real, non-trivial
        // amount of time (comfortably longer than the 20ms + startup slack below), so the
        // background thread is still holding the single permit when the foreground call below
        // attempts to acquire it.
        byte[] slowJpeg = solidColorJpeg(5000, 5000, Color.RED);
        byte[] quickJpeg = solidColorJpeg(10, 10, Color.BLUE);

        CountDownLatch backgroundStarted = new CountDownLatch(1);
        Thread background = new Thread(() -> {
            backgroundStarted.countDown();
            try {
                service.store(new MockMultipartFile("file", "slow.jpg", "image/jpeg", slowJpeg));
            } catch (IOException ignored) {
                // Only this test's own timing assertion below matters; a failure here would
                // already be caught by the other, non-concurrent tests in this suite.
            }
        });
        background.start();
        backgroundStarted.await();
        // Gives the background thread time to get past the (fast) sniff/size/dimension checks
        // and actually acquire the single permit before this thread tries to acquire it too.
        Thread.sleep(30);

        MockMultipartFile file = new MockMultipartFile("file", "quick.jpg", "image/jpeg", quickJpeg);
        assertThatThrownBy(() -> service.store(file))
            .isInstanceOf(ImageProcessingUnavailableException.class)
            .hasMessageContaining("Too many images")
            .hasMessageContaining("try again");

        background.join(10_000);
        assertThat(background.isAlive()).isFalse();
    }

    private static byte[] solidColorJpeg(int width, int height, Color color) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }
}
