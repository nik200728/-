import crypto from "node:crypto";

export type VideoInboxMessage = {
  messageId: string;
  minecraftUuid: string;
  telegramUserId: string;
  chatId: string;
  durationMs: number;
  width: number;
  height: number;
  frameRate: number;
  videoBase64: string;
  createdAt: number;
};

export const MAX_VIDEO_BYTES = 8 * 1024 * 1024;
export const MAX_VIDEO_DURATION_MS = 60_000;
export const MAX_VIDEO_DIMENSION = 512;
export const MAX_VIDEO_FPS = 30;

export function validateVideoInput(body: any): { durationMs: number; width: number; height: number; frameRate: number; video: Buffer } {
  if (typeof body.videoBase64 !== "string") throw new Error("videoBase64 required");
  const video = Buffer.from(body.videoBase64, "base64");
  if (video.length === 0 || video.length > MAX_VIDEO_BYTES) throw new Error("video_too_large");

  const durationMs = Number(body.durationMs);
  const width = Number(body.width);
  const height = Number(body.height);
  const frameRate = Number(body.frameRate);
  if (!Number.isInteger(durationMs) || durationMs < 1 || durationMs > MAX_VIDEO_DURATION_MS) throw new Error("invalid_duration");
  if (!Number.isInteger(width) || width < 1 || width > MAX_VIDEO_DIMENSION) throw new Error("invalid_width");
  if (!Number.isInteger(height) || height < 1 || height > MAX_VIDEO_DIMENSION) throw new Error("invalid_height");
  if (!Number.isInteger(frameRate) || frameRate < 1 || frameRate > MAX_VIDEO_FPS) throw new Error("invalid_frame_rate");
  return { durationMs, width, height, frameRate, video };
}

export async function sendTelegramVideoNote(telegram: (method: string, init?: RequestInit) => Promise<any>, chatId: string, video: Buffer, durationMs: number, length: number, messageId: string) {
  if (video.length === 0 || video.length > MAX_VIDEO_BYTES) throw new Error("video_too_large");
  if (durationMs < 1 || durationMs > MAX_VIDEO_DURATION_MS) throw new Error("invalid_duration");
  if (length < 1 || length > MAX_VIDEO_DIMENSION) throw new Error("invalid_length");

  const form = new FormData();
  form.set("chat_id", chatId);
  form.set("duration", String(Math.ceil(durationMs / 1000)));
  form.set("length", String(length));
  form.set("caption", `Minecraft video note • ${messageId}`);
  form.set("video_note", new Blob([video.buffer.slice(video.byteOffset, video.byteOffset + video.byteLength) as ArrayBuffer], { type: "video/mp4" }), `${messageId}.mp4`);
  const result = await telegram("sendVideoNote", { method: "POST", body: form });
  return result?.message_id ?? null;
}

export async function downloadTelegramVideoNote(telegram: (method: string, init?: RequestInit) => Promise<any>, message: any, botToken: string) {
  const videoNote = message.video_note;
  if (!videoNote?.file_id) return null;
  const durationSeconds = Number(videoNote.duration ?? 0);
  const length = Number(videoNote.length ?? 0);
  if (!Number.isInteger(durationSeconds) || durationSeconds < 1 || durationSeconds > 60) throw new Error("invalid_telegram_video_duration");
  if (!Number.isInteger(length) || length < 1 || length > MAX_VIDEO_DIMENSION) throw new Error("invalid_telegram_video_length");

  const file = await telegram("getFile", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ file_id: videoNote.file_id })
  });
  const filePath = file?.file_path as string | undefined;
  if (!filePath) throw new Error("telegram_video_file_path_missing");

  const response = await fetch(`https://api.telegram.org/file/bot${botToken}/${filePath}`, { signal: AbortSignal.timeout(30_000) });
  if (!response.ok) throw new Error(`telegram_video_file_download_failed:${response.status}`);
  const video = Buffer.from(await response.arrayBuffer());
  if (video.length === 0 || video.length > MAX_VIDEO_BYTES) throw new Error("inbound_video_too_large");

  return {
    messageId: `tg-video-${message.message_id}-${crypto.randomUUID()}`,
    durationMs: durationSeconds * 1000,
    width: length,
    height: length,
    frameRate: 0,
    video
  };
}
