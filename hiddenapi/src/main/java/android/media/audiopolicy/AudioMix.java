package android.media.audiopolicy;

import android.media.AudioFormat;
import android.os.Parcelable;

public class AudioMix {

    public static final int ROUTE_FLAG_LOOP_BACK_RENDER = dummy();

    public static class Builder {
        public Builder(AudioMixingRule rule) {
            throw new RuntimeException("Stub!");
        }

        public Builder setFormat(AudioFormat format) {
            throw new RuntimeException("Stub!");
        }

        public Builder setRouteFlags(int routeFlags) {
            throw new RuntimeException("Stub!");
        }

        public AudioMix build() {
            throw new RuntimeException("Stub!");
        }
    }

    private static int dummy() {
        throw new RuntimeException("Stub!");
    }
}
