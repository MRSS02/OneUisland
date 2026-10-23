package dev.flexy.dynamicisland.island;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Region;
import android.hardware.display.DisplayManager;
import android.view.Display;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import dev.flexy.dynamicisland.xposed.Refl;
import dev.flexy.dynamicisland.xposed.XLog;

@SuppressLint("ViewConstructor")
final class IslandWindow {

    interface Listener {
        void onWindowConfigurationChanged();

        void onWindowInsetsChanged();
    }

    private static final int TYPE_ISLAND = 2017;
    private static final int PRIVATE_FLAGS = 0x20000050;
    private static final int TOUCHABLE_INSETS_REGION = 3;

    final Context context;
    final IslandView view;
    private final WindowManager wm;
    private final Root root;
    private final WindowManager.LayoutParams lp;
    private final Region touchRegion = new Region();
    private boolean added;
    private boolean watchOutside;
    private int windowHeight;

    IslandWindow(Context systemUiContext, IslandView.Host host, Listener listener) {
        DisplayManager dm = systemUiContext.getSystemService(DisplayManager.class);
        Display display = dm.getDisplay(Display.DEFAULT_DISPLAY);
        Context wctx;
        try {
            wctx = systemUiContext.createWindowContext(display, TYPE_ISLAND, null);
        } catch (Throwable t) {
            XLog.e("createWindowContext failed, falling back to display context", t);
            wctx = systemUiContext.createDisplayContext(display);
        }
        context = wctx;
        wm = context.getSystemService(WindowManager.class);
        view = new IslandView(context, host);
        root = new Root(context, listener);
        BlurLayer blur = new BlurLayer(context);
        root.addView(blur, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        view.setBlurSink(blur);
        FrameLayout remoteLayer = new FrameLayout(context);
        remoteLayer.setClipChildren(false);
        root.addView(remoteLayer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        view.setRemoteLayer(remoteLayer);
        root.setClipChildren(false);

        lp = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                1,
                TYPE_ISLAND,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                        | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.x = 0;
        lp.y = 0;
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        lp.setFitInsetsTypes(0);
        lp.windowAnimations = 0;
        lp.setTitle("DynamicIsland");
        try {
            Field f = WindowManager.LayoutParams.class.getField("privateFlags");
            f.setInt(lp, f.getInt(lp) | PRIVATE_FLAGS);
        } catch (Throwable t) {
            XLog.e("privateFlags not set", t);
        }
        installTouchableRegion();
        root.getViewTreeObserver().addOnPreDrawListener(() -> {
            if (surfaceAlpha != 1f || surfaceScale != 1f) applySurfaceFade();
            return true;
        });
    }

    void setWindowHeight(int height) {
        if (height == windowHeight) return;
        windowHeight = height;
        lp.height = Math.max(1, height);
        if (added) updateLayout();
    }

    void attach() {
        if (added) return;
        try {
            wm.addView(root, lp);
            added = true;
            XLog.i("island window added, height=" + lp.height);
        } catch (Throwable t) {
            XLog.e("addView failed", t);
        }
    }

    void detach() {
        if (!added) return;
        try {
            wm.removeViewImmediate(root);
        } catch (Throwable t) {
            XLog.e("removeView failed", t);
        }
        added = false;
    }

    boolean isAttached() {
        return added;
    }

    void setShown(boolean shown) {
        int vis = shown ? View.VISIBLE : View.GONE;
        if (root.getVisibility() != vis) root.setVisibility(vis);
    }

    void setWatchOutsideTouch(boolean watch) {
        if (watchOutside == watch) return;
        watchOutside = watch;
        if (watch) {
            lp.flags |= WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
        } else {
            lp.flags &= ~WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
        }
        if (added) updateLayout();
    }

    void invalidateTouchRegion() {
        view.getTouchRegion(scratchRegion);
        if (scratchRegion.equals(touchRegion)) return;
        touchRegion.set(scratchRegion);
        root.invalidate();
    }

    private final android.graphics.Region scratchRegion = new android.graphics.Region();


    private final android.view.SurfaceControl.Transaction surfaceTx = new android.view.SurfaceControl.Transaction();
    private float surfaceAlpha = 1f;
    private float surfaceScale = 1f;
    private float surfacePx;
    private float surfacePy;
    private boolean surfaceFailed;

    boolean setSurfaceFade(float alpha, float scale, float px, float py) {
        if (alpha == surfaceAlpha && scale == surfaceScale && px == surfacePx && py == surfacePy) return !surfaceFailed;
        surfaceAlpha = alpha;
        surfaceScale = scale;
        surfacePx = px;
        surfacePy = py;
        applySurfaceFade();
        return !surfaceFailed;
    }

    void applySurfaceFade() {
        if (surfaceFailed || !added) return;
        try {
            Object vri = root.getRootSurfaceControl();
            Object sc = vri == null ? null : dev.flexy.dynamicisland.xposed.Refl.call(vri, "getSurfaceControl");
            if (!(sc instanceof android.view.SurfaceControl) || !((android.view.SurfaceControl) sc).isValid()) return;
            android.view.SurfaceControl s = (android.view.SurfaceControl) sc;
            surfaceTx.setAlpha(s, surfaceAlpha);
            surfaceTx.setScale(s, surfaceScale, surfaceScale);
            surfaceTx.setPosition(s, surfacePx * (1f - surfaceScale), surfacePy * (1f - surfaceScale));
            surfaceTx.apply();
        } catch (Throwable t) {
            surfaceFailed = true;
            XLog.e("surface fade failed", t);
        }
    }

    Rect windowBounds() {
        return wm.getCurrentWindowMetrics().getBounds();
    }

    DisplayCutout cutout() {
        WindowInsets insets = root.getRootWindowInsets();
        if (insets != null && insets.getDisplayCutout() != null) return insets.getDisplayCutout();
        try {
            Display d = context.getDisplay();
            return d != null ? d.getCutout() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    int rotation() {
        try {
            Display d = context.getDisplay();
            return d != null ? d.getRotation() : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    private void updateLayout() {
        try {
            wm.updateViewLayout(root, lp);
        } catch (Throwable t) {
            XLog.e("updateViewLayout failed", t);
        }
    }

    private void installTouchableRegion() {
        try {
            Class<?> listenerCls = Class.forName("android.view.ViewTreeObserver$OnComputeInternalInsetsListener");
            Class<?> infoCls = Class.forName("android.view.ViewTreeObserver$InternalInsetsInfo");
            Method setTouchable = infoCls.getMethod("setTouchableInsets", int.class);
            Field regionField = infoCls.getField("touchableRegion");
            Object listener = Refl.proxy(listenerCls, (proxy, method, args) -> {
                if ("onComputeInternalInsets".equals(method.getName()) && args != null && args.length == 1) {
                    setTouchable.invoke(args[0], TOUCHABLE_INSETS_REGION);
                    ((Region) regionField.get(args[0])).set(touchRegion);
                }
                return null;
            });
            Method add = ViewTreeObserver.class.getMethod("addOnComputeInternalInsetsListener", listenerCls);
            root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    try {
                        add.invoke(v.getViewTreeObserver(), listener);
                    } catch (Throwable t) {
                        XLog.e("addOnComputeInternalInsetsListener failed", t);
                    }
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    try {
                        Method remove = ViewTreeObserver.class.getMethod("removeOnComputeInternalInsetsListener", listenerCls);
                        remove.invoke(v.getViewTreeObserver(), listener);
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable t) {
            XLog.e("touchable region unavailable", t);
        }
    }

    private static final class Root extends FrameLayout {
        private final Listener listener;

        Root(Context context, Listener listener) {
            super(context);
            this.listener = listener;
        }

        @Override
        protected void onConfigurationChanged(Configuration newConfig) {
            super.onConfigurationChanged(newConfig);
            listener.onWindowConfigurationChanged();
        }

        @Override
        public WindowInsets onApplyWindowInsets(WindowInsets insets) {
            WindowInsets r = super.onApplyWindowInsets(insets);
            post(listener::onWindowInsetsChanged);
            return r;
        }
    }
}
