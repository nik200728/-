import crypto from "node:crypto";
import fs from "node:fs";
import http from "node:http";
import path from "node:path";
import { downloadTelegramVideoNote, sendTelegramVideoNote, validateVideoInput } from "./video.ts";

const PORT = Number(process.env.PORT ?? 8080);
const BRIDGE_TOKEN = process.env.BRIDGE_TOKEN ?? "";
const TELEGRAM_BOT_TOKEN = process.env.TELEGRAM_BOT_TOKEN ?? "";
const TELEGRAM_POLL_MS = Number(process.env.TELEGRAM_POLL_MS ?? 1500);
const DATA_FILE = process.env.BRIDGE_DATA_FILE ?? path.resolve(process.cwd(), "data", "state.json");
if (BRIDGE_TOKEN.length < 32) throw new Error("BRIDGE_TOKEN must contain at least 32 characters");
if (!TELEGRAM_BOT_TOKEN) console.warn("TELEGRAM_BOT_TOKEN is not configured; Telegram polling is disabled");

type LinkCode = { minecraftUuid: string; code: string; expiresAt: number };
type Binding = { minecraftUuid: string; telegramUserId: string; chatId: string };
type InboxMessage = { messageId: string; minecraftUuid: string; telegramUserId: string; chatId: string; kind: "voice" | "video_note"; durationMs: number; audioBase64?: string; videoBase64?: string; width?: number; height?: number; frameRate?: number; createdAt: number };
type PersistedState = { links: LinkCode[]; bindings: Binding[]; inbox: InboxMessage[]; telegramOffset: number };

const links = new Map<string, LinkCode>(); const bindings = new Map<string, Binding>(); const inbox = new Map<string, InboxMessage[]>();
let telegramOffset = 0; let telegramPolling = false; let stateWriteScheduled = false;
function loadState() {
  try {
    const state = JSON.parse(fs.readFileSync(DATA_FILE, "utf8")) as Partial<PersistedState>;
    for (const link of state.links ?? []) if (typeof link.code === "string" && typeof link.minecraftUuid === "string" && link.expiresAt > Date.now()) links.set(link.code, link);
    for (const binding of state.bindings ?? []) if (binding.minecraftUuid && binding.telegramUserId && binding.chatId) bindings.set(binding.minecraftUuid, binding);
    for (const message of state.inbox ?? []) if (message.messageId && message.minecraftUuid && message.telegramUserId && message.chatId && message.kind) {
      const queue = inbox.get(message.minecraftUuid) ?? []; queue.push(message); inbox.set(message.minecraftUuid, queue);
    }
    if (Number.isInteger(state.telegramOffset) && state.telegramOffset >= 0) telegramOffset = state.telegramOffset;
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code !== "ENOENT") console.warn("Failed to load bridge state:", error);
  }
}
function persistState() {
  if (stateWriteScheduled) return;
  stateWriteScheduled = true;
  setImmediate(() => {
    stateWriteScheduled = false;
    const state: PersistedState = {
      links: [...links.values()], bindings: [...bindings.values()], inbox: [...inbox.values()].flat(), telegramOffset,
    };
    fs.mkdirSync(path.dirname(DATA_FILE), { recursive: true });
    const temporary = `${DATA_FILE}.tmp`;
    fs.writeFileSync(temporary, JSON.stringify(state), "utf8");
    fs.renameSync(temporary, DATA_FILE);
  });
}
function auth(req: http.IncomingMessage) {
  const token = req.headers.authorization?.replace(/^Bearer\s+/i, "") ?? "";
  return token === BRIDGE_TOKEN;
}
function json(res: http.ServerResponse, status: number, body: unknown) {
  const data = JSON.stringify(body);
  res.writeHead(status, { "content-type": "application/json; charset=utf-8", "content-length": Buffer.byteLength(data) });
  res.end(data);
}
function body(req: http.IncomingMessage): Promise<any> {
  return new Promise((resolve, reject) => {
    const chunks: Buffer[] = []; let size = 0;
    req.on("data", chunk => { size += chunk.length; if (size > 12 * 1024 * 1024) { reject(new Error("request_too_large")); req.destroy(); return; } chunks.push(chunk); });
    req.on("end", () => { try { resolve(JSON.parse(Buffer.concat(chunks).toString("utf8"))); } catch { reject(new Error("invalid_json")); } });
    req.on("error", reject);
  });
}
function randomCode() { return crypto.randomBytes(4).toString("hex").toUpperCase(); }
function bindingFor(uuid: string) { return bindings.get(uuid); }
function queueFor(uuid: string) { return inbox.get(uuid) ?? []; }
function enqueue(message: InboxMessage) {
  const queue = inbox.get(message.minecraftUuid) ?? [];
  queue.push(message);
  while (queue.length > 32) queue.shift();
  inbox.set(message.minecraftUuid, queue);
  persistState();
}

