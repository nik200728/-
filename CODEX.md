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
- The bridge now has an outbound `/v1/video-notes` path and Telegram `sendVideoNote` integration, plus inbound `message.video_note` detection and download scaffolding. **This is NOT considered end-to-end Telegram video-note support yet:** downloaded Telegram MP4 still needs conversion into the Minecraft `TGV1` frame container and delivery into the Minecraft inbox/network path.

## Single-player test matrix
The mod has both common (`main`) and client entrypoints, so its common server-side code can run inside Minecraft's integrated single-player server when the mod is installed in the client instance. A single-player world is therefore useful for local smoke tests, but it cannot prove multi-player synchronization.

### What can be tested in a single-player world
- Mod loading and dependency resolution.
- Plasmo Voice initialization/handshake in an integrated server setup, provided Plasmo Voice is installed and compatible.
- Voice-message UI, keybinds, recording lifecycle and local playback.
- Voice-message packet serialization/validation and integrated-server transport.
- Local video-note receive/playback/rendering when a valid `VideoNotePayload` is produced by the implemented path.
- Resource cleanup and client lifecycle.

### What requires two Minecraft clients/players
- Actual server broadcast to another player.
- Sender/receiver synchronization between separate clients.
- Simultaneous proximity voice and media messages between players.
- Disconnect/reconnect behavior across two clients.

### What requires the external Telegram bridge
- Minecraft ↔ Telegram linking.
- Sending Minecraft voice/video notes to Telegram.
- Receiving Telegram voice/video notes in Minecraft.
- Telegram-side authentication, polling and persistence behavior.

### What still requires real hardware/software validation
- Webcam capture on the target machine.
- Telegram MP4 video-note decoding/conversion into `TGV1` frames.
- Final Gradle/TypeScript builds and runtime compatibility checks.

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
12. Complete and test Telegram video_note MP4-to-TGV1 conversion and Minecraft delivery.
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
