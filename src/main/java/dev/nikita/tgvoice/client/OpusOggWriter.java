package dev.nikita.tgvoice.client;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
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
        opusHead[8] = 1; // OpusHead version
        opusHead[9] = 1; // mono
        putLe16(opusHead, 10, 0); // pre-skip
        putLe32(opusHead, 12, SAMPLE_RATE);
        putLe16(opusHead, 16, 0); // output gain
        opusHead[18] = 0; // mono channel mapping family
        writePage(out, serial, sequence++, 0, 0x02, new byte[][]{opusHead});

        byte[] vendor = "tgvoice".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream tags = new ByteArrayOutputStream();
        tags.writeBytes("OpusTags".getBytes(StandardCharsets.US_ASCII));
        writeLe32(tags, vendor.length);
        tags.writeBytes(vendor);
        writeLe32(tags, 0); // user comment count
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
        ByteArrayOutputStream page = new ByteArrayOutputStream();
        page.writeBytes("OggS".getBytes(StandardCharsets.US_ASCII));
        page.write(0); // stream structure version
        page.write(flags);
        writeLe64(page, granulePosition);
        writeLe32(page, serial);
        writeLe32(page, sequence);
        writeLe32(page, 0); // CRC placeholder

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (byte[] packet : packets) {
            if (packet == null || packet.length == 0) {
                throw new IllegalArgumentException("Empty Opus packet");
            }
            int remaining = packet.length;
            int offset = 0;
            while (remaining >= 255) {
                body.write(255);
                body.write(packet, offset, 255);
                offset += 255;
                remaining -= 255;
            }
            body.write(remaining);
            body.write(packet, offset, remaining);
            if (packet.length % 255 == 0) {
                body.write(0);
            }
        }

        int segmentCount = 0;
        int pos = 27;
        while (pos < page.size()) {
            // This writer keeps one packet per page, so lacing values are rebuilt below.
            break;
        }
        byte[] packetData = body.toByteArray();
        ByteArrayOutputStream segments = new ByteArrayOutputStream();
        for (byte[] packet : packets) {
            int remaining = packet.length;
            while (remaining >= 255) {
                segments.write(255);
                segmentCount++;
                remaining -= 255;
            }
            segments.write(remaining);
            segmentCount++;
            if (packet.length % 255 == 0) {
                segments.write(0);
                segmentCount++;
            }
        }

        byte[] header = page.toByteArray();
        header = java.util.Arrays.copyOf(header, 27 + segmentCount);
        header[26] = (byte) segmentCount;
        System.arraycopy(segments.toByteArray(), 0, header, 27, segmentCount);
        byte[] full = new byte[header.length + packetData.length];
        System.arraycopy(header, 0, full, 0, header.length);
        System.arraycopy(packetData, 0, full, header.length, packetData.length);

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

    private static void writeLe16(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
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
