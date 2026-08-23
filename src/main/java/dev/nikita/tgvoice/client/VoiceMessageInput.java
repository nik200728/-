package dev.nikita.tgvoice.client;

/** Keeps input handling isolated from Plasmo Voice key bindings. */
public final class VoiceMessageInput {
    private final VoiceMessageKeyState ptt = new VoiceMessageKeyState();
    private boolean toggle;

    public void press() {
        if (toggle) {
            if (VoiceMessageClient.getInstance().isRecording()) {
                VoiceMessageClient.getInstance().finishRecording();
            } else {
                VoiceMessageClient.getInstance().startRecording();
            }
            return;
        }
        ptt.press();
    }

    public void release() {
        if (!toggle) ptt.release();
    }

    public void cancel() {
        ptt.cancel();
        VoiceMessageClient.getInstance().cancelRecording();
    }

    public void setToggle(boolean toggle) {
        this.toggle = toggle;
        if (toggle && ptt.isHeld()) ptt.cancel();
    }

    public boolean isToggle() { return toggle; }
}
