# Codex Audit Guide — Plasmo Telegram Voice

## Goal
This repository contains a Fabric Minecraft 26.1.2 / Java 25 addon integrating Telegram-like voice messages and video notes with Plasmo Voice, plus a Node/TypeScript Telegram bridge.

## Important rule
Do not claim a feature is implemented merely because transport models, UI, or managers exist. Verify the complete runtime path and build before marking it complete.

## Current implementation status
### Voice messages
- Plasmo Voice client capture integration exists through `AudioCaptureProcessedEvent`.
- PCM is accumulated in `RecordingSession` and encoded to Ogg/Opus through the Plasmo Voice Opus encoder.
- Voice playback is routed through Plasmo Voice client playback primitives.
- Minecraft UI/input/configuration exist.
- Telegram bridge supports linking, outbound voice messages, inbound Telegram voice messages, inbox delivery and acknowledgement.
- Idempotency exists for the voice HTTP path, but inspect concurrency/race behavior and persistence bounds.

### Video notes
- `MediaMessageKind.VIDEO_NOTE` and `VideoNotePayload` exist.
- `VideoNoteContainer` validates/encodes/decodes bounded timestamped frames.
- Server-side video-note transport/broadcast exists.
- Client-side receive, playback timeline, frame cache, texture upload and custom video-note UI exist.
- **Real camera capture is NOT considered implemented until an actual webcam capture backend is present and tested on Minecraft 26.1.2.**
- **Telegram video_note send/receive is NOT considered implemented until the bridge explicitly handles Bot API `sendVideoNote`, inbound `message.video_note`, `getFile`, download, validation and delivery to Minecraft.**

## Architecture constraints
- Do not replace or fork Plasmo Voice's proximity voice protocol.
- Do not send proximity voice traffic to Telegram.
- Voice messages should reuse Plasmo Voice's existing processed capture pipeline where possible.
- Video is a separate media path and must not interfere with Plasmo Voice audio capture/playback.
- Telegram bot token stays server-side.
- Use bounded queues/caches and validate message size, duration, dimensions, frame rate and timestamps.
- Network failures must not crash the Minecraft client or bridge.
- Clean up NativeImage/DynamicTexture/audio resources.

## Required audit sequence
1. Inspect the complete repository and map all client/server/bridge paths.
2. Verify Minecraft 26.1.2, Fabric API and Java 25 compatibility.
3. Verify every Plasmo Voice API call against the actual 2.1.13 API usage; do not invent methods.
4. Run the Minecraft Gradle build with Java 25.
5. Run the Telegram bridge TypeScript build/tests.
6. Fix compilation errors first.
7. Review threading, lifecycle, resource cleanup and bounded-memory behavior.
8. Review packet validation and server authority.
9. Review voice recording/playback end-to-end.
10. Review Telegram voice send/receive end-to-end.
11. Implement and test a real webcam capture backend if still missing.
12. Implement and test Telegram video_note bridge support if still missing.
13. Review video frame encoding/decoding, timing, cache and texture lifecycle.
14. Test local video notes without Telegram linking.
15. Test simultaneous Plasmo Voice proximity audio and media messages.
16. Test reconnects, duplicate messages, malformed payloads and oversized media.
17. Only after all checks pass, update this document's status to reflect verified behavior.

## Known areas requiring special attention
- `VideoNoteTextureManager` currently decodes encoded frames into a `DynamicTexture`; verify the exact 26.1.2 texture API and ensure the displayed frame receives the intended circular mask.
- `VideoNoteFrameCache` uses decoded `NativeImage` frames and applies a circular alpha mask; verify all NativeImage pixel APIs against the target mappings.
- The client video tick currently advances by a fixed 50 ms; consider using the actual client tick delta if required for accurate timing.
- Bridge state is persisted as JSON; inspect growth, atomicity and failure recovery.
- Bridge HTTP request limits and Telegram media limits must be consistent.

## Definition of done
A feature is complete only when its full path is implemented, compiles, and has been tested as far as the environment permits. If hardware-dependent camera testing cannot be performed, report that explicitly instead of pretending it passed.
