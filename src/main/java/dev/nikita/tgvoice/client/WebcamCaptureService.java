package dev.nikita.tgvoice.client;

import org.openpnp.capture.CaptureDevice;
import org.openpnp.capture.CaptureFormat;
import org.openpnp.capture.CaptureStream;
import org.openpnp.capture.OpenPnpCapture;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Small client-only wrapper around OpenPnP Capture.
 *
 * The service owns the native capture handle and deliberately exposes only
 * immutable frame copies to the recorder. Camera access is lazy so installing
 * the mod does not touch a webcam until video-note recording starts.
 */
public final class WebcamCaptureService implements AutoCloseable {
    private OpenPnpCapture capture;
    private CaptureDevice device;
    private CaptureFormat format;
    private CaptureStream stream;

    public synchronized boolean isOpen() {
        return stream != null;
    }

    public synchronized List<CaptureDevice> devices() {
        ensureCapture();
        return List.copyOf(capture.getDevices());
    }

    public synchronized void openDefault() {
        if (isOpen()) {
            return;
        }
        ensureCapture();
        List<CaptureDevice> devices = capture.getDevices();
        if (devices.isEmpty()) {
            throw new IllegalStateException("No webcam devices detected");
        }

        CaptureDevice selected = devices.get(0);
        List<CaptureFormat> formats = selected.getFormats();
        if (formats.isEmpty()) {
            throw new IllegalStateException("Webcam has no supported capture formats");
        }

        // Prefer a square format close to the video-note limit, then fall back
        // to the first format supplied by the platform driver.
        CaptureFormat selectedFormat = formats.get(0);
        for (CaptureFormat candidate : formats) {
            if (candidate.getWidth() <= 512 && candidate.getHeight() <= 512
                    && candidate.getWidth() == candidate.getHeight()) {
                selectedFormat = candidate;
                break;
            }
        }

        try {
            device = selected;
            format = selectedFormat;
            stream = device.openStream(format);
        } catch (Exception exception) {
            closeStreamOnly();
            throw new IllegalStateException("Unable to open webcam", exception);
        }
    }

    public synchronized BufferedImage capture() {
        if (!isOpen()) {
            throw new IllegalStateException("Webcam is not open");
        }
        try {
            return stream.capture();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to capture webcam frame", exception);
        }
    }

    @Override
    public synchronized void close() {
        closeStreamOnly();
        if (capture != null) {
            try {
                capture.close();
            } catch (Exception ignored) {
                // Native cleanup must not prevent Minecraft from closing.
            }
            capture = null;
        }
        device = null;
        format = null;
    }

    private void ensureCapture() {
        if (capture == null) {
            capture = new OpenPnpCapture();
        }
    }

    private void closeStreamOnly() {
        if (stream != null) {
            try {
                stream.close();
            } catch (Exception ignored) {
                // Best-effort native cleanup.
            }
            stream = null;
        }
    }
}
