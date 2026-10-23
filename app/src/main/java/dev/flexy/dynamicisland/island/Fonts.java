package dev.flexy.dynamicisland.island;

import android.graphics.Typeface;
import dev.flexy.dynamicisland.xposed.Refl;

final class Fonts {
    private Fonts() {
    }

    static Object key() {
        Object nativeInstance = Refl.get(Typeface.DEFAULT, "native_instance");
        return nativeInstance != null ? nativeInstance : Typeface.DEFAULT;
    }

    static Typeface of(int weight) {
        if (flipFont()) return weight >= 600 ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT;
        return Typeface.create(Typeface.DEFAULT, weight, false);
    }

    private static boolean flipFont() {
        Object used = Refl.getStatic(Typeface.class, "isFlipFontUsed");
        return Boolean.TRUE.equals(used);
    }
}
