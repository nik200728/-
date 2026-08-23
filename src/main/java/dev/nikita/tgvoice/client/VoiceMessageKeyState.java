package dev.nikita.tgvoice.client;

/** Input state for the addon-owned recording key. */
public final class VoiceMessageKeyState {
    private boolean held;

    public void press() {
        if (!held) {
            held = true;
            VoiceMessageClient.getInstance().startRecording();
        }
    }

    public void release() {
        if (held) {
            held = false;
            VoiceMessageClient.getInstance().finishRecording();
        }
    }

    public void cancel() {
        held = false;
        VoiceMessageClient.getInstance().cancelRecording();
    }

    public boolean isHeld() { return held; }
}
