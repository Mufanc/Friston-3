package android.os;

import dev.rikka.tools.refine.RefineAs;

@RefineAs(Process.class)
public class ProcessHidden {
    public static void setArgV0(String name) {
        throw new RuntimeException("Stub!");
    }
}
