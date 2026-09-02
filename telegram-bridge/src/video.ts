import crypto from "node:crypto";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { spawn } from "node:child_process";

export type VideoInboxMessage = {
  messageId: string; minecraftUuid: string; telegramUserId: string; chatId: string;
  durationMs: number; width: number; height: number; frameRate: number;
  videoBase64: string; createdAt: number;
};

export const MAX_VIDEO_BYTES = 8 * 1024 * 1024;
export const MAX_VIDEO_DURATION_MS = 60_000;
export const MAX_VIDEO_DIMENSION = 512;
export const MAX_VIDEO_FPS = 30;
export const MAX_TELEGRAM_VIDEO_BYTES = 50 * 1024 * 1024;

const MAGIC = 0x54475631;
const VERSION = 1;
const HEADER_BYTES = 23;
const MAX_FRAMES = 1800;
const MAX_FRAME_BYTES = 512 * 1024;

type Frame = { timestampMs: number; image: Buffer };
type Tgv1Video = { width: number; height: number; frameRate: number; durationMs: number; frames: Frame[] };

export function validateVideoInput(body: any) {
  if (typeof body.videoBase64 !== "string") throw new Error("videoBase64 required");
  const video = Buffer.from(body.videoBase64, "base64");
  if (!video.length || video.length > MAX_VIDEO_BYTES) throw new Error("video_too_large");
  const durationMs = Number(body.durationMs), width = Number(body.width), height = Number(body.height), frameRate = Number(body.frameRate);
  if (!Number.isInteger(durationMs) || durationMs < 1 || durationMs > MAX_VIDEO_DURATION_MS) throw new Error("invalid_duration");
  if (!Number.isInteger(width) || width < 1 || width > MAX_VIDEO_DIMENSION) throw new Error("invalid_width");
  if (!Number.isInteger(height) || height < 1 || height > MAX_VIDEO_DIMENSION) throw new Error("invalid_height");
  if (!Number.isInteger(frameRate) || frameRate < 1 || frameRate > MAX_VIDEO_FPS) throw new Error("invalid_frame_rate");
  return { durationMs, width, height, frameRate, video };
}

function decodeTgv1(data: Buffer): Tgv1Video {
  if (data.length < HEADER_BYTES || data.length > MAX_VIDEO_BYTES) throw new Error("invalid_video_container_size");
  let p = 0;
  if (data.readInt32BE(p) !== MAGIC) throw new Error("invalid_video_container_magic"); p += 4;
  if (data.readUInt8(p++) !== VERSION) throw new Error("unsupported_video_container_version");
  const width = data.readUInt16BE(p); p += 2;
  const height = data.readUInt16BE(p); p += 2;
  const frameRate = data.readInt32BE(p); p += 4;
  const durationMs = Number(data.readBigInt64BE(p)); p += 8;
  const count = data.readUInt16BE(p); p += 2;
  if (width < 1 || width > MAX_VIDEO_DIMENSION || height < 1 || height > MAX_VIDEO_DIMENSION) throw new Error("invalid_video_dimensions");
  if (frameRate < 1 || frameRate > MAX_VIDEO_FPS || durationMs < 1 || durationMs > MAX_VIDEO_DURATION_MS) throw new Error("invalid_video_metadata");
  if (count < 1 || count > MAX_FRAMES) throw new Error("invalid_frame_count");
  const frames: Frame[] = []; let previous = -1;
  for (let i = 0; i < count; i++) {
    if (p + 12 > data.length) throw new Error("truncated_video_container");
    const timestampMs = Number(data.readBigInt64BE(p)); p += 8;
    const size = data.readInt32BE(p); p += 4;
    if (size < 1 || size > MAX_FRAME_BYTES || p + size > data.length) throw new Error("invalid_frame_length");
    if (timestampMs <= previous || timestampMs >= durationMs) throw new Error("invalid_frame_timestamps");
    frames.push({ timestampMs, image: Buffer.from(data.subarray(p, p + size)) }); p += size; previous = timestampMs;
  }
  if (p !== data.length) throw new Error("trailing_video_container_bytes");
  return { width, height, frameRate, durationMs, frames };
}

