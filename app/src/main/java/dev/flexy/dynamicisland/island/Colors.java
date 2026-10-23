package dev.flexy.dynamicisland.island;

import android.graphics.Color;

final class Colors {
    private Colors() {
    }

    static float luminance(int c) {
        return (0.2126f * lin(Color.red(c)) + 0.7152f * lin(Color.green(c)) + 0.0722f * lin(Color.blue(c)));
    }

    private static float lin(int channel) {
        float v = channel / 255f;
        return v <= 0.04045f ? v / 12.92f : (float) Math.pow((v + 0.055f) / 1.055f, 2.4f);
    }

    static int readableOnBlack(int c) {
        c |= 0xFF000000;
        int out = c;
        for (int i = 0; i < 12 && luminance(out) < 0.22f; i++) {
            out = blend(out, Color.WHITE, 0.12f);
        }
        return out;
    }

    static int blend(int a, int b, float t) {
        float u = 1f - t;
        return Color.argb(
                Math.round(Color.alpha(a) * u + Color.alpha(b) * t),
                Math.round(Color.red(a) * u + Color.red(b) * t),
                Math.round(Color.green(a) * u + Color.green(b) * t),
                Math.round(Color.blue(a) * u + Color.blue(b) * t));
    }

    static int withAlpha(int c, float alpha) {
        int a = Math.round(Color.alpha(c) * Math.max(0f, Math.min(1f, alpha)));
        return (c & 0x00FFFFFF) | (a << 24);
    }
}
