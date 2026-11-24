package dev.nbtpeek;

import java.lang.reflect.Method;

/**
 * Checks whether Shift / Ctrl / Alt is held, using GLFW through reflection.
 * Reflection on purpose: input and window classes were reshuffled in recent Minecraft versions, and this way
 * a rename can never break compilation. If anything is missing the feature just turns itself off.
 */
final class ExpandKey {
    private static final Method HANDLE;
    private static final Method GET_KEY;
    private static volatile boolean failed;

    static {
        Method h = null, gk = null;
        try {
            Class<?> windowClass = WindowAccess.type();
            if (windowClass != null) {
                for (String wanted : new String[] {"handle", "getWindow"}) {
                    for (Method m : windowClass.getMethods()) {
                        if (h == null && m.getName().equals(wanted) && m.getParameterCount() == 0 && m.getReturnType() == long.class) {
                            h = m;
                        }
                    }
                }
                gk = Class.forName("org.lwjgl.glfw.GLFW").getMethod("glfwGetKey", long.class, int.class);
            }
        } catch (Throwable t) {
            h = null;
            gk = null;
        }
        HANDLE = h;
        GET_KEY = gk;
    }

    private ExpandKey() {}

    static boolean available() {
        return WindowAccess.available() && HANDLE != null && GET_KEY != null && !failed;
    }

    /** mod = "shift", "ctrl", "alt" (anything else, e.g. "none", is never down). */
    static boolean isDown(String mod) {
        int left, right;
        switch (mod) {
            case "shift" -> { left = 340; right = 344; }
            case "ctrl" -> { left = 341; right = 345; }
            case "alt" -> { left = 342; right = 346; }
            default -> { return false; }
        }
        if (!available()) return false;
        try {
            long handle = (Long) HANDLE.invoke(WindowAccess.get());
            return (Integer) GET_KEY.invoke(null, handle, left) == 1
                    || (Integer) GET_KEY.invoke(null, handle, right) == 1;
        } catch (Throwable t) {
            failed = true;
            return false;
        }
    }

    static String displayName(String mod) {
        return switch (mod) {
            case "shift" -> "Shift";
            case "ctrl" -> "Ctrl";
            case "alt" -> "Alt";
            default -> "";
        };
    }
}
