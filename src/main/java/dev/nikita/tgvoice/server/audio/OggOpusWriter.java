package dev.nikita.tgvoice.server.audio;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/** Minimal Ogg Opus muxer for Telegram-compatible .ogg voice messages. */
public final class OggOpusWriter {
    private static final int SERIAL = 0x5447564D; // deterministic stream id for one message
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private int sequence;
    private long granulePosition;

    public OggOpusWriter() {
        writeHeaders();
    }

    public void addPacket(byte[] opusPacket, int samples) {
        if (opusPacket == null || opusPacket.length == 0) {
            throw new IllegalArgumentException("Opus packet is empty");
        }
        if (samples < 0) throw new IllegalArgumentException("samples must be >= 0");
        granulePosition += samples;
        writePage(opusPacket, granulePosition, 0);
    }

    public byte[] finish() {
        writePage(new byte[0], granulePosition, 0x04); // EOS
        return out.toByteArray();
    }

    private void writeHeaders() {
        ByteBuffer head = ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN);
        head.put("OpusHead".getBytes(StandardCharsets.US_ASCII));
        head.put((byte) 1);
        head.put((byte) 1); // mono
        head.putShort((short) 0); // pre-skip; encoder delay is not exposed by the PV API
        head.putInt(48_000);
        head.putShort((short) 0);
        head.put((byte) 0);
        writePage(head.array(), 0, 0x02); // BOS

        ByteBuffer tags = ByteBuffer.allocate(16 + 8 + 8).order(ByteOrder.LITTLE_ENDIAN);
        tags.put("OpusTags".getBytes(StandardCharsets.US_ASCII));
        tags.putInt(8);
        tags.put("TGVoice".getBytes(StandardCharsets.US_ASCII));
        tags.putInt(0);
        writePage(tags.array(), 0, 0);
    }

    private void writePage(byte[] packet, long granule, int flags) {
        int segmentCount = Math.max(1, (packet.length + 254) / 255);
        if (segmentCount > 255) {
            throw new IllegalArgumentException("Opus packet is too large for one Ogg page");
        }
        byte[] lacing = new byte[segmentCount];
        int remaining = packet.length;
        for (int i = 0; i < segmentCount; i++) {
            int size = Math.min(255, remaining);
            lacing[i] = (byte) size;
            remaining -= size;
        }

        ByteArrayOutputStream page = new ByteArrayOutputStream(27 + segmentCount + packet.length);
        page.writeBytes("OggS".getBytes(StandardCharsets.US_ASCII));
        page.write(0); // version
        page.write(flags);
        writeLongLE(page, granule);
        writeIntLE(page, SERIAL);
        writeIntLE(page, sequence++);
        writeIntLE(page, 0); // CRC placeholder
        page.write(segmentCount);
        page.writeBytes(lacing);
        page.writeBytes(packet);

        byte[] bytes = page.toByteArray();
        CRC32 crc = new CRC32();
        crc.update(bytes, 0, bytes.length);
        int value = (int) crc.getValue();
        bytes[22] = (byte) value;
        bytes[23] = (byte) (value >>> 8);
        bytes[24] = (byte) (value >>> 16);
        bytes[25] = (byte) (value >>> 24);
        out.writeBytes(bytes);
    }

    private static void writeIntLE(ByteArrayOutputStream out, int value) {
        out.write(value);
        out.write(value >>> 8);
        out.write(value >>> 16);
        out.write(value >>> 24);
    }

    private static void writeLongLE(ByteArrayOutputStream out, long value) {
        for (int i = 0; i < 8; i++) out.write((int) (value >>> (8 * i)));
    }
}
