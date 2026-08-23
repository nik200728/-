const TOKEN = process.env.TELEGRAM_BOT_TOKEN ?? "";
const API = TOKEN ? `https://api.telegram.org/bot${TOKEN}` : "";

export type TelegramUpdate = {
  update_id: number;
  message?: {
    message_id: number;
    chat: { id: number };
    from?: { id: number; username?: string; first_name?: string };
    text?: string;
    voice?: { file_id: string; duration: number; mime_type?: string; file_size?: number };
    audio?: { file_id: string; duration?: number; mime_type?: string; file_size?: number };
  };
};

async function call<T>(method: string, body?: unknown): Promise<T> {
  if (!API) throw new Error("TELEGRAM_BOT_TOKEN is not configured");
  const response = await fetch(`${API}/${method}`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body ?? {}),
  });
  if (!response.ok) throw new Error(`Telegram HTTP ${response.status}`);
  const result = await response.json() as { ok: boolean; result: T; description?: string };
  if (!result.ok) throw new Error(result.description ?? "Telegram API error");
  return result.result;
}

export async function getUpdates(offset?: number): Promise<TelegramUpdate[]> {
  return call<TelegramUpdate[]>("getUpdates", { timeout: 25, ...(offset === undefined ? {} : { offset }) });
}

export async function sendMessage(chatId: number, text: string) {
  return call("sendMessage", { chat_id: chatId, text });
}

export async function getFilePath(fileId: string): Promise<string> {
  const file = await call<{ file_path?: string }>("getFile", { file_id: fileId });
  if (!file.file_path) throw new Error("Telegram returned no file_path");
  return file.file_path;
}

export async function downloadFile(filePath: string): Promise<Buffer> {
  const response = await fetch(`https://api.telegram.org/file/bot${TOKEN}/${filePath}`);
  if (!response.ok) throw new Error(`Telegram file HTTP ${response.status}`);
  const arrayBuffer = await response.arrayBuffer();
  return Buffer.from(arrayBuffer);
}

export async function sendVoice(chatId: number, audio: Buffer, filename = "voice.ogg") {
  if (!API) throw new Error("TELEGRAM_BOT_TOKEN is not configured");
  const form = new FormData();
  form.append("chat_id", String(chatId));
  form.append("voice", new Blob([audio]), filename);
  const response = await fetch(`${API}/sendVoice`, { method: "POST", body: form });
  if (!response.ok) throw new Error(`Telegram sendVoice HTTP ${response.status}`);
  return response.json();
}
