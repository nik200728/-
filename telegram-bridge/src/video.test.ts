import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { MAX_VIDEO_BYTES, MAX_VIDEO_DIMENSION, MAX_VIDEO_DURATION_MS, MAX_VIDEO_FPS, mp4ToTgv1, tgv1ToMp4, validateVideoInput } from "./video.js";

test("accepts a bounded video payload", () => {
  const result = validateVideoInput({ videoBase64: Buffer.from("TGV1-test").toString("base64"), durationMs: 1000, width: MAX_VIDEO_DIMENSION, height: MAX_VIDEO_DIMENSION, frameRate: MAX_VIDEO_FPS });
  assert.equal(result.durationMs, 1000); assert.equal(result.width, MAX_VIDEO_DIMENSION); assert.equal(result.height, MAX_VIDEO_DIMENSION); assert.equal(result.frameRate, MAX_VIDEO_FPS); assert.equal(result.video.length, 9);
});

test("rejects malformed base64 video payloads", () => {
  const base = { durationMs: 1000, width: 512, height: 512, frameRate: 30 };
  for (const videoBase64 of ["not-base64", "YQ", "YQ===", "YQ==junk", "AA==AA=="]) assert.throws(() => validateVideoInput({ ...base, videoBase64 }), /invalid_video_base64/);
});

test("rejects oversized payloads", () => {
  const oversized = Buffer.alloc(MAX_VIDEO_BYTES + 1).toString("base64");
  assert.throws(() => validateVideoInput({ videoBase64: oversized, durationMs: 1000, width: 512, height: 512, frameRate: 30 }), /video_too_large/);
});

test("rejects invalid duration and frame rate", () => {
  const base = { videoBase64: Buffer.from("frame").toString("base64"), width: 512, height: 512, frameRate: 30 };
  assert.throws(() => validateVideoInput({ ...base, durationMs: MAX_VIDEO_DURATION_MS + 1 }), /invalid_duration/);
  assert.throws(() => validateVideoInput({ ...base, durationMs: 1000, frameRate: MAX_VIDEO_FPS + 1 }), /invalid_frame_rate/);
  assert.throws(() => validateVideoInput({ ...base, durationMs: 1000, frameRate: Number.NaN }), /invalid_frame_rate/);
  assert.throws(() => validateVideoInput({ ...base, durationMs: Infinity }), /invalid_duration/);
  assert.throws(() => validateVideoInput({ ...base, durationMs: 1000, frameRate: -Infinity }), /invalid_frame_rate/);
});

test("rejects dimensions above the video-note limit", () => {
  assert.throws(() => validateVideoInput({ videoBase64: Buffer.from("frame").toString("base64"), durationMs: 1000, width: MAX_VIDEO_DIMENSION + 1, height: 512, frameRate: 30 }), /invalid_width/);
  assert.throws(() => validateVideoInput({ videoBase64: Buffer.from("frame").toString("base64"), durationMs: 1000, width: 512, height: -Infinity, frameRate: 30 }), /invalid_height/);
});

test("rejects malformed TGV1 before invoking ffmpeg", async () => {
  const malformed = Buffer.alloc(23); malformed.writeInt32BE(0x12345678, 0);
  await assert.rejects(() => tgv1ToMp4(malformed), /invalid_video_container_magic/);
});

test("rejects truncated TGV1 frame metadata", async () => {
  const data = Buffer.alloc(23 + 12); data.writeInt32BE(0x54475631, 0); data.writeUInt8(1, 4); data.writeUInt16BE(512, 5); data.writeUInt16BE(512, 7); data.writeInt32BE(30, 9); data.writeBigInt64BE(1000n, 13); data.writeUInt16BE(1, 21); data.writeBigInt64BE(500n, 23); data.writeInt32BE(10, 31);
  await assert.rejects(() => tgv1ToMp4(data), /invalid_frame_length/);
});

