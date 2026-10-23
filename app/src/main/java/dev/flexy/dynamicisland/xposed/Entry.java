package dev.flexy.dynamicisland.xposed;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class Entry implements IXposedHookLoadPackage {
    private static boolean systemUiInstalled;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if ("com.android.systemui".equals(lpparam.packageName)
                && "com.android.systemui".equals(lpparam.processName)
                && !systemUiInstalled) {
            systemUiInstalled = true;
            try {
                SystemUiHooks.install(lpparam.classLoader);
            } catch (Throwable t) {
                XLog.e("install failed", t);
            }
        }
    }
}
