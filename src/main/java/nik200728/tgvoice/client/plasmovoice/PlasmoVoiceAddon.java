package nik200728.tgvoice.client.plasmovoice;

/**
 * Version-isolated Plasmo Voice integration entry point.
 *
 * Keep all direct Plasmo Voice API references in this package. This prevents
 * the rest of the mod from depending on unstable/internal PV classes.
 */
public final class PlasmoVoiceAddon {
    private final PlasmoVoiceCaptureBridge capture = new PlasmoVoiceCaptureBridge();

    public PlasmoVoiceCaptureBridge capture() {
        return capture;
    }
}
