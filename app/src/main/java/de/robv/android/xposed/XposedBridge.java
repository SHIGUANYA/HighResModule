package de.robv.android.xposed;

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Set;

public class XposedBridge {
    public static Set<XC_MethodHook.Unhook> hookAllMethods(Class<?> hookClass, String methodName, XC_MethodHook callback) {
        return null;
    }

    public static Set<XC_MethodHook.Unhook> hookAllConstructors(Class<?> hookClass, XC_MethodHook callback) {
        return null;
    }

    public static XC_MethodHook.Unhook hookMethod(Member hookMethod, XC_MethodHook callback) {
        return null;
    }

    public static void log(String text) {}
    public static void log(Throwable throwable) {}

    public static Object invokeOriginalMethod(Member method, Object thisObject, Object[] args) throws Throwable {
        return null;
    }

    public static class Unhook {
        public Member getHookedMethod() { return null; }
        public XC_MethodHook getCallback() { return null; }
        public void unhook() {}
    }

    public static class XSharedPreferences {
        public XSharedPreferences(String packageName) {}
        public XSharedPreferences(String packageName, String prefFileName) {}
        public String getString(String key, String defValue) { return defValue; }
        public int getInt(String key, int defValue) { return defValue; }
        public boolean getBoolean(String key, boolean defValue) { return defValue; }
        public void reload() {}
    }
}
