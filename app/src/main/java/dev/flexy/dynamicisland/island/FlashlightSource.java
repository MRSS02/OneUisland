package dev.flexy.dynamicisland.island;

import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Handler;

import java.util.List;

import dev.flexy.dynamicisland.xposed.XLog;

final class FlashlightSource {
    static final String KEY = "torch";
    static final int ACCENT = 0xFFC9C6FF;

    private final Context ctx;
    private final Handler main;
    private final Runnable onChanged;
    private CameraManager cameras;
    private String cameraId;
    private int maxLevel = 1;
    private boolean on;
    private int level = 1;
    private boolean keep;
    private long onSince;

    FlashlightSource(Context ctx, Handler main, Runnable onChanged) {
        this.ctx = ctx;
        this.main = main;
        this.onChanged = onChanged;
    }

    void start() {
        try {
            cameras = ctx.getSystemService(CameraManager.class);
            for (String id : cameras.getCameraIdList()) {
                CameraCharacteristics c = cameras.getCameraCharacteristics(id);
                Boolean flash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer facing = c.get(CameraCharacteristics.LENS_FACING);
                if (!Boolean.TRUE.equals(flash) || facing == null || facing != CameraCharacteristics.LENS_FACING_BACK) continue;
                cameraId = id;
                Integer max = c.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL);
                maxLevel = max == null ? 1 : Math.max(1, max);
                break;
            }
            if (cameraId == null) return;
            cameras.registerTorchCallback(new CameraManager.TorchCallback() {
                @Override
                public void onTorchModeChanged(String id, boolean enabled) {
                    if (!id.equals(cameraId)) return;
                    if (enabled && !on) onSince = System.currentTimeMillis();
                    on = enabled;
                    if (enabled) readLevel();
                    onChanged.run();
                }

                @Override
                public void onTorchStrengthLevelChanged(String id, int newLevel) {
                    if (!id.equals(cameraId)) return;
                    level = Math.max(1, Math.min(maxLevel, newLevel));
                    onChanged.run();
                }
            }, main);
            XLog.i("torch camera " + cameraId + " levels " + maxLevel);
        } catch (Throwable t) {
            XLog.e("torch source failed", t);
        }
    }

    private void readLevel() {
        try {
            if (maxLevel > 1) level = Math.max(1, Math.min(maxLevel, cameras.getTorchStrengthLevel(cameraId)));
        } catch (Throwable ignored) {
        }
    }

    boolean isOn() {
        return on;
    }

    boolean available() {
        return cameraId != null;
    }

    boolean collect(List<IslandItem> items, boolean expanded) {
        if (cameraId == null) return false;
        if (!on) {
            if (!keep || !expanded) {
                keep = false;
                return false;
            }
        }
        items.add(item());
        return true;
    }

    private IslandItem cached;

    IslandItem item() {
        IslandItem c = cached;
        if (c != null && c.torchOn == on && c.torchLevel == level) return c;
        IslandItem it = new IslandItem(KEY);
        it.kind = IslandItem.Kind.LIVE;
        it.packageName = "com.android.systemui";
        it.accent = ACCENT;
        it.title = "Flashlight";
        it.torch = true;
        it.torchOn = on;
        it.torchLevel = level;
        it.torchMax = maxLevel;
        it.torchToggle = this::toggle;
        it.torchSet = this::setLevel;
        it.postTime = onSince;
        cached = it;
        return it;
    }

    static boolean isSystemTorchKey(String key) {
        return key != null && key.contains("|com.android.systemui|") && key.contains("|Flashlight|");
    }

    private void toggle() {
        try {
            keep = true;
            if (on) {
                cameras.setTorchMode(cameraId, false);
            } else if (maxLevel > 1) {
                cameras.turnOnTorchWithStrengthLevel(cameraId, level);
            } else {
                cameras.setTorchMode(cameraId, true);
            }
        } catch (Throwable t) {
            XLog.e("torch toggle failed", t);
        }
    }

    private void setLevel(int value) {
        int v = Math.max(0, Math.min(maxLevel, value));
        try {
            keep = true;
            if (v == 0) {
                if (on) cameras.setTorchMode(cameraId, false);
            } else if (maxLevel > 1) {
                if (v == level && on) return;
                level = v;
                cameras.turnOnTorchWithStrengthLevel(cameraId, v);
            } else if (!on) {
                cameras.setTorchMode(cameraId, true);
            }
        } catch (Throwable t) {
            XLog.e("torch level failed", t);
        }
    }

    static boolean isSystemTorchItem(IslandItem it) {
        return isSystemTorchKey(it.key);
    }
}
