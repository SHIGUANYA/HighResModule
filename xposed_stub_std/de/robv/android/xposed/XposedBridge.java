package de.robv.android.xposed;
public class XposedBridge {
    public static void log(String text) {}
    public static void log(Throwable throwable) {}
    public static Unhook hookMethod(java.lang.reflect.Member hookMethod, Object callback) {
        return new Unhook(hookMethod, callback);
    }
    public static class Unhook {
        private final java.lang.reflect.Member m;
        private final Object c;
        public Unhook(java.lang.reflect.Member m, Object c) { this.m = m; this.c = c; }
        public java.lang.reflect.Member getHookedMethod() { return m; }
        public void unhook() {}
    }
}
