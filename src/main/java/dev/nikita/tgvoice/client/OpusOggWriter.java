package dev.nikita.tgvoice.client;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Minimal Ogg Opus container writer for Telegram voice-message payloads. */
public final class OpusOggWriter {
    private static final int SAMPLE_RATE = 48_000;

    private OpusOggWriter() {
    }

    public static byte[] write(List<byte[]> opusPackets, int sampleCount) {
        if (opusPackets == null || opusPackets.isEmpty()) {
            throw new IllegalArgumentException("At least one Opus packet is required");
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int serial = UUID.randomUUID().hashCode();
        int sequence = 0;

        byte[] opusHead = new byte[19];
        System.arraycopy("OpusHead".getBytes(StandardCharsets.US_ASCII), 0, opusHead, 0, 8);
        opusHead[8] = 1;
        opusHead[9] = 1;
        putLe16(opusHead, 10, 0);
        putLe32(opusHead, 12, SAMPLE_RATE);
        putLe16(opusHead, 16, 0);
        opusHead[18] = 0;
        writePage(out, serial, sequence++, 0, 0x02, new byte[][]{opusHead});

        byte[] vendor = "tgvoice".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream tags = new ByteArrayOutputStream();
        tags.writeBytes("OpusTags".getBytes(StandardCharsets.US_ASCII));
        writeLe32(tags, vendor.length);
        tags.writeBytes(vendor);
        writeLe32(tags, 0);
        writePage(out, serial, sequence++, 0, 0x00, new byte[][]{tags.toByteArray()});

        int granule = 0;
        for (int i = 0; i < opusPackets.size(); i++) {
            byte[] packet = opusPackets.get(i);
            granule += 960;
            if (i == opusPackets.size() - 1 && sampleCount > 0) {
                granule = sampleCount;
            }
            int flags = i == opusPackets.size() - 1 ? 0x04 : 0x00;
            writePage(out, serial, sequence++, granule, flags, new byte[][]{packet});
        }
        return out.toByteArray();
    }

    private static void writePage(ByteArrayOutputStream out, int serial, int sequence,
                                  long granulePosition, int flags, byte[][] packets) {
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        header.writeBytes("OggS".getBytes(StandardCharsets.US_ASCII));
        header.write(0);
        header.write(flags);
        writeLe64(header, granulePosition);
        writeLe32(header, serial);
        writeLe32(header, sequence);
        writeLe32(header, 0);

        ByteArrayOutputStream segments = new ByteArrayOutputStream();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        int segmentCount = 0;

        for (byte[] packet : packets) {
            if (packet == null || packet.length == 0) {
                throw new IllegalArgumentException("Empty Opus packet");
            }
            int remaining = packet.length;
            int offset = 0;
            while (remaining >= 255) {
                segments.write(255);
                segmentCount++;
                body.write(packet, offset, 255);
                offset += 255;
                remaining -= 255;
            }
            segments.write(remaining);
            segmentCount++;
            body.write(packet, offset, remaining);
            if (packet.length % 255 == 0) {
                // A zero lacing value terminates a packet whose size is an exact multiple of 255.
                segments.write(0);
                segmentCount++;
            }
        }

        byte[] pageHeader = Arrays.copyOf(header.toByteArray(), 27 + segmentCount);
        pageHeader[26] = (byte) segmentCount;
        System.arraycopy(segments.toByteArray(), 0, pageHeader, 27, segmentCount);

        byte[] packetData = body.toByteArray();
        byte[] full = new byte[pageHeader.length + packetData.length];
        System.arraycopy(pageHeader, 0, full, 0, pageHeader.length);
        System.arraycopy(packetData, 0, full, pageHeader.length, packetData.length);

        int crc = oggCrc(full);
        full[22] = (byte) crc;
        full[23] = (byte) (crc >>> 8);
        full[24] = (byte) (crc >>> 16);
        full[25] = (byte) (crc >>> 24);
        out.writeBytes(full);
    }

    private static int oggCrc(byte[] data) {
        int crc = 0;
        for (byte value : data) {
            crc ^= (value & 0xFF) << 24;
            for (int i = 0; i < 8; i++) {
                crc = (crc & 0x80000000) != 0
                        ? (crc << 1) ^ 0x04C11DB7
                        : crc << 1;
            }
        }
        return crc;
    }

    private static void putLe16(byte[] data, int offset, int value) {
        data[offset] = (byte) value;
        data[offset + 1] = (byte) (value >>> 8);
    }

    private static void putLe32(byte[] data, int offset, int value) {
        data[offset] = (byte) value;
        data[offset + 1] = (byte) (value >>> 8);
        data[offset + 2] = (byte) (value >>> 16);
        data[offset + 3] = (byte) (value >>> 24);
    }

    private static void writeLe32(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 24) & 0xFF);
    }

    private static void writeLe64(ByteArrayOutputStream out, long value) {
        for (int i = 0; i < 8; i++) {
            out.write((int) (value >>> (i * 8)) & 0xFF);
        }
    }
}
