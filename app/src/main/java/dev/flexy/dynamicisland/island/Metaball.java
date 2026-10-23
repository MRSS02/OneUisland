package dev.flexy.dynamicisland.island;

import android.graphics.Path;

final class Metaball {
    private static final float HANDLE_SIZE = 2.4f;
    private static final float HALF_PI = (float) (Math.PI / 2);

    private Metaball() {
    }

    static boolean neck(float c1x, float c1y, float r1, float c2x, float c2y, float r2, float spread, Path out) {
        float dx = c2x - c1x;
        float dy = c2y - c1y;
        float d = (float) Math.hypot(dx, dy);
        if (r1 <= 0f || r2 <= 0f || spread <= 0.01f || d <= Math.abs(r1 - r2) + 0.5f) return false;

        float u1 = 0f;
        float u2 = 0f;
        if (d < r1 + r2) {
            u1 = (float) Math.acos(clamp((r1 * r1 + d * d - r2 * r2) / (2f * r1 * d)));
            u2 = (float) Math.acos(clamp((r2 * r2 + d * d - r1 * r1) / (2f * r2 * d)));
        }
        float between = (float) Math.atan2(dy, dx);
        float maxSpread = (float) Math.acos(clamp((r1 - r2) / d));
        float pi = (float) Math.PI;

        float a1 = between + u1 + (maxSpread - u1) * spread;
        float a2 = between - u1 - (maxSpread - u1) * spread;
        float a3 = between + pi - u2 - (pi - u2 - maxSpread) * spread;
        float a4 = between - pi + u2 + (pi - u2 - maxSpread) * spread;

        float p1x = c1x + r1 * cos(a1), p1y = c1y + r1 * sin(a1);
        float p2x = c1x + r1 * cos(a2), p2y = c1y + r1 * sin(a2);
        float p3x = c2x + r2 * cos(a3), p3y = c2y + r2 * sin(a3);
        float p4x = c2x + r2 * cos(a4), p4y = c2y + r2 * sin(a4);

        float total = r1 + r2;
        float d2Base = Math.min(spread * HANDLE_SIZE, (float) Math.hypot(p3x - p1x, p3y - p1y) / total);
        float d2 = d2Base * Math.min(1f, (d * 2f) / total);
        float h1r = r1 * d2;
        float h2r = r2 * d2;

        float h1x = p1x + h1r * cos(a1 - HALF_PI), h1y = p1y + h1r * sin(a1 - HALF_PI);
        float h2x = p2x + h1r * cos(a2 + HALF_PI), h2y = p2y + h1r * sin(a2 + HALF_PI);
        float h3x = p3x + h2r * cos(a3 + HALF_PI), h3y = p3y + h2r * sin(a3 + HALF_PI);
        float h4x = p4x + h2r * cos(a4 - HALF_PI), h4y = p4y + h2r * sin(a4 - HALF_PI);

        out.moveTo(p1x, p1y);
        out.cubicTo(h1x, h1y, h3x, h3y, p3x, p3y);
        out.lineTo(p4x, p4y);
        out.cubicTo(h4x, h4y, h2x, h2y, p2x, p2y);
        out.close();
        return true;
    }

    private static float clamp(float v) {
        return Math.max(-1f, Math.min(1f, v));
    }

    private static float cos(float a) {
        return (float) Math.cos(a);
    }

    private static float sin(float a) {
        return (float) Math.sin(a);
    }
}