test("rejects trailing bytes in an otherwise valid TGV1 container", async () => {
  const data = Buffer.alloc(23 + 12 + 1 + 1); data.writeInt32BE(0x54475631, 0); data.writeUInt8(1, 4); data.writeUInt16BE(1, 5); data.writeUInt16BE(1, 7); data.writeInt32BE(1, 9); data.writeBigInt64BE(1000n, 13); data.writeUInt16BE(1, 21); data.writeBigInt64BE(500n, 23); data.writeInt32BE(1, 31); data.writeUInt8(0xff, 35); data.writeUInt8(0xee, 36);
  await assert.rejects(() => tgv1ToMp4(data), /trailing_video_container_bytes/);
});

test("rejects Telegram videos longer than 60 seconds before ffmpeg extraction", async () => {
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), "tgvoice-test-")); const ffprobePath = path.join(dir, "ffprobe"); const ffmpegPath = path.join(dir, "ffmpeg"); const previousProbe = process.env.FFPROBE_PATH; const previousFfmpeg = process.env.FFMPEG_PATH;
  try {
    await fs.writeFile(ffprobePath, "#!/bin/sh\nprintf '61.0\\n'\n"); await fs.writeFile(ffmpegPath, "#!/bin/sh\necho 'ffmpeg should not be called' >&2\nexit 99\n"); await fs.chmod(ffprobePath, 0o755); await fs.chmod(ffmpegPath, 0o755);
    process.env.FFPROBE_PATH = ffprobePath; process.env.FFMPEG_PATH = ffmpegPath;
    await assert.rejects(() => mp4ToTgv1(Buffer.from("fake-mp4")), /invalid_telegram_video_duration/);
  } finally {
    if (previousProbe === undefined) delete process.env.FFPROBE_PATH; else process.env.FFPROBE_PATH = previousProbe;
    if (previousFfmpeg === undefined) delete process.env.FFMPEG_PATH; else process.env.FFMPEG_PATH = previousFfmpeg;
    await fs.rm(dir, { recursive: true, force: true });
  }
});

test("round-trips a small TGV1 video through ffmpeg", async () => {
  const jpeg = Buffer.from("/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAYEBQYFBAYGBQYHBwYIChAKCgkJChQODwwQFxQYGBcUFhYaHSUfGhsjHBYWICwgIyYnKSopGR8tMC0oMCUoKSj/2wBDAQcHBwoIChMKChMoGhYaKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCj/wAARCAACAAIDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/9oADAMBAAIRAxEAPwDz6iiivAPqD//Z", "base64");
  const frame = jpeg; const data = Buffer.alloc(23 + 12 + frame.length + 12 + frame.length); let p = 0;
  data.writeInt32BE(0x54475631, p); p += 4; data.writeUInt8(1, p++); data.writeUInt16BE(2, p); p += 2; data.writeUInt16BE(2, p); p += 2; data.writeInt32BE(2, p); p += 4; data.writeBigInt64BE(1000n, p); p += 8; data.writeUInt16BE(2, p); p += 2;
  data.writeBigInt64BE(250n, p); p += 8; data.writeInt32BE(frame.length, p); p += 4; frame.copy(data, p); p += frame.length;
  data.writeBigInt64BE(750n, p); p += 8; data.writeInt32BE(frame.length, p); p += 4; frame.copy(data, p);

  const mp4 = await tgv1ToMp4(data); assert.ok(mp4.length > 0); assert.ok(mp4.length <= 50 * 1024 * 1024);
  const roundTrip = await mp4ToTgv1(mp4); assert.equal(roundTrip.width, 512); assert.equal(roundTrip.height, 512); assert.equal(roundTrip.frameRate >= 1, true); assert.equal(roundTrip.durationMs, 1000); assert.ok(roundTrip.video.length > 23); assert.ok(roundTrip.video.length <= MAX_VIDEO_BYTES);
});
