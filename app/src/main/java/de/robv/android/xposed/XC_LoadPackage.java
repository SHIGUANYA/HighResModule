package de.robv.android.xposed;

public class XC_LoadPackage {
    public static class LoadPackageParam {
        public String packageName;
        public String processName;
        public ClassLoader classLoader;
        public String appInfo;
        public boolean isFirstApplication;
    }
}
