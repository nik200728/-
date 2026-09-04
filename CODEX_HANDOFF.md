# Codex Handoff — Plasmo Telegram Voice / Video Notes

## What this project is
Minecraft Fabric mod + external Telegram Bridge. The goal is Telegram-style voice messages and video notes inside Minecraft, with two-way synchronization between Minecraft and Telegram.

Repository: `nik200728/-`
Active branch: `video-webcam-capture`
Do NOT merge PRs unless explicitly requested.

## Main goals
1. Voice messages that feel like Telegram:
   - hold LMB to record
   - release to send
   - RMB cancels
   - recording indicator
   - playback with pause/resume/seek/stop
   - Plasmo Voice compatibility without breaking normal voice chat
2. Video notes:
   - hold `V` to record webcam video
   - release to send
   - automatic max duration 60s
   - webcam capture through OpenPnP Capture
   - 512x512 target, center-square crop/scale
   - JPEG frames packed into TGV1
   - `J` opens local video-note browser
   - Telegram bridge converts TGV1 <-> MP4 and sends/downloads Telegram video notes
3. Two-way synchronization:
   - Minecraft -> Telegram
   - Telegram -> Minecraft
   - bounded payloads and strict validation
   - idempotency / message metadata must remain consistent

## Important constraints
- Java/JDK 25
- Minecraft 26.1.2
- Fabric Loader 0.18.4
- Fabric API 0.155.2+26.1.2
- Plasmo Voice API 2.1.13
- OpenPnP Capture 0.0.28-0
- Node >=22 for bridge
- TypeScript 5.8.x
- ffmpeg/ffprobe required by bridge

## Current progress
Approximate project completion: 79%.
Do not inflate this percentage until the Minecraft build is actually confirmed clean and the end-to-end flow is tested.

## Major work already implemented
### Minecraft
- Voice recording/playback integration with Plasmo Voice.
- Video webcam capture service.
- Video recorder with bounded frame/video size and 60s limit.
- TGV1 container encoding/decoding with strict validation.
- Video note payload validation.
- Local video-note manager/browser UI.
- Decoded-frame cache with bounded size and collision-safe cache keys.
- GPU texture manager that reuses the same uploaded texture for the same decoded frame and releases textures correctly.
- Playback state manager with bounded access-order cache.
- Playback lifecycle fixes: switching notes stops previous playback; closing the screen stops active playback; evicted/removed/cleared playback states are stopped.

### Telegram bridge
`telegram-bridge/src/video.ts`
- strict base64 validation and canonical re-encoding check
- TGV1 validation
- MP4 <-> TGV1 conversion using ffmpeg/ffprobe
- limits: 8 MiB TGV1, 60s duration, 512x512, max 30 FPS, Telegram MP4 input/output bound 50 MiB
- bounded ffmpeg stderr and process timeout
- strict validation in sendTelegramVideoNote/downloadTelegramVideoNote

### CI
`.github/workflows/build.yml` builds Minecraft and bridge.
- Minecraft: Java 25 + Gradle 9.4
- Bridge: Node 22, explicitly installs ffmpeg/ffprobe, then npm install/build/test

## CRITICAL: current unresolved issue
The video playback manager has a `tick()` method, but an audit found no confirmed call site from the Minecraft client tick lifecycle.

Before doing more feature work, verify this in the current branch. Search for:
- `VideoNotePlaybackManager.tick`
- Fabric client tick event registration
- any existing client lifecycle callback

If there is no call, wire it into the correct Minecraft 26.1.2/Fabric client tick lifecycle. Playback must advance based on elapsed time/ticks and must not depend on render FPS.

## Minecraft 26.1.2 GUI migration notes
This project targets Minecraft 26.1.2 where GUI rendering uses the extraction model.
Current `VideoNoteScreen` uses:
- `GuiGraphicsExtractor`
- `extractRenderState(...)`
- `MouseButtonEvent`
- `KeyEvent`
- `RenderPipelines.GUI_TEXTURED`
Do not blindly reintroduce old 1.21.x APIs such as `Screen.render`, old mouse/key signatures, or old `GuiGraphics` assumptions.

## Known previous build failures
An earlier real CI run had many Minecraft compile errors, including:
- duplicate `TelegramVoiceMod` class
- missing OpenPnP packages on compile classpath
- old GUI APIs (`GuiGraphics`, old `Screen.render`, old mouse/key signatures)
- old `NativeImage` pixel methods
- old `DynamicTexture` constructor
- old `Player.displayClientMessage`
- `InboundVideoMessage` UUID/String mismatch
Subsequent compatibility commits addressed many of these, but there is still NO confirmed clean Minecraft CI run after the latest fixes.

