package dev.nikita.tgvoice.client;

/** Playback state only; actual Minecraft sound source is supplied by the audio adapter. */
public final class VoiceMessagePlayback {
    public enum State { STOPPED, PLAYING, PAUSED }

    private State state = State.STOPPED;
    private long positionMillis;
    private long durationMillis;

    public void load(long durationMillis) {
        this.durationMillis = Math.max(0, durationMillis);
        this.positionMillis = 0;
        this.state = State.STOPPED;
    }

    public void play() {
        if (durationMillis > 0 && positionMillis < durationMillis) state = State.PLAYING;
    }

    public void pause() { if (state == State.PLAYING) state = State.PAUSED; }

    public void resume() { if (state == State.PAUSED) state = State.PLAYING; }

    public void stop() {
        state = State.STOPPED;
        positionMillis = 0;
    }

    public void seek(long millis) {
        positionMillis = Math.max(0, Math.min(durationMillis, millis));
        if (positionMillis >= durationMillis && durationMillis > 0) state = State.STOPPED;
    }

    public void tick(long deltaMillis) {
        if (state != State.PLAYING) return;
        positionMillis = Math.min(durationMillis, positionMillis + Math.max(0, deltaMillis));
        if (positionMillis >= durationMillis) state = State.STOPPED;
    }

    public State state() { return state; }
    public long positionMillis() { return positionMillis; }
    public long durationMillis() { return durationMillis; }
    public float progress() { return durationMillis <= 0 ? 0f : (float) positionMillis / durationMillis; }
}
