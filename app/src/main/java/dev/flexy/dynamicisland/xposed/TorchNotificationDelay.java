package dev.flexy.dynamicisland.xposed;

import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.Method;

import dev.flexy.dynamicisland.island.IslandController;

final class TorchNotificationDelay {
    private static final String TAG = "Flashlight";
    private static final long DELAY_MS = 1000;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static boolean passing;

    private TorchNotificationDelay() {
    }

    static void install(ClassLoader cl) {
        SafeHook hook = SafeHook.before("torch notification delay", p -> {
            if (passing || p.args.length < 2 || !TAG.equals(p.args[0])) return;
            IslandController c = IslandController.get();
            if (c == null || !c.hasTorch()) return;
            Object target = p.thisObject;
            Object[] args = p.args.clone();
            Method method = (Method) p.method;
            p.setResult(null);
            MAIN.postDelayed(() -> {
                IslandController now = IslandController.get();
                if (now != null && now.torchOn()) return;
                passing = true;
                try {
                    method.setAccessible(true);
                    method.invoke(target, args);
                } catch (Throwable t) {
                    XLog.e("torch notification cancel", t);
                } finally {
                    passing = false;
                }
            }, DELAY_MS);
        });
        Refl.hookAll(cl, "android.app.NotificationManager", "cancel", hook);
        Refl.hookAll(cl, "android.app.NotificationManager", "cancelAsUser", hook);
    }
}
