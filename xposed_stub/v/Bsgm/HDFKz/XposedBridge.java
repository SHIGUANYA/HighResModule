package v.Bsgm.HDFKz;

public class XposedBridge {
    public static void log(String text) {}
    public static void log(Throwable throwable) {}

    public static Unhook hookMethod(java.lang.reflect.Member hookMethod, Object callback) {
        return new Unhook(hookMethod, callback);
    }

    public static class Unhook {
        private final java.lang.reflect.Member hookedMethod;
        private final Object callback;
        public Unhook(java.lang.reflect.Member m, Object c) { this.hookedMethod = m; this.callback = c; }
        public java.lang.reflect.Member getHookedMethod() { return hookedMethod; }
        public void unhook() {}
    }
}