function encodeTgv1(video: Tgv1Video): Buffer {
  if (video.width < 1 || video.width > MAX_VIDEO_DIMENSION || video.height < 1 || video.height > MAX_VIDEO_DIMENSION) throw new Error("invalid_video_dimensions");
  if (video.frameRate < 1 || video.frameRate > MAX_VIDEO_FPS || video.durationMs < 1 || video.durationMs > MAX_VIDEO_DURATION_MS) throw new Error("invalid_video_metadata");
  if (!video.frames.length || video.frames.length > MAX_FRAMES) throw new Error("invalid_frame_count");
  const chunks: Buffer[] = []; const header = Buffer.alloc(HEADER_BYTES); let p = 0; let total = HEADER_BYTES; let previous = -1;
  header.writeInt32BE(MAGIC, p); p += 4; header.writeUInt8(VERSION, p++); header.writeUInt16BE(video.width, p); p += 2; header.writeUInt16BE(video.height, p); p += 2; header.writeInt32BE(video.frameRate, p); p += 4; header.writeBigInt64BE(BigInt(video.durationMs), p); p += 8; header.writeUInt16BE(video.frames.length, p); chunks.push(header);
  for (const frame of video.frames) {
    if (!Number.isInteger(frame.timestampMs) || frame.timestampMs <= previous || frame.timestampMs >= video.durationMs) throw new Error("invalid_frame_timestamps");
    if (!frame.image.length || frame.image.length > MAX_FRAME_BYTES) throw new Error("invalid_frame_length");
    const meta = Buffer.alloc(12); meta.writeBigInt64BE(BigInt(frame.timestampMs), 0); meta.writeInt32BE(frame.image.length, 8); chunks.push(meta, frame.image); total += 12 + frame.image.length; previous = frame.timestampMs;
    if (total > MAX_VIDEO_BYTES) throw new Error("encoded_video_too_large");
  }
  return Buffer.concat(chunks, total);
}

function runProcess(executable: string, args: string[], timeoutMs = 90_000): Promise<void> {
  return new Promise((resolve, reject) => {
    const child = spawn(executable, args, { stdio: ["ignore", "ignore", "pipe"] }); let stderr = "";
    const timer = setTimeout(() => { child.kill("SIGKILL"); reject(new Error("media_process_timeout")); }, timeoutMs);
    child.stderr.on("data", chunk => { stderr += chunk.toString(); if (stderr.length > 4096) stderr = stderr.slice(-4096); });
    child.once("error", error => { clearTimeout(timer); reject(new Error(`media_process_spawn_failed:${error.message}`)); });
    child.once("close", code => { clearTimeout(timer); code === 0 ? resolve() : reject(new Error(`media_process_failed:${code ?? "unknown"}:${stderr.trim()}`)); });
  });
}

const ffmpeg = () => process.env.FFMPEG_PATH ?? "ffmpeg";
const ffprobe = () => process.env.FFPROBE_PATH ?? "ffprobe";

export async function tgv1ToMp4(tgv1: Buffer): Promise<Buffer> {
  const video = decodeTgv1(tgv1); const dir = await fs.mkdtemp(path.join(os.tmpdir(), "tgvoice-out-"));
  try {
    const list = path.join(dir, "frames.txt"), output = path.join(dir, "video.mp4"); const paths: string[] = [];
    for (let i = 0; i < video.frames.length; i++) { const file = path.join(dir, `${String(i).padStart(5, "0")}.jpg`); await fs.writeFile(file, video.frames[i].image); paths.push(file); }
    const lines: string[] = [];
    for (let i = 0; i < paths.length; i++) { lines.push(`file '${paths[i].replaceAll("'", "'\\''")}'`); if (i + 1 < paths.length) lines.push(`duration ${Math.max(0.001, (video.frames[i + 1].timestampMs - video.frames[i].timestampMs) / 1000)}`); }
    lines.push(`file '${paths.at(-1)!.replaceAll("'", "'\\''")}'`); await fs.writeFile(list, lines.join("\n") + "\n");
    await runProcess(ffmpeg(), ["-hide_banner", "-loglevel", "error", "-nostdin", "-f", "concat", "-safe", "0", "-i", list, "-t", String(video.durationMs / 1000), "-vf", `scale=${video.width}:${video.height}:force_original_aspect_ratio=decrease,pad=${video.width}:${video.height}:(ow-iw)/2:(oh-ih)/2,format=yuv420p`, "-r", String(video.frameRate), "-c:v", "libx264", "-movflags", "+faststart", "-an", output]);
    const result = await fs.readFile(output); if (!result.length || result.length > MAX_TELEGRAM_VIDEO_BYTES) throw new Error("telegram_video_too_large"); return result;
  } finally { await fs.rm(dir, { recursive: true, force: true }); }
}

