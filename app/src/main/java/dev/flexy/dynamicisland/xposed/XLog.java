package dev.flexy.dynamicisland.xposed;

import android.util.Log;

import de.robv.android.xposed.XposedBridge;

public final class XLog {
    private static final String TAG = "DynamicIsland";

    private XLog() {
    }

    public static void i(String msg) {
        Log.i(TAG, msg);
        try {
            XposedBridge.log(TAG + ": " + msg);
        } catch (Throwable ignored) {
        }
    }

    public static void d(String msg) {
        Log.d(TAG, msg);
    }

    public static void e(String msg, Throwable t) {
        Log.e(TAG, msg, t);
        try {
            XposedBridge.log(TAG + ": " + msg + ": " + Log.getStackTraceString(t));
        } catch (Throwable ignored) {
        }
    }
}
