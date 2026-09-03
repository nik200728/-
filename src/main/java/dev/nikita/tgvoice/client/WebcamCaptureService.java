package dev.nikita.tgvoice.client;

import org.openpnp.capture.CaptureDevice;
import org.openpnp.capture.CaptureFormat;
import org.openpnp.capture.CaptureStream;
import org.openpnp.capture.OpenPnpCapture;
import org.openpnp.capture.library.CapFormatInfo;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Thin client-side webcam adapter. Camera access stays isolated from the video
 * transport format so the recorder can be tested without a camera device.
 */
public final class WebcamCaptureService implements AutoCloseable {
    public static final int TARGET_SIZE = 512;
    private static final int TARGET_FPS = 15;
    private static final String JPEG_FORMAT = "jpg";

    private final OpenPnpCapture capture;
    private CaptureStream stream;
    private int width;
    private int height;

    public WebcamCaptureService() {
        capture = new OpenPnpCapture();
    }

    /** Opens the first available camera using a format suitable for 15 FPS capture. */
    public synchronized void open() throws Exception {
        if (stream != null) return;

        List<CaptureDevice> devices = capture.getDevices();
        if (devices.isEmpty()) {
            throw new IllegalStateException("No webcam detected");
        }

        CaptureDevice device = devices.get(0);
        CaptureFormat selected = selectFormat(device.getFormats());
        if (selected == null) {
            throw new IllegalStateException("No supported webcam format detected");
        }

        stream = device.openStream(selected);
        CapFormatInfo info = selected.getFormatInfo();
        width = info.width;
        height = info.height;
    }

    public synchronized boolean isOpen() {
        return stream != null;
    }

    /** Captures the newest available frame, scaled/cropped to a square JPEG. */
    public synchronized byte[] captureJpeg() throws IOException {
        if (stream == null) {
            throw new IllegalStateException("Webcam is not open");
        }
        final BufferedImage source;
        try {
            source = stream.capture();
        } catch (Exception exception) {
            throw new IOException("Failed to capture webcam frame", exception);
        }
        if (source == null) return null;

        BufferedImage square = centerSquare(source);
        BufferedImage output = scale(square, TARGET_SIZE, TARGET_SIZE);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(32 * 1024);
        if (!ImageIO.write(output, JPEG_FORMAT, bytes)) {
            throw new IOException("JPEG encoder is unavailable");
        }
        return bytes.toByteArray();
    }

    public synchronized Dimension resolution() {
        return new Dimension(width, height);
    }

    @Override
    public synchronized void close() {
        if (stream != null) {
            try {
                stream.close();
            } catch (Exception ignored) {
                // Camera cleanup must not prevent Minecraft from shutting down.
            }
            stream = null;
        }
        capture.close();
    }

    private static CaptureFormat selectFormat(List<CaptureFormat> formats) {
        if (formats == null || formats.isEmpty()) return null;
        CaptureFormat best = null;
        long bestScore = Long.MAX_VALUE;
        for (CaptureFormat format : formats) {
            CapFormatInfo info = format.getFormatInfo();
            int w = info.width;
            int h = info.height;
            int fps = info.fps;
            if (w < 1 || h < 1 || fps < 1) continue;

            long score = Math.abs((long) w * h - (long) TARGET_SIZE * TARGET_SIZE);
            if (w > TARGET_SIZE || h > TARGET_SIZE) score += 10_000_000L;

            // The recorder consumes frames at 15 FPS. Prefer a mode capable of
            // sustaining that rate, while still allowing unusual cameras whose
            // advertised FPS is above the target.
            if (fps < TARGET_FPS) {
                score += (long) (TARGET_FPS - fps) * 5_000_000L;
            } else {
                score += (long) (fps - TARGET_FPS) * 1_000L;
            }

            if (score < bestScore) {
                best = format;
                bestScore = score;
            }
        }
        return best;
    }

    private static BufferedImage centerSquare(BufferedImage source) {
        int side = Math.min(source.getWidth(), source.getHeight());
        int x = (source.getWidth() - side) / 2;
        int y = (source.getHeight() - side) / 2;
        return source.getSubimage(x, y, side, side);
    }

    private static BufferedImage scale(BufferedImage source, int targetWidth, int targetHeight) {
        BufferedImage output = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = output.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return output;
    }
}
