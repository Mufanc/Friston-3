package android.media;

import dev.rikka.tools.refine.RefineAs;

@RefineAs(AudioRecordingConfiguration.class)
public class AudioRecordingConfigurationHidden {

    public static String toLogFriendlyString(AudioRecordingConfiguration arc) {
        throw new RuntimeException("Stub!");
    }

    public int getClientAudioSource() {
        throw new RuntimeException("Stub!");
    }

    public int getClientUid() {
        throw new RuntimeException("Stub!");
    }
}
