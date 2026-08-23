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
const links = new Map<string, LinkCode>();

function authorized(req: http.IncomingMessage): boolean {
  const value = req.headers.authorization ?? "";
  if (!value.startsWith("Bearer ")) return false;
  return crypto.timingSafeEqual(
    Buffer.from(value.slice(7)),
    Buffer.from(BRIDGE_TOKEN),
  );
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
      links.delete(link.code);
      return json(res, 200, { minecraftUuid: link.minecraftUuid, linked: true });
    }

    if (req.method === "POST" && req.url === "/v1/messages") {
      const body = await readBody(req);
      if (typeof body.messageId !== "string" || typeof body.audioId !== "string") {
        return json(res, 400, { error: "messageId and audioId required" });
      }
      // Telegram Bot API delivery is intentionally kept behind this backend boundary.
      return json(res, 202, { accepted: true, messageId: body.messageId, telegramConfigured: Boolean(TELEGRAM_BOT_TOKEN) });
    }

    return json(res, 404, { error: "not_found" });
  } catch (error) {
    console.error(error instanceof Error ? error.message : error);
    return json(res, 500, { error: "internal_error" });
  }
});

server.listen(PORT, () => console.log(`Telegram bridge listening on :${PORT}`));
