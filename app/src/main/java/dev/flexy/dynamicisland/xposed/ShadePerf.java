package dev.flexy.dynamicisland.xposed;

import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.XposedBridge;

final class ShadePerf {
    private ShadePerf() {
    }

    static void install(ClassLoader cl) {
        skipUnchangedFrameRate();
        keepGpuCachesOnShadeClose();
        dropDebugStacks(cl);
        dropLogNoise();
        skipRepeatedBlurReset();
        keepTileFrames();
    }


    private static void skipUnchangedFrameRate() {
        try {
            java.lang.reflect.Method m = View.class.getDeclaredMethod("setRequestedFrameRate", float.class);
            XposedBridge.hookMethod(m, SafeHook.before("frame rate", p -> {
                float f = (Float) p.args[0];
                if (Float.compare(f, ((View) p.thisObject).getRequestedFrameRate()) == 0) p.setResult(null);
            }));
        } catch (Throwable t) {
            XLog.e("frame rate hook failed", t);
        }
    }

    private static int uiHiddenTrims;

    private static void keepGpuCachesOnShadeClose() {
        Refl.hookAll(Refl.findClass("android.view.WindowManagerGlobal", null), "trimMemory",
                SafeHook.before("ui hidden trim", p -> {
                    if (!(p.args[0] instanceof Integer) || (Integer) p.args[0] != 20) return;
                    if (uiHiddenTrims++ < 1) XLog.i("skipping UI_HIDDEN trims");
                    p.setResult(null);
                }));
    }

    private static void dropDebugStacks(ClassLoader cl) {
        Refl.hookAll(cl, "com.android.systemui.qs.animator.QSAnimView", "nowWithDebugCallers",
                SafeHook.before("qs debug callers", p -> p.setResult("")));
        Refl.hookAll(cl, "com.android.systemui.log.QuickPanelLoggerHelper$PanelStateLogger", "log",
                SafeHook.before("panel state log", p -> p.setResult(null)));
    }

    private static final Set<String> NOISY_TAGS = new HashSet<>(Arrays.asList(
            "QuickPanelLog", "PanelSplitOpenAnimator", "View", "QuickTile", "SwipeToCollapseViewModel",
            "NowBarCardViewModel", "KeyguardUpdateMonitor", "NowBarGradientBlurController", "PluginLockMediatorImpl",
            "ShadeExpansionStateManager", "SecQuickSettingScene", "EditableContainer", "SecQpBlurController",
            "SpannableGrid", "BackgroundBlurDrawable", "SecPanelWindowBlurInteractor", "SecPanelWindowBlurBinder",
            "ExpandableNotifRow", "{OngoingActivityDataHelper}", "SecPanelSplitHelper", "{OngoingActivityController}",
            "ImmersiveScrollAnimator", "collectAsStateWithControl", "HoneySpace.OnGoingChip", "NotificationIconManager",
            "PriorityNotiScenarioManager", "LocalImageResolver", "NotificationPanelView", "KeyguardVisible",
            "StackScrollerController", "Tile.CustomTile", "LockscreenNotificationIconsOnlyController",
            "AODNotificationManager", "SamsungMumButtonViewModel", "NowBarUtils", "CentralSurfaces",
            "SamsungHomeStatusBarViewModelExt", "StackScroller", "SystemUIAnalytics", "SecPanelExpansionStateRepository",
            "SecMultiSIMPreferredBarViewModel", "ScrimController", "QuickTileBox", "PanelTransitionAnimator",
            "SamsungHomeStatusBarInteractor", "Highlights", "Bubbles", "SatelliteModeObserver", "TileLifecycleManager",
            "NavBarStateManager", "ImmersiveScrollPopOverAnimator", "KeyguardSecIndicationPolicy",
            "SecNotificationsQSContainerController", "HotspotTile", "BackActionInteractor", "BLASTBufferQueue_Java",
            "MultiWindowEdgeDetector", "ImeFocusController"));

    private static final String[] NOISY_PREFIXES = {"VRI[", "NowBarItemManager", "SamsungAnalytics", "QuickTile["};

    private static boolean noisy(String tag) {
        if (NOISY_TAGS.contains(tag)) return true;
        for (String p : NOISY_PREFIXES) {
            if (tag.startsWith(p)) return true;
        }
        return false;
    }

    private static void dropLogNoise() {
        Refl.hookAll(Log.class, "println_native", SafeHook.before("log noise", p -> {
            if (p.args.length < 4 || !(p.args[1] instanceof Integer) || (Integer) p.args[1] > Log.INFO) return;
            if (p.args[2] instanceof String && noisy((String) p.args[2])) p.setResult(0);
        }));
    }

    private static final Map<View, Boolean> BLUR_CLEARED = new WeakHashMap<>();

    private static void skipRepeatedBlurReset() {
        int n = Refl.hookAll(View.class, "semSetBlurInfo", SafeHook.before("blur reset", p -> {
            if (p.args.length != 1) return;
            View v = (View) p.thisObject;
            if (!v.getClass().getName().equals("com.android.systemui.blur.SecQSNewBlurView")) return;
            if (p.args[0] != null) {
                BLUR_CLEARED.remove(v);
            } else if (BLUR_CLEARED.put(v, Boolean.TRUE) != null) {
                p.setResult(null);
            }
        }));
        if (n == 0) XLog.i("semSetBlurInfo not found");
    }

    private static final int MAX_FRAMES = 32;
    private static final int MAX_FRAME_PX = 512;

    private static final Map<String, Drawable.ConstantState> FRAMES = new LinkedHashMap<String, Drawable.ConstantState>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Drawable.ConstantState> eldest) {
            return size() > MAX_FRAMES;
        }
    };

    private static void keepTileFrames() {
        Class<?> impl = Refl.findClass("android.content.res.ResourcesImpl", null);
        int n = Refl.hookAll(impl, "loadDrawable", SafeHook.after("tile frames", p -> {
            Object result = p.getResult();
            if (!(result instanceof BitmapDrawable)) return;
            TypedValue value = null;
            for (Object a : p.args) {
                if (a instanceof TypedValue) value = (TypedValue) a;
            }
            if (value == null || value.string == null) return;
            String file = value.string.toString();
            if (!file.endsWith(".webp")) return;
            BitmapDrawable d = (BitmapDrawable) result;
            if (d.getBitmap() == null || d.getBitmap().getWidth() > MAX_FRAME_PX || d.getBitmap().getHeight() > MAX_FRAME_PX) return;
            Drawable.ConstantState cs = d.getConstantState();
            if (cs == null) return;
            synchronized (FRAMES) {
                FRAMES.put(file + "#" + value.density, cs);
            }
        }));
        if (n == 0) XLog.i("ResourcesImpl.loadDrawable not found");
    }
}
