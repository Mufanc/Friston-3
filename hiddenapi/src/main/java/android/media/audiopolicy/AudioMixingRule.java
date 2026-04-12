package android.media.audiopolicy;

import android.media.AudioAttributes;

public class AudioMixingRule {

    public static final int RULE_MATCH_ATTRIBUTE_USAGE = dummy();

    public static class Builder {
        public Builder addRule(AudioAttributes attrToMatch, int rule) {
            throw new RuntimeException("Stub!");
        }

        public AudioMixingRule build() {
            throw new RuntimeException("Stub!");
        }
    }

    private static int dummy() {
        throw new RuntimeException("Stub!");
    }
}
