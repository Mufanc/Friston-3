package android.media;

import android.media.audiopolicy.AudioPolicy;

import dev.rikka.tools.refine.RefineAs;

@RefineAs(AudioManager.class)
public class AudioManagerHidden {
    public int registerAudioPolicy(AudioPolicy policy) {
        throw new RuntimeException("Stub!");
    }

    public void unregisterAudioPolicy(AudioPolicy policy) {
        throw new RuntimeException("Stub!");
    }
}
