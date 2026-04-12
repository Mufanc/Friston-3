package android.media.audiopolicy;

import android.content.Context;
import android.media.AudioRecord;

public class AudioPolicy {
    public static class Builder {
        public Builder(Context context) {
            throw new RuntimeException("Stub!");
        }

        public Builder addMix(AudioMix mix) {
            throw new RuntimeException("Stub!");
        }

        public AudioPolicy build() {
            throw new RuntimeException("Stub!");
        }
    }

    public AudioRecord createAudioRecordSink(AudioMix mix) {
        throw new RuntimeException("Stub!");
    }
}