export async function mp4ToTgv1(mp4: Buffer) {
  if (!mp4.length || mp4.length > MAX_TELEGRAM_VIDEO_BYTES) throw new Error("telegram_video_too_large");
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), "tgvoice-in-"));
  try {
    const input = path.join(dir, "input.mp4"), pattern = path.join(dir, "frame-%05d.jpg"); await fs.writeFile(input, mp4);
    const durationMs = await probeDurationMs(input);
    await runProcess(ffmpeg(), ["-hide_banner", "-loglevel", "error", "-nostdin", "-i", input, "-t", "60", "-vf", `fps=${MAX_VIDEO_FPS},scale=${MAX_VIDEO_DIMENSION}:${MAX_VIDEO_DIMENSION}:force_original_aspect_ratio=decrease,pad=${MAX_VIDEO_DIMENSION}:${MAX_VIDEO_DIMENSION}:(ow-iw)/2:(oh-ih)/2`, "-q:v", "5", pattern]);
    const names = (await fs.readdir(dir)).filter(n => /^frame-\d{5}\.jpg$/.test(n)).sort(); if (!names.length || names.length > MAX_FRAMES) throw new Error("invalid_extracted_frame_count");
    const boundedDuration = Math.min(MAX_VIDEO_DURATION_MS, durationMs), fps = Math.min(MAX_VIDEO_FPS, Math.max(1, Math.round(names.length / Math.max(1, boundedDuration / 1000))));
    const interval = boundedDuration / (names.length + 1); const frames: Frame[] = [];
    for (let i = 0; i < names.length; i++) { const image = await fs.readFile(path.join(dir, names[i])); if (image.length > MAX_FRAME_BYTES) throw new Error("extracted_frame_too_large"); frames.push({ timestampMs: Math.max(1, Math.floor(interval * (i + 1))), image }); }
    return { video: encodeTgv1({ width: MAX_VIDEO_DIMENSION, height: MAX_VIDEO_DIMENSION, frameRate: fps, durationMs: boundedDuration, frames }), width: MAX_VIDEO_DIMENSION, height: MAX_VIDEO_DIMENSION, frameRate: fps, durationMs: boundedDuration };
  } finally { await fs.rm(dir, { recursive: true, force: true }); }
}

async function probeDurationMs(input: string): Promise<number> {
  return new Promise((resolve, reject) => {
    const child = spawn(ffprobe(), ["-v", "error", "-show_entries", "format=duration", "-of", "default=noprint_wrappers=1:nokey=1", input], { stdio: ["ignore", "pipe", "pipe"] }); let stdout = "", stderr = "";
    child.stdout.on("data", c => { stdout += c.toString(); }); child.stderr.on("data", c => { stderr += c.toString(); }); child.once("error", e => reject(new Error(`ffprobe_spawn_failed:${e.message}`))); child.once("close", code => { const seconds = Number(stdout.trim()); if (code === 0 && Number.isFinite(seconds) && seconds > 0) resolve(Math.max(1, Math.round(seconds * 1000))); else reject(new Error(`ffprobe_failed:${code ?? "unknown"}:${stderr.trim()}`)); });
  });
}

export async function sendTelegramVideoNote(telegram: (method: string, init?: RequestInit) => Promise<any>, chatId: string, video: Buffer, durationMs: number, length: number, messageId: string) {
  if (video.length === 0 || video.length > MAX_VIDEO_BYTES) throw new Error("video_too_large");
  if (durationMs < 1 || durationMs > MAX_VIDEO_DURATION_MS) throw new Error("invalid_duration");
  if (length < 1 || length > MAX_VIDEO_DIMENSION) throw new Error("invalid_length");
  const mp4 = await tgv1ToMp4(video); const form = new FormData(); form.set("chat_id", chatId); form.set("duration", String(Math.ceil(durationMs / 1000))); form.set("length", String(length)); form.set("caption", `Minecraft video note • ${messageId}`); form.set("video_note", new Blob([mp4.buffer.slice(mp4.byteOffset, mp4.byteOffset + mp4.byteLength) as ArrayBuffer], { type: "video/mp4" }), `${messageId}.mp4`);
  const result = await telegram("sendVideoNote", { method: "POST", body: form }); return result?.message_id ?? null;
}

export async function downloadTelegramVideoNote(telegram: (method: string, init?: RequestInit) => Promise<any>, message: any, botToken: string) {
  const note = message.video_note; if (!note?.file_id) return null; const duration = Number(note.duration ?? 0), length = Number(note.length ?? 0);
  if (!Number.isInteger(duration) || duration < 1 || duration > 60) throw new Error("invalid_telegram_video_duration"); if (!Number.isInteger(length) || length < 1 || length > MAX_VIDEO_DIMENSION) throw new Error("invalid_telegram_video_length");
  const file = await telegram("getFile", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ file_id: note.file_id }) }); const filePath = file?.file_path as string | undefined; if (!filePath) throw new Error("telegram_video_file_path_missing");
  const response = await fetch(`https://api.telegram.org/file/bot${botToken}/${filePath}`, { signal: AbortSignal.timeout(30_000) }); if (!response.ok) throw new Error(`telegram_video_file_download_failed:${response.status}`); const mp4 = Buffer.from(await response.arrayBuffer());
  if (!mp4.length || mp4.length > MAX_TELEGRAM_VIDEO_BYTES) throw new Error("inbound_video_too_large"); const converted = await mp4ToTgv1(mp4);
  return { messageId: `tg-video-${message.message_id}-${crypto.randomUUID()}`, durationMs: converted.durationMs, width: converted.width, height: converted.height, frameRate: converted.frameRate, video: converted.video };
}
