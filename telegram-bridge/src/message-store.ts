import type { Link } from "./link-store.js";

export type StoredMessage = {
  messageId: string;
  audioId: string;
  minecraftUuid: string;
  telegramChatId: number;
  telegramMessageId?: number;
  createdAt: number;
};

export interface MessageStore {
  saveLink(link: Link): Promise<void>;
  getLink(minecraftUuid: string): Promise<Link | undefined>;
  removeLink(minecraftUuid: string): Promise<boolean>;
  saveMessage(message: StoredMessage): Promise<void>;
  getMessage(messageId: string): Promise<StoredMessage | undefined>;
}

/** Development store. Replace with SQLite/PostgreSQL implementation in production. */
export class MemoryMessageStore implements MessageStore {
  private readonly links = new Map<string, Link>();
  private readonly messages = new Map<string, StoredMessage>();

  async saveLink(link: Link) { this.links.set(link.minecraftUuid, link); }
  async getLink(uuid: string) { return this.links.get(uuid); }
  async removeLink(uuid: string) { return this.links.delete(uuid); }
  async saveMessage(message: StoredMessage) { this.messages.set(message.messageId, message); }
  async getMessage(messageId: string) { return this.messages.get(messageId); }
}
