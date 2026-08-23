package dev.nikita.tgvoice.client;

/**
 * Compatibility boundary for the Plasmo Voice public API.
 *
 * The addon never replaces the proximity voice pipeline. A concrete implementation
 * is installed by the client bootstrap once the exact Plasmo Voice API is available.
 */
public interface PlasmoVoiceAdapter {
    /** Start listening for explicit Voice Message capture frames. */
    void startCapture(RecordingSession session);

    /** Stop the explicit Voice Message capture hook. */
    void stopCapture();

    /** Whether the adapter is currently connected to Plasmo Voice. */
    boolean isAvailable();

    /** Create an addon-owned playback source; never reuse proximity sources. */
    PlaybackSource createPlaybackSource();

    interface PlaybackSource {
        void play(byte[] opusData, int sampleRate, int channels);
        void pause();
        void resume();
        void stop();
        void setVolume(float volume);
        void seek(long positionMillis);
        boolean isPlaying();
    }
}
