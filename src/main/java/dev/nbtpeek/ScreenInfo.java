package dev.nbtpeek;

import java.lang.reflect.Method;

/** GUI size, read through reflection so a rename in a future snapshot can only disable auto-fit, never the build. */
final class ScreenInfo {
    private static final Method WIDTH, HEIGHT;

    static {
        Method w = null, h = null;
        try {
            Class<?> wc = WindowAccess.type();
            if (wc != null) {
                w = wc.getMethod("getGuiScaledWidth");
                h = wc.getMethod("getGuiScaledHeight");
            }
        } catch (Throwable t) {
            w = null;
            h = null;
        }
        WIDTH = w;
        HEIGHT = h;
    }

    private ScreenInfo() {}

    private static int read(Method m, int fallback) {
        if (m == null || !WindowAccess.available()) return fallback;
        try {
            return (Integer) m.invoke(WindowAccess.get());
        } catch (Throwable t) {
            return fallback;
        }
    }

    static int width() {
        return read(WIDTH, 427);
    }

    static int height() {
        return read(HEIGHT, 240);
    }

    /** How many extra lines fit under the lines the tooltip already has, without running off the screen. */
    static int capacity(int existingLines) {
        return Math.max(6, height() / 10 - existingLines - 3);
    }

    /** Characters per line that fit across the screen (default font is ~6 px per character). */
    static int charsAcross() {
        return Math.max(40, Math.min(200, width() / 6 - 6));
    }
}
