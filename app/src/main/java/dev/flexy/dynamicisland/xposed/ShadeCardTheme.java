package dev.flexy.dynamicisland.xposed;

import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import android.view.ViewParent;

final class ShadeCardTheme {
    private static final String ANV = "com.android.systemui.statusbar.notification.row.ActivatableNotificationView";
    private static final String NBV = "com.android.systemui.statusbar.notification.row.NotificationBackgroundView";
    private static final String PICKER = "noticolorpicker.NotificationColorPicker";

    private static final float[] DARK_CURVE = {0.4f, -23f, 5f, 250f, 21.6f, 141.5f};
    private static final int DARK_SOLID = 0xF0202022;

    private static final ThreadLocal<View> CURRENT = new ThreadLocal<>();
    private static Class<?> anvClass;

    private ShadeCardTheme() {
    }

    static void install(ClassLoader cl) {
        anvClass = Refl.findClass(ANV, cl);
        if (anvClass == null) return;
        for (String m : new String[]{"initBlurBackground", "keepLandBlurClipInfo"}) {
            Refl.hookAll(cl, ANV, m, SafeHook.before("card theme " + m, p -> CURRENT.set((View) p.thisObject)));
            Refl.hookAll(cl, ANV, m, SafeHook.after("card theme " + m + " end", p -> CURRENT.remove()));
        }
        Refl.hookAll(cl, PICKER, "getColorCurvePreset", SafeHook.after("card theme curve", p -> {
            float[] res = (float[]) p.getResult();
            View row = CURRENT.get();
            if (res == null || res.length < 7 || res[5] < 100f || row == null || !islandRow(row)) return;
            float[] out = new float[7];
            out[0] = res[0];
            System.arraycopy(DARK_CURVE, 0, out, 1, 6);
            p.setResult(out);
        }));
        Refl.hookAll(cl, NBV, "setCustomBackground", SafeHook.after("card theme fill", p -> fill((View) p.thisObject)));
        Refl.hookAll(cl, NBV, "setTint", SafeHook.before("card theme tint", p -> {
            View row = row((View) p.thisObject);
            if (row != null && light(row) && islandRow(row)) p.args[0] = 0;
        }));
    }

    static void refresh(View row) {
        row.post(() -> {
            Refl.call(row, "initBackground");
            Refl.call(row, "updateBackground");
            row.invalidate();
        });
    }

    private static final java.util.Map<View, Long> REPAIRED = new java.util.WeakHashMap<>();

    static void ensureBlur(View row) {
        if (row == null || !row.isAttachedToWindow()) return;
        Object nbv = Refl.get(row, "mBackgroundNormal");
        if (!(nbv instanceof View)) return;
        Drawable bg = ((View) nbv).getBackground();
        if (bg != null && bg.getClass().getName().endsWith("BackgroundBlurDrawable")) return;
        if (Refl.getBool(row, "mOnKeyguard", false)) return;
        for (String m : new String[]{"isHeadsUpState", "isPinned", "isHeadsUpAnimatingAway"}) {
            if (Boolean.TRUE.equals(Refl.call(row, m))) return;
        }
        if (!Boolean.TRUE.equals(Refl.call(row, "isBlurBackground"))) return;
        long now = android.os.SystemClock.uptimeMillis();
        Long last = REPAIRED.get(row);
        if (last != null && now - last < 1000) return;
        REPAIRED.put(row, now);
        XLog.i("card blur restored");
        refresh(row);
    }

    private static boolean islandRow(View row) {
        return ShadeLiveCards.isCardRow(row) || ShadeMediaPlayer.activeRow(row);
    }

    private static boolean light(View v) {
        return (v.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES;
    }

    private static void fill(View nbv) {
        View row = row(nbv);
        if (row == null || !light(row) || !islandRow(row)) return;
        Object bg = Refl.get(nbv, "mBackground");
        if (!(bg instanceof LayerDrawable) || ((LayerDrawable) bg).getNumberOfLayers() == 0) return;
        Drawable d0 = ((LayerDrawable) bg).getDrawable(0);
        if (!(d0 instanceof GradientDrawable)) return;
        GradientDrawable gd = (GradientDrawable) d0.mutate();
        int[] colors = gd.getColors();
        if (colors == null || allTransparent(colors)) return;
        gd.setColors(new int[]{DARK_SOLID, DARK_SOLID});
        nbv.invalidate();
    }

    private static boolean allTransparent(int[] colors) {
        for (int c : colors) if ((c >>> 24) != 0) return false;
        return true;
    }

    private static View row(View v) {
        if (anvClass.isInstance(v)) return v;
        ViewParent p = v.getParent();
        for (int i = 0; i < 3 && p instanceof View; i++, p = p.getParent()) {
            if (anvClass.isInstance(p)) return (View) p;
        }
        return null;
    }
}
