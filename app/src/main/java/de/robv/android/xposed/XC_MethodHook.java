package de.robv.android.xposed;

import java.lang.reflect.Member;

public abstract class XC_MethodHook {
    public XC_MethodHook() {}
    public XC_MethodHook(int priority) {}

    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {}

    public static final int PRIORITY_DEFAULT = 50;

    public static class MethodHookParam {
        public Member method;
        public Object thisObject;
        public Object[] args;
        private Object result;
        private Throwable throwable;

        public Object getResult() { return result; }
        public void setResult(Object result) { this.result = result; }
        public Throwable getThrowable() { return throwable; }
        public boolean hasThrowable() { return throwable != null; }
        public Object getResultOrThrowable() throws Throwable {
            if (throwable != null) throw throwable;
            return result;
        }
    }

    public static class Unhook {
        private final Member hookedMethod;
        private final XC_MethodHook callback;

        public Unhook(Member hookedMethod, XC_MethodHook callback) {
            this.hookedMethod = hookedMethod;
            this.callback = callback;
        }

        public Member getHookedMethod() { return hookedMethod; }
        public XC_MethodHook getCallback() { return callback; }
        public void unhook() {}
    }
}
