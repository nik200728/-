import { createServer } from "node:http";

const port = Number(process.env.PORT ?? 8080);
const bridgeSecret = process.env.BRIDGE_SECRET;

if (!bridgeSecret) {
  console.warn("BRIDGE_SECRET is not configured; authenticated message endpoints are intentionally unavailable.");
}

const server = createServer((req, res) => {
  if (req.method === "GET" && req.url === "/health") {
    res.writeHead(200, { "content-type": "application/json" });
    res.end(JSON.stringify({ ok: true, service: "telegram-bridge" }));
    return;
  }

  res.writeHead(404, { "content-type": "application/json" });
  res.end(JSON.stringify({ error: "not_found" }));
});

server.listen(port, () => {
  console.log(`Telegram bridge listening on :${port}`);
});