## Latest relevant commits
- `75eb4a0` — `fix: stop evicted video playback states`
- `d579bff920e806c01aa3efd44e9c137931a15a96` — `fix: stop video playback when leaving or switching notes`
- `0679fff` — `perf: reuse decoded video frames for GPU upload`
- `cc8aaab` — `perf: upload decoded video frame directly`
- `b17b706` — `fix: make video frame cache collision-safe`
- `25fc4edaa1af215d500c03b4f7d49f596d917302` — `ci: install ffmpeg explicitly for video bridge`

## Files worth inspecting first
Minecraft:
- `src/main/java/dev/nikita/tgvoice/client/TelegramVoiceClient.java`
- `src/main/java/dev/nikita/tgvoice/client/VideoNoteScreen.java`
- `src/main/java/dev/nikita/tgvoice/client/VideoNotePlayback.java`
- `src/main/java/dev/nikita/tgvoice/client/VideoNotePlaybackManager.java`
- `src/main/java/dev/nikita/tgvoice/client/VideoNoteRenderState.java`
- `src/main/java/dev/nikita/tgvoice/client/VideoNoteFrameCache.java`
- `src/main/java/dev/nikita/tgvoice/client/VideoNoteTextureManager.java`
- `src/main/java/dev/nikita/tgvoice/client/VideoNoteCaptureController.java`
- `src/main/java/dev/nikita/tgvoice/client/VideoNoteRecorder.java`
- `src/main/java/dev/nikita/tgvoice/client/WebcamCaptureService.java`
- `src/main/java/dev/nikita/tgvoice/network/VideoNotePayload.java`
- `src/main/java/dev/nikita/tgvoice/network/VideoNoteContainer.java`
- `src/main/java/dev/nikita/tgvoice/network/VideoNoteManager.java`
- `build.gradle`
- `gradle.properties`
- `fabric.mod.json`

Bridge:
- `telegram-bridge/src/video.ts`
- `telegram-bridge/src/video.test.ts`
- `telegram-bridge/src/main.ts`
- `telegram-bridge/package.json`
- `.github/workflows/build.yml`

## Technical behavior already implemented
### TGV1
TGV1 contains magic/version, width/height, FPS, duration, frame count, then timestamped JPEG frames. Validation is intentionally strict:
- increasing timestamps
- timestamps inside duration
- max 1800 frames
- max 512 KiB per frame
- max 8 MiB container
- dimensions <= 512
- FPS <= 30
- duration <= 60s
- no trailing bytes

### Video frame pipeline
Webcam -> OpenPnP frame -> center square crop/scale -> JPEG -> recorder -> TGV1 -> payload -> bridge -> MP4/Telegram.
Playback: TGV1 -> decoded JPEG frame cache -> one reusable GPU texture -> UI.

### Resource ownership
- Frame cache owns decoded `NativeImage` instances and closes evicted/cleared frames.
- Texture manager owns GPU texture and releases it when replaced/closed.
- Playback manager owns playback states and must stop states when evicted/removed/cleared.
- Screen stops active playback and clears resources in `removed()`.

## What NOT to claim
- Do not claim CI passed unless a current GitHub Actions run actually reports success.
- Do not claim real webcam capture works unless tested on a machine with a webcam.
- Do not claim full Minecraft -> Telegram -> Minecraft round trip works unless actually tested end-to-end.
- Do not claim Telegram conversion is production-ready solely because TypeScript tests pass.

## Next recommended order
1. Inspect `TelegramVoiceClient.java` and wire/verify `VideoNotePlaybackManager.tick()` in client tick lifecycle.
2. Run/trigger Minecraft and bridge CI and inspect actual results.
3. Fix all compile/test failures based on real logs, not guesses.
4. Inspect resulting Minecraft JAR and confirm OpenPnP/JNA/native runtime resources are packaged.
5. Add/execute realistic video conversion tests if feasible.
6. Test webcam capture locally.
7. Test complete Minecraft -> bridge -> Telegram -> bridge -> Minecraft video-note round trip.
8. Only then raise completion percentage substantially.

## Working style for Codex
Be implementation-first. Inspect the current repository before changing anything. Prefer small, reviewable commits. Preserve working voice-message functionality and Plasmo Voice compatibility. Avoid speculative API migrations. After each meaningful change, report:
- what was changed
- commit SHA
- what was actually verified
- what remains
- completion percentage
