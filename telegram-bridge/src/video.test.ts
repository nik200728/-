import assert from "node:assert/strict";
import test from "node:test";
import { MAX_VIDEO_BYTES, MAX_VIDEO_DIMENSION, MAX_VIDEO_DURATION_MS, MAX_VIDEO_FPS, tgv1ToMp4, validateVideoInput } from "./video.ts";

test("accepts a bounded video payload", () => {
  const result = validateVideoInput({
    videoBase64: Buffer.from("TGV1-test").toString("base64"),
    durationMs: 1000,
    width: MAX_VIDEO_DIMENSION,
    height: MAX_VIDEO_DIMENSION,
    frameRate: MAX_VIDEO_FPS,
  });
  assert.equal(result.durationMs, 1000);
  assert.equal(result.width, MAX_VIDEO_DIMENSION);
  assert.equal(result.height, MAX_VIDEO_DIMENSION);
  assert.equal(result.frameRate, MAX_VIDEO_FPS);
  assert.equal(result.video.length, 9);
});

test("rejects oversized payloads", () => {
  const oversized = Buffer.alloc(MAX_VIDEO_BYTES + 1).toString("base64");
  assert.throws(() => validateVideoInput({
    videoBase64: oversized,
    durationMs: 1000,
    width: 512,
    height: 512,
    frameRate: 30,
  }), /video_too_large/);
});

test("rejects invalid duration and frame rate", () => {
  const base = {
    videoBase64: Buffer.from("frame").toString("base64"),
    width: 512,
    height: 512,
    frameRate: 30,
  };
  assert.throws(() => validateVideoInput({ ...base, durationMs: MAX_VIDEO_DURATION_MS + 1 }), /invalid_duration/);
  assert.throws(() => validateVideoInput({ ...base, durationMs: 1000, frameRate: MAX_VIDEO_FPS + 1 }), /invalid_frame_rate/);
  assert.throws(() => validateVideoInput({ ...base, durationMs: 1000, frameRate: Number.NaN }), /invalid_frame_rate/);
});

test("rejects dimensions above the video-note limit", () => {
  assert.throws(() => validateVideoInput({
    videoBase64: Buffer.from("frame").toString("base64"),
    durationMs: 1000,
    width: MAX_VIDEO_DIMENSION + 1,
    height: 512,
    frameRate: 30,
  }), /invalid_width/);
});

test("rejects malformed TGV1 before invoking ffmpeg", async () => {
  const malformed = Buffer.alloc(23);
  malformed.writeInt32BE(0x12345678, 0);
  await assert.rejects(() => tgv1ToMp4(malformed), /invalid_video_container_magic/);
});

test("rejects truncated TGV1 frame metadata", async () => {
  const data = Buffer.alloc(23 + 12);
  data.writeInt32BE(0x54475631, 0);
  data.writeUInt8(1, 4);
  data.writeUInt16BE(512, 5);
  data.writeUInt16BE(512, 7);
  data.writeInt32BE(30, 9);
  data.writeBigInt64BE(1000n, 13);
  data.writeUInt16BE(1, 21);
  data.writeBigInt64BE(500n, 23);
  data.writeInt32BE(10, 31);
  await assert.rejects(() => tgv1ToMp4(data), /invalid_frame_length/);
});
