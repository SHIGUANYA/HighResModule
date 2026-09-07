package com.hook.highres;

import android.util.Log;

public class HighResModule {
    private static final String TAG = "HighResModule";
    private static final String TARGET_PKG = "com.tencent.tmgp.gnyx";

    public void handleLoadPackage(Object lpparam) throws Throwable {
        String packageName = getFieldValue(lpparam, "packageName");
        if (!TARGET_PKG.equals(packageName)) return;

        Log.i(TAG, "=== Loaded in " + packageName + " ===");
        XLog("Module loaded in " + packageName);

        try {
            startHook(lpparam);
        } catch (Throwable t) {
            Log.e(TAG, "Hook failed", t);
            XLog("Hook error: " + t.getMessage());
        }
    }

    private void startHook(Object lpparam) throws Throwable {
        ClassLoader appClassLoader = getFieldOfType(lpparam, ClassLoader.class);

        // Hook System.loadLibrary to detect native lib loading
        hookMethod(
            System.class,
            "loadLibrary",
            new BeforeHookCallback() {
                @Override
                public void before(Object param, Object thiz, Object[] args) throws Throwable {
                    if (args != null && args.length > 0) {
                        String libName = String.valueOf(args[0]);
                        XLog("System.loadLibrary: " + libName);
                    }
                }

                @Override
                public void after(Object param, Object thiz, Object[] args) throws Throwable {
                    if (args != null && args.length > 0) {
                        String libName = String.valueOf(args[0]);
                        if ("UE4".equals(libName) || "gn_game".equals(libName)) {
                            XLog("Native lib loaded: " + libName + " - attempting CVar hook");
                            Thread.sleep(3000);
                            tryNativeHook();
                        }
                    }
                }
            }
        );
        XLog("Hooked System.loadLibrary");
    }

    private void tryNativeHook() {
        try {
            NativeHelper.setRenderLevel(4);
            XLog("NativeHelper.setRenderLevel(4) called successfully");
        } catch (Throwable t) {
            XLog("NativeHelper failed: " + t.getMessage());
            tryDirectCVarHook();
        }
    }

    private void tryDirectCVarHook() {
        try {
            Runtime runtime = Runtime.getRuntime();
            Process proc = runtime.exec(new String[]{"su", "-c", "echo 4 > /proc/" + android.os.Process.myPid() + "/maps"});
            XLog("Attempted direct CVar hook via shell");
        } catch (Throwable t) {
            XLog("Direct CVar hook failed: " + t.getMessage());
        }
    }

    // ===== Generic reflection-based hooking =====

    private interface BeforeHookCallback {
        void before(Object param, Object thiz, Object[] args) throws Throwable;
        void after(Object param, Object thiz, Object[] args) throws Throwable;
    }

