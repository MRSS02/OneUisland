package dev.flexy.dynamicisland.xposed;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

public final class Refl {
    private static final Map<String, Field> FIELDS = new ConcurrentHashMap<>();
    private static final Map<String, Method> METHODS = new ConcurrentHashMap<>();
    private static final Set<String> MISSING = ConcurrentHashMap.newKeySet();

    private Refl() {
    }

    public static Class<?> findClass(String name, ClassLoader cl) {
        try {
            return Class.forName(name, false, cl);
        } catch (Throwable t) {
            if (MISSING.add("class:" + name)) XLog.i("class not found: " + name);
            return null;
        }
    }

    public static Field field(Class<?> cls, String name) {
        String key = cls.getName() + "#" + name;
        Field f = FIELDS.get(key);
        if (f != null) return f;
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            try {
                f = c.getDeclaredField(name);
                f.setAccessible(true);
                FIELDS.put(key, f);
                return f;
            } catch (NoSuchFieldException ignored) {
            }
        }
        if (MISSING.add("field:" + key)) XLog.i("field not found: " + key);
        return null;
    }

    public static Object get(Object obj, String name) {
        if (obj == null) return null;
        Field f = field(obj.getClass(), name);
        try {
            return f == null ? null : f.get(obj);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Object getStatic(Class<?> cls, String name) {
        if (cls == null) return null;
        Field f = field(cls, name);
        try {
            return f == null ? null : f.get(null);
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean set(Object obj, String name, Object value) {
        if (obj == null) return false;
        Field f = field(obj.getClass(), name);
        try {
            if (f == null) return false;
            f.set(obj, value);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static int getInt(Object obj, String name, int def) {
        Object v = get(obj, name);
        return v instanceof Integer ? (Integer) v : def;
    }

    public static boolean getBool(Object obj, String name, boolean def) {
        Object v = get(obj, name);
        return v instanceof Boolean ? (Boolean) v : def;
    }

    public static Method method(Class<?> cls, String name, int paramCount) {
        String key = cls.getName() + "#" + name + "/" + paramCount;
        Method m = METHODS.get(key);
        if (m != null) return m;
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            for (Method cand : c.getDeclaredMethods()) {
                if (cand.getName().equals(name) && cand.getParameterTypes().length == paramCount) {
                    cand.setAccessible(true);
                    METHODS.put(key, cand);
                    return cand;
                }
            }
        }
        if (MISSING.add("method:" + key)) XLog.i("method not found: " + key);
        return null;
    }

    public static Object call(Object obj, String name, Object... args) {
        if (obj == null) return null;
        Method m = method(obj.getClass(), name, args.length);
        try {
            return m == null ? null : m.invoke(obj, args);
        } catch (Throwable t) {
            XLog.e("call failed: " + name, t);
            return null;
        }
    }

    public static Object callStatic(Class<?> cls, String name, Object... args) {
        if (cls == null) return null;
        Method m = method(cls, name, args.length);
        try {
            return m == null ? null : m.invoke(null, args);
        } catch (Throwable t) {
            XLog.e("static call failed: " + name, t);
            return null;
        }
    }

    public static int hookAll(Class<?> cls, String name, XC_MethodHook hook) {
        if (cls == null) return 0;
        int n = 0;
        for (Method m : cls.getDeclaredMethods()) {
            if (m.getName().equals(name) && !Modifier.isAbstract(m.getModifiers())) {
                XposedBridge.hookMethod(m, hook);
                n++;
            }
        }
        if (n == 0) XLog.i("nothing to hook: " + cls.getName() + "#" + name);
        return n;
    }

    public static int hookAll(ClassLoader cl, String className, String name, XC_MethodHook hook) {
        Class<?> cls = findClass(className, cl);
        return cls == null ? 0 : hookAll(cls, name, hook);
    }

    public static int hookConstructors(ClassLoader cl, String className, XC_MethodHook hook) {
        Class<?> cls = findClass(className, cl);
        if (cls == null) return 0;
        Set<?> set = XposedBridge.hookAllConstructors(cls, hook);
        int n = set == null ? 0 : set.size();
        if (n == 0) XLog.i("no constructors hooked: " + className);
        return n;
    }

    public static Member memberOf(XC_MethodHook.MethodHookParam p) {
        return p.method;
    }

    public static Object proxy(Class<?> iface, InvocationHandler handler) {
        return Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, (p, m, args) -> {
            switch (m.getName()) {
                case "equals":
                    if (args != null && args.length == 1 && m.getParameterTypes()[0] == Object.class) {
                        return p == args[0];
                    }
                    break;
                case "hashCode":
                    if (args == null || args.length == 0) return System.identityHashCode(p);
                    break;
                case "toString":
                    if (args == null || args.length == 0) return "DynamicIslandProxy(" + iface.getSimpleName() + ")";
                    break;
                default:
                    break;
            }
            Object r = handler.invoke(p, m, args);
            if (r == null) return defaultValue(m.getReturnType());
            return r;
        });
    }

    public static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return (char) 0;
        return null;
    }
}
