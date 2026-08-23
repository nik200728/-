export type CachedAudio = {
  audioId: string;
  data: Buffer;
  expiresAt: number;
};

export class AudioCache {
  private readonly items = new Map<string, CachedAudio>();

  constructor(private readonly maxEntries = 64, private readonly ttlMs = 60 * 60_000) {}

  put(audioId: string, data: Buffer) {
    this.items.delete(audioId);
    this.items.set(audioId, { audioId, data: Buffer.from(data), expiresAt: Date.now() + this.ttlMs });
    while (this.items.size > this.maxEntries) this.items.delete(this.items.keys().next().value!);
  }

  get(audioId: string): Buffer | undefined {
    const item = this.items.get(audioId);
    if (!item) return undefined;
    if (item.expiresAt < Date.now()) {
      this.items.delete(audioId);
      return undefined;
    }
    return Buffer.from(item.data);
  }

  delete(audioId: string) { this.items.delete(audioId); }
}