async function telegramApi(method: string, payload: Record<string, unknown>) {
  if (!TELEGRAM_BOT_TOKEN) throw new Error("telegram_not_configured");
  const response = await fetch(`https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/${method}`, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(payload) });
  if (!response.ok) throw new Error(`telegram_http_${response.status}`);
  const result = await response.json() as { ok?: boolean; result?: any; description?: string };
  if (!result.ok) throw new Error(result.description ?? "telegram_api_error");
  return result.result;
}

async function pollTelegram() {
  if (telegramPolling || !TELEGRAM_BOT_TOKEN) return;
  telegramPolling = true;
  try {
    const updates = await telegramApi("getUpdates", { offset: telegramOffset, timeout: Math.max(1, Math.floor(TELEGRAM_POLL_MS / 1000)), allowed_updates: ["message"] }) as any[];
    for (const update of updates) {
      telegramOffset = update.update_id + 1;
      const message = update.message;
      if (!message?.from || !message.chat) continue;
      const telegramUserId = String(message.from.id);
      const chatId = String(message.chat.id);
      const binding = [...bindings.values()].find(candidate => candidate.telegramUserId === telegramUserId && candidate.chatId === chatId);
      if (!binding) continue;
      if (message.voice) {
        const result = await downloadTelegramVideoNote(message.voice.file_id).catch(() => null);
        if (result) enqueue({ messageId: crypto.randomUUID(), minecraftUuid: binding.minecraftUuid, telegramUserId, chatId, kind: "video_note", durationMs: result.durationMs, videoBase64: Buffer.from(result.video).toString("base64"), width: result.width, height: result.height, frameRate: result.frameRate, createdAt: Date.now() });
      }
    }
    persistState();
  } catch (error) {
    console.warn("Telegram polling failed:", error);
  } finally {
    telegramPolling = false;
    setTimeout(pollTelegram, TELEGRAM_POLL_MS);
  }
}

loadState();
setTimeout(pollTelegram, TELEGRAM_POLL_MS);

const server = http.createServer(async (req, res) => {
  try {
    if (!auth(req)) return json(res, 401, { error: "unauthorized" });
    const url = new URL(req.url ?? "/", `http://${req.headers.host ?? "localhost"}`);
    if (req.method === "GET" && url.pathname === "/health") return json(res, 200, { ok: true });
    if (req.method === "POST" && url.pathname === "/link/create") {
      const input = await body(req); if (typeof input.minecraftUuid !== "string" || !input.minecraftUuid) return json(res, 400, { error: "invalid_uuid" });
      const code = randomCode(); links.set(code, { minecraftUuid: input.minecraftUuid, code, expiresAt: Date.now() + 5 * 60_000 }); persistState(); return json(res, 200, { code, expiresAt: links.get(code)!.expiresAt });
    }
    if (req.method === "POST" && url.pathname === "/link/confirm") {
      const input = await body(req); const link = typeof input.code === "string" ? links.get(input.code.toUpperCase()) : undefined;
      if (!link || link.expiresAt <= Date.now()) return json(res, 400, { error: "invalid_link_code" });
      if (typeof input.telegramUserId !== "string" || typeof input.chatId !== "string") return json(res, 400, { error: "invalid_binding" });
      bindings.set(link.minecraftUuid, { minecraftUuid: link.minecraftUuid, telegramUserId: input.telegramUserId, chatId: input.chatId }); links.delete(link.code); persistState(); return json(res, 200, { ok: true });
    }
    if (req.method === "POST" && url.pathname === "/video/send") {
      const input = await body(req); const binding = bindingFor(String(input.minecraftUuid ?? ""));
      if (!binding || String(input.telegramUserId ?? "") !== binding.telegramUserId) return json(res, 403, { error: "not_bound" });
      const validated = validateVideoInput(input);
      await sendTelegramVideoNote(binding.chatId, validated.video, validated.durationMs, validated.width, validated.height, validated.frameRate);
      return json(res, 200, { ok: true });
    }
    if (req.method === "GET" && url.pathname === "/video/inbox") {
      const uuid = url.searchParams.get("minecraftUuid") ?? ""; const binding = bindingFor(uuid);
      if (!binding) return json(res, 403, { error: "not_bound" });
      const messages = queueFor(uuid); inbox.set(uuid, []); persistState(); return json(res, 200, { messages });
    }
    return json(res, 404, { error: "not_found" });
  } catch (error) {
    const message = error instanceof Error ? error.message : "internal_error";
    const status = message === "request_too_large" ? 413 : message.startsWith("invalid_") ? 400 : 500;
    json(res, status, { error: message });
  }
});

server.listen(PORT, "0.0.0.0", () => console.log(`Telegram bridge listening on ${PORT}`));
