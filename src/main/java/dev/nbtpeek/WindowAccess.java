package dev.nbtpeek;

import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;

/**
 * Finds the game window on Minecraft by its TYPE (a public no-arg method or field that returns a "Window"),
 * through reflection, so renaming the accessor in a future snapshot can only switch off key/size detection, never the build.
 */
final class WindowAccess {
    private static final Member SOURCE;

    static {
        Member found = null;
        try {
            for (Method m : Minecraft.class.getMethods()) {
                if (m.getParameterCount() == 0 && m.getReturnType().getSimpleName().equals("Window")) {
                    if (found == null || m.getName().equals("getWindow")) found = m;
                }
            }
            if (found == null) {
                for (Field f : Minecraft.class.getFields()) {
                    if (f.getType().getSimpleName().equals("Window")) {
                        found = f;
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            found = null;
        }
        SOURCE = found;
    }

    private WindowAccess() {}

    static boolean available() {
        return SOURCE != null;
    }

    /** The Window class, or null if it could not be found. */
    static Class<?> type() {
        if (SOURCE instanceof Method m) return m.getReturnType();
        if (SOURCE instanceof Field f) return f.getType();
        return null;
    }

    /** The current window object (throws if unavailable; callers catch Throwable). */
    static Object get() throws ReflectiveOperationException {
        if (SOURCE instanceof Method m) return m.invoke(Minecraft.getInstance());
        if (SOURCE instanceof Field f) return f.get(Minecraft.getInstance());
        throw new IllegalStateException("no window accessor");
    }
}
