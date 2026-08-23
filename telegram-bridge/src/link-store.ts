export type Link = {
  minecraftUuid: string;
  telegramUserId: number;
  telegramChatId: number;
  createdAt: number;
};

export class LinkStore {
  private readonly links = new Map<string, Link>();
  private readonly pending = new Map<string, { minecraftUuid: string; expiresAt: number }>();

  createCode(minecraftUuid: string): string {
    const code = String(Math.floor(100000 + Math.random() * 900000));
    this.pending.set(code, { minecraftUuid, expiresAt: Date.now() + 5 * 60_000 });
    return code;
  }

  consumeCode(code: string): string | null {
    const item = this.pending.get(code);
    if (!item || item.expiresAt < Date.now()) {
      this.pending.delete(code);
      return null;
    }
    this.pending.delete(code);
    return item.minecraftUuid;
  }

  setLink(minecraftUuid: string, telegramUserId: number, telegramChatId: number) {
    this.links.set(minecraftUuid, { minecraftUuid, telegramUserId, telegramChatId, createdAt: Date.now() });
  }

  getByMinecraft(minecraftUuid: string): Link | undefined {
    return this.links.get(minecraftUuid);
  }

  unlink(minecraftUuid: string): boolean {
    return this.links.delete(minecraftUuid);
  }
}
