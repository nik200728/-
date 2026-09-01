import crypto from "node:crypto";
import http from "node:http";

const PORT = Number(process.env.PORT ?? 8080);
const BRIDGE_TOKEN = process.env.BRIDGE_TOKEN ?? "";
const TELEGRAM_BOT_TOKEN = process.env.TELEGRAM_BOT_TOKEN ?? "";

if (BRIDGE_TOKEN.length < 32) {
  throw new Error("BRIDGE_TOKEN must contain at least 32 characters");
}
if (!TELEGRAM_BOT_TOKEN) {
  console.warn("TELEGRAM_BOT_TOKEN is not configured; Telegram delivery is disabled");
}

type LinkCode = { minecraftUuid: string; code: string; expiresAt: number };
type Binding = { minecraftUuid: string; telegramUserId: string; chatId: string };
const links = new Map<string, LinkCode>();
const bindings = new Map<string, Binding>();

function authorized(req: http.IncomingMessage): boolean {
  const value = req.headers.authorization ?? "";
  if (!value.startsWith("Bearer ")) return false;
  const supplied = Buffer.from(value.slice(7));
  const expected = Buffer.from(BRIDGE_TOKEN);
  return supplied.length === expected.length && crypto.timingSafeEqual(supplied, expected);
}

function json(res: http.ServerResponse, status: number, body: unknown) {
  const data = JSON.stringify(body);
  res.writeHead(status, { "content-type": "application/json; charset=utf-8" });
  res.end(data);
}

function readBody(req: http.IncomingMessage): Promise<any> {
  return new Promise((resolve, reject) => {
    let body = "";
    let size = 0;
    req.on("data", chunk => {
      size += Buffer.byteLength(chunk);
      if (size > 8 * 1024 * 1024) {
        reject(new Error("request too large"));
        req.destroy();
        return;
      }
      body += chunk;
    });
    req.on("end", () => {
      try { resolve(body ? JSON.parse(body) : {}); }
      catch { reject(new Error("invalid json")); }
    });
    req.on("error", reject);
  });
}

function createLinkCode(minecraftUuid: string): LinkCode {
  const code = String(crypto.randomInt(100000, 1000000));
  const link = { minecraftUuid, code, expiresAt: Date.now() + 5 * 60_000 };
  links.set(code, link);
  return link;
}

async function sendTelegramVoice(chatId: string, audio: Buffer, durationMs: number, messageId: string) {
  if (!TELEGRAM_BOT_TOKEN) throw new Error("telegram_not_configured");
  if (audio.length > 2 * 1024 * 1024) throw new Error("audio_too_large");
  if (durationMs < 1 || durationMs > 120_000) throw new Error("invalid_duration");

  const form = new FormData();
  form.set("chat_id", chatId);
  form.set("caption", `Minecraft voice message • ${messageId}`);
  form.set("voice", new Blob([audio], { type: "audio/ogg" }), `${messageId}.ogg`);

  const response = await fetch(`https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendVoice`, {
    method: "POST",
    body: form,
  });
  const payload = await response.json() as { ok?: boolean; result?: { message_id?: number }; description?: string };
  if (!response.ok || !payload.ok) {
    throw new Error(`telegram_send_voice_failed:${payload.description ?? response.status}`);
  }
  return payload.result?.message_id ?? null;
}

const server = http.createServer(async (req, res) => {
  try {
    if (req.method === "GET" && req.url === "/health") return json(res, 200, { ok: true });
    if (!authorized(req)) return json(res, 401, { error: "unauthorized" });

    if (req.method === "POST" && req.url === "/v1/link/code") {
      const body = await readBody(req);
      if (typeof body.minecraftUuid !== "string") return json(res, 400, { error: "minecraftUuid required" });
      const link = createLinkCode(body.minecraftUuid);
      return json(res, 200, { code: link.code, expiresAt: link.expiresAt });
    }

    if (req.method === "POST" && req.url === "/v1/link/consume") {
      const body = await readBody(req);
      const link = links.get(String(body.code));
      if (!link || link.expiresAt < Date.now()) return json(res, 400, { error: "invalid_or_expired_code" });
      if (typeof body.telegramUserId !== "string" || typeof body.chatId !== "string") {
        return json(res, 400, { error: "telegramUserId and chatId required" });
      }
      links.delete(link.code);
      bindings.set(link.minecraftUuid, {
        minecraftUuid: link.minecraftUuid,
        telegramUserId: body.telegramUserId,
        chatId: body.chatId,
      });
      return json(res, 200, { minecraftUuid: link.minecraftUuid, linked: true });
    }

    if (req.method === "POST" && req.url === "/v1/messages") {
      const body = await readBody(req);
      if (typeof body.messageId !== "string" || typeof body.audioBase64 !== "string") {
        return json(res, 400, { error: "messageId and audioBase64 required" });
      }
      if (typeof body.minecraftUuid !== "string") return json(res, 400, { error: "minecraftUuid required" });

      const binding = bindings.get(body.minecraftUuid);
      if (!binding) return json(res, 409, { error: "minecraft_not_linked" });

      let audio: Buffer;
      try { audio = Buffer.from(body.audioBase64, "base64"); }
      catch { return json(res, 400, { error: "invalid_audio_base64" }); }
      if (audio.length === 0 || audio.length > 2 * 1024 * 1024) {
        return json(res, 413, { error: "audio_too_large" });
      }

      const durationMs = Number(body.durationMs);
      const telegramMessageId = await sendTelegramVoice(binding.chatId, audio, durationMs, body.messageId);
      return json(res, 200, {
        accepted: true,
        messageId: body.messageId,
        telegramMessageId,
      });
    }

    if (req.method === "GET" && req.url?.startsWith("/v1/link/status?")) {
      const uuid = new URL(req.url, `http://${req.headers.host ?? "localhost"}`).searchParams.get("minecraftUuid");
      const binding = uuid ? bindings.get(uuid) : undefined;
      return json(res, 200, { linked: Boolean(binding), telegramUserId: binding?.telegramUserId ?? null });
    }

    return json(res, 404, { error: "not_found" });
  } catch (error) {
    console.error(error instanceof Error ? error.message : error);
    return json(res, 500, { error: "internal_error" });
  }
});

server.listen(PORT, () => console.log(`Telegram bridge listening on :${PORT}`));
