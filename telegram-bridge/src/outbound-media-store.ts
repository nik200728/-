export type OutboundMediaKind = "voice" | "video_note";

export type OutboundMedia = {
  minecraftUuid: string;
  messageId: string;
  kind: OutboundMediaKind;
  telegramMessageId: number | null;
  createdAt: number;
};

/** Bounded idempotency registry for Telegram sends, including concurrent retries. */
export class OutboundMediaStore {
  private readonly completed = new Map<string, OutboundMedia>();
  private readonly inFlight = new Map<string, Promise<OutboundMedia>>();

  constructor(private readonly maxEntries = 4096) {
    if (!Number.isInteger(maxEntries) || maxEntries < 1) {
      throw new Error("maxEntries must be positive");
    }
  }

  get(messageId: string): OutboundMedia | undefined {
    return this.completed.get(messageId);
  }

  values(): OutboundMedia[] {
    return [...this.completed.values()];
  }

  remember(delivery: OutboundMedia): void {
    validateDelivery(delivery);
    this.completed.set(delivery.messageId, delivery);
    this.trim();
  }

  send(messageId: string, factory: () => Promise<OutboundMedia>): Promise<OutboundMedia> {
    const existing = this.completed.get(messageId);
    if (existing) return Promise.resolve(existing);

    const pending = this.inFlight.get(messageId);
    if (pending) return pending;

    const created = Promise.resolve().then(factory).then(delivery => {
      if (delivery.messageId !== messageId) {
        throw new Error("outbound_media_message_id_mismatch");
      }
      this.remember(delivery);
      return delivery;
    });
    this.inFlight.set(messageId, created);
    return created.finally(() => {
      if (this.inFlight.get(messageId) === created) this.inFlight.delete(messageId);
    });
  }

  private trim(): void {
    while (this.completed.size > this.maxEntries) {
      const oldest = this.completed.keys().next().value;
      if (oldest === undefined) return;
      this.completed.delete(oldest);
    }
  }
}

function validateDelivery(delivery: OutboundMedia): void {
  if (!delivery || typeof delivery !== "object") throw new Error("invalid_outbound_media");
  if (typeof delivery.minecraftUuid !== "string" || delivery.minecraftUuid.length === 0) throw new Error("invalid_outbound_media");
  if (typeof delivery.messageId !== "string" || delivery.messageId.length === 0 || delivery.messageId.length > 128) throw new Error("invalid_outbound_media");
  if (delivery.kind !== "voice" && delivery.kind !== "video_note") throw new Error("invalid_outbound_media");
  if (delivery.telegramMessageId !== null && (!Number.isInteger(delivery.telegramMessageId) || delivery.telegramMessageId < 0)) throw new Error("invalid_outbound_media");
  if (!Number.isFinite(delivery.createdAt) || delivery.createdAt < 0) throw new Error("invalid_outbound_media");
}
