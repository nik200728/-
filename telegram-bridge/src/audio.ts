import crypto from "node:crypto";

const MAX_AUDIO_BYTES = 2 * 1024 * 1024;

export function createAudioId(): string {
  return crypto.randomUUID();
}

export function validateAudio(buffer: Buffer): void {
  if (buffer.length === 0) throw new Error("empty audio");
  if (buffer.length > MAX_AUDIO_BYTES) throw new Error("audio exceeds 2 MiB limit");
}

/**
 * Telegram voice messages are normally OGG/Opus. This function deliberately does
 * not pretend to transcode data: transcoding belongs to the worker that has ffmpeg
 * or another verified Opus implementation installed.
 */
export function acceptOggOpus(buffer: Buffer): Buffer {
  validateAudio(buffer);
  if (buffer.length < 4 || buffer.subarray(0, 4).toString("ascii") !== "OggS") {
    throw new Error("expected OGG container");
  }
  return Buffer.from(buffer);
}
