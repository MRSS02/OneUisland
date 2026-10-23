package de.robv.android.xposed.callbacks;

import android.content.pm.ApplicationInfo;

import de.robv.android.xposed.IXposedHookLoadPackage;

public abstract class XC_LoadPackage extends XCallback implements IXposedHookLoadPackage {
    public XC_LoadPackage() {
        super();
    }

    public XC_LoadPackage(int priority) {
        super(priority);
    }

    public static final class LoadPackageParam extends XCallback.Param {
        public String packageName;
        public String processName;
        public ClassLoader classLoader;
        public ApplicationInfo appInfo;
        public boolean isFirstApplication;
    }
}