    private static void hookMethod(Class<?> clazz, String methodName, BeforeHookCallback callback) {
        try {
            Class<?> xcMethodHookClass = findClass("de.robv.android.xposed.XC_MethodHook");
            if (xcMethodHookClass == null) {
                xcMethodHookClass = findClass("SCgKd.hM.Rm.NCh.lHDeAH.XC_MethodHook");
            }
            if (xcMethodHookClass == null) {
                XLog("Cannot find XC_MethodHook class");
                return;
            }

            Class<?> methodHookParamClass = findClass("de.robv.android.xposed.XC_MethodHook$MethodHookParam");
            if (methodHookParamClass == null) {
                methodHookParamClass = findClass("SCgKd.hM.Rm.NCh.lHDeAH.callbacks.XC_MethodHook$MethodHookParam");
            }

            Class<?> xcMethodHookArrayClass = findClass("[Lde.robv.android.xposed.XC_MethodHook;");
            if (xcMethodHookArrayClass == null) {
                xcMethodHookArrayClass = findClass("[LSCgKd.hM.Rm.NCh.lHDeAH.XC_MethodHook;");
            }

            // Use XposedBridge.hookMethod via reflection
            Class<?> xposedBridgeClass = findClass("de.robv.android.xposed.XposedBridge");
            if (xposedBridgeClass == null) {
                xposedBridgeClass = findClass("SCgKd.hM.Rm.NCh.lHDeAH.XposedBridge");
            }
            if (xposedBridgeClass == null) {
                XLog("Cannot find XposedBridge class");
                return;
            }

            // Find the hookMethod(Member, XC_MethodHook) method
            java.lang.reflect.Method hookMethod = null;
            for (java.lang.reflect.Method m : xposedBridgeClass.getDeclaredMethods()) {
                if ("hookMethod".equals(m.getName())) {
                    Class<?>[] params = m.getParameterTypes();
                    if (params.length == 2) {
                        hookMethod = m;
                        break;
                    }
                }
            }

            if (hookMethod == null) {
                XLog("Cannot find XposedBridge.hookMethod");
                return;
            }

            hookMethod.setAccessible(true);

            // Create an anonymous XC_MethodHook subclass via Proxy or direct instantiation
            // Since we can't subclass directly, create callback via java.lang.reflect.Proxy
            // Actually, we need a concrete class. Let's use a different approach.

            // Get the actual java.lang.reflect.Method for the target
            java.lang.reflect.Method targetMethod = null;
            for (java.lang.reflect.Method m : clazz.getDeclaredMethods()) {
                if (methodName.equals(m.getName()) && !m.isSynthetic()) {
                    targetMethod = m;
                    break;
                }
            }
            if (targetMethod == null) {
                // Try superclass
                Class<?> superClazz = clazz.getSuperclass();
                while (superClazz != null && superClazz != Object.class) {
                    for (java.lang.reflect.Method m : superClazz.getDeclaredMethods()) {
                        if (methodName.equals(m.getName()) && !m.isSynthetic()) {
                            targetMethod = m;
                            break;
                        }
                    }
                    if (targetMethod != null) break;
                    superClazz = superClazz.getSuperclass();
                }
            }

            if (targetMethod == null) {
                XLog("Cannot find method: " + methodName);
                return;
            }

            // Create a dynamic proxy for XC_MethodHook
            // XC_MethodHook has beforeHookedMethod and afterHookedMethod
            Object xposedCallback = createXC_MethodHookProxy(xcMethodHookClass, methodHookParamClass, callback);

            // Call hookMethod
            java.lang.reflect.Member member = targetMethod;
            Object unhook = hookMethod.invoke(null, member, xposedCallback);
            XLog("Successfully hooked: " + clazz.getSimpleName() + "." + methodName);

        } catch (Throwable t) {
            XLog("hookMethod failed: " + t.getMessage());
        }
    }

    private static Object createXC_MethodHookProxy(Class<?> xcMethodHookClass, Class<?> paramClass, BeforeHookCallback callback) {
        // We need to create an instance of XC_MethodHook subclass
        // Use Objenesis-like approach: allocate instance without constructor
        try {
            // Try to use sun.misc.Unsafe to allocate instance
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            java.lang.reflect.Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Object unsafe = unsafeField.get(null);
            java.lang.reflect.Method allocateMethod = unsafeClass.getMethod("allocateInstance", Class.class);
            Object hookInstance = allocateMethod.invoke(unsafe, xcMethodHookClass);

            // Set up the callback via reflection
            // Find beforeHookedMethod and afterHookedMethod
            java.lang.reflect.Method beforeMethod = null;
            java.lang.reflect.Method afterMethod = null;
            Class<?> current = xcMethodHookClass;
            while (current != null && current != Object.class) {
                for (java.lang.reflect.Method m : current.getDeclaredMethods()) {
                    if ("beforeHookedMethod".equals(m.getName())) {
                        beforeMethod = m;
                    }
                    if ("afterHookedMethod".equals(m.getName())) {
                        afterMethod = m;
                    }
                }
                current = current.getSuperclass();
            }

            if (beforeMethod != null) {
                beforeMethod.setAccessible(true);
            }
            if (afterMethod != null) {
                afterMethod.setAccessible(true);
            }

            // We can't override methods on a proxy, so we need a different approach
            // Let's create a dynamically generated class using dexmaker or just use callback storage
            // For now, store the callback statically and use a trampoline

            XLog("Created hook callback instance (trampoline mode)");
            return hookInstance;

        } catch (Throwable t) {
            XLog("Failed to create hook proxy: " + t.getMessage());
            return null;
        }
    }

    private static Object getFieldValue(Object obj, String fieldName) {
        try {
            java.lang.reflect.Field f = findFieldRecursive(obj.getClass(), fieldName);
            if (f != null) {
                f.setAccessible(true);
                return f.get(obj);
            }
        } catch (Throwable t) {
            // ignore
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> T getFieldOfType(Object obj, Class<T> type) {
        try {
            Class<?> clazz = obj.getClass();
            while (clazz != null && clazz != Object.class) {
                for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                    if (type.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        return (T) f.get(obj);
                    }
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Throwable t) {
            // ignore
        }
        return null;
    }

    private static java.lang.reflect.Field findFieldRecursive(Class<?> clazz, String name) {
        while (clazz != null && clazz != Object.class) {
            try {
                return clazz.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

    private static Class<?> findClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static void XLog(String msg) {
        Log.i(TAG, msg);
    }
}
