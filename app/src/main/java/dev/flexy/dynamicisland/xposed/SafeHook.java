package dev.flexy.dynamicisland.xposed;

import de.robv.android.xposed.XC_MethodHook;

public abstract class SafeHook extends XC_MethodHook {
    private final String name;

    protected SafeHook(String name) {
        this.name = name;
    }

    @Override
    protected final void beforeHookedMethod(MethodHookParam param) {
        try {
            before(param);
        } catch (Throwable t) {
            XLog.e("before " + name, t);
        }
    }

    @Override
    protected final void afterHookedMethod(MethodHookParam param) {
        try {
            after(param);
        } catch (Throwable t) {
            XLog.e("after " + name, t);
        }
    }

    protected void before(MethodHookParam param) throws Throwable {
    }

    protected void after(MethodHookParam param) throws Throwable {
    }

    public interface Body {
        void run(MethodHookParam param) throws Throwable;
    }

    public static SafeHook before(String name, Body body) {
        return new SafeHook(name) {
            @Override
            protected void before(MethodHookParam param) throws Throwable {
                body.run(param);
            }
        };
    }

    public static SafeHook after(String name, Body body) {
        return new SafeHook(name) {
            @Override
            protected void after(MethodHookParam param) throws Throwable {
                body.run(param);
            }
        };
    }
}
