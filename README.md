# Telegram Voice Messages for Plasmo Voice

Minecraft Fabric addon that adds Telegram-style voice messages without replacing Plasmo Voice proximity voice chat.

## Status

Work in progress. The repository is being built around the public Plasmo Voice addon API. The first milestone is a compileable Fabric/Plasmo Voice addon with isolated Voice Messages UI and recording session architecture; Telegram Bridge follows as a separate service.

## Design goals

- No second proximity voice protocol.
- No replacement of Plasmo Voice microphone/device settings.
- No interception of normal Plasmo Voice proximity packets.
- Voice Messages are created only after explicit user recording.
- Telegram Bot Token is server-side only.
- Separate Minecraft addon and Telegram Bridge.

## Target

- Minecraft Java 26.1.x
- Fabric
- Java 25
- Plasmo Voice 2.1.x

## Build

Use the Gradle wrapper:

```text
./gradlew build
```

On Windows:

```text
gradlew.bat build
```

The build artifact is placed under `build/libs/`.

## Planned features

- Push-to-talk and toggle recording
- Release to send
- Right click to cancel
- Recording HUD independent of Plasmo Voice UI
- Interactive waveform
- Play / pause / resume / stop / seek
- Minecraft ↔ Telegram synchronization
- `/tglink`, `/tgunlink`, `/tgstatus`, `/tgchat`
- Message/audio IDs and idempotency
- Retry/reconnect and local cache
- Telegram voice-message upload/download
