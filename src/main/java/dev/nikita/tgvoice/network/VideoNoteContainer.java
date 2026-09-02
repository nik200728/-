package dev.nikita.tgvoice.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Small versioned container for circular-video frames.
 *
 * The container deliberately does not pretend to be MP4/WebM: each frame is an
 * encoded image (normally JPEG) with a presentation timestamp. A future camera
 * backend can produce these frames without changing the Minecraft network API.
 */
public final class VideoNoteContainer {
    private static final int MAGIC = 0x54475631; // TGV1
    private static final int VERSION = 1;
    private static final int MAX_FRAMES = 1800;
    private static final int MAX_FRAME_BYTES = 512 * 1024;

    private VideoNoteContainer() {}

    public record Frame(long timestampMillis, byte[] encodedImage) {
        public Frame {
            if (timestampMillis < 0) throw new IllegalArgumentException("negative timestamp");
            if (encodedImage == null || encodedImage.length == 0 || encodedImage.length > MAX_FRAME_BYTES) {
                throw new IllegalArgumentException("invalid frame data");
            }
            encodedImage = encodedImage.clone();
        }

        @Override
        public byte[] encodedImage() {
            return encodedImage.clone();
        }
    }

    public record Video(int width, int height, int frameRate, long durationMillis, List<Frame> frames) {
        public Video {
            if (width < 1 || width > VideoNotePayload.MAX_DIMENSION) throw new IllegalArgumentException("invalid width");
            if (height < 1 || height > VideoNotePayload.MAX_DIMENSION) throw new IllegalArgumentException("invalid height");
            if (frameRate < 1 || frameRate > VideoNotePayload.MAX_FRAME_RATE) throw new IllegalArgumentException("invalid frame rate");
            if (durationMillis < 1 || durationMillis > VideoNotePayload.MAX_DURATION_MILLIS) throw new IllegalArgumentException("invalid duration");
            if (frames == null || frames.isEmpty() || frames.size() > MAX_FRAMES) throw new IllegalArgumentException("invalid frame count");
            frames = List.copyOf(frames);
        }
    }

    public static byte[] encode(Video video) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(MAGIC);
            out.writeByte(VERSION);
            out.writeShort(video.width());
            out.writeShort(video.height());
            out.writeByte(video.frameRate());
            out.writeLong(video.durationMillis());
            out.writeInt(video.frames().size());
            long previousTimestamp = -1;
            for (Frame frame : video.frames()) {
                if (frame.timestampMillis() < previousTimestamp) {
                    throw new IllegalArgumentException("frames must be ordered by timestamp");
                }
                previousTimestamp = frame.timestampMillis();
                byte[] image = frame.encodedImage();
                out.writeLong(frame.timestampMillis());
                out.writeInt(image.length);
                out.write(image);
            }
            out.flush();
            byte[] result = bytes.toByteArray();
            if (result.length > VideoNotePayload.MAX_VIDEO_BYTES) throw new IllegalArgumentException("video container too large");
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("failed to encode video container", e);
        }
    }

    public static Video decode(byte[] data) {
        if (data == null || data.length < 22 || data.length > VideoNotePayload.MAX_VIDEO_BYTES) {
            throw new IllegalArgumentException("invalid video container");
        }
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
            if (in.readInt() != MAGIC || in.readUnsignedByte() != VERSION) {
                throw new IllegalArgumentException("unsupported video container");
            }
            int width = in.readUnsignedShort();
            int height = in.readUnsignedShort();
            int frameRate = in.readUnsignedByte();
            long duration = in.readLong();
            int frameCount = in.readInt();
            if (frameCount < 1 || frameCount > MAX_FRAMES) throw new IllegalArgumentException("invalid frame count");

            List<Frame> frames = new ArrayList<>(frameCount);
            long previousTimestamp = -1;
            for (int i = 0; i < frameCount; i++) {
                long timestamp = in.readLong();
                int length = in.readInt();
                if (timestamp < 0 || timestamp < previousTimestamp || length < 1 || length > MAX_FRAME_BYTES) {
                    throw new IllegalArgumentException("invalid frame");
                }
                if (length > in.available()) throw new IllegalArgumentException("truncated frame");
                byte[] image = new byte[length];
                in.readFully(image);
                frames.add(new Frame(timestamp, image));
                previousTimestamp = timestamp;
            }
            if (in.available() != 0) throw new IllegalArgumentException("trailing video data");
            return new Video(width, height, frameRate, duration, frames);
        } catch (IOException e) {
            throw new IllegalArgumentException("malformed video container", e);
        }
    }

    public static boolean isContainer(byte[] data) {
        if (data == null || data.length < 5) return false;
        return (data[0] & 0xFF) == 0x54
                && (data[1] & 0xFF) == 0x47
                && (data[2] & 0xFF) == 0x56
                && (data[3] & 0xFF) == 0x31
                && (data[4] & 0xFF) == VERSION;
    }
}
