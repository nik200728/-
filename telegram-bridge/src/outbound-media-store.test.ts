import assert from "node:assert/strict";
import test from "node:test";
import { OutboundMediaStore } from "./outbound-media-store.ts";

const delivery = (messageId: string) => ({
  minecraftUuid: "minecraft-1",
  messageId,
  kind: "video_note" as const,
  telegramMessageId: 123,
  createdAt: Date.now(),
});

test("deduplicates sequential outbound sends", async () => {
  const store = new OutboundMediaStore();
  let calls = 0;
  const factory = async () => {
    calls++;
    return delivery("m1");
  };

  const first = await store.send("m1", factory);
  const second = await store.send("m1", factory);

  assert.equal(calls, 1);
  assert.deepEqual(second, first);
});

test("deduplicates concurrent outbound sends", async () => {
  const store = new OutboundMediaStore();
  let calls = 0;
  let release!: () => void;
  const gate = new Promise<void>(resolve => { release = resolve; });

  const factory = async () => {
    calls++;
    await gate;
    return delivery("m2");
  };

  const firstPromise = store.send("m2", factory);
  const secondPromise = store.send("m2", factory);
  release();

  const [first, second] = await Promise.all([firstPromise, secondPromise]);
  assert.equal(calls, 1);
  assert.deepEqual(second, first);
});

test("remembers completed deliveries across reload", () => {
  const store = new OutboundMediaStore();
  store.remember(delivery("m3"));

  const restored = new OutboundMediaStore();
  for (const item of store.values()) restored.remember(item);

  assert.equal(restored.get("m3")?.telegramMessageId, 123);
});
