package dev.nikita.tgvoice.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Versioned transport container for local video-note frames.
 *
 * The container intentionally stores encoded image frames rather than pretending that
 * arbitrary bytes are an H.264/MP4 stream. A later capture/decoder implementation can
 * use the same stable container without changing the Minecraft network contract.
 */
public final class VideoNoteContainer {
    private static final int MAGIC = 0x54475631; // TGV1
    private static final int VERSION = 1;
    private static final int MAX_FRAMES = 1_800;
    private static final int MAX_FRAME_BYTES = 512 * 1024;
    private static final int HEADER_BYTES = 4 + 1 + 2 + 2 + 4 + 8 + 2;

    private VideoNoteContainer() {}

    public record Frame(long timestampMillis, byte[] encodedImage) {
        public Frame {
            if (timestampMillis < 0 || timestampMillis > VideoNotePayload.MAX_DURATION_MILLIS) {
                throw new IllegalArgumentException("invalid frame timestamp");
            }
            if (encodedImage == null || encodedImage.length == 0 || encodedImage.length > MAX_FRAME_BYTES) {
                throw new IllegalArgumentException("invalid encoded frame");
            }
            encodedImage = Arrays.copyOf(encodedImage, encodedImage.length);
        }

        @Override
        public byte[] encodedImage() {
            return Arrays.copyOf(encodedImage, encodedImage.length);
        }
    }

    public record Video(int width, int height, int frameRate, long durationMillis, List<Frame> frames) {
        public Video {
            if (width < 1 || width > VideoNotePayload.MAX_DIMENSION
                    || height < 1 || height > VideoNotePayload.MAX_DIMENSION) {
                throw new IllegalArgumentException("invalid video dimensions");
            }
            if (frameRate < 1 || frameRate > VideoNotePayload.MAX_FRAME_RATE) {
                throw new IllegalArgumentException("invalid frame rate");
            }
            if (durationMillis < 1 || durationMillis > VideoNotePayload.MAX_DURATION_MILLIS) {
                throw new IllegalArgumentException("invalid duration");
            }
            if (frames == null || frames.isEmpty() || frames.size() > MAX_FRAMES) {
                throw new IllegalArgumentException("invalid frame count");
            }
            long previous = -1;
            for (Frame frame : frames) {
                if (frame.timestampMillis() <= previous || frame.timestampMillis() >= durationMillis) {
                    throw new IllegalArgumentException("frame timestamps must be strictly increasing and inside duration");
                }
                previous = frame.timestampMillis();
            }
            frames = List.copyOf(frames);
        }
    }

    public static byte[] encode(Video video) {
        if (video == null) throw new IllegalArgumentException("video is required");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(MAGIC);
            out.writeByte(VERSION);
            out.writeShort(video.width());
            out.writeShort(video.height());
            out.writeInt(video.frameRate());
            out.writeLong(video.durationMillis());
            out.writeShort(video.frames().size());
            for (Frame frame : video.frames()) {
                out.writeLong(frame.timestampMillis());
                out.writeInt(frame.encodedImage().length);
                out.write(frame.encodedImage());
            }
            out.flush();
            byte[] result = bytes.toByteArray();
            if (result.length > VideoNotePayload.MAX_VIDEO_BYTES) {
                throw new IllegalArgumentException("encoded video is too large");
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("failed to encode video container", exception);
        }
    }

    public static Video decode(byte[] data) {
        if (data == null || data.length < HEADER_BYTES || data.length > VideoNotePayload.MAX_VIDEO_BYTES) {
            throw new IllegalArgumentException("invalid video container size");
        }
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
            if (in.readInt() != MAGIC) throw new IllegalArgumentException("invalid video container magic");
            if (in.readUnsignedByte() != VERSION) throw new IllegalArgumentException("unsupported video container version");

            int width = in.readUnsignedShort();
            int height = in.readUnsignedShort();
            int frameRate = in.readInt();
            long durationMillis = in.readLong();
            int frameCount = in.readUnsignedShort();

            if (frameCount < 1 || frameCount > MAX_FRAMES) {
                throw new IllegalArgumentException("invalid frame count");
            }
            List<Frame> frames = new java.util.ArrayList<>(frameCount);
            long previous = -1;
            for (int i = 0; i < frameCount; i++) {
                long timestamp = in.readLong();
                int frameLength = in.readInt();
                if (frameLength < 1 || frameLength > MAX_FRAME_BYTES || frameLength > in.available()) {
                    throw new IllegalArgumentException("invalid frame length");
                }
                byte[] image = new byte[frameLength];
                in.readFully(image);
                if (timestamp <= previous || timestamp >= durationMillis) {
                    throw new IllegalArgumentException("invalid frame timestamps");
                }
                previous = timestamp;
                frames.add(new Frame(timestamp, image));
            }
            if (in.available() != 0) throw new IllegalArgumentException("trailing bytes in video container");
            return new Video(width, height, frameRate, durationMillis, frames);
        } catch (EOFException exception) {
            throw new IllegalArgumentException("truncated video container", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("failed to decode video container", exception);
        }
    }
}
