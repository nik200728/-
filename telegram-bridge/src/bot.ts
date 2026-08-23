import { getUpdates, sendMessage } from "./telegram.js";
import { LinkStore } from "./link-store.js";

export class TelegramBot {
  private offset = 0;
  private running = false;

  constructor(private readonly links: LinkStore) {}

  async start() {
    this.running = true;
    while (this.running) {
      try {
        const updates = await getUpdates(this.offset);
        for (const update of updates) {
          this.offset = Math.max(this.offset, update.update_id + 1);
          await this.handle(update);
        }
      } catch (error) {
        console.error("Telegram polling error", error);
        await new Promise(resolve => setTimeout(resolve, 3000));
      }
    }
  }

  stop() { this.running = false; }

  private async handle(update: Awaited<ReturnType<typeof getUpdates>>[number]) {
    const message = update.message;
    if (!message?.text || !message.from) return;
    const match = message.text.trim().match(/^\/link\s+(\d{6})$/);
    if (!match) return;

    const minecraftUuid = this.links.consumeCode(match[1]);
    if (!minecraftUuid) {
      await sendMessage(message.chat.id, "Код недействителен или уже истёк.");
      return;
    }

    this.links.setLink(minecraftUuid, message.from.id, message.chat.id);
    await sendMessage(message.chat.id, "Minecraft-аккаунт успешно привязан к этому Telegram-чату.");
  }
}
