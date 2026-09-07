package de.robv.android.xposed;
public abstract class XC_MethodHook {
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {}
    public static class MethodHookParam {
        public java.lang.reflect.Member method;
        public Object thisObject;
        public Object[] args;
        private Object result;
        private Throwable throwable;
        public Object getResult() { return result; }
        public void setResult(Object r) { this.result = r; }
        public Throwable getThrowable() { return throwable; }
        public boolean hasThrowable() { return throwable != null; }
    }
    public static class Unhook {
        private final java.lang.reflect.Member m;
        private final XC_MethodHook c;
        public Unhook(java.lang.reflect.Member m, XC_MethodHook c) { this.m = m; this.c = c; }
        public java.lang.reflect.Member getHookedMethod() { return m; }
        public void unhook() {}
    }
}
